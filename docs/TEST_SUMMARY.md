# Fiber 单元测试总结

> 最近更新：2026-10-26
> 最近验证：1.2.3 发布前全量单测、Debug/Release 构建及 lint、Android 设备功能回归、正式签名覆盖升级
> 验证结果：360 项 JVM 用例通过，19 个不同设备功能用例最终验证通过；Debug/Release lint 均为 0 错误
> 测试框架：JUnit 4 + MockK + Coroutines Test

## 覆盖重点

- `FileIndexerTest`：同步批处理、删除保护、串行锁、取消安全
- `AttachmentRepositoryImplTest`：删除、路径规范化、引用识别
- `EditorViewModelTest`：预览防抖、附件添加/丢弃/保存
- `QuickNoteViewModelTest`：库切换后的草稿隔离、附件生命周期、保存流程
- `AttachmentManagerViewModelTest`：引用加载、选择删除、异常提示
- `LibraryRepositoryTest`：库 CRUD、URI 校验
- `MarkdownSyncPlannerTest` / `PreviewCacheTest` / `MarkdownUtilsTest` / `FileUtilsTest` / `UriHelperTest`
- `GenerateFileNameUseCaseTest` / `CreateMarkdownFileUseCaseTest`

## 最近验证

- 2026-10-26：1.2.3 发布前检查通过；新增热力图无人工等待回归与真实 Room 全部笔记/按日查询回归，修正现有设备测试的 JUnit 返回类型。真实 SAF 授权后附件用例补跑通过。正式签名 1.2.2 → 1.2.3 覆盖升级验证保留笔记、目录权限与文件内容。完整范围、日志和保留限制见 [1.2.3 发布前检查](RELEASE_CHECK_1.2.3.md)。
- 2026-08-13：`.\gradlew.bat testDebugUnitTest` 通过。
- 2026-08-09 的完整质量门禁结果仍可参考 `docs/ARCHITECTURE_REVIEW_2026-08-09.md`；本次没有重新跑 lint。

## 说明

- 这个文档只记录测试覆盖和最近验证，不替代架构审查。
- 如果改动涉及 SAF、附件清理、索引同步或编辑器预览，优先补对应 ViewModel / Repository / Indexer 测试。
