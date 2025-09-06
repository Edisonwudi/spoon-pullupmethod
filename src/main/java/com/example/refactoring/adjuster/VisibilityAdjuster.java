package com.example.refactoring.adjuster;

import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;

import java.util.Set;

/**
 * 可见性修正器 - 调整方法的可见性以确保上提后仍能被子类访问
 */
public class VisibilityAdjuster {
    
    /**
     * 调整方法的可见性
     * 
     * @param method 要调整的方法
     * @return 可见性调整结果
     */
    public VisibilityAdjustmentResult adjustVisibility(CtMethod<?> method) {
        Set<ModifierKind> modifiers = method.getModifiers();
        ModifierKind originalVisibility = getCurrentVisibility(modifiers);
        ModifierKind requiredVisibility = determineRequiredVisibility(originalVisibility);
        
        if (originalVisibility == requiredVisibility) {
            return VisibilityAdjustmentResult.noChangeNeeded(
                "方法可见性已满足要求: " + originalVisibility
            );
        }
        
        // 移除原有的可见性修饰符
        removeVisibilityModifiers(method);
        
        // 添加新的可见性修饰符
        if (requiredVisibility != ModifierKind.PUBLIC) { // public是默认的，不需要显式添加
            method.addModifier(requiredVisibility);
        }
        
        return VisibilityAdjustmentResult.adjusted(
            "可见性从 " + originalVisibility + " 调整为 " + requiredVisibility,
            originalVisibility,
            requiredVisibility
        );
    }
    
    /**
     * 获取当前方法的可见性
     */
    private ModifierKind getCurrentVisibility(Set<ModifierKind> modifiers) {
        if (modifiers.contains(ModifierKind.PRIVATE)) {
            return ModifierKind.PRIVATE;
        } else if (modifiers.contains(ModifierKind.PROTECTED)) {
            return ModifierKind.PROTECTED;
        } else if (modifiers.contains(ModifierKind.PUBLIC)) {
            return ModifierKind.PUBLIC;
        } else {
            // 包私有（默认可见性）
            return null; // 用null表示包私有
        }
    }
    
    /**
     * 确定上提后需要的可见性级别
     */
    private ModifierKind determineRequiredVisibility(ModifierKind currentVisibility) {
        // 如果是private，需要至少提升到protected以便子类访问
        if (currentVisibility == ModifierKind.PRIVATE) {
            return ModifierKind.PROTECTED;
        }
        
        // 如果是包私有，也需要提升到protected（除非确定在同一包中）
        if (currentVisibility == null) {
            return ModifierKind.PROTECTED;
        }
        
        // protected和public保持不变
        return currentVisibility;
    }
    
    /**
     * 移除所有可见性修饰符
     */
    private void removeVisibilityModifiers(CtMethod<?> method) {
        method.removeModifier(ModifierKind.PRIVATE);
        method.removeModifier(ModifierKind.PROTECTED);
        method.removeModifier(ModifierKind.PUBLIC);
    }
    
    /**
     * 检查方法是否需要调整可见性
     */
    public boolean needsVisibilityAdjustment(CtMethod<?> method) {
        ModifierKind currentVisibility = getCurrentVisibility(method.getModifiers());
        ModifierKind requiredVisibility = determineRequiredVisibility(currentVisibility);
        return currentVisibility != requiredVisibility;
    }
    
    /**
     * 获取建议的可见性级别
     */
    public ModifierKind getSuggestedVisibility(CtMethod<?> method) {
        ModifierKind currentVisibility = getCurrentVisibility(method.getModifiers());
        return determineRequiredVisibility(currentVisibility);
    }
    
    /**
     * 可见性调整结果类
     */
    public static class VisibilityAdjustmentResult {
        private final boolean adjusted;
        private final String message;
        private final ModifierKind originalVisibility;
        private final ModifierKind newVisibility;
        
        private VisibilityAdjustmentResult(boolean adjusted, String message, 
                                         ModifierKind originalVisibility, ModifierKind newVisibility) {
            this.adjusted = adjusted;
            this.message = message;
            this.originalVisibility = originalVisibility;
            this.newVisibility = newVisibility;
        }
        
        public static VisibilityAdjustmentResult adjusted(String message, 
                                                         ModifierKind originalVisibility, 
                                                         ModifierKind newVisibility) {
            return new VisibilityAdjustmentResult(true, message, originalVisibility, newVisibility);
        }
        
        public static VisibilityAdjustmentResult noChangeNeeded(String message) {
            return new VisibilityAdjustmentResult(false, message, null, null);
        }
        
        public boolean wasAdjusted() {
            return adjusted;
        }
        
        public String getMessage() {
            return message;
        }
        
        public ModifierKind getOriginalVisibility() {
            return originalVisibility;
        }
        
        public ModifierKind getNewVisibility() {
            return newVisibility;
        }
        
        @Override
        public String toString() {
            return "VisibilityAdjustmentResult{" +
                   "adjusted=" + adjusted +
                   ", message='" + message + '\'' +
                   ", originalVisibility=" + originalVisibility +
                   ", newVisibility=" + newVisibility +
                   '}';
        }
    }
}
