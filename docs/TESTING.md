<!-- generated-by: gsd-doc-writer -->
# 测试指南

本文档涵盖 CS2-Box Minecraft 模组的测试基础设施。

## 测试框架和配置

CS2-Box 使用 **NeoForge GameTest 框架**进行集成测试。GameTest 是 Minecraft 内置的测试系统，在实际游戏实例中运行测试，允许模组在受控测试环境中验证方块行为、物品交互和游戏事件。

GameTest 框架随 NeoForged 提供，无需额外依赖。

**前置要求：**
- Java 21（见 `build.gradle` 中的 `java.toolchain.languageVersion`）
- 带 NeoForged userdev 插件的 Gradle（在 `build.gradle` 中已配置）

## 运行测试

### GameTest 服务器（主要方式）

使用专用测试服务器运行所有已注册的 GameTest：

```bash
./gradlew gameTestServer
```

这会启动一个在无头环境中运行测试的专用 Minecraft 服务器实例。

### 客户端 GameTest

在可见客户端窗口中运行测试：

```bash
./gradlew runGameTestClient
```

### 单个测试方法

要运行特定测试批次，使用 `--tests` 参数：

```bash
./gradlew gameTestServer --tests "csgobox.*"
```

将模式替换为完全限定的测试类名或批次名。

## 编写新测试

### 文件位置

在 `src/test/java/` 目录创建测试类（此目录尚不存在，需手动创建）：

```
src/
  main/java/     # 源代码
  test/java/     # 测试代码（如不存在需创建）
```

### 测试类结构

GameTest 类使用 `net.neoforged.neoforge.gametest` 中的注解：

```java
package com.reclizer.csgobox.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.junit.jupiter.api.Test;

@GameTestHolder("csgobox")
public class BoxOpeningTest {

    @GameTest(batch = "box_functionality", setupTicks = 10)
    public void testBoxOpens(GameTestHelper helper) {
        // 测试逻辑
        helper.succeed();
    }
}
```

**关键注解：**
- `@GameTestHolder("csgobox")` - 此模组测试的命名空间
- `@GameTest` - 标记测试方法
  - `batch` - 将相关测试分组（一起运行）
  - `setupTicks` - 测试运行前等待的 tick 数

### 测试辅助方法

使用 `GameTestHelper` 方法与测试世界交互：
- `helper.succeed()` - 标记测试通过
- `helper.fail(String message)` - 标记测试失败
- `helper.assertItemStackPresent(ItemStack stack, String message)` - 断言物品存在
- `helper.setBlock(pos, block)` - 放置方块
- `helper.getBlock(pos)` - 获取指定位置的方块

### 批次组织

使用 `batch` 参数按功能分组测试：

```java
@GameTest(batch = "box_loot")
public void testRareItemDrop(GameTestHelper helper) { }

@GameTest(batch = "box_loot")
public void testCommonItemDrop(GameTestHelper helper) { }
```

## 覆盖率要求

本项目未配置覆盖率阈值。测试覆盖率目前是临时性的。

## CI 集成

本项目未配置 CI/CD 流水线。如需将游戏测试添加到 CI，在 `.github/workflows/gametest.yml` 创建工作流文件：

```yaml
name: Game Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: 21
      - name: Run GameTest Server
        run: ./gradlew gameTestServer
```

## 测试命名规范

GameTest 类应遵循以下规范：
- 类名：`XxxTest.java`（如 `BoxOpeningTest.java`）
- 方法名：`testXxx`（如 `testBoxOpens`）
- 批次名：小写下划线分隔（如 `box_loot`）
- 测试命名空间：使用模组 ID `csgobox`

## 调试测试

调试失败的测试：

1. 使用客户端运行：`./gradlew runGameTestClient`
2. 测试结构会在游戏中以红/绿标记可视化
3. 使用 F3+T 重新加载结构模板
4. 查看 `forge logs/test/` 获取详细错误输出
