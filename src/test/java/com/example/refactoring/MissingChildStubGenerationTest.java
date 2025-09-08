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
 * 当依赖方法被上提为抽象方法时，为未实现的其它子类自动生成最基础实现的测试
 */
public class MissingChildStubGenerationTest {

    @TempDir
    Path tempDir;

    private PullUpMethodRefactoring refactoring;

    @BeforeEach
    void setUp() {
        refactoring = new PullUpMethodRefactoring();
    }

    @Test
    void testGenerateStubForMissingChild_nonVoidReturn() throws Exception {
        File parent = createParent("BaseA");
        File child1 = createChildWithDependentNonVoid("ChildA", "BaseA");
        File child2 = createEmptyChild("ChildB", "BaseA");

        RefactoringResult result = refactoring.pullUpMethod(
            Arrays.asList(parent.getAbsolutePath(), child1.getAbsolutePath(), child2.getAbsolutePath()),
            "ChildA",
            "doWork",
            null
        );

        assertTrue(result.isSuccess(), "重构应该成功: " + result.getMessage());

        // 验证 ChildB 生成了 helper 方法的基础实现
        String child2Content = java.nio.file.Files.readString(child2.toPath());
        assertTrue(child2Content.contains("@Override"), "应为生成的方法添加 @Override 注解");
        assertTrue(child2Content.contains("int helper("), "应生成与父类一致签名的方法");
        assertTrue(child2Content.contains("throw new UnsupportedOperationException"), "非 void 返回应抛出 UnsupportedOperationException 以保证可编译");
    }

    @Test
    void testGenerateStubForMissingChild_voidReturn() throws Exception {
        File parent = createParent("BaseB");
        File child1 = createChildWithDependentVoid("ChildC", "BaseB");
        File child2 = createEmptyChild("ChildD", "BaseB");

        RefactoringResult result = refactoring.pullUpMethod(
            Arrays.asList(parent.getAbsolutePath(), child1.getAbsolutePath(), child2.getAbsolutePath()),
            "ChildC",
            "run",
            null
        );

        assertTrue(result.isSuccess(), "重构应该成功: " + result.getMessage());

        // 验证 ChildD 生成了 helper 方法（void 返回）的基础实现（空方法体或无返回）
        String child2Content = java.nio.file.Files.readString(child2.toPath());
        assertTrue(child2Content.contains("@Override"), "应为生成的方法添加 @Override 注解");
        assertTrue(child2Content.contains("void helper("), "应生成与父类一致签名的方法");
        // 不应包含 return 语句（简单校验，不强制）
        // 允许包含注释或空体，这里仅检查未包含 UnsupportedOperationException 文本
        assertFalse(child2Content.contains("UnsupportedOperationException"), "void 返回不应抛出异常");
    }

    private File createParent(String parentName) throws Exception {
        File file = new File(tempDir.toFile(), parentName + ".java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "public class " + parentName + " {\n" +
                        "    protected int state = 0;\n" +
                        "}\n");
        }
        return file;
    }

    private File createEmptyChild(String childName, String parentName) throws Exception {
        File file = new File(tempDir.toFile(), childName + ".java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "public class " + childName + " extends " + parentName + " {\n" +
                        "}\n");
        }
        return file;
    }

    @Test
    void testGenerateStubForGrandChild() throws Exception {
        // Base -> Mid -> Leaf1/Leaf2（仅 Leaf1 提供依赖方法，Leaf2 应自动补全）
        File base = createParent("BaseC");
        File mid = createEmptyChild("MidC", "BaseC");

        // Leaf1 有 target 方法，内部依赖 helper()
        File leaf1 = new File(tempDir.toFile(), "Leaf1C.java");
        try (FileWriter writer = new FileWriter(leaf1)) {
            writer.write("package test;\n\n" +
                        "public class Leaf1C extends MidC {\n\n" +
                        "    public String work() {\n" +
                        "        return helper(3);\n" +
                        "    }\n\n" +
                        "    public String helper(int v) {\n" +
                        "        return \"#\" + (state + v);\n" +
                        "    }\n" +
                        "}\n");
        }

        // Leaf2 缺少 helper()，应在上提后作为孙子类被自动补全
        File leaf2 = new File(tempDir.toFile(), "Leaf2C.java");
        try (FileWriter writer = new FileWriter(leaf2)) {
            writer.write("package test;\n\n" +
                        "public class Leaf2C extends MidC {\n" +
                        "}\n");
        }

        RefactoringResult result = refactoring.pullUpMethod(
            Arrays.asList(base.getAbsolutePath(), mid.getAbsolutePath(), leaf1.getAbsolutePath(), leaf2.getAbsolutePath()),
            "Leaf1C",
            "work",
            null
        );

        assertTrue(result.isSuccess(), "重构应该成功: " + result.getMessage());

        String leaf2Content = java.nio.file.Files.readString(leaf2.toPath());
        assertTrue(leaf2Content.contains("@Override"), "孙子类也应生成 @Override 方法");
        assertTrue(leaf2Content.contains("String helper("), "孙子类应生成与父类抽象方法一致的签名");
    }

    @Test
    void testIntermediateAncestorMethodPulledUpWhenTargetIsHigherAncestor() throws Exception {
        // BaseE <- MidE <- LeafE
        // LeafE.work() 调用 MidE.helper()，目标上提到 BaseE，应将 MidE.helper() 也上提为抽象到 BaseE
        File base = createParent("BaseE");
        File mid = new File(tempDir.toFile(), "MidE.java");
        try (FileWriter w = new FileWriter(mid)) {
            w.write("package test;\n\n" +
                    "public class MidE extends BaseE {\n\n" +
                    "    public int helper(int v) { return v + 1; }\n" +
                    "}\n");
        }
        File leaf = new File(tempDir.toFile(), "LeafE.java");
        try (FileWriter w = new FileWriter(leaf)) {
            w.write("package test;\n\n" +
                    "public class LeafE extends MidE {\n\n" +
                    "    public int work() { return helper(10); }\n" +
                    "}\n");
        }

        RefactoringResult result = new PullUpMethodRefactoring().pullUpMethodToAncestor(
            Arrays.asList(base.getAbsolutePath(), mid.getAbsolutePath(), leaf.getAbsolutePath()),
            "LeafE",
            "work",
            "BaseE",
            null
        );

        assertTrue(result.isSuccess(), "重构应该成功: " + result.getMessage());
        // 验证 BaseE 中出现 abstract helper 声明
        String baseContent = java.nio.file.Files.readString(base.toPath());
        assertTrue(baseContent.contains("abstract int helper("), "BaseE 应声明 abstract helper 方法");
    }

    @Test
    void testAncestorConcretePreventsDuplicateChildStub() throws Exception {
        // BaseD 抽象方法将从 ChildX 上提，其它分支： ParentY 提供了 helper 的具体实现
        File base = createParent("BaseD");

        // ParentY 作为 BaseD 的直接子类，先行提供 helper 的具体实现
        File parentY = new File(tempDir.toFile(), "ParentY.java");
        try (FileWriter writer = new FileWriter(parentY)) {
            writer.write("package test;\\n\\n" +
                        "public class ParentY extends BaseD {\\n\\n" +
                        "    public int helper(int x) {\\n" +
                        "        return state + x;\\n" +
                        "    }\\n" +
                        "}\\n");
        }

        // ChildY 继承 ParentY，不包含 helper 的实现，应该继承自 ParentY 且不应被自动生成重复实现
        File childY = new File(tempDir.toFile(), "ChildY.java");
        try (FileWriter writer = new FileWriter(childY)) {
            writer.write("package test;\\n\\n" +
                        "public class ChildY extends ParentY {\\n" +
                        "}\\n");
        }

        // 在其它分支 ChildX 中，work() 依赖 helper()，触发上提
        File childX = new File(tempDir.toFile(), "ChildX.java");
        try (FileWriter writer = new FileWriter(childX)) {
            writer.write("package test;\\n\\n" +
                        "public class ChildX extends BaseD {\\n\\n" +
                        "    public int work() {\\n" +
                        "        return helper(5);\\n" +
                        "    }\\n\\n" +
                        "    public int helper(int v) {\\n" +
                        "        return state + v;\\n" +
                        "    }\\n" +
                        "}\\n");
        }

        RefactoringResult result = refactoring.pullUpMethod(
            Arrays.asList(base.getAbsolutePath(), parentY.getAbsolutePath(), childY.getAbsolutePath(), childX.getAbsolutePath()),
            "ChildX",
            "work",
            null
        );

        assertTrue(result.isSuccess(), "重构应该成功: " + result.getMessage());

        // 验证 ChildY 没有生成重复 helper 实现（应继承自 ParentY）
        String childYContent = java.nio.file.Files.readString(childY.toPath());
        assertFalse(childYContent.contains("int helper("), "当父类已有具体实现时，子类不应该生成重复实现");
    }

    private File createChildWithDependentNonVoid(String childName, String parentName) throws Exception {
        File file = new File(tempDir.toFile(), childName + ".java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "public class " + childName + " extends " + parentName + " {\n\n" +
                        "    public int doWork() {\n" +
                        "        return helper(42);\n" +
                        "    }\n\n" +
                        "    public int helper(int x) {\n" +
                        "        return state + x;\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }

    private File createChildWithDependentVoid(String childName, String parentName) throws Exception {
        File file = new File(tempDir.toFile(), childName + ".java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("package test;\n\n" +
                        "public class " + childName + " extends " + parentName + " {\n\n" +
                        "    public void run() {\n" +
                        "        helper(7);\n" +
                        "    }\n\n" +
                        "    public void helper(int x) {\n" +
                        "        this.state += x;\n" +
                        "    }\n" +
                        "}\n");
        }
        return file;
    }
}


