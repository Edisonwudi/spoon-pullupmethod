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
 * 说明：本文中注释提到的“子类/父类”广义指“后代类/目标祖先类”，
 * 支持从任意后代类上提到任意祖先类（非仅限直接父子）。
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
     * @param childClass 后代类（触发上提的方法所在类）
     * @param parentClass 目标祖先类（字段被上提到的类）
     * @return 字段上提结果
     */
    public FieldPullUpResult pullUpDependentFields(CtMethod<?> method, 
                                                  CtClass<?> childClass, 
                                                  CtClass<?> parentClass) {
        try {
            logger.debug("开始分析方法 {} 的字段依赖", method.getSimpleName());
            
            // 1. 收集方法中引用的后代类字段，及位于 后代类 -> 目标祖先类 之间的中间祖先类的字段
            Set<CtField<?>> dependentFields = collectDependentFields(method, childClass, parentClass);
            
            if (dependentFields.isEmpty()) {
                return FieldPullUpResult.success("方法无依赖字段需要上提", new ArrayList<>());
            }
            
            logger.info("发现 {} 个依赖字段需要上提", dependentFields.size());
            try {
                List<String> names = new ArrayList<>();
                for (CtField<?> f : dependentFields) {
                    names.add(f.getSimpleName());
                }
                if (!names.isEmpty()) {
                    logger.info("依赖字段列表: {}", String.join(", ", names));
                }
            } catch (Exception ignore) {}
            
            // 2. 检查字段是否可以安全上提到目标祖先类
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
                        logger.info("成功上提字段: {} 到目标祖先类 {}", field.getSimpleName(), parentClass.getSimpleName());
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
     * 收集方法中引用的当前后代类字段
     */
    private Set<CtField<?>> collectDependentFields(CtMethod<?> method, CtClass<?> childClass, CtClass<?> targetAncestorClass) {
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
                
                CtField<?> field = fieldRef.getFieldDeclaration();
                if (field == null) return;

                // 情况A：当前后代类自身字段
                if (declaringType.equals(childClass.getReference()) && field.getParent() == childClass) {
                    dependentFields.add(field);
                    logger.debug("发现依赖字段(后代自身): {}", field.getSimpleName());
                    return;
                }

                // 情况B：位于 后代类 -> 目标祖先类 之间的中间祖先类字段
                if (isTypeBetweenDescendantAndAncestor(declaringType, childClass, targetAncestorClass)) {
                    dependentFields.add(field);
                    logger.debug("发现依赖字段(中间祖先): {} (声明于: {})", field.getSimpleName(), declaringType.getQualifiedName());
                }
            }
        });
        
        return dependentFields;
    }

    /**
     * 判断某类型是否位于 后代类 与 目标祖先类 之间的继承路径上（不包含目标祖先类本身）
     */
    private boolean isTypeBetweenDescendantAndAncestor(CtTypeReference<?> typeRef,
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
     * 验证字段是否可以安全上提到目标祖先类
     */
    private FieldPullUpResult validateFieldsCanBePulledUp(Set<CtField<?>> fields, CtClass<?> parentClass) {
        List<String> warnings = new ArrayList<>();
        List<String> blockingIssues = new ArrayList<>();
        Map<CtField<?>, String> blockedBy = new HashMap<>();

        for (CtField<?> field : fields) {
            // 私有字段仅提示，将在上提时改为 protected，不作为阻断
            if (field.hasModifier(ModifierKind.PRIVATE)) {
                warnings.add("字段 " + field.getSimpleName() + " 为私有，将调整为 protected");
                logger.info("字段 {} 为 private，将在上提时调整为 protected", field.getSimpleName());
            }

            // 规则1：目标祖先类存在同名字段 -> 阻断
            if (hasFieldWithSameName(parentClass, field.getSimpleName())) {
                String issue = "父类已存在同名字段: " + field.getSimpleName();
                blockedBy.put(field, issue);
                logger.warn("字段 {} 无法上提：{}", field.getSimpleName(), issue);
                continue;
            }

            // 规则2：字段类型在目标祖先类中不可访问 -> 阻断
            CtTypeReference<?> fieldType = field.getType();
            if (fieldType != null && !isTypeAccessibleInParent(fieldType, parentClass)) {
                String issue = "字段 " + field.getSimpleName() + " 的类型在父类中不可访问";
                blockedBy.put(field, issue);
                logger.warn("字段 {} 无法上提：{} (类型: {})", field.getSimpleName(), issue, String.valueOf(fieldType));
                continue;
            }

            // 通过校验
            logger.debug("字段 {} 校验通过，可上提", field.getSimpleName());
        }

        // 允许部分成功：仅保留通过校验的字段
        if (!blockedBy.isEmpty()) {
            List<CtField<?>> allowed = new ArrayList<>();
            for (CtField<?> f : fields) {
                if (!blockedBy.containsKey(f)) {
                    allowed.add(f);
                }
            }

            // 替换集合内容为允许上提的字段
            fields.clear();
            fields.addAll(allowed);

            // 汇总阻断原因日志（按字段）
            logger.warn("部分字段无法上提，阻断原因如下:");
            for (Map.Entry<CtField<?>, String> e : blockedBy.entrySet()) {
                logger.warn("  - {}: {}", e.getKey().getSimpleName(), e.getValue());
                blockingIssues.add(e.getValue());
            }
        }

        if (fields.isEmpty()) {
            // 全部被阻断
            return FieldPullUpResult.failure("字段验证失败:\n" + String.join("\n", new LinkedHashSet<>(blockingIssues)));
        }

        FieldPullUpResult result = FieldPullUpResult.success(
            "字段验证通过，允许上提 " + fields.size() + " 个字段，阻断 " + blockedBy.size() + " 个",
            new ArrayList<>()
        );
        warnings.forEach(result::addWarning);
        return result;
    }
    
    /**
     * 检查目标祖先类是否已有同名字段
     */
    private boolean hasFieldWithSameName(CtClass<?> parentClass, String fieldName) {
        return parentClass.getFields().stream()
            .anyMatch(field -> field.getSimpleName().equals(fieldName));
    }
    
    /**
     * 检查字段类型在目标祖先类中是否可访问
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
     * 上提单个字段（从后代类到目标祖先类）
     */
    private CtField<?> pullUpSingleField(CtField<?> field, CtClass<?> childClass, CtClass<?> parentClass) {
        try {
            // 1. 克隆字段
            CtField<?> clonedField = field.clone();
            clonedField.setParent(parentClass);
            
            // 2. 调整可见性
            // 2.1 私有字段改为受保护（默认规则）
            if (clonedField.hasModifier(ModifierKind.PRIVATE)) {
                clonedField.removeModifier(ModifierKind.PRIVATE);
                clonedField.addModifier(ModifierKind.PROTECTED);
                logger.info("字段 {} 可见性已从 private 调整为 protected", field.getSimpleName());
            }
            // 2.2 若发生跨模块上提（后代类与目标祖先类属于不同模块），将字段提升为 public
            if (isCrossModule(childClass, parentClass)) {
                clonedField.removeModifier(ModifierKind.PROTECTED);
                clonedField.removeModifier(ModifierKind.PRIVATE);
                clonedField.addModifier(ModifierKind.PUBLIC);
                logger.info("检测到跨模块上提，字段 {} 可见性提升为 public", field.getSimpleName());
            }
            
            // 2.3 基于所有后代类中同名字段类型，统一上提字段的类型为公共父类型，避免类型冲突
            try {
                adjustFieldTypeForAllDescendants(clonedField, parentClass);
            } catch (Exception typeEx) {
                logger.debug("统一上提字段类型失败: {}", typeEx.getMessage());
            }

            // 3. 添加到父类
            parentClass.addField(clonedField);
            
            // 4. 从子类移除
            childClass.removeField(field);
            
            // 5. 沿继承路径（后代类 -> 目标祖先类 之间的中间祖先类）移除同名字段，避免隐藏或引用到私有旧字段
            removeShadowingFieldsAlongPath(childClass, parentClass, clonedField.getSimpleName());

            return clonedField;
            
        } catch (Exception e) {
            logger.error("上提字段 {} 失败", field.getSimpleName(), e);
            throw e;
        }
    }

    /**
     * 统一上提字段类型：
     * 收集目标祖先类的所有后代类中同名字段的类型，计算一个兼容的公共父类型，设置给上提的字段。
     * 若无法解析公共父类，则回退为 java.lang.Object。
     */
    private void adjustFieldTypeForAllDescendants(CtField<?> liftedField, CtClass<?> targetAncestor) {
        try {
            String fieldName = liftedField.getSimpleName();
            spoon.reflect.factory.Factory factory = liftedField.getFactory();
            java.util.List<spoon.reflect.reference.CtTypeReference<?>> types = new java.util.ArrayList<>();

            // 包含原字段类型
            if (liftedField.getType() != null) {
                types.add(liftedField.getType());
            }

            // 收集所有后代类中同名字段的类型
            for (CtClass<?> desc : collectAllDescendantClasses(targetAncestor)) {
                for (CtField<?> f : desc.getFields()) {
                    if (f.getSimpleName().equals(fieldName) && f.getType() != null) {
                        types.add(f.getType());
                        break;
                    }
                }
            }

            if (types.isEmpty()) return;

            spoon.reflect.reference.CtTypeReference<?> current = types.get(0);
            for (int i = 1; i < types.size(); i++) {
                spoon.reflect.reference.CtTypeReference<?> t = types.get(i);
                if (current == null) { current = t; continue; }
                if (isSubtypeOf(t, current)) {
                    // ok
                } else if (isSubtypeOf(current, t)) {
                    current = t; // 选择更上层
                } else {
                    current = findCommonSuperType(current, t, factory);
                    if (current == null) {
                        current = factory.Type().OBJECT;
                        break;
                    }
                }
            }
            if (current != null) {
                liftedField.setType(current);
            }
        } catch (Exception e) {
            logger.debug("调整字段类型时异常: {}", e.getMessage());
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
     * 收集目标祖先类的所有后代类
     */
    private java.util.List<CtClass<?>> collectAllDescendantClasses(CtClass<?> ancestorClass) {
        java.util.List<CtClass<?>> descendants = new java.util.ArrayList<>();
        try {
            spoon.reflect.CtModel model = ancestorClass.getFactory().getModel();
            for (spoon.reflect.declaration.CtType<?> type : model.getAllTypes()) {
                if (type instanceof CtClass) {
                    CtClass<?> clazz = (CtClass<?>) type;
                    if (isDescendantClass(clazz, ancestorClass)) {
                        descendants.add(clazz);
                    }
                }
            }
        } catch (Exception ignore) {}
        return descendants;
    }

    private boolean isDescendantClass(CtClass<?> potentialDescendant, CtClass<?> ancestorClass) {
        try {
            CtClass<?> current = potentialDescendant;
            while (current != null) {
                spoon.reflect.reference.CtTypeReference<?> superRef = current.getSuperclass();
                if (superRef == null) return false;
                spoon.reflect.declaration.CtType<?> superType = superRef.getTypeDeclaration();
                if (superType instanceof CtClass) {
                    CtClass<?> superClass = (CtClass<?>) superType;
                    if (superClass.equals(ancestorClass)) return true;
                    current = superClass;
                } else {
                    return false;
                }
            }
        } catch (Exception ignore) {}
        return false;
    }

    /**
     * 若后代类与目标祖先类属于不同模块（通过最近的 pom.xml 判断），视为跨模块
     */
    private boolean isCrossModule(CtClass<?> childClass, CtClass<?> parentClass) {
        try {
            java.io.File childFile = childClass.getPosition() != null ? childClass.getPosition().getFile() : null;
            java.io.File parentFile = parentClass.getPosition() != null ? parentClass.getPosition().getFile() : null;
            if (childFile == null || parentFile == null) return false;
            java.io.File childModule = findNearestPom(childFile);
            java.io.File parentModule = findNearestPom(parentFile);
            if (childModule == null || parentModule == null) return false;
            return !childModule.getAbsolutePath().equals(parentModule.getAbsolutePath());
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

    /**
     * 沿 后代类 -> 目标祖先类 的继承路径，在中间祖先类中移除同名字段
     */
    private void removeShadowingFieldsAlongPath(CtClass<?> childClass, CtClass<?> parentClass, String fieldName) {
        try {
            spoon.reflect.reference.CtTypeReference<?> superRef = childClass.getSuperclass();
            while (superRef != null) {
                spoon.reflect.declaration.CtType<?> superType = superRef.getTypeDeclaration();
                if (!(superType instanceof CtClass)) break;
                CtClass<?> superClass = (CtClass<?>) superType;
                if (superClass.equals(parentClass)) {
                    break; // 到达目标父类，不处理父类本身
                }
                // 移除同名字段（若存在）
                CtField<?> toRemove = null;
                for (CtField<?> f : superClass.getFields()) {
                    if (f.getSimpleName().equals(fieldName)) {
                        toRemove = f; break;
                    }
                }
                if (toRemove != null) {
                    superClass.removeField(toRemove);
                    logger.info("移除中间类 {} 中的同名字段 {}，避免隐藏", superClass.getQualifiedName(), fieldName);
                }
                superRef = superClass.getSuperclass();
            }
        } catch (Exception e) {
            logger.debug("沿路径移除同名字段失败: {}", e.getMessage());
        }
    }
    
}
