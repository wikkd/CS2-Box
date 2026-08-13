# CS2 Box — MC百科 模组介绍页

## 模组名称

主要名称：CS2 Box
次要名称：（不填）

## 作者

Reclizer（Original）、ChloePrime（This version）

## 模组元素

核心元素：魔改
杂项：道具、指令、生存

## 模组关系

前置：NeoForge

## 简介

CS2 Box 是 CsgoBox 在 NeoForge 平台的移植增强版。

原版作者 Reclizer 将 CS2 风格的开箱机制带入 Minecraft。模组支持 MC 1.21.1 / 26.1.2 / 26.2 三平台（另有 MinecraftForge 26.1.2 实验版本），通过 config/csbox/*.json 文件自由配置箱子内容，无需重新编译。

功能：

- 5 档稀有度分级：consumer、industrial、mil_spec、restricted、classified，每级独立权重与边框色
- 4 种钥匙：铁、金、钻石、下界合金（下界合金仅通过锻造台升级获得）
- 箱子配置通过 config/csbox/*.json 管理，支持 data component 与 NBT 标签
- 生物掉落：可配置全局掉落率与实体独立掉落率
- 成就系统："全新的开始"（首次开箱）与隐藏挑战"导购"（累计开箱 200 次）
- /csbox 命令：info、reload、reload tutorial、nbt hand
- 服务端授权 RNG：中奖结果由服务端计算，客户端仅渲染动画
- 可视化开箱 GUI：物品拖拽预览、滚轮动画、稀有度对应边框色
- 批量开箱：Shift+右键进入批量开箱总览屏，流水式展示结果
- 终端机：五轮报价谈判式抽卡（服务端权威会话，随机磨损与武库点数惩罚）

前置：NeoForge 21.1.248+（1.21.1）/ 26.1.2.95（26.1.2）/ 26.2.0.59（26.2）

## 下载

- GitHub：https://github.com/wikkd/CS2-Box
- MIT License，继承原版 CsgoBox 协议
