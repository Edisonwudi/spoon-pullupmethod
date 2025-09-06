package com.example.refactoring.handler;

import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 字段上提处理器 - 处理方法依赖字段的自动上提
 */
public class FieldPullUpHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(FieldPullUpHandler.class);
    
    /**
     * 字段上提结果类
     */
    public static class FieldPullUpResult {
        private final boolean success;
        private final String message;
        private final List<CtField<?>> pulledUpFields;
        private final List<String> warnings;
        
        private FieldPullUpResult(boolean success, String message, 
                                List<CtField<?>> pulledUpFields, List<String> warnings) {
            this.success = success;
            this.message = message;
            this.pulledUpFields = pulledUpFields != null ? pulledUpFields : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }
        
        public static FieldPullUpResult success(String message, List<CtField<?>> pulledUpFields) {
            return new FieldPullUpResult(true, message, pulledUpFields, new ArrayList<>());
        }
        
        public static FieldPullUpResult failure(String message) {
            return new FieldPullUpResult(false, message, new ArrayList<>(), new ArrayList<>());
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<CtField<?>> getPulledUpFields() { return new ArrayList<>(pulledUpFields); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        
        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
    }
    
    /**
     * 分析并上提方法依赖的字段
     * 
     * @param method 要分析的方法
     * @param childClass 子类
     * @param parentClass 父类
     * @return 字段上提结果
     */
    public FieldPullUpResult pullUpDependentFields(CtMethod<?> method, 
                                                  CtClass<?> childClass, 
                                                  CtClass<?> parentClass) {
        try {
            logger.debug("开始分析方法 {} 的字段依赖", method.getSimpleName());
            
            // 1. 收集方法中引用的子类字段
            Set<CtField<?>> dependentFields = collectDependentFields(method, childClass);
            
            if (dependentFields.isEmpty()) {
                return FieldPullUpResult.success("方法无依赖字段需要上提", new ArrayList<>());
            }
            
            logger.info("发现 {} 个依赖字段需要上提", dependentFields.size());
            
            // 2. 检查字段是否可以安全上提
            FieldPullUpResult validationResult = validateFieldsCanBePulledUp(dependentFields, parentClass);
            if (!validationResult.isSuccess()) {
                return validationResult;
            }
            
            // 3. 执行字段上提
            List<CtField<?>> pulledUpFields = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            for (CtField<?> field : dependentFields) {
                try {
                    logger.info("开始上提字段: {} (类型: {})", field.getSimpleName(), field.getType());
                    CtField<?> clonedField = pullUpSingleField(field, childClass, parentClass);
                    if (clonedField != null) {
                        pulledUpFields.add(clonedField);
                        logger.info("成功上提字段: {} 到父类 {}", field.getSimpleName(), parentClass.getSimpleName());
                    } else {
                        logger.warn("字段上提返回 null: {}", field.getSimpleName());
                    }
                } catch (Exception e) {
                    String warning = "字段上提失败: " + field.getSimpleName() + " - " + e.getMessage();
                    warnings.add(warning);
                    logger.warn(warning, e);
                }
            }
            
            // 4. 导入更新由主重构类统一处理，这里不单独处理
            
            FieldPullUpResult result = FieldPullUpResult.success(
                "成功上提 " + pulledUpFields.size() + " 个字段", pulledUpFields);
            warnings.forEach(result::addWarning);
            
            return result;
            
        } catch (Exception e) {
            logger.error("字段上提过程中发生异常", e);
            return FieldPullUpResult.failure("字段上提失败: " + e.getMessage());
        }
    }
    
    /**
     * 收集方法中引用的子类字段
     */
    private Set<CtField<?>> collectDependentFields(CtMethod<?> method, CtClass<?> childClass) {
        Set<CtField<?>> dependentFields = new HashSet<>();
        
        method.accept(new CtScanner() {
            @Override
            public <T> void visitCtFieldRead(spoon.reflect.code.CtFieldRead<T> fieldRead) {
                checkFieldReference(fieldRead.getVariable());
                super.visitCtFieldRead(fieldRead);
            }
            
            @Override
            public <T> void visitCtFieldWrite(spoon.reflect.code.CtFieldWrite<T> fieldWrite) {
                checkFieldReference(fieldWrite.getVariable());
                super.visitCtFieldWrite(fieldWrite);
            }
            
            private void checkFieldReference(CtFieldReference<?> fieldRef) {
                if (fieldRef == null) return;
                
                CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
                if (declaringType == null) return;
                
                // 检查是否为子类的字段
                if (declaringType.equals(childClass.getReference())) {
                    CtField<?> field = fieldRef.getFieldDeclaration();
                    if (field != null && field.getParent() == childClass) {
                        dependentFields.add(field);
                        logger.debug("发现依赖字段: {}", field.getSimpleName());
                    }
                }
            }
        });
        
        return dependentFields;
    }
    
    /**
     * 验证字段是否可以安全上提
     */
    private FieldPullUpResult validateFieldsCanBePulledUp(Set<CtField<?>> fields, CtClass<?> parentClass) {
        List<String> blockingIssues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        for (CtField<?> field : fields) {
            // 私有字段可以通过调整可见性解决，不阻止重构
            if (field.hasModifier(ModifierKind.PRIVATE)) {
                warnings.add("字段 " + field.getSimpleName() + " 为私有，将调整为 protected");
            }
            
            // 检查父类是否已有同名字段 - 这是真正的阻止条件
            if (hasFieldWithSameName(parentClass, field.getSimpleName())) {
                blockingIssues.add("父类已存在同名字段: " + field.getSimpleName());
            }
            
            // 检查字段类型是否在父类中可访问 - 这是真正的阻止条件
            CtTypeReference<?> fieldType = field.getType();
            if (fieldType != null && !isTypeAccessibleInParent(fieldType, parentClass)) {
                blockingIssues.add("字段 " + field.getSimpleName() + " 的类型在父类中不可访问");
            }
        }
        
        if (!blockingIssues.isEmpty()) {
            return FieldPullUpResult.failure("字段验证失败:\n" + String.join("\n", blockingIssues));
        }
        
        FieldPullUpResult result = FieldPullUpResult.success("字段验证通过", new ArrayList<>());
        warnings.forEach(result::addWarning);
        return result;
    }
    
    /**
     * 检查父类是否已有同名字段
     */
    private boolean hasFieldWithSameName(CtClass<?> parentClass, String fieldName) {
        return parentClass.getFields().stream()
            .anyMatch(field -> field.getSimpleName().equals(fieldName));
    }
    
    /**
     * 检查类型在父类中是否可访问
     */
    private boolean isTypeAccessibleInParent(CtTypeReference<?> typeRef, CtClass<?> parentClass) {
        try {
            // 简化检查：如果类型可以解析，认为是可访问的
            return typeRef.getTypeDeclaration() != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 上提单个字段
     */
    private CtField<?> pullUpSingleField(CtField<?> field, CtClass<?> childClass, CtClass<?> parentClass) {
        try {
            // 1. 克隆字段
            CtField<?> clonedField = field.clone();
            clonedField.setParent(parentClass);
            
            // 2. 调整可见性（私有字段改为受保护）
            if (clonedField.hasModifier(ModifierKind.PRIVATE)) {
                clonedField.removeModifier(ModifierKind.PRIVATE);
                clonedField.addModifier(ModifierKind.PROTECTED);
                logger.info("字段 {} 可见性已从 private 调整为 protected", field.getSimpleName());
            }
            
            // 3. 添加到父类
            parentClass.addField(clonedField);
            
            // 4. 从子类移除
            childClass.removeField(field);
            
            return clonedField;
            
        } catch (Exception e) {
            logger.error("上提字段 {} 失败", field.getSimpleName(), e);
            throw e;
        }
    }
    
}
