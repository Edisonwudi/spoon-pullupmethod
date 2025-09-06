package examples;

import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 复杂狗类 - 包含需要import管理的方法
 */
public class ComplexDog extends ComplexAnimal {
    private String breed;
    private Map<String, Integer> skillLevels;
    
    public ComplexDog(String name, int age, String breed) {
        super(name, age);
        this.breed = breed;
        this.skillLevels = new HashMap<>();
    }
    
    public String getBreed() {
        return breed;
    }
    
    // 这个方法使用了多个需要import的类型，可以上提
    public void recordActivity(String activity) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = now.format(formatter);
        
        activities.add(activity + " at " + timestamp);
        System.out.println(name + " performed " + activity + " at " + timestamp);
    }
    
    // 这个方法也使用了Map类型，可以上提
    public void updateSkillLevel(String skill, int level) {
        Map<String, Integer> skills = new HashMap<>();
        skills.put(skill, level);
        skillLevels.putAll(skills);
        
        System.out.println(name + " updated skill " + skill + " to level " + level);
    }
    
    // 这个方法引用了子类特有字段，不能上提
    public void showBreedInfo() {
        System.out.println("I am a " + breed + " with " + skillLevels.size() + " skills");
    }
    
    // 狗特有的方法
    public void bark() {
        recordActivity("barking");
    }
}
