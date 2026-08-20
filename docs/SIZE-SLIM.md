# CS2-Box 包体积瘦身探索报告

> 探索先行：所有改动**不触碰默认构建产物、CI 与 `dist/`**。资源层已在工作树生效；
> `-Pslim` 与 `minifyJar` 均为可选开关/任务，默认构建行为不变。
> 版本号未动（1.0.6）。实测日期：2026-08-13/14（最终四平台重建）。

## 结论摘要

三个可叠加杠杆（资源、LVT 剥离、ProGuard 瘦身）+ 死代码清理，以 26.2 为基准：

| 形态 | 磁盘 | 压缩内容(deflate) |
|---|---|---|
| 默认 jar（当前工作树） | 577.3 KB | 517.7 KB |
| `-Pslim`（去 LVT） | 531.2 KB | 471.6 KB |
| `-Pslim` + `minifyJar` | **488.7 KB** | **441.9 KB** |

- 叠加收益 **-88.6 KB（-15.4%）**；四平台 -15.4% ~ -16.4%（见下表）。
- 达到的幅度低于最初估算（-22~25%）：估算中的 ProGuard -66.9 KB 来自一次
  **错误规则下的破坏性运行**（9 个功能类被误删，见「ProGuard 规则修正」）；
  修正后诚实收益约 -42~-45 KB/平台（minify 段）。
- 类清单审计：四平台 minified jar **0 个非预期删除**（硬门禁通过）。
- **L4 全量回归需要完整 MC 客户端（runClient + 人工交互），本环境无法执行**；
  已完成的自动化验证与 L4 交接清单见文末。

## 基线说明（重要）

旧 `dist/` 26.2 = 600.3 KB（141 个类）；当前工作树代码已新增 KubeJS 事件三件套
与 terminal 功能（152 个类）。**旧 dist 与当前代码不可直接对比**——所有对比均以
当前工作树重新构建为准。资源层对照实验（`git stash` 旧资源后构建）测得资源层
jar 内净收益约 -68 KB/平台。

## 分层结果（四平台，磁盘字节，2026-08-14 重建）

| 平台 | 默认 jar | `-Pslim` | `-Pslim`+minify | 叠加收益 |
|---|---|---|---|---|
| v1_21_1 | 590,912 | 542,497 | 494,163 | **-96,749 (-16.4%)** |
| v26_1_2 | 578,160 | 531,908 | 489,417 | -88,743 (-15.4%) |
| v26_2 | 577,328 | 531,205 | 488,684 | -88,644 (-15.4%) |
| forge_26_1_2 | 568,100 | 523,610 | 478,690 | -89,410 (-15.7%) |

单杠杆收益（26.2 实测，2026-08-14 javap 复核）：LVT 剥离 -46,123 B（-8.0%）；
ProGuard（slim 输入）**-42,521 B（-8.0%）**；两者叠加接近线性（-88,644 B）。

---

## 杠杆 1：资源层（已入库，默认构建即生效）

### 1a. PNG 无损重编码 — `scripts/slim-optimize-png.py`

Pillow `optimize=True` 对全部 PNG 无损重编码；幂等（重复运行输出不变），
`--force` 语义对齐 `gen-terminal-assets.py`。21 个小 PNG 合计约 -2.5 KB。

### 1b. PNG 有损量化（默认参数，验收已过）— `scripts/slim-lossy.py`

pngquant 3.0.3 量化（保留 alpha，P 模式）。第一批 3 张大图：

| 文件 | 前 | 后 | 收益 |
|---|---|---|---|
| `textures/screens/lens_vignette.png` | 30,727 B | 8,626 B | -22,101 B |
| `textures/item/csgo_box.png` | 29,184 B | 13,260 B | -15,924 B |

第二批 6 张 UI 小图（q85-100，视觉模型逐张验收「无可见差异」后入库，
对比图 `shots/_slim_ui_sprites_grid.png`）：

| 文件 | 前 | 后 | 收益 |
|---|---|---|---|
| `screens/spot_glow.png` | 5,844 B | 4,197 B | -1,647 B |
| `gui/terminal/terminal_avatar.png` | 3,087 B | 2,457 B | -630 B |
| `gui/terminal/terminal_circle_glow.png` | 2,779 B | 2,071 B | -708 B |
| `gui/terminal/terminal_dot_tile.png` | 2,157 B | 193 B | -1,964 B |
| `gui/terminal/terminal_badge.png` | 2,164 B | 2,014 B | -150 B |
| `screens/gold_item.png` | 1,545 B | 1,283 B | -262 B |

`terminal_avatar_wm.png` / `terminal_circle.png` 量化后反而变大，**跳过不量化**。

**含损决策记录**：
- 皮肤初测 q70-95 有可见缺陷（`csgo_box` 左下条纹压碎），
  **升到 q85-100 修复并通过**（视觉模型 + 人眼复核）。q70 版本不保留。
- vignette 原计划 512→256 降采样；实测 pngquant 量化在**保留 512×512 分辨率**
  的情况下已达成体积目标（30.7→8.6 KB），**放弃降采样**（无分辨率损失，更优）。
- 原件备份：`/tmp/csbox-slim-orig/`（git HEAD 亦有原件）。
- 对比图：`shots/_slim_vignette_before_after.png`、
  `shots/_slim_csgo_box_q85_before_after.png`、
  `shots/_slim_ui_sprites_grid.png`。

### 1c. 音效重编码 — `scripts/slim-encode-sounds.sh`

ffmpeg 解码 + oggenc（vorbis-tools）→ **mono 22050 Hz VBR q4**：

| 文件 | 前 | 后 |
|---|---|---|
| `cs_dita.ogg` | 9,331 B | 6,521 B |
| `cs_open.ogg` | 16,909 B | 10,037 B |
| `cs_finish.ogg` | 28,334 B | 15,541 B |

合计 -22,475 B（-22.0 KB）。原件备份 `/tmp/csbox-slim-sounds-orig/` 供听感对比。

### 1d. 模型 JSON 精度修剪 — `scripts/slim-trim-model-precision.py`

`terminal.json` 浮点从 17 位舍入到 4 位小数：508,648 → 492,412 B（-15.9 KB 解压）。
脚本内置断言（元素数 1021 不变、最小厚度不缩水）。模型 JSON 压缩率高，
**jar 内实际收益仅约 -0.8 KB**（压缩后）。曾试 3/2 位小数，压缩后零收益，**停在 4 位**。

### 1e. 死资源与死代码清理（有证据才删）

- **3 张 weapon PNG**（`terminal/weapon_pistol.png` 1,293 B / `weapon_rifle.png`
  1,546 B / `weapon_smg.png` 1,496 B）：`NegotiationModel.SKIN_WP` 常量定义后
  全仓零引用，图片与常量一并删除。
- **`SKIN_C1` / `SKIN_C2`**（NegotiationModel 两个未使用皮肤常量）：零引用，删除。
- **`keySample`**（三平台 `CsboxBulkOverviewScreen` 未使用字段）：零引用，删除。
- **`serializeColoredName`**（v26_1_2/v26_2 `BoxJsonLoader` 私有方法）：
  仅定义无调用，删除。**forge / v1_21_1 有实际调用，未动**（镜像纪律逐平台核实）。

**资源层合计：原始文件 -87.2 KB（1b）+ -5.4 KB（1e PNG）+ 音效/JSON 等；
jar 内约 -72 KB/平台。**

---

## 杠杆 2：`-Pslim`（LVT 剥离，开关，默认关闭）

各平台 `compileJava` 增加：

```groovy
if (project.hasProperty('slim')) {
    options.debugOptions.debugLevel = 'lines,source'
}
```

- 剥离 LocalVariableTable，**保留行号与源文件**（堆栈可读性影响最小）。
- 收益 -44.5 ~ -48.4 KB/平台（-8.0%）。
- 复现：`./gradlew :<module>:jar -Pactive_versions=<v> -Pslim`
- 复核（2026-08-14）：默认构建 javap 确认含 `LocalVariableTable`，`-Pslim`
  构建为 0 处——开关真实生效，此前「开关为 no-op」的中间结论系误判
  （当时 javap 的样本 jar 已被 slim 构建覆盖），以本文为准。

---

## 杠杆 3：ProGuard `minifyJar`（任务，默认不跑）

### 任务形态

四平台均已移植 `minifyJar`（v1_21_1 原有，本次补 v26_1_2/v26_2/forge_26_1_2）：

- ProGuard **7.5.0 → 7.9.1**（`com.guardsquare:proguard-base` Maven 最新版；
  实测可处理 26.x 的 class major 69 preview 字节码；不存在 8.x）。
- `-libraryjars` 追加 `java.base.jmod` + `java.net.http.jmod`（缺 jmod 时报
  `can't find superclass java.lang.Object`）。
- shrink-only：`-dontoptimize -dontobfuscate -dontpreverify`；
  `-keepattributes SourceFile/LineNumberTable/*Annotation*/Signature/InnerClasses
  /EnclosingMethod`（行号与注解保留，minified 栈可读）。

### 规则修正（9 个功能类从误删中救回）

首个 minify 版本只用了 `-keepclassmembers @EventBusSubscriber`，注解扫描注册的
类本身被整体删除（ModEvents / ClientModEvents / ClickEvent / CsboxCommand /
LoadErrorAnnouncer / ArmoryRecyclerScreen / EntityChineseMap / NetworkLimits /
CsboxConfigDefaults 中运行时必需的 9 个类）。已升级为：

```proguard
-keep @net.neoforged.fml.common.EventBusSubscriber class * {
    <init>();
    public *;
}
```

并补 forge 侧 `@net.minecraftforge.fml.common.Mod$EventBusSubscriber` 等价规则、
JEI（`mezz.jei.**`）、TACZ（`com.tacz.guns.**`）与 java.base jmod 显式白名单；
`-ignorewarnings` 已移除，缺失类现在**显式失败**而不是静默吞掉。

### 类清单审计（硬门禁）— `scripts/slim-audit-classes.py`

```
python3 scripts/slim-audit-classes.py ORIG.jar MINIFIED.jar \
    --allow com/reclizer/csgobox/box/NetworkLimits.class \
    --allow com/reclizer/csgobox/config/CsboxConfigDefaults.class \
    [--allow ...]
```

四平台结果（2026-08-14 重建）：`removed=2/2/2/4, added=0, unexpected=0,
GATE PASSED`。

**Allowlist 及理由**（全部核验为编译期常量/死代码，运行时零引用）：

| 类 | 平台 | 理由 |
|---|---|---|
| `box/NetworkLimits` | 全部 | 纯 `static final int` 常量，javac 已内联 |
| `config/CsboxConfigDefaults` | 全部 | 纯常量类（默认值/取值范围），javac 已内联 |
| `box/BoxOdds` | 仅 forge | 仅被 JEI 类别引用（显示概率用）；forge 无 JEI 集成 → 死代码 |
| `villager/ModVillagers` | 仅 forge | forge 模块零引用（见文末「发现」） |

minified jar 资源完整性已抽查：`mods.toml`/`pack.mcmeta`/`assets/`/`data/` 全保留。

### 实测否决项：ProGuard `-optimize`

试点 `-Poptimize`（5 passes）：内联膨胀，产物比 shrink-only **大 23 KB**
（516 KB vs 492.7 KB deflate），已完全回退到 `-dontoptimize`，**不再重试**。
`-obfuscate` 同样不开（mod 生态需要稳定类名/混淆会破坏反射面）。

### 实测否决项：deflate level 9

Gradle 9.5.1 `Zip` 任务只暴露 `entryCompression`（DEFLATED/STORED），无压缩级别
API；用 Python zipfile `compresslevel=9` 后处理实测与 level 6 **零收益**
（类文件已高度可压缩，且 ProGuard 自身打包比 Python 重打包还小 1.7 KB）。不做。

---

## 杠杆 4：代码层（死代码已删；注释/import 不动）

**实际执行**（2026-08-13/14，全部有引用证据，见杠杆 1e）：

- 死代码删除：`keySample`、`serializeColoredName`（仅 26.x）、`SKIN_WP/C1/C2`
  + 3 张 weapon PNG；逐平台核实引用后才删，forge/legacy 有差异处保留。
- import 清理：全仓 43 处未使用 import（IDE 扫描），编译期零体积收益，
  纯可读性。
- 注释精简：6 大文件 × 4 平台（`BoxDefaults` / `TerminalSessionManager` /
  `BoxJsonLoader` / `PacketCsgoProgress` / `TerminalChatRegion` /
  `AnimRenderOps` / `CsgoBox` / `CsboxProgressScreen`），全仓 Java 净 -1,200 行，
  **保留技术要点与版权语义，删除 AI 味措辞**（`deliberately` / `Note:` /
  `We deliberately` / `This commit` 全仓清零）。
  工具：`scripts/slim-prop-comments.py`（注释 hunk 同步镜像）+ 
  `scripts/slim_comments_lib.py`（marker 块替换）。

**固定结论**（评估后不做）：

- **注释/import 对 jar 体积零影响**：common 源码注释占 22.6%、平台约 14%，
  但全部在源码层；`.class` 里没有注释，import 已被 javac 消解。精简仅为
  可读性，**不作为体积杠杆**。
- **GUI 屏幕类约 78 KB（压缩后）是产品本体**，不是可瘦身冗余。
- top-25 类占代码约 51%——头部集中，但均为功能实现。
- `EntityChineseMap` 数据外移 JSON：净省约 2 KB，需同步改 3 平台 JEI
  适配面 → **放弃**（收益/改动面不划算）。
- packet 样板合并：14 个 packet 类各有业务逻辑（服务端权威 RNG、批量计算池、
  terminal 协商状态等），合并会破坏可读性与边界 → **放弃**。

---

## 复现命令

```bash
# 资源层（已入库；幂等）
python3 scripts/slim-optimize-png.py                 # 无损 PNG
python3 scripts/slim-lossy.py                        # 有损 PNG（9 文件，q85-100）
bash scripts/slim-encode-sounds.sh                   # 音效重编码（需 ffmpeg + oggenc）
python3 scripts/slim-trim-model-precision.py         # terminal.json 精度

# 每 Gradle 调用只构建一个 MC 版本
./gradlew :<module>:jar -Pactive_versions=<v>        # 默认构建（新资源已生效）
./gradlew :<module>:jar -Pactive_versions=<v> -Pslim # 叠加 LVT 剥离
./gradlew :<module>:minifyJar -Pactive_versions=<v> [-Pslim]  # 叠加 ProGuard
# <module>: v1_21_1 / v26_1_2 / v26_2 / forge_26_1_2
# <v>: 1.21.1 / 26.1.2 / 26.2 / forge-26.1.2

# 审计门禁（--allow 白名单见上表；脚本支持一次 --allow 传多个值）
python3 scripts/slim-audit-classes.py \
  <module>/build/libs/csgobox-<mc>-<ver>.jar \
  <module>/build/libs/csgobox-<mc>-<ver>-minified.jar \
  --allow com/reclizer/csgobox/box/NetworkLimits.class \
  --allow com/reclizer/csgobox/config/CsboxConfigDefaults.class
```

## 自动化验证（本环境已完成，2026-08-14 最终重建）

- 四平台 `jar`（默认）→ `jar -Pslim` → `minifyJar -Pslim` 全绿
  （无 Warning，仅无害 Note）。
- 四平台类清单审计 **GATE PASSED**（removed 2/2/2/4，unexpected=0）。
- `:common:test`（含 BoxJsonSchemaValidator 24 用例）通过。
- `:v26_1_2:test` / `:forge_26_1_2:test`（PlatformSmokeTest）/ `:v1_21_1:test`
  （BoxItemCodecTest）通过。
- 资源有效性：PNG 全部可解码（新量化 6 张均为 P 模式、尺寸不变）；
  3 个 ogg 均为 Vorbis 22050 Hz mono；`terminal.json` JSON 合法、元素数 1021 不变。
- 视觉验收：6 张 UI 量化图经视觉模型（qwen3.8-max）逐张比对原始图，
  **全部「无可见差异」**；对比图在 `shots/_slim_ui_sprites_grid.png`。
- 工具修正：`slim-audit-classes.py` 的 `--allow` 曾只消费一个值导致后续
  allow 项被当成位置参数，已修复为一次可传多个值（本报告命令已更新）。

## L4 全量回归交接（需人工执行）

`minifyJar` 产物没有自动化运行时测试覆盖（PlatformSmokeTest 只验证编译产物
类存在性，不加载 MC 运行时）。**请按 `docs/RELEASE.md` §3 质量门对
`csgobox-<mc>-<ver>-minified.jar` 逐平台跑 runClient L4 清单**：
开箱动画 + 3D 拖拽、批量开箱、磨损耐久、成就、`/csbox` 三命令、动态 item、
终端机全交互矩阵、HUD 恢复，并抽查音效/皮肤/vignette/UI 纹理观感。

## 发现（非本次任务引入，供维护者跟进）

- **forge `ModVillagers` 零引用**：`forge_26_1_2` 的 `CsgoBox` 注册了
  ModSounds/ModItems/ModMenus，但**没有调用 `ModVillagers.register(...)`**
  （v26_1_2 在 CsgoBox:167 调用）。forge 构建里 POI/职业不会注册，
  armory_recycler 村民相关功能疑似同步缺口。ProGuard 将其判为死代码删除，
  与 forge 当前实际行为一致。建议在 forge 同步任务中补上注册调用。
