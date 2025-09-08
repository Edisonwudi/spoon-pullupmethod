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
 * 验证：方法上提到祖先后，方法体内 this 用作实参的场景会被自动强转为 (Child)this。
 */
public class ThisCastFixTest {

    @TempDir
    Path tempDir;

    private PullUpMethodRefactoring refactoring;

    @BeforeEach
    void setUp() {
        refactoring = new PullUpMethodRefactoring();
    }

    @Test
    void testInsertCastForThisArgument() throws Exception {
        // BaseH <- ChildH
        // ChildH.work() 调用 Util.use(ChildH, int) 以 this 作为第一个参数；上提到 BaseH 后应插入 (ChildH) this
        File base = writeFile("BaseH.java", "package test;\npublic class BaseH { }\n");
        writeFile("ChildH.java", "package test;\npublic class ChildH extends BaseH { public int work(){ return test.Util.use(this, 1); } }\n");
        writeFile("Util.java", "package test;\npublic class Util { public static int use(test.ChildH h, int x){ return x; } }\n");

        RefactoringResult result = refactoring.pullUpMethodToAncestor(
            Arrays.asList(base.getAbsolutePath(),
                          new File(tempDir.toFile(), "ChildH.java").getAbsolutePath(),
                          new File(tempDir.toFile(), "Util.java").getAbsolutePath()),
            "ChildH",
            "work",
            "BaseH",
            null
        );

        assertTrue(result.isSuccess(), "重构应该成功: " + result.getMessage());
        String baseContent = java.nio.file.Files.readString(base.toPath());
        assertTrue(baseContent.contains("(test.ChildH) this") || baseContent.contains("(ChildH) this"),
            "应插入对 this 的强制类型转换");
    }

    private File writeFile(String name, String content) throws Exception {
        File f = new File(tempDir.toFile(), name);
        if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) { w.write(content); }
        return f;
    }
}


