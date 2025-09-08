package examples;

/**
 * 第二个中间父类
 */
public class Parent2 extends ComplexHierarchy {
    protected String type;
    
    public Parent2(String name, String type) {
        super(name);
        this.type = type;
    }
    
    public String getType() {
        return type;
    }
}
