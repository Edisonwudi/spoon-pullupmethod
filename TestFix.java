// 测试修复效果的示例代码
public class TestFix {
    public static void main(String[] args) {
        String problematicCode = "toggleGridButton.setSelected(.preferences.getBoolean(\"view.gridVisible\", false));\n" +
                               "java.awt.geom.Rectangle2D.Double r = ((java.awt.geom.Rectangle2D.Double) (.rectangle.clone()));\n" +
                               "return .preferences;\n" +
                               "if (.rectangle != null) {\n" +
                               "    System.out.println(.preferences.toString());\n" +
                               "}\n";
        
        System.out.println("=== 修复前的错误代码 ===");
        System.out.println(problematicCode);
        
        // 应用我们的修复规则
        String fixed = problematicCode;
        
        // 修复模式1: 行首的点号 + 标识符
        fixed = fixed.replaceAll("(?m)^(\\s*)\\.(\\w+)", "$1this.$2");
        
        // 修复模式2: 非标识符字符后的点号 + 标识符
        fixed = fixed.replaceAll("([^\\w.]|^)\\.(\\w+)(?!\\w|\\.)", "$1this.$2");
        
        // 修复模式3: 括号后的点号 + 标识符
        fixed = fixed.replaceAll("(\\))\\.(\\w+)(?!\\s*\\()", "$1this.$2");
        
        System.out.println("\n=== 修复后的正确代码 ===");
        System.out.println(fixed);
    }
}
