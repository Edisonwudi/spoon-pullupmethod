# Pull-Up-Method 重构工具

基于 Spoon 的自动化 Pull-Up-Method 重构工具，用于将子类中的重复方法自动上提到父类，从而消除代码冗余，提升代码可维护性。

## 功能特性

- ✅ **自动依赖分析**: 检查方法是否引用子类特有的字段或方法
- ✅ **冲突检测**: 检查父类中是否已存在同名方法或签名冲突
- ✅ **可见性自动调整**: 自动将 private 方法提升为 protected 或 public
- ✅ **智能import管理**: 自动生成所需import语句，清理冗余import，使用简单类名
- ✅ **安全重构**: 保持源码结构与编译语义正确
- ✅ **命令行界面**: 提供友好的 CLI 工具
- ✅ **详细日志**: 支持详细的重构过程日志输出

## 快速开始

### 环境要求

- Java 11 或更高版本
- Maven 3.6 或更高版本

### 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd pull-up-method-refactoring

# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包
mvn package
```

### 使用方法

#### 1. 基本用法

```bash
# 执行 Pull-Up-Method 重构
java -jar target/pull-up-method-refactoring-1.0.0.jar \
  --source src/main/java \
  --class com.example.Child \
  --method methodToMove
```

#### 2. 查看可用的类和方法

```bash
# 列出所有类
java -jar target/pull-up-method-refactoring-1.0.0.jar \
  --source src/main/java \
  --list-classes

# 列出指定类的所有方法
java -jar target/pull-up-method-refactoring-1.0.0.jar \
  --source src/main/java \
  --class com.example.Child \
  --list-methods
```

#### 3. 输出到指定目录

```bash
# 将重构结果输出到指定目录
java -jar target/pull-up-method-refactoring-1.0.0.jar \
  --source src/main/java \
  --class com.example.Child \
  --method methodToMove \
  --output output/
```

#### 4. 启用详细输出

```bash
# 启用详细日志输出
java -jar target/pull-up-method-refactoring-1.0.0.jar \
  --source src/main/java \
  --class com.example.Child \
  --method methodToMove \
  --verbose
```

### 命令行参数

| 参数 | 简写 | 必需 | 描述 |
|------|------|------|------|
| `--source` | `-s` | ✅ | 源代码路径，多个路径用逗号分隔 |
| `--class` | `-c` | ✅ | 包含要上提方法的子类名称 |
| `--method` | `-m` | ✅ | 要上提的方法名称 |
| `--output` | `-o` | ❌ | 输出目录路径（默认覆盖原文件） |
| `--verbose` | `-v` | ❌ | 启用详细输出 |
| `--list-classes` | - | ❌ | 列出所有可用的类 |
| `--list-methods` | - | ❌ | 列出指定类的所有方法 |
| `--help` | `-h` | ❌ | 显示帮助信息 |
| `--version` | - | ❌ | 显示版本信息 |

## 示例场景

### 示例 1: 基本重构

假设有以下代码结构：

```java
// Animal.java
public class Animal {
    protected String name;
    
    public Animal(String name) {
        this.name = name;
    }
}

// Dog.java  
public class Dog extends Animal {
    public Dog(String name) {
        super(name);
    }
    
    // 这个方法可以上提到父类
    public void eat() {
        System.out.println(name + " is eating...");
    }
    
    public void bark() {
        System.out.println("Woof!");
    }
}

// Cat.java
public class Cat extends Animal {
    public Cat(String name) {
        super(name);
    }
    
    // 与Dog中的eat方法实现相同
    public void eat() {
        System.out.println(name + " is eating...");
    }
    
    public void meow() {
        System.out.println("Meow!");
    }
}
```

执行重构：

```bash
java -jar tool.jar -s src/main/java -c Dog -m eat
```

重构后的结果：

```java
// Animal.java
public class Animal {
    protected String name;
    
    public Animal(String name) {
        this.name = name;
    }
    
    // 方法已从Dog类上提到这里
    public void eat() {
        System.out.println(name + " is eating...");
    }
}

// Dog.java  
public class Dog extends Animal {
    public Dog(String name) {
        super(name);
    }
    
    public void bark() {
        System.out.println("Woof!");
    }
}
```

### 示例 2: Import管理功能

假设有包含复杂类型引用的方法：

```java
// ComplexDog.java
public class ComplexDog extends ComplexAnimal {
    // 这个方法使用了多个需要import的类型
    public void recordActivity(String activity) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = now.format(formatter);
        
        activities.add(activity + " at " + timestamp);
        System.out.println(name + " performed " + activity + " at " + timestamp);
    }
}
```

执行重构：

```bash
java -jar tool.jar -s src/main/java -c ComplexDog -m recordActivity
```

重构后的父类自动生成了正确的import语句：

```java
// ComplexAnimal.java
package examples;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ComplexAnimal {
    // 方法已上提，使用简单类名
    public void recordActivity(String activity) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // ...
    }
}
```

### 示例 3: 不能重构的情况

```java
public class Dog extends Animal {
    private String breed;
    
    // 这个方法引用了子类特有字段，不能上提
    public void showBreed() {
        System.out.println("I am a " + breed);  // 引用了子类字段
    }
}
```

工具会检测到依赖并阻止重构：

```
✗ 重构失败!
  方法存在子类依赖，无法上提:
  - 引用了私有字段: breed
```

## 重构检查规则

工具会执行以下检查来确保重构的安全性：

### 1. 依赖分析
- ✅ 检查方法是否引用子类特有的字段
- ✅ 检查方法是否调用子类特有的方法
- ✅ 识别私有成员的访问

### 2. 方法冲突检查
- ✅ 检查父类中是否已存在相同签名的方法
- ✅ 检查是否存在方法重载冲突
- ✅ 识别可能的方法调用歧义

### 3. 可见性检查
- ✅ 自动将 private 方法提升为 protected
- ✅ 确保子类在重构后仍能访问方法
- ✅ 保持适当的封装性

### 4. Import管理
- ✅ 自动收集方法中使用的所有类型引用
- ✅ 生成必要的import语句（排除java.lang包和基本类型）
- ✅ 在方法体中使用简单类名替代全限定名
- ✅ 保持import语句的整洁和排序

## 架构设计

```
src/main/java/com/example/refactoring/
├── core/                          # 核心重构逻辑
│   ├── PullUpMethodRefactoring.java   # 主重构类
│   ├── RefactoringResult.java         # 结果封装
│   └── RefactoringException.java      # 异常定义
├── analyzer/                      # 依赖分析
│   └── DependencyAnalyzer.java        # 分析方法依赖
├── checker/                       # 冲突检查  
│   └── MethodConflictChecker.java     # 检查方法冲突
├── adjuster/                      # 可见性调整
│   └── VisibilityAdjuster.java        # 调整方法可见性
└── cli/                          # 命令行接口
    └── PullUpMethodCLI.java           # CLI实现
```

## 技术栈

- **Spoon**: Java 源码分析和转换框架
- **Apache Commons CLI**: 命令行参数解析
- **SLF4J**: 日志框架
- **JUnit 5**: 单元测试框架
- **Maven**: 项目构建工具

## 开发指南

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=PullUpMethodRefactoringTest

# 生成测试报告
mvn surefire-report:report
```

### 调试模式

```bash
# 启用详细日志
java -jar tool.jar -s src/main/java -c MyClass -m myMethod --verbose

# 使用IDE调试
# 在IDE中运行 PullUpMethodCLI.main() 并设置断点
```

### 扩展功能

1. **添加新的检查器**: 在 `checker` 包中实现新的检查逻辑
2. **扩展依赖分析**: 在 `DependencyAnalyzer` 中添加更多依赖类型的检测
3. **自定义输出格式**: 修改 `PullUpMethodRefactoring` 中的输出逻辑

## 限制和注意事项

1. **泛型支持**: 当前版本对复杂泛型的支持有限
2. **注解保留**: 方法上的注解会被保留，但可能需要手动检查
3. **内部类**: 对内部类的支持需要进一步测试
4. **包可见性**: 跨包的可见性调整可能需要额外注意

## 贡献指南

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 联系方式

如有问题或建议，请通过以下方式联系：

- 提交 Issue: [GitHub Issues](https://github.com/your-repo/issues)
- 邮箱: your-email@example.com

## 更新日志

### v1.0.0 (2024-01-XX)
- ✅ 初始版本发布
- ✅ 实现基本的 Pull-Up-Method 重构功能
- ✅ 添加依赖分析和冲突检查
- ✅ 提供命令行界面
- ✅ 支持可见性自动调整
- ✅ 智能import管理：自动生成import语句，使用简单类名
- ✅ 完整的package声明和编译单元支持
