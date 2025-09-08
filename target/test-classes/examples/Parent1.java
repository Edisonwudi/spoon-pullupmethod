package examples;

/**
 * 第一个中间父类
 */
public class Parent1 extends ComplexHierarchy {
    protected int level;
    
    public Parent1(String name, int level) {
        super(name);
        this.level = level;
    }
    
    public int getLevel() {
        return level;
    }
}
