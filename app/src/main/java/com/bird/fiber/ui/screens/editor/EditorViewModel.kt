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
import com.bird.fiber.utils.MarkdownChunker
import com.bird.fiber.utils.UriHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * 编辑器 ViewModel
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
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

    private val _renderState = MutableStateFlow(EditorRenderState())
    val renderState: StateFlow<EditorRenderState> = _renderState.asStateFlow()

    private var currentFileUri: String? = null
    private var originalContent: String = ""  // 保存原始内容，用于判断是否有修改
    private val pendingAttachmentUris = mutableSetOf<String>()

    private val renderRequests = MutableStateFlow<RenderRequest?>(null)

    init {
        viewModelScope.launch {
            renderRequests
                .debounce { request -> if (request == null || request.immediate) 0L else RENDER_DEBOUNCE_MS }
                .collectLatest { request ->
                    if (request == null) {
                        _renderState.value = EditorRenderState()
                    } else {
                        performRender(request)
                    }
                }
        }
    }

    /**
     * 加载文件内容
     */
    fun loadFile(fileUri: String) {
        if (currentFileUri == fileUri && !_uiState.value.isLoading) return
        currentFileUri = fileUri
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            fileRepository.readFileContent(fileUri)
                .onSuccess { content ->
                    originalContent = content
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        textValue = TextFieldValue(content, TextRange(content.length)),
                        fileName = UriHelper.extractFileName(fileUri)
                    )
                    // 预览模式首帧立即渲染，编辑模式不保留渲染树
                    requestRender(content, immediate = true)
                }
                .onError { error ->
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
        _uiState.value = _uiState.value.copy(textValue = newValue)
        // 只有预览模式需要渲染，编辑模式不常驻完整 Spanned
        if (_uiState.value.isPreviewMode) {
            requestRender(newValue.text)
        }
    }

    fun addImage(sourceUri: String) {
        if (_uiState.value.isAddingImage) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingImage = true, error = null)
            attachmentRepository.copyImage(sourceUri)
                .onSuccess { attachment ->
                    pendingAttachmentUris += attachment.uri
                    insertAtSelection(attachment.toMarkdown())
                    _uiState.value = _uiState.value.copy(isAddingImage = false)
                }
                .onError { error ->
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
        val start = current.selection.min.coerceIn(0, current.text.length)
        val end = current.selection.max.coerceIn(start, current.text.length)
        val updatedText = current.text.replaceRange(start, end, markdown)
        _uiState.value = _uiState.value.copy(
            textValue = TextFieldValue(updatedText, TextRange(start + markdown.length))
        )
        if (_uiState.value.isPreviewMode) {
            requestRender(updatedText)
        }
    }

    /**
     * 保存文件
     */
    fun saveFile() {
        val uri = currentFileUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            fileRepository.saveFileContent(uri, _uiState.value.content)
                .onSuccess {
                    // 更新原始内容为当前内容
                    originalContent = _uiState.value.content
                    pendingAttachmentUris.clear()
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    // 发送文件更新事件，通知文件列表刷新
                    eventBus.emit(AppEvent.FileUpdated(uri))
                }
                .onError { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = error.toUserMessage()
                    )
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
            requestRender(_uiState.value.content, immediate = true)
        } else {
            renderRequests.value = null
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

    private fun requestRender(content: String, immediate: Boolean = false) {
        if (!_uiState.value.isPreviewMode) return
        renderRequests.value = RenderRequest(
            content = content,
            fileUri = currentFileUri,
            immediate = immediate,
            sequence = nextRenderSequence++
        )
    }

    private suspend fun performRender(request: RenderRequest) {
        if (request.content.isBlank()) {
            _renderState.value = EditorRenderState()
            return
        }

        _renderState.value = _renderState.value.copy(isRendering = true)
        try {
            val rendered = withContext(renderDispatcher) {
                renderContent(request)
            }
            _renderState.value = EditorRenderState(
                renderedMarkdown = rendered,
                isRendering = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _renderState.value = _renderState.value.copy(isRendering = false)
        }
    }

    /**
     * 渲染预览内容。超过阈值的超大正文按块渲染再拼接，
     * 把单次 Markwon 解析的内存峰值摊薄，块间也让出协程避免长时间占用
     */
    private suspend fun renderContent(request: RenderRequest): android.text.Spanned {
        if (request.content.length <= LARGE_RENDER_THRESHOLD_CHARS) {
            return renderMarkdownUseCase.render(request.content, request.fileUri)
        }
        val chunks = MarkdownChunker.split(request.content, RENDER_CHUNK_CHARS)
        if (chunks.size <= 1) {
            return renderMarkdownUseCase.render(request.content, request.fileUri)
        }
        val parts = chunks.map { chunk ->
            currentCoroutineContext().ensureActive()
            renderMarkdownUseCase.render(chunk, request.fileUri)
        }
        return renderMarkdownUseCase.concat(parts)
    }

    private var nextRenderSequence = 0L

    private data class RenderRequest(
        val content: String,
        val fileUri: String?,
        val immediate: Boolean,
        val sequence: Long
    )

    private companion object {
        const val RENDER_DEBOUNCE_MS = 400L

        /** 超过约 1MB 的正文走分块渲染 */
        const val LARGE_RENDER_THRESHOLD_CHARS = 1_000_000

        /** 分块渲染时每块的目标大小（约 250KB） */
        const val RENDER_CHUNK_CHARS = 250_000
    }
}
