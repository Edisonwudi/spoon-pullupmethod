package examples;

/**
 * 狗类 - 包含可以上提到父类的方法
 */
public class Dog extends Animal {
    private String breed;
    
    public Dog(String name, int age, String breed) {
        super(name, age);
        this.breed = breed;
    }
    
    public String getBreed() {
        return breed;
    }
    
    // 这个方法可以上提到Animal类
    public void eat() {
        System.out.println(name + " is eating...");
    }
    
    // 这个方法也可以上提到Animal类
    public void drink() {
        System.out.println(name + " is drinking water...");
    }
    
    // 这个方法引用了子类特有字段，不能上提
    public void showBreed() {
        System.out.println("I am a " + breed);
    }
    
    // 狗特有的方法
    public void bark() {
        System.out.println(name + " is barking: Woof!");
    }
}
