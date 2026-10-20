package com.bird.fiber.ui.screens.editor

import com.bird.fiber.domain.markdown.DocumentSnapshot
import com.bird.fiber.domain.usecase.RenderMarkdownUseCase
import com.bird.fiber.utils.MarkdownChunker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** 只接受不可变快照，不持有或修改编辑原文。编辑模式立即释放完整预览。 */
@OptIn(FlowPreview::class)
internal class PreviewCoordinator(
    scope: CoroutineScope,
    private val renderer: RenderMarkdownUseCase,
    private val dispatcher: CoroutineDispatcher
) {
    private data class Request(val document: DocumentSnapshot, val immediate: Boolean, val sequence: Long)
    private val requests = MutableStateFlow<Request?>(null)
    private val mutableState = MutableStateFlow(EditorRenderState())
    val state = mutableState.asStateFlow()
    private var sequence = 0L

    init {
        scope.launch {
            requests.debounce { if (it == null || it.immediate) 0L else 400L }
                .collectLatest { request ->
                    if (request == null) return@collectLatest
                    if (requests.value != request) return@collectLatest
                    mutableState.value = mutableState.value.copy(isRendering = true, error = null)
                    try {
                        val rendered = withContext(dispatcher) {
                            val document = request.document
                            if (document.content.isBlank()) null else {
                                val chunks = if (document.content.length > 1_000_000) {
                                    MarkdownChunker.split(document.content, 250_000)
                                } else listOf(document.content)
                                val parts = chunks.map { chunk ->
                                    currentCoroutineContext().ensureActive()
                                    renderer.render(document.copy(content = chunk))
                                }
                                if (parts.size == 1) parts.single() else renderer.concat(parts)
                            }
                        }
                        if (requests.value == request) {
                            mutableState.value = EditorRenderState(renderedMarkdown = rendered, document = request.document)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Timber.e(error, "预览渲染失败")
                        if (requests.value == request) {
                            mutableState.value = EditorRenderState(document = request.document, error = "预览生成失败，请重试")
                        }
                    }
                }
        }
    }

    fun request(document: DocumentSnapshot, immediate: Boolean = false) {
        requests.value = Request(document, immediate, sequence++)
    }

    fun clear() {
        requests.value = null
        mutableState.value = EditorRenderState()
    }
}
