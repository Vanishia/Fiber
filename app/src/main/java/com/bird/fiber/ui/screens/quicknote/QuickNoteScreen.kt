package com.bird.fiber.ui.screens.quicknote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 快速记录页面
 *
 * 类似聊天软件的输入界面：
 * - 顶部栏：返回按钮 + 标题
 * - 中间：大输入框（输入笔记内容）
 * - 底部：发送按钮（纸飞机图标）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteScreen(
    onClose: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickNoteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // 监听一次性事件（保存成功）
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickNoteEvent.SaveSuccess -> {
                    onSaveSuccess()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("快速记录") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 输入区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 提示文字
                Text(
                    text = "输入笔记内容...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 大输入框 - 使用 ViewModel 的状态作为唯一数据源
                OutlinedTextField(
                    value = uiState.content,
                    onValueChange = { newValue ->
                        Timber.d("QuickNoteScreen: 输入变化 '${uiState.content}' -> '$newValue'")
                        viewModel.onContentChange(newValue)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    placeholder = {
                        Text("在这里输入你的想法...")
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                    )
                )

                // 底部按钮区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 错误提示
                    if (uiState.error != null) {
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                    }

                    // 字数统计
                    Text(
                        text = "${uiState.content.length} 字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )

                    // 发送按钮（纸飞机图标）
                    val isContentEmpty = uiState.content.isBlank()
                    FloatingActionButton(
                        onClick = { viewModel.saveNote() },
                        modifier = Modifier.size(56.dp),
                        containerColor = if (isContentEmpty) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ) {
                        if (uiState.isSaving) {
                            // 保存中显示加载动画
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = if (isContentEmpty) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                }
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (isContentEmpty) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
