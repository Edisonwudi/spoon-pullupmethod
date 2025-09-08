package examples;

/**
 * Parent2的第一个子类
 */
public class Child3 extends Parent2 {
    public Child3(String name, String type) {
        super(name, type);
    }
    
    // 与Child1中同名的方法，访问级别应该被调整
    protected void commonMethod() {
        System.out.println("Child3: " + name + " executing common method");
    }
    
    public void specificMethod3() {
        System.out.println("Child3 specific method");
    }
}
