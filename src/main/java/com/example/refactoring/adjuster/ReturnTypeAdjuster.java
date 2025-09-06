package com.example.refactoring.adjuster;

import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtLocalVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 返回类型调整器 - 解决 Pull-Up 方法时的返回类型冲突问题
 */
public class ReturnTypeAdjuster {
    
    private static final Logger logger = LoggerFactory.getLogger(ReturnTypeAdjuster.class);
    
    /**
     * 返回类型调整结果类
     */
    public static class ReturnTypeAdjustmentResult {
        private final boolean wasAdjusted;
        private final String message;
        private final CtTypeReference<?> originalReturnType;
        private final CtTypeReference<?> adjustedReturnType;
        
        private ReturnTypeAdjustmentResult(boolean wasAdjusted, String message,
                                         CtTypeReference<?> originalReturnType,
                                         CtTypeReference<?> adjustedReturnType) {
            this.wasAdjusted = wasAdjusted;
            this.message = message;
            this.originalReturnType = originalReturnType;
            this.adjustedReturnType = adjustedReturnType;
        }
        
        public static ReturnTypeAdjustmentResult noAdjustment(String message) {
            return new ReturnTypeAdjustmentResult(false, message, null, null);
        }
        
        public static ReturnTypeAdjustmentResult adjusted(String message, 
                                                        CtTypeReference<?> originalType,
                                                        CtTypeReference<?> adjustedType) {
            return new ReturnTypeAdjustmentResult(true, message, originalType, adjustedType);
        }
        
        public boolean wasAdjusted() { return wasAdjusted; }
        public String getMessage() { return message; }
        public CtTypeReference<?> getOriginalReturnType() { return originalReturnType; }
        public CtTypeReference<?> getAdjustedReturnType() { return adjustedReturnType; }
    }
    
    /**
     * 为上提的方法调整返回类型以避免与其他子类方法冲突
     * 
     * @param method 要上提的方法
     * @param childClass 原子类
     * @param parentClass 目标父类
     * @return 调整结果
     */
    public ReturnTypeAdjustmentResult adjustReturnTypeForPullUp(CtMethod<?> method, 
                                                               CtClass<?> childClass, 
                                                               CtClass<?> parentClass) {
        try {
            logger.debug("检查方法 {} 的返回类型是否需要调整", method.getSimpleName());
            
            CtTypeReference<?> currentReturnType = method.getType();
            if (currentReturnType == null || currentReturnType.isPrimitive()) {
                return ReturnTypeAdjustmentResult.noAdjustment("方法返回类型为基础类型，无需调整");
            }
            
            // 1. 收集父类的所有子类
            List<CtClass<?>> siblingClasses = findSiblingClasses(parentClass, childClass);
            if (siblingClasses.isEmpty()) {
                return ReturnTypeAdjustmentResult.noAdjustment("未找到其他子类，无需调整返回类型");
            }
            
            // 2. 检查是否存在同名方法的返回类型冲突
            List<CtMethod<?>> conflictingMethods = findConflictingMethods(method, siblingClasses);
            if (conflictingMethods.isEmpty()) {
                return ReturnTypeAdjustmentResult.noAdjustment("未发现返回类型冲突");
            }
            
            // 3. 计算兼容的返回类型
            CtTypeReference<?> compatibleReturnType = findCompatibleReturnType(
                method, conflictingMethods, parentClass);
            
            if (compatibleReturnType == null) {
                return ReturnTypeAdjustmentResult.noAdjustment("无法找到兼容的返回类型");
            }
            
            // 4. 调整返回类型
            CtTypeReference<?> originalType = currentReturnType;
            method.setType(compatibleReturnType);
            
            // 5. 调整方法体中的返回语句
            adjustReturnStatements(method, originalType, compatibleReturnType);
            
            String message = String.format("返回类型从 %s 调整为 %s", 
                                         originalType.getSimpleName(), 
                                         compatibleReturnType.getSimpleName());
            
            logger.info("方法 {} 的返回类型已调整: {}", method.getSimpleName(), message);
            return ReturnTypeAdjustmentResult.adjusted(message, originalType, compatibleReturnType);
            
        } catch (Exception e) {
            logger.error("调整返回类型时发生异常", e);
            return ReturnTypeAdjustmentResult.noAdjustment("调整失败: " + e.getMessage());
        }
    }
    
    /**
     * 查找父类的其他子类（兄弟类）
     */
    private List<CtClass<?>> findSiblingClasses(CtClass<?> parentClass, CtClass<?> excludeChild) {
        List<CtClass<?>> siblings = new ArrayList<>();
        
        try {
            // 在同一个模型中查找所有继承自父类的子类
            parentClass.getFactory().getModel().getAllTypes().forEach(type -> {
                if (type instanceof CtClass && type != excludeChild) {
                    CtClass<?> clazz = (CtClass<?>) type;
                    if (isSubclassOf(clazz, parentClass)) {
                        siblings.add(clazz);
                        logger.debug("找到兄弟类: {}", clazz.getQualifiedName());
                    }
                }
            });
        } catch (Exception e) {
            logger.warn("查找兄弟类时发生异常", e);
        }
        
        return siblings;
    }
    
    /**
     * 检查是否为子类关系
     */
    private boolean isSubclassOf(CtClass<?> childClass, CtClass<?> parentClass) {
        try {
            CtTypeReference<?> superClass = childClass.getSuperclass();
            while (superClass != null) {
                if (superClass.equals(parentClass.getReference())) {
                    return true;
                }
                CtType<?> superType = superClass.getTypeDeclaration();
                if (superType instanceof CtClass) {
                    superClass = ((CtClass<?>) superType).getSuperclass();
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            logger.debug("检查继承关系时发生异常: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 查找具有冲突返回类型的同名方法
     */
    private List<CtMethod<?>> findConflictingMethods(CtMethod<?> targetMethod, List<CtClass<?>> siblingClasses) {
        List<CtMethod<?>> conflictingMethods = new ArrayList<>();
        String methodName = targetMethod.getSimpleName();
        
        for (CtClass<?> siblingClass : siblingClasses) {
            for (CtMethod<?> method : siblingClass.getMethods()) {
                if (method.getSimpleName().equals(methodName) &&
                    hasSameParameters(method, targetMethod) &&
                    hasConflictingReturnType(method, targetMethod)) {
                    
                    conflictingMethods.add(method);
                    logger.debug("发现冲突方法: {} 在类 {}, 返回类型: {}", 
                               methodName, siblingClass.getSimpleName(), method.getType());
                }
            }
        }
        
        return conflictingMethods;
    }
    
    /**
     * 检查两个方法是否有相同的参数列表
     */
    private boolean hasSameParameters(CtMethod<?> method1, CtMethod<?> method2) {
        List<CtParameter<?>> params1 = method1.getParameters();
        List<CtParameter<?>> params2 = method2.getParameters();
        
        if (params1.size() != params2.size()) {
            return false;
        }
        
        for (int i = 0; i < params1.size(); i++) {
            if (!params1.get(i).getType().equals(params2.get(i).getType())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查两个方法是否有冲突的返回类型
     */
    private boolean hasConflictingReturnType(CtMethod<?> method1, CtMethod<?> method2) {
        CtTypeReference<?> type1 = method1.getType();
        CtTypeReference<?> type2 = method2.getType();
        
        if (type1 == null || type2 == null) {
            return false;
        }
        
        // 如果返回类型相同，则无冲突
        if (type1.equals(type2)) {
            return false;
        }
        
        // 如果其中一个是另一个的子类，则无冲突（协变返回类型）
        if (isSubtypeOf(type1, type2) || isSubtypeOf(type2, type1)) {
            return false;
        }
        
        // 否则存在冲突
        return true;
    }
    
    /**
     * 检查类型1是否是类型2的子类型
     */
    private boolean isSubtypeOf(CtTypeReference<?> subType, CtTypeReference<?> superType) {
        try {
            CtType<?> subTypeDecl = subType.getTypeDeclaration();
            CtType<?> superTypeDecl = superType.getTypeDeclaration();
            
            if (subTypeDecl instanceof CtClass && superTypeDecl instanceof CtClass) {
                return isSubclassOf((CtClass<?>) subTypeDecl, (CtClass<?>) superTypeDecl);
            }
        } catch (Exception e) {
            logger.debug("检查子类型关系时发生异常: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 找到兼容所有冲突方法的返回类型
     */
    private CtTypeReference<?> findCompatibleReturnType(CtMethod<?> targetMethod, 
                                                       List<CtMethod<?>> conflictingMethods,
                                                       CtClass<?> parentClass) {
        
        // 收集所有返回类型
        Set<CtTypeReference<?>> returnTypes = new HashSet<>();
        returnTypes.add(targetMethod.getType());
        conflictingMethods.forEach(method -> returnTypes.add(method.getType()));
        
        logger.debug("需要兼容的返回类型: {}", 
                   returnTypes.stream().map(t -> t.getSimpleName()).toArray());
        
        // 1. 尝试找到共同的父类
        CtTypeReference<?> commonSuperType = findCommonSuperType(returnTypes, parentClass.getFactory());
        if (commonSuperType != null) {
            logger.debug("找到共同父类型: {}", commonSuperType.getSimpleName());
            return commonSuperType;
        }
        
        // 2. 如果没有找到合适的父类，尝试使用 Object 类型
        logger.debug("未找到合适的共同父类型，使用 Object");
        return parentClass.getFactory().Type().objectType();
    }
    
    /**
     * 查找多个类型的共同父类型
     */
    private CtTypeReference<?> findCommonSuperType(Set<CtTypeReference<?>> types, 
                                                  spoon.reflect.factory.Factory factory) {
        
        if (types.isEmpty()) {
            return null;
        }
        
        if (types.size() == 1) {
            return types.iterator().next();
        }
        
        // 获取第一个类型的所有父类型
        CtTypeReference<?> firstType = types.iterator().next();
        Set<CtTypeReference<?>> commonAncestors = getAllSuperTypes(firstType);
        
        // 与其他类型的父类型求交集
        for (CtTypeReference<?> type : types) {
            if (type != firstType) {
                Set<CtTypeReference<?>> superTypes = getAllSuperTypes(type);
                commonAncestors.retainAll(superTypes);
            }
        }
        
        // 返回最具体的共同父类型（排除 Object）
        return commonAncestors.stream()
            .filter(type -> !type.equals(factory.Type().objectType()))
            .findFirst()
            .orElse(factory.Type().objectType());
    }
    
    /**
     * 获取类型的所有父类型
     */
    private Set<CtTypeReference<?>> getAllSuperTypes(CtTypeReference<?> type) {
        Set<CtTypeReference<?>> superTypes = new HashSet<>();
        
        try {
            CtType<?> typeDecl = type.getTypeDeclaration();
            if (typeDecl instanceof CtClass) {
                CtClass<?> clazz = (CtClass<?>) typeDecl;
                
                // 添加父类
                CtTypeReference<?> superClass = clazz.getSuperclass();
                while (superClass != null) {
                    superTypes.add(superClass);
                    CtType<?> superDecl = superClass.getTypeDeclaration();
                    if (superDecl instanceof CtClass) {
                        superClass = ((CtClass<?>) superDecl).getSuperclass();
                    } else {
                        break;
                    }
                }
                
                // 添加接口
                clazz.getSuperInterfaces().forEach(superTypes::add);
            }
        } catch (Exception e) {
            logger.debug("获取父类型时发生异常: {}", e.getMessage());
        }
        
        return superTypes;
    }
    
    /**
     * 调整方法体中的返回语句以匹配新的返回类型
     */
    private void adjustReturnStatements(CtMethod<?> method, 
                                      CtTypeReference<?> originalType, 
                                      CtTypeReference<?> newType) {
        
        if (originalType.equals(newType)) {
            return; // 类型相同，无需调整
        }
        
        method.accept(new spoon.reflect.visitor.CtScanner() {
            @Override
            public <T> void visitCtReturn(spoon.reflect.code.CtReturn<T> returnStatement) {
                super.visitCtReturn(returnStatement);
                logger.debug("检查返回语句，从 {} 到 {}", originalType.getSimpleName(), newType.getSimpleName());
            }
            
            @Override
            public <T> void visitCtLocalVariable(CtLocalVariable<T> localVariable) {
                super.visitCtLocalVariable(localVariable);
                
                // 调整局部变量的类型声明（如 EllipseShape that -> BaseShape that）
                if (localVariable.getType().equals(originalType)) {
                    localVariable.setType(newType);
                    logger.debug("调整局部变量类型: {} -> {}", originalType.getSimpleName(), newType.getSimpleName());
                }
            }
            
            // 注意：类型转换的具体调整可能需要根据实际的 Spoon API 版本进行调整
            // 当前版本重点处理局部变量类型声明的调整
        });
    }
}
