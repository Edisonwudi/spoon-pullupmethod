package com.example.refactoring;

import com.example.refactoring.core.RefactoringOrchestrator;
import com.example.refactoring.core.RefactoringResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 测试上提方法到任意祖先类的功能
 */
public class AncestorPullUpIntegrationTest {
    
    private RefactoringOrchestrator orchestrator;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        orchestrator = new RefactoringOrchestrator();
    }
    
    @Test
    void testGetAncestorClassNames() {
        // 测试获取祖先类列表
        List<String> sourcePaths = Arrays.asList("src/test/resources/examples");
        List<String> ancestors = orchestrator.getAncestorClassNames(sourcePaths, "AdvancedDog");
        
        assertFalse(ancestors.isEmpty(), "AdvancedDog应该有祖先类");
        assertTrue(ancestors.contains("examples.Mammal"), "应该包含直接父类Mammal");
        assertTrue(ancestors.contains("examples.Animal"), "应该包含祖先类Animal");
        
        // 验证顺序：应该是从直接父类到最顶层祖先类
        assertEquals("examples.Mammal", ancestors.get(0), "第一个应该是直接父类");
        assertEquals("examples.Animal", ancestors.get(1), "第二个应该是祖先类");
    }
    
    @Test
    void testPullUpToDirectParent() throws IOException {
        // 测试默认行为：上提到直接父类
        copyTestFiles();
        
        List<String> sourcePaths = Arrays.asList(tempDir.toString());
        RefactoringResult result = orchestrator.pullUpMethod(
            sourcePaths, "AdvancedDog", "pant", tempDir.toString()
        );
        
        assertTrue(result.isSuccess(), "重构应该成功");
        assertTrue(result.getMessage().contains("Mammal"), "应该上提到Mammal类");
        
        // 验证方法已添加到Mammal类
        String mammalContent = Files.readString(tempDir.resolve("Mammal.java"));
        assertTrue(mammalContent.contains("public void pant()"), "Mammal类应该包含pant方法");
        
        // 验证方法已从AdvancedDog类移除
        String dogContent = Files.readString(tempDir.resolve("AdvancedDog.java"));
        assertFalse(dogContent.contains("public void pant()"), "AdvancedDog类不应该再包含pant方法");
    }
    
    @Test
    void testPullUpToSpecificAncestor() throws IOException {
        // 测试新功能：上提到指定祖先类
        copyTestFiles();
        
        List<String> sourcePaths = Arrays.asList(tempDir.toString());
        RefactoringResult result = orchestrator.pullUpMethodToAncestor(
            sourcePaths, "AdvancedDog", "move", "examples.Animal", tempDir.toString()
        );
        
        assertTrue(result.isSuccess(), "重构应该成功");
        assertTrue(result.getMessage().contains("Animal"), "应该上提到Animal类");
        
        // 验证方法已添加到Animal类
        String animalContent = Files.readString(tempDir.resolve("Animal.java"));
        assertTrue(animalContent.contains("public void move()"), "Animal类应该包含move方法");
        
        // 验证方法已从AdvancedDog类移除
        String dogContent = Files.readString(tempDir.resolve("AdvancedDog.java"));
        assertFalse(dogContent.contains("public void move()"), "AdvancedDog类不应该再包含move方法");
    }
    
    @Test
    void testInvalidAncestorClass() {
        // 测试无效祖先类的错误处理
        List<String> sourcePaths = Arrays.asList("src/test/resources/examples");
        RefactoringResult result = orchestrator.pullUpMethodToAncestor(
            sourcePaths, "AdvancedDog", "move", "examples.Cat", null
        );
        
        assertFalse(result.isSuccess(), "重构应该失败");
        assertTrue(result.getMessage().contains("不是") && result.getMessage().contains("祖先类"), 
                  "错误消息应该说明不是祖先类");
    }
    
    @Test
    void testNonExistentAncestorClass() {
        // 测试不存在的祖先类
        List<String> sourcePaths = Arrays.asList("src/test/resources/examples");
        RefactoringResult result = orchestrator.pullUpMethodToAncestor(
            sourcePaths, "AdvancedDog", "move", "examples.NonExistent", null
        );
        
        assertFalse(result.isSuccess(), "重构应该失败");
        assertTrue(result.getMessage().contains("找不到"), "错误消息应该说明找不到类");
    }
    
    @Test
    void testBackwardCompatibility() throws IOException {
        // 测试向后兼容性：原有的pullUpMethod方法应该仍然工作
        copyTestFiles();
        
        List<String> sourcePaths = Arrays.asList(tempDir.toString());
        RefactoringResult result = orchestrator.pullUpMethod(
            sourcePaths, "AdvancedDog", "pant", tempDir.toString()
        );
        
        assertTrue(result.isSuccess(), "原有方法应该仍然工作");
        
        // 验证结果与调用pullUpMethodToAncestor(null)相同
        copyTestFiles(); // 重新复制文件
        RefactoringResult result2 = orchestrator.pullUpMethodToAncestor(
            sourcePaths, "AdvancedDog", "pant", null, tempDir.toString()
        );
        
        assertTrue(result2.isSuccess(), "新方法传入null应该有相同效果");
        assertEquals(result.getMessage(), result2.getMessage(), "两种调用方式应该产生相同结果");
    }
    
    /**
     * 将测试文件复制到临时目录
     */
    private void copyTestFiles() throws IOException {
        // 复制测试文件到临时目录
        copyFile("src/test/resources/examples/Animal.java", "Animal.java");
        copyFile("src/test/resources/examples/Mammal.java", "Mammal.java");
        copyFile("src/test/resources/examples/AdvancedDog.java", "AdvancedDog.java");
    }
    
    private void copyFile(String sourcePath, String targetName) throws IOException {
        Path source = Path.of(sourcePath);
        Path target = tempDir.resolve(targetName);
        if (Files.exists(target)) {
            Files.delete(target);
        }
        Files.copy(source, target);
    }
}
