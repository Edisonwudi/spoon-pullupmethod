#!/bin/bash

# Pull-Up-Method 重构工具示例运行脚本

echo "正在编译项目..."
mvn clean package -q

if [ $? -ne 0 ]; then
    echo "编译失败！"
    exit 1
fi

echo ""
echo "编译完成！"
echo ""

JAR_FILE="target/pull-up-method-refactoring-1.0.0.jar"
EXAMPLES_PATH="src/test/resources/examples"

echo "========================================"
echo "示例 1: 列出所有可用的类"
echo "========================================"
java -jar $JAR_FILE --source $EXAMPLES_PATH --list-classes

echo ""
echo "========================================"  
echo "示例 2: 列出 Dog 类的所有方法"
echo "========================================"
java -jar $JAR_FILE --source $EXAMPLES_PATH --class examples.Dog --list-methods

echo ""
echo "========================================"
echo "示例 3: 将 Dog 类的 eat 方法上提到 Animal 类"
echo "========================================"
java -jar $JAR_FILE --source $EXAMPLES_PATH --class examples.Dog --method eat --verbose

echo ""
echo "运行完成！"
