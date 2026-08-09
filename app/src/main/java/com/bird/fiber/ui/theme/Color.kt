package com.bird.fiber.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.palettes.CorePalette
import com.materialkolor.palettes.TonalPalette

/**
 * 从一个种子色生成完整的 Material 3 色彩角色。
 *
 * 推荐色与自定义颜色都经过同一条路径，避免为每套主题手写并维护 ColorScheme。
 */
internal fun colorSchemeFromSeed(seedColor: Int, darkTheme: Boolean): ColorScheme {
    val palette = CorePalette.of(seedColor.withOpaqueAlpha())

    fun TonalPalette.color(tone: Int) = Color(this.tone(tone))

    return if (darkTheme) {
        darkColorScheme(
            primary = palette.a1.color(80),
            onPrimary = palette.a1.color(20),
            primaryContainer = palette.a1.color(30),
            onPrimaryContainer = palette.a1.color(90),
            inversePrimary = palette.a1.color(40),
            secondary = palette.a2.color(80),
            onSecondary = palette.a2.color(20),
            secondaryContainer = palette.a2.color(30),
            onSecondaryContainer = palette.a2.color(90),
            tertiary = palette.a3.color(80),
            onTertiary = palette.a3.color(20),
            tertiaryContainer = palette.a3.color(30),
            onTertiaryContainer = palette.a3.color(90),
            background = palette.n1.color(10),
            onBackground = palette.n1.color(90),
            surface = palette.n1.color(10),
            onSurface = palette.n1.color(90),
            surfaceVariant = palette.n2.color(30),
            onSurfaceVariant = palette.n2.color(80),
            surfaceTint = palette.a1.color(80),
            inverseSurface = palette.n1.color(90),
            inverseOnSurface = palette.n1.color(20),
            error = palette.error.color(80),
            onError = palette.error.color(20),
            errorContainer = palette.error.color(30),
            onErrorContainer = palette.error.color(90),
            outline = palette.n2.color(60),
            outlineVariant = palette.n2.color(30),
            scrim = palette.n1.color(0),
            surfaceBright = palette.n1.color(24),
            surfaceDim = palette.n1.color(6),
            surfaceContainerLowest = palette.n1.color(4),
            surfaceContainerLow = palette.n1.color(10),
            surfaceContainer = palette.n1.color(12),
            surfaceContainerHigh = palette.n1.color(17),
            surfaceContainerHighest = palette.n1.color(22),
        )
    } else {
        lightColorScheme(
            primary = palette.a1.color(40),
            onPrimary = palette.a1.color(100),
            primaryContainer = palette.a1.color(90),
            onPrimaryContainer = palette.a1.color(10),
            inversePrimary = palette.a1.color(80),
            secondary = palette.a2.color(40),
            onSecondary = palette.a2.color(100),
            secondaryContainer = palette.a2.color(90),
            onSecondaryContainer = palette.a2.color(10),
            tertiary = palette.a3.color(40),
            onTertiary = palette.a3.color(100),
            tertiaryContainer = palette.a3.color(90),
            onTertiaryContainer = palette.a3.color(10),
            background = palette.n1.color(99),
            onBackground = palette.n1.color(10),
            surface = palette.n1.color(99),
            onSurface = palette.n1.color(10),
            surfaceVariant = palette.n2.color(90),
            onSurfaceVariant = palette.n2.color(30),
            surfaceTint = palette.a1.color(40),
            inverseSurface = palette.n1.color(20),
            inverseOnSurface = palette.n1.color(95),
            error = palette.error.color(40),
            onError = palette.error.color(100),
            errorContainer = palette.error.color(90),
            onErrorContainer = palette.error.color(10),
            outline = palette.n2.color(50),
            outlineVariant = palette.n2.color(80),
            scrim = palette.n1.color(0),
            surfaceBright = palette.n1.color(98),
            surfaceDim = palette.n1.color(87),
            surfaceContainerLowest = palette.n1.color(100),
            surfaceContainerLow = palette.n1.color(96),
            surfaceContainer = palette.n1.color(94),
            surfaceContainerHigh = palette.n1.color(92),
            surfaceContainerHighest = palette.n1.color(90),
        )
    }
}

internal fun Int.withOpaqueAlpha(): Int = this or (0xFF shl 24)
