package com.bird.fiber.ui.screens.sidebar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.bird.fiber.ui.theme.FiberTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SidebarHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun doubleTapRevealsMessageForThreeSecondsAndSettingsStaysClickable() {
        var settingsClicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FiberTheme(dynamicColor = false) {
                SidebarHeader({ settingsClicks++ }, listOf("已经记录了12天"))
            }
        }
        compose.onNodeWithText("Fiber").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("已经记录了12天").assertDoesNotExist()

        compose.onNodeWithText("Fiber").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithText("已经记录了12天").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.runOnIdle { assertEquals(1, settingsClicks) }
        compose.mainClock.advanceTimeBy(3100)
        compose.onNodeWithText("已经记录了12天").assertDoesNotExist()
    }

    @Test fun repeatedDoubleTapRestartsTheDisplayTime() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FiberTheme(darkTheme = true, dynamicColor = false) {
                SidebarHeader({}, listOf("今天写下123字"))
            }
        }
        compose.onNodeWithText("Fiber").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(2200)
        compose.onNodeWithText("Fiber").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithText("今天写下123字").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("今天写下123字").assertDoesNotExist()
    }

    @Test fun changingMessagesKeepsTextBaselinesAndHeaderPositionsStable() {
        assertRepeatedRevealLayout(fontSizeScale = 1f)
    }

    @Test fun changingMessagesKeepsLargeTextBaselinesAndHeaderPositionsStable() {
        assertRepeatedRevealLayout(fontSizeScale = 1.2f)
    }

    private fun assertRepeatedRevealLayout(fontSizeScale: Float) {
        var messages by mutableStateOf(listOf("思想的原材料"))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FiberTheme(dynamicColor = false, fontSizeScale = fontSizeScale) {
                Box(Modifier.width(280.dp)) {
                    SidebarHeader({}, messages)
                }
            }
        }
        val title = compose.onNodeWithText("Fiber", useUnmergedTree = true)
        val titleTop = title.fetchSemanticsNode().boundsInRoot.top
        val settingsTop = compose.onNodeWithContentDescription("设置").fetchSemanticsNode().boundsInRoot.top

        fun baseline(node: SemanticsNodeInteraction): Float {
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return node.fetchSemanticsNode().boundsInRoot.top + layouts.single().firstBaseline
        }

        val sequence = listOf("思想的原材料", "已经记录了295天", "今天写下1535字", "已经记录了295天", "思想的原材料", "今天写下1535字")
        sequence.forEachIndexed { index, text ->
            compose.runOnIdle { messages = listOf(text) }
            title.performTouchInput { doubleClick() }
            compose.mainClock.advanceTimeBy(250)
            val message = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            assertEquals("Message baseline after reveal $index", baseline(title), baseline(message), 1f)
            assertEquals("Title position after reveal $index", titleTop, title.fetchSemanticsNode().boundsInRoot.top, 1f)
            assertEquals("Settings position after reveal $index", settingsTop,
                compose.onNodeWithContentDescription("设置").fetchSemanticsNode().boundsInRoot.top, 1f)
            when (index % 3) {
                1 -> compose.mainClock.advanceTimeBy(2650)
                2 -> {
                    compose.mainClock.advanceTimeBy(3100)
                    compose.onNodeWithText(text).assertDoesNotExist()
                }
            }
        }
    }
}
