# Release Process

> 适用于 1.0.7+ 的多平台发布流程。版本矩阵见 `gradle.properties` 的 `active_versions`。

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
# 9 个平台逐个编译（Linux/macOS 环境变量改为 -Pactive_versions=）
for v in 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2; do
  case $v in
    1.21.*) ./gradlew :v1_21_${v#1.21.}:jar -Pactive_versions=$v ;;
    26.1.2) ./gradlew :v26_1_2:jar -Pactive_versions=$v ;;
    26.2)   ./gradlew :v26_2:jar -Pactive_versions=$v ;;
  esac
done
```

产物命名：`<module>/build/libs/csgobox-<mc>-<mod_version>.jar`（如 `csgobox-26.1.2-1.0.7.jar`）。

## 3. 质量门（发布前必须全绿）

1. **9 平台 clean 编译**：`./gradlew :<module>:clean compileJava -Pactive_versions=<v>`（防止增量缓存假象——曾有模块因 build 产物残留而"假通过"）
2. **common 单元测试**：`./gradlew :common:test`
3. **运行时回归**（至少 26.1.2 + 1.21.1 两个代表平台）：
   - 开箱动画 + 3D 拖拽旋转（PIP）
   - 批量开箱（Shift+右键 → 确认屏 → 流水结果屏）
   - 磨损耐久：单开有耐久物品按磨损值扣耐久（查看界面 wear 显示=实际扣损率，无耐久物品仍为随机磨损率）；批量开箱同样扣损；`damageItemByWear=false` 时关闭
   - 成就触发（`csgobox:opened_boxes` 累计）
   - `/csbox reload`、`/csbox tutorial refresh`、`/csbox errors`
   - 动态 box item（`/give @p csgobox:<filename>` 图标非紫黑）
   - GUI 渲染验证走自动化工作流：`docs/RUNTIME-UI-TESTING.md`（CGEvent 驱动 + 帧缓冲像素断言）

## 4. 发布产物

- CI：`.github/workflows/build.yml` 的 matrix 自动产出 9 个平台 jar（artifacts）
- 手动：见上文构建矩阵脚本
- 可选项：`./gradlew :<module>:minifyJar -Pactive_versions=<v>` 产出 ProGuard 混淆版（`-minified.jar`），`proguard-rules.pro` 需与新增反射面同步

## 5. 发布后收尾

- 更新 `docs/` 下相关文档（`ARCHITECTURE.md` / `CONFIGURATION.md` 若涉及变更）
- 教程文档源推送到 Gitee 公开仓库（`gitee.com/hou-xiangling/CS2-Box/docs/tutorials/`）——仅维护者需要，运行时下载走 HTTPS 公开访问
- 打 tag：`git tag v<mod_version> && git push origin v<mod_version>`
