# 1.18.45 周年庆维护补丁

这个目录保存从 1.18.41 周年庆基线生成 `1.18.45-bed-items-mc1.21.1` 的增量源码。仓库根目录的 Gradle 工程继续提供独立的 1.18.41 源码构建。维护流程需要有权使用的周年庆发布包作为输入，并沿用该包中的资源许可。

维护补丁包括对局配置与药水堆叠、客户端特效预算、竞技场规则与小地图刷新，以及起床战争的床边道具和救援方块。`src/main` 保存新增代码和两个新增配置资源。`baseline-1.18.41.patch` 保存既有类的增量，构建时用 CFR 0.152 在临时目录重建参考文本。`tools` 中的两个 Java 工具负责向现有类应用字节码补丁。

## 构建

准备 Python 3.11 或更新版本、Git、JDK 21 和 CFR 0.152。基线应为实际安装的 `megawalls-mod-1.18.41-bteamy-story-mc1.21.1.jar`，SHA-256 是 `15eceba2c7476e54372846fbe80ce621fe22e20db5ca7215b7225ce5b36dbaf9`。

另需 Fabric Loom 生成的 Minecraft 1.21.1 named 和 intermediary JAR、Yarn `1.21.1+build.3` 的 Tiny 映射、Fabric API 0.116.17+1.21.1 和 Minecraft 库目录。重映射依赖为 Tiny Remapper 0.10.4、Mapping IO 0.6.1，以及 ASM 9.7.1 的 `asm`、`asm-commons`、`asm-tree`、`asm-util` 和 `asm-analysis`。

运行 `python maintenance/1.18.45/build_overlay.py --help` 查看参数。每个重映射依赖传一个 `--remapper-jar`，每个库目录传一个 `--library-dir`。使用 `--output-dir` 可指定产物目录，默认是 `maintenance/1.18.45/output`。脚本会检查基线 SHA-256，构建失败时保留已有 JAR。

对比维护产物时，请按 JAR 内部路径核对条目内容。ZIP 时间戳和目录项顺序会改变外层 JAR 的 SHA-256。
