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
 * 返回类型调整功能测试
 */
public class ReturnTypeAdjustmentTest {
    
    @TempDir
    Path tempDir;
    
    private PullUpMethodRefactoring refactoring;
    
    @BeforeEach
    void setUp() {
        refactoring = new PullUpMethodRefactoring();
    }
    
    @Test
    void testPullUpMethodWithReturnTypeConflict() throws Exception {
        // 创建测试文件
        File parentFile = createBaseClass();
        File ellipseFile = createEllipseClass();
        File rectFile = createRectClass();
        
        // 执行重构：将 EllipseShape.clone() 上提到 BaseShape
        RefactoringResult result = refactoring.pullUpMethod(
            Arrays.asList(parentFile.getAbsolutePath(), ellipseFile.getAbsolutePath(), rectFile.getAbsolutePath()),
            "EllipseShape",
            "clone",
            null // 覆盖原文件
        );
        
        // 验证结果
        assertTrue(result.isSuccess(), "重构应该成功: " + result.getMessage());
        assertFalse(result.getModifiedFiles().isEmpty(), "应该有文件被修改");
        
        // 验证父类文件的返回类型是否被正确调整
        File modifiedParentFile = new File(parentFile.getAbsolutePath());
        String parentContent = java.nio.file.Files.readString(modifiedParentFile.toPath());
        
        System.out.println("\n=== 父类文件内容 ===");
        System.out.println(parentContent);
        
        // 验证返回类型被调整为兼容类型（应该是 BaseShape 而不是 EllipseShape）
        assertTrue(parentContent.contains("public BaseShape clone()"), 
                  "父类的 clone 方法应该返回 BaseShape 类型");
        
        // 验证子类 RectShape 的 clone 方法仍然可以正常工作
        File modifiedRectFile = new File(rectFile.getAbsolutePath());
        String rectContent = java.nio.file.Files.readString(modifiedRectFile.toPath());
        
        System.out.println("\n=== RectShape 文件内容 ===");
        System.out.println(rectContent);
        
        // RectShape.clone() 应该仍然返回 RectShape（协变返回类型）
        assertTrue(rectContent.contains("public RectShape clone()"), 
                  "RectShape 的 clone 方法应该返回 RectShape 类型");
        
        System.out.println("重构结果: " + result.getMessage());
        result.getModifiedFiles().forEach(file -> 
            System.out.println("修改文件: " + file));
    }
    
    private File createBaseClass() throws Exception {
        File file = new File(tempDir.toFile(), "BaseShape.java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "public class BaseShape {\n\n" +
                        "    protected double x, y;\n\n" +
                        "    public void move(double dx, double dy) {\n" +
                        "        this.x += dx;\n" +
                        "        this.y += dy;\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }
    
    private File createEllipseClass() throws Exception {
        File file = new File(tempDir.toFile(), "EllipseShape.java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "import java.awt.geom.Ellipse2D;\n\n" +
                        "public class EllipseShape extends BaseShape {\n\n" +
                        "    private Ellipse2D.Double ellipse = new Ellipse2D.Double();\n\n" +
                        "    @Override\n" +
                        "    public EllipseShape clone() {\n" +
                        "        EllipseShape that = (EllipseShape) super.clone();\n" +
                        "        that.ellipse = (Ellipse2D.Double) this.ellipse.clone();\n" +
                        "        return that;\n" +
                        "    }\n\n" +
                        "    public void setSize(double width, double height) {\n" +
                        "        ellipse.setFrame(x, y, width, height);\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }
    
    private File createRectClass() throws Exception {
        File file = new File(tempDir.toFile(), "RectShape.java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "import java.awt.geom.RoundRectangle2D;\n\n" +
                        "public class RectShape extends BaseShape {\n\n" +
                        "    private RoundRectangle2D.Double rect = new RoundRectangle2D.Double();\n\n" +
                        "    @Override\n" +
                        "    public RectShape clone() {\n" +
                        "        RectShape that = (RectShape) super.clone();\n" +
                        "        that.rect = (RoundRectangle2D.Double) this.rect.clone();\n" +
                        "        return that;\n" +
                        "    }\n\n" +
                        "    public void setSize(double width, double height) {\n" +
                        "        rect.setRoundRect(x, y, width, height, 0, 0);\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }
}
