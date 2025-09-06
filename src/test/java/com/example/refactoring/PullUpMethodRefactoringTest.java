package com.example.refactoring;

import com.example.refactoring.core.PullUpMethodRefactoring;
import com.example.refactoring.core.RefactoringResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pull-Up-Method重构工具测试类
 */
public class PullUpMethodRefactoringTest {
    
    private PullUpMethodRefactoring refactoring;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        refactoring = new PullUpMethodRefactoring();
    }
    
    @Test
    void testSuccessfulPullUp() throws IOException {
        // 创建测试文件
        createTestFiles();
        
        List<String> sourcePaths = List.of(tempDir.toString());
        String className = "examples.Dog";
        String methodName = "eat";
        
        RefactoringResult result = refactoring.pullUpMethod(sourcePaths, className, methodName, null);
        
        assertTrue(result.isSuccess(), "重构应该成功");
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("成功"));
    }
    
    @Test
    void testClassNotFound() {
        List<String> sourcePaths = List.of("src/test/resources/examples");
        String className = "NonExistentClass";
        String methodName = "someMethod";
        
        RefactoringResult result = refactoring.pullUpMethod(sourcePaths, className, methodName, null);
        
        assertFalse(result.isSuccess(), "应该失败，因为类不存在");
        assertTrue(result.getMessage().contains("找不到"));
    }
    
    @Test
    void testMethodNotFound() {
        List<String> sourcePaths = List.of("src/test/resources/examples");
        String className = "examples.Dog";
        String methodName = "nonExistentMethod";
        
        RefactoringResult result = refactoring.pullUpMethod(sourcePaths, className, methodName, null);
        
        assertFalse(result.isSuccess(), "应该失败，因为方法不存在");
        assertTrue(result.getMessage().contains("找不到方法"));
    }
    
    @Test
    void testGetClassNames() {
        List<String> sourcePaths = List.of("src/test/resources/examples");
        List<String> classNames = refactoring.getClassNames(sourcePaths);
        
        assertFalse(classNames.isEmpty(), "应该找到类");
        assertTrue(classNames.stream().anyMatch(name -> name.contains("Animal")));
        assertTrue(classNames.stream().anyMatch(name -> name.contains("Dog")));
        assertTrue(classNames.stream().anyMatch(name -> name.contains("Cat")));
    }
    
    @Test
    void testGetMethodNames() {
        List<String> sourcePaths = List.of("src/test/resources/examples");
        String className = "examples.Dog";
        List<String> methodNames = refactoring.getMethodNames(sourcePaths, className);
        
        assertFalse(methodNames.isEmpty(), "应该找到方法");
        assertTrue(methodNames.contains("eat"));
        assertTrue(methodNames.contains("drink"));
        assertTrue(methodNames.contains("bark"));
    }
    
    private void createTestFiles() throws IOException {
        // 创建包目录
        Path examplesDir = tempDir.resolve("examples");
        Files.createDirectories(examplesDir);
        
        // 创建Animal.java
        String animalCode = "package examples;\n\n" +
            "public class Animal {\n" +
            "    protected String name;\n" +
            "    protected int age;\n" +
            "    \n" +
            "    public Animal(String name, int age) {\n" +
            "        this.name = name;\n" +
            "        this.age = age;\n" +
            "    }\n" +
            "    \n" +
            "    public String getName() {\n" +
            "        return name;\n" +
            "    }\n" +
            "    \n" +
            "    public int getAge() {\n" +
            "        return age;\n" +
            "    }\n" +
            "}\n";
        Files.writeString(examplesDir.resolve("Animal.java"), animalCode);
        
        // 创建Dog.java
        String dogCode = "package examples;\n\n" +
            "public class Dog extends Animal {\n" +
            "    public Dog(String name, int age) {\n" +
            "        super(name, age);\n" +
            "    }\n" +
            "    \n" +
            "    public void eat() {\n" +
            "        System.out.println(name + \" is eating...\");\n" +
            "    }\n" +
            "    \n" +
            "    public void bark() {\n" +
            "        System.out.println(name + \" is barking!\");\n" +
            "    }\n" +
            "}\n";
        Files.writeString(examplesDir.resolve("Dog.java"), dogCode);
    }
}
