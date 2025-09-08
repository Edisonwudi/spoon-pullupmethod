package com.example.refactoring.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Pull-Up-Method 重构工具核心类
 * 这是一个向后兼容的包装器，实际工作委托给 RefactoringOrchestrator
 * 
 * @deprecated 建议直接使用 RefactoringOrchestrator
 */
public class PullUpMethodRefactoring {
    
    private static final Logger logger = LoggerFactory.getLogger(PullUpMethodRefactoring.class);
    
    private final RefactoringOrchestrator orchestrator;
    
    public PullUpMethodRefactoring() {
        this.orchestrator = new RefactoringOrchestrator();
        logger.warn("PullUpMethodRefactoring 已被弃用，建议使用 RefactoringOrchestrator");
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
        return orchestrator.pullUpMethod(sourcePaths, childClassName, methodName, outputPath);
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
        return orchestrator.pullUpMethodToAncestor(sourcePaths, childClassName, methodName, targetAncestorClassName, outputPath);
    }
    
    /**
     * 获取类的所有方法名称（用于CLI提示）
     */
    public List<String> getMethodNames(List<String> sourcePaths, String className) {
        return orchestrator.getMethodNames(sourcePaths, className);
    }
    
    /**
     * 获取所有类名称（用于CLI提示）
     */
    public List<String> getClassNames(List<String> sourcePaths) {
        return orchestrator.getClassNames(sourcePaths);
    }
    
    /**
     * 获取指定类的所有祖先类名称（用于CLI选择目标祖先类）
     */
    public List<String> getAncestorClassNames(List<String> sourcePaths, String className) {
        return orchestrator.getAncestorClassNames(sourcePaths, className);
    }

    /**
     * 从快照恢复上一次重构修改的文件（CLI 使用）。
     */
    public boolean restoreSnapshot(List<String> sourcePaths) {
        return orchestrator.restoreSnapshot(sourcePaths);
    }
}