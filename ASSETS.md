# 菜单美术资产记录

## 主题身份与来源边界

用户在本次任务中确认：SAO 是半城ccc（现 ID `TheBcc_`）的传奇末影人皮肤，半城ccc 现属 TEAMY／天门。该关系用于游戏主题；最终个人档案展示的是打开菜单者自己的 ID、头像与人物，头部跟随鼠标。公开网页只作名称与文化语境参考，不用于证明“国服第一”等称号对应官方排名。

TEAMY 成员标签根据用户指定的九个用户名匹配，作者文字仅对 `Freezea` 显示；其他用户名显示“玩家”。这些文字仅作界面展示，不用于账号认证或权限判断。

## 用户提供的皮肤

下表所有文件位于 `src/main/resources/assets/megawalls/textures/gui/cinematic/`，来源均为工作区 `skin/` 下同名文件。对应成员打开菜单时用于自己的三维预览和头像；账号 ID 保持当前玩家实际大小写。

| 文件 | 匹配用户名 | 模型 | SHA-256 |
| --- | --- | --- | --- |
| `xty.png` | `TheXty_` | SLIM / 细臂 | `a8f905d25bcd365b52c3ec620e02735fa704c1c95d1c69fa2b198a331cca0240` |
| `bcc.png` | `TheBcc_` | SLIM / 细臂 | `bcd31f540e45b9443f03fb2d36fa80f352f02775e500b4438a407d8caf1b5b76` |
| `wsd.png` | `TheWsd_` | SLIM / 细臂 | `8150c22c5f325509ebea1f314e33220db422b535cac06e9316e0ce87906e7ca9` |
| `zzl.png` | `TheZzl_` | SLIM / 细臂 | `6635794a111fe73610a8dde91905cb37ac44d6a9691803cd1861d4269b124deb` |
| `swk.png` | `TheSwk_` | SLIM / 细臂 | `255a8f6c4843cdc119bdc70c33ff75d54cca404c39ed429678d76cd933f1c4f2` |
| `lgg.png` | `TheLgg_` | SLIM / 细臂 | `4564b3eb0961ac84a1823c6f0c44009c65d70a78581772a49b9607d98099fdc9` |
| `gou.png` | `TheGou_` | SLIM / 细臂 | `ca3f10b8b2005aaa7f0c5c56f712f399e838c0c70333f6326edbb3fb0d6bff79` |
| `yuu.png` | `TheYuu_` | WIDE / 宽臂 | `3486e6a5731b469253aa48ef80f999a02d76dc30763d23e5002e0c90264d9a0f` |
| `sxl.png` | `TheSxl_` | SLIM / 细臂 | `11f73d86b93d4079d4d1d787c1a7690a481b7ebed4f03b6c4426d79db7cca2bb` |

九份文件均与用户提供的原文件逐字节一致，未用生成模型重画人物皮肤。头像使用原皮肤正脸及外层 UV。名单匹配不区分大小写；不在名单的玩家使用游戏账号皮肤及其自带模型信息，未获取到时回退 Minecraft 默认皮肤。用户提供文件不等于已证明可向公众重分发或商业使用；本次不虚构作者授权，也不把原皮肤宣称为本项目原创。

菜单中的职业图标使用 `textures/gui/cinematic/classes/*.png` 下的 13 张对应普通玩家皮肤，头像裁切包含正脸与外层；详细映射和原始纹理来源见 [ASSETS-class-heads.md](ASSETS-class-heads.md)。基础版本技能 HUD 使用的 `textures/gui/skills/*.png` 及普通／传奇衣装 `textures/skin/enderman.png`、`textures/skin/hoops.png` 保留，其权利声明和使用范围不因本次 UI 修改而改变。门形徽记由 `EpicTheme.emblem` 用方块几何绘制，是本项目设计的装饰图案，不是声称得到授权的 TEAMY 官方标志。

## 公开参考

- [夏天y本人发布《XTeamY团建啦！》](https://www.bilibili.com/video/BV1V6421Z7ba/)：用于确认公开使用的 XTeamY 名称与团队语境。
- [半城ccc本人视频页面](https://www.bilibili.com/list/35321910?bvid=BV1EhJn63EuF&oid=116742093276209)：署名和简介包含 XTeamY，用作人物名称与团队语境参考。
- [《超级战墙 SAO 1v40》转载页面](https://www.bilibili.com/video/BV1KHSzBLE35/)：仅作旧超级战墙／SAO 社区语境参考；转载描述与视频标题不作为官方成绩证明。

未将上述网页的封面、视频帧、声音、真人肖像或标志下载进发布包；这些链接不构成素材使用授权。

## battlefield.png

- 集成路径：`src/main/resources/assets/megawalls/textures/gui/cinematic/battlefield.png`
- Minecraft 资源标识：`megawalls:textures/gui/cinematic/battlefield.png`
- 制作方式：内置 `image_gen` 工具按本项目提示词全新生成；未使用第三方图像输入，未调用付费 CLI/API fallback。
- 生成日期：2026-09-20。
- 实际尺寸：**1672 × 941 px**，RGB PNG，约 16:9（工具返回的原始尺寸；保留原图，没有拉伸或重采样）。
- 文件大小：1,882,166 bytes。
- SHA-256：`414ed28f645e5371c7de1c5e04863d7caa596fa89c1aa0f564fbf7694370a230`。
- 原始生成文件：`LOCAL_WORKSPACE`。
- 用途：超级战墙菜单背景。左侧深色低对比便于文字阅读，右侧为末影紫、暗金点缀的方块堡垒及战墙废墟。没有人物、按钮、文字或团队标志；人物与 UI 由游戏代码另行绘制。
- 目视检查：生成图已经查看，符合右侧视觉重心、左侧留暗、Minecraft 方块建筑、紫黑史诗战场方向。远方有少量浮空块体与火光，建筑旗帜上出现抽象金色几何装饰，未使用已知官方团队标志。
- 来源说明：本图是本项目生成的概念美术，不宣称为 Mojang、Hypixel、TEAMY 或任何人物的官方授权美术。未抓取网络视频封面、真人肖像或团队官方图标。

### 最终生成提示词

```text
Use case: stylized-concept.
Asset type: production background texture for a Minecraft Mega Walls in-game cinematic menu, landscape 16:9, ideally 2048 x 1152 pixels.
Primary request: an original epic battlefield environment constructed entirely from clearly recognizable Minecraft-style cubic blocks and pixel-textured voxel architecture, with no people or creatures. A distant, damaged fortress and broken defensive ramparts occupy the RIGHT half of the image. Scattered cuboid rubble and a few restrained ember flecks float through the battlefield. Dark obsidian stone, ender violet atmospheric light, restrained aged-gold highlights. This is the quiet moment before a legendary battle, solemn, premium, monumental.
Composition: very wide establishing view, horizon around the middle, fortress visual focus in the right third, layered geometric mountains receding into mist. The LEFT 45% is intentionally dark, low contrast and sparsely detailed to permit readable menu typography added later. Low foreground steps and broken block terrain on the right. Keep the center-left open. No hero silhouette because a Minecraft character will be rendered separately by the game.
Lighting: deep night and twilight, delicate volumetric violet haze behind fortress, restrained warm ember glow from a few cracks; rich dark values without crushed-black image. Avoid bright central flares.
Style: carefully art-directed high-quality voxel game environment render with crisp cuboid construction and pixel textures, cinematic depth and atmospheric scale. Minecraft-related, not photoreal medieval masonry.
Constraints: environment background ONLY, edge-to-edge image, no letters, no words, no numbers, no logos, no watermarks, no interface, no buttons, no borders, no people, no humanoid statues, no faces, no weapons hovering in center, no modern buildings. Preserve 16:9 horizontal composition.
```
