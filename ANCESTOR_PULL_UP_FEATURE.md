# Pull-Up Method 到任意祖先类功能改造

## 功能概述

原有的Pull-Up Method工具只支持将方法上提到直接父类。经过改造，现在支持将方法上提到任意祖先类，大大增强了重构的灵活性。

## 主要改进

### 1. 扩展了ClassFinder类

新增了以下方法：
- `getAllAncestorClasses(CtClass<?> childClass)`: 获取类的所有祖先类
- `isAncestorClass(CtClass<?> potentialAncestor, CtClass<?> descendantClass)`: 检查祖先关系
- `getInheritancePath(CtClass<?> childClass, CtClass<?> targetAncestor)`: 获取继承路径

### 2. 增强了RefactoringOrchestrator类

新增方法：
- `pullUpMethodToAncestor(...)`: 支持指定目标祖先类的重构方法
- `getAncestorClassNames(...)`: 获取祖先类名称列表，用于CLI选择

保持向后兼容：
- 原有的`pullUpMethod(...)`方法仍然可用，默认上提到直接父类

### 3. 更新了CLI接口

新增命令行选项：
- `-t, --target <ancestorClassName>`: 指定目标祖先类
- `--list-ancestors`: 列出指定类的所有祖先类

### 4. 创建了测试和演示

- `AncestorPullUpIntegrationTest`: 完整的集成测试
- `AncestorPullUpDemo`: 功能演示程序
- 新的测试类层次：`Animal -> Mammal -> AdvancedDog`

## 使用示例

### 命令行使用

```bash
# 传统方式：上提到直接父类
java -jar tool.jar -s src/main/java -c com.example.Child -m methodToMove

# 新功能：上提到指定祖先类
java -jar tool.jar -s src/main/java -c com.example.Child -m methodToMove -t com.example.GrandParent

# 列出类的祖先类
java -jar tool.jar -s src/main/java -c com.example.Child --list-ancestors
```

### 编程接口使用

```java
RefactoringOrchestrator orchestrator = new RefactoringOrchestrator();

// 获取祖先类列表
List<String> ancestors = orchestrator.getAncestorClassNames(sourcePaths, "ChildClass");

// 上提到直接父类（原有功能）
RefactoringResult result1 = orchestrator.pullUpMethod(
    sourcePaths, "ChildClass", "methodName", outputPath);

// 上提到指定祖先类（新功能）
RefactoringResult result2 = orchestrator.pullUpMethodToAncestor(
    sourcePaths, "ChildClass", "methodName", "GrandParentClass", outputPath);
```

## 核心特性

### 1. 向后兼容性
- 所有原有API保持不变
- 默认行为（上提到直接父类）保持一致
- 现有代码无需修改即可继续使用

### 2. 祖先关系验证
- 自动验证目标类是否为真正的祖先类
- 提供清晰的错误消息
- 防止无效的重构操作

### 3. 灵活的目标选择
- 支持跨越多个继承层次
- 可以精确选择最合适的目标类
- 减少不必要的中间重构步骤

### 4. 完整的错误处理
- 目标类不存在的情况
- 目标类不是祖先类的情况
- 保持原有的所有验证逻辑

## 测试覆盖

创建的测试包括：
1. 获取祖先类列表的功能测试
2. 上提到直接父类的功能测试（向后兼容性）
3. 上提到指定祖先类的功能测试
4. 错误情况的处理测试
5. 文件修改验证测试

## 实际应用场景

### 场景1：多层继承优化
```
Animal (基类)
  ↑
Mammal (中间类)
  ↑
Dog (具体类)
```

现在可以直接将Dog中的通用方法上提到Animal类，而不需要先上提到Mammal再上提到Animal。

### 场景2：重构策略优化
当发现某个方法应该属于更高层的抽象时，可以一步到位地完成重构，避免多次中间步骤。

### 场景3：架构清理
在重构遗留代码时，可以更精确地将方法放置到最合适的抽象层次上。

## 技术实现要点

1. **继承链遍历**: 使用递归方式遍历完整的继承链
2. **类型安全**: 确保所有类型转换的安全性
3. **依赖处理**: 保持原有的依赖字段和方法处理逻辑
4. **可见性调整**: 自动处理跨层级的可见性要求

## 总结

这次改造成功地将Pull-Up Method工具从"只能上提到父类"升级为"可以上提到任意祖先类"，同时保持了完整的向后兼容性。新功能大大提高了重构的灵活性和效率，使开发者能够更精确地进行代码重构。
