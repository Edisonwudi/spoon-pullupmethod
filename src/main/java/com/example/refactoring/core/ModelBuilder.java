package com.example.refactoring.core;

import spoon.Launcher;
import spoon.reflect.CtModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

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
            
            // 设置源码路径
            for (String path : sourcePaths) {
                File file = new File(path);
                if (file.exists()) {
                    launcher.addInputResource(path);
                } else {
                    logger.warn("源码路径不存在: {}", path);
                }
            }
            
            // 构建模型
            launcher.buildModel();
            return launcher.getModel();
            
        } catch (Exception e) {
            logger.error("构建代码模型失败", e);
            return null;
        }
    }
}
