# Fiber 单元测试总结

> 最近更新：2026-08-13
> 最近验证：`.\gradlew.bat testDebugUnitTest`
> 验证结果：通过
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

- 2026-08-13：`.\gradlew.bat testDebugUnitTest` 通过。
- 2026-08-09 的完整质量门禁结果仍可参考 `docs/ARCHITECTURE_REVIEW_2026-08-09.md`；本次没有重新跑 lint。

## 说明

- 这个文档只记录测试覆盖和最近验证，不替代架构审查。
- 如果改动涉及 SAF、附件清理、索引同步或编辑器预览，优先补对应 ViewModel / Repository / Indexer 测试。
