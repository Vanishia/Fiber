package com.bird.fiber.ui.screens.importing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.bird.fiber.ui.navigation.FiberRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/** 分享可能在后台读取/保存完成，等 Activity 和当前页面都恢复、转场结束后再导航。 */
@Composable
internal fun ImportNavigationEffect(
    navController: NavHostController,
    pendingEditor: StateFlow<String?>,
    onEditorOpened: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(navController, lifecycleOwner, pendingEditor) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            navController.currentBackStackEntryFlow.collectLatest { entry ->
                entry.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    pendingEditor.filterNotNull().collect { fileUri ->
                        // 新建独立 entry，保留原编辑器的 ViewModel 和未保存草稿。
                        navController.navigate(FiberRoute.editor(fileUri, editMode = true))
                        onEditorOpened(fileUri)
                    }
                }
            }
        }
    }
}
