package com.bird.fiber.ui.screens.importing

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.ui.navigation.FiberRoute
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ImportNavigationEffectTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val pendingEditor = MutableStateFlow<String?>(null)
    private val opened = mutableListOf<String>()
    private lateinit var owner: Owner
    private lateinit var navController: NavHostController

    private fun setUpNavigation(startDestination: String = FiberRoute.SETTINGS) {
        compose.runOnUiThread {
            owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED }
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                navController = rememberNavController()
                NavHost(navController, startDestination = startDestination) {
                    composable(FiberRoute.SETTINGS) { Text("原设置页") }
                    composable(FiberRoute.SEARCH) { Text("原搜索页") }
                    composable(
                        FiberRoute.EDITOR,
                        arguments = listOf(
                            navArgument(FiberRoute.ARG_ENCODED_FILE_URI) { type = NavType.StringType },
                            navArgument(FiberRoute.ARG_EDITOR_MODE) {
                                type = NavType.StringType
                                defaultValue = FiberRoute.MODE_PREVIEW
                            }
                        )
                    ) { Text("编辑器：${it.arguments?.getString(FiberRoute.ARG_ENCODED_FILE_URI)}") }
                }
                ImportNavigationEffect(navController, pendingEditor) { uri ->
                    opened += uri
                    pendingEditor.compareAndSet(uri, null)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun backgroundResultWaitsForResumeAndNavigatesOnlyOnce() {
        setUpNavigation()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        compose.runOnIdle { pendingEditor.value = "content://shared/note.md" }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(FiberRoute.SETTINGS, navController.currentDestination?.route)
            assertEquals(emptyList<String>(), opened)
            assertEquals("content://shared/note.md", pendingEditor.value)
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(emptyList<String>(), opened)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("content://shared/note.md"), opened)
            assertEquals(FiberRoute.EDITOR, navController.currentDestination?.route)
            assertEquals(FiberRoute.MODE_EDIT,
                navController.currentBackStackEntry?.arguments?.getString(FiberRoute.ARG_EDITOR_MODE))
            assertNull(pendingEditor.value)
            owner.registry.currentState = Lifecycle.State.CREATED
        }
        compose.waitForIdle()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, opened.size)
            navController.popBackStack()
        }
        compose.onNodeWithText("原设置页").assertIsDisplayed()
    }

    @Test
    fun resultDuringPageTransitionWaitsForDestinationToResume() {
        setUpNavigation()
        compose.runOnIdle {
            navController.navigate(FiberRoute.SEARCH)
            pendingEditor.value = "content://shared/during-transition.md"
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("content://shared/during-transition.md"), opened)
            assertEquals(FiberRoute.EDITOR, navController.currentDestination?.route)
            navController.popBackStack()
        }
        compose.onNodeWithText("原搜索页").assertIsDisplayed()
    }

    @Test
    fun importingFromExistingEditorPreservesOriginalEntry() {
        val originalRoute = FiberRoute.editor("content://original/unsaved.md", editMode = true)
        setUpNavigation(originalRoute)
        val originalEntry = navController.currentBackStackEntry
        compose.runOnIdle {
            originalEntry!!.savedStateHandle["draft"] = "未保存的修改"
            pendingEditor.value = "content://shared/new.md"
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, opened.size)
            navController.popBackStack()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(originalEntry, navController.currentBackStackEntry)
            assertEquals("未保存的修改", navController.currentBackStackEntry?.savedStateHandle?.get<String>("draft"))
        }
    }

    @Test
    fun savingDialogDisablesSelectionAndCancellation() {
        val library = LibraryEntity("library", "测试笔记库", "content://library", 0L)
        var selections = 0
        var dismissals = 0
        compose.setContent {
            ImportLibraryDialog("分享.md", listOf(library), { selections++ }, { dismissals++ }, isSaving = true)
        }
        compose.onNodeWithText("正在保存…").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsNotEnabled()
        compose.onNodeWithText(library.name).assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(0, selections)
            assertEquals(0, dismissals)
        }
    }
}
