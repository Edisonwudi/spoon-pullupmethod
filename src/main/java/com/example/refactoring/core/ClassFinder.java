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
                    logger.debug("发现直接子类: {}", clazz.getQualifiedName());
                }
            }
        }
        
        logger.info("收集到 {} 个直接子类", childClasses.size());
        return childClasses;
    }
    
    /**
     * 收集父类的所有后代类（包括直接和间接子类）
     * 
     * @param ancestorClass 祖先类
     * @return 所有后代类的列表
     */
    public List<CtClass<?>> collectAllDescendantClasses(CtClass<?> ancestorClass) {
        List<CtClass<?>> descendants = new ArrayList<>();
        
        // 遍历模型中的所有类，查找所有继承自ancestorClass的类
        spoon.reflect.CtModel model = ancestorClass.getFactory().getModel();
        for (CtType<?> type : model.getAllTypes()) {
            if (type instanceof CtClass) {
                CtClass<?> clazz = (CtClass<?>) type;
                if (isDescendantClass(clazz, ancestorClass)) {
                    descendants.add(clazz);
                    logger.debug("发现后代类: {}", clazz.getQualifiedName());
                }
            }
        }
        
        logger.info("收集到 {} 个后代类", descendants.size());
        return descendants;
    }
    
    /**
     * 检查一个类是否是另一个类的后代类（包括直接和间接子类）
     * 
     * @param potentialDescendant 潜在的后代类
     * @param ancestorClass 祖先类
     * @return 如果是后代类返回true
     */
    public boolean isDescendantClass(CtClass<?> potentialDescendant, CtClass<?> ancestorClass) {
        if (potentialDescendant.equals(ancestorClass)) {
            return false; // 自身不算后代类
        }
        
        try {
            CtClass<?> currentClass = potentialDescendant;
            while (currentClass != null) {
                CtTypeReference<?> superClassRef = currentClass.getSuperclass();
                if (superClassRef == null) {
                    break;
                }
                
                CtType<?> superType = superClassRef.getTypeDeclaration();
                if (superType instanceof CtClass) {
                    CtClass<?> superClass = (CtClass<?>) superType;
                    if (superClass.getQualifiedName().equals(ancestorClass.getQualifiedName())) {
                        return true;
                    }
                    currentClass = superClass;
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            logger.debug("检查后代关系时发生异常: {}", e.getMessage());
        }
        
        return false;
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
     * 获取类的所有祖先类（不包括Object类）
     * 
     * @param childClass 子类
     * @return 祖先类列表，从直接父类到最顶层祖先类的顺序
     */
    public List<CtClass<?>> getAllAncestorClasses(CtClass<?> childClass) {
        List<CtClass<?>> ancestors = new ArrayList<>();
        CtClass<?> currentClass = childClass;
        
        while (currentClass != null) {
            CtClass<?> parentClass = getParentClass(currentClass);
            if (parentClass != null && 
                !parentClass.getQualifiedName().equals("java.lang.Object")) {
                ancestors.add(parentClass);
                currentClass = parentClass;
            } else {
                break;
            }
        }
        
        logger.debug("找到 {} 个祖先类", ancestors.size());
        return ancestors;
    }
    
    /**
     * 检查一个类是否是另一个类的祖先类
     * 
     * @param potentialAncestor 潜在的祖先类
     * @param descendantClass 后代类
     * @return 如果是祖先类返回true
     */
    public boolean isAncestorClass(CtClass<?> potentialAncestor, CtClass<?> descendantClass) {
        List<CtClass<?>> ancestors = getAllAncestorClasses(descendantClass);
        return ancestors.stream()
            .anyMatch(ancestor -> ancestor.getQualifiedName().equals(potentialAncestor.getQualifiedName()));
    }
    
    /**
     * 获取从子类到指定祖先类的继承路径
     * 
     * @param childClass 子类
     * @param targetAncestor 目标祖先类
     * @return 继承路径，如果不是祖先关系返回空列表
     */
    public List<CtClass<?>> getInheritancePath(CtClass<?> childClass, CtClass<?> targetAncestor) {
        List<CtClass<?>> path = new ArrayList<>();
        CtClass<?> currentClass = childClass;
        
        while (currentClass != null) {
            if (currentClass.getQualifiedName().equals(targetAncestor.getQualifiedName())) {
                return path; // 找到目标祖先类
            }
            
            CtClass<?> parentClass = getParentClass(currentClass);
            if (parentClass != null && 
                !parentClass.getQualifiedName().equals("java.lang.Object")) {
                path.add(parentClass);
                currentClass = parentClass;
            } else {
                break;
            }
        }
        
        return new ArrayList<>(); // 未找到继承关系
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
