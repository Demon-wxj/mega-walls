# 原创末影裂缝 Effekseer 源工程

本目录将历史开发目录中明确原创的生成器、可编辑源工程和导出脚本归回对应战墙仓库，没有把 repair 目录另建一个 Mod 仓库。作者脚本声明不复制 Effekseer 的 stock/sample 效果或素材。原创工具与源工程适用 GPL-3.0-only。

`authored/*.efkproj` 为可编辑工程；数学星芒/细丝图片为原创生成材质。源目录中的第三方 AAA JAR、Effekseer 工具 ZIP、研究用第三方 C# 文件和下载缓存均未复制。

需要 Python 3 + Pillow 与另行获取的 [官方 Effekseer 1.80.7](https://github.com/effekseer/Effekseer/releases/tag/1807)。将工具放在 `effekseer-1.80.7/Effekseer1.80.7Win/Tool/bin/Effekseer.exe`。在本目录执行：

```powershell
./export_ender_effeks.ps1
```

脚本先生成 `authored/`，再用官方 CLI 导出并回读检查。显式添加 `-InstallAssets` 才将结果复制到本仓库的 `src/main/resources/assets/megawalls/effeks/`。本次公开整理没有执行生成、导出或游戏测试。AAA 2.3.0 是独立运行依赖，源码来源 [ChloePrime/AAAParticles](https://github.com/ChloePrime/AAAParticles/tree/0af9a6817c85c090e1cc2c2c4dd5f2b430bea1e4)，不复制其样例效果。
