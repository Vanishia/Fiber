package com.bird.fiber.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.repository.FileRepository
import com.bird.fiber.data.model.toUserMessage
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.domain.usecase.RenderMarkdownUseCase
import com.bird.fiber.utils.UriHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(kotlinx.coroutines.FlowPreview::class)

/**
 * 编辑器 ViewModel
 */
@HiltViewModel
class EditorViewModel : ViewModel {

    private val fileRepository: FileRepository
    private val eventBus: EventBus
    private val renderMarkdownUseCase: RenderMarkdownUseCase
    private val renderDispatcher: CoroutineDispatcher

    @Inject
    constructor(
        fileRepository: FileRepository,
        eventBus: EventBus,
        renderMarkdownUseCase: RenderMarkdownUseCase
    ) : this(fileRepository, eventBus, renderMarkdownUseCase, Dispatchers.Default)

    internal constructor(
        fileRepository: FileRepository,
        eventBus: EventBus,
        renderMarkdownUseCase: RenderMarkdownUseCase,
        renderDispatcher: CoroutineDispatcher
    ) : super() {
        this.fileRepository = fileRepository
        this.eventBus = eventBus
        this.renderMarkdownUseCase = renderMarkdownUseCase
        this.renderDispatcher = renderDispatcher
    }

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _renderState = MutableStateFlow(EditorRenderState())
    val renderState: StateFlow<EditorRenderState> = _renderState.asStateFlow()

    private var currentFileUri: String? = null
    private var originalContent: String = ""  // 保存原始内容，用于判断是否有修改

    private val renderRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            renderRequests
                .debounce(400)
                .collect { content -> performRender(content) }
        }
    }

    /**
     * 加载文件内容
     */
    fun loadFile(fileUri: String) {
        if (currentFileUri == fileUri && !_uiState.value.isLoading) {
            return
        }
        currentFileUri = fileUri
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = fileRepository.readFileContent(fileUri)) {
                is com.bird.fiber.data.model.FileResult.Success -> {
                    val fileName = UriHelper.extractFileName(fileUri)

                    // 保存原始内容，用于判断是否有修改
                    originalContent = result.data

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        content = result.data,
                        fileName = fileName
                    )
                    // 初始加载直接渲染，不经过 debounce，避免启动时空白延迟
                    performRender(result.data)
                }
                is com.bird.fiber.data.model.FileResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error.toUserMessage()
                    )
                }
                is com.bird.fiber.data.model.FileResult.Loading -> {
                    // Loading 状态已经在上面设置了，这里不需要处理
                }
            }
        }
    }

    /**
     * 更新编辑内容
     */
    fun onContentChange(newContent: String) {
        _uiState.value = _uiState.value.copy(content = newContent)
        // 打字期间的渲染经过 debounce，避免频繁解析大文件
        renderMarkdown(newContent)
    }

    /**
     * 保存文件
     */
    fun saveFile() {
        val uri = currentFileUri ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            when (val result = fileRepository.saveFileContent(uri, _uiState.value.content)) {
                is com.bird.fiber.data.model.FileResult.Success -> {
                    // 更新原始内容为当前内容
                    originalContent = _uiState.value.content
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    // 发送文件更新事件，通知文件列表刷新
                    eventBus.emit(AppEvent.FileUpdated(uri))
                }
                is com.bird.fiber.data.model.FileResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = result.error.toUserMessage()
                    )
                }
                is com.bird.fiber.data.model.FileResult.Loading -> {
                    // 不需要处理
                }
            }
        }
    }

    /**
     * 切换预览模式
     */
    fun togglePreviewMode() {
        _uiState.value = _uiState.value.copy(isPreviewMode = !_uiState.value.isPreviewMode)
    }

    /**
     * 检查是否有未保存的修改
     */
    fun hasUnsavedChanges(): Boolean {
        return uiState.value.content != originalContent
    }

    /**
     * 设置初始预览模式
     */
    fun setInitialPreviewMode(isPreview: Boolean) {
        _uiState.value = _uiState.value.copy(isPreviewMode = isPreview)
    }

    private fun renderMarkdown(content: String) {
        renderRequests.tryEmit(content)
    }

    private suspend fun performRender(content: String) {
        if (content.isBlank()) {
            _renderState.value = EditorRenderState()
            return
        }

        _renderState.value = _renderState.value.copy(isRendering = true)
        try {
            val rendered = withContext(renderDispatcher) {
                renderMarkdownUseCase.render(content)
            }
            _renderState.value = EditorRenderState(
                renderedMarkdown = rendered,
                isRendering = false
            )
        } catch (_: Exception) {
            _renderState.value = _renderState.value.copy(isRendering = false)
        }
    }
}
