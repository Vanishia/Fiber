# 搜索与 FTS 评估记录

## 当前决定

本阶段不迁移 Room schema，继续使用 `LIKE '%query%'`。

原因：

- 目前没有代表性设备数据证明搜索 p95 已超过 250-300 ms。
- Fiber 当前搜索语义包含中文任意子串、英文词内子串、文件名和路径子串。
- FTS4 的 token 匹配无法等价覆盖任意子串。例如 `observability` 可被 `LIKE '%serv%'` 命中，但 FTS4 `MATCH 'serv'` 不命中。
- 在确认 FTS5 trigram 的 Android SQLite 兼容范围前，直接迁移可能提升速度但降低结果一致性。

## 评估语料

`SearchEvaluationInstrumentedTest` 覆盖：

- 中文正文任意子串：`中华人民共和国` 搜索 `人民共和`。
- 英文词内子串：`observability` 搜索 `serv`。
- 路径匹配：`projects/roadmap/quarter.md` 搜索 `roadmap`。
- Markdown 正文匹配：`项目会议记录` 搜索 `会议`。
- 5000 篇 Markdown、约 100 MB 正文的现有 LIKE 查询 p95。

基准使用独立的临时 Room 数据库，测试结束后删除，不读取、修改或清理用户笔记库。

## 执行命令

```powershell
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.bird.fiber.data.local.SearchEvaluationInstrumentedTest"
```

在 Logcat 中筛选 `SearchEvaluation`，读取 `LIKE_SEARCH_BENCHMARK` 的各查询 p95。

## 迁移触发条件

满足以下任一条件后重新评估 schema 4 -> 5：

- 代表性中低端设备的常用查询 p95 超过 250-300 ms。
- 常用库规模稳定达到数千文件或上百 MB 正文，并出现可感知搜索等待。

若触发，必须先验证：

- FTS4 与中文、部分词、任意子串的差异是否可接受。
- FTS5 trigram 在最低 Android 版本及目标设备 SQLite 中是否可用。
- 升级迁移、回滚、全量重建索引和搜索结果排序的一致性。
