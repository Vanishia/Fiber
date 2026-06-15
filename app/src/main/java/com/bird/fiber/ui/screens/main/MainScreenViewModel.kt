package com.bird.fiber.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.local.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主屏幕 ViewModel
 *
 * 管理侧边栏状态和库切换
 */
@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainScreenUiState())
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    init {
        observeActiveLibrary()
    }

    /**
     * 观察当前激活的库
     */
    private fun observeActiveLibrary() {
        viewModelScope.launch {
            libraryRepository.getActiveLibrary().collect { library ->
                _uiState.value = _uiState.value.copy(
                    selectedLibraryId = library?.id,
                    currentLibraryName = library?.name,
                    currentLibraryUri = library?.folderUri
                )
            }
        }
    }

    /**
     * 选择库
     */
    fun onLibrarySelected(libraryId: String) {
        _uiState.value = _uiState.value.copy(
            selectedLibraryId = libraryId
        )
    }
}

/**
 * 主屏幕 UI 状态
 */
data class MainScreenUiState(
    val selectedLibraryId: String? = null,
    val currentLibraryName: String? = null,
    val currentLibraryUri: String? = null
)
