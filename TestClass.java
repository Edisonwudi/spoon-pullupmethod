package test;

public class TestClass {
    private String preferences;
    private Rectangle rectangle;
    
    public void testMethod() {
        // 这些代码模拟Spoon PrettyPrinter可能生成的错误语法
        String code = "toggleGridButton.setSelected(.preferences.getBoolean(\"view.gridVisible\", false));\n";
        code += "java.awt.geom.Rectangle2D.Double r = ((java.awt.geom.Rectangle2D.Double) (.rectangle.clone()));\n";
        code += "return .preferences;\n";
        code += "if (.rectangle != null) {\n";
        code += "    System.out.println(.preferences.toString());\n";
        code += "}\n";
        
        System.out.println("原始错误代码:");
        System.out.println(code);
    }
}
