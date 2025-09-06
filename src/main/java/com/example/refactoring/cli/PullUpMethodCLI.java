package com.example.refactoring.cli;

import com.example.refactoring.core.PullUpMethodRefactoring;
import com.example.refactoring.core.RefactoringResult;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pull-Up-Method 重构工具命令行接口
 */
public class PullUpMethodCLI {
    
    private static final Logger logger = LoggerFactory.getLogger(PullUpMethodCLI.class);
    
    private static final String VERSION = "1.0.0";
    
    public static void main(String[] args) {
        PullUpMethodCLI cli = new PullUpMethodCLI();
        cli.run(args);
    }
    
    public void run(String[] args) {
        Options options = createOptions();
        
        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            
            // 处理帮助和版本选项
            if (cmd.hasOption("help")) {
                printHelp(options);
                return;
            }
            
            if (cmd.hasOption("version")) {
                printVersion();
                return;
            }
            
            // 验证必需参数（对于列表操作，不需要所有参数）
            boolean listClasses = cmd.hasOption("list-classes");
            boolean listMethods = cmd.hasOption("list-methods");
            
            if (!cmd.hasOption("source")) {
                System.err.println("错误: 缺少必需的参数 --source");
                printHelp(options);
                System.exit(1);
            }
            
            if (!listClasses && !listMethods && (!cmd.hasOption("class") || !cmd.hasOption("method"))) {
                System.err.println("错误: 缺少必需的参数 --class 和 --method");
                printHelp(options);
                System.exit(1);
            }
            
            // 解析参数
            List<String> sourcePaths = parseSourcePaths(cmd.getOptionValue("source"));
            String className = cmd.getOptionValue("class");
            String methodName = cmd.getOptionValue("method");
            String outputPath = cmd.getOptionValue("output");
            boolean verbose = cmd.hasOption("verbose");
            
            // 设置日志级别
            if (verbose) {
                System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
            }
            
            // 验证源码路径
            if (!validateSourcePaths(sourcePaths)) {
                System.exit(1);
            }
            
            PullUpMethodRefactoring refactoring = new PullUpMethodRefactoring();
            
            // 处理列表选项
            if (listClasses) {
                listClasses(refactoring, sourcePaths);
                return;
            }
            
            if (listMethods) {
                listMethods(refactoring, sourcePaths, className);
                return;
            }
            
            // 执行重构
            executeRefactoring(refactoring, sourcePaths, className, methodName, outputPath);
            
        } catch (ParseException e) {
            System.err.println("参数解析错误: " + e.getMessage());
            printHelp(options);
            System.exit(1);
        } catch (Exception e) {
            logger.error("程序执行失败", e);
            System.err.println("执行失败: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * 创建命令行选项
     */
    private Options createOptions() {
        Options options = new Options();
        
        // 必需选项
        options.addOption(Option.builder("s")
            .longOpt("source")
            .hasArg()
            .argName("paths")
            .desc("源代码路径，多个路径用逗号分隔")
            .build());
        
        options.addOption(Option.builder("c")
            .longOpt("class")
            .hasArg()
            .argName("className")
            .desc("包含要上提方法的子类名称")
            .build());
        
        options.addOption(Option.builder("m")
            .longOpt("method")
            .hasArg()
            .argName("methodName")
            .desc("要上提的方法名称")
            .build());
        
        // 可选选项
        options.addOption(Option.builder("o")
            .longOpt("output")
            .hasArg()
            .argName("path")
            .desc("输出目录路径（默认覆盖原文件）")
            .build());
        
        options.addOption(Option.builder("v")
            .longOpt("verbose")
            .desc("启用详细输出")
            .build());
        
        // 工具选项
        options.addOption(Option.builder()
            .longOpt("list-classes")
            .desc("列出所有可用的类")
            .build());
        
        options.addOption(Option.builder()
            .longOpt("list-methods")
            .desc("列出指定类的所有方法（需要配合 --class 使用）")
            .build());
        
        // 帮助和版本
        options.addOption(Option.builder("h")
            .longOpt("help")
            .desc("显示帮助信息")
            .build());
        
        options.addOption(Option.builder()
            .longOpt("version")
            .desc("显示版本信息")
            .build());
        
        return options;
    }
    
    /**
     * 解析源码路径
     */
    private List<String> parseSourcePaths(String sourcePathsStr) {
        if (sourcePathsStr == null || sourcePathsStr.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        return Arrays.asList(sourcePathsStr.split(","))
            .stream()
            .map(String::trim)
            .filter(path -> !path.isEmpty())
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 验证源码路径
     */
    private boolean validateSourcePaths(List<String> sourcePaths) {
        if (sourcePaths.isEmpty()) {
            System.err.println("错误: 未指定源码路径");
            return false;
        }
        
        boolean allValid = true;
        for (String path : sourcePaths) {
            File file = new File(path);
            if (!file.exists()) {
                System.err.println("错误: 源码路径不存在: " + path);
                allValid = false;
            }
        }
        
        return allValid;
    }
    
    /**
     * 列出所有类
     */
    private void listClasses(PullUpMethodRefactoring refactoring, List<String> sourcePaths) {
        System.out.println("正在扫描类...");
        List<String> classNames = refactoring.getClassNames(sourcePaths);
        
        if (classNames.isEmpty()) {
            System.out.println("未找到任何类");
        } else {
            System.out.println("找到的类:");
            classNames.forEach(className -> System.out.println("  " + className));
        }
    }
    
    /**
     * 列出指定类的方法
     */
    private void listMethods(PullUpMethodRefactoring refactoring, List<String> sourcePaths, String className) {
        if (className == null || className.trim().isEmpty()) {
            System.err.println("错误: 使用 --list-methods 时必须指定 --class 参数");
            return;
        }
        
        System.out.println("正在扫描类 " + className + " 的方法...");
        List<String> methodNames = refactoring.getMethodNames(sourcePaths, className);
        
        if (methodNames.isEmpty()) {
            System.out.println("在类 " + className + " 中未找到任何方法");
        } else {
            System.out.println("在类 " + className + " 中找到的方法:");
            methodNames.forEach(methodName -> System.out.println("  " + methodName));
        }
    }
    
    /**
     * 执行重构
     */
    private void executeRefactoring(PullUpMethodRefactoring refactoring, 
                                  List<String> sourcePaths, 
                                  String className, 
                                  String methodName, 
                                  String outputPath) {
        System.out.println("开始执行 Pull-Up-Method 重构...");
        System.out.println("  源码路径: " + sourcePaths);
        System.out.println("  目标类: " + className);
        System.out.println("  目标方法: " + methodName);
        if (outputPath != null) {
            System.out.println("  输出路径: " + outputPath);
        }
        System.out.println();
        
        RefactoringResult result = refactoring.pullUpMethod(sourcePaths, className, methodName, outputPath);
        
        if (result.isSuccess()) {
            System.out.println("✓ 重构成功!");
            System.out.println("  " + result.getMessage());
            
            if (!result.getModifiedFiles().isEmpty()) {
                System.out.println("  修改的文件:");
                result.getModifiedFiles().forEach(file -> System.out.println("    " + file));
            }
            
            if (!result.getWarnings().isEmpty()) {
                System.out.println("  警告:");
                result.getWarnings().forEach(warning -> System.out.println("    ⚠ " + warning));
            }
        } else {
            System.err.println("✗ 重构失败!");
            System.err.println("  " + result.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * 打印帮助信息
     */
    private void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(100);
        
        System.out.println("Pull-Up-Method 重构工具 v" + VERSION);
        System.out.println("基于 Spoon 的自动化代码重构工具");
        System.out.println();
        
        formatter.printHelp("java -jar pull-up-method-refactoring.jar", options);
        
        System.out.println();
        System.out.println("示例:");
        System.out.println("  # 执行重构");
        System.out.println("  java -jar tool.jar -s src/main/java -c com.example.Child -m methodToMove");
        System.out.println();
        System.out.println("  # 列出所有类");
        System.out.println("  java -jar tool.jar -s src/main/java --list-classes");
        System.out.println();
        System.out.println("  # 列出类的所有方法");
        System.out.println("  java -jar tool.jar -s src/main/java -c com.example.Child --list-methods");
        System.out.println();
        System.out.println("  # 输出到指定目录");
        System.out.println("  java -jar tool.jar -s src/main/java -c com.example.Child -m methodToMove -o output/");
    }
    
    /**
     * 打印版本信息
     */
    private void printVersion() {
        System.out.println("Pull-Up-Method 重构工具 v" + VERSION);
        System.out.println("基于 Spoon " + getSpoonVersion());
        System.out.println("Copyright (c) 2024");
    }
    
    /**
     * 获取Spoon版本
     */
    private String getSpoonVersion() {
        try {
            Package spoonPackage = spoon.Launcher.class.getPackage();
            String version = spoonPackage.getImplementationVersion();
            return version != null ? version : "未知版本";
        } catch (Exception e) {
            return "未知版本";
        }
    }
}
