package com.bird.fiber.ui.screens.settings

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bird.fiber.BuildConfig
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicColorActive = dynamicColorAvailable && uiState.isDynamicColorEnabled
    var showCustomColorPicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            SettingsGroupTitle("外观")

            SettingsItem(
                icon = Icons.Outlined.ColorLens,
                title = "系统动态颜色",
                description = if (dynamicColorAvailable) {
                    "跟随壁纸生成主题色"
                } else {
                    "需要 Android 12 或更高版本"
                },
                onClick = if (dynamicColorAvailable) viewModel::toggleDynamicColor else ({ })
            ) {
                Switch(
                    checked = dynamicColorActive,
                    onCheckedChange = { viewModel.toggleDynamicColor() },
                    enabled = dynamicColorAvailable
                )
            }

            SettingsDivider()

            ThemeColorSetting(
                uiState = uiState,
                dynamicColorActive = dynamicColorActive,
                onSelectPreset = viewModel::setColorScheme,
                onOpenCustomColor = { showCustomColorPicker = true }
            )

            SettingsDivider()

            SettingsItem(
                icon = Icons.Outlined.DarkMode,
                title = "深色模式",
                description = uiState.darkMode.displayName,
                onClick = { showThemePicker = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = uiState.darkMode.shortName,
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

            SettingsGroupTitle("显示")

            FontSizeSettingItem(
                currentLevel = uiState.fontSizeLevel,
                onLevelChange = viewModel::setFontSizeLevel
            )

            SettingsGroupTitle("关于")

            SettingsItem(
                icon = Icons.Outlined.Info,
                title = "关于 Fiber",
                description = "版本 ${BuildConfig.VERSION_NAME}",
                onClick = { }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showCustomColorPicker) {
        CustomColorDialog(
            initialColor = uiState.themeSeedColor,
            onDismiss = { showCustomColorPicker = false },
            onConfirm = { seedColor ->
                viewModel.setCustomSeedColor(seedColor)
                showCustomColorPicker = false
            }
        )
    }

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

@Composable
private fun SettingsGroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

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
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
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
        trailing()
    }
}

@Composable
private fun ThemeColorSetting(
    uiState: SettingsUiState,
    dynamicColorActive: Boolean,
    onSelectPreset: (ColorSchemeType) -> Unit,
    onOpenCustomColor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("主题颜色", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (dynamicColorActive) {
                        "系统动态颜色正在生效"
                    } else {
                        "${uiState.colorScheme.label} · ${uiState.themeSeedColor.toHexColor()}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "推荐颜色",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ColorSchemeType.recommended.forEach { scheme ->
                PresetColorButton(
                    scheme = scheme,
                    selected = !dynamicColorActive && uiState.colorScheme == scheme,
                    onClick = { onSelectPreset(scheme) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = if (!dynamicColorActive && uiState.colorScheme == ColorSchemeType.CUSTOM) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onOpenCustomColor)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(uiState.themeSeedColor))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("自定义颜色", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "从色环选择一个种子色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowScope.PresetColorButton(
    scheme: ColorSchemeType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val swatch = Color(scheme.seedColor)
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(swatch),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = scheme.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

@Composable
private fun CustomColorDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedColor by remember(initialColor) { mutableIntStateOf(initialColor) }
    val hsv = selectedColor.toHsv()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义主题色") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "在色环上选择色相与饱和度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))
                SeedColorWheel(
                    seedColor = selectedColor,
                    onColorChange = { selectedColor = it }
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("明度", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = hsv[2],
                        onValueChange = { value ->
                            selectedColor = AndroidColor.HSVToColor(
                                floatArrayOf(hsv[0], hsv[1], value)
                            )
                        },
                        valueRange = 0.35f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(selectedColor))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                }
                Text(
                    text = selectedColor.toHexColor(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedColor) }) {
                Text("应用")
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
private fun SeedColorWheel(
    seedColor: Int,
    onColorChange: (Int) -> Unit
) {
    var wheelSize by remember { mutableStateOf(IntSize.Zero) }
    val hsv = seedColor.toHsv()
    val outlineColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = Modifier
            .size(210.dp)
            .semantics { contentDescription = "主题色环" }
            .onSizeChanged { wheelSize = it }
            .pointerInput(wheelSize, hsv[2]) {
                detectTapGestures { position ->
                    onColorChange(colorFromWheel(position, wheelSize, hsv[2]))
                }
            }
            .pointerInput(wheelSize, hsv[2]) {
                detectDragGestures(
                    onDragStart = { position ->
                        onColorChange(colorFromWheel(position, wheelSize, hsv[2]))
                    },
                    onDrag = { change, _ ->
                        onColorChange(colorFromWheel(change.position, wheelSize, hsv[2]))
                        change.consume()
                    }
                )
            }
    ) {
        val wheelRadius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red
                ),
                center = center
            ),
            radius = wheelRadius
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = wheelRadius
            ),
            radius = wheelRadius
        )

        val angle = hsv[0] / 180f * PI
        val selectorRadius = wheelRadius * hsv[1]
        val selector = Offset(
            x = center.x + cos(angle).toFloat() * selectorRadius,
            y = center.y + sin(angle).toFloat() * selectorRadius
        )
        drawCircle(Color.White, radius = 9.dp.toPx(), center = selector)
        drawCircle(
            color = outlineColor,
            radius = 9.dp.toPx(),
            center = selector,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(Color(seedColor), radius = 5.dp.toPx(), center = selector)
    }
}

private fun colorFromWheel(position: Offset, size: IntSize, brightness: Float): Int {
    if (size.width == 0 || size.height == 0) return ColorSchemeType.BLUE.seedColor

    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val dx = position.x - centerX
    val dy = position.y - centerY
    val radius = min(size.width, size.height) / 2f
    val saturation = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
    val hue = ((atan2(dy, dx) * 180f / PI).toFloat() + 360f) % 360f

    return AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontSizeSettingItem(
    currentLevel: Int,
    onLevelChange: (Int) -> Unit
) {
    val levels = SettingsUiState.FONT_SIZE_PERCENTAGES
    val poem = remember {
        listOf(
            "山光悦鸟性，潭影空人心。",
            "明月松间照，清泉石上流。",
            "行到水穷处，坐看云起时。",
            "春风又绿江南岸，明月何时照我还。",
            "海上生明月，天涯共此时。"
        ).random()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "字体大小",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${levels[currentLevel.coerceIn(levels.indices)]}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            levels.forEachIndexed { index, percent ->
                SegmentedButton(
                    selected = index == currentLevel,
                    onClick = { onLevelChange(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, levels.size),
                    label = {
                        Text(
                            text = percent.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = poem,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "字体预览",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemePickerDialog(
    currentMode: DarkMode,
    onDismiss: () -> Unit,
    onSelect: (DarkMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("深色模式") },
        text = {
            Column {
                DarkMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (mode == currentMode) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
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

private val DarkMode.displayName: String
    get() = when (this) {
        DarkMode.SYSTEM -> "跟随系统"
        DarkMode.LIGHT -> "浅色模式"
        DarkMode.DARK -> "深色模式"
    }

private val DarkMode.shortName: String
    get() = when (this) {
        DarkMode.SYSTEM -> "自动"
        DarkMode.LIGHT -> "浅色"
        DarkMode.DARK -> "深色"
    }

private fun Int.toHsv(): FloatArray = FloatArray(3).also {
    AndroidColor.colorToHSV(this, it)
}

private fun Int.toHexColor(): String = "#%06X".format(this and 0xFFFFFF)
