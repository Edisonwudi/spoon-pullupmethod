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
 * Super调用处理功能测试
 */
public class SuperCallHandlingTest {
    
    @TempDir
    Path tempDir;
    
    private PullUpMethodRefactoring refactoring;
    
    @BeforeEach
    void setUp() {
        refactoring = new PullUpMethodRefactoring();
    }
    
    @Test
    void testPullUpMethodWithSuperCallConflict() throws Exception {
        // 创建测试文件 - 模拟ODG图形类的层次结构
        File grandParentFile = createBaseFigure();
        File parentFile = createAttributedFigure();
        File childFile = createEllipseFigureWithSuperCall();
        
        // 执行重构：将 EllipseFigure.transform() 上提到 AttributedFigure
        RefactoringResult result = refactoring.pullUpMethod(
            Arrays.asList(grandParentFile.getAbsolutePath(), 
                         parentFile.getAbsolutePath(), 
                         childFile.getAbsolutePath()),
            "EllipseFigure",
            "transform",
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
        
        // 验证父类存在（可能是抽象的，也可能不是，取决于是否还有其他抽象方法）
        assertTrue(parentContent.contains("class AttributedFigure"), 
                  "父类应该存在");
        
        // 验证依赖方法被上提并提供默认实现
        assertTrue(parentContent.contains("protected void invalidate()") && 
                   !parentContent.contains("abstract void invalidate()"), 
                  "invalidate方法应该被上提并提供默认实现");
        
        // 验证主方法被上提
        assertTrue(parentContent.contains("public void transform(AffineTransform tx)"), 
                  "transform方法应该被上提到父类");
        
        // 验证子类文件的内容
        File modifiedChildFile = new File(childFile.getAbsolutePath());
        String childContent = java.nio.file.Files.readString(modifiedChildFile.toPath());
        
        System.out.println("\n=== 子类文件内容 ===");
        System.out.println(childContent);
        
        // 验证子类的super调用仍然存在且可以正常工作（最佳实践）
        // 因为父类现在有默认实现，子类的super调用是安全的
        assertTrue(childContent.contains("super.invalidate()"), 
                   "子类应该仍然包含super.invalidate()调用，因为父类有默认实现");
        
        // 验证子类方法有正确的@Override注解
        assertTrue(childContent.contains("@Override") || childContent.contains("@java.lang.Override"), 
                  "子类的invalidate方法应该有@Override注解");
        
        System.out.println("重构结果: " + result.getMessage());
        result.getModifiedFiles().forEach(file -> 
            System.out.println("修改文件: " + file));
    }
    
    private File createBaseFigure() throws Exception {
        File file = new File(tempDir.toFile(), "BaseFigure.java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "import java.awt.geom.AffineTransform;\n\n" +
                        "public class BaseFigure {\n\n" +
                        "    protected boolean needsDisplay = false;\n\n" +
                        "    public void invalidate() {\n" +
                        "        needsDisplay = true;\n" +
                        "        System.out.println(\"BaseFigure invalidated\");\n" +
                        "    }\n\n" +
                        "    public void repaint() {\n" +
                        "        System.out.println(\"BaseFigure repaint\");\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }
    
    private File createAttributedFigure() throws Exception {
        File file = new File(tempDir.toFile(), "AttributedFigure.java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "import java.awt.geom.AffineTransform;\n" +
                        "import java.util.Map;\n" +
                        "import java.util.HashMap;\n\n" +
                        "public class AttributedFigure extends BaseFigure {\n\n" +
                        "    protected Map<String, Object> attributes = new HashMap<>();\n\n" +
                        "    public void setAttribute(String key, Object value) {\n" +
                        "        attributes.put(key, value);\n" +
                        "    }\n\n" +
                        "    public Object getAttribute(String key) {\n" +
                        "        return attributes.get(key);\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }
    
    private File createEllipseFigureWithSuperCall() throws Exception {
        File file = new File(tempDir.toFile(), "EllipseFigure.java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "import java.awt.geom.AffineTransform;\n" +
                        "import java.awt.geom.Ellipse2D;\n\n" +
                        "public class EllipseFigure extends AttributedFigure {\n\n" +
                        "    private Ellipse2D.Double ellipse = new Ellipse2D.Double();\n" +
                        "    private transient java.awt.Shape cachedTransformedShape;\n\n" +
                        "    public void transform(AffineTransform tx) {\n" +
                        "        // 这个方法依赖子类的invalidate方法\n" +
                        "        ellipse.x += tx.getTranslateX();\n" +
                        "        ellipse.y += tx.getTranslateY();\n" +
                        "        invalidate();\n" +
                        "    }\n\n" +
                        "    private void invalidate() {\n" +
                        "        // 这里调用了父类的invalidate方法\n" +
                        "        super.invalidate();\n" +
                        "        cachedTransformedShape = null;\n" +
                        "        System.out.println(\"EllipseFigure invalidated\");\n" +
                        "    }\n\n" +
                        "    public void setFrame(double x, double y, double w, double h) {\n" +
                        "        ellipse.setFrame(x, y, w, h);\n" +
                        "        invalidate();\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }
}
