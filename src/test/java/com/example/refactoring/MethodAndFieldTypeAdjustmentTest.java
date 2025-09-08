package com.example.refactoring;

import com.example.refactoring.core.PullUpMethodRefactoring;
import com.example.refactoring.core.RefactoringResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试：
 * 1) 方法上提到更高祖先时，统一返回类型为公共父类型
 * 2) 字段上提到祖先时，统一字段类型为公共父类型
 */
public class MethodAndFieldTypeAdjustmentTest {

    @TempDir
    Path tempDir;

    private PullUpMethodRefactoring refactoring;

    @BeforeEach
    void setUp() {
        refactoring = new PullUpMethodRefactoring();
    }

    @Test
    void testMethodReturnTypeAdjustedToCommonSuper() throws Exception {
        // BaseF <- MidF <- LeafF
        // MidF.helper() 返回 ViewA；另一个子类 SiblingF.helper() 返回 ViewB；
        // 目标将 LeafF.work() 上提到 BaseF，需将 helper() 抽象上提到 BaseF 并统一返回类型为公共父类（Object 作为兜底）
        File base = writeFile("BaseF.java", "package test;\npublic class BaseF { }\n");
        writeFile("MidF.java", "package test;\npublic class MidF extends BaseF { public test.a.ViewA helper(){ return new test.a.ViewA(); } }\n");
        writeFile("LeafF.java", "package test;\npublic class LeafF extends MidF { public Object work(){ return helper(); } }\n");
        writeFile("SiblingF.java", "package test;\npublic class SiblingF extends BaseF { public test.b.ViewB helper(){ return new test.b.ViewB(); } }\n");
        // 伪造不同包下的两种 View 类型
        writeFile("test/a/ViewA.java", "package test.a; public class ViewA {}\n");
        writeFile("test/b/ViewB.java", "package test.b; public class ViewB {}\n");

        RefactoringResult result = refactoring.pullUpMethodToAncestor(
            Arrays.asList(base.getAbsolutePath(),
                          new File(tempDir.toFile(), "MidF.java").getAbsolutePath(),
                          new File(tempDir.toFile(), "LeafF.java").getAbsolutePath(),
                          new File(tempDir.toFile(), "SiblingF.java").getAbsolutePath(),
                          new File(tempDir.toFile(), "test/a/ViewA.java").getAbsolutePath(),
                          new File(tempDir.toFile(), "test/b/ViewB.java").getAbsolutePath()),
            "LeafF",
            "work",
            "BaseF",
            null
        );

        assertTrue(result.isSuccess(), "重构应该成功: " + result.getMessage());

        String baseContent = java.nio.file.Files.readString(base.toPath());
        assertTrue(baseContent.contains("abstract"), "BaseF 应有抽象方法声明");
        assertTrue(baseContent.contains("helper("), "BaseF 应声明 helper 抽象方法");
        // 公共父类型无法判定时应为 Object（兜底）
        assertTrue(baseContent.contains("Object") || baseContent.contains("java.lang.Object"), "返回类型应被统一为公共父类型（兜底 Object）");
    }

    @Test
    void testFieldTypeAdjustedToCommonSuper() throws Exception {
        // BaseG <- ChildG1/ChildG2
        // 两个子类各有字段 f 类型不同，方法在子类中引用该字段触发上提时，字段类型应统一为公共父类型（兜底 Object）
        File base = writeFile("BaseG.java", "package test;\npublic class BaseG { }\n");
        // ChildG1 访问自身字段 f；ChildG2 的 use2() 通过 super 访问父类字段 f，用于验证“依赖父类字段时目标为祖先也会上提”
        writeFile("ChildG1.java", "package test;\npublic class ChildG1 extends BaseG { public test.a.ViewA f = new test.a.ViewA(); public void use(){ Object x = f; } }\n");
        writeFile("ChildG2.java", "package test;\npublic class ChildG2 extends BaseG { public test.b.ViewB f = new test.b.ViewB(); public void use2(){ Object x = super.f; } }\n");
        writeFile("test/a/ViewA.java", "package test.a; public class ViewA {}\n");
        writeFile("test/b/ViewB.java", "package test.b; public class ViewB {}\n");

        RefactoringResult result = refactoring.pullUpMethodToAncestor(
            Arrays.asList(base.getAbsolutePath(),
                          new File(tempDir.toFile(), "ChildG1.java").getAbsolutePath(),
                          new File(tempDir.toFile(), "ChildG2.java").getAbsolutePath(),
                          new File(tempDir.toFile(), "test/a/ViewA.java").getAbsolutePath(),
                          new File(tempDir.toFile(), "test/b/ViewB.java").getAbsolutePath()),
            "ChildG1",
            "use",
            "BaseG",
            null
        );

        assertTrue(result.isSuccess(), "重构应该成功: " + result.getMessage());

        String baseContent = java.nio.file.Files.readString(base.toPath());
        assertTrue(baseContent.contains("f"), "BaseG 应该有上提后的字段 f");
        assertTrue(baseContent.contains("Object") || baseContent.contains("java.lang.Object"), "字段类型应被统一为公共父类型（兜底 Object）");
    }

    private File writeFile(String name, String content) throws Exception {
        File f = new File(tempDir.toFile(), name);
        if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) { w.write(content); }
        return f;
    }
}


