<!-- generated-by: gsd-doc-writer -->
# CS2-Box 测试指南

> CS2-Box 使用 NeoForge 内置的 GameTest 框架进行集成测试,加上手动测试覆盖 GUI 与运行时行为。

## 测试框架与配置

CS2-Box 使用 **NeoForge GameTest 框架**进行集成测试。GameTest 是 Minecraft 内置的测试系统,在实际游戏实例中运行测试,允许模组在受控测试环境中验证方块行为、物品交互和游戏事件。

GameTest 框架随 NeoForged 提供,无需额外依赖。

## 前置要求

- **Java 21**(v1_21_1)或 **Java 25 + `--enable-preview`**(v26_1_2),由 `java.toolchain.languageVersion` 强制
- Gradle Wrapper(项目自带)
- NeoForged userdev 插件(`build.gradle` 已配置)

## 运行测试

### GameTest 服务器(主要方式)

```bash
./gradlew gameTestServer
```

启动专用测试服务器,在无头环境中运行所有已注册的 GameTest。

### 客户端 GameTest

```bash
./gradlew runGameTestClient
```

在可见客户端窗口中运行测试,可视化测试结构(成功/失败用绿/红标记)。

### 单个测试方法

```bash
./gradlew gameTestServer --tests "csgobox.*"
./gradlew gameTestServer --tests "csgobox.BoxOpeningTest.testBoxOpens"
```

## 编写新测试

### 文件位置

测试代码放在对应模块的 `src/test/java/` 目录(默认不存在,需手动创建):

```
common/src/test/java/        # 跨版本测试
v1_21_1/src/test/java/       # 1.21.1 平台特化测试
v26_1_2/src/test/java/       # 26.1.2 平台特化测试
```

### 测试类结构

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

**关键注解**:

- `@GameTestHolder("csgobox")` — 此模组测试的命名空间(用 mod ID)
- `@GameTest` — 标记测试方法
  - `batch` — 分组相关测试一起运行(如 `box_loot`、`smithing_recipe`)
  - `setupTicks` — 测试运行前等待的 tick 数

### 测试辅助方法

`GameTestHelper` 提供测试世界交互 API:

- `helper.succeed()` — 标记测试通过
- `helper.fail(String message)` — 标记测试失败
- `helper.assertItemStackPresent(ItemStack stack, String message)` — 断言物品存在
- `helper.setBlock(pos, block)` — 放置方块
- `helper.getBlock(pos)` — 查询指定位置的方块

### 批次组织

按功能分组:

```java
@GameTest(batch = "box_loot")
public void testRareItemDrop(GameTestHelper helper) { }

@GameTest(batch = "box_loot")
public void testCommonItemDrop(GameTestHelper helper) { }

@GameTest(batch = "smithing_recipe")
public void testNetheriteKeySmithing(GameTestHelper helper) { }
```

## 手动测试

GUI 渲染、动画、音效、CS:GO 风格的滚动体验等无法用 GameTest 自动化覆盖,需要手动测试。

### v26_1_2 GUI 验收清单

来自 `.planning/csbox-gui-26.1.2-fix-guide.md`:

- [ ] CsboxScreen 主预览的物品视觉居中(屏幕宽度约 16%)
- [ ] CsLookItemScreen 返回按钮的白色加粗文案清晰可见(不被矩形覆盖)
- [ ] 未配置箱子的 banner 居中显示,不与 3D 预览重叠
- [ ] ButtonPalette 按钮色在 hover 时切换深浅
- [ ] RenderFontTool 限宽省略号正确截断(长物品名)
- [ ] csgo_background.png 在 CsboxProgressScreen 上显示完整镜片框 + dim 灰色外层

### v1.0.5 功能验收清单

来自 `docs/MANUAL-TESTING-v1.0.5.md`:

- [ ] TC-1:右键 csgo_box 打开预览界面,显示 2×10 物品网格
- [ ] TC-2:放入对应钥匙,点开启按钮触发滚动动画
- [ ] TC-3:服务端授权 RNG 决定结果,客户端动画与服务端一致
- [ ] TC-4:动画结束显示 CsLookItemScreen,展示结果物品
- [ ] TC-5:`/csbox info` 显示所有已注册箱子与加载错误
- [ ] TC-6:`/give @p csgobox:csgo_box` 正确发放箱子物品
- [ ] TC-7:`全新的开始` 成就首次主动开箱时解锁
- [ ] TC-8:`导购` 成就累计 200 个箱子后解锁
- [ ] TC-9:csgo_key3 通过锻造台配方升级 csgo_key2

## CI 集成

CI 已配置三个 workflow（见 `.github/workflows/`）：

- `build.yml` — 3 平台编译 + 打包矩阵（`1.21.1` / `26.1.2` / `26.2`）+ `common-test` job（版本同步、AnimRenderOps 漂移、CONSTRAINT-001、common 单测）
- `gametest.yml` — GameTest 集成测试（1.21.1 + 26.1.2，当前无用例时跳过）
- `pr-checks.yml` — PR 描述模板与自查勾选校验

GameTest 用例加入后自动被 `gametest.yml` 的矩阵执行，无需修改 CI 配置。

## 测试命名规范

- 类名:`XxxTest.java`(如 `BoxOpeningTest.java`)
- 方法名:`testXxx`(如 `testBoxOpens`)
- 批次名:小写下划线分隔(如 `box_loot`、`smithing_recipe`)
- 测试命名空间:用 mod ID `csgobox`

## 调试失败的测试

1. **可视化运行**:`./gradlew runGameTestClient`
2. **测试结构**在游戏中用红/绿标记可视化
3. **重载结构模板**:F3+T 重新加载
4. **详细日志**:`forge logs/test/` 或 `.minecraft/logs/`
5. **断点**:IntelliJ IDEA 中以 Debug 模式启动 `runGameTestClient`,在测试方法上打断点
