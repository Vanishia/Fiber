package com.bird.fiber.ui.screens.tags

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.LoadState
import com.bird.fiber.ui.screens.notelist.NoteList
import com.bird.fiber.ui.screens.notelist.NoteListActionsViewModel
import com.bird.fiber.ui.screens.notelist.NoteListActionEvent
import com.bird.fiber.ui.screens.notelist.NoteListItemActionOverlays
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors
import com.bird.fiber.data.model.MarkdownFileMeta

@Composable
fun TagNotesScreen(
    onBackClick: () -> Unit,
    onFileClick: (String, Boolean) -> Unit,
    viewModel: TagNotesViewModel = hiltViewModel(),
    actions: NoteListActionsViewModel = hiltViewModel()
) {
    val count by viewModel.count.collectAsStateWithLifecycle()
    val displayPath by viewModel.displayPath.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val notes = viewModel.pager.collectAsLazyPagingItems()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var selectedFile by remember { mutableStateOf<MarkdownFileMeta?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MarkdownFileMeta?>(null) }
    LaunchedEffect(actions) {
        actions.events.collect { event ->
            when (event) {
                is NoteListActionEvent.OpenFile -> onFileClick(event.uri, event.edit)
                is NoteListActionEvent.Message -> snackbar.showSnackbar(event.text)
                is NoteListActionEvent.CopyContent -> {
                    clipboard.setText(AnnotatedString(event.content))
                    snackbar.showSnackbar("内容已复制")
                }
            }
        }
    }
    Scaffold(
        containerColor = LocalFiberSurfaceColors.current.pageBackground,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    Column(Modifier.weight(1f).padding(8.dp)) {
                        Text("#$displayPath", style = MaterialTheme.typography.titleMedium)
                        Text("$count 篇笔记" + if (progress.visible) " · 标签数据仍在整理" else "",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (notes.itemCount == 0 && notes.loadState.refresh is LoadState.Loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (notes.itemCount == 0 && notes.loadState.refresh is LoadState.Error) {
                Column(Modifier.padding(24.dp)) {
                    Text("加载标签笔记失败")
                    TextButton(onClick = notes::retry) { Text("重试") }
                }
            } else if (notes.itemCount == 0 && notes.loadState.refresh is LoadState.NotLoading) {
                Text(if (progress.visible) "标签数据仍在整理，笔记将陆续出现" else "该标签下暂无笔记",
                    Modifier.padding(24.dp))
            } else {
                NoteList(
                    lazyPagingItems = notes,
                    onFileClick = actions::openFile,
                    onLongPress = { selectedFile = it; showMenu = true },
                    onDeleteRequest = { pendingDelete = it },
                    topPadding = 0.dp,
                    showLibraryName = true
                )
            }
        }
    }
    NoteListItemActionOverlays(
        selectedFile = selectedFile,
        showMenu = showMenu,
        onDismissMenu = { showMenu = false },
        directDeleteFile = pendingDelete,
        onDismissDirectDelete = { pendingDelete = null },
        actions = actions
    )
}
