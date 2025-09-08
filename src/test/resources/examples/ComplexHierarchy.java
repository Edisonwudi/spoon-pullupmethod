package examples;

/**
 * 复杂继承层次测试 - 祖先类
 * GrandParent -> Parent1/Parent2 -> Child1/Child2/Child3/Child4
 */
public class ComplexHierarchy {
    protected String name;
    
    public ComplexHierarchy(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
}
