package com.bird.fiber.ui.screens.sidebar

import com.bird.fiber.data.local.library.LibraryEntity

/**
 * 侧边栏 UI 状态
 */
data class SidebarUiState(
    val libraries: List<LibraryEntity> = emptyList(),
    val activeLibraryId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /**
     * 当前激活的库
     */
    val activeLibrary: LibraryEntity?
        get() = libraries.find { it.id == activeLibraryId }

    /**
     * 是否有库
     */
    val hasLibraries: Boolean
        get() = libraries.isNotEmpty()
}
