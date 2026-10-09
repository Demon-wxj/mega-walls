# 构建

先安装 **JDK 21 和 Gradle 8.12**。项目使用 Fabric Loom 1.9 系列；仓库没有 Gradle Wrapper，首次构建需要联网获取 Maven 依赖。

```powershell
gradle --version
gradle clean build --console=plain
```

构建产物在 `build/libs/`。安装生产 JAR，不要把 `-sources.jar` 或开发 JAR 放入游戏。

以下依赖需自行从合法渠道取得，按列出的文件名放入 `deps/`。仓库不附这些 JAR：

- `deps/aaa_particles-fabric-1.21.1-2.3.0.jar`（modImplementation）
- `deps/tabletop-0.4.2+mc1.21.1.jar`（modCompileOnly）

源码引用 Tabletop 0.4.2 的 API。Tabletop 仓库的源码版本是 0.5.8，尚未验证能否替代 0.4.2；缺少旧版依赖时可能无法构建。AAA Particles 2.3.0 也需单独取得。Effekseer 特效源文件、生成器和导出工具见 [tools/effekseer](tools/effekseer/README.md)，第三方编辑器不包含在仓库中。

未包含无公开授权的美术，代码仍可能引用原资源路径，导致缺失纹理或模型。需要补入有权使用的资源，或自行替换。
