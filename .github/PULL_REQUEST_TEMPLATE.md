## 改动说明

## 影响平台

- [ ] common（共享层）
- [ ] v1_21_1
- [ ] v26_1_2
- [ ] v26_2
- [ ] 仅文档 / 脚本 / CI（无代码改动）

## 作者自查（对照 docs/CODE-REVIEW.md §6.1）

- [ ] `common/` 无 `net.minecraft.*` / `net.neoforged.*` 引用（CONSTRAINT-001，§4.1）
- [ ] 跨平台改动已在所有受影响平台同步：纯新增走 `scripts/mirror.sh`，适配差异定点合入，**未整文件覆盖 v26_2**（§4.2）
- [ ] 动过版本号则四处同步（gradle.properties / mods.toml `${mod_version}` / CHANGELOG.md / README.md）（§4.3）
- [ ] 动过 `AnimRenderOps` 则三平台签名一致、era 头正确（§4.4）
- [ ] 配置项三平台 + common 同步，`CONFIG` 无 `!= null` 守卫（§4.5）
- [ ] TACZ 相关代码包在 `ModList.isLoaded("tacz")` 判断内（§4.6）
- [ ] 服务端权威 + 复核客户端数值，无越界/竞态风险（§4.7）
- [ ] 渲染状态无泄漏，26.2 HUD 走 `HudVisibility`（§4.8）
- [ ] 相关文档（CHANGELOG / CONFIGURATION / README / docs/*）已更新

## 测试说明

- [ ] CI（build matrix + common-test）全绿
- [ ] 本地验证命令：`__请填写__`
- [ ] 手动/运行时回归：`__请填写__`

## 相关链接
