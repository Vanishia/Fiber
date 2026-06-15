package com.bird.fiber.ui.screens.quicknote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.FileError
import com.bird.fiber.data.model.toUserMessage
import com.bird.fiber.domain.usecase.CreateMarkdownFileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 快速记录 ViewModel
 *
 * 职责：
 * 1. 管理输入内容（UI 状态）
 * 2. 协调创建文件的业务流程
 * 3. 通过事件总线通知其他 ViewModel
 *
 * 架构改进：
 * - 移除了业务逻辑（文件名生成）到 Domain 层
 * - 使用 CreateMarkdownFileUseCase 封装创建流程
 * - ViewModel 只负责 UI 状态管理和协调
 * - 使用 Channel 发送一次性事件，避免回调生命周期问题
 */
@HiltViewModel
class QuickNoteViewModel @Inject constructor(
    private val createMarkdownFile: CreateMarkdownFileUseCase,
    private val eventBus: EventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickNoteUiState())
    val uiState: StateFlow<QuickNoteUiState> = _uiState.asStateFlow()

    // 使用 Channel 发送一次性事件（保存成功通知）
    private val _events = Channel<QuickNoteEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * 更新输入内容
     */
    fun onContentChange(content: String) {
        Timber.d("QuickNoteViewModel: onContentChange('${_uiState.value.content}' -> '$content')")
        _uiState.value = _uiState.value.copy(content = content)
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 保存笔记
     *
     * 架构改进：
     * - 使用 CreateMarkdownFileUseCase 封装业务逻辑
     * - ViewModel 只负责更新 UI 状态
     * - 通过事件总线通知其他页面刷新
     * - 通过 Channel 通知 UI 层关闭页面
     */
    fun saveNote() {
        val content = _uiState.value.content
        Timber.d("QuickNoteViewModel: saveNote() 被调用，当前 content = '$content'")

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            // 调用 UseCase 创建文件
            when (val result = createMarkdownFile(
                folderUri = null,  // 使用当前选中的库
                content = content
            )) {
                is FileResult.Success -> {
                    Timber.d("QuickNoteViewModel: 保存成功，文件 URI = '${result.data.uri}'")
                    // 发送文件创建事件，通知文件列表刷新
                    eventBus.emit(AppEvent.FileCreated(result.data.uri))

                    // 清空输入框，保存完成
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        content = ""
                    )
                    Timber.d("QuickNoteViewModel: content 已清空")

                    // 通过 Channel 通知 UI 层关闭页面
                    _events.send(QuickNoteEvent.SaveSuccess)
                }

                is FileResult.Error -> {
                    Timber.e("QuickNoteViewModel: 保存失败，error = ${result.error}")
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = result.error.toUserMessage()
                    )
                }

                is FileResult.Loading -> {
                    Timber.d("QuickNoteViewModel: 返回 Loading（不应该发生）")
                    // 不需要处理（Loading 状态由 isSaving 控制）
                }
            }
        }
    }
}

/**
 * 快速记录页面的一次性事件
 */
sealed class QuickNoteEvent {
    /**
     * 保存成功，可以关闭页面
     */
    object SaveSuccess : QuickNoteEvent()
}
