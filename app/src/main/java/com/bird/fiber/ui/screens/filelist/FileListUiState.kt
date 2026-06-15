package com.bird.fiber.ui.screens.filelist

import com.bird.fiber.data.model.MarkdownFileMeta

/**
 * 文件列表页面 UI状态契约
 *
 * 这是ViewModel向UI暴露数据的唯一方式
 * UI层只需要观察这个State，不需要知道ViewModel如何实现
 *
 * 注意：文件列表现在通过 Paging 3 管理，不再存储在 UiState 中
 */
data class FileListUiState(
    val isLoading: Boolean = false,                    // 加载状态
    val isSyncing: Boolean = false,                    // 是否正在同步文件
    val currentFolderUri: String? = null,              // 当前选择的文件夹URI
    val selectedFile: MarkdownFileMeta? = null,        // 当前选中的文件
    val error: String? = null,                         // 错误信息
    val hasResolvedInitialLibrary: Boolean = false,    // 是否已完成首次活动库解析
    val isFolderSelected: Boolean = false,             // 是否已选择文件夹
    val searchQuery: String = ""                       // 搜索关键词
) {
    /**
     * 是否显示未选择文件夹提示
     */
    val showNoFolderState: Boolean
        get() = hasResolvedInitialLibrary && !isLoading && !isFolderSelected
}
