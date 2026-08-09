package com.bird.fiber.ui.theme

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.bird.fiber.ui.screens.settings.ColorSchemeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ColorSchemeTest {

    @Test
    fun `different seeds affect global color roles`() {
        val blue = colorSchemeFromSeed(ColorSchemeType.BLUE.seedColor, darkTheme = false)
        val amber = colorSchemeFromSeed(ColorSchemeType.AMBER.seedColor, darkTheme = false)

        assertNotEquals(blue.primary.toArgb(), amber.primary.toArgb())
        assertNotEquals(blue.primaryContainer.toArgb(), amber.primaryContainer.toArgb())
        assertNotEquals(blue.surface.toArgb(), amber.surface.toArgb())
    }

    @Test
    fun `one seed generates separate light and dark schemes`() {
        val light = colorSchemeFromSeed(ColorSchemeType.OCEAN.seedColor, darkTheme = false)
        val dark = colorSchemeFromSeed(ColorSchemeType.OCEAN.seedColor, darkTheme = true)

        assertNotEquals(light.primary.toArgb(), dark.primary.toArgb())
        assertNotEquals(light.background.toArgb(), dark.background.toArgb())
        assertNotEquals(light.onSurface.toArgb(), dark.onSurface.toArgb())
    }

    @Test
    fun `light and dark themes map surfaces to the intended roles`() {
        ColorSchemeType.recommended.forEach { scheme ->
            val light = colorSchemeFromSeed(scheme.seedColor, darkTheme = false)
            val dark = colorSchemeFromSeed(scheme.seedColor, darkTheme = true)
            val lightSurfaces = fiberSurfaceColors(light, darkTheme = false)
            val darkSurfaces = fiberSurfaceColors(dark, darkTheme = true)
            val expectedLightContentCard = light.primary
                .copy(alpha = 0.04f)
                .compositeOver(light.surfaceContainerLowest)

            assertEquals(light.surfaceContainerLow, lightSurfaces.pageBackground)
            assertEquals(expectedLightContentCard, lightSurfaces.contentCard)
            assertEquals(dark.surface, darkSurfaces.pageBackground)
            assertEquals(dark.surfaceContainerLow, darkSurfaces.contentCard)
            assertEquals(lightSurfaces.pageBackground, lightSurfaces.topBar)
            assertEquals(darkSurfaces.pageBackground, darkSurfaces.topBar)
        }
    }

    @Test
    fun `recommended colors exclude legacy purple and custom entry`() {
        assertEquals(5, ColorSchemeType.recommended.size)
        assertFalse(ColorSchemeType.DEFAULT in ColorSchemeType.recommended)
        assertFalse(ColorSchemeType.CUSTOM in ColorSchemeType.recommended)
    }
}
