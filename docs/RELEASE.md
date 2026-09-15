# Release Process

> 适用于 2.0.0+ 的六平台发布流程（NeoForge 3 + Forge 3，共享同一 `mod_version`）。版本矩阵见 `gradle.properties` 的 `active_versions`。

## 1. 版本号同步（四处在升级时必须一致）

| 文件 | 位置 |
|---|---|
| `gradle.properties` | `mod_version=` |
| 各平台 `src/main/resources/META-INF/neoforge.mods.toml`（forge_26_1_2 为 `META-INF/mods.toml`） | `version="${mod_version}"`（模板变量，无需手改） |
| `CHANGELOG.md` | 新版本条目 |
| `README.md` | 版本提及 |

`neoforge.mods.toml` 通过模板变量 `${mod_version}` 从 Gradle 注入，**不要**手动改版本字符串。

## 2. 构建矩阵

NeoGradle userdev 无法在同一次 Gradle 调用中并行加载多个 MC 版本（IDEA 扩展冲突，历史限制），因此**每次 Gradle 调用只构建一个版本**：

```bash
# 6 个平台逐个打包（NeoGradle/ForgeGradle 每次只能构建一个版本）
for v in 1.21.1 26.1.2 26.2 forge-26.1.2 forge-26.2 forge-1.20.1; do
  case $v in
    1.21.1)         ./gradlew :v1_21_1:jar -Pactive_versions=$v ;;
    26.1.2)         ./gradlew :v26_1_2:jar -Pactive_versions=$v ;;
    26.2)           ./gradlew :v26_2:jar -Pactive_versions=$v ;;
    forge-26.1.2)   ./gradlew :forge_26_1_2:jar -Pactive_versions=$v ;;
    forge-26.2)     ./gradlew :forge_26_2:jar -Pactive_versions=$v ;;
    forge-1.20.1)   ./gradlew :forge_1_20_1:renameJar -Pactive_versions=$v ;;  # renameJar 依赖 jar；产物为 -srg.jar（SRG 重映射，1.20.1 生产必需）
  esac
done
```

> 已归档（EOL）平台 v1_21_0 / v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10 / v1_21_11 自 2026-08-09 起不再构建发布，最后状态见 tag `eol-legacy-21x-1.0.6`。

> Forge 三模块（`forge_26_1_2` / `forge_26_2` / `forge_1_20_1`）自 2.0.0 起纳入
> **正式发布**（与对应 NeoForge 平台保持特性同步，同步纪律见 AGENTS.md「forge_26_1_2 同步」）；
> 不在 CI 构建矩阵，发布门禁独立运行 `scripts/test-forge-2612.sh` /
> `scripts/test-forge-262.sh` / `docs/TESTING-FORGE-1201.md` 对应的门禁脚本（L0-L3）
> + L4 运行时 E2E。

产物命名：NeoForge `<module>/build/libs/csgobox-<mc>-<mod_version>.jar`、Forge `<module>/build/libs/csgobox-forge-<mc>-<mod_version>.jar`（如 `csgobox-26.1.2-2.0.0.jar` / `csgobox-forge-26.1.2-2.0.0.jar`）。**例外：`forge_1_20_1` 的发布产物是 `forge_1_20_1/build/libs/csgobox-forge-1.20.1-<mod_version>-srg.jar`**（SRG 重映射版，`renameJar` 任务产出）；`jar` 直出产物只用于 dev/内部，进生产会因 SRG 命名域不匹配而崩溃（如 2.0.0-beta 的 `NoSuchMethodError: CriteriaTriggers.register`）。

## 3. 质量门（发布前必须全绿）

1. **6 平台 clean 编译**：`./gradlew :<module>:clean compileJava -Pactive_versions=<v>`（防止增量缓存假象——曾有模块因 build 产物残留而"假通过"）
2. **common 单元测试**：`./gradlew :common:test`
3. **运行时回归**（至少 26.1.2 + 1.21.1 两个代表平台）：
   - 开箱动画 + 3D 拖拽旋转（PIP）
   - 批量开箱（Shift+右键 → 总览屏点「开启」直接开箱 → 流水结果屏；「显示全部」网格滚轮 / ↑↓ 滚动浏览，不被收集按钮遮挡）
   - 磨损耐久：单开有耐久物品按磨损值扣耐久（查看界面 wear 显示=实际扣损率，无耐久物品仍为随机磨损率）；`damageItemByWear=false` 时关闭
   - 成就触发（`csgobox:opened_boxes` 累计）
   - `/csbox reload`、`/csbox reload tutorial`、`/csbox info error`（加载错误）
   - 动态 box item（`/give @p csgobox:<filename>` 图标非紫黑）
   - GUI 渲染验证走自动化工作流：`docs/RUNTIME-UI-TESTING.md`（CGEvent 驱动 + 帧缓冲像素断言）
   - 终端机屏幕（`terminal` 物品右键打开，六平台）：
     - 四区静态布局对齐原型（左聊天气泡 / 右下报价卡 / 左下操作条 / 底行三格）；点阵为 512px tile 平铺，无白色块状失真
     - 时间轴：倒计时 DD:HH:MM:SS 每秒递减（初始 2天23:57:45）、打字点循环、武器 2.5s 轮换、8-F 磨损条箭头 0.95s 滑入 + 扫描带
     - 交互：长按「接受」胶囊（700ms）成交 → 第 2 轮报价；长按「拒绝」→ 第 3 轮；第 5 轮拒绝出红色失败横幅；批量上限下拉（30/64/200/400/800/无上限）；「检视」胶囊切换 3D 自转拖拽预览；ESC / ✕ 关闭恢复 HUD
     - 语言：中/英 locale 下对话、皮肤名、磨损档位、计数文案均正确翻译
   - 终端机关闭后 HUD 恢复（1.21.1 无 tint 泄漏：关闭后屏幕无异常着色）

## 4. 发布产物

- CI：`.github/workflows/build.yml` 的 matrix 自动产出 3 个 NeoForge 平台 jar（artifacts）；Forge 三模块为手工/门禁脚本产出
- 手动：见上文构建矩阵脚本
- 可选项：`./gradlew :<module>:minifyJar -Pactive_versions=<v>` 产出 ProGuard 混淆版（`-minified.jar`），`proguard-rules.pro` 需与新增反射面同步

## 5. 发布后收尾

- **版本号提升（`gradle.properties` 的 `mod_version`）是发布动作，仅由维护者在发布时明确执行**；未明确给出新版本号时严禁修改（教程落盘文件名与它强耦合，见 AGENTS.md「版本号变更铁律」）
- 更新 `docs/` 下相关文档（`ARCHITECTURE.md` / `CONFIGURATION.md` 若涉及变更）
- 按新版本号准备教程内容：更新内嵌源 `common/src/main/resources/assets/csgobox/tutorials/tutorial.md` 与 `tutorial_zh_cn.md`（固定文件名；首次启动随包复制到 `config/csbox/_tutorial_v<mod_version>.md` / `_zh_cn.md`，离线可用，**不再联网下载**，无缺文件落空问题）
- 同步在线版（Mod 列表地球按钮指向）：把上述内嵌内容复制为 `docs/tutorials/_tutorial_v<mod_version>.md` / `_zh_cn.md` 并推送到 Gitee 公开仓库（`gitee.com/hou-xiangling/CS2-Box/docs/tutorials/`）——仅维护者需要
- 打 tag：`git tag v<mod_version> && git push origin v<mod_version>`
