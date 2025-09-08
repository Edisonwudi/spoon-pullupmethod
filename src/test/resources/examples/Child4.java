package examples;

/**
 * Parent2的第二个子类
 */
public class Child4 extends Parent2 {
    public Child4(String name, String type) {
        super(name, type);
    }
    
    // 与Child1中同名的方法，访问级别应该被调整
    public void commonMethod() {
        System.out.println("Child4: " + name + " executing common method");
    }
    
    public void specificMethod4() {
        System.out.println("Child4 specific method");
    }
}
