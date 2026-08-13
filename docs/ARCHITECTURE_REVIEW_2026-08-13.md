# Fiber 当前审查与续查

> 审查日期：2026-08-13
> 说明：这是对 2026-08-09 审查的续查，不是旧快照复写
> 验证：`.\gradlew.bat testDebugUnitTest` 通过

## 结论

当前没有看到明确的 Activity/View/Context 堆内存泄漏。08-09 报告里最重的几项已经明显缓解，但还有两类问题要继续盯：

1. 文件索引和真实 SAF 文件之间仍有少量不同步路径。
2. 附件清理已经比之前好很多，但“进程异常退出/未走到显式丢弃”时，仍可能留下孤儿附件。
3. 单个超大 Markdown 文件在打开、保存或切换预览时，仍可能带来明显的瞬时内存峰值。

## 已修复或显著缓解

### 1. 大库同步的峰值内存风险已经下降

现在 `FileIndexer.syncLibrary()` 不再把整库正文一起拉进比较阶段，而是改成：

- `MarkdownFileDao.getIndexSnapshotsByLibrary()` 只取轻量快照
- `plan.entriesToUpsert.chunked(50)` 分批读取和写入
- `shouldGuardMassDeletion()` 继续拦截全空目录误删

相关位置：
- `app/src/main/java/com/bird/fiber/data/local/FileIndexer.kt:51-101`
- `app/src/main/java/com/bird/fiber/data/local/library/MarkdownFileDao.kt:172-179`

这意味着 08-09 报告里那种“同步时持有全库正文 + 全部待更新正文”的高危 OOM 形态，已经被拆掉了。

### 2. 新建文件的目标库归属已修正

`FileRepositoryImpl.createMarkdownFile()` 现在用显式传入的 `target.libraryId` 写索引，不再回头读活动库。

位置：
- `app/src/main/java/com/bird/fiber/data/local/FileRepositoryImpl.kt:62-108`

这条是已处理的旧问题。

### 3. 快速记录和编辑器的附件回滚已经补上

`AttachmentRepositoryImpl` 现在有 `delete()`，`EditorViewModel.discardChanges()` 和 `QuickNoteViewModel.discardDraft()` 都会清理 `pendingAttachmentUris` 对应的图片。

位置：
- `app/src/main/java/com/bird/fiber/data/local/AttachmentRepositoryImpl.kt:42-109`
- `app/src/main/java/com/bird/fiber/ui/screens/editor/EditorViewModel.kt:227-243`
- `app/src/main/java/com/bird/fiber/ui/screens/quicknote/QuickNoteViewModel.kt:171-184`

这比 08-09 时“只复制、不回收”已经好很多。

### 4. 预览缓存和渲染调度都收口了

- `PreviewCache` 只有 100 项上限。
- 编辑器预览在切换内容时会 `unschedule()` 旧 drawable。

位置：
- `app/src/main/java/com/bird/fiber/data/local/PreviewCache.kt:14-49`
- `app/src/main/java/com/bird/fiber/ui/screens/editor/EditorPreviewPane.kt:92-125`

## 仍然存在的问题

### 1. 删除和重命名文件仍可能把索引写歪

`FileRepositoryImpl.deleteFile()` 直接调用 `DocumentsContract.deleteDocument()`，但没有检查返回值；然后无论底层文件到底删没删，都会删索引。

`renameFile()` 则是在重建索引时读取“当前活动库”，不是原文件所属库。用户如果在跨库操作后重命名，可能把索引挂错库。

位置：
- `app/src/main/java/com/bird/fiber/data/local/FileRepositoryImpl.kt:142-166`

影响：
- app 内列表会和真实本地文件状态不一致
- 跨库时可能出现“文件还在原库，索引跑到当前库”的错配

这不是典型堆泄漏，但确实会影响用户本地文件库的可见性和一致性。

### 2. 孤儿附件仍可能在异常退出时留下

现在显式丢弃和保存成功后的回滚链路已经有了，但 `copyImage()` 仍是“先复制到 `attachments/`，再靠 ViewModel 记住这次会话是否需要删”的模型。

如果应用进程被杀、用户强退、系统回收，或者某些路径没走到 `discardChanges()` / `discardDraft()`，这些图片仍会留在库里。

位置：
- `app/src/main/java/com/bird/fiber/data/local/AttachmentRepositoryImpl.kt:42-109`
- `app/src/main/java/com/bird/fiber/ui/screens/editor/EditorViewModel.kt:144-174`
- `app/src/main/java/com/bird/fiber/ui/screens/quicknote/QuickNoteViewModel.kt:97-135`

这属于确定性的磁盘资源累积，不是内存泄漏，但时间久了会污染用户本地库。

### 3. 附件管理页加载引用关系时仍有大内存峰值

`loadReferencesForLibrary()` 会先扫描整库 Markdown，再把每个文件全文读进内存，最后再按附件逐个比对。

位置：
- `app/src/main/java/com/bird/fiber/data/local/AttachmentRepositoryImpl.kt:184-216`

对大库来说，这一段会比列表页更容易出现 CPU 和内存峰值。它不是“长期不释放”，但打开附件管理页时可能很重。

### 4. 保存时仍有一处额外字节数组复制

`FileIndexer.updateFileAfterSave()` 里有 `content.toByteArray().size` 的兜底分支。

位置：
- `app/src/main/java/com/bird/fiber/data/local/FileIndexer.kt:171-191`

当前保存流程已经会传实际大小，所以这条不是主风险，但如果未来别的调用方没传 `size`，这里会再多拷一次正文。

### 5. 单文件超大正文仍是实际峰值点

`FileRepositoryImpl.readFileContent()` 会把整份 Markdown 读成一个 `String`，`EditorViewModel.loadFile()` 再把它放进 `TextFieldValue`，预览模式还会把同一份内容再送进 Markdown 渲染链。

位置：
- `app/src/main/java/com/bird/fiber/data/local/FileRepositoryImpl.kt:43-60`
- `app/src/main/java/com/bird/fiber/ui/screens/editor/EditorViewModel.kt:93-114`
- `app/src/main/java/com/bird/fiber/ui/screens/editor/EditorViewModel.kt:258-282`

这不是长期泄漏，但对特别大的单文件来说，打开、保存、预览切换都可能出现可见的内存尖峰，极端时会触发卡顿甚至崩溃。

## 优化建议

1. 先修 `deleteFile()` 和 `renameFile()` 的索引一致性，优先级最高。
2. 把附件改成 staged / commit / rollback 模型，别只靠 ViewModel 内存状态回收。
3. 把 `loadReferencesForLibrary()` 改成按文件流式比对，或者至少分批读 Markdown。
4. 给超大单文件加保护策略，比如首屏分段读取、预览延迟渲染或内容上限提示。
5. 把 `updateFileAfterSave()` 的兜底大小计算改掉，避免未来再引入一次大正文复制。

## 说明

这份续查只覆盖源码和现有单测验证，不等于设备级内存分析。当前 `testDebugUnitTest` 已通过，但我没有在这次续查里重新跑 lint 或做 heap dump。
