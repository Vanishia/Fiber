package com.bird.fiber.ui.theme

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
    fun `recommended colors exclude legacy purple and custom entry`() {
        assertEquals(5, ColorSchemeType.recommended.size)
        assertFalse(ColorSchemeType.DEFAULT in ColorSchemeType.recommended)
        assertFalse(ColorSchemeType.CUSTOM in ColorSchemeType.recommended)
    }
}
