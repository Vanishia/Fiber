package com.bird.fiber.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.bird.fiber.ui.theme.FiberTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeViewSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun changingOneLibraryKeepsOtherLibrarySelection() {
        var state by mutableStateOf(HomeViewSettingsUiState(
            libraries = listOf(
                LibraryHomeViewSetting("a", "工作笔记"),
                LibraryHomeViewSetting("b", "生活笔记")
            ),
            isLoading = false
        ))
        val saved = mutableListOf<Pair<String, ListViewMode>>()
        compose.setContent {
            FiberTheme(dynamicColor = false) {
                HomeViewSettingsContent(
                    uiState = state,
                    onBackClick = {},
                    onModeChange = { id, mode ->
                        saved += id to mode
                        state = state.copy(libraries = state.libraries.map {
                            if (it.libraryId == id) it.copy(viewMode = mode) else it
                        })
                    }
                )
            }
        }
        compose.onNodeWithText("主页视图设置").assertIsDisplayed()
        compose.onNodeWithText("条目视图").assertIsDisplayed()
        compose.onNodeWithText("瀑布流视图").assertIsDisplayed()
        compose.onNodeWithContentDescription("工作笔记，条目视图").assertIsSelected()
        compose.onNodeWithContentDescription("生活笔记，条目视图").assertIsSelected()
        compose.onNodeWithContentDescription("工作笔记，瀑布流视图").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("工作笔记，条目视图").assertIsNotSelected()
        compose.onNodeWithContentDescription("生活笔记，条目视图").assertIsSelected()
        compose.onNodeWithContentDescription("工作笔记，条目视图").performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf("a" to ListViewMode.GRID, "a" to ListViewMode.LIST), saved)
        }
    }

    @Test
    fun emptyLibraryListExplainsHowToStart() {
        compose.setContent {
            FiberTheme(dynamicColor = false) {
                HomeViewSettingsContent(HomeViewSettingsUiState(isLoading = false), {}, { _, _ -> })
            }
        }
        compose.onNodeWithText("暂无已导入库，请先在主页导入库").assertIsDisplayed()
    }

    @Test
    fun longLibraryListCanScrollAndSelectLastRow() {
        var selectedId: String? = null
        val state = HomeViewSettingsUiState(
            libraries = (1..40).map { LibraryHomeViewSetting("$it", "笔记库 $it") },
            isLoading = false
        )
        compose.setContent {
            FiberTheme(darkTheme = true, dynamicColor = false) {
                HomeViewSettingsContent(state, {}, { id, _ -> selectedId = id })
            }
        }
        compose.onNode(hasScrollAction()).performScrollToIndex(39)
        compose.onNodeWithContentDescription("笔记库 40，瀑布流视图").performClick()
        compose.runOnIdle { assertEquals("40", selectedId) }
    }
}
