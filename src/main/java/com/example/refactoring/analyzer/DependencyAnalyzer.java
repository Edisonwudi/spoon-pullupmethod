package com.example.refactoring.analyzer;

import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

import java.util.HashSet;
import java.util.Set;

/**
 * 依赖分析器 - 检查方法是否引用子类特定的字段或方法
 */
public class DependencyAnalyzer {
    
    /**
     * 分析方法的依赖关系
     * 
     * @param method 要分析的方法
     * @param containingClass 包含该方法的类
     * @return 依赖分析结果
     */
    public DependencyAnalysisResult analyzeDependencies(CtMethod<?> method, CtClass<?> containingClass) {
        DependencyScanner scanner = new DependencyScanner(containingClass);
        scanner.scan(method);
        
        return new DependencyAnalysisResult(
            scanner.hasChildClassDependencies(),
            scanner.getReferencedFields(),
            scanner.getReferencedMethods(),
            scanner.getDependencyIssues()
        );
    }
    
    /**
     * 依赖扫描器 - 遍历方法AST节点检查依赖
     */
    private static class DependencyScanner extends CtScanner {
        private final CtClass<?> containingClass;
        private final Set<CtField<?>> referencedFields = new HashSet<>();
        private final Set<CtMethod<?>> referencedMethods = new HashSet<>();
        private final Set<String> dependencyIssues = new HashSet<>();
        private boolean hasChildClassDependencies = false;
        
        public DependencyScanner(CtClass<?> containingClass) {
            this.containingClass = containingClass;
        }
        
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
        
        private void checkFieldReference(CtFieldReference<?> fieldRef) {
            if (fieldRef == null) return;
            
            CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
            if (declaringType == null) return;
            
            // 检查是否引用了当前类或其子类的字段
            if (isChildClassMember(declaringType)) {
                CtField<?> field = fieldRef.getFieldDeclaration();
                if (field != null) {
                    referencedFields.add(field);
                    
                    // 检查字段的可见性 - 私有字段可以通过自动上提解决，不阻止重构
                    if (field.hasModifier(ModifierKind.PRIVATE)) {
                        dependencyIssues.add("引用了私有字段: " + fieldRef.getSimpleName() + " (将自动上提)");
                    }
                }
            }
        }
        
        private void checkMethodReference(CtExecutableReference<?> methodRef) {
            if (methodRef == null) return;
            
            CtTypeReference<?> declaringType = methodRef.getDeclaringType();
            if (declaringType == null) return;
            
            // 检查是否调用了当前类或其子类的方法
            if (isChildClassMember(declaringType)) {
                CtExecutable<?> executable = methodRef.getExecutableDeclaration();
                if (executable instanceof CtMethod) {
                    CtMethod<?> method = (CtMethod<?>) executable;
                    referencedMethods.add(method);
                    
                    // 检查方法的可见性
                    if (method.hasModifier(ModifierKind.PRIVATE)) {
                        // 私有方法依赖可以通过上提抽象方法解决，但需要特殊处理
                        dependencyIssues.add("调用了私有方法: " + methodRef.getSimpleName() + " (将上提为抽象方法)");
                    } else {
                        // 非私有方法依赖也需要上提抽象版本以保持接口一致性
                        dependencyIssues.add("调用了子类方法: " + methodRef.getSimpleName() + " (将上提为抽象方法)");
                    }
                }
            }
        }
        
        /**
         * 检查类型是否为当前类或其子类
         */
        private boolean isChildClassMember(CtTypeReference<?> typeRef) {
            if (typeRef == null || containingClass == null) return false;
            
            // 检查是否为当前类
            if (typeRef.equals(containingClass.getReference())) {
                return true;
            }
            
            // 检查是否为子类（这里简化处理，实际可能需要更复杂的继承关系检查）
            try {
                CtType<?> type = typeRef.getTypeDeclaration();
                if (type instanceof CtClass) {
                    CtClass<?> clazz = (CtClass<?>) type;
                    return isSubclassOf(clazz, containingClass);
                }
            } catch (Exception e) {
                // 类型解析失败，保守处理
                return false;
            }
            
            return false;
        }
        
        /**
         * 检查是否为子类关系
         */
        private boolean isSubclassOf(CtClass<?> childClass, CtClass<?> parentClass) {
            if (childClass == null || parentClass == null) return false;
            
            CtTypeReference<?> superClass = childClass.getSuperclass();
            while (superClass != null) {
                if (superClass.equals(parentClass.getReference())) {
                    return true;
                }
                try {
                    CtType<?> superType = superClass.getTypeDeclaration();
                    if (superType instanceof CtClass) {
                        superClass = ((CtClass<?>) superType).getSuperclass();
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }
            return false;
        }
        
        public boolean hasChildClassDependencies() {
            return hasChildClassDependencies;
        }
        
        public Set<CtField<?>> getReferencedFields() {
            return new HashSet<>(referencedFields);
        }
        
        public Set<CtMethod<?>> getReferencedMethods() {
            return new HashSet<>(referencedMethods);
        }
        
        public Set<String> getDependencyIssues() {
            return new HashSet<>(dependencyIssues);
        }
    }
    
    /**
     * 依赖分析结果类
     */
    public static class DependencyAnalysisResult {
        private final boolean hasChildClassDependencies;
        private final Set<CtField<?>> referencedFields;
        private final Set<CtMethod<?>> referencedMethods;
        private final Set<String> dependencyIssues;
        
        public DependencyAnalysisResult(boolean hasChildClassDependencies,
                                      Set<CtField<?>> referencedFields,
                                      Set<CtMethod<?>> referencedMethods,
                                      Set<String> dependencyIssues) {
            this.hasChildClassDependencies = hasChildClassDependencies;
            this.referencedFields = referencedFields;
            this.referencedMethods = referencedMethods;
            this.dependencyIssues = dependencyIssues;
        }
        
        public boolean hasChildClassDependencies() {
            return hasChildClassDependencies;
        }
        
        public Set<CtField<?>> getReferencedFields() {
            return new HashSet<>(referencedFields);
        }
        
        public Set<CtMethod<?>> getReferencedMethods() {
            return new HashSet<>(referencedMethods);
        }
        
        public Set<String> getDependencyIssues() {
            return new HashSet<>(dependencyIssues);
        }
        
        public boolean canBePulledUp() {
            return !hasChildClassDependencies;
        }
    }
}
