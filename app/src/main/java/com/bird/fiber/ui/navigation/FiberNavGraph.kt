package com.bird.fiber.ui.navigation

import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bird.fiber.ui.screens.editor.EditorScreen
import com.bird.fiber.ui.screens.attachments.AttachmentManagerScreen
import com.bird.fiber.ui.screens.heatmap.HeatmapScreen
import com.bird.fiber.ui.screens.main.MainScreenContainer
import com.bird.fiber.ui.screens.notelist.NoteListRouteScreen
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
    var lastUserNavigationAt by remember { mutableLongStateOf(0L) }
    var lastUserNavigationRoute by remember { mutableStateOf<String?>(null) }
    var lastUserPopAt by remember { mutableLongStateOf(0L) }

    /**
     * 防抖只拦截"同一目的地的快速重复导航"（防双击重复入栈）。
     * 不同目的地必须放行——否则刚进入页面就点卡片会被误吞
     */
    fun acceptUserNavigation(route: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (route == lastUserNavigationRoute &&
            now - lastUserNavigationAt < USER_NAVIGATION_DEBOUNCE_MS
        ) {
            return false
        }
        lastUserNavigationAt = now
        lastUserNavigationRoute = route
        return true
    }

    fun navigateFromUser(route: String) {
        if (acceptUserNavigation(route)) {
            navController.navigateSafely(route)
        }
    }

    /**
     * 进入某日笔记页
     *
     * 当日页之间切换必须用替换语义：navigation 2.8.x 的 launchSingleTop
     * 同路由导航复用 entry 时只更新 arguments Bundle，不同步 SavedStateHandle，
     * ViewModel 拿不到新日期；popUpTo 自身 + inclusive 保证产生新 entry。
     * 栈中没有当日页时 popUpTo 不匹配任何项，等效普通 push
     */
    fun navigateToDayNotes(date: java.time.LocalDate) {
        val route = FiberRoute.dayNotes(date)
        if (acceptUserNavigation(route)) {
            navController.navigate(route) {
                popUpTo(FiberRoute.DAY_NOTES) { inclusive = true }
            }
        }
    }

    fun popFromUser() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastUserPopAt < USER_NAVIGATION_DEBOUNCE_MS) {
            return
        }
        lastUserPopAt = now
        navController.popBackStackSafely()
    }

    val navigateToEditor: (String, Boolean) -> Unit = { fileUri, editMode ->
        navigateFromUser(FiberRoute.editor(fileUri, editMode))
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
                onSearchClick = { navigateFromUser(FiberRoute.SEARCH) },
                onSettingsClick = { navigateFromUser(FiberRoute.SETTINGS) },
                onManageAttachments = { libraryId ->
                    navigateFromUser(FiberRoute.attachments(libraryId))
                },
                onAllNotesClick = { navigateFromUser(FiberRoute.ALL_NOTES) },
                onHeatmapDayClick = { date ->
                    navigateToDayNotes(date)
                },
                onHeatmapClick = { navigateFromUser(FiberRoute.HEATMAP) },
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
            ),
            // 打开笔记：编辑页轻微上滑并淡入，覆盖在列表之上；
            // 返回时下滑淡出，重新露出列表。纯几何/透明度动画，日夜间模式体验一致。
            enterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    slideInVertically(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 12 }
                    )
            },
            exitTransition = { fadeOut(animationSpec = tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = tween(180)) },
            popExitTransition = {
                fadeOut(animationSpec = tween(200)) +
                    slideOutVertically(
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        targetOffsetY = { it / 12 }
                    )
            }
        ) { backStackEntry ->
            val fileUri = decodeFileUriOrPop(
                navController = navController,
                encodedFileUri = backStackEntry.arguments?.getString(FiberRoute.ARG_ENCODED_FILE_URI)
            ) ?: return@composable

            val mode = backStackEntry.arguments?.getString(FiberRoute.ARG_EDITOR_MODE)
            val initialPreviewMode = mode != FiberRoute.MODE_EDIT

            EditorScreen(
                fileUri = fileUri,
                onClose = ::popFromUser,
                initialPreviewMode = initialPreviewMode
            )
        }

        composable(route = FiberRoute.QUICKNOTE) {
            QuickNoteScreen(
                onClose = ::popFromUser,
                onSaveSuccess = ::popFromUser
            )
        }

        composable(route = FiberRoute.ALL_NOTES) {
            NoteListRouteScreen(
                onBackClick = ::popFromUser,
                onFileClick = navigateToEditor,
                onSearchClick = { navigateFromUser(FiberRoute.SEARCH) },
                onAddLibrary = onAddLibrary,
                onSettingsClick = { navigateFromUser(FiberRoute.SETTINGS) },
                onManageAttachments = { libraryId ->
                    navigateFromUser(FiberRoute.attachments(libraryId))
                },
                // 已在全部笔记页，只需收起抽屉
                onAllNotesClick = { },
                onHeatmapClick = { navigateFromUser(FiberRoute.HEATMAP) },
                onHeatmapDayClick = { date ->
                    navigateToDayNotes(date)
                },
                // 切库后回主界面，主界面会展示新激活的库
                onLibrarySelected = { popFromUser() }
            )
        }

        composable(
            route = FiberRoute.DAY_NOTES,
            arguments = listOf(
                navArgument(FiberRoute.ARG_DATE) { type = NavType.StringType }
            )
        ) {
            NoteListRouteScreen(
                onBackClick = ::popFromUser,
                onFileClick = navigateToEditor,
                onSearchClick = { navigateFromUser(FiberRoute.SEARCH) },
                onAddLibrary = onAddLibrary,
                onSettingsClick = { navigateFromUser(FiberRoute.SETTINGS) },
                onManageAttachments = { libraryId ->
                    navigateFromUser(FiberRoute.attachments(libraryId))
                },
                onAllNotesClick = { navigateFromUser(FiberRoute.ALL_NOTES) },
                onHeatmapClick = { navigateFromUser(FiberRoute.HEATMAP) },
                onHeatmapDayClick = { date ->
                    navigateToDayNotes(date)
                },
                onLibrarySelected = { popFromUser() }
            )
        }

        composable(route = FiberRoute.HEATMAP) {
            HeatmapScreen(
                onBackClick = ::popFromUser,
                onDayClick = { date -> navigateToDayNotes(date) }
            )
        }

        composable(route = FiberRoute.SEARCH) {
            SearchScreen(
                onBackClick = ::popFromUser,
                onFileClick = { fileUri -> navigateToEditor(fileUri, false) },
                headerModifier = Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState("main-search-top-bar"),
                    animatedVisibilityScope = this
                )
            )
        }

        composable(route = FiberRoute.SETTINGS) {
            SettingsScreen(
                onBackClick = ::popFromUser
            )
        }

        composable(
            route = FiberRoute.ATTACHMENTS,
            arguments = listOf(
                navArgument(FiberRoute.ARG_LIBRARY_ID) { type = NavType.StringType }
            )
        ) {
            AttachmentManagerScreen(
                onBackClick = ::popFromUser
            )
        }
        }
    }
}

private fun NavHostController.navigateSafely(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

private fun NavHostController.popBackStackSafely() {
    val currentRoute = currentBackStackEntry?.destination?.route ?: return
    if (currentRoute == FiberRoute.FILES) {
        Timber.w("FiberNavGraph: ignored popBackStack on start destination")
        return
    }

    if (!popBackStack()) {
        Timber.w("FiberNavGraph: popBackStack returned false for route=%s", currentRoute)
    }
}

private fun decodeFileUriOrPop(
    navController: NavHostController,
    encodedFileUri: String?
): String? {
    if (encodedFileUri == null) {
        Timber.e("FiberNavGraph: missing encodedFileUri argument")
        navController.popBackStackSafely()
        return null
    }

    return try {
        UriHelper.decodeBase64(encodedFileUri)
    } catch (e: Exception) {
        Timber.e(e, "FiberNavGraph: failed to decode encodedFileUri=%s", encodedFileUri)
        navController.popBackStackSafely()
        null
    }
}

object FiberRoute {
    const val ARG_ENCODED_FILE_URI = "encodedFileUri"
    const val ARG_EDITOR_MODE = "mode"
    const val ARG_LIBRARY_ID = "libraryId"
    const val ARG_DATE = "date"
    const val MODE_PREVIEW = "preview"
    const val MODE_EDIT = "edit"

    const val FILES = "files"
    const val EDITOR = "editor/{encodedFileUri}?mode={mode}"
    const val QUICKNOTE = "quicknote"
    const val ALL_NOTES = "all_notes"
    const val DAY_NOTES = "day_notes/{date}"
    const val HEATMAP = "heatmap"
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

    fun dayNotes(date: java.time.LocalDate): String = "day_notes/$date"
}

private const val USER_NAVIGATION_DEBOUNCE_MS = 300L
