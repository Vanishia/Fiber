package com.bird.fiber.ui.screens.sidebar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
}
