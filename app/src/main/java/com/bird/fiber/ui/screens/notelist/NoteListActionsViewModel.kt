package com.bird.fiber.ui.screens.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.toTarget
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.data.model.toUserMessage
import com.bird.fiber.data.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 跨库浏览中的条目操作使用原 URI；只有打开编辑器时才切换所属库。 */
@HiltViewModel
class NoteListActionsViewModel @Inject constructor(
    private val files: FileRepository,
    private val libraries: LibraryRepository,
    private val eventBus: EventBus
) : ViewModel() {
    val activeLibrary = libraries.getActiveLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val eventChannel = Channel<NoteListActionEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var working = false

    fun openFile(file: MarkdownFileMeta, edit: Boolean) = action {
        file.libraryId?.let { libraryId ->
            checkNotNull(libraries.getLibraryById(libraryId)) { "笔记所属库已不存在" }
            if (libraries.getActiveLibrary().first()?.id != libraryId) {
                libraries.switchLibrary(libraryId)
            }
        }
        eventChannel.send(NoteListActionEvent.OpenFile(file.uri, edit))
    }

    fun copyFile(file: MarkdownFileMeta) = action {
        when (val result = files.readFileContent(file.uri)) {
            is FileResult.Success -> eventChannel.send(NoteListActionEvent.CopyContent(result.data))
            is FileResult.Error -> message(result.error.toUserMessage())
            FileResult.Loading -> Unit
        }
    }

    fun deleteFile(file: MarkdownFileMeta) = action {
        when (val result = files.deleteFile(file.uri)) {
            is FileResult.Success -> {
                eventBus.emit(AppEvent.FileDeleted(file.uri))
                message("「${file.name.removeSuffix(".md")}」已删除")
            }
            is FileResult.Error -> message(result.error.toUserMessage())
            FileResult.Loading -> Unit
        }
    }

    fun renameFile(file: MarkdownFileMeta, name: String) = action {
        when (val result = files.renameFile(file.uri, name)) {
            is FileResult.Success -> {
                eventBus.emit(AppEvent.FileUpdated(result.data))
                message("已重命名为「$name」")
            }
            is FileResult.Error -> message(result.error.toUserMessage())
            FileResult.Loading -> Unit
        }
    }

    // 捕获打开创建对话框时显示的库，避免操作途中切库造成写错位置。
    fun createFile(library: LibraryEntity, name: String) = action {
        when (val result = files.createMarkdownFile(library.toTarget(), name)) {
            is FileResult.Success -> {
                eventBus.emit(AppEvent.FileCreated(result.data.uri))
                if (libraries.getActiveLibrary().first()?.id != library.id) {
                    libraries.switchLibrary(library.id)
                }
                eventChannel.send(NoteListActionEvent.OpenFile(result.data.uri, true))
            }
            is FileResult.Error -> message(result.error.toUserMessage())
            FileResult.Loading -> Unit
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (working) return
        working = true
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message(error.message ?: "操作失败，请重试")
            } finally {
                working = false
            }
        }
    }

    private suspend fun message(text: String) {
        eventChannel.send(NoteListActionEvent.Message(text))
    }
}

sealed interface NoteListActionEvent {
    data class OpenFile(val uri: String, val edit: Boolean) : NoteListActionEvent
    data class CopyContent(val content: String) : NoteListActionEvent
    data class Message(val text: String) : NoteListActionEvent
}
