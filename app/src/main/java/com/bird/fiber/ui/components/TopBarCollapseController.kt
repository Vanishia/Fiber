package com.bird.fiber.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 顶栏随列表滚动收起/展开的控制器（类似手机浏览器导航栏）
 *
 * 手指上滑浏览内容时，顶栏跟随手势向上平移收起；手指下滑时重新滑出。
 * 滚动停止后（[settle]）按当前偏移吸附到完全展开或完全收起，避免顶栏停在半隐藏状态。
 *
 * 用法：把 [nestedScrollConnection] 挂到列表容器上，顶栏通过
 * `Modifier.graphicsLayer { translationY = controller.offsetPx }` 应用偏移，
 * 并用 `Modifier.onSizeChanged` 把顶栏高度写入 [heightPx]。
 */
class TopBarCollapseController internal constructor(
    private val scope: CoroutineScope
) {
    private val animatable = Animatable(0f)

    /** 顶栏总高度（px），由顶栏的 onSizeChanged 写入 */
    var heightPx by mutableFloatStateOf(0f)

    /** 当前纵向偏移（px）：0 = 完全展开，-heightPx = 完全收起 */
    val offsetPx: Float get() = animatable.value

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val height = heightPx
            if (height <= 0f || available.y == 0f) return Offset.Zero
            val target = (animatable.value + available.y).coerceIn(-height, 0f)
            scope.launch { animatable.snapTo(target) }
            // 不消费滚动量，列表照常滚动
            return Offset.Zero
        }
    }

    /** 滚动停止后吸附到最近的端点（完全展开或完全收起） */
    fun settle() {
        val height = heightPx
        if (height <= 0f) return
        val target = if (animatable.value < -height / 2f) -height else 0f
        scope.launch { animatable.animateTo(target, tween(durationMillis = 200)) }
    }
}

@Composable
fun rememberTopBarCollapseController(): TopBarCollapseController {
    val scope = rememberCoroutineScope()
    return remember(scope) { TopBarCollapseController(scope) }
}
