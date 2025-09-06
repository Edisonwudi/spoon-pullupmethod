package com.example.refactoring.handler;

import spoon.reflect.declaration.*;
import spoon.reflect.code.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.CtScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Super调用处理器 - 处理子类方法中的super调用冲突
 */
public class SuperCallHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(SuperCallHandler.class);
    
    /**
     * Super调用处理结果类
     */
    public static class SuperCallHandlingResult {
        private final boolean success;
        private final String message;
        private final List<String> handledCalls;
        private final List<String> warnings;
        
        private SuperCallHandlingResult(boolean success, String message, 
                                      List<String> handledCalls, List<String> warnings) {
            this.success = success;
            this.message = message;
            this.handledCalls = handledCalls != null ? handledCalls : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }
        
        public static SuperCallHandlingResult success(String message, List<String> handledCalls) {
            return new SuperCallHandlingResult(true, message, handledCalls, new ArrayList<>());
        }
        
        public static SuperCallHandlingResult failure(String message) {
            return new SuperCallHandlingResult(false, message, new ArrayList<>(), new ArrayList<>());
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<String> getHandledCalls() { return new ArrayList<>(handledCalls); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        
        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
    }
    
    /**
     * 处理子类方法中的super调用冲突
     * 
     * @param childMethod 子类方法
     * @param abstractMethods 已上提的抽象方法列表
     * @param parentClass 父类
     * @return 处理结果
     */
    public SuperCallHandlingResult handleSuperCalls(CtMethod<?> childMethod, 
                                                   List<CtMethod<?>> abstractMethods,
                                                   CtClass<?> parentClass) {
        try {
            logger.debug("开始处理方法 {} 中的super调用", childMethod.getSimpleName());
            
            // 1. 收集方法中的super调用
            List<SuperCallInfo> superCalls = collectSuperCalls(childMethod, abstractMethods);
            
            if (superCalls.isEmpty()) {
                return SuperCallHandlingResult.success("方法中无需要处理的super调用", new ArrayList<>());
            }
            
            logger.info("发现 {} 个需要处理的super调用", superCalls.size());
            
            // 2. 处理每个super调用
            List<String> handledCalls = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            for (SuperCallInfo callInfo : superCalls) {
                try {
                    boolean handled = handleSingleSuperCall(callInfo, parentClass);
                    if (handled) {
                        handledCalls.add(callInfo.methodName);
                        logger.info("成功处理super调用: {}", callInfo.methodName);
                    } else {
                        warnings.add("无法处理super调用: " + callInfo.methodName);
                    }
                } catch (Exception e) {
                    String warning = "处理super调用失败: " + callInfo.methodName + " - " + e.getMessage();
                    warnings.add(warning);
                    logger.warn(warning, e);
                }
            }
            
            SuperCallHandlingResult result = SuperCallHandlingResult.success(
                "成功处理 " + handledCalls.size() + " 个super调用", handledCalls);
            warnings.forEach(result::addWarning);
            
            return result;
            
        } catch (Exception e) {
            logger.error("处理super调用过程中发生异常", e);
            return SuperCallHandlingResult.failure("super调用处理失败: " + e.getMessage());
        }
    }
    
    /**
     * Super调用信息类
     */
    private static class SuperCallInfo {
        final CtInvocation<?> invocation;
        final String methodName;
        final List<CtExpression<?>> arguments;
        
        SuperCallInfo(CtInvocation<?> invocation, String methodName, List<CtExpression<?>> arguments) {
            this.invocation = invocation;
            this.methodName = methodName;
            this.arguments = arguments;
        }
    }
    
    /**
     * 收集方法中的super调用
     */
    private List<SuperCallInfo> collectSuperCalls(CtMethod<?> method, List<CtMethod<?>> abstractMethods) {
        List<SuperCallInfo> superCalls = new ArrayList<>();
        Set<String> abstractMethodNames = abstractMethods.stream()
            .map(CtMethod::getSimpleName)
            .collect(java.util.stream.Collectors.toSet());
        
        method.accept(new CtScanner() {
            @Override
            public <T> void visitCtInvocation(CtInvocation<T> invocation) {
                if (isSuperCall(invocation) && 
                    abstractMethodNames.contains(invocation.getExecutable().getSimpleName())) {
                    
                    SuperCallInfo callInfo = new SuperCallInfo(
                        invocation,
                        invocation.getExecutable().getSimpleName(),
                        new ArrayList<>(invocation.getArguments())
                    );
                    superCalls.add(callInfo);
                    logger.debug("发现有问题的super调用: {}", callInfo.methodName);
                }
                super.visitCtInvocation(invocation);
            }
        });
        
        return superCalls;
    }
    
    /**
     * 检查是否为super调用
     */
    private boolean isSuperCall(CtInvocation<?> invocation) {
        CtExpression<?> target = invocation.getTarget();
        return target instanceof CtSuperAccess;
    }
    
    /**
     * 处理单个super调用
     */
    private boolean handleSingleSuperCall(SuperCallInfo callInfo, CtClass<?> parentClass) {
        try {
            // 策略1: 查找祖先类中的具体实现
            CtMethod<?> ancestorMethod = findConcreteMethodInAncestors(callInfo.methodName, parentClass, callInfo.invocation);
            
            if (ancestorMethod != null) {
                // 找到了祖先类中的具体实现，在父类中创建默认实现
                return createDefaultImplementationInParent(callInfo, ancestorMethod, parentClass);
            } else {
                // 策略2: 移除super调用（如果方法体允许）
                logger.warn("未找到祖先类实现，将移除super调用: {}", callInfo.methodName);
                return removeSuperCall(callInfo);
            }
            
        } catch (Exception e) {
            logger.error("处理super调用 {} 失败", callInfo.methodName, e);
            return false;
        }
    }
    
    /**
     * 在祖先类中查找具体方法实现
     */
    private CtMethod<?> findConcreteMethodInAncestors(String methodName, CtClass<?> startClass, CtInvocation<?> originalCall) {
        CtClass<?> currentClass = startClass;
        
        // 从父类的父类开始查找（跳过直接父类，因为它现在有抽象方法）
        if (currentClass.getSuperclass() != null) {
            try {
                CtType<?> superType = currentClass.getSuperclass().getTypeDeclaration();
                if (superType instanceof CtClass) {
                    currentClass = (CtClass<?>) superType;
                    
                    // 继续向上查找
                    while (currentClass != null) {
                        for (CtMethod<?> method : currentClass.getMethods()) {
                            if (method.getSimpleName().equals(methodName) &&
                                !method.hasModifier(ModifierKind.ABSTRACT) &&
                                hasSameSignature(method, originalCall)) {
                                
                                logger.debug("在祖先类 {} 中找到具体实现: {}", 
                                           currentClass.getSimpleName(), methodName);
                                return method;
                            }
                        }
                        
                        // 继续向上查找
                        if (currentClass.getSuperclass() != null) {
                            CtType<?> nextSuper = currentClass.getSuperclass().getTypeDeclaration();
                            currentClass = (nextSuper instanceof CtClass) ? (CtClass<?>) nextSuper : null;
                        } else {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("查找祖先类方法时发生异常: {}", e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * 检查方法签名是否匹配
     */
    private boolean hasSameSignature(CtMethod<?> method, CtInvocation<?> invocation) {
        List<CtParameter<?>> methodParams = method.getParameters();
        List<CtExpression<?>> callArgs = invocation.getArguments();
        
        if (methodParams.size() != callArgs.size()) {
            return false;
        }
        
        // 简化的签名匹配，实际应用中可能需要更复杂的类型检查
        return true;
    }
    
    /**
     * 在父类中创建默认实现，调用祖先类的具体实现
     */
    private boolean createDefaultImplementationInParent(SuperCallInfo callInfo, CtMethod<?> ancestorMethod, CtClass<?> parentClass) {
        try {
            String methodName = callInfo.methodName;
            logger.info("在父类 {} 中为方法 {} 创建默认实现", parentClass.getSimpleName(), methodName);
            
            // 1. 查找父类中是否已经有这个抽象方法
            CtMethod<?> abstractMethodInParent = findMethodInClass(parentClass, methodName, callInfo.invocation);
            if (abstractMethodInParent == null) {
                logger.warn("未在父类中找到抽象方法: {}", methodName);
                return false;
            }
            
            // 2. 将抽象方法转换为具体方法，提供默认实现
            abstractMethodInParent.removeModifier(ModifierKind.ABSTRACT);
            
            // 3. 创建方法体，调用祖先类的实现
            CtBlock<?> methodBody = createDefaultMethodBody(abstractMethodInParent, ancestorMethod);
            abstractMethodInParent.setBody(methodBody);
            
            // 4. 如果父类现在没有其他抽象方法，移除abstract修饰符
            updateParentClassAbstractStatus(parentClass);
            
            logger.info("成功在父类中创建默认实现: {}", methodName);
            return true;
            
        } catch (Exception e) {
            logger.error("创建默认实现失败", e);
            return false;
        }
    }
    
    /**
     * 在指定类中查找方法
     */
    private CtMethod<?> findMethodInClass(CtClass<?> clazz, String methodName, CtInvocation<?> originalCall) {
        for (CtMethod<?> method : clazz.getMethods()) {
            if (method.getSimpleName().equals(methodName) && 
                hasSameSignature(method, originalCall)) {
                return method;
            }
        }
        return null;
    }
    
    /**
     * 创建默认方法体，调用祖先类实现
     */
    private CtBlock<?> createDefaultMethodBody(CtMethod<?> method, CtMethod<?> ancestorMethod) {
        spoon.reflect.factory.Factory factory = method.getFactory();
        
        try {
            // 创建简单的方法体：只调用 super.methodName()
            String methodName = ancestorMethod.getSimpleName();
            String paramList = method.getParameters().stream()
                .map(param -> param.getSimpleName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
            
            String methodBodyCode;
            if (!method.getType().equals(factory.Type().voidPrimitiveType())) {
                methodBodyCode = "{ return super." + methodName + "(" + paramList + "); }";
            } else {
                methodBodyCode = "{ super." + methodName + "(" + paramList + "); }";
            }
            
            logger.debug("创建方法体代码: {}", methodBodyCode);
            
            // 使用代码片段创建整个方法体块
            spoon.reflect.code.CtCodeSnippetStatement snippet = factory.Code().createCodeSnippetStatement(methodBodyCode);
            CtBlock<?> body = factory.Core().createBlock();
            body.addStatement(snippet);
            
            if (body != null) {
                logger.info("成功创建方法体，包含super调用");
                return body;
            } else {
                logger.warn("代码片段创建失败，使用手动创建的空方法体");
            }
            
        } catch (Exception e) {
            logger.warn("创建方法体失败: {}", e.getMessage());
        }
        
        // 如果上面的方法失败，创建一个包含注释的空方法体
        CtBlock<?> body = factory.Core().createBlock();
        try {
            String comment = "// TODO: 手动添加 super." + ancestorMethod.getSimpleName() + "() 调用";
            body.addComment(factory.createComment(comment, spoon.reflect.code.CtComment.CommentType.INLINE));
        } catch (Exception e) {
            logger.debug("添加注释失败: {}", e.getMessage());
        }
        
        return body;
    }
    
    /**
     * 更新父类的abstract状态
     */
    private void updateParentClassAbstractStatus(CtClass<?> parentClass) {
        // 检查是否还有其他抽象方法
        boolean hasAbstractMethods = parentClass.getMethods().stream()
            .anyMatch(method -> method.hasModifier(ModifierKind.ABSTRACT));
        
        if (!hasAbstractMethods && parentClass.hasModifier(ModifierKind.ABSTRACT)) {
            parentClass.removeModifier(ModifierKind.ABSTRACT);
            logger.info("父类 {} 已移除abstract修饰符，因为不再有抽象方法", parentClass.getSimpleName());
        }
    }
    
    /**
     * 重定向super调用到祖先类（已弃用，保留以备后用）
     */
    private boolean redirectSuperCall(SuperCallInfo callInfo, CtMethod<?> ancestorMethod) {
        try {
            // 这是一个复杂的操作，需要创建新的调用表达式
            // 对于现在的实现，我们采用更简单的策略：移除super调用并添加注释
            
            CtInvocation<?> invocation = callInfo.invocation;
            CtStatement parentStatement = getParentStatement(invocation);
            
            if (parentStatement != null) {
                // 创建注释说明原来的super调用
                String comment = "// 原 super." + callInfo.methodName + "() 调用已移除，" +
                               "因为方法已上提为抽象方法。如需调用祖先类实现，请手动处理。";
                
                // 在语句前添加注释
                parentStatement.addComment(parentStatement.getFactory().createComment(
                    comment, spoon.reflect.code.CtComment.CommentType.INLINE));
                
                // 尝试移除包含super调用的语句
                try {
                    parentStatement.delete();
                    logger.info("移除了包含super调用的语句: {}", callInfo.methodName);
                    return true;
                } catch (Exception e) {
                    logger.debug("无法删除语句，可能是复杂表达式: {}", e.getMessage());
                }
            }
            
            logger.warn("无法完全处理super调用: {}", callInfo.methodName);
            return false;
            
        } catch (Exception e) {
            logger.error("重定向super调用失败", e);
            return false;
        }
    }
    
    /**
     * 移除super调用
     */
    private boolean removeSuperCall(SuperCallInfo callInfo) {
        try {
            CtInvocation<?> invocation = callInfo.invocation;
            CtStatement parentStatement = getParentStatement(invocation);
            
            if (parentStatement != null) {
                // 创建注释说明
                String comment = "// 原 super." + callInfo.methodName + "() 调用已移除，因为方法已上提为抽象方法";
                
                parentStatement.addComment(parentStatement.getFactory().createComment(
                    comment, spoon.reflect.code.CtComment.CommentType.INLINE));
                
                // 尝试移除包含super调用的语句
                try {
                    parentStatement.delete();
                    logger.info("移除了super调用语句: {}", callInfo.methodName);
                    return true;
                } catch (Exception e) {
                    logger.debug("无法删除语句，可能是复杂表达式: {}", e.getMessage());
                }
                
                logger.warn("super调用嵌套在复杂表达式中，无法自动移除: {}", callInfo.methodName);
                return false;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("移除super调用失败", e);
            return false;
        }
    }
    
    /**
     * 获取包含表达式的父语句
     */
    private CtStatement getParentStatement(CtElement element) {
        CtElement current = element;
        while (current != null) {
            if (current instanceof CtStatement) {
                return (CtStatement) current;
            }
            current = current.getParent();
        }
        return null;
    }
}
