package com.bird.fiber.ui.screens.quicknote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.LibraryTarget
import com.bird.fiber.data.model.toUserMessage
import com.bird.fiber.domain.usecase.CreateMarkdownFileUseCase
import com.bird.fiber.data.repository.AttachmentRepository
import com.bird.fiber.data.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val eventBus: EventBus,
    private val attachmentRepository: AttachmentRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickNoteUiState())
    val uiState: StateFlow<QuickNoteUiState> = _uiState.asStateFlow()

    // 使用 Channel 发送一次性事件（保存成功通知）
    private val _events = Channel<QuickNoteEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val draftTargetMutex = Mutex()
    private var draftTarget: LibraryTarget? = null
    private val pendingAttachmentUris = mutableSetOf<String>()

    /**
     * 更新输入内容
     */
    fun onContentChange(content: String) {
        Timber.d("QuickNoteViewModel: onContentChange('${_uiState.value.content}' -> '$content')")
        val shouldLockTarget = _uiState.value.content.isBlank() && content.isNotBlank() && draftTarget == null
        _uiState.value = _uiState.value.copy(content = content)
        if (shouldLockTarget) {
            viewModelScope.launch { resolveDraftTarget() }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun addImage(sourceUri: String) {
        if (_uiState.value.isAddingImage) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingImage = true, error = null)
            val target = resolveDraftTarget()
            if (target == null) {
                _uiState.value = _uiState.value.copy(
                    isAddingImage = false,
                    error = "未选择笔记库，请先添加库"
                )
                return@launch
            }
            when (val result = attachmentRepository.copyImage(sourceUri, target)) {
                is FileResult.Success -> {
                    pendingAttachmentUris += result.data.uri
                    _uiState.value = _uiState.value.copy(
                        attachments = _uiState.value.attachments + result.data,
                        isAddingImage = false
                    )
                }
                is FileResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isAddingImage = false,
                        error = result.error.toUserMessage()
                    )
                }
                is FileResult.Loading -> Unit
            }
        }
    }

    fun removeAttachment(relativePath: String) {
        val attachment = _uiState.value.attachments.firstOrNull { it.relativePath == relativePath }
        _uiState.value = _uiState.value.copy(
            attachments = _uiState.value.attachments.filterNot { it.relativePath == relativePath }
        )
        if (attachment != null && pendingAttachmentUris.remove(attachment.uri)) {
            viewModelScope.launch {
                when (val result = attachmentRepository.delete(attachment.uri)) {
                    is FileResult.Error -> {
                        Timber.e("QuickNoteViewModel: 删除附件失败，uri=${attachment.uri}, error=${result.error}")
                        _uiState.value = _uiState.value.copy(error = result.error.toUserMessage())
                    }
                    is FileResult.Success,
                    is FileResult.Loading -> Unit
                }
            }
        }
    }

    fun discardDraft(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val urisToDelete = pendingAttachmentUris.toList()
            pendingAttachmentUris.clear()
            _uiState.value = QuickNoteUiState()
            draftTargetMutex.withLock { draftTarget = null }
            onComplete(deleteAttachments(urisToDelete))
        }
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
        if (_uiState.value.isSaving || _uiState.value.isAddingImage) return
        val content = buildNoteContent(_uiState.value)
        if (content.isBlank()) return
        Timber.d("QuickNoteViewModel: saveNote() 被调用，当前 content = '$content'")

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            val target = resolveDraftTarget()
            if (target == null) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "未选择笔记库，请先添加库"
                )
                return@launch
            }

            // 调用 UseCase 创建文件
            when (val result = createMarkdownFile(
                target = target,
                content = content
            )) {
                is FileResult.Success -> {
                    Timber.d("QuickNoteViewModel: 保存成功，文件 URI = '${result.data.uri}'")
                    // 发送文件创建事件，通知文件列表刷新
                    eventBus.emit(AppEvent.FileCreated(result.data.uri))

                    // 清空输入框，保存完成
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        content = "",
                        attachments = emptyList()
                    )
                    pendingAttachmentUris.clear()
                    draftTargetMutex.withLock { draftTarget = null }
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

    private fun buildNoteContent(state: QuickNoteUiState): String {
        val references = state.attachments.joinToString("\n") { it.toMarkdown() }
        return when {
            state.content.isBlank() -> references
            references.isBlank() -> state.content
            else -> state.content.trimEnd() + "\n\n" + references
        }
    }

    private suspend fun resolveDraftTarget(): LibraryTarget? = draftTargetMutex.withLock {
        draftTarget ?: fileRepository.currentLibraryTarget.firstOrNull()?.also { draftTarget = it }
    }

    private suspend fun deleteAttachments(uris: List<String>): Boolean {
        var allDeleted = true
        uris.forEach { uri ->
            when (val result = attachmentRepository.delete(uri)) {
                is FileResult.Error -> {
                    allDeleted = false
                    Timber.e("QuickNoteViewModel: 回滚附件失败，uri=$uri, error=${result.error}")
                }
                is FileResult.Success,
                is FileResult.Loading -> Unit
            }
        }
        return allDeleted
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
