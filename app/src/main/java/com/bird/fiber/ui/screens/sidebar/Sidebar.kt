package com.bird.fiber.ui.screens.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bird.fiber.data.local.library.LibraryEntity

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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SidebarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
    modifier: Modifier = Modifier,
    viewModel: SidebarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部栏
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
                }
            )
        } else {
            // 空状态
            EmptyLibrariesState(
                onAddLibrary = onAddLibrary
            )
        }

        // 底部区域：添加按钮
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddLibrary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加笔记库")
            }
        }
    }
}

/**
 * 侧边栏顶部栏
 */
@Composable
private fun SidebarHeader(
    onSettingsClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "笔记库",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // 设置按钮 - 右上角齿轮图标
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置"
            )
        }
    }

    HorizontalDivider()
}

/**
 * 库列表
 */
@Composable
private fun LibraryList(
    libraries: List<LibraryEntity>,
    selectedLibraryId: String?,
    onLibraryClick: (String) -> Unit,
    onDeleteClick: (LibraryEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(libraries) { library ->
            LibraryListItem(
                library = library,
                isSelected = library.id == selectedLibraryId,
                onClick = { onLibraryClick(library.id) },
                onDeleteClick = { onDeleteClick(library) }
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
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
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
 * 空状态
 */
@Composable
private fun EmptyLibrariesState(
    onAddLibrary: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
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
