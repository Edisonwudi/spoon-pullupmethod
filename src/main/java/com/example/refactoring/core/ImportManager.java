package com.example.refactoring.core;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 导入语句管理器
 * 负责为类添加必要的导入语句，避免过度导入
 */
public class ImportManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ImportManager.class);
    
    /**
     * 为方法、字段和上提方法补齐导入语句
     * 
     * @param targetClass 目标类
     * @param method 主方法
     * @param fields 字段列表
     * @param pulledUpMethods 上提方法列表
     */
    public void ensureMissingImportsForMethodAndFieldsAndMethods(CtClass<?> targetClass, CtMethod<?> method, 
                                                               List<CtField<?>> fields,
                                                               List<CtMethod<?>> pulledUpMethods) {
        try {
            if (targetClass == null || method == null) return;
            spoon.reflect.factory.Factory factory = targetClass.getFactory();
            if (factory == null) return;
            spoon.reflect.cu.CompilationUnit cu = getCompilationUnit(targetClass, factory);
            if (cu == null) return;

            String currentPackage = targetClass.getPackage() != null ? targetClass.getPackage().getQualifiedName() : "";

            // 收集方法、字段和上提方法的类型引用
            Set<CtTypeReference<?>> referenced = new HashSet<>();
            
            // 收集主方法体内类型引用
            collectTypeReferences(method, referenced);
            
            // 收集字段的类型引用
            if (fields != null) {
                for (CtField<?> field : fields) {
                    collectTypeReferences(field, referenced);
                }
            }
            
            // 收集上提方法的类型引用
            if (pulledUpMethods != null) {
                for (CtMethod<?> pulledUpMethod : pulledUpMethods) {
                    collectTypeReferences(pulledUpMethod, referenced);
                }
            }

            // 处理导入
            processImports(cu, factory, referenced, currentPackage);
            
        } catch (Exception e) {
            logger.debug("ensureMissingImportsForMethodAndFieldsAndMethods 失败: {}", e.getMessage());
        }
    }
    
    /**
     * 为方法和字段补齐导入语句
     * 
     * @param targetClass 目标类
     * @param method 方法
     * @param fields 字段列表
     */
    public void ensureMissingImportsForMethodAndFields(CtClass<?> targetClass, CtMethod<?> method, List<CtField<?>> fields) {
        try {
            if (targetClass == null || method == null) return;
            spoon.reflect.factory.Factory factory = targetClass.getFactory();
            if (factory == null) return;
            spoon.reflect.cu.CompilationUnit cu = getCompilationUnit(targetClass, factory);
            if (cu == null) return;

            String currentPackage = targetClass.getPackage() != null ? targetClass.getPackage().getQualifiedName() : "";

            // 收集方法和字段的类型引用
            Set<CtTypeReference<?>> referenced = new HashSet<>();
            
            // 收集方法体内类型引用
            collectTypeReferences(method, referenced);
            
            // 收集字段的类型引用
            if (fields != null) {
                for (CtField<?> field : fields) {
                    collectTypeReferences(field, referenced);
                }
            }

            // 处理导入
            processImports(cu, factory, referenced, currentPackage);
            
        } catch (Exception e) {
            logger.debug("ensureMissingImportsForMethodAndFields 失败: {}", e.getMessage());
        }
    }
    
    /**
     * 为单个方法补齐导入语句
     * 
     * @param targetClass 目标类
     * @param method 方法
     */
    public void ensureMissingImportsForMethod(CtClass<?> targetClass, CtMethod<?> method) {
        try {
            if (targetClass == null || method == null) return;
            spoon.reflect.factory.Factory factory = targetClass.getFactory();
            if (factory == null) return;
            spoon.reflect.cu.CompilationUnit cu = getCompilationUnit(targetClass, factory);
            if (cu == null) return;

            String currentPackage = targetClass.getPackage() != null ? targetClass.getPackage().getQualifiedName() : "";

            // 收集方法体内类型引用
            Set<CtTypeReference<?>> referenced = new HashSet<>();
            collectTypeReferences(method, referenced);

            // 处理导入
            processImports(cu, factory, referenced, currentPackage);
            
        } catch (Exception e) {
            logger.debug("ensureMissingImportsForMethod 失败: {}", e.getMessage());
        }
    }
    
    /**
     * 获取或创建编译单元
     */
    private spoon.reflect.cu.CompilationUnit getCompilationUnit(CtClass<?> targetClass, spoon.reflect.factory.Factory factory) {
        spoon.reflect.cu.CompilationUnit cu = targetClass.getPosition() != null ? 
            targetClass.getPosition().getCompilationUnit() : null;
        if (cu == null) {
            cu = factory.CompilationUnit().getOrCreate(targetClass);
        }
        return cu;
    }
    
    /**
     * 收集元素中的类型引用
     */
    private void collectTypeReferences(spoon.reflect.declaration.CtElement element, Set<CtTypeReference<?>> referenced) {
        element.accept(new spoon.reflect.visitor.CtScanner() {
            @Override
            public <T> void visitCtTypeReference(CtTypeReference<T> ref) {
                if (ref != null) referenced.add(ref);
                super.visitCtTypeReference(ref);
            }
        });
    }
    
    /**
     * 处理导入语句
     */
    private void processImports(spoon.reflect.cu.CompilationUnit cu, spoon.reflect.factory.Factory factory,
                              Set<CtTypeReference<?>> referenced, String currentPackage) {
        // 清理无效导入并收集已存在的导入
        Set<String> existingImports = cleanAndCollectExistingImports(cu);
        
        // 添加新的导入
        for (CtTypeReference<?> ref : referenced) {
            if (shouldAddImport(ref, currentPackage, existingImports)) {
                addImport(cu, factory, ref, existingImports);
            }
        }
    }
    
    /**
     * 清理无效导入并收集已存在的导入
     */
    private Set<String> cleanAndCollectExistingImports(spoon.reflect.cu.CompilationUnit cu) {
        Set<String> existingImports = new HashSet<>();
        List<spoon.reflect.declaration.CtImport> importsCopy = 
            new java.util.ArrayList<>(cu.getImports());
        
        for (spoon.reflect.declaration.CtImport imp : importsCopy) {
            try {
                String refStr = imp.getReference() != null ? imp.getReference().toString() : null;
                if (refStr == null || refStr.contains("nulltype") || refStr.contains("<nulltype>")) {
                    cu.getImports().remove(imp); // 移除无效导入
                } else {
                    existingImports.add(refStr);
                }
            } catch (Exception ignore) {
                cu.getImports().remove(imp); // 移除异常导入
            }
        }
        
        return existingImports;
    }
    
    /**
     * 判断是否应该添加导入
     */
    private boolean shouldAddImport(CtTypeReference<?> ref, String currentPackage, Set<String> existingImports) {
        if (ref == null || ref.isPrimitive()) return false;
        
        String qname = ref.getQualifiedName();
        if (qname == null || qname.isEmpty()) return false;
        
        // 排除 java.lang
        if (qname.startsWith("java.lang.")) return false;
        
        // 排除同包类型
        String refPkg = ref.getPackage() != null ? ref.getPackage().getQualifiedName() : "";
        if (!currentPackage.isEmpty() && currentPackage.equals(refPkg)) return false;
        
        // 排除内部类
        if (qname.contains("$")) return false;
        
        // 排除无效类型名
        if (qname.contains("nulltype") || qname.contains("<nulltype>")) return false;
        
        // 若已存在相应 import 则跳过
        if (existingImports.contains(qname)) return false;
        
        // 若已有通配符导入则跳过
        if (refPkg != null && existingImports.contains(refPkg + ".*")) return false;
        
        return true;
    }
    
    /**
     * 添加导入语句
     */
    private void addImport(spoon.reflect.cu.CompilationUnit cu, spoon.reflect.factory.Factory factory,
                          CtTypeReference<?> ref, Set<String> existingImports) {
        try {
            String qname = ref.getQualifiedName();
            CtTypeReference<?> addRef = factory.Type().createReference(qname);
            if (addRef != null) {
                cu.getImports().add(factory.createImport(addRef));
                existingImports.add(qname);
                logger.debug("添加导入: {}", qname);
            }
        } catch (Exception ignore) {
            // 忽略添加失败的导入
        }
    }
}
