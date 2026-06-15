package com.bird.fiber.ui.screens.sidebar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 侧边栏 ViewModel
 *
 * 管理笔记库列表和切换逻辑
 */
@HiltViewModel
class SidebarViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SidebarUiState())
    val uiState: StateFlow<SidebarUiState> = _uiState.asStateFlow()

    init {
        loadLibraries()
        observeActiveLibrary()
    }

    /**
     * 加载所有库
     */
    private fun loadLibraries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                libraryRepository.getAllLibraries().collect { libraries ->
                    _uiState.value = _uiState.value.copy(
                        libraries = libraries,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载库列表失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 观察当前激活的库
     */
    private fun observeActiveLibrary() {
        viewModelScope.launch {
            libraryRepository.getActiveLibrary().collect { library ->
                _uiState.value = _uiState.value.copy(
                    activeLibraryId = library?.id
                )
            }
        }
    }

    /**
     * 切换到指定库
     */
    fun switchLibrary(libraryId: String) {
        viewModelScope.launch {
            try {
                libraryRepository.switchLibrary(libraryId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "切换库失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 删除库
     */
    fun deleteLibrary(library: LibraryEntity) {
        viewModelScope.launch {
            try {
                // 传递 ContentResolver 以释放 URI 权限
                libraryRepository.deleteLibrary(library, context.contentResolver)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "删除库失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 添加新库
     */
    fun addLibrary(name: String, folderUri: String) {
        viewModelScope.launch {
            try {
                val newLibrary = LibraryEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    folderUri = folderUri,
                    createdAt = System.currentTimeMillis(),
                    lastOpenedAt = System.currentTimeMillis(),
                    isActive = false  // 先设为 false
                )

                // 先添加库，再切换
                libraryRepository.addLibrary(newLibrary)
                libraryRepository.switchLibrary(newLibrary.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "添加库失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
