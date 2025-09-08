package com.example.refactoring.core;

import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 代码生成器
 * 负责生成完整的Java文件内容并写入文件系统
 */
public class CodeGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(CodeGenerator.class);
    
    /**
     * 只写入被修改的类，避免重写所有文件
     * 
     * @param childClass 子类
     * @param parentClass 父类
     * @param outputPath 输出路径
     * @param sourcePaths 源路径列表
     * @param classFinder 类查找器
     * @return 修改的文件列表
     */
    public List<String> writeModifiedClassesOnly(CtClass<?> childClass, CtClass<?> parentClass, 
                                                String outputPath, List<String> sourcePaths,
                                                ClassFinder classFinder) {
        List<String> modifiedFiles = new ArrayList<>();
        
        try {
            // 收集所有被修改的类
            Set<CtClass<?>> modifiedClasses = collectModifiedClasses(childClass, parentClass, classFinder);
            
            // 写入每个修改的类
            for (CtClass<?> clazz : modifiedClasses) {
                String filePath = writeClass(clazz, outputPath, sourcePaths);
                if (filePath != null) {
                    modifiedFiles.add(filePath);
                    logger.info("已修改文件: {} ({})", filePath, clazz.getSimpleName());
                }
            }
            
            logger.info("重构完成：修改了 {} 个相关类文件", modifiedFiles.size());
            
        } catch (Exception e) {
            logger.error("写入修改的类失败", e);
            // 如果失败，回退到原来的全量写入方式
            logger.warn("回退到全量文件写入方式");
            return writeAllResults(childClass.getFactory().getModel(), outputPath, sourcePaths);
        }
        
        return modifiedFiles;
    }
    
    /**
     * 写入重构结果（使用Spoon的自动import功能）
     * 
     * @param model Spoon模型
     * @param outputPath 输出路径
     * @param sourcePaths 源路径列表
     * @return 修改的文件列表
     */
    public List<String> writeAllResults(CtModel model, String outputPath, List<String> sourcePaths) {
        List<String> modifiedFiles = new ArrayList<>();
        
        try {
            // 遍历模型中的所有类型，使用自动import功能输出
            for (CtType<?> type : model.getAllTypes()) {
                String filePath = writeType(type, outputPath, sourcePaths);
                if (filePath != null) {
                    modifiedFiles.add(filePath);
                    logger.debug("已输出文件（带自动import）: {}", filePath);
                }
            }
            
            logger.info("使用自动import功能生成了 {} 个文件", modifiedFiles.size());
            
        } catch (Exception e) {
            logger.error("写入结果失败", e);
            // 如果Spoon的自动输出失败，回退到手动方式
            return fallbackWriteResults(model, outputPath, sourcePaths);
        }
        
        return modifiedFiles;
    }
    
    /**
     * 生成包含package声明、import语句和类定义的完整文件内容
     */
    public String generateFullFileContentWithAutoImports(CtType<?> type) {
        try {
            spoon.reflect.factory.Factory factory = type.getFactory();
            if (factory == null || factory.getEnvironment() == null) {
                return type.toString();
            }
            
            boolean originalAutoImports = factory.getEnvironment().isAutoImports();
            try {
                factory.getEnvironment().setAutoImports(true);
                // 使用 PrettyPrinter 打印整个编译单元，包含 package 和 imports
                spoon.reflect.cu.CompilationUnit cu = getOrCreateCompilationUnit(type, factory);
                if (cu != null) {
                    // 使用 SniperJavaPrettyPrinter 以避免丢失原有的 import static
                    spoon.support.sniper.SniperJavaPrettyPrinter sniperPrinter =
                        new spoon.support.sniper.SniperJavaPrettyPrinter(factory.getEnvironment());
                    sniperPrinter.calculate(cu, cu.getDeclaredTypes());
                    String result = sniperPrinter.getResult();
                    
                    // 修复Spoon PrettyPrinter的问题
                    result = fixOverrideAnnotationFormatting(result);
                    
                    return result;
                }
                return type.toString();
            } finally {
                factory.getEnvironment().setAutoImports(originalAutoImports);
            }
        } catch (Exception e) {
            logger.warn("生成完整文件内容失败: {}", e.getMessage());
            return type.toString();
        }
    }
    
    /**
     * 收集所有被修改的类
     */
    private Set<CtClass<?>> collectModifiedClasses(CtClass<?> childClass, CtClass<?> parentClass, 
                                                  ClassFinder classFinder) {
        Set<CtClass<?>> modifiedClasses = new HashSet<>();
        modifiedClasses.add(childClass);
        modifiedClasses.add(parentClass);
        
        // 添加所有被可见性调整影响的后代类（包括间接子类）
        List<CtClass<?>> allDescendantClasses = classFinder.collectAllDescendantClasses(parentClass);
        for (CtClass<?> descendantClass : allDescendantClasses) {
            if (!descendantClass.equals(childClass)) {
                // 检查是否有方法被修改（添加了@Override注解或调整了可见性）
                if (hasMethodModifications(descendantClass)) {
                    modifiedClasses.add(descendantClass);
                    logger.info("检测到后代类 {} 有方法修改，将包含在输出中", descendantClass.getSimpleName());
                }
            }
        }
        
        return modifiedClasses;
    }

    /**
     * 提供给外部使用：获取将被写入的原始文件的绝对路径列表（基于被修改的类集合）。
     */
    public List<String> getOriginalFilePathsForModifiedClasses(CtClass<?> childClass,
                                                               CtClass<?> parentClass,
                                                               ClassFinder classFinder) {
        List<String> files = new ArrayList<>();
        try {
            Set<CtClass<?>> modified = collectModifiedClasses(childClass, parentClass, classFinder);
            for (CtClass<?> clazz : modified) {
                if (clazz.getPosition() != null && clazz.getPosition().getFile() != null) {
                    files.add(clazz.getPosition().getFile().getAbsolutePath());
                }
            }
        } catch (Exception e) {
            logger.warn("收集原始文件路径失败: {}", e.getMessage());
        }
        return files;
    }
    
    /**
     * 检查类是否有方法修改（@Override注解或可见性调整）
     */
    private boolean hasMethodModifications(CtClass<?> clazz) {
        for (spoon.reflect.declaration.CtMethod<?> method : clazz.getMethods()) {
            // 检查是否有@Override注解
            boolean hasOverride = method.getAnnotations().stream()
                .anyMatch(annotation -> 
                    annotation.getAnnotationType().getSimpleName().equals("Override"));
            
            if (hasOverride) {
                return true;
            }
            
            // 检查是否有可见性修改
            if (method.hasModifier(spoon.reflect.declaration.ModifierKind.PUBLIC) && 
                !method.getSimpleName().equals(clazz.getSimpleName())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 写入单个类到文件
     */
    private String writeClass(CtClass<?> clazz, String outputPath, List<String> sourcePaths) {
        if (clazz.getPosition() == null || clazz.getPosition().getFile() == null) {
            return null;
        }
        
        try {
            File originalFile = clazz.getPosition().getFile();
            String content = generateFullFileContentWithAutoImports(clazz);
            
            File targetFile = determineTargetFile(originalFile, outputPath, sourcePaths);
            writeToFile(targetFile, content);
            
            return targetFile.getAbsolutePath();
        } catch (Exception e) {
            logger.error("写入类失败: {}", clazz.getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 写入单个类型到文件
     */
    private String writeType(CtType<?> type, String outputPath, List<String> sourcePaths) {
        if (type.getPosition() == null || type.getPosition().getFile() == null) {
            return null;
        }
        
        try {
            File originalFile = type.getPosition().getFile();
            String content = generateFullFileContentWithAutoImports(type);
            
            File targetFile = determineTargetFile(originalFile, outputPath, sourcePaths);
            writeToFile(targetFile, content);
            
            return targetFile.getAbsolutePath();
        } catch (Exception e) {
            logger.error("写入类型失败: {}", type.getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 确定目标文件路径
     */
    private File determineTargetFile(File originalFile, String outputPath, List<String> sourcePaths) {
        if (outputPath != null) {
            // 输出到指定目录
            String relativePath = getRelativePath(originalFile, sourcePaths);
            File targetFile = new File(outputPath, relativePath);
            targetFile.getParentFile().mkdirs();
            return targetFile;
        } else {
            // 覆盖原文件
            return originalFile;
        }
    }
    
    /**
     * 写入内容到文件
     */
    private void writeToFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
    
    /**
     * 获取或创建编译单元
     */
    private spoon.reflect.cu.CompilationUnit getOrCreateCompilationUnit(CtType<?> type, 
                                                                       spoon.reflect.factory.Factory factory) {
        if (type.getPosition() != null && type.getPosition().getCompilationUnit() != null) {
            return type.getPosition().getCompilationUnit();
        } else {
            return factory.CompilationUnit().getOrCreate(type);
        }
    }
    
    
    /**
     * 修复@Override注解粘黏问题
     */
    private String fixOverrideAnnotationFormatting(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        
        try {
            code = code.replaceAll("@Override(public|protected|private)\\s+", "@Override\n    $1 ");
            logger.debug("已修复代码中的@Override注解格式");
            return code;
        } catch (Exception e) {
            logger.warn("修复@Override注解格式时发生异常: {}", e.getMessage());
            return code;
        }
    }
    
    /**
     * 回退的文件写入方法
     */
    private List<String> fallbackWriteResults(CtModel model, String outputPath, List<String> sourcePaths) {
        List<String> modifiedFiles = new ArrayList<>();
        logger.warn("使用回退方法输出文件（不包含自动import管理）");
        
        try {
            for (CtType<?> type : model.getAllTypes()) {
                if (type.getPosition() != null && type.getPosition().getFile() != null) {
                    File originalFile = type.getPosition().getFile();
                    String content = type.toString();
                    
                    File targetFile = determineTargetFile(originalFile, outputPath, sourcePaths);
                    writeToFile(targetFile, content);
                    
                    modifiedFiles.add(targetFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            logger.error("回退写入方法也失败", e);
        }
        
        return modifiedFiles;
    }
    
    /**
     * 获取文件的相对路径
     */
    private String getRelativePath(File file, List<String> sourcePaths) {
        for (String sourcePath : sourcePaths) {
            File sourceDir = new File(sourcePath);
            if (file.getAbsolutePath().startsWith(sourceDir.getAbsolutePath())) {
                return file.getAbsolutePath().substring(sourceDir.getAbsolutePath().length() + 1);
            }
        }
        return file.getName();
    }
}
