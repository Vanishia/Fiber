# Fiber 1.2.3 发布前检查

- 检查日期：2026-10-26（Asia/Shanghai）。
- 发布状态：本地发布候选包与更新说明已准备，尚未创建 Git 标签或发布 GitHub Release。
- 最终包源码提交：`7ee2fc9`，包含设置页调整 `5b482be`。
- 版本：`versionName = "1.2.3"`，`versionCode = 9`；上一版为 `1.2.2 / 8`。
- 更新说明：[中英文更新说明](RELEASE_NOTES_1.2.3.md)。内容按 1.2.2 版本提交 `59ab445` 之后的实际改动整理，不把未完成的交互改造写成已修复。

## 本轮修复

- 正文行距移至字体大小下方并改为五档胶囊，古诗移至行距下方且随字号、行距变化；推荐配色改为主题颜色右侧的五个圆点。
- 移除热力图筛选及索引更新时固定的 200ms 等待。
- 移除人为清空网格及 `isSwitching` 过渡态；新结果在后台计算完成后一次性更新，期间保留已有内容。
- 保留首次查询尚未返回时的真实加载提示；无笔记时仍展示正常的空日历。
- 新增 4 项 ViewModel 回归，覆盖月/年/最近一年切换、索引刷新、连续切换与首次加载。使用测试调度器验证无需推进虚拟时间即可得到完整结果。
- 新增 3 项真实 Room 分页查询回归，覆盖全部库浏览、软删除过滤、摘要数据、日期归属、日界限及无结果日期。
- 修复现有搜索和 SAF 设备测试的 JUnit 方法返回类型，确保它们可以被实际运行，而非在初始化时失败。

## 自动化结果

| 检查 | 结果 |
| --- | --- |
| JVM 全量单测 | 36 个测试类，360 项通过，0 失败、0 错误、0 跳过 |
| Debug 构建 | 通过，版本为 1.2.3 / 9 |
| Release 构建 | 通过，启用既有 R8 混淆及资源压缩 |
| Debug lint | 0 错误、98 警告、6 条提示 |
| Release lint | 0 错误、94 警告、6 条提示 |
| Android 设备功能回归 | Android 16 / API 36.1 独立模拟器，19 个不同用例最终均验证通过 |
| 发布签名 | APK 验签通过，与本地 1.2.2 发布包证书一致 |
| 覆盖升级 | 早前候选包从正式签名 1.2.2 以 `install -r` 升级到 1.2.3，通过 |
| 版本显示与更新比较 | 设置页使用 `BuildConfig.VERSION_NAME`；版本比较单测通过 |
| 补丁格式 | `git diff --check` 通过 |

设备功能回归首轮为 18 项通过、1 项因没有已授权笔记库而跳过。通过系统 SAF 选择器授权真实测试目录后，单独补跑该附件用例通过。这里的“19 项”按不同用例计数，不把补跑重复计算。首轮误用共享模拟器的未完成运行不计入验收结果，最终使用本轮启动的只读独立模拟器 `emulator-5582`。

lint 仍有警告与提示，包含依赖版本、API 弃用、Room 非搜索投影缺省 `match_snippet` 等现有问题；摘要投影缺省值已由新增设备用例验证。检查结果不等同于已消除全部静态分析提示。

设置页调整后的最终包重新执行全量 JVM 单测（360 项通过）、Release 构建及 Release lint（0 错误、94 警告、6 条提示），并复核 APK 实际版本为 1.2.3 / 9、签名证书与 1.2.2 一致。设置页改动已通过 Debug 构建与 Debug lint；此处的设备功能回归及覆盖升级记录对应早前候选包，最终包未再次安装到设备。

## 功能覆盖

| 功能 | 验证内容 |
| --- | --- |
| 编辑与保存 | 加载失败、原文保存、保存期间继续输入、旧异步结果失效、图片插入、撤销/重做、中文与 emoji 反向选区、工具栏焦点及键盘撤销 |
| Markdown 预览 | 代码与 CRLF 保留、引用式图片、代码中的伪图片、表格换行与字号变化、图片加载及资源释放、大文档回归样本 |
| 热力图与统计 | 日期聚合、月/年/最近一年范围、数量与字数、库分布与记录时段、空范围、无人工等待及空白过渡帧 |
| 全部笔记与某日笔记 | 实际 Room 分页、跨库元数据、排序、软删除、快速笔记创建日与普通笔记修改日口径、次日零点排除、无结果日期 |
| 快速记录 | 保存与错误处理、切库后的草稿隔离、附件生命周期；JVM 回归 |
| 分享与导入 | 后台恢复、Activity 重建、转场完成后导航、重复结果处理、原编辑草稿保留、保存期间对话框交互 |
| 笔记库与索引 | 库管理、同步规划、删除保护、批处理、取消及并发安全；JVM 回归 |
| 搜索 | 中文与任意子串语义的真实 SQLite 回归，以及相关搜索/文件仓库测试 |
| 设置与主页视图 | 按库持久化与迁移、切库后独立视图、同名库、空库、长列表、保存失败反馈；真实 Compose 设置页回归 |
| 附件管理 | ViewModel/仓库回归；真实 SAF 目录中的图片复制、重名文件命名与删除，操作后清理测试附件 |
| 主题、滚动及辅助功能 | 颜色方案、预览配置、滚动条数学边界、图标内容描述及设置单选语义 |

## 签名版升级与启动验证

早前候选包在独立的只读模拟器副本中，以实际系统文件选择器授权测试目录：

1. 安装 `release/Fiber-v1.2.2.apk`，导入包含 `Release123Regression.md` 的真实 SAF 笔记库。
2. 确认旧版本能够显示该笔记并记录文件 SHA-256。
3. 通过 `adb install -r` 安装 `release/Fiber-v1.2.3.apk`，没有卸载旧正式版或清空其应用数据。
4. 确认升级后笔记库、目录访问权限、索引条目与文件 SHA-256 保留。
5. 打开已有笔记，确认经过混淆的正式版能加载并显示 Markdown 正文；进入“全部笔记”仍可查看该笔记。
6. 核对系统安装信息为 `versionName=1.2.3`、`versionCode=9`，且没有 `DEBUGGABLE` 标志。

上述步骤全部通过。此次没有安装或覆盖真实手机上的应用。

## 发布文件

- 正式 APK：`release/Fiber-v1.2.3.apk`，2,495,086 字节。
- APK SHA-256：`BFD25B60B7DC35D492275404EEC7DB3CE4D7A6C918C33B4ADC11672068E331C7`。
- 签名证书 SHA-256：`ed02567df7c6e7183f15790e22dbdb7131d29b6f3f9f28ffcacdac8475ff3c86`。
- APK、证书及本地构建日志遵循现有忽略规则，不加入源码版本控制。

## 复核命令与记录

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --offline --console plain
.\gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --offline --console plain
adb -s emulator-5582 shell am instrument -w -r -e notAnnotation androidx.test.filters.LargeTest com.bird.fiber.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5582 shell am instrument -w -r -e class com.bird.fiber.data.local.AttachmentRepositorySafTest com.bird.fiber.test/androidx.test.runner.AndroidJUnitRunner
git diff --check
```

- JVM 报告：`app/build/reports/tests/testDebugUnitTest/index.html`。
- lint 报告：`app/build/reports/lint-results-debug.html`、`app/build/reports/lint-results-release.html`。
- 构建日志：`build/release-1.2.3-validation.log`、`build/release-1.2.3-test-harness.log`。
- 最终包检查日志：`build/release-1.2.3-final-build.log`、`build/release-1.2.3-final-package.log`。
- 早前候选包保留于 `build/Fiber-v1.2.3-before-settings.apk`，SHA-256 为 `C29CF46354D597E656DE137073A990F51861D9EB8B855E79FBD409AC60946FF5`，用于对应此前设备升级记录。
- 最终设备日志：`build/release-1.2.3-device-tests-final.log`、`build/release-1.2.3-saf-test.log`。
- 正式版升级及启动记录：`build/release-1.2.3-upgrade.log`；本地验证脚本为 `build/verify-release-1.2.3-upgrade.ps1`。

## 边界与后续事项

- 本轮去除的是热力图的固定等待，不宣称解决页面切换的全部性能问题或此前梳理出的全部操作链路问题。
- 按日页缺少相邻日期切换、搜索未继承浏览范围、切库仅返回一层及日期页替换栈等交互问题仍需后续单独处理，未写入本版“已修复”内容。
- 本轮未运行 5,000 文件 / 100MB 的搜索性能基准，不提供新的性能提升比例。
- 表格插入、LaTeX 公式以及精确源码位置驱动滚动仍属未交付功能；既有 Markdown 表格预览已回归。
- 模拟器验证不能替代真实手机上的第三方中文输入法、键盘动画、不同屏幕与低内存设备体验验收，也未向 GitHub 服务发起实际发布或在线更新下载验证。
