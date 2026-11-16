# CS2-Box 宝箱配置编辑器（Box Editor）

为 CS2-Box 模组打造的纯前端可视化配置工具：不写 JSON，在表单里点点选选，
就能创建宝箱、终端机配置和统一价格表。

- **纯前端、零依赖、零网络**——双击 `index.html` 即可使用（`file://` 可用），
  也可以用 npm 起本地服务；不需要安装任何依赖
- **跟随八平台版本**（1.21.1 NeoForge / Fabric、26.1.2、26.2、Forge 26.1.2 /
  Forge 26.2 / Forge 1.20.1 / Forge 1.18.2），按版本控制 TACZ 变体键与
  Data Components / legacy NBT 的可用性
- **中英双语**，右上角切换、自动记忆，首次访问跟随浏览器语言
- **实时预览与校验**：JSON 输出、概率 / 期望值计算、开箱模拟、规则校验
  （点校验项直接跳到出问题的输入框）
- **对齐 2.0.4 玩法字段**：自定义档位数组、多钥匙、磨损区间、单开奖励、
  全服播报阈值、时间窗、开箱费用、首开保底、`/csbox upgrade`、自定义音效、
  隐藏概率行、批量开箱门禁、终端谈判参数——全部可视化编辑
- **多草稿工作区**：同时管理多份配置草稿，支持分享链接、撤销重做、
  PWA 离线安装
- **奶白毛玻璃界面**，仓库封面图作背景，浅色 / 深色主题可切换

## 快速开始

- **直接打开**：双击 `box-editor/index.html`。推荐 Chrome / Edge——Firefox 在
  `file://` 下禁用 localStorage，功能仍可用但不会自动保存。
- **本地服务**（Node ≥ 18）：Windows 双击 `start.bat`，其他平台
  `./start.sh`，或 `cd box-editor && npm start`。自动打开
  `http://127.0.0.1:4173/`，可带 `--port` / `--no-open` 参数。
- **构建产物**：`npm run build` 后 `node server.mjs --dir ./dist`，`dist/`
  可整目录托管到任意静态服务。

首次打开会自动载入「武器供应商终端」示例；所有改动自动保存在浏览器
localStorage，刷新不丢。

## 配置流程

一次完整的宝箱配置通常是这样走下来的：

**1. 选版本、建草稿**

右上角选择目标平台版本（影响 TACZ 字段与 Data Components 的可见性，见
[版本差异](#版本差异)）；左侧文件栏新建草稿。草稿名就是箱子 id，也是
导出文件名。

**2. 填基本信息**

类型选 `csbox`（宝箱）或 `terminal`（终端机）；文件名自动校验字符集；
显示名支持 `#RRGGBB` 颜色前缀。钥匙、掉落概率、启用开关、所需模组都在
这一屏。终端机多出折扣、库存、每人上限、冷却、权限几项参数。

**3. 搭奖池**

默认五个档位，每件物品三选一来源：物品 ID、物品标签（`#tag`）、战利品表。
数量可以固定也可以区间，档内权重控制稀缺度，附魔支持随机（可带等级窗口，
留空 id 即「随机可附魔项」）/ 指定 / 指定+等级区间。原始 NBT tag 和
Data Components 收在物品卡底部的「高级数据」折叠区，粘贴 `/csbox nbt hand`
的输出会自动带出。

需要更多档位时点「切换为自定义档位」：档位从低到高排列（1..64 档），
每档可配置 id（pity / 播报阈值引用它）、显示名、`#RRGGBB` 颜色、权重与
档位库存，整体替代五档 + random 权重。

**3b. 玩法扩展（v2.0.4）**

「玩法扩展」卡收纳 2.0.4 的全部可选玩法字段：保底（含首开必出，
宝箱与终端机通用）、隐藏概率行、批量开箱开关、多钥匙 any-of、磨损区间、
单开奖励（翻倍 / 再开一箱）、全服播报阈值（档位 id 或序号）、生效时间窗
（ISO-8601，必须带时区）、开箱费用（武库点数）、自定义音效、`/csbox upgrade`
升级规则；终端机额外有谈判参数（轮数 / 倒计时 / 报价偏移）。

**4. 定价**

切到价格表页，箱子里的所有物品键（含 `id#变体`）会自动列出。每格填
固定价（`1500`）或范围价（`1500-3000`，导出为 `[1500, 3000]`，游戏内
每次报价 / 拆解时随机取整）。

> **物品池 id 必须定价。** 未定价会导致游戏加载报错、终端机不售、
> 拆解产出 0。价格表实时显示拆解回收参考（固定价 = 表价 × 90% 向上取整）。
> 支持批量导入：粘贴 `键<Tab>价格` 多行，或直接贴整份 `_prices.json`。

**5. 预览与校验**

右侧栏实时生成当前页面的 JSON；「精简输出」可省略默认值让文件更短。
切到「概率表」看每档抽中概率、每件物品的档内 / 综合概率和期望价值
（与游戏端 `OddsCalculator` 同口径）。模拟开箱可以单次播放 CS2 风格
滚动动画，或批量 10 / 100 次直接出统计表。底部校验结果分错误 / 警告 /
提示三级，点击任意一条会滚动定位并高亮对应的输入框。

**6. 导出、放进游戏**

下载 `xxx.json` + `_prices.json`（或单独下当前文件），放进游戏的
`config/csbox/` 文件夹，游戏内 `/csbox reload` 生效。Chrome / Edge
用户可以直接用「从游戏读取」反向导入现有配置，也能用「应用到游戏」
把编辑器草稿直写回 `config/csbox/`（仅限这两款内核浏览器）。

### 高级模式

顶栏「高级」开关默认关闭，收纳了进阶配置：

- **自定义贴图（图标）**——整数 `CustomModelData`（全平台，需资源包），或
  `ns:path` 物品模型 id（如 `minecraft:ender_chest`，仅 26.x 直接渲染），
  附常用模型快捷选择与版本兼容性提示
- **TACZ 专属字段**——变体 GunId / AmmoId，卡内附中英双语导入教程按钮；
  开关支持「自动 → 启用 → 禁用」循环覆盖（自动时跟随所选版本）
- **物品附魔配置**——随机附魔 / 指定附魔 + 等级

普通模式下，已配置附魔 / TACZ 变体 / 原始 NBT 的物品卡头部会出现
`⚙ 附魔 / TACZ` 徽章，点一下就能开启高级模式。

### 效率功能

- **撤销 / 重做**：`Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y`，输入框内保留原生文本撤销
- **复制粘贴**：每条物品可复制 JSON；奖池内可批量粘贴（JSON 对象 / 数组，
  或每行一个物品 ID），也能直接贴 `/csbox nbt hand` 的聊天整段
- **分享链接**：一键把当前配置压缩进 URL（`#state=...`），对方打开即还原；
  覆盖本地草稿时可 `Ctrl+Z` 撤回
- **悬浮帮助**：悬停任意输入框浮出填写教程（中英文），触屏设备长按约半秒弹出
- **折叠记忆**：奖池卡片与功能区卡片的展开状态自动保存
- **主题**：顶栏 ◐ 切换浅色 / 深色，未手动切换时跟随系统

### 导入与迁移

- **导入**：选本地 `.json` 文件或粘贴 JSON 文本，自动识别箱子 vs 价格表
- **旧格式导入**：v2.0.1 前的配置专用入口。内联 `price` 自动迁移进价格表
  （同物多价取平均，已有价优先）；文件名为 `terminal.json` 的旧终端机自动
  识别并移除遗留 `key`；ChloePrime 旧版写反的 `random` 权重自动检测并反转。
  普通导入也会迁移内联 `price`，但不做后两项旧格式推断
- **内置示例**：武器供应商终端（含谈判演示）、附魔书终端、生存补给终端、
  TACZ 军火枪械箱、v2.0.4 玩法演示箱（自定义档位 + 全部玩法字段）；
  示例弹窗里可一键整包导入 `compat-packs/` 官方联动包模板
  （Apotheosis / Iron's Spells / TACZ，含 `requires`、加权、附魔与
  `id#变体` 价格示例）
- **PWA**：部署到 HTTPS 后浏览器地址栏出现「安装」入口，可装成独立应用
  离线使用；`file://` 双击打开不受影响

## 版本差异

JSON 结构在八个平台间当前完全一致；版本切换主要影响：

| 版本 | Data Components | TACZ `id#变体` | item-model 图标 |
|---|---|---|---|
| 1.21.1 (NeoForge) | ✅ | ✅ | ❌（只支持整数 CustomModelData） |
| 26.1.2 / 26.2 / Forge 26.x | ✅ | ❌（`#变体` 键会回退纯 id 价并提示） | ✅ |
| Forge 1.20.1 | ❌（用 legacy NBT `tag`） | ✅ | ❌（只支持整数 CustomModelData） |
| 1.21.1 (Fabric) | ✅ | ✅ | ❌（只支持整数 CustomModelData） |
| Forge 1.18.2 | ❌（用 legacy NBT `tag`） | ✅ | ❌（只支持整数 CustomModelData） |

> 上表是「自动」模式的行为。TACZ 专属字段开关可强制启用 / 禁用：强制启用后
> 即使选了 26.x 也能配置变体并生成 `#变体` 价格键（是否被服务端接受取决于
> 对应整合包）；强制禁用后即使选 1.21.1 也会隐藏这些字段。

未来某版本 schema 分叉时，在 `scripts/sync-box-editor-data.py` 的版本元数据中
挂载对应 schema 文件即可（当前共用 `docs/box-schema/` 的共享 schema）。

## 部署

本目录是纯静态站点，推荐用仓库内的 Actions 工作流自动发布（推送到 `main`
且改动涉及 `box-editor/` / `docs/box-schema/` / `docs/examples/` 时自动
构建上传）：

1. 推送代码到 CS2-Box 仓库 `main` 分支
2. 仓库 **Settings → Pages → Build and deployment → Source** 选
   **`GitHub Actions`**（不是 Deploy from a branch）
3. 等 `.github/workflows/box-editor-pages.yml` 跑完，访问
   `https://<用户名>.github.io/CS2-Box/`（默认发布到站点根；需要子路径时
   在 Pages 设置里配 **Custom domain / path** 为 `/box-editor`）

> 浏览器在 `file://` 下无法 fetch 外部 JSON Schema 文件，所以 schema、内置
> 示例与联动包模板都**内嵌**进 `js/data.js`（生成式），离线也能完整校验；
> 原始 schema 副本保留在 `box-editor/data/schemas/shared/`。

手动部署不依赖 Actions：本地 `npm run build` 后把 `dist/` 推到任意静态托管，
或整目录发布 `box-editor/`（Settings → Pages → **Deploy from a branch** →
`main` + `/box-editor`）。

## 数据同步

修改了 `docs/box-schema/`、内置示例或 `compat-packs/` 后，重新生成内嵌数据：

```bash
# Windows（仓库推荐 Python 3.12 环境）
C:/Users/FrostStarInquire/.venv-html-to-docx/Scripts/python.exe scripts/sync-box-editor-data.py

# 或任意 Python 3.9+
python scripts/sync-box-editor-data.py
```

脚本会重写 `box-editor/js/data.js`（schema + 版本元数据 + 示例 + 联动包 +
物品 / 实体补全表）与 `box-editor/data/schemas/shared/*.schema.json`。

## 目录结构

```
box-editor/
├── index.html            # 箱子配置页入口
├── prices.html           # 价格表页入口
├── sw.js                 # 【PWA】Service Worker（离线缓存，__VER__ 由 build 注入）
├── manifest.webmanifest  # 【PWA】安装清单
├── package.json          # npm scripts（start / build / sync / test）
├── server.mjs            # 零依赖本地静态服务器（Node ≥ 18）
├── build.mjs             # 静态构建 → dist/
├── start.bat             # Windows 一键启动（双击）
├── start.sh              # macOS / Linux / Git Bash 一键启动
├── css/style.css         # 奶白毛玻璃 + 深色主题层（html[data-theme="dark"] 切换）
├── js/
│   ├── i18n.js           # 中/英字典
│   ├── data.js           # 【生成】schema + 版本 + 示例 + 联动包 + 物品/实体补全表
│   ├── model.js          # 数据模型 / 序列化 / 概率 / 模拟器 / 旧版 price 迁移
│   ├── validator.js      # 轻量校验（跟随运行时规则）
│   └── app.js            # UI 渲染 / 文件栏 / 事件 / 预览 / 导入导出
├── img/
│   ├── bg.jpg            # 页面背景封面
│   └── icon.svg          # 【PWA】应用图标
├── tests/                # model / i18n / esc 单测 + Playwright 冒烟
├── data/schemas/shared/  # 【生成】原始 schema 副本
├── dist/                 # 【构建产物】npm run build 生成，可独立托管
└── README.md
```

## 设计取舍

- 纯原生 JavaScript（无框架、无构建），保证 `file://` 与 GitHub Pages 零配置
  可用；后续若引入构建（Vite + Vue）仍可发布到 Pages，但会失去「双击即用」。
- 编辑器校验是**轻量校验**（覆盖运行时主要规则），最终以游戏内
  `/csbox validate` 与 Java 校验器为准。
- `tag_nbt` 是 schema 残留文档字段，运行时并不读取——编辑器按 loader 实际
  行为生成 `tag`（SNBT/JSON 对象），TACZ 变体也从 `tag` 提取。
