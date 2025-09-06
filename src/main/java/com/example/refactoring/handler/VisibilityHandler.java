package com.example.refactoring.handler;

import spoon.reflect.declaration.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 可见性处理器 - 确保方法上提后的可见性一致性
 */
public class VisibilityHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(VisibilityHandler.class);
    
    /**
     * 可见性调整结果
     */
    public static class VisibilityAdjustmentResult {
        private final boolean success;
        private final String message;
        private final List<String> adjustments;
        private final List<String> warnings;
        
        private VisibilityAdjustmentResult(boolean success, String message, 
                                         List<String> adjustments, List<String> warnings) {
            this.success = success;
            this.message = message;
            this.adjustments = adjustments != null ? adjustments : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }
        
        public static VisibilityAdjustmentResult success(String message, List<String> adjustments) {
            return new VisibilityAdjustmentResult(true, message, adjustments, new ArrayList<>());
        }
        
        public static VisibilityAdjustmentResult failure(String message) {
            return new VisibilityAdjustmentResult(false, message, new ArrayList<>(), new ArrayList<>());
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<String> getAdjustments() { return new ArrayList<>(adjustments); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        
        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
    }
    
    /**
     * 调整方法的可见性以保持一致性
     * 
     * @param abstractMethod 父类中的抽象方法
     * @param childMethods 所有子类中的对应方法
     * @return 调整结果
     */
    public VisibilityAdjustmentResult adjustMethodVisibility(CtMethod<?> abstractMethod, 
                                                           List<CtMethod<?>> childMethods) {
        try {
            logger.debug("开始调整方法 {} 的可见性一致性", abstractMethod.getSimpleName());
            
            // 1. 确定最合适的可见性级别
            ModifierKind targetVisibility = determineTargetVisibility(abstractMethod, childMethods);
            
            List<String> adjustments = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            // 2. 调整父类方法的可见性
            ModifierKind parentCurrentVisibility = getCurrentVisibility(abstractMethod);
            if (parentCurrentVisibility != targetVisibility) {
                adjustMethodVisibility(abstractMethod, targetVisibility);
                adjustments.add("父类方法 " + abstractMethod.getSimpleName() + " 可见性调整为 " + targetVisibility);
                logger.info("父类方法 {} 可见性从 {} 调整为 {}", 
                           abstractMethod.getSimpleName(), parentCurrentVisibility, targetVisibility);
            }
            
            // 3. 调整所有子类方法的可见性
            for (CtMethod<?> childMethod : childMethods) {
                ModifierKind childCurrentVisibility = getCurrentVisibility(childMethod);
                if (childCurrentVisibility != targetVisibility) {
                    adjustMethodVisibility(childMethod, targetVisibility);
                    adjustments.add("子类方法 " + childMethod.getSimpleName() + " 可见性调整为 " + targetVisibility);
                    logger.info("子类方法 {} 可见性从 {} 调整为 {}", 
                               childMethod.getSimpleName(), childCurrentVisibility, targetVisibility);
                }
            }
            
            VisibilityAdjustmentResult result = VisibilityAdjustmentResult.success(
                "成功调整 " + (childMethods.size() + 1) + " 个方法的可见性", adjustments);
            warnings.forEach(result::addWarning);
            
            return result;
            
        } catch (Exception e) {
            logger.error("调整方法可见性时发生异常", e);
            return VisibilityAdjustmentResult.failure("可见性调整失败: " + e.getMessage());
        }
    }
    
    /**
     * 确定目标可见性级别
     * 规则：选择最宽的可见性，确保不会降低任何现有方法的可见性
     */
    private ModifierKind determineTargetVisibility(CtMethod<?> abstractMethod, List<CtMethod<?>> childMethods) {
        // 收集所有方法的当前可见性
        List<ModifierKind> visibilities = new ArrayList<>();
        visibilities.add(getCurrentVisibility(abstractMethod));
        
        for (CtMethod<?> childMethod : childMethods) {
            visibilities.add(getCurrentVisibility(childMethod));
        }
        
        // 选择最宽的可见性
        if (visibilities.contains(ModifierKind.PUBLIC)) {
            return ModifierKind.PUBLIC;
        } else if (visibilities.contains(ModifierKind.PROTECTED)) {
            return ModifierKind.PROTECTED;
        } else {
            // 如果都是package-private或private，选择protected作为默认
            return ModifierKind.PROTECTED;
        }
    }
    
    /**
     * 获取方法的当前可见性
     */
    private ModifierKind getCurrentVisibility(CtMethod<?> method) {
        Set<ModifierKind> modifiers = method.getModifiers();
        
        if (modifiers.contains(ModifierKind.PUBLIC)) {
            return ModifierKind.PUBLIC;
        } else if (modifiers.contains(ModifierKind.PROTECTED)) {
            return ModifierKind.PROTECTED;
        } else if (modifiers.contains(ModifierKind.PRIVATE)) {
            return ModifierKind.PRIVATE;
        } else {
            // package-private，但Spoon中没有对应的ModifierKind，用null表示
            return null; // package-private
        }
    }
    
    /**
     * 调整方法的可见性
     */
    private void adjustMethodVisibility(CtMethod<?> method, ModifierKind targetVisibility) {
        // 移除所有可见性修饰符
        method.removeModifier(ModifierKind.PUBLIC);
        method.removeModifier(ModifierKind.PROTECTED);
        method.removeModifier(ModifierKind.PRIVATE);
        
        // 添加目标可见性修饰符（如果不是package-private）
        if (targetVisibility != null) {
            method.addModifier(targetVisibility);
        }
    }
    
    /**
     * 检查是否所有子类方法都存在于其他子类中
     * 这用于确保方法上提是安全的
     */
    public boolean validateMethodExistsInAllChildClasses(String methodName, 
                                                       List<CtParameter<?>> parameters,
                                                       List<CtClass<?>> childClasses) {
        for (CtClass<?> childClass : childClasses) {
            boolean found = false;
            for (CtMethod<?> method : childClass.getMethods()) {
                if (method.getSimpleName().equals(methodName) && 
                    hasSameParameters(method, parameters)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                logger.warn("子类 {} 中未找到方法 {}", childClass.getSimpleName(), methodName);
                return false;
            }
        }
        return true;
    }
    
    /**
     * 检查方法参数是否匹配
     */
    private boolean hasSameParameters(CtMethod<?> method, List<CtParameter<?>> targetParameters) {
        List<CtParameter<?>> methodParams = method.getParameters();
        
        if (methodParams.size() != targetParameters.size()) {
            return false;
        }
        
        for (int i = 0; i < methodParams.size(); i++) {
            if (!methodParams.get(i).getType().equals(targetParameters.get(i).getType())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 为方法正确添加@Override注解
     * 确保注解与修饰符之间有正确的换行，避免粘黏问题
     */
    public void addOverrideAnnotationProperly(CtMethod<?> method) {
        addOverrideAnnotationProperly(method, false);
    }
    
    /**
     * 为方法正确添加@Override注解
     * 
     * @param method 目标方法
     * @param skipModifierReset 是否跳过修饰符重置（当方法的修饰符刚被调整过时应该跳过）
     */
    public void addOverrideAnnotationProperly(CtMethod<?> method, boolean skipModifierReset) {
        try {
            // 检查是否已有@Override注解
            boolean hasOverride = method.getAnnotations().stream()
                .anyMatch(annotation -> 
                    annotation.getAnnotationType().getSimpleName().equals("Override"));
            
            if (!hasOverride) {
                spoon.reflect.factory.Factory factory = method.getFactory();
                
                // 创建@Override注解 - 使用简化的引用
                spoon.reflect.reference.CtTypeReference<Override> overrideRef = 
                    factory.Type().createReference("Override");
                CtAnnotation<Override> overrideAnnotation = factory.Core().createAnnotation();
                overrideAnnotation.setAnnotationType(overrideRef);
                
                // 确保注解被正确添加到方法前面
                method.addAnnotation(overrideAnnotation);
                
                // 根据情况决定是否进行修饰符重置
                if (!skipModifierReset) {
                    // 修复格式问题：重新设置修饰符以强制格式刷新
                    try {
                        Set<ModifierKind> modifiers = new HashSet<>(method.getModifiers());
                        method.setModifiers(new HashSet<>()); // 先清空
                        method.setModifiers(modifiers); // 再重新设置，这样可以触发格式重新计算
                        
                        // 额外的格式修复：如果方法有位置信息，尝试刷新
                        if (method.getPosition() != null && method.getPosition().isValidPosition()) {
                            // 通过重新设置父元素来触发格式更新
                            spoon.reflect.declaration.CtElement parent = method.getParent();
                            if (parent != null) {
                                method.setParent(parent);
                            }
                        }
                    } catch (Exception formatEx) {
                        logger.debug("格式调整失败，但注解已添加: {}", formatEx.getMessage());
                    }
                } else {
                    logger.debug("跳过修饰符重置，因为方法修饰符刚被调整过");
                }
                
                logger.debug("为方法 {} 添加了@Override注解", method.getSimpleName());
            }
            
        } catch (Exception e) {
            logger.warn("添加@Override注解失败: {}", e.getMessage());
        }
    }
}
