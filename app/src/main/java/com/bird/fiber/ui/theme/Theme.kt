package com.bird.fiber.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.bird.fiber.ui.screens.settings.ColorSchemeType

/**
 * 根据配色方案类型获取完整的 ColorScheme
 */
private fun getColorScheme(
    colorSchemeType: ColorSchemeType,
    darkTheme: Boolean
): ColorScheme = when (colorSchemeType) {
    ColorSchemeType.DEFAULT -> if (darkTheme) DefaultColorScheme.dark else DefaultColorScheme.light
    ColorSchemeType.SAKURA -> if (darkTheme) SakuraColorScheme.dark else SakuraColorScheme.light
    ColorSchemeType.BLUE -> if (darkTheme) BlueColorScheme.dark else BlueColorScheme.light
    ColorSchemeType.OCEAN -> if (darkTheme) OceanColorScheme.dark else OceanColorScheme.light
    ColorSchemeType.GRAY -> if (darkTheme) GrayColorScheme.dark else GrayColorScheme.light
    ColorSchemeType.AMBER -> if (darkTheme) AmberColorScheme.dark else AmberColorScheme.light
}

@Composable
fun FiberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    colorSchemeType: ColorSchemeType = ColorSchemeType.DEFAULT,
    fontSizeScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 动态颜色优先级最高（如果开启且系统支持）
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 否则使用选定的配色方案
        else -> getColorScheme(colorSchemeType, darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = fiberTypography((fontSizeScale * 100).toInt()),
        content = content
    )
}