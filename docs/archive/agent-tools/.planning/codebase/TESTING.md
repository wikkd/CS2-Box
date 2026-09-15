<!-- refreshed: 2026-06-29 -->
# 测试模式

**分析日期：** 2026-06-28
**最近增量更新：** 2026-06-29

## 测试框架

**运行器：** 不存在。仓库**不包含任何自动化测试代码**。无 `src/test/java/`，无 JUnit，无 GameTest 类。

**为什么没有测试：** 该团队验证方法为**手动 UAT**，在真实 Minecraft 客户端中测试开箱动画、命令树、JSON 生成和配方合成。

## 起测试作用的防御性代码

以下方法编码了不变量，起到轻量级运行时测试的作用：
- `PacketValidation.requireSameSize` / `requireMaxSize` — 快速失败
- `BoxDefinition.read` / `PacketSyncBoxItems.read` — 超出范围时抛出 `DecoderException`
- `BoxJsonLoader.parseWeights` — 钳位或默认，WARN 日志
- `BoxJsonLoader.deleteFile` — 防止路径遍历
- `RandomItem.randomItemsGrade` — 非正权重时返回最低等级

## 手动 UAT 检查清单

1. 默认 JSON 自动生成和加载
2. 对象风格和遗留字符串风格物品
3. 边界情况：缺失 ID、空等级、异常权重、奇数长度实体列表
4. 箱子变体：需要钥匙、无钥匙、错误钥匙
5. 动画最终物品与实际奖励匹配
6. ESC 退出不破坏下次开箱
7. 空箱警告不被 3D 模型遮挡
8. 下界合金钥匙仅锻造台合成

## 推荐的测试目标（待添加）

1. **`RandomItem.randomItemsGrade`** — 纯函数
2. **`PacketValidation`** — 集合不变量
3. **`BoxJsonLoader.parseItem` + `parseWeights` + `parseEntities`** — JSON 解析
4. **`BoxJsonLoader.parseItem` 遗留字符串路径** — 向后兼容
5. **GameTest：`/csbox reload` 和 `/csbox add hand`** — 注册表变更回归

推荐 JUnit 5 + AssertJ，`src/test/java/` 作为源码根。

## 2026-06-29 更新

- 配方路径已更正：`data/csbox/recipes/` → `data/csgobox/recipe/`
- Java 源路径已从 `com.reclizer.csgobox` 更正
- 无新自动化测试引入

---

*测试分析：2026-06-28（增量更新：2026-06-29）*
