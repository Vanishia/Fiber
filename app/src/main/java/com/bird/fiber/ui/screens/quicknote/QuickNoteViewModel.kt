package com.bird.fiber.ui.screens.quicknote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.model.Attachment
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

    private var currentTarget: LibraryTarget? = null
    private val draftsByLibraryId = mutableMapOf<String, QuickNoteDraft>()

    init {
        viewModelScope.launch {
            fileRepository.currentLibraryTarget.collect { target ->
                currentTarget = target
                showDraftFor(target)
            }
        }
    }

    /**
     * 更新输入内容
     */
    fun onContentChange(content: String) {
        Timber.d("QuickNoteViewModel: onContentChange('${_uiState.value.content}' -> '$content')")
        val target = currentTarget
        if (target == null) {
            _uiState.value = _uiState.value.copy(content = content)
            return
        }

        updateDraft(target.libraryId) { draft ->
            draft.copy(content = content)
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        val target = currentTarget
        if (target == null) {
            _uiState.value = _uiState.value.copy(error = null)
            return
        }

        updateDraft(target.libraryId) { draft ->
            draft.copy(error = null)
        }
    }

    fun addImage(sourceUri: String) {
        if (_uiState.value.isAddingImage) return
        val targetAtRequest = currentTarget
        viewModelScope.launch {
            val target = targetAtRequest ?: resolveCurrentTarget()
            if (target == null) {
                _uiState.value = _uiState.value.copy(
                    isAddingImage = false,
                    error = "未选择笔记库，请先添加库"
                )
                return@launch
            }
            val libraryId = target.libraryId

            updateDraft(libraryId) { draft ->
                draft.copy(isAddingImage = true, error = null)
            }
            when (val result = attachmentRepository.copyImage(sourceUri, target)) {
                is FileResult.Success -> {
                    updateDraft(libraryId) { draft ->
                        draft.copy(
                            attachments = draft.attachments + result.data,
                            pendingAttachmentUris = draft.pendingAttachmentUris + result.data.uri,
                            isAddingImage = false
                        )
                    }
                }
                is FileResult.Error -> {
                    updateDraft(libraryId) { draft ->
                        draft.copy(
                            isAddingImage = false,
                            error = result.error.toUserMessage()
                        )
                    }
                }
                is FileResult.Loading -> Unit
            }
        }
    }

    fun removeAttachment(relativePath: String) {
        val target = currentTarget ?: return
        val libraryId = target.libraryId
        val draft = draftFor(libraryId)
        val attachment = draft.attachments.firstOrNull { it.relativePath == relativePath }
        val shouldDelete = attachment != null && attachment.uri in draft.pendingAttachmentUris

        updateDraft(libraryId) {
            it.copy(
                attachments = it.attachments.filterNot { item -> item.relativePath == relativePath },
                pendingAttachmentUris = if (attachment == null) {
                    it.pendingAttachmentUris
                } else {
                    it.pendingAttachmentUris - attachment.uri
                }
            )
        }

        if (attachment != null && shouldDelete) {
            viewModelScope.launch {
                when (val result = attachmentRepository.delete(attachment.uri)) {
                    is FileResult.Error -> {
                        Timber.e("QuickNoteViewModel: 删除附件失败，uri=${attachment.uri}, error=${result.error}")
                        updateDraft(libraryId) { draft ->
                            draft.copy(error = result.error.toUserMessage())
                        }
                    }
                    is FileResult.Success,
                    is FileResult.Loading -> Unit
                }
            }
        }
    }

    fun discardDraft(onComplete: (Boolean) -> Unit = {}) {
        val target = currentTarget
        if (target == null) {
            _uiState.value = QuickNoteUiState()
            onComplete(true)
            return
        }

        val libraryId = target.libraryId
        val urisToDelete = draftFor(libraryId).pendingAttachmentUris.toList()
        clearDraft(libraryId)
        viewModelScope.launch {
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
        val stateAtSave = _uiState.value
        if (stateAtSave.isSaving || stateAtSave.isAddingImage) return
        val content = buildNoteContent(stateAtSave)
        if (content.isBlank()) return
        val targetAtSave = currentTarget
        Timber.d("QuickNoteViewModel: saveNote() 被调用，当前 content = '$content'")

        viewModelScope.launch {
            val target = targetAtSave ?: resolveCurrentTarget()
            if (target == null) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "未选择笔记库，请先添加库"
                )
                return@launch
            }
            val libraryId = target.libraryId

            updateDraft(libraryId) { draft ->
                draft.copy(isSaving = true, error = null)
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
                    clearDraft(libraryId)
                    Timber.d("QuickNoteViewModel: content 已清空")

                    // 通过 Channel 通知 UI 层关闭页面
                    _events.send(QuickNoteEvent.SaveSuccess)
                }

                is FileResult.Error -> {
                    Timber.e("QuickNoteViewModel: 保存失败，error = ${result.error}")
                    updateDraft(libraryId) { draft ->
                        draft.copy(
                            isSaving = false,
                            error = result.error.toUserMessage()
                        )
                    }
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

    private suspend fun resolveCurrentTarget(): LibraryTarget? {
        currentTarget?.let { return it }
        return fileRepository.currentLibraryTarget.firstOrNull()?.also { target ->
            currentTarget = target
            showDraftFor(target)
        }
    }

    private fun showDraftFor(target: LibraryTarget?) {
        _uiState.value = target
            ?.let { draftsByLibraryId[it.libraryId]?.toUiState() }
            ?: QuickNoteUiState()
    }

    private fun draftFor(libraryId: String): QuickNoteDraft {
        return draftsByLibraryId[libraryId] ?: QuickNoteDraft()
    }

    private fun updateDraft(
        libraryId: String,
        transform: (QuickNoteDraft) -> QuickNoteDraft
    ) {
        val updated = transform(draftFor(libraryId))
        if (updated.isEmpty()) {
            draftsByLibraryId.remove(libraryId)
        } else {
            draftsByLibraryId[libraryId] = updated
        }

        if (currentTarget?.libraryId == libraryId) {
            _uiState.value = updated.toUiState()
        }
    }

    private fun clearDraft(libraryId: String) {
        draftsByLibraryId.remove(libraryId)
        if (currentTarget?.libraryId == libraryId) {
            _uiState.value = QuickNoteUiState()
        }
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

private data class QuickNoteDraft(
    val content: String = "",
    val attachments: List<Attachment> = emptyList(),
    val pendingAttachmentUris: Set<String> = emptySet(),
    val isAddingImage: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    fun toUiState(): QuickNoteUiState = QuickNoteUiState(
        content = content,
        attachments = attachments,
        isAddingImage = isAddingImage,
        isSaving = isSaving,
        error = error
    )

    fun isEmpty(): Boolean {
        return content.isBlank() &&
            attachments.isEmpty() &&
            pendingAttachmentUris.isEmpty() &&
            !isAddingImage &&
            !isSaving &&
            error == null
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
