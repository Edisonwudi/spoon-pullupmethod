package examples;

/**
 * 猫类 - 也包含相同的方法实现
 */
public class Cat extends Animal {
    private boolean isIndoor;
    
    public Cat(String name, int age, boolean isIndoor) {
        super(name, age);
        this.isIndoor = isIndoor;
    }
    
    public boolean isIndoor() {
        return isIndoor;
    }
    
    // 与Dog类中的eat方法实现相同，可以上提
    public void eat() {
        System.out.println(name + " is eating...");
    }
    
    // 与Dog类中的drink方法实现相同，可以上提  
    public void drink() {
        System.out.println(name + " is drinking water...");
    }
    
    // 猫特有的方法
    public void meow() {
        System.out.println(name + " is meowing: Meow!");
    }
    
    // 这个方法引用了子类特有字段，不能上提
    public void checkLocation() {
        if (isIndoor) {
            System.out.println(name + " is inside the house");
        } else {
            System.out.println(name + " is outside");
        }
    }
}
