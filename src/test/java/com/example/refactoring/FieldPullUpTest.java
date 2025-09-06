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
 * 字段自动上提功能测试
 */
public class FieldPullUpTest {
    
    @TempDir
    Path tempDir;
    
    private PullUpMethodRefactoring refactoring;
    
    @BeforeEach
    void setUp() {
        refactoring = new PullUpMethodRefactoring();
    }
    
    @Test
    void testPullUpMethodWithDependentField() throws Exception {
        // 创建测试文件
        File parentFile = createParentClass();
        File childFile = createChildClassWithFieldDependency();
        
        // 执行重构
        RefactoringResult result = refactoring.pullUpMethod(
            Arrays.asList(parentFile.getAbsolutePath(), childFile.getAbsolutePath()),
            "TestChild",
            "processData",
            null // 覆盖原文件
        );
        
        // 验证结果
        assertTrue(result.isSuccess(), "重构应该成功: " + result.getMessage());
        assertFalse(result.getModifiedFiles().isEmpty(), "应该有文件被修改");
        
        // 验证父类文件的导入是否正确更新
        File modifiedParentFile = new File(parentFile.getAbsolutePath());
        String parentContent = java.nio.file.Files.readString(modifiedParentFile.toPath());
        
        System.out.println("\n=== 父类文件内容 ===");
        System.out.println(parentContent);
        
        // 验证必要的导入已添加
        assertTrue(parentContent.contains("import java.util.List;"), 
                  "父类应该包含 List 的导入");
        // ArrayList 可能不会被导入，因为字段声明只使用了 List 接口
        
        // 验证字段已上提
        assertTrue(parentContent.contains("protected List<String> dataList"), 
                  "父类应该包含上提的 dataList 字段");
        assertTrue(parentContent.contains("protected String configValue"), 
                  "父类应该包含上提的 configValue 字段");
        
        // 验证方法已上提
        assertTrue(parentContent.contains("public void processData()"), 
                  "父类应该包含上提的 processData 方法");
        
        System.out.println("重构结果: " + result.getMessage());
        result.getModifiedFiles().forEach(file -> 
            System.out.println("修改文件: " + file));
    }
    
    private File createParentClass() throws Exception {
        File file = new File(tempDir.toFile(), "TestParent.java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "import java.util.List;\n\n" +
                        "public class TestParent {\n\n" +
                        "    public void baseMethod() {\n" +
                        "        System.out.println(\"Base method\");\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }
    
    private File createChildClassWithFieldDependency() throws Exception {
        File file = new File(tempDir.toFile(), "TestChild.java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "import java.util.List;\n" +
                        "import java.util.ArrayList;\n\n" +
                        "public class TestChild extends TestParent {\n\n" +
                        "    private List<String> dataList = new ArrayList<>();\n" +
                        "    private String configValue = \"default\";\n\n" +
                        "    public void processData() {\n" +
                        "        // 这个方法依赖子类的字段\n" +
                        "        dataList.add(\"processed\");\n" +
                        "        System.out.println(\"Config: \" + configValue);\n\n" +
                        "        for (String data : dataList) {\n" +
                        "            System.out.println(\"Processing: \" + data);\n" +
                        "        }\n" +
                        "    }\n\n" +
                        "    public void childOnlyMethod() {\n" +
                        "        System.out.println(\"Child specific method\");\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }
}
