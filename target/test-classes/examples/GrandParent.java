package examples;

/**
 * 复杂继承层次测试 - 祖先类
 * GrandParent -> Parent1/Parent2 -> Child1/Child2/Child3/Child4
 */
public class GrandParent {
    protected String name;
    
    public GrandParent(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
}
