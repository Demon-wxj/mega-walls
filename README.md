# Mega Walls

一个超级战墙与小游戏分支，包含职业技能、菜单、特效和场地互动。玩法参考 Hypixel Mega Walls，非官方项目。

Minecraft **1.21.1 / Fabric / Java 21**。源码版本 `1.18.38-tnt-floor-mc1.21.1`，Mod ID：`megawalls`。

按 **M** 打开战墙菜单，按 **V** 释放技能；`/games` 打开小游戏入口。客户端和服务器都要安装，并准备对应的地图和数据包。

**不要同时安装另一个使用 `megawalls` Mod ID 的版本。**运行需要 AAA Particles 2.3.0；编译还需要 Tabletop 0.4.2，尚未验证能否用 Tabletop 仓库中的 0.5.8 替代。

仓库未包含无公开授权的皮肤、头像和图标。渲染代码仍可能引用这些路径，会出现缺失纹理或模型；完整显示需补入有权使用的资源，或自行替换。Effekseer 特效源文件和工具在 [tools/effekseer](tools/effekseer/README.md)。

[构建](BUILDING.md) · [安装](INSTALLATION.md) · [来源与许可](THIRD_PARTY_NOTICES.md)

代码采用 GPL-3.0-only；第三方部分按各自许可保留署名。整理时未重新编译或进游戏测试。

## 贡献者

Demon-wxj · GPT（OpenAI）。分工见 [CONTRIBUTORS.md](CONTRIBUTORS.md)。
