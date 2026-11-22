# CS2-Box 预览适配接口（Preview API）

> 状态：**v1 已实现**，forge_1_20_1 先行（v1_21_1 / fabric_1_21_1 待 L4 后镜像接线；26.x / forge_1_18_2 未接线，见平台矩阵）。2026-11-22 预览适配接口 v1。

## 这是什么

给其他模组作者的 3D 预览适配通道：**你声明「某个物品在 CS2-Box 的预览里画什么」，管线负责画好**——顶点探针、auto-fit 缩放、拖拽枢轴、降级链都是 CS2-Box 的既有管线，声明方不碰姿态数学。两层接入方式：

1. **JSON 声明层**（零代码，推荐起点）：在自己模组的 jar 里放一个 JSON 文件。
2. **代码 SPI**（深度适配）：实现一个 Java 类，注册到 `CsboxPreviewAPI`。

## 生效范围

CS2-Box 显示 3D 预览的一切位置：箱子预览网格、终端机报价悬停、检视大图、拖拽跟随、批量开箱总览。2D 小格（物品图标网格）不受影响，保持原物品图标。

## 解析优先级

同一物品命中多层时按序放行，先命中先接管：

1. 代码 SPI 提供者（`priority` 大者先，返回 null = 放行）
2. CS2-Box 内置适配（TACZ 枪、SBW 载具、黑板、女仆等自带深度适配的模组）
3. JSON 声明
4. CS2-Box 默认管线（BEWLR / 刷怪蛋 / 盔甲 / 实体容器 / 方块回退等通用约定）

## JSON 声明层

### 放置位置

自己模组的 jar 内 `assets/<modid>/csgobox_preview/<任意名>.json`，**一个文件一条声明**，`items` 字段可列多个物品。资源包（resource pack）同样生效（`assets/...` 同布局），适合整合包微调。每次客户端资源重载（F3+T）自动重扫，无需重启。

### 字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `items` | 字符串数组 | 是 | 受影响的物品 id 列表（`ns:path`，小写） |
| `mode` | 字符串 | 是 | `"entity"`（实体代演）或 `"block"`（方块形态） |
| `entity` | 字符串 | mode=entity | 预览时渲染的实体类型 id |
| `spawn_data` | JSON 对象 | 否 | 实体 NBT（如生物变体 `model_id`、颜色）；缺 `"id"` 键时自动补实体 id |
| `block` | 字符串 | mode=block | 预览时渲染的方块 id |
| `block_properties` | 字符串→字符串 | 否 | 方块状态属性（如 `{"facing":"north"}`） |
| `rest_pose` | JSON 对象 | 否 | 静止朝向 `{yaw, pitch, roll}`，单位度；拖拽旋转叠加在其外层 |
| `fit_boost` | 数字 | 否 | 自动缩放结果的倍率（默认 1.0，>0；模型偏小时可调大） |
| `priority` | 整数 | 否 | 与其他声明竞争同一物品时的排序键（大者胜，同值按文件路径） |

### 示例

```json
{
  "items": ["mymod:boss_summon"],
  "mode": "entity",
  "entity": "mymod:boss",
  "spawn_data": {"id": "mymod:boss", "Variant": 2},
  "rest_pose": {"yaw": 180},
  "fit_boost": 1.0,
  "priority": 0
}
```

### 容错

坏 JSON、未知物品、不存在的实体/方块 id 都只打一次日志并降级到默认管线，绝不影响其他内容或刷屏。解析诊断会点名文件与原因，方便排查。

## 代码 SPI

### 依赖路线

把 CS2-Box 发行 jar 作为 `compileOnly` 依赖（与你适配 JEI/TACZ 同款惯例）：CurseMaven / Modrinth maven 拉发行版，或本地 jar 直挂。CS2-Box 暂不维护独立 maven 仓库。

### 用法

客户端 setup 阶段（`FMLClientSetupEvent` / Fabric `onInitializeClient`）注册一次即可，注册顺序无关（渲染期才解析）：

```java
public final class MyPreviewProvider extends CsboxPreviewProvider {
    @Override
    public boolean matches(ItemStack stack) {
        return stack.getItem() == ModItems.MY_SUMMON.get();
    }

    @Override
    public PreviewSource preview(ItemStack stack) {
        return PreviewSource.entity(ModEntities.MY_BOSS.get(), null, RestPose.NONE, 1.0F);
    }
}

// client setup:
CsboxPreviewAPI.register(new MyPreviewProvider());
```

### PreviewSource 三种形态

| 形态 | 工厂 | 管线支持 |
|---|---|---|
| 实体 | `entity(EntityType, CompoundTag spawnData, RestPose, fitBoost)` | 探针 + auto-fit + 实体枢轴 + 失败降级平图标 |
| 方块 | `block(BlockState, RestPose, fitBoost)` | 包络 auto-fit + 底面足迹枢轴 + 失败降级平图标 |
| 裸画布 | `raw(RawRenderer)` | 高级逃生口：与内置策略同形画布，**无自动取景**，姿态数学自理 |

`raw` 的画布契约：`(graphics, stack, player, cx, cy, rotation, scale)`，其中 `(cx, cy)` 是预览格中心，`rotation` 是当前拖拽四元数，`scale` 是格子像素尺度。除非你确有 TACZ 级别的渲染需求，请优先用前两种形态。

### 契约

- `matches` / `preview` 每帧对每个可见物品调用——保持廉价，重计算请在 provider 内部缓存。
- 返回 `null` = 放行给内置链与默认管线。
- 只在渲染线程被调用；provider 应无跨帧可变状态（或自限于渲染线程）。
- provider 抛异常会被隔离（打一次日志并跳过该 provider），不会拖垮渲染。

## 平台矩阵

| 平台 | JSON 声明 | 代码 SPI | 状态 |
|---|:---:|:---:|---|
| `forge_1_20_1` | ✅ | ✅ | v1 先行，L4 验证中 |
| `v1_21_1` | ✅ | ✅ | 待 L4 后镜像 |
| `fabric_1_21_1` | ✅ | ✅ | 待 L4 后镜像 |
| `v26_1_2` / `v26_2` / `forge_26_1_2` / `forge_26_2` | — | — | Phase 2（PIP 状态管线无策略链，另案接线） |
| `forge_1_18_2` | — | — | Phase 2（PoseStack 时代，无 GuiGraphics） |

## 稳定性声明

- JSON 字段**只增不删**：未知键产生警告并忽略，旧声明永远可读。
- SPI 类（`com.reclizer.csgobox.api.preview.*`）签名变更随模组版本走 CHANGELOG；`raw` 回调的画布契约与渲染时代绑定，跨大版本升级时以本文档为准。
- 未接线的平台不会消费声明与 provider（也不会报错）。

## 已知边界

- 2D 小格不接管（保持物品图标）。
- `spawn_data` 是原始 JSON→NBT 文本解析，语法遵循 SNBT（与原版刷怪蛋 `entity_data` 同形）。
- rest_pose 的 yaw 方向跟随预览模型空间（纯 yaw=180 与内置 SBP「正面朝外」行为一致）。
- 同一物品同时存在声明与内置适配时内置适配优先（内置适配含降级链，更完整）。
