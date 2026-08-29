package com.bird.fiber.data.event

/**
 * 应用全局事件
 *
 * 用于跨页面通信，避免 ViewModel 之间的隐式依赖
 */
sealed class AppEvent {
    /**
     * 刷新文件列表
     *
     * 触发时机：
     * - 创建文件后
     * - 删除文件后
     * - 修改文件后
     * - 切换库后
     */
    data object RefreshFileList : AppEvent()

    /**
     * 文件创建成功
     */
    data class FileCreated(val fileUri: String) : AppEvent()

    /**
     * 文件删除成功
     */
    data class FileDeleted(val fileUri: String) : AppEvent()

    /**
     * 文件更新成功
     */
    data class FileUpdated(val fileUri: String) : AppEvent()

    /**
     * 开始同步库
     *
     * @param isReindex true 表示这是一次全量重建索引（如数据库升级后回填），
     * 界面应提示"数据库更新中"而非"导入笔记"
     */
    data class SyncStarted(val libraryId: String, val isReindex: Boolean = false) : AppEvent()

    data class SyncProgress(
        val libraryId: String,
        val processed: Int,
        val total: Int
    ) : AppEvent()

    /**
     * 同步库完成
     */
    data class SyncCompleted(val libraryId: String) : AppEvent()
}
