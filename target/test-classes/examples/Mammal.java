package examples;

/**
 * 哺乳动物类 - 三层继承结构的中间层
 * Animal -> Mammal -> Dog/Cat
 */
public class Mammal extends Animal {
    protected boolean hasFur;
    protected double bodyTemperature;
    
    public Mammal(String name, int age, boolean hasFur, double bodyTemperature) {
        super(name, age);
        this.hasFur = hasFur;
        this.bodyTemperature = bodyTemperature;
    }
    
    public boolean hasFur() {
        return hasFur;
    }
    
    public double getBodyTemperature() {
        return bodyTemperature;
    }
    
    public void regulateTemperature() {
        System.out.println(name + " is regulating body temperature to " + bodyTemperature + "°C");
    }
}
