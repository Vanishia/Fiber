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
     */
    data class SyncStarted(val libraryId: String) : AppEvent()

    /**
     * 同步库完成
     */
    data class SyncCompleted(val libraryId: String) : AppEvent()
}
