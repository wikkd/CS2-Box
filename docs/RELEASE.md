# Release Process

> 适用于 1.0.6+ 的多平台发布流程。版本矩阵见 `gradle.properties` 的 `active_versions`。

## 1. 版本号同步（四处在升级时必须一致）

| 文件 | 位置 |
|---|---|
| `gradle.properties` | `mod_version=` |
| 各平台 `src/main/resources/META-INF/neoforge.mods.toml` | `version="${mod_version}"`（模板变量，无需手改） |
| `CHANGELOG.md` | 新版本条目 |
| `README.md` | 版本提及 |

`neoforge.mods.toml` 通过模板变量 `${mod_version}` 从 Gradle 注入，**不要**手动改版本字符串。

## 2. 构建矩阵

NeoGradle userdev 无法在同一次 Gradle 调用中并行加载多个 MC 版本（IDEA 扩展冲突，历史限制），因此**每次 Gradle 调用只构建一个版本**：

```bash
# 3 个平台逐个编译（Linux/macOS 环境变量改为 -Pactive_versions=）
for v in 1.21.1 26.1.2 26.2; do
  case $v in
    1.21.1) ./gradlew :v1_21_1:jar -Pactive_versions=$v ;;
    26.1.2) ./gradlew :v26_1_2:jar -Pactive_versions=$v ;;
    26.2)   ./gradlew :v26_2:jar -Pactive_versions=$v ;;
  esac
done
```

> 已归档（EOL）平台 v1_21_0 / v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10 / v1_21_11 自 2026-08-09 起不再构建发布，最后状态见 tag `eol-legacy-21x-1.0.6`。

> `forge_26_1_2`（MinecraftForge，实验模块）与 `v26_1_2` 保持特性同步（同一
> `mod_version`，同步纪律见 AGENTS.md「forge_26_1_2 同步」），**不进入本构建矩阵**
> 与 CI；其发布门禁独立运行 `scripts/test-forge-2612.sh`（L0-L3）+ L4 运行时 E2E，
> 流程见 `docs/TESTING-FORGE-2612.md` §6。

产物命名：`<module>/build/libs/csgobox-<mc>-<mod_version>.jar`（如 `csgobox-26.1.2-1.0.6.jar`）。

## 3. 质量门（发布前必须全绿）

1. **3 平台 clean 编译**：`./gradlew :<module>:clean compileJava -Pactive_versions=<v>`（防止增量缓存假象——曾有模块因 build 产物残留而"假通过"）
2. **common 单元测试**：`./gradlew :common:test`
3. **运行时回归**（至少 26.1.2 + 1.21.1 两个代表平台）：
   - 开箱动画 + 3D 拖拽旋转（PIP）
   - 批量开箱（Shift+右键 → 确认屏 → 流水结果屏；**1.0.6 已屏蔽，1.0.7 恢复后回归**）
   - 磨损耐久：单开有耐久物品按磨损值扣耐久（查看界面 wear 显示=实际扣损率，无耐久物品仍为随机磨损率）；`damageItemByWear=false` 时关闭
   - 成就触发（`csgobox:opened_boxes` 累计）
   - `/csbox reload`、`/csbox tutorial refresh`、`/csbox errors`
   - 动态 box item（`/give @p csgobox:<filename>` 图标非紫黑）
   - GUI 渲染验证走自动化工作流：`docs/RUNTIME-UI-TESTING.md`（CGEvent 驱动 + 帧缓冲像素断言）
   - 终端机屏幕（`terminal` 物品右键打开，三平台）：
     - 四区静态布局对齐原型（左聊天气泡 / 右下报价卡 / 左下操作条 / 底行三格）；点阵为 512px tile 平铺，无白色块状失真
     - 时间轴：倒计时 DD:HH:MM:SS 每秒递减（初始 2天23:57:45）、打字点循环、武器 2.5s 轮换、8-F 磨损条箭头 0.95s 滑入 + 扫描带
     - 交互：长按「接受」胶囊（700ms）成交 → 第 2 轮报价；长按「拒绝」→ 第 3 轮；第 5 轮拒绝出红色失败横幅；批量上限下拉（30/64/200/400/800/无上限）；「检视」胶囊切换 3D 自转拖拽预览；ESC / ✕ 关闭恢复 HUD
     - 语言：中/英 locale 下对话、皮肤名、磨损档位、计数文案均正确翻译
   - 终端机关闭后 HUD 恢复（1.21.1 无 tint 泄漏：关闭后屏幕无异常着色）

## 4. 发布产物

- CI：`.github/workflows/build.yml` 的 matrix 自动产出 3 个平台 jar（artifacts）
- 手动：见上文构建矩阵脚本
- 可选项：`./gradlew :<module>:minifyJar -Pactive_versions=<v>` 产出 ProGuard 混淆版（`-minified.jar`），`proguard-rules.pro` 需与新增反射面同步

## 5. 发布后收尾

- 更新 `docs/` 下相关文档（`ARCHITECTURE.md` / `CONFIGURATION.md` 若涉及变更）
- 教程文档源推送到 Gitee 公开仓库（`gitee.com/hou-xiangling/CS2-Box/docs/tutorials/`）——仅维护者需要，运行时下载走 HTTPS 公开访问
- 打 tag：`git tag v<mod_version> && git push origin v<mod_version>`
