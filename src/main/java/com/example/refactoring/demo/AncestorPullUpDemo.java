package com.example.refactoring.demo;

import com.example.refactoring.core.RefactoringOrchestrator;
import com.example.refactoring.core.RefactoringResult;

import java.util.Arrays;
import java.util.List;

/**
 * 演示上提方法到任意祖先类的功能
 */
public class AncestorPullUpDemo {
    
    public static void main(String[] args) {
        AncestorPullUpDemo demo = new AncestorPullUpDemo();
        demo.runDemo();
    }
    
    public void runDemo() {
        RefactoringOrchestrator orchestrator = new RefactoringOrchestrator();
        List<String> sourcePaths = Arrays.asList("src/test/resources/examples");
        
        System.out.println("=== Pull-Up Method 到任意祖先类功能演示 ===\n");
        
        // 1. 展示获取祖先类列表
        demonstrateGetAncestors(orchestrator, sourcePaths);
        
        // 2. 展示传统的上提到父类
        demonstrateTraditionalPullUp(orchestrator, sourcePaths);
        
        // 3. 展示新的上提到指定祖先类
        demonstrateAncestorPullUp(orchestrator, sourcePaths);
        
        // 4. 展示错误处理
        demonstrateErrorHandling(orchestrator, sourcePaths);
    }
    
    private void demonstrateGetAncestors(RefactoringOrchestrator orchestrator, List<String> sourcePaths) {
        System.out.println("1. 获取类的祖先类列表");
        System.out.println("=======================");
        
        String className = "AdvancedDog";
        List<String> ancestors = orchestrator.getAncestorClassNames(sourcePaths, className);
        
        System.out.println("类 " + className + " 的祖先类:");
        if (ancestors.isEmpty()) {
            System.out.println("  无祖先类（除了Object）");
        } else {
            for (int i = 0; i < ancestors.size(); i++) {
                String prefix = i == 0 ? "  直接父类: " : "  祖先类 " + (i + 1) + ": ";
                System.out.println(prefix + ancestors.get(i));
            }
        }
        System.out.println();
    }
    
    private void demonstrateTraditionalPullUp(RefactoringOrchestrator orchestrator, List<String> sourcePaths) {
        System.out.println("2. 传统方式：上提到直接父类");
        System.out.println("============================");
        
        System.out.println("模拟执行: pullUpMethod(sourcePaths, \"AdvancedDog\", \"pant\", null)");
        System.out.println("结果: 方法 pant 将从 AdvancedDog 上提到其直接父类 Mammal");
        System.out.println("这保持了向后兼容性\n");
    }
    
    private void demonstrateAncestorPullUp(RefactoringOrchestrator orchestrator, List<String> sourcePaths) {
        System.out.println("3. 新功能：上提到指定祖先类");
        System.out.println("============================");
        
        System.out.println("模拟执行: pullUpMethodToAncestor(sourcePaths, \"AdvancedDog\", \"move\", \"examples.Animal\", null)");
        System.out.println("结果: 方法 move 将从 AdvancedDog 直接上提到 Animal 类");
        System.out.println("跳过了中间的 Mammal 类\n");
    }
    
    private void demonstrateErrorHandling(RefactoringOrchestrator orchestrator, List<String> sourcePaths) {
        System.out.println("4. 错误处理演示");
        System.out.println("================");
        
        // 测试无效祖先类
        System.out.println("尝试上提到非祖先类:");
        RefactoringResult result = orchestrator.pullUpMethodToAncestor(
            sourcePaths, "AdvancedDog", "move", "examples.Cat", null);
        
        if (!result.isSuccess()) {
            System.out.println("✓ 正确检测到错误: " + result.getMessage());
        }
        
        // 测试不存在的类
        System.out.println("\n尝试上提到不存在的类:");
        result = orchestrator.pullUpMethodToAncestor(
            sourcePaths, "AdvancedDog", "move", "examples.NonExistent", null);
        
        if (!result.isSuccess()) {
            System.out.println("✓ 正确检测到错误: " + result.getMessage());
        }
        
        System.out.println();
    }
}
