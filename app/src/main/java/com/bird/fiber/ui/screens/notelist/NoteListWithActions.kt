package com.bird.fiber.ui.screens.notelist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.ui.screens.filelist.components.CreateFileDialog
import com.bird.fiber.ui.screens.main.QuickNoteBar
import com.bird.fiber.ui.screens.quicknote.QuickNoteViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

/** 每日与全部笔记共享主页的菜单、对话框和记录栏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteListWithActions(
    viewModel: NoteListViewModel,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFileClick: (String, Boolean) -> Unit,
    onAddLibrary: () -> Unit,
    date: LocalDate? = null,
    onBackClick: () -> Unit = {},
    actions: NoteListActionsViewModel = hiltViewModel(),
    quickNotes: QuickNoteViewModel = hiltViewModel()
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val emptyText by viewModel.emptyText.collectAsStateWithLifecycle()
    val library by actions.activeLibrary.collectAsStateWithLifecycle()
    val quickNote by quickNotes.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedFile by remember { mutableStateOf<MarkdownFileMeta?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var deleteFile by remember { mutableStateOf<MarkdownFileMeta?>(null) }
    var createInLibrary by remember { mutableStateOf<LibraryEntity?>(null) }
    var createName by remember { mutableStateOf("") }
    val create: () -> Unit = {
        val target = library
        if (target == null) onAddLibrary() else {
            createName = ""
            createInLibrary = target
        }
    }

    LaunchedEffect(actions) {
        actions.events.collect { event ->
            when (event) {
                is NoteListActionEvent.OpenFile -> onFileClick(event.uri, event.edit)
                is NoteListActionEvent.CopyContent -> {
                    clipboard.setText(AnnotatedString(event.content))
                    scope.launch { snackbar.showSnackbar("内容已复制") }
                }
                is NoteListActionEvent.Message -> scope.launch { snackbar.showSnackbar(event.text) }
            }
        }
    }
    LaunchedEffect(quickNotes) {
        quickNotes.events.collect {
            focus.clearFocus()
            // 回顾历史日时新记录仍按实际创建时间归档，并明确反馈保存位置。
            scope.launch { snackbar.showSnackbar("笔记已保存到当前库") }
        }
    }

    NoteListScreen(
        title = title,
        emptyText = emptyText,
        pager = viewModel.pager,
        onMenuClick = onMenuClick,
        onSearchClick = onSearchClick,
        onFileClick = actions::openFile,
        date = date,
        onBackClick = onBackClick,
        onDayChange = viewModel::showDate,
        onCreateClick = create,
        onLongPress = { selectedFile = it; showMenu = true },
        onDeleteRequest = { deleteFile = it },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = library?.let { "记录到：${it.name}" } ?: "请先添加笔记库",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = create) {
                        Icon(Icons.Default.Create, contentDescription = "新建笔记")
                    }
                }
                QuickNoteBar(
                    content = quickNote.content,
                    attachments = quickNote.attachments,
                    isSaving = quickNote.isSaving,
                    isAddingImage = quickNote.isAddingImage,
                    error = quickNote.error,
                    onContentChange = quickNotes::onContentChange,
                    onImageSelected = quickNotes::addImage,
                    onRemoveAttachment = quickNotes::removeAttachment,
                    onDismissError = quickNotes::clearError,
                    onSaveClick = quickNotes::saveNote
                )
            }
        }
    )

    NoteListItemActionOverlays(
        selectedFile = selectedFile,
        showMenu = showMenu,
        onDismissMenu = { showMenu = false },
        directDeleteFile = deleteFile,
        onDismissDirectDelete = { deleteFile = null },
        actions = actions
    )
    createInLibrary?.let { target ->
        CreateFileDialog(
            fileName = createName,
            onFileNameChange = { createName = it },
            onDismiss = { createInLibrary = null },
            onConfirm = { actions.createFile(target, createName); createInLibrary = null }
        )
    }
}
