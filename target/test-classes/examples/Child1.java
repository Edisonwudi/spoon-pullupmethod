package examples;

/**
 * Parent1的第一个子类
 */
public class Child1 extends Parent1 {
    public Child1(String name, int level) {
        super(name, level);
    }
    
    // 这个方法将被上提到GrandParent
    private void commonMethod() {
        System.out.println("Child1: " + name + " executing common method");
    }
    
    public void specificMethod1() {
        System.out.println("Child1 specific method");
    }
}
