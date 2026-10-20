package com.bird.fiber.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.bird.fiber.data.repository.FileRepository
import com.bird.fiber.data.model.onError
import com.bird.fiber.data.model.onSuccess
import com.bird.fiber.data.model.toUserMessage
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.domain.usecase.RenderMarkdownUseCase
import com.bird.fiber.data.repository.AttachmentRepository
import com.bird.fiber.domain.markdown.DocumentSnapshot
import com.bird.fiber.utils.UriHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 编辑器 ViewModel
 */
@HiltViewModel
class EditorViewModel : ViewModel {

    private val fileRepository: FileRepository
    private val eventBus: EventBus
    private val renderMarkdownUseCase: RenderMarkdownUseCase
    private val attachmentRepository: AttachmentRepository
    private val renderDispatcher: CoroutineDispatcher

    @Inject
    constructor(
        fileRepository: FileRepository,
        eventBus: EventBus,
        renderMarkdownUseCase: RenderMarkdownUseCase,
        attachmentRepository: AttachmentRepository
    ) : this(fileRepository, eventBus, renderMarkdownUseCase, attachmentRepository, Dispatchers.Default)

    internal constructor(
        fileRepository: FileRepository,
        eventBus: EventBus,
        renderMarkdownUseCase: RenderMarkdownUseCase,
        attachmentRepository: AttachmentRepository,
        renderDispatcher: CoroutineDispatcher
    ) : super() {
        this.fileRepository = fileRepository
        this.eventBus = eventBus
        this.renderMarkdownUseCase = renderMarkdownUseCase
        this.attachmentRepository = attachmentRepository
        this.renderDispatcher = renderDispatcher
    }

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val preview by lazy { PreviewCoordinator(viewModelScope, renderMarkdownUseCase, renderDispatcher) }
    val renderState: StateFlow<EditorRenderState> get() = preview.state

    private var currentFileUri: String? = null
    private var originalContent: String = ""
    private var generation = 0L
    private var renameInProgress = false
    private val pendingAttachmentUris = mutableSetOf<String>()

    private fun snapshot() = DocumentSnapshot(currentFileUri, generation, _uiState.value.revision, _uiState.value.content)

    /**
     * 加载文件内容
     */
    fun loadFile(fileUri: String) {
        if (currentFileUri == fileUri && _uiState.value.error == null) return
        currentFileUri = fileUri
        val identity = ++generation
        preview.clear()
        originalContent = ""
        pendingAttachmentUris.clear()
        _uiState.value = EditorUiState(isPreviewMode = _uiState.value.isPreviewMode)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            fileRepository.readFileContent(fileUri)
                .onSuccess { content ->
                    if (generation != identity || currentFileUri != fileUri) return@onSuccess
                    originalContent = content
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        textValue = TextFieldValue(content, TextRange(content.length)),
                        revision = _uiState.value.revision + 1,
                        fileName = UriHelper.extractFileName(fileUri)
                    )
                    // 预览模式首帧立即渲染，编辑模式不保留渲染树
                    requestRender(immediate = true)
                }
                .onError { error ->
                    if (generation != identity || currentFileUri != fileUri) return@onError
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.toUserMessage()
                    )
                }
        }
    }

    /**
     * 更新编辑内容
     */
    fun onContentChange(newContent: String) {
        onTextValueChange(TextFieldValue(newContent, TextRange(newContent.length)))
    }

    fun onTextValueChange(newValue: TextFieldValue) {
        val changed = newValue.text != _uiState.value.content
        _uiState.value = _uiState.value.copy(
            textValue = newValue,
            revision = _uiState.value.revision + if (changed) 1 else 0
        )
        // 只有预览模式需要渲染，编辑模式不常驻完整 Spanned
        if (changed && _uiState.value.isPreviewMode) {
            requestRender()
        }
    }

    fun addImage(sourceUri: String) {
        if (_uiState.value.isAddingImage) return
        val imageSnapshot = snapshot()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingImage = true, error = null)
            attachmentRepository.copyImage(sourceUri)
                .onSuccess { attachment ->
                    if (generation != imageSnapshot.generation) {
                        attachmentRepository.delete(attachment.uri)
                        return@onSuccess
                    }
                    if (_uiState.value.revision != imageSnapshot.revision) {
                        attachmentRepository.delete(attachment.uri)
                        _uiState.value = _uiState.value.copy(isAddingImage = false, error = "正文已变动，请重新选择图片插入位置")
                        return@onSuccess
                    }
                    pendingAttachmentUris += attachment.uri
                    insertAtSelection(attachment.toMarkdown())
                    _uiState.value = _uiState.value.copy(isAddingImage = false)
                }
                .onError { error ->
                    if (generation != imageSnapshot.generation) return@onError
                    _uiState.value = _uiState.value.copy(
                        isAddingImage = false,
                        error = error.toUserMessage()
                    )
                }
        }
    }

    /** 在当前选区插入文本（图片 markdown），光标移到插入内容之后 */
    private fun insertAtSelection(markdown: String) {
        val current = _uiState.value.textValue
        val start = current.selection.min
        val end = current.selection.max
        onTextValueChange(TextFieldValue(current.text.replaceRange(start, end, markdown), TextRange(start + markdown.length)))
    }

    /**
     * 保存文件
     */
    fun saveFile() {
        val uri = currentFileUri ?: return
        if (_uiState.value.isSaving || _uiState.value.isLoading || renameInProgress) return
        val saved = snapshot()
        val savedAttachments = pendingAttachmentUris.toSet()
        _uiState.value = _uiState.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            fileRepository.saveFileContent(uri, saved.content)
                .onSuccess {
                    if (generation != saved.generation) return@onSuccess
                    originalContent = saved.content
                    pendingAttachmentUris.removeAll(savedAttachments)
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    // 发送文件更新事件，通知文件列表刷新
                    eventBus.emit(AppEvent.FileUpdated(uri))
                }
                .onError { error ->
                    if (generation != saved.generation) return@onError
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = error.toUserMessage()
                    )
                }
        }
    }

    /**
     * 重命名当前文件。
     * SAF 重命名会生成新 URI，成功后必须同步更新 currentFileUri，
     * 否则后续保存会写到已失效的旧 URI
     */
    fun renameFile(newName: String, onComplete: (Boolean) -> Unit) {
        val uri = currentFileUri ?: return
        if (_uiState.value.isSaving || renameInProgress || _uiState.value.isLoading) { onComplete(false); return }
        val identity = generation
        renameInProgress = true
        viewModelScope.launch {
            fileRepository.renameFile(uri, newName)
                .onSuccess { newUri ->
                    renameInProgress = false
                    if (generation != identity) { onComplete(false); return@onSuccess }
                    currentFileUri = newUri
                    preview.clear()
                    requestRender(immediate = true)
                    _uiState.value = _uiState.value.copy(fileName = UriHelper.extractFileName(newUri))
                    // 通知文件列表刷新名称
                    eventBus.emit(AppEvent.FileUpdated(newUri))
                    onComplete(true)
                }
                .onError { error ->
                    renameInProgress = false
                    Timber.e("EditorViewModel: 重命名失败 uri=$uri, error=$error")
                    onComplete(false)
                }
        }
    }

    /**
     * 切换预览模式
     */
    fun togglePreviewMode() {
        setPreviewMode(!_uiState.value.isPreviewMode)
    }

    /**
     * 设置初始预览模式
     */
    fun setInitialPreviewMode(isPreview: Boolean) {
        setPreviewMode(isPreview)
    }

    private fun setPreviewMode(isPreview: Boolean) {
        _uiState.value = _uiState.value.copy(isPreviewMode = isPreview)
        if (isPreview) {
            requestRender(immediate = true)
        } else {
            preview.clear()
        }
    }

    /**
     * 检查是否有未保存的修改
     */
    fun hasUnsavedChanges(): Boolean {
        return uiState.value.content != originalContent || pendingAttachmentUris.isNotEmpty()
    }

    fun discardChanges(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val urisToDelete = pendingAttachmentUris.toList()
            pendingAttachmentUris.clear()
            var allDeleted = true
            urisToDelete.forEach { uri ->
                attachmentRepository.delete(uri).onError { error ->
                    allDeleted = false
                    Timber.e("EditorViewModel: 回滚附件失败，uri=$uri, error=$error")
                }
            }
            onComplete(allDeleted)
        }
    }

    fun retryPreview() = requestRender(immediate = true)

    fun applyPreview(view: android.widget.TextView, text: android.text.Spanned) = renderMarkdownUseCase.apply(view, text)

    private fun requestRender(immediate: Boolean = false) {
        if (!_uiState.value.isPreviewMode || _uiState.value.isLoading) return
        preview.request(snapshot(), immediate)
    }
}

