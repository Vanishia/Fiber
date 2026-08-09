package com.bird.fiber.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bird.fiber.ui.screens.settings.ColorSchemeType

@Composable
fun FiberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Int = ColorSchemeType.BLUE.seedColor,
    fontSizeScale: Float = 1.0f,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = fiberTypography((fontSizeScale * 100).toInt()),
        content = content
    )
}
