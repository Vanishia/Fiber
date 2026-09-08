package com.bird.fiber.ui.screens.sidebar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.settings.SettingsDataStore
import com.bird.fiber.data.update.AppUpdateInfo
import com.bird.fiber.data.update.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 侧边栏 ViewModel
 *
 * 管理笔记库列表和切换逻辑
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SidebarViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val updateChecker: UpdateChecker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SidebarUiState())
    val uiState: StateFlow<SidebarUiState> = _uiState.asStateFlow()

    /** 可用更新信息；null 表示已是最新、检查失败或用户关闭了更新通知 */
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    init {
        loadLibraries()
        observeActiveLibrary()
        observeUpdateNotificationSetting()
    }

    /**
     * 跟随"显示更新通知"开关：开启时检查一次新版本，关闭时立即清除提示
     */
    private fun observeUpdateNotificationSetting() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map { it.updateNotificationEnabled }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    _updateInfo.value = if (enabled) {
                        updateChecker.checkForUpdate()
                    } else {
                        null
                    }
                }
        }
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
