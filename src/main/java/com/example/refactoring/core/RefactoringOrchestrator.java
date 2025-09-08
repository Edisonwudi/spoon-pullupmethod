package com.example.refactoring.core;

import com.example.refactoring.analyzer.DependencyAnalyzer;
import com.example.refactoring.checker.MethodConflictChecker;
import com.example.refactoring.adjuster.VisibilityAdjuster;
import com.example.refactoring.adjuster.ReturnTypeAdjuster;
import com.example.refactoring.handler.FieldPullUpHandler;
import com.example.refactoring.handler.MethodPullUpHandler;
import com.example.refactoring.handler.VisibilityHandler;
import com.example.refactoring.handler.ThisCastFixHandler;

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
    private final SnapshotManager snapshotManager;
    private final PomDependencyManager pomDependencyManager;
    
    private final DependencyAnalyzer dependencyAnalyzer;
    private final MethodConflictChecker conflictChecker;
    private final VisibilityAdjuster visibilityAdjuster;
    private final ReturnTypeAdjuster returnTypeAdjuster;
    private final FieldPullUpHandler fieldPullUpHandler;
    private final MethodPullUpHandler methodPullUpHandler;
    private final VisibilityHandler visibilityHandler;
    private final ThisCastFixHandler thisCastFixHandler;
    
    public RefactoringOrchestrator() {
        this.modelBuilder = new ModelBuilder();
        this.classFinder = new ClassFinder();
        this.codeGenerator = new CodeGenerator();
        this.importManager = new ImportManager();
        this.snapshotManager = new SnapshotManager();
        this.pomDependencyManager = new PomDependencyManager();
        
        this.dependencyAnalyzer = new DependencyAnalyzer();
        this.conflictChecker = new MethodConflictChecker();
        this.visibilityAdjuster = new VisibilityAdjuster();
        this.returnTypeAdjuster = new ReturnTypeAdjuster();
        this.fieldPullUpHandler = new FieldPullUpHandler();
        this.methodPullUpHandler = new MethodPullUpHandler();
        this.visibilityHandler = new VisibilityHandler();
        this.thisCastFixHandler = new ThisCastFixHandler();
    }
    
    /**
     * 执行Pull-Up-Method重构（默认上提到直接父类）
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
        return pullUpMethodToAncestor(sourcePaths, childClassName, methodName, null, outputPath);
    }
    
    /**
     * 执行Pull-Up-Method重构到指定祖先类
     * 
     * @param sourcePaths 源代码路径列表
     * @param childClassName 子类名称
     * @param methodName 要上提的方法名
     * @param targetAncestorClassName 目标祖先类名称（null表示直接父类）
     * @param outputPath 输出路径（可选，null表示覆盖原文件）
     * @return 重构结果
     */
    public RefactoringResult pullUpMethodToAncestor(List<String> sourcePaths, 
                                                  String childClassName, 
                                                  String methodName, 
                                                  String targetAncestorClassName,
                                                  String outputPath) {
        try {
            String ancestorInfo = targetAncestorClassName != null ? 
                " 到祖先类=" + targetAncestorClassName : " 到直接父类";
            logger.info("开始Pull-Up-Method重构: 类={}, 方法={}{}", childClassName, methodName, ancestorInfo);
            
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
            
            // 3. 确定目标祖先类
            CtClass<?> targetAncestorClass;
            if (targetAncestorClassName == null) {
                // 默认行为：上提到直接父类
                targetAncestorClass = classFinder.getParentClass(childClass);
                if (targetAncestorClass == null) {
                    return RefactoringResult.failure("类 " + childClassName + " 没有父类或父类无法解析");
                }
            } else {
                // 查找指定的祖先类
                targetAncestorClass = classFinder.findClass(model, targetAncestorClassName);
                if (targetAncestorClass == null) {
                    return RefactoringResult.failure("找不到指定的目标祖先类: " + targetAncestorClassName);
                }
                
                // 验证是否为祖先类关系
                if (!classFinder.isAncestorClass(targetAncestorClass, childClass)) {
                    return RefactoringResult.failure("类 " + targetAncestorClassName + " 不是 " + childClassName + " 的祖先类");
                }
            }
            
            logger.info("找到目标祖先类: {}", targetAncestorClass.getQualifiedName());
            
            // 4. 执行重构前检查
            RefactoringResult checkResult = performPreChecks(targetMethod, childClass, targetAncestorClass);
            if (!checkResult.isSuccess()) {
                return checkResult;
            }
            
            // 5. 执行方法迁移
            RefactoringResult migrationResult = performMethodMigration(targetMethod, childClass, targetAncestorClass);
            if (!migrationResult.isSuccess()) {
                return migrationResult;
            }
            
            // 6. 在写入前保存快照（仅当覆盖原文件时生效）
            if (outputPath == null) {
                List<String> originals = codeGenerator.getOriginalFilePathsForModifiedClasses(
                    childClass, targetAncestorClass, classFinder);
                snapshotManager.saveSnapshot(originals, sourcePaths);
            }

            // 7. 输出结果
            List<String> modifiedFiles = codeGenerator.writeModifiedClassesOnly(
                childClass, targetAncestorClass, outputPath, sourcePaths, classFinder);
            
            // 7.5 自动修复跨模块依赖（仅在覆盖原文件时执行，避免输出目录被污染）
            if (outputPath == null && !modifiedFiles.isEmpty()) {
                pomDependencyManager.fixMissingModuleDependencies(modifiedFiles, sourcePaths);
            }

            // 7.6 清理无效的 @Override（父类为 Object 的类）
            try {
                visibilityHandler.cleanInvalidOverrides(targetAncestorClass);
                // 目标祖先类发生变化，需要再次写入
                codeGenerator.writeModifiedClassesOnly(childClass, targetAncestorClass, outputPath, sourcePaths, classFinder);
            } catch (Exception e) {
                logger.debug("清理 @Override 注解时发生异常: {}", e.getMessage());
            }

            logger.info("Pull-Up-Method重构完成，修改了 {} 个文件", modifiedFiles.size());
            
            // 构建详细的成功消息
            StringBuilder successMessage = new StringBuilder();
            successMessage.append("成功将方法 ").append(methodName)
                         .append(" 从 ").append(childClassName)
                         .append(" 上提到 ").append(targetAncestorClass.getSimpleName());
            
            return RefactoringResult.success(successMessage.toString(), modifiedFiles);
            
        } catch (Exception e) {
            logger.error("重构过程中发生异常", e);
            return RefactoringResult.failure("重构失败: " + e.getMessage());
        }
    }

    /**
     * 从快照恢复上一次重构修改的文件。
     */
    public boolean restoreSnapshot(List<String> sourcePaths) {
        try {
            return snapshotManager.restoreSnapshot(sourcePaths);
        } catch (Exception e) {
            logger.error("恢复快照时发生异常", e);
            return false;
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

            // 3.1 修复迁移后方法体内的 this 在类型期望不匹配处的用法
            thisCastFixHandler.fixThisCastsForPulledUpMethod(clonedMethod, childClass, parentClass);
            
            // 4. 处理依赖字段的自动上提
            FieldPullUpHandler.FieldPullUpResult fieldResult = 
                fieldPullUpHandler.pullUpDependentFields(clonedMethod, childClass, parentClass);
            
            // 5. 收集目标类的所有后代类（用于可见性调整）
            List<CtClass<?>> allDescendantClasses = classFinder.collectAllDescendantClasses(parentClass);
            
            // 6. 使用所有后代类（直接子类与孙子类等）用于依赖方法处理与补全
            List<CtClass<?>> allChildrenAndDescendants = allDescendantClasses;
            
            // 7. 处理依赖方法的自动上提
            MethodPullUpHandler.MethodPullUpResult methodResult = 
                methodPullUpHandler.pullUpDependentMethods(clonedMethod, childClass, parentClass, allChildrenAndDescendants);
            
            // 8. 将方法添加到父类
            parentClass.addMethod(clonedMethod);
            logger.debug("方法已添加到父类: {}", parentClass.getQualifiedName());
            
            // 9. 调整所有后代类中同名方法的可见性
            adjustVisibilityForConflictingMethodsInAllDescendants(clonedMethod, allDescendantClasses, childClass);
            
            // 10. 补齐导入语句
            importManager.ensureMissingImportsForMethodAndFieldsAndMethods(
                parentClass, clonedMethod, fieldResult.getPulledUpFields(), methodResult.getPulledUpMethods());
            
            // 11. 从子类中移除原方法
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
     * 修复：当方法被上提到目标祖先类后，方法体内的 this 在某些调用上下文中不再满足期望类型。
     * 策略：若调用实参为 this，且对应参数类型不兼容祖先类型但兼容原后代类型，则将实参替换为 (ChildClass) this。
     */
    // this-cast 修复逻辑已提取到 ThisCastFixHandler
    
    /**
     * 调整所有后代类中同名方法的可见性
     */
    private void adjustVisibilityForConflictingMethodsInAllDescendants(CtMethod<?> parentMethod, 
                                                                      List<CtClass<?>> allDescendantClasses, 
                                                                      CtClass<?> originalChildClass) {
        try {
            String methodName = parentMethod.getSimpleName();
            List<CtParameter<?>> parentParams = parentMethod.getParameters();
            
            logger.debug("检查所有后代类中的同名方法可见性冲突: {}", methodName);
            
            // 收集所有冲突的后代类方法
            List<CtMethod<?>> conflictingMethods = new ArrayList<>();
            for (CtClass<?> descendantClass : allDescendantClasses) {
                if (descendantClass.equals(originalChildClass)) {
                    continue; // 跳过原始子类（已被移除方法）
                }
                
                // 查找同名方法
                CtMethod<?> conflictingMethod = classFinder.findMatchingMethod(descendantClass, methodName, parentParams);
                if (conflictingMethod != null) {
                    conflictingMethods.add(conflictingMethod);
                    logger.debug("发现后代类 {} 中的同名方法: {}", descendantClass.getSimpleName(), methodName);
                }
            }
            
            // 如果有冲突方法，使用VisibilityHandler统一调整可见性
            if (!conflictingMethods.isEmpty()) {
                VisibilityHandler.VisibilityAdjustmentResult result = 
                    visibilityHandler.adjustMethodVisibility(parentMethod, conflictingMethods);
                
                if (result.isSuccess()) {
                    logger.info("成功调整了 {} 个后代类中同名方法的可见性", conflictingMethods.size());
                    result.getAdjustments().forEach(adjustment -> logger.info("  - {}", adjustment));
                } else {
                    logger.warn("调整后代类方法可见性失败: {}", result.getMessage());
                }
                
                // 为所有冲突方法添加@Override注解
                for (CtMethod<?> conflictingMethod : conflictingMethods) {
                    visibilityHandler.addOverrideAnnotationProperly(conflictingMethod, true);
                }
            }
            
        } catch (Exception e) {
            logger.warn("调整后代类可见性时发生异常: {}", e.getMessage(), e);
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
    
    /**
     * 获取指定类的所有祖先类名称（用于CLI选择目标祖先类）
     * 
     * @param sourcePaths 源代码路径列表
     * @param className 类名
     * @return 祖先类名称列表，从直接父类到最顶层祖先类的顺序
     */
    public List<String> getAncestorClassNames(List<String> sourcePaths, String className) {
        try {
            CtModel model = modelBuilder.buildModel(sourcePaths);
            if (model != null) {
                CtClass<?> clazz = classFinder.findClass(model, className);
                if (clazz != null) {
                    List<CtClass<?>> ancestors = classFinder.getAllAncestorClasses(clazz);
                    return ancestors.stream()
                        .map(ancestor -> ancestor.getQualifiedName())
                        .collect(java.util.stream.Collectors.toList());
                }
            }
        } catch (Exception e) {
            logger.error("获取祖先类名称失败", e);
        }
        
        return new ArrayList<>();
    }
}
