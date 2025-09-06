package com.example.refactoring;

import com.example.refactoring.core.PullUpMethodRefactoring;
import com.example.refactoring.core.RefactoringResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 方法依赖自动上提功能测试
 */
public class MethodDependencyTest {
    
    @TempDir
    Path tempDir;
    
    private PullUpMethodRefactoring refactoring;
    
    @BeforeEach
    void setUp() {
        refactoring = new PullUpMethodRefactoring();
    }
    
    @Test
    void testPullUpMethodWithMethodDependency() throws Exception {
        // 创建测试文件
        File parentFile = createBaseService();
        File childFile = createChildServiceWithMethodDependency();
        
        // 执行重构：将 ChildService.processRequest() 上提到 BaseService
        RefactoringResult result = refactoring.pullUpMethod(
            Arrays.asList(parentFile.getAbsolutePath(), childFile.getAbsolutePath()),
            "ChildService",
            "processRequest",
            null // 覆盖原文件
        );
        
        // 验证结果
        assertTrue(result.isSuccess(), "重构应该成功: " + result.getMessage());
        assertFalse(result.getModifiedFiles().isEmpty(), "应该有文件被修改");
        
        // 验证父类文件的内容
        File modifiedParentFile = new File(parentFile.getAbsolutePath());
        String parentContent = java.nio.file.Files.readString(modifiedParentFile.toPath());
        
        System.out.println("\n=== 父类文件内容 ===");
        System.out.println(parentContent);
        
        // 验证父类被设置为抽象类
        assertTrue(parentContent.contains("public abstract class BaseService"), 
                  "父类应该被设置为抽象类");
        
        // 验证依赖方法被上提为抽象方法
        assertTrue(parentContent.contains("protected abstract String validateData(String data);"), 
                  "依赖方法应该被上提为抽象方法");
        
        // 验证主方法被上提
        assertTrue(parentContent.contains("public String processRequest(String request)"), 
                  "主方法应该被上提到父类");
        
        // 验证子类文件的内容
        File modifiedChildFile = new File(childFile.getAbsolutePath());
        String childContent = java.nio.file.Files.readString(modifiedChildFile.toPath());
        
        System.out.println("\n=== 子类文件内容 ===");
        System.out.println(childContent);
        
        // 验证子类的依赖方法有 @Override 注解和正确的可见性
        assertTrue(childContent.contains("@Override") || childContent.contains("@java.lang.Override"), 
                  "子类的依赖方法应该有 @Override 注解");
        assertTrue(childContent.contains("protected String validateData(String data)"), 
                  "子类的依赖方法应该是 protected");
        
        System.out.println("重构结果: " + result.getMessage());
        result.getModifiedFiles().forEach(file -> 
            System.out.println("修改文件: " + file));
    }
    
    private File createBaseService() throws Exception {
        File file = new File(tempDir.toFile(), "BaseService.java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "public class BaseService {\n\n" +
                        "    protected String serviceName = \"base\";\n\n" +
                        "    public void init() {\n" +
                        "        System.out.println(\"Service initialized: \" + serviceName);\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }
    
    private File createChildServiceWithMethodDependency() throws Exception {
        File file = new File(tempDir.toFile(), "ChildService.java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "import java.util.List;\n" +
                        "import java.util.ArrayList;\n\n" +
                        "public class ChildService extends BaseService {\n\n" +
                        "    private List<String> cache = new ArrayList<>();\n\n" +
                        "    public String processRequest(String request) {\n" +
                        "        // 这个方法依赖子类的其他方法\n" +
                        "        String validated = validateData(request);\n" +
                        "        cache.add(validated);\n" +
                        "        return \"Processed: \" + validated;\n" +
                        "    }\n\n" +
                        "    private String validateData(String data) {\n" +
                        "        if (data == null || data.trim().isEmpty()) {\n" +
                        "            throw new IllegalArgumentException(\"Invalid data\");\n" +
                        "        }\n" +
                        "        return data.trim().toLowerCase();\n" +
                        "    }\n\n" +
                        "    public void clearCache() {\n" +
                        "        cache.clear();\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }
}
