package com.bird.fiber.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bird.fiber.BuildConfig

/**
 * 设置页面
 *
 * 风格参考：系统设置页面
 * - 分组标题（主题色小字）
 * - 设置项：图标 + 标题 + 描述 + 右侧控件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showColorPicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // 显示设置分组
            SettingsGroupTitle("显示设置")

            // 字体大小（分段滑块 + 实时预览）
            FontSizeSettingItem(
                currentLevel = uiState.fontSizeLevel,
                onLevelChange = viewModel::setFontSizeLevel
            )

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 动态颜色开关
            SettingsItem(
                icon = Icons.Outlined.ColorLens,
                title = "动态颜色",
                description = "使用系统主题颜色",
                onClick = { viewModel.toggleDynamicColor() }
            ) {
                Switch(
                    checked = uiState.isDynamicColorEnabled,
                    onCheckedChange = { viewModel.toggleDynamicColor() }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 配色选择
            SettingsItem(
                icon = Icons.Outlined.Palette,
                title = "配色方案",
                description = if (uiState.isDynamicColorEnabled) {
                    "${uiState.colorScheme.label}（动态颜色开启中）"
                } else {
                    uiState.colorScheme.label
                },
                onClick = { showColorPicker = true }
            ) {
                // 显示当前配色预览
                ColorSchemePreview(uiState.colorScheme)
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // 深色模式选择
            SettingsItem(
                icon = Icons.Outlined.DarkMode,
                title = "深色模式",
                description = when (uiState.darkMode) {
                    DarkMode.SYSTEM -> "跟随系统"
                    DarkMode.LIGHT -> "始终关闭"
                    DarkMode.DARK -> "始终开启"
                },
                onClick = { showThemePicker = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (uiState.darkMode) {
                            DarkMode.SYSTEM -> "跟随系统"
                            DarkMode.LIGHT -> "关闭"
                            DarkMode.DARK -> "开启"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 关于分组
            SettingsGroupTitle("关于")

            SettingsItem(
                icon = Icons.Outlined.Info,
                title = "关于 Fiber",
                description = "版本 ${BuildConfig.VERSION_NAME}",
                onClick = { /* 可以打开关于页面 */ }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 配色选择弹窗
    if (showColorPicker) {
        ColorPickerDialog(
            currentScheme = uiState.colorScheme,
            onDismiss = { showColorPicker = false },
            onSelect = { scheme ->
                viewModel.setColorScheme(scheme)
                showColorPicker = false
            }
        )
    }

    // 深色模式选择弹窗
    if (showThemePicker) {
        ThemePickerDialog(
            currentMode = uiState.darkMode,
            onDismiss = { showThemePicker = false },
            onSelect = { mode ->
                viewModel.setDarkMode(mode)
                showThemePicker = false
            }
        )
    }
}

/**
 * 分组标题
 */
@Composable
private fun SettingsGroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

/**
 * 设置项组件
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 标题和描述
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 右侧控件
        trailing()
    }
}

/**
 * 配色方案预览
 */
@Composable
private fun ColorSchemePreview(scheme: ColorSchemeType) {
    val previewColor = when (scheme) {
        ColorSchemeType.DEFAULT -> Color(0xFF6650a4)
        ColorSchemeType.SAKURA -> Color(0xFFF48FB1)
        ColorSchemeType.BLUE -> Color(0xFF2196F3)
        ColorSchemeType.OCEAN -> Color(0xFF006B6B)
        ColorSchemeType.GRAY -> Color(0xFF607D8B)
        ColorSchemeType.AMBER -> Color(0xFFFFA000)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(previewColor)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 配色选择弹窗
 */
@Composable
private fun ColorPickerDialog(
    currentScheme: ColorSchemeType,
    onDismiss: () -> Unit,
    onSelect: (ColorSchemeType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择配色") },
        text = {
            Column {
                ColorSchemeType.entries.forEach { scheme ->
                    ColorSchemeOption(
                        scheme = scheme,
                        isSelected = scheme == currentScheme,
                        onClick = { onSelect(scheme) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 配色选项
 */
@Composable
private fun ColorSchemeOption(
    scheme: ColorSchemeType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val previewColor = when (scheme) {
        ColorSchemeType.DEFAULT -> Color(0xFF6650a4)
        ColorSchemeType.SAKURA -> Color(0xFFF48FB1)
        ColorSchemeType.BLUE -> Color(0xFF2196F3)
        ColorSchemeType.OCEAN -> Color(0xFF006B6B)
        ColorSchemeType.GRAY -> Color(0xFF607D8B)
        ColorSchemeType.AMBER -> Color(0xFFFFA000)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(previewColor)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = scheme.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 深色模式选择弹窗
 */
@Composable
private fun ThemePickerDialog(
    currentMode: DarkMode,
    onDismiss: () -> Unit,
    onSelect: (DarkMode) -> Unit
) {
    val options = listOf(
        DarkMode.SYSTEM to "跟随系统",
        DarkMode.LIGHT to "浅色模式",
        DarkMode.DARK to "深色模式"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("深色模式") },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    ThemeOption(
                        label = label,
                        isSelected = mode == currentMode,
                        onClick = { onSelect(mode) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 主题选项
 */
@Composable
private fun ThemeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 字体大小设置项（分段滑块 + 实时预览）
 *
 * 参考微信字体大小调节界面
 */
@Composable
private fun FontSizeSettingItem(
    currentLevel: Int,
    onLevelChange: (Int) -> Unit
) {
    // 字体大小级别：0-4 对应 80%, 90%, 100%, 110%, 120%
    val levels = listOf(80, 90, 100, 110, 120)
    val currentPercent = levels[currentLevel.coerceIn(0, 4)]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)  // 从 16.dp 减小到 12.dp
    ) {
        // 标题
        Text(
            text = "字体大小",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))  // 从 16.dp 减小到 12.dp

        // 分段滑块 + 百分比
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 分段滑块
            SegmentedSlider(
                level = currentLevel,
                onLevelChange = onLevelChange,
                modifier = Modifier.weight(1f)
            )

            // 百分比显示
            Text(
                text = "$currentPercent%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))  // 从 16.dp 减小到 12.dp

        // 实时预览文本
        Text(
            text = "这是一个示例的聊天文本",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 分段滑块组件
 *
 * 5个档位，带圆点标记
 */
@Composable
private fun SegmentedSlider(
    level: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val levelCount = 5
    val trackHeight = 16.dp  // 从 24.dp 减小到 16.dp
    val thumbWidth = 3.dp    // 从 4.dp 减小到 3.dp
    val thumbHeight = 20.dp  // 从 32.dp 减小到 20.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbHeight),
        contentAlignment = Alignment.Center
    ) {
        // 轨道背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // 已选中部分（左侧深色）
            Box(
                modifier = Modifier
                    .fillMaxWidth((level.toFloat() + 0.5f) / levelCount)
                    .height(trackHeight)
                    .background(MaterialTheme.colorScheme.primary)
            )

            // 刻度点
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(levelCount) { index ->
                    val isActive = index <= level
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                    )
                }
            }
        }

        // 滑块指示器（竖线）- 使用 Row 布局简化位置计算
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(levelCount) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (index == level) {
                        Box(
                            modifier = Modifier
                                .width(thumbWidth)
                                .height(thumbHeight)
                                .clip(RoundedCornerShape(thumbWidth / 2))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        // 点击区域
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(levelCount) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onLevelChange(index) }
                )
            }
        }
    }
}
