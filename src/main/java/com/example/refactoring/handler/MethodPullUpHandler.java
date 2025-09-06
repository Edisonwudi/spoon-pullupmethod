package com.example.refactoring.handler;

import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.CtScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 方法依赖上提处理器 - 处理依赖方法的抽象上提
 */
public class MethodPullUpHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MethodPullUpHandler.class);
    private final SuperCallHandler superCallHandler;
    private final VisibilityHandler visibilityHandler;
    
    public MethodPullUpHandler() {
        this.superCallHandler = new SuperCallHandler();
        this.visibilityHandler = new VisibilityHandler();
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
      * @param childClass 子类
      * @param parentClass 父类
      * @param allChildClasses 所有子类（用于可见性一致性检查）
      * @return 方法上提结果
      */
     public MethodPullUpResult pullUpDependentMethods(CtMethod<?> method, 
                                                    CtClass<?> childClass, 
                                                    CtClass<?> parentClass,
                                                    List<CtClass<?>> allChildClasses) {
        try {
            logger.debug("开始分析方法 {} 的方法依赖", method.getSimpleName());
            
            // 1. 收集方法中调用的子类方法
            Set<CtMethod<?>> dependentMethods = collectDependentMethods(method, childClass);
            
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
     * 收集方法中调用的子类方法
     */
    private Set<CtMethod<?>> collectDependentMethods(CtMethod<?> method, CtClass<?> childClass) {
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
                
                // 检查是否调用了子类的方法
                if (declaringType.equals(childClass.getReference())) {
                    CtExecutable<?> executable = methodRef.getExecutableDeclaration();
                    if (executable instanceof CtMethod) {
                        CtMethod<?> dependentMethod = (CtMethod<?>) executable;
                        // 排除要上提的方法本身
                        if (!dependentMethod.equals(method) && dependentMethod.getParent() == childClass) {
                            dependentMethods.add(dependentMethod);
                            logger.debug("发现依赖方法: {}", dependentMethod.getSimpleName());
                        }
                    }
                }
            }
        });
        
         return dependentMethods;
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

}
