package com.example.refactoring.core;

import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 类和方法查找器
 * 负责在Spoon模型中查找类、方法和继承关系
 */
public class ClassFinder {
    
    private static final Logger logger = LoggerFactory.getLogger(ClassFinder.class);
    
    /**
     * 查找指定名称的类
     * 
     * @param model Spoon模型
     * @param className 类名（支持简单名或全限定名）
     * @return 找到的类，如果未找到返回null
     */
    public CtClass<?> findClass(CtModel model, String className) {
        for (CtType<?> type : model.getAllTypes()) {
            if (type instanceof CtClass && 
                (type.getSimpleName().equals(className) || type.getQualifiedName().equals(className))) {
                return (CtClass<?>) type;
            }
        }
        return null;
    }
    
    /**
     * 在类中查找指定名称的方法
     * 
     * @param clazz 目标类
     * @param methodName 方法名
     * @return 找到的方法，如果未找到返回null
     */
    public CtMethod<?> findMethod(CtClass<?> clazz, String methodName) {
        return clazz.getMethods().stream()
            .filter(method -> method.getSimpleName().equals(methodName))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 获取类的直接父类
     * 
     * @param childClass 子类
     * @return 父类，如果没有父类或解析失败返回null
     */
    public CtClass<?> getParentClass(CtClass<?> childClass) {
        try {
            CtTypeReference<?> superClassRef = childClass.getSuperclass();
            if (superClassRef == null) {
                return null;
            }
            
            CtType<?> superType = superClassRef.getTypeDeclaration();
            if (superType instanceof CtClass) {
                return (CtClass<?>) superType;
            }
        } catch (Exception e) {
            logger.error("获取父类失败", e);
        }
        return null;
    }
    
    /**
     * 收集父类的所有直接子类
     * 
     * @param parentClass 父类
     * @return 所有直接子类的列表
     */
    public List<CtClass<?>> collectAllChildClasses(CtClass<?> parentClass) {
        List<CtClass<?>> childClasses = new ArrayList<>();
        
        // 遍历模型中的所有类，查找继承自parentClass的类
        spoon.reflect.CtModel model = parentClass.getFactory().getModel();
        for (CtType<?> type : model.getAllTypes()) {
            if (type instanceof CtClass) {
                CtClass<?> clazz = (CtClass<?>) type;
                if (isDirectSubclass(clazz, parentClass)) {
                    childClasses.add(clazz);
                    logger.debug("发现子类: {}", clazz.getQualifiedName());
                }
            }
        }
        
        logger.info("收集到 {} 个子类", childClasses.size());
        return childClasses;
    }
    
    /**
     * 检查一个类是否是另一个类的直接子类
     * 
     * @param potentialChild 潜在的子类
     * @param potentialParent 潜在的父类
     * @return 如果是直接子类返回true
     */
    public boolean isDirectSubclass(CtClass<?> potentialChild, CtClass<?> potentialParent) {
        try {
            spoon.reflect.reference.CtTypeReference<?> superClass = potentialChild.getSuperclass();
            return superClass != null && 
                   superClass.getQualifiedName().equals(potentialParent.getQualifiedName());
        } catch (Exception e) {
            logger.debug("检查继承关系时发生异常: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 在指定类中查找匹配的方法（根据方法名和参数）
     * 
     * @param clazz 目标类
     * @param methodName 方法名
     * @param parameters 参数列表
     * @return 匹配的方法，如果未找到返回null
     */
    public CtMethod<?> findMatchingMethod(CtClass<?> clazz, String methodName, List<CtParameter<?>> parameters) {
        for (CtMethod<?> method : clazz.getMethods()) {
            if (method.getSimpleName().equals(methodName) && 
                method.getParameters().size() == parameters.size()) {
                // 简化的参数类型匹配
                boolean parametersMatch = true;
                for (int i = 0; i < parameters.size(); i++) {
                    if (!method.getParameters().get(i).getType().equals(parameters.get(i).getType())) {
                        parametersMatch = false;
                        break;
                    }
                }
                if (parametersMatch) {
                    return method;
                }
            }
        }
        return null;
    }
    
    /**
     * 获取类的所有方法名称
     * 
     * @param clazz 目标类
     * @return 方法名称列表
     */
    public List<String> getMethodNames(CtClass<?> clazz) {
        List<String> methodNames = new ArrayList<>();
        if (clazz != null) {
            clazz.getMethods().forEach(method -> methodNames.add(method.getSimpleName()));
        }
        return methodNames;
    }
    
    /**
     * 获取模型中所有类的名称
     * 
     * @param model Spoon模型
     * @return 类名称列表
     */
    public List<String> getClassNames(CtModel model) {
        List<String> classNames = new ArrayList<>();
        if (model != null) {
            model.getAllTypes().forEach(type -> {
                if (type instanceof CtClass) {
                    classNames.add(type.getQualifiedName());
                }
            });
        }
        return classNames;
    }
}
