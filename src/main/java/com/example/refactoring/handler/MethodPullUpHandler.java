package com.example.refactoring.handler;

import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.CtScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import com.example.refactoring.core.ImportManager;

/**
 * 方法依赖上提处理器 - 处理依赖方法的抽象上提
 */
public class MethodPullUpHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MethodPullUpHandler.class);
    private final SuperCallHandler superCallHandler;
    private final VisibilityHandler visibilityHandler;
    private final ImportManager importManager;
    
    public MethodPullUpHandler() {
        this.superCallHandler = new SuperCallHandler();
        this.visibilityHandler = new VisibilityHandler();
        this.importManager = new ImportManager();
    }
    
    /**
     * 方法上提结果类
     */
    public static class MethodPullUpResult {
        private final boolean success;
        private final String message;
        private final List<CtMethod<?>> pulledUpMethods;
        private final List<String> warnings;
        
        private MethodPullUpResult(boolean success, String message, 
                                 List<CtMethod<?>> pulledUpMethods, List<String> warnings) {
            this.success = success;
            this.message = message;
            this.pulledUpMethods = pulledUpMethods != null ? pulledUpMethods : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }
        
        public static MethodPullUpResult success(String message, List<CtMethod<?>> pulledUpMethods) {
            return new MethodPullUpResult(true, message, pulledUpMethods, new ArrayList<>());
        }
        
        public static MethodPullUpResult failure(String message) {
            return new MethodPullUpResult(false, message, new ArrayList<>(), new ArrayList<>());
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<CtMethod<?>> getPulledUpMethods() { return new ArrayList<>(pulledUpMethods); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        
        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
    }
    
     /**
      * 分析并上提方法依赖的其他方法（作为抽象方法）
      * 
      * @param method 要分析的方法
      * @param childClass 后代类（触发上提的方法所在类）
      * @param parentClass 目标祖先类（抽象声明将添加到此类）
      * @param allChildClasses 所有后代类（用于可见性一致性和缺失实现补全）
      * @return 方法上提结果
      */
     public MethodPullUpResult pullUpDependentMethods(CtMethod<?> method, 
                                                    CtClass<?> childClass, 
                                                    CtClass<?> parentClass,
                                                    List<CtClass<?>> allChildClasses) {
        try {
            logger.debug("开始分析方法 {} 的方法依赖", method.getSimpleName());
            
            // 1. 收集方法中调用的后代类自身或其与目标祖先类之间的祖先类的方法
            Set<CtMethod<?>> dependentMethods = collectDependentMethods(method, childClass, parentClass);
            
            if (dependentMethods.isEmpty()) {
                return MethodPullUpResult.success("方法无依赖方法需要上提", new ArrayList<>());
            }
            
            logger.info("发现 {} 个依赖方法需要上提为抽象方法", dependentMethods.size());
            
            // 2. 检查方法是否可以安全上提
            MethodPullUpResult validationResult = validateMethodsCanBePulledUp(dependentMethods, parentClass);
            if (!validationResult.isSuccess()) {
                return validationResult;
            }
            
             // 3. 执行方法上提（作为抽象方法）
             List<CtMethod<?>> pulledUpMethods = new ArrayList<>();
             List<String> warnings = new ArrayList<>();
             
             for (CtMethod<?> dependentMethod : dependentMethods) {
                 try {
                     CtMethod<?> abstractMethod = pullUpSingleMethodAsAbstract(dependentMethod, childClass, parentClass);
                     if (abstractMethod != null) {
                         // 先尝试根据所有后代类的方法签名统一返回类型，避免类型冲突
                         try {
                             adjustAbstractReturnTypeForAllChildren(abstractMethod, allChildClasses);
                         } catch (Exception rtEx) {
                             logger.debug("依赖方法返回类型调整失败: {}", rtEx.getMessage());
                         }
                         pulledUpMethods.add(abstractMethod);
                         logger.info("成功上提抽象方法: {}", dependentMethod.getSimpleName());
                         
                         // 3.5. 收集所有子类中的对应方法并调整可见性
                         List<CtMethod<?>> allChildMethods = collectCorrespondingMethodsInAllChildClasses(
                             dependentMethod, allChildClasses);
                         
                         if (!allChildMethods.isEmpty()) {
                             VisibilityHandler.VisibilityAdjustmentResult visibilityResult = 
                                 visibilityHandler.adjustMethodVisibility(abstractMethod, allChildMethods);
                             
                             if (visibilityResult.isSuccess()) {
                                 visibilityResult.getAdjustments().forEach(adjustment -> 
                                     logger.info("可见性调整: {}", adjustment));
                             } else {
                                 warnings.add("可见性调整失败: " + visibilityResult.getMessage());
                             }
                             
                             visibilityResult.getWarnings().forEach(warning -> 
                                 warnings.add("可见性调整警告: " + warning));
                         }

                         // 3.6. 为未实现该方法的其它直接子类生成最基础的覆写实现
                         try {
                             generateMissingChildStubs(abstractMethod, childClass, allChildClasses);
                         } catch (Exception genEx) {
                             String warn = "为未实现子类生成基础实现失败: " + genEx.getMessage();
                             warnings.add(warn);
                             logger.warn(warn, genEx);
                         }
                     }
                 } catch (Exception e) {
                     String warning = "方法上提失败: " + dependentMethod.getSimpleName() + " - " + e.getMessage();
                     warnings.add(warning);
                     logger.warn(warning, e);
                 }
             }
            
            // 4. 处理子类中的super调用冲突
            if (!pulledUpMethods.isEmpty()) {
                try {
                    // 需要检查子类中所有依赖方法的super调用
                    for (CtMethod<?> dependentMethod : dependentMethods) {
                        SuperCallHandler.SuperCallHandlingResult superCallResult = 
                            superCallHandler.handleSuperCalls(dependentMethod, pulledUpMethods, parentClass);
                        
                        if (superCallResult.isSuccess() && !superCallResult.getHandledCalls().isEmpty()) {
                            logger.info("在方法 {} 中处理了 {} 个super调用冲突", 
                                      dependentMethod.getSimpleName(), superCallResult.getHandledCalls().size());
                            superCallResult.getHandledCalls().forEach(call -> 
                                logger.info("  - 处理super调用: {}", call));
                        }
                        
                        superCallResult.getWarnings().forEach(warning -> {
                            warnings.add("Super调用处理警告: " + warning);
                            logger.warn("Super调用处理警告: {}", warning);
                        });
                    }
                    
                    // 5. 检查父类是否还需要保持抽象类状态
                    updateParentAbstractStatus(parentClass, pulledUpMethods);
                    
                } catch (Exception e) {
                    String warning = "处理super调用时发生异常: " + e.getMessage();
                    warnings.add(warning);
                    logger.warn(warning, e);
                }
            }
            
            MethodPullUpResult result = MethodPullUpResult.success(
                "成功上提 " + pulledUpMethods.size() + " 个抽象方法", pulledUpMethods);
            warnings.forEach(result::addWarning);
            
            return result;
            
        } catch (Exception e) {
            logger.error("方法上提过程中发生异常", e);
            return MethodPullUpResult.failure("方法上提失败: " + e.getMessage());
        }
    }
    
    /**
     * 收集方法中调用的后代类自身方法，及位于 后代类 -> 目标祖先类 之间的中间祖先类的方法
     */
    private Set<CtMethod<?>> collectDependentMethods(CtMethod<?> method, CtClass<?> childClass, CtClass<?> targetAncestorClass) {
        Set<CtMethod<?>> dependentMethods = new HashSet<>();
        
        method.accept(new CtScanner() {
            @Override
            public <T> void visitCtInvocation(spoon.reflect.code.CtInvocation<T> invocation) {
                checkMethodReference(invocation.getExecutable());
                super.visitCtInvocation(invocation);
            }
            
            @Override
            public <T> void visitCtConstructorCall(spoon.reflect.code.CtConstructorCall<T> ctConstructorCall) {
                checkMethodReference(ctConstructorCall.getExecutable());
                super.visitCtConstructorCall(ctConstructorCall);
            }
            
            private void checkMethodReference(CtExecutableReference<?> methodRef) {
                if (methodRef == null) return;
                
                spoon.reflect.reference.CtTypeReference<?> declaringType = methodRef.getDeclaringType();
                if (declaringType == null) return;
                
                CtExecutable<?> executable = methodRef.getExecutableDeclaration();
                if (!(executable instanceof CtMethod)) return;
                CtMethod<?> calledMethod = (CtMethod<?>) executable;

                // 情况A：调用了当前后代类自身方法
                if (declaringType.equals(childClass.getReference())) {
                    if (!calledMethod.equals(method) && calledMethod.getParent() == childClass) {
                        dependentMethods.add(calledMethod);
                        logger.debug("发现依赖方法(后代自身): {}", calledMethod.getSimpleName());
                    }
                    return;
                }

                // 情况B：调用了位于 后代类 -> 目标祖先类 之间的中间祖先类的方法
                if (isTypeBetweenDescendantAndAncestor(declaringType, childClass, targetAncestorClass)) {
                    dependentMethods.add(calledMethod);
                    logger.debug("发现依赖方法(中间祖先): {} (声明于: {})", calledMethod.getSimpleName(), declaringType.getQualifiedName());
                }
            }
        });
        
         return dependentMethods;
     }

    /**
     * 判断某类型是否位于 后代类 与 目标祖先类 之间的继承路径上（不包含目标祖先类本身）
     */
    private boolean isTypeBetweenDescendantAndAncestor(spoon.reflect.reference.CtTypeReference<?> typeRef,
                                                       CtClass<?> descendant,
                                                       CtClass<?> targetAncestor) {
        try {
            if (typeRef == null) return false;
            spoon.reflect.declaration.CtType<?> typeDecl = typeRef.getTypeDeclaration();
            if (!(typeDecl instanceof CtClass)) return false;
            CtClass<?> targetType = (CtClass<?>) typeDecl;

            CtClass<?> current = descendant;
            while (current != null) {
                spoon.reflect.reference.CtTypeReference<?> superRef = current.getSuperclass();
                if (superRef == null) return false;
                spoon.reflect.declaration.CtType<?> superDecl = superRef.getTypeDeclaration();
                if (!(superDecl instanceof CtClass)) return false;
                CtClass<?> superClass = (CtClass<?>) superDecl;
                if (superClass.equals(targetAncestor)) {
                    return false; // 到达目标祖先
                }
                if (superClass.equals(targetType)) {
                    return true; // 命中路径上的中间祖先类
                }
                current = superClass;
            }
        } catch (Exception ignore) {}
        return false;
    }
     
     /**
      * 收集所有子类中对应的方法
      */
     private List<CtMethod<?>> collectCorrespondingMethodsInAllChildClasses(CtMethod<?> referenceMethod, 
                                                                           List<CtClass<?>> allChildClasses) {
         List<CtMethod<?>> correspondingMethods = new ArrayList<>();
         String methodName = referenceMethod.getSimpleName();
         List<CtParameter<?>> referenceParams = referenceMethod.getParameters();
         
         for (CtClass<?> childClass : allChildClasses) {
             for (CtMethod<?> method : childClass.getMethods()) {
                 if (method.getSimpleName().equals(methodName) && 
                     hasSameParameters(method, referenceParams)) {
                     correspondingMethods.add(method);
                     logger.debug("找到对应方法: {} 在类 {}", methodName, childClass.getSimpleName());
                     break; // 每个类只应该有一个对应方法
                 }
             }
         }
         
         return correspondingMethods;
     }
     
     /**
      * 检查两个方法的参数是否匹配
      */
     private boolean hasSameParameters(CtMethod<?> method, List<CtParameter<?>> referenceParams) {
         List<CtParameter<?>> methodParams = method.getParameters();
         
         if (methodParams.size() != referenceParams.size()) {
             return false;
         }
         
         for (int i = 0; i < methodParams.size(); i++) {
             if (!methodParams.get(i).getType().equals(referenceParams.get(i).getType())) {
                 return false;
             }
         }
         
         return true;
     }
     
     /**
      * 验证方法是否可以安全上提
      */
    private MethodPullUpResult validateMethodsCanBePulledUp(Set<CtMethod<?>> methods, CtClass<?> parentClass) {
        List<String> blockingIssues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        for (CtMethod<?> method : methods) {
            // 检查父类是否已有同名方法
            if (hasMethodWithSameSignature(parentClass, method)) {
                blockingIssues.add("父类已存在同签名方法: " + method.getSimpleName());
            }
            
            // 私有方法需要调整可见性
            if (method.hasModifier(ModifierKind.PRIVATE)) {
                warnings.add("方法 " + method.getSimpleName() + " 为私有，将调整为 protected abstract");
            } else {
                warnings.add("方法 " + method.getSimpleName() + " 将转换为 abstract");
            }
        }
        
        if (!blockingIssues.isEmpty()) {
            return MethodPullUpResult.failure("方法验证失败:\n" + String.join("\n", blockingIssues));
        }
        
        MethodPullUpResult result = MethodPullUpResult.success("方法验证通过", new ArrayList<>());
        warnings.forEach(result::addWarning);
        return result;
    }
    
     /**
      * 检查父类是否已有同签名方法
      */
     private boolean hasMethodWithSameSignature(CtClass<?> parentClass, CtMethod<?> method) {
         return parentClass.getMethods().stream()
             .anyMatch(existingMethod -> 
                 existingMethod.getSimpleName().equals(method.getSimpleName()) &&
                 hasSameParameters(existingMethod, method.getParameters()));
     }
    
    
    /**
     * 上提单个方法作为抽象方法
     */
    private CtMethod<?> pullUpSingleMethodAsAbstract(CtMethod<?> method, CtClass<?> childClass, CtClass<?> parentClass) {
        try {
            // 1. 创建抽象方法签名
            CtMethod<?> abstractMethod = createAbstractMethodSignature(method, parentClass.getFactory());
            abstractMethod.setParent(parentClass);
            
            // 2. 调整可见性
            if (abstractMethod.hasModifier(ModifierKind.PRIVATE)) {
                abstractMethod.removeModifier(ModifierKind.PRIVATE);
                abstractMethod.addModifier(ModifierKind.PROTECTED);
                logger.info("方法 {} 可见性已从 private 调整为 protected", method.getSimpleName());
            }
            // 若发生跨模块上提（后代类与目标祖先类属于不同模块），将抽象方法设为 public 以便外部模块直接调用
            if (isCrossModule(childClass, parentClass)) {
                abstractMethod.removeModifier(ModifierKind.PROTECTED);
                abstractMethod.addModifier(ModifierKind.PUBLIC);
                logger.info("检测到跨模块上提，方法 {} 可见性提升为 public", method.getSimpleName());
            }
            
            // 3. 添加 abstract 修饰符
            abstractMethod.addModifier(ModifierKind.ABSTRACT);
            
            // 4. 移除方法体（抽象方法没有实现）
            abstractMethod.setBody(null);
            
            // 5. 将父类设置为抽象类（如果还不是的话）
            if (!parentClass.hasModifier(ModifierKind.ABSTRACT)) {
                parentClass.addModifier(ModifierKind.ABSTRACT);
                logger.info("父类 {} 已设置为抽象类", parentClass.getSimpleName());
            }
            
            // 6. 添加到父类
            parentClass.addMethod(abstractMethod);
            
            // 7. 不从子类移除原方法，因为子类需要实现这个抽象方法
            // 但需要调整可见性并添加 @Override 注解
            boolean visibilityWasAdjusted = false;
            if (method.hasModifier(ModifierKind.PRIVATE)) {
                method.removeModifier(ModifierKind.PRIVATE);
                method.addModifier(ModifierKind.PROTECTED);
                visibilityWasAdjusted = true;
                logger.debug("子类方法 {} 可见性已从 private 调整为 protected", method.getSimpleName());
            }
            
             if (!hasOverrideAnnotation(method)) {
                 // 关键修复：如果刚调整过可见性，跳过修饰符重置以避免粘黏
                 visibilityHandler.addOverrideAnnotationProperly(method, visibilityWasAdjusted);
                 logger.debug("为子类方法 {} 添加了 @Override 注解", method.getSimpleName());
             }
            
            return abstractMethod;
            
        } catch (Exception e) {
            logger.error("上提抽象方法 {} 失败", method.getSimpleName(), e);
            throw e;
        }
    }
    
    /**
     * 创建抽象方法签名
     */
    private CtMethod<?> createAbstractMethodSignature(CtMethod<?> originalMethod, spoon.reflect.factory.Factory factory) {
        CtMethod<?> abstractMethod = factory.Core().createMethod();
        
        // 复制基本信息
        abstractMethod.setSimpleName(originalMethod.getSimpleName());
        abstractMethod.setType(originalMethod.getType());
        
        // 复制修饰符（除了 final 和 static）
        Set<ModifierKind> modifiers = new HashSet<>(originalMethod.getModifiers());
        modifiers.remove(ModifierKind.FINAL);
        modifiers.remove(ModifierKind.STATIC);
        abstractMethod.setModifiers(modifiers);
        
        // 复制参数
        for (CtParameter<?> param : originalMethod.getParameters()) {
            CtParameter<?> newParam = param.clone();
            newParam.setParent(abstractMethod);
            abstractMethod.addParameter(newParam);
        }
        
        // 复制异常声明
        for (spoon.reflect.reference.CtTypeReference<? extends Throwable> thrownType : originalMethod.getThrownTypes()) {
            abstractMethod.addThrownType(thrownType);
        }
        
        return abstractMethod;
    }
    
    /**
     * 检查方法是否已有 @Override 注解
     */
    private boolean hasOverrideAnnotation(CtMethod<?> method) {
        return method.getAnnotations().stream()
            .anyMatch(annotation -> 
                annotation.getAnnotationType().getSimpleName().equals("Override"));
    }
    
    /**
     * 更新父类的抽象状态
     */
    private void updateParentAbstractStatus(CtClass<?> parentClass, List<CtMethod<?>> pulledUpMethods) {
        // 检查是否还有抽象方法
        boolean hasAbstractMethods = parentClass.getMethods().stream()
            .anyMatch(method -> method.hasModifier(ModifierKind.ABSTRACT));
        
        if (!hasAbstractMethods && parentClass.hasModifier(ModifierKind.ABSTRACT)) {
            parentClass.removeModifier(ModifierKind.ABSTRACT);
            logger.info("父类 {} 已移除abstract修饰符，所有抽象方法都已提供默认实现", parentClass.getSimpleName());
        }
    }

    /**
     * 为未实现抽象方法的直接子类生成最基础的覆写实现
     * 规则：
     * - 复制签名（名称、返回类型、参数、抛出异常）
     * - 移除abstract，保持与父类一致/合理的可见性
     * - 添加@Override
     * - 方法体：
     *   - void：空方法体
     *   - 非void：抛出 UnsupportedOperationException，以保证可编译
     */
    private void generateMissingChildStubs(CtMethod<?> abstractMethod, CtClass<?> originalChildClass, List<CtClass<?>> targetClasses) {
        if (abstractMethod == null || targetClasses == null || targetClasses.isEmpty()) {
            return;
        }
        try {
            String methodName = abstractMethod.getSimpleName();
            List<CtParameter<?>> referenceParams = abstractMethod.getParameters();
            spoon.reflect.factory.Factory factory = abstractMethod.getFactory();

            // 祖先类（抽象方法所在类）
            final CtClass<?> ancestorClass = (abstractMethod.getParent() instanceof CtClass)
                ? (CtClass<?>) abstractMethod.getParent()
                : null;

            // 为了避免在同一分支重复实现，按从近祖先到更深后代的顺序生成
            List<CtClass<?>> ordered = new java.util.ArrayList<>(targetClasses);
            ordered.sort((a, b) -> Integer.compare(
                computeDepthFromAncestor(a, ancestorClass),
                computeDepthFromAncestor(b, ancestorClass)));
            
            for (CtClass<?> child : ordered) {
                // 跳过：原始子类及其所有后代（它们已拥有或继承实现）
                if (child.equals(originalChildClass) || isDescendantOf(child, originalChildClass)) {
                    continue;
                }
                // 跳过：若该子类的任何祖先（直到抽象方法所在类）已提供了具体实现
                if (hasAncestorConcreteImplementation(child, methodName, referenceParams, ancestorClass)) {
                    continue;
                }
                // 已存在同签名方法则跳过
                boolean exists = false;
                for (CtMethod<?> m : child.getMethods()) {
                    if (m.getSimpleName().equals(methodName) && hasSameParameters(m, referenceParams)) {
                        exists = true;
                        break;
                    }
                }
                if (exists) continue;
                
                // 生成最基础实现
                CtMethod<?> stub = factory.Core().createMethod();
                stub.setSimpleName(methodName);
                stub.setType(abstractMethod.getType());
                
                // 复制修饰符（去掉abstract）
                java.util.Set<ModifierKind> mods = new java.util.HashSet<>(abstractMethod.getModifiers());
                mods.remove(ModifierKind.ABSTRACT);
                stub.setModifiers(mods);
                
                // 复制参数
                for (CtParameter<?> p : referenceParams) {
                    CtParameter<?> np = p.clone();
                    np.setParent(stub);
                    stub.addParameter(np);
                }
                
                // 复制throws
                for (spoon.reflect.reference.CtTypeReference<? extends Throwable> t : abstractMethod.getThrownTypes()) {
                    stub.addThrownType(t);
                }
                
                // 方法体
                spoon.reflect.code.CtBlock<?> body = factory.Core().createBlock();
                if (stub.getType() != null && !"void".equals(stub.getType().getSimpleName())) {
                    // 非void返回：抛出异常，避免构造各类型默认值带来的复杂导入
                    spoon.reflect.code.CtStatement throwStmt = factory.Code()
                        .createCodeSnippetStatement("throw new UnsupportedOperationException(\"Auto-generated method stub\")");
                    body.addStatement(throwStmt);
                }
                stub.setBody(body);
                
                // 添加到子类并加上@Override
                child.addMethod(stub);
                visibilityHandler.addOverrideAnnotationProperly(stub, false);

                // 补齐导入：确保子类文件包含签名与方法体所需的类型导入
                try {
                    importManager.ensureMissingImportsForMethod(child, stub);
                } catch (Exception impEx) {
                    logger.debug("为子类 {} 的方法 {} 补齐导入失败: {}", child.getSimpleName(), methodName, impEx.getMessage());
                }
                
                logger.info("为子类 {} 生成方法 {} 的基础实现", child.getSimpleName(), methodName);
            }
        } catch (Exception e) {
            logger.warn("生成未实现子类基础方法实现时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 判断 target 是否是 ancestor 的后代类
     */
    private boolean isDescendantOf(CtClass<?> target, CtClass<?> ancestor) {
        try {
            CtClass<?> current = target;
            while (current != null) {
                spoon.reflect.reference.CtTypeReference<?> superRef = current.getSuperclass();
                if (superRef == null) return false;
                spoon.reflect.declaration.CtType<?> superType = superRef.getTypeDeclaration();
                if (superType instanceof CtClass) {
                    CtClass<?> superClass = (CtClass<?>) superType;
                    if (superClass.equals(ancestor)) return true;
                    current = superClass;
                } else {
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 若在 child 的祖先链（直到 ancestorClass，不含其上层）中已存在同签名且有方法体的实现，则返回 true
     */
    private boolean hasAncestorConcreteImplementation(CtClass<?> child,
                                                      String methodName,
                                                      List<CtParameter<?>> referenceParams,
                                                      CtClass<?> ancestorClass) {
        try {
            CtClass<?> current = child;
            while (current != null) {
                spoon.reflect.reference.CtTypeReference<?> superRef = current.getSuperclass();
                if (superRef == null) return false;
                spoon.reflect.declaration.CtType<?> superType = superRef.getTypeDeclaration();
                if (!(superType instanceof CtClass)) return false;
                CtClass<?> superClass = (CtClass<?>) superType;

                // 到达抽象方法所在类则停止（该类为抽象声明，不视为具体实现）
                if (ancestorClass != null && superClass.equals(ancestorClass)) {
                    return false;
                }

                for (CtMethod<?> m : superClass.getMethods()) {
                    if (m.getSimpleName().equals(methodName) && hasSameParameters(m, referenceParams)) {
                        if (m.getBody() != null) {
                            return true; // 已有具体实现
                        }
                    }
                }

                current = superClass;
            }
        } catch (Exception ignore) {}
        return false;
    }

    private int computeDepthFromAncestor(CtClass<?> clazz, CtClass<?> ancestorClass) {
        int depth = 0;
        try {
            CtClass<?> current = clazz;
            while (current != null) {
                if (ancestorClass != null && current.equals(ancestorClass)) {
                    return depth;
                }
                spoon.reflect.reference.CtTypeReference<?> superRef = current.getSuperclass();
                if (superRef == null) break;
                spoon.reflect.declaration.CtType<?> superType = superRef.getTypeDeclaration();
                if (!(superType instanceof CtClass)) break;
                current = (CtClass<?>) superType;
                depth++;
            }
        } catch (Exception ignore) {}
        return Integer.MAX_VALUE; // 不在该祖先链上时，放到最后
    }

    /**
     * 基于所有后代类中对应方法的返回类型，调整抽象方法的返回类型为兼容的公共超类型。
     * 若无法解析公共父类，则回退为 java.lang.Object。
     */
    private void adjustAbstractReturnTypeForAllChildren(CtMethod<?> abstractMethod, List<CtClass<?>> allChildClasses) {
        try {
            String methodName = abstractMethod.getSimpleName();
            List<CtParameter<?>> params = abstractMethod.getParameters();
            spoon.reflect.factory.Factory factory = abstractMethod.getFactory();

            List<spoon.reflect.reference.CtTypeReference<?>> returnTypes = new java.util.ArrayList<>();
            for (CtClass<?> c : allChildClasses) {
                for (CtMethod<?> m : c.getMethods()) {
                    if (m.getSimpleName().equals(methodName) && hasSameParameters(m, params) && m.getType() != null) {
                        returnTypes.add(m.getType());
                        break;
                    }
                }
            }
            if (returnTypes.isEmpty()) return; // 无信息则不调整

            spoon.reflect.reference.CtTypeReference<?> current = abstractMethod.getType();
            if (current == null && !returnTypes.isEmpty()) {
                current = returnTypes.get(0);
            }
            for (spoon.reflect.reference.CtTypeReference<?> rt : returnTypes) {
                if (current == null) { current = rt; continue; }
                if (isSubtypeOf(rt, current)) {
                    // ok
                } else if (isSubtypeOf(current, rt)) {
                    current = rt; // 选择更上层的
                } else {
                    // 迭代寻找公共父类
                    current = findCommonSuperType(current, rt, factory);
                    if (current == null) {
                        current = factory.Type().OBJECT;
                        break;
                    }
                }
            }
            if (current != null) {
                abstractMethod.setType(current);
            }
        } catch (Exception e) {
            logger.debug("调整抽象方法返回类型时异常: {}", e.getMessage());
        }
    }

    private boolean isSubtypeOf(spoon.reflect.reference.CtTypeReference<?> a, spoon.reflect.reference.CtTypeReference<?> b) {
        try {
            if (a == null || b == null) return false;
            return a.isSubtypeOf(b);
        } catch (Exception ignore) {
            return false;
        }
    }

    private spoon.reflect.reference.CtTypeReference<?> findCommonSuperType(
        spoon.reflect.reference.CtTypeReference<?> a,
        spoon.reflect.reference.CtTypeReference<?> b,
        spoon.reflect.factory.Factory factory) {
        try {
            // 简化：向上攀升 a 的父类，直到 b 是其子类型，或到达 Object
            spoon.reflect.reference.CtTypeReference<?> cur = a;
            int guard = 64;
            while (cur != null && guard-- > 0) {
                if (isSubtypeOf(b, cur)) return cur;
                spoon.reflect.declaration.CtType<?> decl = cur.getTypeDeclaration();
                if (decl instanceof CtClass) {
                    spoon.reflect.reference.CtTypeReference<?> superRef = ((CtClass<?>) decl).getSuperclass();
                    if (superRef == null) break;
                    cur = superRef;
                } else {
                    break;
                }
            }
        } catch (Exception ignore) {}
        return factory != null ? factory.Type().OBJECT : null;
    }

    /**
     * 若后代类与目标祖先类属于不同模块（通过最近的 pom.xml 判断），视为跨模块
     */
    private boolean isCrossModule(CtClass<?> descendantClass, CtClass<?> ancestorClass) {
        try {
            java.io.File a = ancestorClass.getPosition() != null ? ancestorClass.getPosition().getFile() : null;
            java.io.File d = descendantClass.getPosition() != null ? descendantClass.getPosition().getFile() : null;
            if (a == null || d == null) return false;
            java.io.File pomA = findNearestPom(a);
            java.io.File pomD = findNearestPom(d);
            if (pomA == null || pomD == null) return false;
            return !pomA.getAbsolutePath().equals(pomD.getAbsolutePath());
        } catch (Exception ignore) {
            return false;
        }
    }

    private java.io.File findNearestPom(java.io.File start) {
        try {
            java.io.File dir = start.isDirectory() ? start : start.getParentFile();
            while (dir != null) {
                java.io.File pom = new java.io.File(dir, "pom.xml");
                if (pom.exists() && pom.isFile()) return pom;
                dir = dir.getParentFile();
            }
        } catch (Exception ignore) {}
        return null;
    }

}
