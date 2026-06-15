package com.bird.fiber.ui.screens.filelist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.ui.screens.filelist.components.CreateFileDialog
import com.bird.fiber.ui.screens.filelist.components.DeleteConfirmDialog
import com.bird.fiber.ui.screens.filelist.components.FileListContent
import com.bird.fiber.ui.screens.filelist.components.FileListSkeleton
import com.bird.fiber.ui.screens.filelist.components.NoFolderSelected
import com.bird.fiber.ui.screens.filelist.components.RenameFileDialog
import timber.log.Timber

/**
 * 文件列表页面
 *
 * 这个UI完全不依赖具体的ViewModel实现
 * 只要ViewModel提供的UiState接口不变，UI可以随意调整
 *
 * 现在使用 Paging 3 实现流式加载
 */
@Composable
fun FileListScreen(
    onFileClick: (String, Boolean) -> Unit = { _, _ -> },
    onSelectFolder: () -> Unit = {},
    onChangeFolder: () -> Unit = {},
    onCopyContent: (String) -> Unit = {},
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    currentLibraryName: String? = null,
    onListScroll: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FileListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 获取响应式 Paging 数据（当库或搜索词变化时自动更新）
    val lazyPagingItems = viewModel.pager.collectAsLazyPagingItems()

    LaunchedEffect(
        uiState.showNoFolderState,
        uiState.isSyncing,
        uiState.hasResolvedInitialLibrary,
        uiState.isFolderSelected,
        lazyPagingItems.itemCount,
        lazyPagingItems.loadState.refresh
    ) {
        val branch = when {
            uiState.showNoFolderState -> "NoFolderSelected"
            uiState.isSyncing -> "Syncing"
            else -> "FileListContent"
        }
        Timber.d(
            "StartupTrace: FileListScreen branch=$branch resolved=${uiState.hasResolvedInitialLibrary} folderSelected=${uiState.isFolderSelected} syncing=${uiState.isSyncing} itemCount=${lazyPagingItems.itemCount} refresh=${lazyPagingItems.loadState.refresh::class.simpleName}"
        )
    }

    // 创建文件对话框状态
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var createFileName by remember { mutableStateOf("") }

    // 删除确认对话框状态
    var fileToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 重命名对话框状态
    var fileToRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameFileName by remember { mutableStateOf("") }

    // Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 监听文件创建成功事件，自动进入编辑器
    LaunchedEffect(Unit) {
        viewModel.fileCreatedEvents.collect { file ->
            onFileClick(file.uri, true)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        when {
            !uiState.hasResolvedInitialLibrary -> {
                FileListSkeleton(modifier = Modifier.fillMaxSize())
            }
            // 未选择文件夹
            uiState.showNoFolderState -> {
                NoFolderSelected(
                    onSelectFolder = onSelectFolder
                )
            }

            // 正在同步文件
            uiState.isSyncing -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在同步文件...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 显示文件列表（使用 Paging）
            else -> {
                FileListContent(
                    lazyPagingItems = lazyPagingItems,
                    onFileClick = { fileUri, isEditMode ->
                        onFileClick(fileUri, isEditMode)
                    },
                    searchQuery = uiState.searchQuery,
                    onDeleteRequest = { fileName, fileUri ->
                        fileToDelete = fileName to fileUri
                    },
                    onEditRequest = { fileUri ->
                        // 向右滑动编辑直接进入编辑页面（编辑模式）
                        onFileClick(fileUri, true)
                    },
                    onCopyRequest = { fileName, fileUri ->
                        viewModel.readFileContent(fileUri) { result ->
                            if (result is FileResult.Success) {
                                onCopyContent(result.data)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "「${fileName}」内容已复制",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    },
                    onRenameRequest = { fileName, fileUri ->
                        fileToRename = fileName to fileUri
                        renameFileName = fileName
                    },
                    onMenuClick = onMenuClick,
                    onSearchClick = onSearchClick,
                    onCreateClick = { showCreateFileDialog = true },
                    currentLibraryName = currentLibraryName,
                    onListScroll = onListScroll,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 错误提示
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = {
                        viewModel.onEvent(FileListEvent.ClearError)
                    }) {
                        Text("关闭")
                    }
                }
            ) {
                Text(error)
            }
        }

        // 创建文件对话框
        if (showCreateFileDialog) {
            CreateFileDialog(
                fileName = createFileName,
                onFileNameChange = { createFileName = it },
                onDismiss = { showCreateFileDialog = false },
                onConfirm = {
                    if (createFileName.isNotBlank()) {
                        viewModel.onEvent(FileListEvent.CreateFile(createFileName))
                        showCreateFileDialog = false
                        createFileName = ""
                    }
                }
            )
        }

        // 删除确认对话框
        fileToDelete?.let { (fileName, fileUri) ->
            DeleteConfirmDialog(
                fileName = fileName,
                onDismiss = {
                    fileToDelete = null
                },
                onConfirm = {
                    viewModel.onEvent(FileListEvent.DeleteFile(fileUri))
                    fileToDelete = null
                    // 显示删除成功 Snackbar
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "「${fileName}」已删除",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
        }

        // 重命名对话框
        fileToRename?.let { (fileName, fileUri) ->
            RenameFileDialog(
                currentName = fileName,
                newName = renameFileName,
                onNewNameChange = { renameFileName = it },
                onDismiss = {
                    fileToRename = null
                    renameFileName = ""
                },
                onConfirm = {
                    if (renameFileName.isNotBlank() && renameFileName != fileName) {
                        val finalNewName = renameFileName
                        viewModel.onEvent(FileListEvent.RenameFile(fileUri, finalNewName))
                        fileToRename = null
                        renameFileName = ""
                        // 显示重命名成功 Snackbar
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "已重命名为「${finalNewName}」",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }
            )
        }
        }
    }
}
