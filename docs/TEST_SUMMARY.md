# Fiber 单元测试总结

> 测试完成时间：2026-03-11
> 测试框架：JUnit 4 + MockK + Coroutines Test

---

## 📦 已添加的测试依赖

### 核心测试框架
- **JUnit 4** - 基础测试框架
- **MockK 1.13.8** - Kotlin Mock 框架
- **Coroutines Test 1.9.0** - 协程测试支持
- **Turbine 1.0.0** - Flow 测试工具
- **Room Testing 2.6.1** - 数据库测试
- **Hilt Testing 2.48** - 依赖注入测试
- **AndroidX Test Core 1.5.0** - Android 测试核心
- **Arch Core Testing 2.2.0** - ViewModel 测试

---

## 📝 已编写/更新的测试

### 1. QuickNoteViewModelTest
路径：`app/src/test/java/com/bird/fiber/ui/screens/quicknote/QuickNoteViewModelTest.kt`

**测试覆盖**：
- ✅ 内容输入管理：`onContentChange()`
- ✅ 保存流程：成功后清空内容、发出 `QuickNoteEvent.SaveSuccess`
- ✅ 错误处理：保存失败更新 `error`，`clearError()` 清理错误
- ✅ 状态切换：保存中 `isSaving=true`（通过对 UseCase stub 加 `delay` 保证窗口期）
- ✅ 边界/并发：空白内容、快速连续保存调用

**测试要点**：
- `saveNote()` 成功分支会读取 `MarkdownFileMeta.uri` 并发出 `AppEvent.FileCreated`，因此用例中 `FileResult.Success` 需要返回带 `uri` 的 `MarkdownFileMeta` mock（否则会触发 MockKException）。

### 2. EditorViewModelTest
路径：`app/src/test/java/com/bird/fiber/ui/screens/editor/EditorViewModelTest.kt`

**测试覆盖**：
- ✅ 文件加载：成功/失败/加载中状态
- ✅ 内容编辑与未保存修改检测：`hasUnsavedChanges()`
- ✅ 保存文件：成功/失败/未加载文件时不操作
- ✅ 预览模式切换：`togglePreviewMode()` / `setInitialPreviewMode()`

**测试要点**：
- `loadFile()` 内部在协程中设置 `isLoading=true`，因此测试里通过让 `readFileContent()` 延迟返回，确保能稳定断言到 loading 状态。

### 3. CreateMarkdownFileUseCaseTest
路径：`app/src/test/java/com/bird/fiber/domain/usecase/CreateMarkdownFileUseCaseTest.kt`

**测试覆盖**：
- ✅ 内容校验：空/空白内容返回错误
- ✅ 文件名生成：未提供文件名时调用 `GenerateFileNameUseCase`
- ✅ 文件夹 URI：未提供 folderUri 时使用当前库 URI
- ✅ Repository 异常/错误结果透传

**测试要点**：
- 部分用例需要显式 stub `generateFileName()`，并与 `createMarkdownFile(folderUri, fileName, content)` 的参数精确匹配。

### 4. LibraryRepositoryTest
路径：`app/src/test/java/com/bird/fiber/data/local/library/LibraryRepositoryTest.kt`

**测试覆盖**：
- ✅ 库列表/当前库：DAO Flow 透传
- ✅ 添加/更新/切换/删除
- ✅ 校验并清理无效库：根据 `persistedUriPermissions` 判断 URI 是否仍有效

**测试要点**：
- JVM 单元测试环境下 `android.net.Uri.parse()` 会触发 “not mocked” 的运行时异常；相关用例通过 `mockkStatic(Uri::class)` + stub `Uri.parse(any())` 避免依赖 Android framework 实现。

---

## 🧪 如何运行测试

### 运行全部 JVM 单元测试
```bash
./gradlew :app:testDebugUnitTest
```

### 只运行某个 TestClass
```bash
./gradlew :app:testDebugUnitTest --tests "com.bird.fiber.ui.screens.quicknote.QuickNoteViewModelTest"
```

---

**最后更新**: 2026-03-11
