package com.bird.fiber.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import com.bird.fiber.ui.screens.settings.ColorSchemeType

@Composable
fun FiberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Int = ColorSchemeType.BLUE.seedColor,
    fontSizeScale: Float = 1.0f,
    lineHeightScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> remember(seedColor, darkTheme) {
            colorSchemeFromSeed(seedColor, darkTheme)
        }
    }

    val surfaceColors = remember(colorScheme, darkTheme) {
        fiberSurfaceColors(colorScheme, darkTheme)
    }

    CompositionLocalProvider(
        LocalFiberSurfaceColors provides surfaceColors,
        LocalLineHeightScale provides lineHeightScale
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = fiberTypography((fontSizeScale * 100).toInt()),
            content = content
        )
    }
}

internal data class FiberSurfaceColors(
    val pageBackground: Color,
    val contentCard: Color,
    val topBar: Color,
    val searchInput: Color
)

internal fun fiberSurfaceColors(
    colorScheme: ColorScheme,
    darkTheme: Boolean
): FiberSurfaceColors {
    val pageBackground = if (darkTheme) {
        colorScheme.surface
    } else {
        colorScheme.surfaceContainerLow
    }
    val contentCard = if (darkTheme) {
        colorScheme.surfaceContainerLow
    } else {
        // Keep day cards close to white while adding a barely-there theme tint.
        colorScheme.primary
            .copy(alpha = 0.04f)
            .compositeOver(colorScheme.surfaceContainerLowest)
    }
    return FiberSurfaceColors(
        pageBackground = pageBackground,
        contentCard = contentCard,
        topBar = pageBackground,
        searchInput = colorScheme.surfaceContainerHigh
    )
}

internal val LocalFiberSurfaceColors = staticCompositionLocalOf<FiberSurfaceColors> {
    error("Fiber surface colors are not available outside FiberTheme")
}

/**
 * 正文行距缩放系数（默认 1.0）
 *
 * 在编辑器（字号 × 1.4）与主页条目（typography 预设行高）各自的预设行距
 * 基础上做整体缩放，而不是覆盖为统一值；瀑布流卡片有自己的固定行距，不受影响
 */
val LocalLineHeightScale = compositionLocalOf { 1.0f }
