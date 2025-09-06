package examples;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

/**
 * 复杂动物基类 - 用于演示import管理功能
 */
public class ComplexAnimal {
    protected String name;
    protected int age;
    protected List<String> activities;
    
    public ComplexAnimal(String name, int age) {
        this.name = name;
        this.age = age;
        this.activities = new ArrayList<>();
    }
    
    public String getName() {
        return name;
    }
    
    public int getAge() {
        return age;
    }
    
    public List<String> getActivities() {
        return activities;
    }
    
    public void sleep() {
        System.out.println(name + " is sleeping at " + LocalDateTime.now());
        activities.add("sleeping");
    }
}
