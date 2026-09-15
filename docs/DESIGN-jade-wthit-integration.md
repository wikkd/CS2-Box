# 设计：Jade / WTHIT 开箱信息显示（P5 批次 B）

> 状态：**设计定稿，待联网接线**。核心可查性已由 P1（物品 tooltip 概率行）承担；
> 本设计是「锦上添花」的 HUD 增强，面向唯一方块 `ArmoryRecyclerBlock` 与掉落物实体。
> 因当前构建环境**离线且无 Jade/WTHIT API jar 缓存**，接线到 `build.gradle` 会在离线
> 编译时失败，故本设计文档给出全部接入点与可粘贴代码，待联网取 jar 后落地。

## 范围修正（相对早期研究）

- CS2-Box 的箱子/终端机是**手持物品**，Jade/WTHIT 的「准星信息」面向**方块/实体**，
  对手持物品不适用 → 已由 P1 tooltip 覆盖。
- 因此本设计的目标：
  1. **`ArmoryRecyclerBlock`（唯一方块）**：显示拆解价值 / 名称（block provider）。
  2. **掉落物实体（`ItemEntity`）**：显示该箱 5 档概率（纯客户端，读 `box_id` 组件 →
     客户端 `BoxRegistry` → `BoxOdds`）。箱子被丢在地上时准星可见。
  3. 终端机轮次/剩余时间：是物品+UI 场景，**不**用 HUD，画在 `TerminalScreen`
     （`PacketTerminalState` 数据已在客户端）。

## 已核实的 API（来自克隆源码 /tmp/src）

### Jade（NeoForge 1.21.1 = `Jade-1.21-neoforge`，26.x = `Jade-26.3-fabric`）
- 插件：`@WailaPlugin`（`snownee.jade.api.WailaPlugin`）标注 `IWailaPlugin` 实现。
- 注册：`IWailaPlugin.register(IWailaCommonRegistration)`（服务端数据）+
  `registerClient(IWailaClientRegistration)`（客户端 tooltip）。
- 组件：`IBlockComponentProvider extends IComponentProvider<BlockAccessor>`，
  `void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)`。
- 客户端注册：`IWailaClientRegistration.registerBlockComponent(provider, blockClass)`。
- 依赖：`snownee.jade:jade-neoforge`（Modrinth：1.21.1 = `15.10.6+neoforge`，
  26.2 = `26.2.10+neoforge`，均为虚构前提需自核）。

### WTHIT（`wthit-dev-master`）
- 插件：`IWailaPlugin.register(IRegistrar registrar)`（master 标注 `@Deprecated`，新版
  建议 `IWailaCommonPlugin` / 客户端侧 plugin，落地点以目标版本 javadoc 为准）。
- 数据：`IDataProvider`（raw NBT / 类型化 `IData`，**不是** `Codec`）。
- 依赖：`mcp.mobius.waila:wthit-api:neo-<ver>` / `:forge-<ver>` + `lol.bai:badpackets`。

## 目录与文件（落地时）

新增每平台 `jade/` 包（仿 `jei/` 结构），共 6 个平台 × 2 类：

```
<platform>/src/main/java/com/reclizer/csgobox/<platform>/jade/
  CsgoBoxJadePlugin.java   // @WailaPlugin + registerClient → registerBlockComponent
  RecyclerBlockProvider.java // IBlockComponentProvider → ArmoryRecyclerBlock
```

纯新增文件走 `scripts/mirror.sh new`；`build.gradle` / `mods.toml` 为适配差异定点合入。

## 可粘贴代码（Jade，v26_1_2 为例）

```java
package com.reclizer.csgobox.v26_1_2.jade;

import com.reclizer.csgobox.v26_1_2.block.ArmoryRecyclerBlock;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin(CsgoBoxJadePlugin.ID)
public final class CsgoBoxJadePlugin implements IWailaPlugin {
    public static final String ID = "csgobox:jade";

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new RecyclerBlockProvider(), ArmoryRecyclerBlock.class);
    }
}

// RecyclerBlockProvider：显示方块名 + 简短说明
public final class RecyclerBlockProvider implements IBlockComponentProvider {
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        tooltip.add(Component.translatable("jade.csgobox.recycler"));
    }
}
```

> `jade.csgobox.recycler` 等新键进 `common/.../lang/{en_us,zh_cn}.json`。

## build.gradle / mods.toml 接线（联网后）

`gradle.properties` 新增（每平台一个版本属性）：

```properties
jade_version_v1_21_1=15.10.6+neoforge
jade_version_26_1_2=<自核>
jade_version_26_2=26.2.10+neoforge
jade_version_forge_1_20_1=<自核>
jade_version_forge_26_1_2=<自核>
jade_version_forge_26_2=<自核>
```

各平台 `build.gradle`：

```groovy
// 仿 forge_1_20_1 的 JEI 写法：compileOnly 常驻，runtimeOnly 用 -PwithJade
compileOnly "snownee.jade:jade-neoforge:${jade_version_26_1_2}"
if (project.hasProperty('withJade')) {
    runtimeOnly "snownee.jade:jade-neoforge:${jade_version_26_1_2}"
}
```

`neoforge.mods.toml` / `mods.toml`：

```toml
[[dependencies.${mod_id}]]
modId="jade"
mandatory=false
versionRange="[15,)"
ordering="NONE"
side="CLIENT"
```

## 与 P1 / JEI 的关系

- P1 tooltip 是无依赖的「必达」；Jade/WTHIT 是可选「增强」，同一数据源 `BoxOdds`。
- 服务端不需要新数据往返（概率纯客户端可算，`BoxRegistry` 已全量同步）。
- 不引入新 packet；不改变服务端权威逻辑。

## 落地前置条件

1. 联网拉取各平台 Jade/WTHIT API jar（Modrinth/CurseForge），先跑一次
   `./gradlew :<m>:compileJava --offline` 确认能解析（或放入 `local-repo/`）。
2. 按 `scripts/mirror.sh new` 铺 `jade/` 包，`build.gradle`/`mods.toml` 定点合入。
3. 六平台 `clean compileJava` + `:common:test` + `check-version.sh`。
4. 真机回归：装 Jade → 看掉落箱子/回收台；不装 → 无影响。
