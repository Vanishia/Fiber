## 注册为 Markdown 打开方式 → 选库保存

**完全可行，改动量中等偏小。**

关键的有利条件：

- `FileRepositoryImpl.createMarkdownFile(target, fileName, content)`(`app/src/main/java/com/bird/fiber/data/local/FileRepositoryImpl.kt:62`）已经支持带 `content` 参数直接写入，保存逻辑零新增，直接复用。
- 库列表有现成的 `LibraryRepository.getAllLibrariesList()`(`LibraryRepository.kt:164`)，弹一个选库对话框就行，项目里也有现成的弹窗组件风格可抄。

需要做的事：

- `AndroidManifest.xml` 给 `MainActivity` 加 `intent-filter`(`ACTION_VIEW` + `ACTION_SEND`,`content`/`file` scheme,`text/markdown`、`text/plain` 等 mimeType)。建议 VIEW 和 SEND 都注册，因为不同应用"用其他应用打开"走的路径不一样。
- `MainActivity` 里解析 incoming intent → 用 `contentResolver` 读内容（intent 自带临时读权限，不需要额外权限申请）→ 弹库选择对话框 → 调已有的 `createMarkdownFile` 写入。
- 处理几个边角：冷启动（`onCreate`）vs 热启动（`onNewIntent`，需要给 activity 加 `launchMode="singleTask"` 之类）、同名文件冲突、非 UTF-8 编码、大文件。

**影响面**：Manifest 一处、MainActivity 一块 intent 处理、新增一个选库对话框，数据层完全不动。估算半天到一天（含真机验证各家应用的分享行为差异）。