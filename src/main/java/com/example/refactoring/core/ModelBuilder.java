package com.example.refactoring.core;

import spoon.Launcher;
import spoon.reflect.CtModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Spoon代码模型构建器
 * 负责根据源代码路径构建Spoon模型
 */
public class ModelBuilder {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelBuilder.class);
    
    /**
     * 构建Spoon代码模型
     * 
     * @param sourcePaths 源代码路径列表
     * @return 构建的CtModel，如果失败返回null
     */
    public CtModel buildModel(List<String> sourcePaths) {
        try {
            Launcher launcher = new Launcher();
            
            // 启用自动import管理
            launcher.getEnvironment().setAutoImports(true);
            // 使用 Sniper 打印器以尽量保持原有导入与格式
            launcher.getEnvironment().setPrettyPrinterCreator(() -> 
                new spoon.support.sniper.SniperJavaPrettyPrinter(launcher.getEnvironment()));
            // 启用注释保留
            launcher.getEnvironment().setCommentEnabled(true);
            // 设置代码合规性检查级别
            launcher.getEnvironment().setComplianceLevel(11);
            
            // 设置源码路径（包含自动发现的多模块源目录）
            List<String> allSources = new ArrayList<>();
            for (String path : sourcePaths) {
                File file = new File(path);
                if (file.exists()) {
                    allSources.add(file.getAbsolutePath());
                    allSources.addAll(discoverModuleSourceRoots(file));
                } else {
                    logger.warn("源码路径不存在: {}", path);
                }
            }
            // 去重并添加
            Set<String> uniqueSources = new HashSet<>(allSources);
            for (String src : uniqueSources) {
                launcher.addInputResource(src);
                logger.debug("添加源码目录: {}", src);
            }

            // 设置 sourceClasspath（指向各模块 target/classes，如存在）
            List<String> classpath = discoverModuleClasses(uniqueSources);
            if (!classpath.isEmpty()) {
                launcher.getEnvironment().setSourceClasspath(classpath.toArray(new String[0]));
                logger.info("设置 sourceClasspath 项数: {}", classpath.size());
            }
            
            // 构建模型
            launcher.buildModel();
            return launcher.getModel();
            
        } catch (Exception e) {
            logger.error("构建代码模型失败", e);
            return null;
        }
    }

    /**
     * 发现多模块源码根目录（形如 <module>/src/main/java）
     */
    private List<String> discoverModuleSourceRoots(File root) {
        List<String> sources = new ArrayList<>();
        try {
            if (root.isDirectory()) {
                // 当前目录自身是否有 src/main/java
                File srcMainJava = new File(root, "src/main/java");
                if (srcMainJava.exists() && srcMainJava.isDirectory()) {
                    sources.add(srcMainJava.getAbsolutePath());
                }
                // 遍历一级子目录作为潜在模块
                File[] children = root.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (child.isDirectory()) {
                            File childSrc = new File(child, "src/main/java");
                            if (childSrc.exists() && childSrc.isDirectory()) {
                                sources.add(childSrc.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("发现模块源码目录失败: {}", e.getMessage());
        }
        return sources;
    }

    /**
     * 发现多模块编译输出目录（形如 <module>/target/classes）
     */
    private List<String> discoverModuleClasses(Set<String> sourceRoots) {
        List<String> classpath = new ArrayList<>();
        try {
            Set<File> visitedModules = new HashSet<>();
            for (String src : sourceRoots) {
                File srcDir = new File(src);
                // 推断模块根：去掉 /src/main/java
                File moduleRoot = srcDir.getParentFile() != null ? srcDir.getParentFile().getParentFile() : null;
                if (moduleRoot != null && moduleRoot.isDirectory() && visitedModules.add(moduleRoot)) {
                    File classesDir = new File(moduleRoot, "target/classes");
                    if (classesDir.exists() && classesDir.isDirectory()) {
                        classpath.add(classesDir.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("发现模块类路径失败: {}", e.getMessage());
        }
        return classpath;
    }
}
