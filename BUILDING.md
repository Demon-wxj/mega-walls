# 构建

使用 **JDK 21、Gradle 8.12、Fabric Loom 1.9 系列**。本仓库不附本机 JDK、Gradle 缓存或 Gradle Wrapper 二进制，先在你的环境中安装匹配工具。`settings.gradle` 使用 Fabric Maven、Maven Central 与 Gradle Plugin Portal；首次构建需要网络。

```powershell
gradle --version
gradle clean build --console=plain
```

产物应位于 `build/libs/`。安装普通重映射生产 JAR；`-sources.jar` 与开发 JAR 不装入游戏。发布副本的构建脚本保留 main/client/test 生产源码编译，将原本机 repair Maven、外部测试世界与 smoke 入口去除，不自动下载或安装游戏。

构建前需合法获得以下准确版本，放入本仓库的 `deps/`（文件名与 build.gradle 一致）；这些外部 JAR 不提交 Git：

- `deps/aaa_particles-fabric-1.21.1-2.3.0.jar`（modImplementation）
- `deps/tabletop-0.4.2+mc1.21.1.jar`（modCompileOnly）

当前源码编译引用 Tabletop 0.4.2 的 API；发布的另一 Tabletop 工程为 0.5.8。尚未验证新版本可以替代该编译依赖，缺少 0.4.2 时不能承诺本仓库可直接构建。AAA Particles 2.3.0 必须另行获取。原创 Effekseer 导出资产保留；其历史原创生成器、可编辑工程和导出工具已归集到 `tools/effekseer/`，详见该目录 README。第三方编辑器需另行获取。

公开副本排除了缺少公开授权的美术。保留的渲染代码可能仍引用原资源路径，缺少授权资源时可能显示缺失纹理或模型；不会把本副本称为完整可运行美术发行版。完整运行需要自行提供有权使用的对应资源，或后续替换为新的原创资源。

本轮只核查配置、JSON、文档链接、脚本语法、发布文件与来源快照。未运行 Gradle 编译或游戏，不能用此记录代替构建和多人验证。
