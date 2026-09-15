<!-- generated-by: gsd-doc-writer -->
# CS2-Box 配置指南

> CS2-Box 通过 NeoForge 原生 `ModConfigSpec` 持久化 TOML 配置 + `config/csbox/*.json` 文件配置箱子数据。**Cloth Config 可选**：装 Cloth Config（`cloth_config`，客户端）后，模组列表的「Configure」按钮会打开由 `CsboxClothConfigScreen` 生成的 GUI，读写同一份 `ModConfigSpec`/`ForgeConfigSpec` 绑定——**存储仍是 TOML**，无 Cloth 时行为不变（保持纯 TOML 编辑）。

## 1. 配置文件位置

| 环境 | 路径 |
|---|---|
| 客户端 | `config/csgobox.toml` |
| 服务端 | `config/csgobox.toml` |

模组注册一个通用配置文件(`csgobox.toml`),客户端与服务端共用同一份。

## 2. 配置项总览

`CsboxConfig.java` 在六个平台 loader 中定义一致(7 个字段,2 个 TOML 分组)。
自 2.0.0 起仅保留服务端/服主向配置项——音效音量、动画时长/速度、物品名显示、背景样式与模糊半径、调试日志等玩家向/开发向选项已从配置中移除并硬编码为固定默认值(不允许配置)。

### 2.0 GUI 编辑（可选，Cloth Config）

- 客户端安装 **Cloth Config** 后（NeoForge 1.21.1 需 `[15.0,)`、26.1.2/26.2 需 `[26.1,)`/`[26.2,)`，Forge 1.20.1 需 `[11.0,)`），模组列表（Mods）里本模组会出现 **Configure** 按钮，点击进入 `CsboxClothConfigScreen` 生成的配置屏。
- 屏内分 `[general]` / `[advanced]` 两个分组，与下方 TOML 项一一对应；每个控件可编辑、可「恢复默认」。
- 保存时写回同一 `ModConfigSpec`/`ForgeConfigSpec` 并落盘 `config/csgobox.toml`——**存储不变**，与直接编辑 TOML 完全等价。
- 未安装 Cloth Config 时无 Configure 按钮，纯 TOML 编辑流程不变。

### 2.1 `[general]` 通用设置

| 配置项 | 类型 | 默认值 | 范围 | 说明 |
|---|---|---|---|---|
| `globalDropRatePercent` | 整数 | `100` | `0` = 关闭,无上限 | 全局掉落概率百分比 |

### 2.2 `[advanced]` 高级设置

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `loadDefaultBoxes` | 布尔 | `true` | 启动时自动从 `config/csbox/*.json` 加载箱子定义 |
| `enableAchievements` | 布尔 | `true` | 启用成就系统;关闭时仍累积统计(保留进度) |
| `enableHotReload` | 布尔 | `true` | 监听 `config/csbox/*.json` 文件变化并自动热重载(300ms 防抖) |
| `bulkOpenCount` | 整数 | `0` | 单次批量开箱上限(0 = 无上限),服务端权威截断 |
| `jsonErrorAudience` | 枚举 | `OP_ONLY` | JSON 加载错误提示受众:`OP_ONLY`(仅 OP)/ `EVERYONE`(所有玩家) |
| `damageItemByWear` | 布尔 | `true` | 抽出的物品若有耐久,按磨损值百分比损耗耐久(不会碎裂) |

## 3. 宝箱数据配置(JSON schema)

可通过 `config/csbox/` 目录下的 JSON 文件定义自定义宝箱。**文件名(不含 `.json`)即为箱子 ID**。

### 3.1 顶级字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | 字符串 | 是 | 宝箱在界面显示的名称 |
| `type` | 字符串 | 否 | 箱子类型:**`csbox`**(默认,普通宝箱)/ `terminal`(终端机)。v2.0.0 起为唯一判定字段,决定物品注册为 `ItemCsgoBox` 还是 `ItemTerminal` |
| `key` | 字符串 | 仅普通箱 | 所需钥匙物品 ID;`minecraft:air` 免钥匙。**终端机禁止使用 `key` 字段**(严格分离,出现即报 schema 错误) |
| `drop` | 浮点数 | 否 | 默认实体掉落概率(0.0 到 1.0)。**当 `entity` 为纯实体 ID 列表时,本值是每个实体的掉落概率;若 `entity` 用 ID/概率交替数组,本值作为未显式指定概率实体的兜底** |
| `random` | 浮点数组[5] | 否 | 5 个等级权重(grade1 到 grade5 顺序) |
| `entity` | 数组 | 否 | 掉落该宝箱的实体 ID 列表(或 ID/概率 交替数组)。玩家自建 `terminal.json` 时可给终端机配置危险生物掉落 |
| `grade1` ~ `grade5` | 数组 | 否 | 各等级物品清单(industry / consumer / mil_spec / restricted / classified) |
| `enabled` | 布尔 | 否 | v2.1.0：`false` 整箱跳过（不注册、不报错，作者主动停用）。默认 `true` |
| `requires` | 字符串数组 | 否 | v2.1.0：所需模组 id 列表。任一未加载时整箱跳过并报「缺少依赖」错误（联动包防半残箱子） |
| `icon` | 字符串/整数 | 否 | v2.1.0：每箱图标。整数 = CustomModelData（配资源包）；`ns:path` 字符串 = item-model id（26.x 直接渲染；1.21.1 / 1.20.1 记警告跳过） |
| `discount` | 浮点数 | 否 | v2.1.0：终端售价折扣（0..1，如 `0.2` = 8 折）。仅终端机有意义 |
| `stock` | 整数 | 否 | v2.1.0：终端**全局**库存上限（-1 = 无限，默认）。售罄后终端显示空态并拒绝购买；`restock_minutes` 补货。内存态，重启恢复满库存 |
| `restock_minutes` | 整数 | 否 | v2.1.0：补货间隔（分钟）。`0` = 永不自动补货（默认） |
| `max_per_player` | 整数 | 否 | v2.1.0：每人开箱上限（-1 = 无限，默认）。内存态，重启清零；单次/批量均生效，验证通过后才消耗钥匙 |
| `cooldown_seconds` | 整数 | 否 | v2.1.0：每箱开箱冷却（0 = 无，默认） |
| `permission` | 字符串 | 否 | v2.1.0：权限节点。默认放行；整合包通过 `CsgoBox.PERMISSION_GATE` 钩子接入权限后端（如 LuckPerms） |

> **文件名校验（v2.1.0）**：文件名（不含 `.json`）即箱子 id，只允许小写字母、数字、
> `_`、`.`、`/`、`-`。非法文件名（空格、大写、中文、`#` 等）直接拒绝加载并提示合法示例。

> **JSON Schema（v2.1.0）**：`docs/box-schema/box.schema.json` 提供完整字段定义
> （含全部新字段），VS Code / IDEA 配置后即可离线补全与校验。运行时仍以 Java 校验器为准。

> **箱子类型判定（v2.0.0 起）**：`type` 字段是**唯一**判定机制——`"type": "terminal"` 注册为终端机物品（`ItemTerminal`，打开终端谈判屏），`"type": "csbox"` 或省略为普通宝箱。终端机与普通箱**字段严格分离**：终端机不持有 `key` 字段（旧版（v2.0.0 之前）配置里的 `key: "minecraft:air"` 已在升级时自动迁移删除），普通箱的 `key: "minecraft:air"` 仅表示免钥匙、绝不会把宝箱变成终端机。`terminal.json` 缺少 `type` 会被拒绝加载并给出明确报错（防止静默退化成免费开箱）。

> **多终端支持**：终端机与普通宝箱一样，**一个 JSON 文件注册一个物品**——任意文件（如 `terminal2.json`、`armory_shop.json`）只要声明 `"type": "terminal"`，就会注册为对应 id 的终端机（`csgobox:terminal2` 等），拥有自己独立的谈判掉落池，互不干扰。`csgobox:terminal` 本身是**静态注册**的（与 `csgobox:csgo_box` 同机制）：即使 `terminal.json` 不存在，物品也始终存在（打开显示空谈判屏），`terminal.json` 存在时为其提供默认奖池。额外终端机通过 `/give` 或创作模式标签获取；军火商村民交易固定出售 `csgobox:terminal`。完整示例见 `docs/examples/`（`weapon_dealer.json` / `enchant_vendor.json` / `supply_outpost.json`）。

### 3.2 物品对象

```json
{
  "id": "minecraft:diamond",
  "count": 1,
  "components": {
    "minecraft:custom_name": "\"闪亮钻石\""
  }
}
```

- `id`:物品命名空间 ID（与 `tag`/`loot_table` 三选一，只能出现一个）
- `count`:数量(默认 1)；v2.1.0 支持 `[min,max]` 区间（开箱/购买时随机，预览显示下限）
- `weight`(可选, v2.1.0):档内权重(默认 1,越大越常见;`0` 临时禁用该条目,不用删配置)
- `tag`(可选, v2.1.0):**以 `#` 开头**的物品标签引用,如 `"#minecraft:swords"`——加载时展开为当前成员物品,自动跟随整合包增减。不带 `#` 的 `tag` 仍是旧版 NBT 字符串(向后兼容)
- `loot_table`(可选, v2.1.0):战利品表引用,如 `"minecraft:chests/simple_dungeon"`——开箱时服务端掷该表(预览显示桶占位)。可联动任意模组战利品表
- `enchant`(可选, v2.1.0):随机附魔快捷——`true`(任意可附魔随机)或 `{"id": "minecraft:sharpness", "level": [3,5]}`(指定附魔与等级/区间)
- `price` **已移除(v2.1.0 起)**:终端价格不再写在箱子 JSON 里,统一由 `config/csbox/_prices.json` 管理(见 [3.3 价格表](#33-价格表v210-起))。残留的 `price` 字段会作为 schema 错误上报(`/csbox info error`),**但在下次 `/csbox reload`(或热重载/重启)时会自动迁移进价格表并清零该字段**(见「旧版自动迁移」),迁移前该物品没有价格（终端机不售卖、拆解为 0，直到补价）。购买时实际成交价 = 价格表中的价(范围价 = 当次报价随机采样价)+ 磨损惩罚(仅对无耐久条物品,按磨损百分比加价:`ceil(基础价 × 20% × 磨损值)`,满磨损 +20%,向上取整),再叠加箱子 `discount` 折扣
- `components`(可选):Minecraft 1.21+ data components
- 旧版 `tag` 字符串字段**仍可加载**(向后兼容)

> **v2.1.0 完整示例**：见 `compat-packs/apotheosis-pack/gem_crate.json`（`requires`、
> `weight`、`count` 区间、`enchant` 的用法示范）。生成工具 `scripts/boxgen.py`。

### 3.3 价格表(v2.1.0 起)

所有终端机的物品成交价(武库点数)统一放在 **`config/csbox/_prices.json`**。`_`
前缀保证它不会被当成箱子加载(与教程文档同机制)。格式为**物品 id → 固定非负整数
或 `[min, max]` 随机范围**(与 `count` 区间同风格)的扁平对象:

```json
{
  "minecraft:diamond_sword": 1500,
  "minecraft:arrow": [200, 400],
  "tacz:modern_kinetic_gun#tacz:deagle_golden": 30000
}
```

- **范围价随机(v2.1.0)**:值写成 `[min, max]`(闭区间,两元素均为非负整数且
  `min ≤ max`)时,该物品**每次终端机报价从区间内均匀随机取整**——同一件物品不同
  轮次价格会浮动,随机采样在服务端进行(会话创建时与磨损/花纹同一随机源),客户端
  显示与扣账都使用这份采样后的价。范围同样参与箱子 `discount` 折扣(先采样再打折)。
  固定价就是 `[v, v]`,直接写单个整数即可;不想用范围就不写数组,零迁移成本。

- **未定价即报错(v2.1.0,无默认价回退)**:箱子物品加载时按物品 id(或 `id#变体`)
  在价格表查价,命中即用;未命中 = **没有价格**——终端机不会报价该物品、拆解台
  回收为 0,且装载器对 id 物品**直接报错拒绝加载该箱子**(必须先在 `_prices.json`
  补价)。`loot_table` 条目没有固定物品 id,是唯一例外:不参与终端机报价(开箱掉落
  正常),加载时只记 warning。
- **变体键**:同一物品 id 但靠 NBT 区分不同价格的物品(TACZ 枪械/弹药),用
  `id#变体` 子键。加载器从物品条目的旧版 `tag` 字符串读取 `GunId` / `AmmoId`
  拼键,例如 `"tacz:modern_kinetic_gun#tacz:ak47"`。查价优先变体键,没有则回退
  纯 id 价(想给整类枪统一价就只写纯 id)。
- **纯全局、无箱子级覆盖**:同一个物品 id(或变体)在所有终端机同价。同 id 带不同
  附魔/组件(如 `minecraft:enchanted_book` 多档附魔书)无法按价格区分,只能取表内
  单值——这是"统一管理"的取舍。
- **完整性**:价格表是作者自维护文件,模组**不会自动生成空表**;唯一的自动写入是
  「旧版自动迁移」把历史 `price` 转进来(见下)。`/csbox validate` 会一并校验
  `_prices.json`(非法键/负值/小数/非数字/范围长度≠2/`min>max` 都报错,报错条目
  被跳过,其余条目照常生效);`/csbox info error` 也能看到价格表诊断。改动后
  `/csbox reload` 或热重载即时生效。
- **旧版自动迁移(v2.1.0 适配)**:每次 `loadAll` / `/csbox reload`(含热重载)前,
  `LegacyPriceMigration` 自动扫描箱子 JSON 里残留的 `price`,把**有效且能定位物品
  id** 的条目转入 `_prices.json`(键 = `id` 或 `id#变体`);同一键多处价格不同时
  **按平均值(四舍五入)迁移**,然后从箱子 JSON 里删除该字段(幂等,下一次不再改写)。
  已经写在 `_prices.json` 里的价格优先,不会被旧配置覆盖。`#tag` 物品标签 /
  `loot_table` 条目与非法价格(负数/小数/非数字)无法自动迁移,保留原位继续报错,
  需手动处理;`_prices.json` 本身损坏时整个迁移停止(不写任何文件),避免丢价格。
- **拆解台计价对齐(v2.1.0)**：武库拆解台对**盖章物品**先按价格表计价——该物品 id
  (或 `id#变体`，TACZ 枪/弹)在 `_prices.json` 有价时，拆解回收 = 表价 × **90%**
  (向上取整，如表价 4500 → 4050、表价 10 → 9)；**范围价每次拆解时先随机采样
  一次再 ×90%**（与购买价同规则、服务端独立采样）。表外(未定价)物品**不可拆解**(产出 0,
  物品留在输入槽)——没有等级价回退。拆解资格不变(仍只收开箱盖章带 `csgobox:grade` 的物品)；`ArmoryRecycleEvent` 仍可
  否决或改价。价格表由 `PriceTableRegistry` 在每次 load/reload 时发布给拆解台。
- IDE 补全与离线校验:`docs/box-schema/prices.schema.json`。

### 3.4 默认文件生成

首次启动时 `BoxJsonLoader.loadAll()` 会保证 `config/csbox/` 目录存在，并：

- **不生成任何箱子默认配置（含终端机）**：2.0.0 起终端机也出厂即**空箱**——`terminal.json` 不再自动生成，终端机与默认箱行为一致（打开为空谈判屏，不绑定奖池）。旧版（无 `type` 且带遗留 `key`）的 `terminal.json` 会自动迁移；空文件视为合法未配置状态；损坏文件**保留原位**（让 `/csbox info error` 持续报告）并另存 `terminal.json.corrupt-<时间戳>` 备份副本（v2.1.0 行为，此前是移出加载路径）。
- 把**随包内置**的 `_tutorial_v<版本>.md` 教程文档（中英双份）复制到 `config/csbox/`——**无网络依赖、离线可用**。**服务端与客户端都会触发**：客户端 `onClientSetup` 同样调用复制（`BoxJsonLoader.downloadTutorialsAsync`），专用服务器玩家的本机 `config/csbox/` 也会落一份；单机/局域网与服务端同一目录，复制幂等且同 JVM 串行，不会重复覆盖。只复制缺失文件、不覆盖玩家修改；当前版本就位后才清理旧版本残留。**Mod 列表入口**：模组详情页的描述会写明教程位置，且详情页有"Website（地球）"按钮（`displayURL`）可直接打开在线教程（`gitee.com/hou-xiangling/CS2-Box/docs/tutorials/`）。

**普通箱子同样没有内置默认配置**：`weapon_supply_box.json` 等普通箱文件需要由玩家/服主自行创建（或从教程文档中复制示例），创建后才会出现在创造物品栏。

### 3.5 五个等级命名

| 等级 ID | 显示名 |
|---|---|
| `consumer` | 消费级(灰) |
| `industrial` | 工业级(浅蓝) |
| `mil_spec` | 军规级(蓝) |
| `restricted` | 受限级(紫) |
| `classified` | 保密级(粉/红) |

## 4. 配置重载行为

**TOML 配置(`config/csgobox.toml`)重载**:

- 游戏中 `/reload` 命令可触发 `ModConfigEvent.Reloading`,日志记录
- 重启游戏也可应用新值

**箱子 JSON(`config/csbox/*.json`)重载**:

- `/csbox reload` 立即重新加载（`/csbox reload tutorial` 同时刷新教程文档）
- `enableHotReload`（默认开启）开启时，`config/csbox/*.json` 文件变化会自动热重载（300ms 防抖）
- **价格表随箱重载**：`/csbox reload` 与热重载会同时重读 `config/csbox/_prices.json`。
  加载缓存按「箱子文件内容 + 价格表内容」双重 hash 判定，价格表一改，所有箱子定义
  即时按新价重算（箱子文件本身未改也会失效重载）。
- 现有 JSON 文件**不会被覆盖**(只读)
- **`/csbox validate [box]`（v2.1.0）**：干跑校验——解析指定箱子（或全部）**不注册、不进缓存**，
  只报告「能否加载」与诊断；作者可循环「改→验→改」而不影响线上状态。校验结果不污染
  `/csbox info error` 列表。
- **加载诊断（v2.1.0 增强）**：`/csbox info error` 现在还会报告「空档位」（某档 0 个有效物品，
  warning 级）、「缺模组 id」（unknown item id 提示是模组未装还是拼写错）、「非法文件名」。
- **损坏文件**：语法损坏的 JSON 与损坏的 `terminal.json` 都**保留原位**并记录错误
  （/csbox reload 后仍持续报告，直到作者修复或删除）；`terminal.json` 另存
  `terminal.json.corrupt-<时间戳>` 备份副本。

## 5. 运行时一致性

- **`CONFIG` 是 `public static final`**,永不为 null。**删除所有 `CONFIG != null` 守卫检查**——这是 v1.0.5 修复的关键 bug
- 调用方通过 `CsgoBox.CONFIG.<fieldName>()` 访问(扁平化访问,不是 `CONFIG.section.fieldName`)
- Java 端通过 `CsboxConfig` 构造器中的 `builder.define*().get()` 内联求值,不再有 `init()` 延迟填充(那也是 v1.0.5 修复点)
- **Cloth Config 仅为可选 GUI**：配置屏通过公开的 `set*` 写回 `ModConfigSpec`/`ForgeConfigSpec` 的 `ConfigValue`（再 `CONFIG_SPEC.save()`），不迁移存储、不引入第二份配置

## 6. 配置开关与运行时一致性

- `enableAchievements=false` 关闭成就弹窗与 toast,但 `Stats.CUSTOM` 仍累积,重新开启后即恢复触发
- `loadDefaultBoxes=false` 可阻止自动加载 `config/csbox/*.json`,适合纯数据包驱动的服务端

修改这些开关并 `/reload` 即可生效;JSON 改动执行 `/csbox reload`（或热重载）即可生效。

## 7. 箱子与物品注册表（联机注意，v2.1.0）

**箱子是数据，不是物品 id。** 2.1.0 起物品注册表是**编译期常量**，不再随 `config/csbox/` 变化：

- 随模组发布的 5 个默认箱子 id（`ammo_crate` / `attachment_crate` / `gun_crate` /
  `normal_crate` / `tacz_terminal`）固定注册；其余箱子（包括你自建的文件名）统一使用通用物品
  `csgobox:csgo_box`，`"type": "terminal"` 的箱子使用 `csgobox:terminal`。
- 箱子身份存在物品的 `csgobox:box_id` 标签（1.21+ 为数据组件）里；箱子定义来自服务端同步的
  `BoxRegistry`（玩家登录时 `PacketSyncBoxDefinitions` 全量下发）。界面类型（普通箱 / 终端机）
  也按定义分派，与"物品叫什么"无关。
- **获得箱子**：`/csbox give <玩家> <箱子id> [数量]`（权限等级 2，创造模式物品栏也可直接取用）。
  旧写法 `/give @p csgobox:<自建名字>` 对自建箱子不再有效；5 个默认 id 仍可直接 `/give`。
- **联机**：客户端与服务端的 `config/csbox/` **不再需要保持一致**（多客户端以服务端下发为准）。
  这正是 2.1.0 修复的事故：以前每个箱子各占一个物品 id，两侧配置不同（或 `requires` 门控的
  可选模组只装在一侧）就会让物品注册表分叉，连接被以「Failed to synchronize registry data
  from server / 模组版本不匹配」拒绝——而两侧版本号显示完全相同，极易误判。联机只需保证
  **模组版本一致**。
- **升级提示**：用户自建箱子的旧物品 id 在升级后失效，世界中已有的旧物品需用 `/csbox give`
  重新发放；随模组发布的默认箱子物品不受影响（无 `box_id` 标签时以物品注册 id 兜底为箱子 id）。
