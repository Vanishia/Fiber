package com.bird.fiber.ui.screens.attachments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.ManagedAttachment
import com.bird.fiber.data.model.toUserMessage
import com.bird.fiber.data.repository.AttachmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttachmentManagerViewModel @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
    private val libraryRepository: LibraryRepository,
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
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
                }
                is FileResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        libraryName = library.name,
                        isLoading = false,
                        error = result.error.toUserMessage()
                    )
                }
                is FileResult.Loading -> Unit
            }
        }
    }

    fun setFilter(filter: AttachmentFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun startSelection(attachment: ManagedAttachment) {
        if (attachment.isReferenced) {
            _uiState.value = _uiState.value.copy(message = "暂不支持删除有关联的文件")
            return
        }
        _uiState.value = _uiState.value.copy(selectedUris = setOf(attachment.uri))
    }

    fun toggleSelection(attachment: ManagedAttachment) {
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
        if (selectedUris.isEmpty() || _uiState.value.isDeleting) return

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

    companion object {
        const val ARG_LIBRARY_ID = "libraryId"
    }
}
