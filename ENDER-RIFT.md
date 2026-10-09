# 末影人 · 星空裂缝

本次版本：`1.18.14-ender-rift-mc1.21.1`。Minecraft Java 1.21.1、Fabric、Java 21，增加 AAA Particles 2.3.0 依赖。

## 玩家可见的效果

成功瞬移时，出发点与服务器确认的实际落点各留下一道约 4.2 格宽、2.44 格高的横向空间裂缝。依照用户提供的画面参考，轮廓呈不规则尖菱形，蓝白电弧沿边缘游走、交缠；内部紫色能量纹路连续翻涌，后层星云缓慢漂动、星光独立闪烁。出发端碎屑内吸，到达端向外散开。约 1.2 秒内张开、停留和闭合。门面、亮边和软晕由效果自身绘制，关闭光影仍有完整表现。统一一套效果，无画质档位。

技能仍立即瞬移；不增加蓄力、改动落点/视角、伤害、冷却、能量或速度/虚弱规则。失败施法不生成裂缝。门只作短暂视觉表现，不能再次穿越，也不显示另一端的实时场景。两个端点在世界中固定，不跟随目标移动；近距离第一人称淡化，避免门面挡住瞄准。

## 实现与资产

- `EndermanSkill` 在原传送成功后调用 `EnderRiftEffects`。仅向同一世界中、超级战墙模式内且距任一端 64 格内的客户端发送一个 `S2C_EnderRift` 事件。
- 客户端按事件唯一 ID 去重，保存两个不可变坐标，以本地单调客户端 tick 计时，24 tick 到期。服务器世界时间校正不会让新效果突然跳龄；换世界、断线、资源重载时清理，异步资源回调具有世界/代次/过期校验。
- `EnderRiftRenderer` 使用 Minecraft 标准 RenderLayer，不替换光影包，不读取屏幕背景；门面按距离排序并进行深度测试。星空使用 `getEntityNoOutline` 与满亮度光照，保留普通透明混合、双面和不写深度；电弧使用 `getEyes` 加法层。实测发现 Iris 将 `getEntityTranslucentEmissive` 转入 SpiderEyes 加法混合，导致白天暗色门面消失，因此承载面不能使用该层。
- `EnderRiftRenderOrder` 对 Iris 的每个裂口建立独立绘制组，固定“星空 → 流体 → 电弧 → 星屑”顺序，防止世界渲染回调被透明批次重排后底层盖住流体。通过可选反射读取已核对的 Iris 1.8.8 `Groupable` 接口，无 Iris 时沿用原版顺序，不新增 Iris 必装依赖，也不结束其他渲染器的分组。
- 紫色能量来自连续域扭动噪声，制作时以像素精度计算柔和云层及液态脉络，烘焙成 64 帧、4096×4096 图集。游戏中对相邻帧平滑混合，并用尖菱形网格裁出裂口，避免每帧在 CPU 上生成数千条纹路线段。星空漂移慢于前景能量，制造层次差；不是整张纹理匀速旋转。复用网格缓冲，64 格外与视锥外不提交绘制。
- 进入服务器时提前加载门面与电弧纹理，避免首次施法时才解码及上传图集。所有裂缝共享同一份纹理，纹理重载与释放交给 Minecraft 资源管理器。
- `EnderRiftParticles` 仅由客户端入口引用，通过 AAA Particles 的 Effekseer 播放 API 运行原创星屑/细丝资产；保留独立服类加载边界。
- 本实例实测发现 AAA 2.3.0 加载效果时将 `GL_ARRAY_BUFFER` 错用作绑定状态查询参数。`MWAAARenderStateMixin` 仅修正该次查询为 `GL_ARRAY_BUFFER_BINDING`，保留后续绑定目标，避免 `GL_INVALID_ENUM` 与错误状态恢复。此兼容 Mixin 仅在客户端注册，AAA 官方 JAR 保持原始哈希。
- 星空 PNG 由内置 imagegen 生成，最终提示词与原图记录于 `../repair-20260928-ender-rift/assets/`。没有复制第三方星空图。数学软晕和电弧光芯由 `tools/generate_rift_primitives.py` 生成，流体图集由 `tools/generate_rift_plasma.py` 生成。
- Effekseer 资源由 `../repair-20260928-ender-rift/research/generate_ender_effeks.py` 生成可读工程，再用官方 Effekseer 1.80.7 CLI 导出。源工程、导出脚本、官方链接与许可证见同目录 `README-AAA-EFFEKSEER.md`。

## 安装

所有玩家客户端以及开服方/独立服务器均更新本版 `megawalls-mod`，并安装 **`aaa_particles-fabric-1.21.1-2.3.0.jar`**。AAA 是本版清单的全环境强依赖；不能只给开端的人安装，也不能在服务端省略。对应的 Fabric API 继续保留。

AAA 2.3.0 要求 Fabric Loader 至少 0.16.9，已内嵌 Architectury 13.0.8 和 Forge Config API Port 21.1.6；当前实例使用 Loader 0.19.5。Windows 原生渲染库由 AAA 自行加载，安装者不需要 Effekseer 编辑器。

技能资源已打包进战墙 JAR，无需单独启用资源包；数据包和地图不因本次效果更新而替换。移出旧战墙 JAR后完整重启游戏与独立服务器；`/reload` 不会加载新 Mod。

Iris/Sodium/Complementary 只用于可选的光影显示。具体测试的光影文件、客户端运行日志、截图、测试范围和仍未验证项目，以本次交付目录记录为准；不把编译通过当作光影兼容通过。

## 构建与验证入口

原工具链：Java 21、Gradle 8.12、Fabric Loom 1.9。`libs/` 中保留经 SHA-256 记录的官方 AAA 2.3.0 JAR 作为本地固定依赖，不修改该 JAR。

构建：`gradle build -PriftTest remapRiftTestJar --offline`。

双客户端验证入口：`tools/launch_rift_test.py`，必须显式提供正式 JAR、测试 JAR；可选择 `--shader none` 或 `--shader complementary`。测试只加载新建的 fixture-world，正式存档仅作为数据包只读来源。`mw_rift_test` 测试包不能安装进正式整合包。

九组同时播放的场景是视觉并发模拟；不应描述为九名真人联网压测，或据此承诺某张显卡的帧率。
