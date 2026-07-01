<!-- generated-by: gsd-doc-writer -->
# 为 CS2-Box 贡献代码

感谢你考虑贡献 CS2-Box。本文档覆盖本地开发环境、代码规范、分支约定、PR 流程与问题报告。

## 开发环境配置

### 快速配置

1. 安装 **Java 21**(v1_21_1 工作)+ **Java 25**(v26_1_2,需要 `--enable-preview`)
2. 克隆仓库:`git clone https://github.com/wikkd/CS2-Box.git && cd CS2-Box`
3. 配置活动版本(默认 `26.1.2`):编辑 `gradle.properties` 中的 `active_versions`
4. 验证构建:`./gradlew :v26_1_2:build` 或 `./gradlew :v1_21_1:build`
5. 启动开发客户端:`./gradlew :v26_1_2:runClient`(或 `:v1_21_1:runClient`)

### 项目结构

仓库是 multiloader Gradle 多模块结构:

```
CS2-Box/
├── common/                          # 跨版本业务逻辑 + 共享资源 + platform 接口
│   └── src/main/
│       ├── java/com/reclizer/csgobox/
│       │   ├── platform/             # Platform 接口(由 v1_21_1 / v26_1_2 实现)
│       │   ├── box/                  # BoxDefinition / BoxRegistry / BoxJsonLoader
│       │   ├── packet/               # 数据包 Codec / StreamCodec
│       │   └── ...                   # 其他业务代码
│       └── resources/                # 共享资源(纹理、音效、配方、advancement)
├── v1_21_1/                         # MC 1.21.1 / NeoForge 21.1.115 / Java 21
│   └── src/main/java/com/reclizer/csgobox/v1_21_1/
│       ├── CsgoBox.java              # 模组入口
│       ├── advancement/              # 自定义进度触发器
│       ├── capability/               # NeoForge 玩家数据附件
│       ├── command/                  # 服务端控制台命令
│       ├── config/                   # TOML 配置处理
│       ├── event/                    # 事件订阅(客户端/服务端)
│       ├── gui/                      # 客户端 UI 界面
│       ├── item/                     # 物品定义和注册
│       ├── packet/                   # 网络协议接线
│       ├── sounds/                   # 音效事件定义
│       └── utils/                    # 共享工具类
├── v26_1_2/                         # MC 26.1.2 / NeoForge 26.1.2.76 / Java 25
│   └── src/main/java/com/reclizer/csgobox/v26_1_2/
│       ├── (同 v1_21_1 结构)
│       ├── gui/pip/                  # 独有:Icon3DRenderer + Icon3DRenderState (PIP 3D 管线)
│       ├── platform/                 # 独有:Platform26 (注入 Platform 接口)
│       └── utils/                    # 独有:ButtonPalette + RenderFontTool
├── settings.gradle                  # 模块注册 + active_versions 切换
├── gradle.properties                # mod_version / pack_format / active_versions
└── docs/                            # 项目文档
```

## 代码规范

- **语言**:Java 21 / 25(由 toolchain 强制)
- **构建系统**:Gradle + NeoForged userdev 插件
- **代码风格**:
  - 遵循标准 Java 命名规范
  - 4 空格缩进,LF 行尾
  - 最大行长度:120 字符
  - 公共 API 添加 Javadoc 注释
  - 使用 `Record` 类处理不可变数据结构(参见 `BoxDefinition.java`)
  - 使用 NeoForge 的 `Codec` 和 `StreamCodec` 进行数据序列化
  - **不使用 Cloth Config**(已移除),仅用 `ModConfigSpec`
- **构建命令**:
  - `./gradlew build` — 完整构建
  - `./gradlew :v26_1_2:compileJava` — 快速编译检查
  - `./gradlew :v26_1_2:runClient` — 启动 v26_1_2 客户端
  - `./gradlew :v1_21_1:runClient` — 启动 v1_21_1 客户端
  - `./gradlew :v26_1_2:runServer` — 启动 v26_1_2 服务端
  - `./gradlew gameTestServer` — 运行 GameTest 集成测试
- **依赖方向**:`common/` **不得**直接 `import net.minecraft.*` 或 `import net.neoforged.*`(版本敏感代码留在平台模块)

## 分支约定

- `main` — 稳定发布分支(1.21.1 主分支)
- `multiloader-refactor` — v26.1.2 迁移分支(当前活跃)
- 功能分支命名:`feat/描述`、`fix/描述`、`docs/描述`、`refactor/描述`
- 所有 PR 应指向 `main`(或活跃开发分支)

## PR 指南

1. **Fork 并创建分支**:从目标分支(如 `main`)创建描述性名称(如 `feat/add-new-box-type`)
2. **保持更改专注**:每个 PR 一个功能或修复
3. **测试更改**:
   - 在修改的模块下运行 `./gradlew :<module>:build` 确保编译成功
   - 用 `./gradlew :<module>:runClient` 进行手动测试
   - 跨平台验证(如改动影响 common)
4. **更新文档**:如更改影响行为:
   - 更新对应 `docs/*.md` 或根目录 `README.md`
   - 更新 JSON schema 文档
5. **提交信息格式**(推荐 Conventional Commits):
   - `feat: add new box type`
   - `fix: resolve key validation issue`
   - `docs: update configuration guide`
   - `refactor: extract ButtonPalette from CsboxScreen`
6. **提交 PR**:附上清晰的更改描述、测试说明、影响的 MC 版本

## 问题报告

在 [GitHub Issues 页面](https://github.com/wikkd/CS2-Box/issues) 报告问题。

报告 bug 时请包含:

- Minecraft 版本(1.21.1 或 26.1.2)
- NeoForge 版本(21.1.115 或 26.1.2.76)
- 模组版本(`1.0.5` 或 `1.0.5-26.1.2`)
- 重现步骤
- 预期与实际行为
- 相关日志文件(来自 `.minecraft/logs/latest.log` 或 `runs/client/logs/latest.log`)

功能请求请描述:

- 使用场景
- 如何让用户受益
- 任何现有的替代方案

## 游戏测试框架

CS2-Box 使用 NeoForge 内置的 GameTest 框架。详见 [docs/TESTING.md](./docs/TESTING.md)。

测试应放在:

```
common/src/test/java/      (跨版本测试)
v1_21_1/src/test/java/     (1.21.1 平台特化)
v26_1_2/src/test/java/     (26.1.2 平台特化)
```

运行游戏测试:

```bash
./gradlew gameTestServer
```

## 配置系统

配置文件为 TOML + JSON 双轨:

- `config/csgobox.toml` — 主模组配置(`ModConfigSpec` 持久化)
- `config/csbox/*.json` — 箱子数据定义

添加新配置选项时:

1. 在 `common/.../config/CsboxConfig.java` 与 `v1_21_1/.../config/CsboxConfig.java` 与 `v26_1_2/.../config/CsboxConfig.java` 三处同步添加字段
2. 通过 `builder.define*().get()` 内联求值(扁平化访问,不用 `init()` 延迟填充)
3. 在 [docs/CONFIGURATION.md](./docs/CONFIGURATION.md) 中记录
4. `CONFIG` 是 `public static final` —— **不要写 `CONFIG != null` 守卫**

## 宝箱定义 JSON 格式

宝箱定义使用 Mojang 1.21+ DataComponent 系统。修改 `BoxDefinition.java` 时:

1. 用新字段更新 `BoxDefinition` Record
2. 更新 JSON 序列化的 `CODEC`
3. 更新网络传输的 `StreamCodec`
4. 在 GUI 中添加对应字段(如适用)

详细 schema 见 [docs/CONFIGURATION.md §3](./docs/CONFIGURATION.md)。

## Multiloader 开发注意事项

- 修改 `common/` 后需在两个平台都重新构建验证
- GUI 代码改动先在 v1_21_1 落地(legacy `GuiGraphics`),再迁移到 v26_1_2(`GuiGraphicsExtractor` decoupled API)
- v26_1_2 渲染管线变更:用 `nextStratum()` 分层、用 `RenderPipelines.GUI_TEXTURED` 替换静态 `RenderSystem` 调用、用 `Lighting` instance API

## 许可证

为 CS2-Box 贡献代码即表示您同意您的贡献将采用 MIT 许可证。参见 [LICENSE](./LICENSE) 文件了解详情。