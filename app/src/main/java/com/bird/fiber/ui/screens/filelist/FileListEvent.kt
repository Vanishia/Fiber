package com.bird.fiber.ui.screens.filelist

/**
 * 文件列表页面用户事件
 *
 * UI通过这些事件与ViewModel交互
 * 这是单向数据流的核心：UI -> Event -> ViewModel -> UiState
 */
sealed interface FileListEvent {
    /**
     * 选择文件夹
     */
    data object SelectFolder : FileListEvent

    /**
     * 刷新文件列表
     */
    data object RefreshFiles : FileListEvent

    /**
     * 选择文件
     */
    data class SelectFile(val file: com.bird.fiber.data.model.MarkdownFileMeta) : FileListEvent

    /**
     * 搜索文件
     */
    data class Search(val query: String) : FileListEvent

    /**
     * 清除错误
     */
    data object ClearError : FileListEvent

    /**
     * 创建新文件
     */
    data class CreateFile(val fileName: String) : FileListEvent

    /**
     * 删除文件
     */
    data class DeleteFile(val fileUri: String) : FileListEvent

    /**
     * 重命名文件
     */
    data class RenameFile(val fileUri: String, val newName: String) : FileListEvent
}
