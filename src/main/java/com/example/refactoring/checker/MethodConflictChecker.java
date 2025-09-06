package com.example.refactoring.checker;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;
import java.util.Optional;

/**
 * 方法冲突检查器 - 检查父类中是否已存在同名方法或签名冲突
 */
public class MethodConflictChecker {
    
    /**
     * 检查方法是否可以安全地上提到父类
     * 
     * @param method 要上提的方法
     * @param childClass 子类
     * @param parentClass 父类
     * @return 冲突检查结果
     */
    public ConflictCheckResult checkConflict(CtMethod<?> method, CtClass<?> childClass, CtClass<?> parentClass) {
        if (parentClass == null) {
            return ConflictCheckResult.failure("父类不存在");
        }
        
        // 检查父类中是否已存在相同签名的方法
        Optional<CtMethod<?>> existingMethod = findMethodWithSameSignature(method, parentClass);
        if (existingMethod.isPresent()) {
            CtMethod<?> existing = existingMethod.get();
            
            // 检查方法体是否完全相同
            if (areMethodBodiesIdentical(method, existing)) {
                return ConflictCheckResult.duplicate("父类中已存在相同的方法: " + method.getSimpleName());
            } else {
                return ConflictCheckResult.conflict("父类中存在同名但实现不同的方法: " + method.getSimpleName());
            }
        }
        
        // 检查是否存在方法名相同但参数不同的重载方法
        List<CtMethod<?>> overloadedMethods = findMethodsWithSameName(method.getSimpleName(), parentClass);
        if (!overloadedMethods.isEmpty()) {
            // 存在重载方法，需要检查是否会造成歧义
            ConflictCheckResult overloadResult = checkOverloadConflict(method, overloadedMethods);
            if (!overloadResult.isSuccess()) {
                return overloadResult;
            }
        }
        
        return ConflictCheckResult.success("方法可以安全上提");
    }
    
    /**
     * 在指定类中查找具有相同签名的方法
     */
    private Optional<CtMethod<?>> findMethodWithSameSignature(CtMethod<?> method, CtClass<?> targetClass) {
        return targetClass.getMethods().stream()
            .filter(m -> hasSameSignature(method, m))
            .findFirst();
    }
    
    /**
     * 在指定类中查找具有相同名称的所有方法
     */
    private List<CtMethod<?>> findMethodsWithSameName(String methodName, CtClass<?> targetClass) {
        return targetClass.getMethods().stream()
            .filter(m -> m.getSimpleName().equals(methodName))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 检查两个方法是否具有相同的签名
     */
    private boolean hasSameSignature(CtMethod<?> method1, CtMethod<?> method2) {
        // 检查方法名
        if (!method1.getSimpleName().equals(method2.getSimpleName())) {
            return false;
        }
        
        // 检查参数列表
        List<CtParameter<?>> params1 = method1.getParameters();
        List<CtParameter<?>> params2 = method2.getParameters();
        
        if (params1.size() != params2.size()) {
            return false;
        }
        
        for (int i = 0; i < params1.size(); i++) {
            CtTypeReference<?> type1 = params1.get(i).getType();
            CtTypeReference<?> type2 = params2.get(i).getType();
            
            if (!type1.equals(type2)) {
                return false;
            }
        }
        
        // 检查返回类型
        return method1.getType().equals(method2.getType());
    }
    
    /**
     * 检查两个方法的方法体是否相同
     */
    private boolean areMethodBodiesIdentical(CtMethod<?> method1, CtMethod<?> method2) {
        if (method1.getBody() == null && method2.getBody() == null) {
            return true;
        }
        
        if (method1.getBody() == null || method2.getBody() == null) {
            return false;
        }
        
        // 比较方法体的字符串表示（简化处理）
        String body1 = method1.getBody().toString().replaceAll("\\s+", " ").trim();
        String body2 = method2.getBody().toString().replaceAll("\\s+", " ").trim();
        
        return body1.equals(body2);
    }
    
    /**
     * 检查重载方法是否会产生冲突
     */
    private ConflictCheckResult checkOverloadConflict(CtMethod<?> method, List<CtMethod<?>> existingMethods) {
        for (CtMethod<?> existing : existingMethods) {
            // 检查是否会产生参数类型转换的歧义
            if (couldCauseAmbiguity(method, existing)) {
                return ConflictCheckResult.conflict(
                    "方法重载可能导致调用歧义: " + method.getSimpleName() + 
                    " 与现有方法 " + existing.getSignature()
                );
            }
        }
        
        return ConflictCheckResult.success("重载方法检查通过");
    }
    
    /**
     * 检查两个重载方法是否可能导致调用歧义
     */
    private boolean couldCauseAmbiguity(CtMethod<?> method1, CtMethod<?> method2) {
        List<CtParameter<?>> params1 = method1.getParameters();
        List<CtParameter<?>> params2 = method2.getParameters();
        
        // 如果参数数量不同，通常不会有歧义
        if (params1.size() != params2.size()) {
            return false;
        }
        
        // 检查参数类型是否可能导致自动类型转换的歧义
        for (int i = 0; i < params1.size(); i++) {
            CtTypeReference<?> type1 = params1.get(i).getType();
            CtTypeReference<?> type2 = params2.get(i).getType();
            
            // 如果参数类型存在继承关系，可能导致歧义
            if (areRelatedTypes(type1, type2)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查两个类型是否存在继承关系
     */
    private boolean areRelatedTypes(CtTypeReference<?> type1, CtTypeReference<?> type2) {
        if (type1.equals(type2)) {
            return true;
        }
        
        try {
            // 检查是否存在子类关系（简化处理）
            return type1.isSubtypeOf(type2) || type2.isSubtypeOf(type1);
        } catch (Exception e) {
            // 类型检查失败，保守处理
            return false;
        }
    }
    
    /**
     * 冲突检查结果类
     */
    public static class ConflictCheckResult {
        private final boolean success;
        private final String message;
        private final ConflictType conflictType;
        
        private ConflictCheckResult(boolean success, String message, ConflictType conflictType) {
            this.success = success;
            this.message = message;
            this.conflictType = conflictType;
        }
        
        public static ConflictCheckResult success(String message) {
            return new ConflictCheckResult(true, message, ConflictType.NONE);
        }
        
        public static ConflictCheckResult failure(String message) {
            return new ConflictCheckResult(false, message, ConflictType.ERROR);
        }
        
        public static ConflictCheckResult conflict(String message) {
            return new ConflictCheckResult(false, message, ConflictType.CONFLICT);
        }
        
        public static ConflictCheckResult duplicate(String message) {
            return new ConflictCheckResult(false, message, ConflictType.DUPLICATE);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public ConflictType getConflictType() {
            return conflictType;
        }
        
        @Override
        public String toString() {
            return "ConflictCheckResult{" +
                   "success=" + success +
                   ", message='" + message + '\'' +
                   ", conflictType=" + conflictType +
                   '}';
        }
    }
    
    /**
     * 冲突类型枚举
     */
    public enum ConflictType {
        NONE,       // 无冲突
        CONFLICT,   // 存在冲突
        DUPLICATE,  // 重复方法
        ERROR       // 检查错误
    }
}
