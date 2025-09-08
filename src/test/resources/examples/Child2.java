package examples;

/**
 * Parent1的第二个子类
 */
public class Child2 extends Parent1 {
    public Child2(String name, int level) {
        super(name, level);
    }
    
    // 与Child1中同名的方法，访问级别应该被调整
    private void commonMethod() {
        System.out.println("Child2: " + name + " executing common method");
    }
    
    public void specificMethod2() {
        System.out.println("Child2 specific method");
    }
}
