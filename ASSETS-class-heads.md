# 职业普通皮肤头像来源

本轮将菜单职业图标改为对应职业的普通玩家皮肤头像。全部 13 项由工作区既有 MWE 皮肤映射的同名默认项确定，没有使用原版怪物实体贴图，没有以传奇皮肤替代默认皮肤。

## 可核对的映射

- 本地映射源：`../repair-20260907-models/reference/MWE-master/src/main/java/fr/alexdoru/mwe/api/enums/MWSkin.java`。
- 映射源文件 SHA-256：`a83acac6c828e3075733732d4b7dcb2d9fb9e9ea4bf2a109d5dca0bec22c941d`。
- [MWE 作者源文件](https://github.com/Alexdoru/MWE/blob/master/src/main/java/fr/alexdoru/mwe/api/enums/MWSkin.java)，作者 Alexdoru；采用工作区已有快照作为本次固定依据，不声称是 Hypixel 当前官方 API。
- 纹理由下表对应的 `textures.minecraft.net` 公共静态地址下载，日期 2026-09-21。下载原始 PNG 暂存于工作区 `tmp/class-head-sources/`，不放进发布 JAR。
- 普通末影人 hash 与此前基础版本采用的普通末影人皮肤相同；本轮未替换主角 BCC / XTY 皮肤。

| ID | 项目职业 | MWE 明确默认项 | 源码行 | 集成皮肤（相对本项目） | 原始纹理 |
| --- | --- | --- | --- | --- | --- |
| 1 | 僵尸 | `ZOMBIE$ZOMBIE` | 355 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/zombie.png` | [Zombie](https://textures.minecraft.net/texture/921bb6d67119cb719b25d68ee8b9f41d8711ba43cdb6f32a85ccc5da4aab8f6a) |
| 2 | 骷髅 | `SKELETON$SKELETON` | 290 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/skeleton.png` | [Skeleton](https://textures.minecraft.net/texture/9221105ca93a482e3b6118594fda063ade986faafd57fe4cfc99453656973) |
| 3 | 蜘蛛 | `SPIDER$SPIDER` | 316 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/spider.png` | [Spider](https://textures.minecraft.net/texture/87dbc2169ba4cf3d4539e81b8c7ecbe0751bccb16513befffeb09cbd81fc) |
| 4 | 鱿鱼 | `SQUID$SQUID` | 331 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/squid.png` | [Squid](https://textures.minecraft.net/texture/fbeb2056d74bc9922fcace84e594ca33f28aceb5acd04f5aa6f547b13f10472) |
| 5 | 末影人 | `ENDERMAN$ENDERMAN` | 136 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/enderman.png` | [Enderman](https://textures.minecraft.net/texture/7a59bb0a7a32965b3d90d8eafa899d1835f424509eadd4e6b709ada50b9cf) |
| 6 | 海诺布 | `HEROBRINE$HEROBRINE` | 163 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/herobrine.png` | [Herobrine](https://textures.minecraft.net/texture/98b7ca3c7d314a61abed8fc18d797fc30b6efc8445425c4e250997e52e6cb) |
| 7 | 苦力怕 | `CREEPER$CREEPER` | 101 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/creeper.png` | [Creeper](https://textures.minecraft.net/texture/d81e3d5658fabac148520f9affa66be7d355e61729ec01def86c341ec855170) |
| 8 | 恐惧魔王 | `DREADLORD$DREADLORD` | 127 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/dreadlord.png` | [Dreadlord](https://textures.minecraft.net/texture/1ec29051df136f9c5cce3cdd3338ea58af22a7afc6ba32debd8cd397c539712) |
| 9 | 铁傀儡 | `GOLEM$GOLEM` | 152 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/golem.png` | [Golem](https://textures.minecraft.net/texture/89091d79ea0f59ef7ef94d7bba6e5f17f2f7d4572c44f90f76c4819a714) |
| 10 | 烈焰人 | `BLAZE$BLAZE` | 74 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/blaze.png` | [Blaze](https://textures.minecraft.net/texture/35658689d2d61a7a75b4f5c4192e899ecac16a08ff416d995f5c10c77aa) |
| 11 | 猎人 | `HUNTER$HUNTER` | 180 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/hunter.png` | [Hunter](https://textures.minecraft.net/texture/58ee5769626afeb4f2cfd961f0f098507b04d9e7fdddbebca1f5f22573dfa9) |
| 12 | 鼹鼠 | `MOLEMAN$MOLEMAN` | 195 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/mole.png` | [Moleman](https://textures.minecraft.net/texture/d110608cd0934bebfa488aebb8a9f74997412342937f3cab5247c35a575d6) |
| 13 | 狼人 | `WEREWOLF$WEREWOLF` | 342 | `src/main/resources/assets/megawalls/textures/gui/cinematic/classes/werewolf.png` | [Werewolf](https://textures.minecraft.net/texture/8c4328f30644e97dc3119a6cae4a51825293ef5972fa4619c72b87ec345f5) |

## 图像处理与检查

- 原始文件全部为 64×32 RGBA 玩家皮肤；输出全部为 64×64 RGBA。
- 仅按旧玩家皮肤 UV 规则复制并镜像右侧肢体到新版左肢体位置，不重绘、不调色、不生成新头像。顶部 64×32 区域逐像素与原始文件一致。
- 菜单正脸裁切使用 `(8, 8, 8, 8)`，帽子／头部外层使用 `(40, 8, 8, 8)`；两者纹理总尺寸均为 64×64，按像素最近邻采样显示。
- 核查了 13 份文件的 PNG 可解码性、尺寸、顶部区域像素一致性与输出散列；已目视检查来源玩家皮肤，未用生物实体 UV 代替。
- 下载及文件检查不等于游戏内验收；界面渲染与各分辨率验证记录在 `VERIFICATION.md`。

## 文件校验

| 文件 | 原始 PNG SHA-256 | 集成 64×64 PNG SHA-256 |
| --- | --- | --- |
| `zombie.png` | `bdc7b23a706945a6b397b558bbd1a3be31dd2a65f3068705c9d1921a2e05098c` | `dbff9d032bf972c29fea66864522436e985590527d510725bdbdf10511e1a165` |
| `skeleton.png` | `aff3b60a57a4ab7fb0fdac09e1d69e4bbb0838fef919dab014d38369b23c1ad8` | `53caf6b255816a6c0b40eaaf649e4c0a01e694716e242afa0fe298aa8cd12409` |
| `spider.png` | `c105299c6e47ee7d530bfc15d791bccb301c349487989e174fdb3e860f3ea333` | `2f62e79e00ae8ebf03558e57f3a664d0cbf05c28d28dc0f9b6496d8270597040` |
| `squid.png` | `3f001ec36ddc9f5837c0bda155b562b84a40cda355549880e987337325c4a48f` | `090dcab97470818cbe14780045733a2c3d0c9aecbfa6c633941257ebb46422fb` |
| `enderman.png` | `f2b498a58c57e13c9faa63b4d4bc349a4bb1eeb3ee3793a9eb0eb652311f3949` | `392e54c984dfacb00172dfa6c4cc9fecebb6ede6ac4a4c90ad1a20029a3a3efb` |
| `herobrine.png` | `d672913c89f9b49a769b310133f66565eea3087223df2100f3f46f23dca4fe21` | `c591cdebe8fd36c82f0ef96ce76f162478c1e580cff125ea1f245e66d14f4da4` |
| `creeper.png` | `648c62e7c310dd4dd90d8f9c28d29aa91405daa5e4349b7ad678a9b570bb9f82` | `1133f7dc65e9430a0ab10fe5f1c0643e33949eeee28ee7cfc16b89eb189ddcb8` |
| `dreadlord.png` | `5f329550fd85d375d5e039b4ed72abaff7f66b1854e56d97920b3fd4d341b5fc` | `4003ea4303d40594ce9ef055a2b9a2a797819221f30e5803345a86ccd86b11c5` |
| `golem.png` | `113286ff5e01786969b0463b2082f06cdbe7b40b0049962b7b7ebe16d01eb188` | `0747246a142c6226d5c04fc163da0dd4e27326d9e2450fce98d6abc8602eb633` |
| `blaze.png` | `83094d4eca75202ff8ba8194c614a9500278e015d9c2cf3185fac549cccfa418` | `ee9aea9958087294ea63ece9fc8ff8f66f87b90ac7ca614d987453594c75c7da` |
| `hunter.png` | `22665f3d3a1c2fdc9fd6e5d0173908861a60fec6ae79cb2b79ba4ec1c9b43405` | `be4c7f09ed7db242a2b967114fccc5050ab7fd45ac0e0f1b4a11942a83eb6964` |
| `mole.png` | `aeb7d64b73706207b8fd222052e070dbc3ac4283f189a80220d1d2892ac83141` | `2cf8b4ada348578ceb1c19acc0d8964b913779cf503fce41ccec589507ce2927` |
| `werewolf.png` | `00cddc7f5a8ebcf5d8f4e567780c9046b77074f88702b20dd3a5aa0941ff42e8` | `13d1e128586580fd6fac22335834866e8ed2c281ad5cd94ea9fc26b56f7de126` |

## 来源与使用范围

MWE 在此仅提供职业名到公开纹理地址的映射依据；没有复制其实现代码，也不以 MWE 的代码许可证代替皮肤作者的权利。本地 MWE 快照的许可证含非商业限制，可在其 `LICENSE.md` 核查。纹理公开可访问并不自动授予再分发授权；皮肤并非本项目原创，未核实每张皮肤的独立作者和公开商业分发许可。此记录支持用户当前本地整合包的可追溯使用，不宣称获得 Hypixel、Mojang 或皮肤作者的官方授权。
