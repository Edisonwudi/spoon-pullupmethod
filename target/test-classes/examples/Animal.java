package examples;

/**
 * 动物基类 - 用于演示Pull-Up-Method重构
 */
public class Animal {
    protected String name;
    protected int age;
    
    public Animal(String name, int age) {
        this.name = name;
        this.age = age;
    }
    
    public String getName() {
        return name;
    }
    
    public int getAge() {
        return age;
    }
    
    public void sleep() {
        System.out.println(name + " is sleeping...");
    }
}
