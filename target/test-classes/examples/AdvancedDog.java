package examples;

/**
 * 高级狗类 - 三层继承结构测试
 * Animal -> Mammal -> AdvancedDog
 */
public class AdvancedDog extends Mammal {
    private String breed;
    private String owner;
    
    public AdvancedDog(String name, int age, String breed, String owner) {
        super(name, age, true, 38.5); // 狗有毛，体温38.5度
        this.breed = breed;
        this.owner = owner;
    }
    
    public String getBreed() {
        return breed;
    }
    
    public String getOwner() {
        return owner;
    }
    
    // 可以上提到Mammal类的方法
    public void pant() {
        System.out.println(name + " is panting to cool down");
    }
    
    // 可以上提到Animal类的方法（跨越两层）
    public void move() {
        System.out.println(name + " is moving around");
    }
    
    // 依赖于子类字段的方法，不能上提
    public void introduce() {
        System.out.println("Hi, I'm " + name + ", a " + breed + " owned by " + owner);
    }
    
    // 狗特有的行为
    public void fetchBall() {
        System.out.println(name + " is fetching the ball for " + owner);
    }
    
    // 调用父类方法的示例
    public void stayWarm() {
        regulateTemperature();
        System.out.println(name + " is staying warm with fur");
    }
}
