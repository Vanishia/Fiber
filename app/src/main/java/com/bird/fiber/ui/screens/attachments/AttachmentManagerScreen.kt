package com.bird.fiber.ui.screens.attachments

import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.load
import com.bird.fiber.data.model.ManagedAttachment
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AttachmentManagerScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AttachmentManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.isSelecting) { viewModel.clearSelection() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (uiState.isSelecting) "已选择 ${uiState.selectedUris.size} 项" else "管理附件")
                        if (!uiState.isSelecting && uiState.libraryName.isNotBlank()) {
                            Text(
                                text = uiState.libraryName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = if (uiState.isSelecting) viewModel::clearSelection else onBackClick
                    ) {
                        Icon(
                            imageVector = if (uiState.isSelecting) {
                                Icons.Default.Close
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = if (uiState.isSelecting) "退出多选" else "返回"
                        )
                    }
                },
                actions = {
                    if (!uiState.isSelecting) {
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "筛选：${uiState.filter.label}"
                                )
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false }
                            ) {
                                AttachmentFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.label) },
                                        onClick = {
                                            viewModel.setFilter(filter)
                                            showFilterMenu = false
                                        },
                                        leadingIcon = if (filter == uiState.filter) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.isSelecting) {
                FloatingActionButton(
                    onClick = { showDeleteConfirmation = true },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = "删除所选附件")
                    }
                }
            }
        }
    ) { paddingValues ->
        AttachmentManagerContent(
            uiState = uiState,
            onRetry = viewModel::loadAttachments,
            onAttachmentClick = { attachment ->
                if (uiState.isSelecting) viewModel.toggleSelection(attachment)
            },
            onAttachmentLongClick = viewModel::startSelection,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("删除所选图片？") },
            text = { Text("将永久删除 ${uiState.selectedUris.size} 张未关联图片，此操作无法撤销。") },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("删除")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentManagerContent(
    uiState: AttachmentManagerUiState,
    onRetry: () -> Unit,
    onAttachmentClick: (ManagedAttachment) -> Unit,
    onAttachmentLongClick: (ManagedAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        uiState.error != null -> Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiState.error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
        uiState.filteredAttachments.isEmpty() -> EmptyAttachments(
            filter = uiState.filter,
            modifier = modifier
        )
        else -> LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = modifier,
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp
        ) {
            items(uiState.filteredAttachments, key = { it.uri }) { attachment ->
                AttachmentCard(
                    attachment = attachment,
                    selected = attachment.uri in uiState.selectedUris,
                    selectionMode = uiState.isSelecting,
                    onClick = { onAttachmentClick(attachment) },
                    onLongClick = { onAttachmentLongClick(attachment) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentCard(
    attachment: ManagedAttachment,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(attachment.aspectRatio)
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AttachmentImage(
                attachment = attachment,
                modifier = Modifier.fillMaxSize()
            )
            if (selectionMode && attachment.isReferenced) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                )
            }
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "已选择",
                        modifier = Modifier.padding(4.dp).size(18.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .zIndex(1f)
                .fillMaxWidth()
                .background(containerColor)
                .padding(10.dp)
        ) {
            Text(
                text = attachment.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.size(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (attachment.isReferenced) Icons.Default.Link else Icons.Default.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (attachment.isReferenced) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (attachment.isReferenced) {
                        "关联 ${attachment.referencedBy.size} 个文件"
                    } else {
                        "未关联"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "${formatFileSize(attachment.size)} · ${formatDate(attachment.lastModified)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AttachmentImage(
    attachment: ManagedAttachment,
    modifier: Modifier = Modifier
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(placeholderColor)
            }
        },
        update = { imageView ->
            imageView.load(Uri.parse(attachment.uri)) {
                crossfade(true)
            }
        },
        modifier = modifier.clipToBounds()
    )
}

@Composable
private fun EmptyAttachments(filter: AttachmentFilter, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(48.dp).alpha(0.7f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = when (filter) {
                    AttachmentFilter.ALL -> "还没有附件"
                    AttachmentFilter.REFERENCED -> "没有已关联的附件"
                    AttachmentFilter.ORPHANED -> "没有未关联的附件"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${(bytes / (1024f * 1024f) * 10).roundToInt() / 10f} MB"
    bytes >= 1024L -> "${(bytes / 1024f).roundToInt()} KB"
    else -> "$bytes B"
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "未知日期"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}
