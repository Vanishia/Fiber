package com.bird.fiber.ui.screens.sidebar

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.update.AppUpdateInfo
import com.bird.fiber.ui.screens.sidebar.heatmap.NoteHeatmapSection
import com.bird.fiber.ui.screens.sidebar.heatmap.HeatmapViewModel
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

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
    onTagClick: (String) -> Unit = {},
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
            onTagClick = onTagClick,
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
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {},
    onManageAttachments: (String) -> Unit = {},
    onHeatmapClick: () -> Unit = {},
    onHeatmapDayClick: (LocalDate) -> Unit = {},
    onAllNotesClick: () -> Unit = {},
    onTagClick: (String) -> Unit = {},
    viewModel: SidebarViewModel = hiltViewModel(),
    heatmapViewModel: HeatmapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val heatmapState by heatmapViewModel.uiState.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsState()
    var updateDialogInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddTag by remember { mutableStateOf(false) }
    val tags by viewModel.tags.collectAsState(initial = emptyList())
    val progress by viewModel.tagProgress.collectAsState()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        SidebarHeader(
            onSettingsClick = onSettingsClick,
            easterEggMessages = if (heatmapState.isLoaded) listOf(
                "思想的原材料",
                "已经记录了${heatmapState.recordedDays}天",
                "今天写下${heatmapState.todayChars}字"
            ) else listOf("思想的原材料")
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconToggleButton(checked = !uiState.showTags, onCheckedChange = { viewModel.selectTagView(false) }) {
                Icon(Icons.Default.Folder, "笔记库", tint = if (!uiState.showTags) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconToggleButton(checked = uiState.showTags, onCheckedChange = { viewModel.selectTagView(true) }) {
                Icon(Icons.Rounded.Numbers, "标签", tint = if (uiState.showTags) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { showAddMenu = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "添加", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("添加笔记库") },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            onAddLibrary()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加标签") },
                        leadingIcon = { Icon(Icons.Rounded.Numbers, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            showAddTag = true
                        }
                    )
                }
            }
        }

        // 库列表
        if (uiState.showTags) {
            TagList(tags, progress.visible, onTagClick, viewModel::deleteTag, Modifier.weight(1f))
        } else if (uiState.hasLibraries) {
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
            onClick = onHeatmapClick,
            viewModel = heatmapViewModel
        )

        // 全部笔记入口
        AllNotesListItem(onClick = onAllNotesClick, onStatisticsClick = onHeatmapClick)

        // 有新版本时显示更新提示块（跟随"显示更新通知"开关）
        updateInfo?.let { update ->
            UpdateBanner(
                versionName = update.versionName,
                onClick = { updateDialogInfo = update }
            )
        }

    }

    if (showAddTag) {
        AddTagDialog(onDismiss = { showAddTag = false }, onAdd = viewModel::addTag,
            onAdded = { path -> showAddTag = false; viewModel.selectTagView(true); onTagClick(path) })
    }
    uiState.error?.let { error ->
        AlertDialog(onDismissRequest = viewModel::clearError, text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } })
    }

    // 更新详情对话框：release 说明 + 下载按钮
    updateDialogInfo?.let { update ->
        UpdateDialog(
            update = update,
            onDismiss = { updateDialogInfo = null },
            onDownload = viewModel::downloadUpdate
        )
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
 * 更新详情对话框：有 APK 时直接下载，没有附件时明确提供发布页入口。
 */
@Composable
internal fun UpdateDialog(
    update: AppUpdateInfo,
    onDismiss: () -> Unit,
    onDownload: suspend (AppUpdateInfo) -> Long
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.8f
    val hasApk = !update.downloadUrl.isNullOrBlank()
    var isStartingDownload by remember(update) { mutableStateOf(false) }
    var actionError by remember(update) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isStartingDownload) onDismiss() },
        modifier = Modifier.heightIn(max = maxDialogHeight),
        title = { Text("有新版本 v${update.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = update.releaseNotes.ifBlank { "暂无更新说明" },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                actionError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!isStartingDownload) {
                        isStartingDownload = true
                        actionError = null
                        scope.launch {
                            try {
                                if (hasApk) {
                                    onDownload(update)
                                    Toast.makeText(
                                        context,
                                        "已开始下载，可在通知栏查看进度",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(update.releasePageUrl))
                                    )
                                }
                                onDismiss()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.w(e, "启动更新下载或打开发布页失败")
                                actionError = if (hasApk) {
                                    "无法开始下载，请稍后重试"
                                } else {
                                    "无法打开发布页，请稍后重试"
                                }
                            } finally {
                                isStartingDownload = false
                            }
                        }
                    }
                },
                enabled = !isStartingDownload
            ) {
                Text(if (isStartingDownload) "正在处理…" else if (hasApk) "下载" else "查看发布页")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isStartingDownload) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SidebarHeader(
    onSettingsClick: () -> Unit,
    easterEggMessages: List<String> = listOf("思想的原材料")
) {
    var revealCount by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val opacity = remember { Animatable(0f) }

    LaunchedEffect(revealCount) {
        if (revealCount == 0) return@LaunchedEffect
        message = easterEggMessages.randomOrNull() ?: "思想的原材料"
        opacity.snapTo(0f)
        opacity.animateTo(1f, tween(200))
        delay(2500)
        opacity.animateTo(0f, tween(300))
        message = null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 26.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Fiber",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onDoubleClick = { revealCount++ }
                )
        )

        Box(modifier = Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
            message?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(opacity.value)
                )
            }
        }

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
 * "全部笔记"入口，右侧提供独立的统计入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllNotesListItem(onClick: () -> Unit, onStatisticsClick: () -> Unit) {
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onStatisticsClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.BarChart,
                    contentDescription = "笔记统计",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
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
