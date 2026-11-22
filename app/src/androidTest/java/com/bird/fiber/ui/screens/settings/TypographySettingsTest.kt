package com.bird.fiber.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.bird.fiber.ui.theme.FiberTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TypographySettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun slidersUseExistingStepsAndKeepLabelsOnTheLeft() {
        var level by mutableIntStateOf(2)
        var lineHeight by mutableIntStateOf(100)
        compose.setContent {
            FiberTheme(dynamicColor = false) {
                Column(Modifier.width(320.dp)) {
                    FontSizeSettingItem(level) { level = it }
                    LineHeightSettingItem(lineHeight) { lineHeight = it }
                }
            }
        }
        val fontSlider = compose.onNodeWithContentDescription("字体大小")
        val lineSlider = compose.onNodeWithContentDescription("正文行距")
        fontSlider.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(2f, 0f..4f, 3))
        lineSlider.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(100f, 90f..130f, 3))
        fontSlider.performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        lineSlider.performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        compose.runOnIdle { assertEquals(4, level); assertEquals(120, lineHeight) }
        compose.onAllNodesWithText("120%").assertCountEquals(2)
        val fontLabelBounds = compose.onNodeWithText("字体大小").fetchSemanticsNode().boundsInRoot
        val lineLabelBounds = compose.onNodeWithText("正文行距").fetchSemanticsNode().boundsInRoot
        val fontBounds = fontSlider.fetchSemanticsNode().boundsInRoot
        val lineBounds = lineSlider.fetchSemanticsNode().boundsInRoot
        assertEquals(fontLabelBounds.left, lineLabelBounds.left, 0.1f)
        assertEquals(fontBounds.left, lineBounds.left, 0.1f)
        assertTrue(fontLabelBounds.right <= fontBounds.left)
        assertTrue(kotlin.math.abs(fontLabelBounds.center.y - fontBounds.center.y) < 2f)
    }

    @Test fun largerFontKeepsBothSlidersAndValuesVisibleOnNarrowScreen() {
        compose.setContent {
            FiberTheme(darkTheme = true, dynamicColor = false, fontSizeScale = 1.2f) {
                Column(Modifier.width(320.dp)) {
                    FontSizeSettingItem(4) {}
                    LineHeightSettingItem(130) {}
                }
            }
        }
        compose.onNodeWithText("字体大小").assertIsDisplayed()
        compose.onNodeWithText("正文行距").assertIsDisplayed()
        compose.onNodeWithText("120%").assertIsDisplayed()
        compose.onNodeWithText("130%").assertIsDisplayed()
        compose.onNodeWithContentDescription("字体大小").assertWidthIsAtLeast(80.dp)
        compose.onNodeWithContentDescription("正文行距").assertWidthIsAtLeast(80.dp)
    }
}
