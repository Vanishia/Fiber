package com.bird.fiber.ui.navigation

import android.net.Uri
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bird.fiber.ui.screens.editor.EditorScreen
import com.bird.fiber.ui.screens.attachments.AttachmentManagerScreen
import com.bird.fiber.ui.screens.main.MainScreenContainer
import com.bird.fiber.ui.screens.quicknote.QuickNoteScreen
import com.bird.fiber.ui.screens.search.SearchScreen
import com.bird.fiber.ui.screens.settings.SettingsScreen
import com.bird.fiber.utils.UriHelper
import timber.log.Timber

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FiberNavGraph(
    navController: NavHostController,
    onSelectFolder: () -> Unit,
    onAddLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val navigateToEditor: (String, Boolean) -> Unit = { fileUri, editMode ->
        navController.navigate(FiberRoute.editor(fileUri, editMode))
    }

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = FiberRoute.FILES,
            modifier = modifier.drawBehind { drawRect(backgroundColor) }
        ) {
        composable(route = FiberRoute.FILES) {
            MainScreenContainer(
                visible = true,
                onFileClick = navigateToEditor,
                onSelectFolder = onSelectFolder,
                onAddLibrary = onAddLibrary,
                onSearchClick = { navController.navigate(FiberRoute.SEARCH) },
                onSettingsClick = { navController.navigate(FiberRoute.SETTINGS) },
                onManageAttachments = { libraryId ->
                    navController.navigate(FiberRoute.attachments(libraryId))
                },
                topBarModifier = Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState("main-search-top-bar"),
                    animatedVisibilityScope = this
                )
            )
        }

        composable(
            route = FiberRoute.EDITOR,
            arguments = listOf(
                navArgument(FiberRoute.ARG_ENCODED_FILE_URI) { type = NavType.StringType },
                navArgument(FiberRoute.ARG_EDITOR_MODE) {
                    type = NavType.StringType
                    defaultValue = FiberRoute.MODE_PREVIEW
                }
            )
        ) { backStackEntry ->
            val fileUri = decodeFileUriOrPop(
                navController = navController,
                encodedFileUri = backStackEntry.arguments?.getString(FiberRoute.ARG_ENCODED_FILE_URI)
            ) ?: return@composable

            val mode = backStackEntry.arguments?.getString(FiberRoute.ARG_EDITOR_MODE)
            val initialPreviewMode = mode != FiberRoute.MODE_EDIT

            EditorScreen(
                fileUri = fileUri,
                onClose = { navController.popBackStack() },
                initialPreviewMode = initialPreviewMode
            )
        }

        composable(route = FiberRoute.QUICKNOTE) {
            QuickNoteScreen(
                onClose = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }

        composable(route = FiberRoute.SEARCH) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onFileClick = { fileUri -> navigateToEditor(fileUri, false) },
                headerModifier = Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState("main-search-top-bar"),
                    animatedVisibilityScope = this
                )
            )
        }

        composable(route = FiberRoute.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = FiberRoute.ATTACHMENTS,
            arguments = listOf(
                navArgument(FiberRoute.ARG_LIBRARY_ID) { type = NavType.StringType }
            )
        ) {
            AttachmentManagerScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        }
    }
}

private fun decodeFileUriOrPop(
    navController: NavHostController,
    encodedFileUri: String?
): String? {
    if (encodedFileUri == null) {
        Timber.e("FiberNavGraph: missing encodedFileUri argument")
        navController.popBackStack()
        return null
    }

    return try {
        UriHelper.decodeBase64(encodedFileUri)
    } catch (e: Exception) {
        Timber.e(e, "FiberNavGraph: failed to decode encodedFileUri=%s", encodedFileUri)
        navController.popBackStack()
        null
    }
}

object FiberRoute {
    const val ARG_ENCODED_FILE_URI = "encodedFileUri"
    const val ARG_EDITOR_MODE = "mode"
    const val ARG_LIBRARY_ID = "libraryId"
    const val MODE_PREVIEW = "preview"
    const val MODE_EDIT = "edit"

    const val FILES = "files"
    const val EDITOR = "editor/{encodedFileUri}?mode={mode}"
    const val QUICKNOTE = "quicknote"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ATTACHMENTS = "attachments/{libraryId}"

    fun editor(fileUri: String): String = editor(fileUri, editMode = false)

    fun editor(fileUri: String, editMode: Boolean): String {
        val encodedUri = UriHelper.encodeBase64(fileUri)
        val mode = if (editMode) MODE_EDIT else MODE_PREVIEW
        return "editor/$encodedUri?mode=$mode"
    }

    fun attachments(libraryId: String): String = "attachments/${Uri.encode(libraryId)}"
}
