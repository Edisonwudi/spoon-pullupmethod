package com.example.refactoring.core;

import com.example.refactoring.analyzer.DependencyAnalyzer;
import com.example.refactoring.checker.MethodConflictChecker;
import com.example.refactoring.adjuster.VisibilityAdjuster;
import com.example.refactoring.adjuster.ReturnTypeAdjuster;
import com.example.refactoring.handler.FieldPullUpHandler;
import com.example.refactoring.handler.MethodPullUpHandler;
import com.example.refactoring.handler.VisibilityHandler;

import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 重构编排器
 * 负责协调整个Pull-Up-Method重构过程的各个步骤
 */
public class RefactoringOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(RefactoringOrchestrator.class);
    
    private final ModelBuilder modelBuilder;
    private final ClassFinder classFinder;
    private final CodeGenerator codeGenerator;
    private final ImportManager importManager;
    
    private final DependencyAnalyzer dependencyAnalyzer;
    private final MethodConflictChecker conflictChecker;
    private final VisibilityAdjuster visibilityAdjuster;
    private final ReturnTypeAdjuster returnTypeAdjuster;
    private final FieldPullUpHandler fieldPullUpHandler;
    private final MethodPullUpHandler methodPullUpHandler;
    private final VisibilityHandler visibilityHandler;
    
    public RefactoringOrchestrator() {
        this.modelBuilder = new ModelBuilder();
        this.classFinder = new ClassFinder();
        this.codeGenerator = new CodeGenerator();
        this.importManager = new ImportManager();
        
        this.dependencyAnalyzer = new DependencyAnalyzer();
        this.conflictChecker = new MethodConflictChecker();
        this.visibilityAdjuster = new VisibilityAdjuster();
        this.returnTypeAdjuster = new ReturnTypeAdjuster();
        this.fieldPullUpHandler = new FieldPullUpHandler();
        this.methodPullUpHandler = new MethodPullUpHandler();
        this.visibilityHandler = new VisibilityHandler();
    }
    
    /**
     * 执行Pull-Up-Method重构
     * 
     * @param sourcePaths 源代码路径列表
     * @param childClassName 子类名称
     * @param methodName 要上提的方法名
     * @param outputPath 输出路径（可选，null表示覆盖原文件）
     * @return 重构结果
     */
    public RefactoringResult pullUpMethod(List<String> sourcePaths, 
                                        String childClassName, 
                                        String methodName, 
                                        String outputPath) {
        try {
            logger.info("开始Pull-Up-Method重构: 类={}, 方法={}", childClassName, methodName);
            
            // 1. 构建Spoon模型
            CtModel model = modelBuilder.buildModel(sourcePaths);
            if (model == null) {
                return RefactoringResult.failure("无法构建代码模型");
            }
            
            // 2. 定位子类和方法
            CtClass<?> childClass = classFinder.findClass(model, childClassName);
            if (childClass == null) {
                return RefactoringResult.failure("找不到指定的子类: " + childClassName);
            }
            
            CtMethod<?> targetMethod = classFinder.findMethod(childClass, methodName);
            if (targetMethod == null) {
                return RefactoringResult.failure("在类 " + childClassName + " 中找不到方法: " + methodName);
            }
            
            // 3. 获取父类
            CtClass<?> parentClass = classFinder.getParentClass(childClass);
            if (parentClass == null) {
                return RefactoringResult.failure("类 " + childClassName + " 没有父类或父类无法解析");
            }
            
            logger.info("找到父类: {}", parentClass.getQualifiedName());
            
            // 4. 执行重构前检查
            RefactoringResult checkResult = performPreChecks(targetMethod, childClass, parentClass);
            if (!checkResult.isSuccess()) {
                return checkResult;
            }
            
            // 5. 执行方法迁移
            RefactoringResult migrationResult = performMethodMigration(targetMethod, childClass, parentClass);
            if (!migrationResult.isSuccess()) {
                return migrationResult;
            }
            
            // 6. 输出结果
            List<String> modifiedFiles = codeGenerator.writeModifiedClassesOnly(
                childClass, parentClass, outputPath, sourcePaths, classFinder);
            
            logger.info("Pull-Up-Method重构完成，修改了 {} 个文件", modifiedFiles.size());
            
            // 构建详细的成功消息
            StringBuilder successMessage = new StringBuilder();
            successMessage.append("成功将方法 ").append(methodName)
                         .append(" 从 ").append(childClassName)
                         .append(" 上提到 ").append(parentClass.getSimpleName());
            
            return RefactoringResult.success(successMessage.toString(), modifiedFiles);
            
        } catch (Exception e) {
            logger.error("重构过程中发生异常", e);
            return RefactoringResult.failure("重构失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行重构前的各项检查
     */
    private RefactoringResult performPreChecks(CtMethod<?> method, CtClass<?> childClass, CtClass<?> parentClass) {
        List<String> warnings = new ArrayList<>();
        
        // 1. 依赖分析
        logger.debug("执行依赖分析...");
        DependencyAnalyzer.DependencyAnalysisResult dependencyResult = 
            dependencyAnalyzer.analyzeDependencies(method, childClass);
        
        if (!dependencyResult.canBePulledUp()) {
            StringBuilder sb = new StringBuilder("方法存在子类依赖，无法上提:");
            dependencyResult.getDependencyIssues().forEach(issue -> sb.append("\n- ").append(issue));
            return RefactoringResult.failure(sb.toString());
        }
        
        if (!dependencyResult.getDependencyIssues().isEmpty()) {
            dependencyResult.getDependencyIssues().forEach(issue -> warnings.add("依赖警告: " + issue));
        }
        
        // 2. 方法冲突检查
        logger.debug("执行方法冲突检查...");
        MethodConflictChecker.ConflictCheckResult conflictResult = 
            conflictChecker.checkConflict(method, childClass, parentClass);
        
        if (!conflictResult.isSuccess()) {
            return RefactoringResult.failure("方法冲突检查失败: " + conflictResult.getMessage());
        }
        
        // 3. 可见性检查
        logger.debug("检查可见性要求...");
        if (visibilityAdjuster.needsVisibilityAdjustment(method)) {
            warnings.add("方法可见性将被调整为: " + visibilityAdjuster.getSuggestedVisibility(method));
        }
        
        RefactoringResult result = RefactoringResult.success("预检查通过", new ArrayList<>());
        warnings.forEach(result::addWarning);
        return result;
    }
    
    /**
     * 执行方法迁移
     */
    private RefactoringResult performMethodMigration(CtMethod<?> method, CtClass<?> childClass, CtClass<?> parentClass) {
        try {
            logger.debug("开始方法迁移...");
            
            // 1. 克隆方法
            CtMethod<?> clonedMethod = method.clone();
            clonedMethod.setParent(parentClass);
            
            // 2. 调整可见性
            VisibilityAdjuster.VisibilityAdjustmentResult visibilityResult = 
                visibilityAdjuster.adjustVisibility(clonedMethod);
            
            if (visibilityResult.wasAdjusted()) {
                logger.info("方法可见性已调整: {}", visibilityResult.getMessage());
            }
            
            // 3. 调整返回类型以兼容其他子类
            ReturnTypeAdjuster.ReturnTypeAdjustmentResult returnTypeResult = 
                returnTypeAdjuster.adjustReturnTypeForPullUp(clonedMethod, childClass, parentClass);
            
            if (returnTypeResult.wasAdjusted()) {
                logger.info("方法返回类型已调整: {}", returnTypeResult.getMessage());
            }
            
            // 4. 处理依赖字段的自动上提
            FieldPullUpHandler.FieldPullUpResult fieldResult = 
                fieldPullUpHandler.pullUpDependentFields(clonedMethod, childClass, parentClass);
            
            // 5. 收集父类的所有子类
            List<CtClass<?>> allChildClasses = classFinder.collectAllChildClasses(parentClass);
            
            // 6. 处理依赖方法的自动上提
            MethodPullUpHandler.MethodPullUpResult methodResult = 
                methodPullUpHandler.pullUpDependentMethods(clonedMethod, childClass, parentClass, allChildClasses);
            
            // 7. 将方法添加到父类
            parentClass.addMethod(clonedMethod);
            logger.debug("方法已添加到父类: {}", parentClass.getQualifiedName());
            
            // 8. 调整其他子类中同名方法的可见性
            adjustVisibilityForConflictingMethodsInOtherChildClasses(clonedMethod, allChildClasses, childClass);
            
            // 9. 补齐导入语句
            importManager.ensureMissingImportsForMethodAndFieldsAndMethods(
                parentClass, clonedMethod, fieldResult.getPulledUpFields(), methodResult.getPulledUpMethods());
            
            // 10. 从子类中移除原方法
            childClass.removeMethod(method);
            logger.debug("方法已从子类移除: {}", childClass.getQualifiedName());
            
            // 记录结果
            logMigrationResults(fieldResult, methodResult);
            
            return RefactoringResult.success("方法迁移完成", new ArrayList<>());
            
        } catch (Exception e) {
            logger.error("方法迁移失败", e);
            return RefactoringResult.failure("方法迁移失败: " + e.getMessage());
        }
    }
    
    /**
     * 调整其他子类中同名方法的可见性
     */
    private void adjustVisibilityForConflictingMethodsInOtherChildClasses(CtMethod<?> parentMethod, 
                                                                        List<CtClass<?>> allChildClasses, 
                                                                        CtClass<?> originalChildClass) {
        try {
            String methodName = parentMethod.getSimpleName();
            List<CtParameter<?>> parentParams = parentMethod.getParameters();
            
            logger.debug("检查其他子类中的同名方法可见性冲突: {}", methodName);
            
            // 收集所有冲突的子类方法
            List<CtMethod<?>> conflictingMethods = new ArrayList<>();
            for (CtClass<?> childClass : allChildClasses) {
                if (childClass.equals(originalChildClass)) {
                    continue; // 跳过原始子类
                }
                
                // 查找同名方法
                CtMethod<?> conflictingMethod = classFinder.findMatchingMethod(childClass, methodName, parentParams);
                if (conflictingMethod != null) {
                    conflictingMethods.add(conflictingMethod);
                    logger.debug("发现子类 {} 中的同名方法: {}", childClass.getSimpleName(), methodName);
                }
            }
            
            // 如果有冲突方法，使用VisibilityHandler统一调整可见性
            if (!conflictingMethods.isEmpty()) {
                VisibilityHandler.VisibilityAdjustmentResult result = 
                    visibilityHandler.adjustMethodVisibility(parentMethod, conflictingMethods);
                
                if (result.isSuccess()) {
                    logger.info("成功调整了 {} 个子类中同名方法的可见性", conflictingMethods.size());
                    result.getAdjustments().forEach(adjustment -> logger.info("  - {}", adjustment));
                } else {
                    logger.warn("调整子类方法可见性失败: {}", result.getMessage());
                }
                
                // 为所有冲突方法添加@Override注解
                for (CtMethod<?> conflictingMethod : conflictingMethods) {
                    visibilityHandler.addOverrideAnnotationProperly(conflictingMethod, true);
                }
            }
            
        } catch (Exception e) {
            logger.warn("调整其他子类可见性时发生异常: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 记录迁移结果
     */
    private void logMigrationResults(FieldPullUpHandler.FieldPullUpResult fieldResult, 
                                   MethodPullUpHandler.MethodPullUpResult methodResult) {
        if (fieldResult.isSuccess() && !fieldResult.getPulledUpFields().isEmpty()) {
            logger.info("自动上提了 {} 个依赖字段", fieldResult.getPulledUpFields().size());
            fieldResult.getPulledUpFields().forEach(field -> 
                logger.info("  - 上提字段: {}", field.getSimpleName()));
        }
        
        fieldResult.getWarnings().forEach(warning -> logger.warn("字段上提警告: {}", warning));
        
        if (methodResult.isSuccess() && !methodResult.getPulledUpMethods().isEmpty()) {
            logger.info("自动上提了 {} 个依赖方法为抽象方法", methodResult.getPulledUpMethods().size());
            methodResult.getPulledUpMethods().forEach(pulledMethod -> 
                logger.info("  - 上提抽象方法: {}", pulledMethod.getSimpleName()));
        }
        
        methodResult.getWarnings().forEach(warning -> logger.warn("方法上提警告: {}", warning));
    }
    
    /**
     * 获取类的所有方法名称（用于CLI提示）
     */
    public List<String> getMethodNames(List<String> sourcePaths, String className) {
        try {
            CtModel model = modelBuilder.buildModel(sourcePaths);
            if (model != null) {
                CtClass<?> clazz = classFinder.findClass(model, className);
                if (clazz != null) {
                    return classFinder.getMethodNames(clazz);
                }
            }
        } catch (Exception e) {
            logger.error("获取方法名称失败", e);
        }
        
        return new ArrayList<>();
    }
    
    /**
     * 获取所有类名称（用于CLI提示）
     */
    public List<String> getClassNames(List<String> sourcePaths) {
        try {
            CtModel model = modelBuilder.buildModel(sourcePaths);
            if (model != null) {
                return classFinder.getClassNames(model);
            }
        } catch (Exception e) {
            logger.error("获取类名称失败", e);
        }
        
        return new ArrayList<>();
    }
}
