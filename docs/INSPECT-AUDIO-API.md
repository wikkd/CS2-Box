# 检视音频接口 v1（2026-11-22）

第三方模组让任意物品接入检视页的音乐开关：检视页工具栏的检视钮对该物品变为
播放/停止开关——点击播放音频，再点停止，关闭检视页自动停止。

内置路径无需任何代码：原版与模组 `RecordItem` 子类自动播放唱片音轨（jukebox
同款播放管线，跟随设置里的唱片机音量滑条）。其他物品有两条零门槛递增的接入
通道：JSON 声明层（资源包/模组 jar 声明物品→音效，见下节）与代码 SPI（Java
注册 provider）。

## 代码 SPI

公开面在 `com.reclizer.csgobox.api.audio`（mcshared 单源，全平台同一 FQN）：

```java
// 客户端 setup 期注册（FMLClientSetupEvent 等）
CsboxInspectAudioAPI.register(new CsboxInspectAudioProvider() {
    @Override
    public boolean matches(ItemStack stack) {
        return stack.is(MY_ITEM);
    }

    @Override
    public SoundEvent inspectSound(ItemStack stack) {
        return MY_SOUND; // null = 放行给下一家
    }
});
```

契约：

- 解析顺序：provider 优先（`priority` 大者先，默认 0，同优先级按注册顺序）
  → JSON 声明层（同物品多声明 priority 大者先，同分按文件路径字典序）
  → 内置 RecordItem 路径；
- `matches` / `inspectSound` 抛异常的 provider 打一次 WARN 日志后整体摘除
  （隔离降级，不再参与解析）；
- 返回的 SoundEvent 走 jukebox 播放管线（RECORDS 音源，唱片机音量滑条生效）；
- 播放状态（复播/停止/检视钮下划线态）由检视页维护，第三方无需干预。

## JSON 声明层

零代码接入：第三方模组 jar 或资源包在
`assets/<modid>/csgobox_inspect_audio/<任意名>.json` 声明物品→音效，一份声明
可列多个物品：

```json
{
  "items": ["mymod:my_item"],
  "sound": "mymod:my_sound",
  "priority": 0
}
```

- `items` 必填，非空数组，元素为物品 id（`ns:path`）；
- `sound` 必填，已注册的 SoundEvent id（原版唱片音轨是点号形态，如
  `minecraft:music_disc.pigstep`）；
- `priority` 可选整数，默认 0；
- 未知字段警告忽略；形状错误的声明整体拒绝并打日志，不影响其余声明；
- 音效 id 未注册：命中时打一次 WARN 后跳过该条声明，继续下一条；
- 客户端资源管理器每次重载（F3+T、模组增删）自动重扫，无需重启游戏。

## 平台可用矩阵

| 平台 | 消费端状态 |
| --- | --- |
| forge_1_20_1 | 已接线（检视页消费） |
| 其余七平台 | API 面已就绪（同 FQN 编译），随检视功能镜像后接线 |

在未接线平台注册的 provider 不报错，但检视钮暂不播放音频。

## 稳定性

- 公开面的方法与字段只增不删；SoundEvent 描述符跨版本稳定。
- 播放控制面（平台侧检视音频播放器）不属公开 API，第三方请勿依赖；未来若
  开放控制入口会在本文件记录。
- JSON 声明层字段只增不删；未知字段以警告忽略，向前兼容。
