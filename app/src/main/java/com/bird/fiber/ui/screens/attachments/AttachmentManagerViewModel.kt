package com.bird.fiber.ui.screens.attachments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.model.AttachmentReference
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.ManagedAttachment
import com.bird.fiber.data.model.toUserMessage
import com.bird.fiber.data.repository.AttachmentRepository
import com.bird.fiber.data.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AttachmentManagerViewModel @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
    private val libraryRepository: LibraryRepository,
    private val fileRepository: FileRepository,
    private val markdownFileDao: MarkdownFileDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val libraryId = requireNotNull(savedStateHandle.get<String>(ARG_LIBRARY_ID))
    private val _uiState = MutableStateFlow(AttachmentManagerUiState())
    val uiState: StateFlow<AttachmentManagerUiState> = _uiState.asStateFlow()

    init {
        loadAttachments()
    }

    fun loadAttachments() {
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isReferencesLoading = true,
                referencesLoaded = false,
                referenceError = null,
                selectedUris = emptySet(),
                filter = AttachmentFilter.ALL,
                error = null
            )
            val library = libraryRepository.getLibraryById(libraryId)
            if (library == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "笔记库不存在或已被移除"
                )
                return@launch
            }

            when (val result = attachmentRepository.listForLibrary(libraryId)) {
                is FileResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        libraryName = library.name,
                        attachments = result.data,
                        selectedUris = emptySet(),
                        isLoading = false,
                        error = null
                    )
                    if (result.data.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isReferencesLoading = false,
                            referencesLoaded = true
                        )
                    } else {
                        loadReferences(result.data)
                    }
                }
                is FileResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        libraryName = library.name,
                        isLoading = false,
                        isReferencesLoading = false,
                        error = result.error.toUserMessage()
                    )
                }
                is FileResult.Loading -> Unit
            }
        }
    }

    fun setFilter(filter: AttachmentFilter) {
        if (!_uiState.value.referencesLoaded) return
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun startSelection(attachment: ManagedAttachment) {
        if (!_uiState.value.referencesLoaded) {
            _uiState.value = _uiState.value.copy(message = "关联状态仍在加载，请稍后")
            return
        }
        if (attachment.isReferenced) {
            _uiState.value = _uiState.value.copy(message = "暂不支持删除有关联的文件")
            return
        }
        _uiState.value = _uiState.value.copy(selectedUris = setOf(attachment.uri))
    }

    fun toggleSelection(attachment: ManagedAttachment) {
        if (!_uiState.value.referencesLoaded) {
            _uiState.value = _uiState.value.copy(message = "关联状态仍在加载，请稍后")
            return
        }
        if (attachment.isReferenced) {
            _uiState.value = _uiState.value.copy(message = "暂不支持删除有关联的文件")
            return
        }
        val selected = _uiState.value.selectedUris.toMutableSet()
        if (!selected.add(attachment.uri)) selected.remove(attachment.uri)
        _uiState.value = _uiState.value.copy(selectedUris = selected)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedUris = emptySet())
    }

    fun deleteSelected() {
        val selectedUris = _uiState.value.selectedUris
        if (
            selectedUris.isEmpty() ||
            _uiState.value.isDeleting ||
            !_uiState.value.referencesLoaded
        ) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            when (val result = attachmentRepository.deleteOrphans(libraryId, selectedUris)) {
                is FileResult.Success -> {
                    val summary = result.data
                    val message = buildString {
                        append("已删除 ${summary.deletedCount} 张图片")
                        if (summary.skippedReferencedCount > 0) {
                            append("，${summary.skippedReferencedCount} 张因已有引用而跳过")
                        }
                        if (summary.failedCount > 0) {
                            append("，${summary.failedCount} 张删除失败")
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        selectedUris = emptySet(),
                        isDeleting = false,
                        message = message
                    )
                    loadAttachments()
                }
                is FileResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        message = result.error.toUserMessage()
                    )
                }
                is FileResult.Loading -> {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        message = "删除任务未完成，请重试"
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /**
     * 打开附件的关联笔记
     *
     * 只有一篇时直接弹出笔记预览；多篇时先弹出选择菜单（文件名 + 摘要）
     */
    fun openLinkedNotes(attachment: ManagedAttachment) {
        if (!_uiState.value.referencesLoaded || !attachment.isReferenced) return
        viewModelScope.launch {
            val notes = attachment.referencedBy.map { loadLinkedNote(it) }
            if (notes.size == 1) {
                _uiState.value = _uiState.value.copy(viewingLinkedNote = notes.first())
            } else {
                _uiState.value = _uiState.value.copy(linkedNoteChoices = notes)
            }
        }
    }

    /** 从多篇关联笔记的菜单中选定一篇，弹出笔记预览 */
    fun openLinkedNote(note: LinkedNote) {
        _uiState.value = _uiState.value.copy(
            linkedNoteChoices = null,
            viewingLinkedNote = note
        )
    }

    fun dismissLinkedNoteChoices() {
        _uiState.value = _uiState.value.copy(linkedNoteChoices = null)
    }

    fun dismissLinkedNote() {
        _uiState.value = _uiState.value.copy(viewingLinkedNote = null)
    }

    /**
     * 读取关联笔记的元信息和全文
     *
     * 文件名/摘要优先取索引；全文读取失败时 content 为 null，界面退回展示摘要
     */
    private suspend fun loadLinkedNote(reference: AttachmentReference): LinkedNote {
        val entity = runCatching { markdownFileDao.getFileByUri(reference.fileUri) }
            .onFailure { Timber.e(it, "Failed to load linked note meta") }
            .getOrNull()
        val content = runCatching { fileRepository.readFileContent(reference.fileUri) }
            .onFailure { Timber.e(it, "Failed to read linked note content") }
            .getOrNull()
            ?.let { it as? FileResult.Success }
            ?.data
        return LinkedNote(
            fileUri = reference.fileUri,
            fileName = entity?.name ?: reference.fileName.removeSuffix(".md"),
            preview = entity?.contentPreview ?: content?.take(PREVIEW_FALLBACK_CHARS).orEmpty(),
            content = content,
            lastModified = entity?.lastModified ?: 0L
        )
    }

    fun retryReferences() {
        val attachments = _uiState.value.attachments
        if (attachments.isEmpty() || _uiState.value.isReferencesLoading) return
        viewModelScope.launch { loadReferences(attachments) }
    }

    private suspend fun loadReferences(attachments: List<ManagedAttachment>) {
        _uiState.value = _uiState.value.copy(
            isReferencesLoading = true,
            referencesLoaded = false,
            referenceError = null
        )
        when (val result = attachmentRepository.loadReferencesForLibrary(libraryId, attachments)) {
            is FileResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    attachments = attachments.map { attachment ->
                        attachment.copy(
                            referencedBy = result.data[attachment.uri].orEmpty()
                        )
                    },
                    isReferencesLoading = false,
                    referencesLoaded = true,
                    referenceError = null
                )
            }
            is FileResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isReferencesLoading = false,
                    referencesLoaded = false,
                    referenceError = result.error.toUserMessage()
                )
            }
            is FileResult.Loading -> Unit
        }
    }

    companion object {
        const val ARG_LIBRARY_ID = "libraryId"

        /** 笔记未被索引时，取正文开头多少字符作为摘要 */
        private const val PREVIEW_FALLBACK_CHARS = 200
    }
}
