# CS2-Box 宝箱配置编辑器（Box Editor）

一个**纯前端、零依赖、可离线打开**的可视化配置工具，帮助普通玩家/服主在不手写 JSON
的情况下创建 CS2-Box 的宝箱 / 终端机配置与统一价格表。

- 目录：`box-editor/`
- 数据同步：`scripts/sync-box-editor-data.py`（从 `docs/box-schema` 与 `docs/examples`
  生成 `js/data.js`，保证编辑器校验规则与游戏文档一致）
- 跟随游戏版本（1.21.1 / 26.1.2 / 26.2 / Forge 26.1.2 / Forge 26.2 / Forge 1.20.1），
  按版本控制 TACZ 变体键与 Data Components / legacy NBT 的可用性
- 界面中英双语（右上角切换，自动记忆）

## 快速开始（本地）

三种方式任选其一：

1. **双击打开**：直接用浏览器打开 `box-editor/index.html`（`file://` 可用，无需 Node）
2. **一键启动**（推荐，Node ≥ 18）：
   - Windows：双击 `box-editor/start.bat`（自动打开 `http://127.0.0.1:4173/`）
   - macOS / Linux / Git Bash：`./start.sh`
   - 或手写：`cd box-editor && npm start`（等价 `node server.mjs`，`--port` / `--no-open` 可调）
3. **访问 Dist 构建**：`npm run build` 后 `node server.mjs --dir ./dist`

不需要安装任何依赖、不需要网络。首次打开会自动载入
「武器供应商终端」示例，所有状态自动保存在浏览器 localStorage。

## 构建与本地部署

```bash
cd box-editor

npm run sync    # 【可选】从 docs/box-schema + docs/examples 重新生成 js/data.js（需 Python 3.9+）
npm run build   # 零依赖复制静态资源到 box-editor/dist/
npm start       # 本地部署：http://127.0.0.1:4173/
```

- `npm run sync` 等价于
  `python ../scripts/sync-box-editor-data.py`（Windows 上若 `python` 是
  WindowsApps 占位符，改用仓库推荐的
  `C:/Users/FrostStarInquire/.venv-html-to-docx/Scripts/python.exe` 直接运行该脚本）
- `dist/` 是可独立托管的产物目录（`index.html + css + js + data + README`），
  可以整目录丢到任意静态托管 / CDN。

## 功能

### 可视化表单

- **基本信息**：文件名（即箱子 id，自动校验字符集）、显示名称（支持 `#RRGGBB`
  颜色前缀）、类型（`csbox` / `terminal`）、钥匙、掉落概率、图标、启用开关、所需模组
- **掉落与等级权重**：实体掉落列表（ID / 概率交替）、grade1–grade5 档内权重
- **终端机参数**：折扣、库存、补货、每人上限、冷却、权限（仅 `terminal` 显示）
- **五档奖池**：每件物品支持 物品ID / 物品标签(#) / 战利品表 三选一来源，
  数量（固定 / 区间）、档内权重、附魔（随机 / 指定 / 指定+等级区间）、
  TACZ 变体（写入 `tag: {GunId:"..."}`）、原始 NBT tag、Data Components（JSON）；
  TACZ 变体 / 原始 NBT 属于**专属字段**，由「TACZ 专属字段」开关控制：自动时
  跟随所选版本，也可点击循环「自动 → 启用 → 禁用」手动覆盖（开关状态会本地保存，
  导入 / 载入示例时保留）
- **价格表**：自动收集物品池里的所有物品键（含 `id#变体`），未定价提示将使用档位
  默认价；实时显示拆解回收（表价 ×90% 向上取整）；可添加额外键、**批量导入**
  （粘贴 `键<Tab>价格` 多行或整份 `_prices.json`）
- **撤销 / 重做**：顶部按钮或 `Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y`（输入框内保留
  原生文本撤销）；误操作随时回退
- **物品复制 / 批量粘贴**：每条物品可复制其 JSON，奖池内支持粘贴多条（JSON
  对象/数组，或每行一个物品 ID）
- **分享链接**：一键把当前配置压缩进 URL（`#state=...`），发给别人打开即还原
- **折叠记忆**：五个奖池卡片的展开/折叠状态自动保存
- **功能区折叠**：各卡片标题可点击折叠/展开（掉落实体权重、终端机参数默认折叠，
  其余默认展开），状态本地保存；卡头内操作按钮（如批量导入）不受折叠影响

### 悬浮帮助

- 把鼠标移到任意输入框 / 下拉框上，会浮出对应的填写教程（中英文跟随界面语言；
  键盘 Tab 聚焦同样显示），不用翻文档即可知道每个字段的含义与格式。

### 双页工作区

- 编辑器拆成两个独立页面（顶部导航切换，状态本地共享）：
  - **箱子配置**（`index.html`）：基本信息 / 奖池 / 掉落实体 / 终端机参数
  - **价格表**（`prices.html`）：`_prices.json` 编辑，自动列出箱子页物品键
- 两页共用一份本地状态：任一侧的修改都会同步到另一页（含跨标签页实时同步）。

### 实时预览与校验

- 右侧实时生成当前页面对应的 `箱子.json` 或 `_prices.json` 内容
- 内嵌轻量校验（对应运行时规则：文件名字符集、终端禁止 key、random 5 项、
  item 三选一来源、count/weight/enchant 合法性、价格表键格式与整数范围、
  版本差异提示等）
- 校验结果分 错误 / 警告 / 提示 三级展示；**点击任意一条校验项会滚动定位并
  高亮对应的输入框**

### 导入 / 迁移 / 导出

- **导入**：选择本地 `.json` 文件，或粘贴 JSON 文本（自动识别箱子 vs 价格表）
- **旧版自动迁移**：导入带内联 `price` 的旧版箱子 JSON 时，自动把价格迁移进价格表
  ——同一物品多处价格按**平均值（四舍五入）**迁移，表中已有价格优先，非法价格
  忽略，并自动从箱子 JSON 中剥离 `price` 字段（与游戏内 `LegacyPriceMigration`
  规则一致）
- **内置示例**：武器供应商终端、附魔书终端、生存补给终端、TACZ 军火枪械箱
- **下载**：单独下载当前文件，或一键下载 `xxx.json` + `_prices.json`

### 版本切换说明

JSON 结构在六个平台间当前完全一致；版本切换主要影响：

| 版本 | Data Components | TACZ `id#变体` |
|---|---|---|
| 1.21.1 (NeoForge) | ✅ | ✅ |
| 26.1.2 / 26.2 / Forge 26.x | ✅ | ❌（`#变体` 键会回退纯 id 价并提示） |
| Forge 1.20.1 | ❌（用 legacy NBT `tag`） | ✅ |

> 上表是「自动」模式的行为。表单里的 **TACZ 专属字段** 开关可强制启用 / 禁用
> 变体输入与 `id#变体` 键提示：强制启用后即使选了 26.x 也能配置 TACZ 变体并生成
> `#变体` 价格键（是否被服务端接受取决于对应整合包）；强制禁用后即使选 1.21.1
> 也会隐藏这些字段。

未来某版本 schema 分叉时，在 `scripts/sync-box-editor-data.py` 的版本元数据中挂载
对应 schema 文件即可（当前共用 `docs/box-schema/` 的共享 schema）。

## 部署到 GitHub Pages

本目录是纯静态站点，**推荐用仓库内的 Actions 工作流自动发布**（推送到 `main`
且改动涉及 `box-editor/` / `docs/box-schema/` / `docs/examples/` 时自动构建上传）：

1. 推送代码到 CS2-Box 仓库 `main` 分支
2. 打开仓库 **Settings → Pages**，在 **Build and deployment → Source** 选择
   **`GitHub Actions`**（不是 Deploy from a branch）
3. 等待 `.github/workflows/box-editor-pages.yml` 跑完，访问
   `https://<用户名>.github.io/CS2-Box/`（工作流默认发布到站点根；如需子路径
   发布，在仓库设置里把 Pages **Custom domain / path** 配成 `/box-editor`）

> 说明：浏览器在 `file://` 下无法 fetch 外部 JSON Schema 文件，因此本工具把
> `docs/box-schema/*.schema.json` 与内置示例**内嵌**进 `js/data.js`（生成式），
> 离线也能完整校验；原始 schema 副本保留在 `box-editor/data/schemas/shared/`
> 供排查与未来版本化。

也可以完全手动部署（不依赖 Actions）：

- 本地 `npm run build` 后，把 `box-editor/dist/` 推到任意静态托管；
- 或整目录发布 `box-editor/`：Settings → Pages → **Deploy from a branch** →
  `main` + 文件夹 `/box-editor`。

## 数据同步

修改了 `docs/box-schema/` 或想更新内置示例后，重新生成内嵌数据：

```bash
# Windows（仓库推荐 Python 3.12 环境）
C:/Users/FrostStarInquire/.venv-html-to-docx/Scripts/python.exe scripts/sync-box-editor-data.py

# 或任意 Python 3.9+
python scripts/sync-box-editor-data.py
```

脚本会重写：

- `box-editor/js/data.js`（schema + 版本元数据 + 示例）
- `box-editor/data/schemas/shared/*.schema.json`（原始副本）

## 目录结构

```
box-editor/
├── index.html            # 单页入口
├── package.json          # npm scripts（start / build / sync）
├── server.mjs            # 零依赖本地静态服务器（Node ≥ 18）
├── build.mjs             # 静态构建 → dist/
├── start.bat             # Windows 一键启动（双击）
├── start.sh              # macOS / Linux / Git Bash 一键启动
├── css/style.css         # 深色 UI
├── js/
│   ├── i18n.js           # 中/英字典
│   ├── data.js           # 【生成】schema + 版本 + 示例
│   ├── model.js          # 数据模型 / 序列化 / 旧版 price 迁移
│   ├── validator.js      # 轻量校验（跟随运行时规则）
│   └── app.js            # UI 渲染 / 事件 / 预览 / 导入导出
├── data/schemas/shared/  # 【生成】原始 schema 副本
├── dist/                 # 【构建产物】npm run build 生成，可独立托管
└── README.md
```

## 设计取舍（v0 初始版）

- 纯原生 JavaScript（无框架、无构建），保证 `file://` 与 GitHub Pages 零配置可用；
  后续滚动开发如引入构建（Vite + Vue），仍可发布到 Pages，但会失去“双击即用”。
- 编辑器校验是**轻量校验**（覆盖运行时主要规则），最终以游戏内 `/csbox validate`
  与 Java 校验器为准。
- `tag_nbt` 是 schema 残留文档字段，运行时并不读取——编辑器按 loader 实际行为
  生成 `tag`（SNBT/JSON 对象），TACZ 变体也从 `tag` 提取。