package com.bird.fiber.ui.screens.sidebar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.update.AppUpdateInfo
import com.bird.fiber.ui.screens.sidebar.heatmap.NoteHeatmapSection
import java.time.LocalDate

/**
 * 侧边栏（完整版本，带背景和外层容器）
 *
 * 显示所有笔记库，支持切换和添加
 */
@Composable
fun Sidebar(
    selectedLibraryId: String?,
    onLibrarySelected: (String) -> Unit,
    onAddLibrary: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageAttachments: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SidebarViewModel = hiltViewModel()
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        SidebarContent(
            selectedLibraryId = selectedLibraryId,
            onLibrarySelected = onLibrarySelected,
            onAddLibrary = onAddLibrary,
            onSettingsClick = onSettingsClick,
            onManageAttachments = onManageAttachments,
            viewModel = viewModel
        )
    }
}

/**
 * 侧边栏内容（不带外层容器）
 *
 * 用于 ModalDrawer 内部
 */
@Composable
fun SidebarContent(
    selectedLibraryId: String?,
    onLibrarySelected: (String) -> Unit,
    onAddLibrary: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onManageAttachments: (String) -> Unit = {},
    onHeatmapClick: () -> Unit = {},
    onHeatmapDayClick: (LocalDate) -> Unit = {},
    onAllNotesClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SidebarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    var updateDialogInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        SidebarHeader(onSettingsClick = onSettingsClick)

        // 库列表
        if (uiState.hasLibraries) {
            LibraryList(
                libraries = uiState.libraries,
                selectedLibraryId = selectedLibraryId,
                onLibraryClick = { libraryId ->
                    viewModel.switchLibrary(libraryId)
                    onLibrarySelected(libraryId)
                },
                onDeleteClick = { library ->
                    viewModel.deleteLibrary(library)
                },
                onManageAttachments = onManageAttachments,
                modifier = Modifier.weight(1f)
            )
        } else {
            // 空状态
            EmptyLibrariesState(
                onAddLibrary = onAddLibrary,
                modifier = Modifier.weight(1f)
            )
        }

        // 记录热力图
        NoteHeatmapSection(
            onDayClick = onHeatmapDayClick,
            onClick = onHeatmapClick
        )

        // 全部笔记入口
        AllNotesListItem(onClick = onAllNotesClick)

        // 有新版本时显示更新提示块（跟随"显示更新通知"开关）
        updateInfo?.let { update ->
            UpdateBanner(
                versionName = update.versionName,
                onClick = { updateDialogInfo = update }
            )
        }

        // 底部操作区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            FilledTonalButton(
                onClick = onAddLibrary,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加笔记库")
            }
        }
    }

    // 更新详情对话框：release 说明 + 下载按钮
    updateDialogInfo?.let { update ->
        UpdateDialog(update = update, onDismiss = { updateDialogInfo = null })
    }
}

/**
 * 新版本提示块：比笔记卡片更深的主题色块（primaryContainer），点击弹出更新详情
 */
@Composable
private fun UpdateBanner(
    versionName: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "有新版本 v$versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "点击查看更新内容",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 更新详情对话框：展示 release 说明，点击"下载"打开 APK 附件地址
 * （没有 APK 附件时退回到 release 网页）
 */
@Composable
private fun UpdateDialog(
    update: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("有新版本 v${update.versionName}") },
        text = {
            Text(
                text = update.releaseNotes.ifBlank { "暂无更新说明" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val url = update.downloadUrl ?: update.releasePageUrl
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    onDismiss()
                }
            ) {
                Text("下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun SidebarHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Fiber",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * 库列表
 */
@Composable
private fun LibraryList(
    libraries: List<LibraryEntity>,
    selectedLibraryId: String?,
    onLibraryClick: (String) -> Unit,
    onDeleteClick: (LibraryEntity) -> Unit,
    onManageAttachments: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(libraries) { library ->
            LibraryListItem(
                library = library,
                isSelected = library.id == selectedLibraryId,
                onClick = { onLibraryClick(library.id) },
                onDeleteClick = { onDeleteClick(library) },
                onManageAttachments = { onManageAttachments(library.id) }
            )
        }
    }
}

/**
 * 库列表项
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryListItem(
    library: LibraryEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onManageAttachments: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = library.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "更多",
                    modifier = Modifier.size(20.dp),
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("管理附件") },
                    onClick = {
                        showMenu = false
                        onManageAttachments()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                )
            }
        }
    }
}

/**
 * "全部笔记"入口（样式与库条目一致，无更多菜单）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllNotesListItem(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "全部笔记",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyLibrariesState(
    onAddLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "还没有笔记库",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onAddLibrary) {
                Text("添加第一个笔记库")
            }
        }
    }
}
