package com.bird.fiber.ui.screens.filelist.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 滑动配置
 */
object SwipeConfig {
    // 横向位移必须是纵向位移的几倍才触发滑动（默认 2.5 倍，避免误触）
    const val DIRECTION_RATIO = 2.5f

    // 触发编辑的滑动距离阈值（右滑，dp）
    const val TRIGGER_EDIT_THRESHOLD_DP = 60f

    // 触发删除的滑动距离阈值（左滑，dp）
    const val TRIGGER_DELETE_THRESHOLD_DP = 70f

    // 最大滑动距离（dp）
    const val MAX_SWIPE_DP = 100f
}

/**
 * 可左滑删除、右滑编辑的容器组件
 *
 * 特点：
 * 1. 严格的方向判断：只有当 abs(dx) > abs(dy) * 2.5 时才响应横向滑动
 * 2. 滑动阈值：必须滑动到 80dp 以上才触发动作，否则自动回弹
 * 3. 平滑动画：使用 Animatable 实现流畅的回弹效果
 *
 * @param onSwipeLeft 左滑触发的回调（删除）
 * @param onSwipeRight 右滑触发的回调（编辑）
 * @param content 内容区域
 */
@Composable
fun SwipeableContainer(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 转换 dp 到 px
    val triggerEditThreshold = with(density) { SwipeConfig.TRIGGER_EDIT_THRESHOLD_DP.dp.toPx() }
    val triggerDeleteThreshold = with(density) { SwipeConfig.TRIGGER_DELETE_THRESHOLD_DP.dp.toPx() }
    val maxSwipe = with(density) { SwipeConfig.MAX_SWIPE_DP.dp.toPx() }

    // 当前偏移量
    val offsetX = remember { Animatable(0f) }

    // 手势状态
    var startX by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }
    var isHorizontalSwipe by remember { mutableStateOf(false) }
    var hasDecidedDirection by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 背景层（左侧编辑图标，右侧删除图标）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // 左侧：绿色圆形背景 + 笔图标（右滑显示）- 临时注释掉，避免与侧边栏冲突
            // if (offsetX.value > 0) {
            //     Box(
            //         modifier = Modifier
            //             .align(Alignment.CenterStart)
            //             .size(48.dp)
            //             .background(Color(0xFF4CAF50), CircleShape),
            //         contentAlignment = Alignment.Center
            //     ) {
            //         Icon(
            //             imageVector = Icons.Default.Edit,
            //             contentDescription = "编辑",
            //             tint = Color.White,
            //             modifier = Modifier.size(24.dp)
            //         )
            //     }
            // }

            // 右侧：红色圆形背景 + 垃圾桶图标（左滑显示）
            if (offsetX.value < 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .background(Color.Red, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 内容层（可滑动）
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            // 等待手指按下
                            val down = awaitFirstDown()
                            val initialX = down.position.x
                            val initialY = down.position.y

                            // 等待滑动超过 touch slop
                            val change = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                                val dx = change.position.x - initialX
                                val dy = change.position.y - initialY
                                val absDx = abs(dx)
                                val absDy = abs(dy)

                                // 严格判断：横向位移必须是纵向位移的 2.5 倍以上
                                if (absDx > absDy * SwipeConfig.DIRECTION_RATIO) {
                                    // 屏幕左半边的右滑留给侧边栏呼出
                                    val isRightSwipe = dx > 0
                                    val inLeftHalf = initialX < 400f

                                    // 如果是左半边右滑，不消费此手势（让事件传递给侧边栏）
                                    if (inLeftHalf && isRightSwipe) {
                                        // 不调用 consume()，让事件冒泡
                                    } else {
                                        // 消费此手势
                                        change.consume()
                                    }
                                }
                            }

                            // 如果 change 为 null，说明手势被取消或没有满足条件，重新开始循环
                            if (change == null) continue

                            // 检查手势是否被消费（通过检查 change.isConsumed）
                            if (!change.isConsumed) {
                                // 手势未被消费，重新开始循环
                                continue
                            }

                            // 已确认是水平滑动，开始处理拖动
                            isHorizontalSwipe = true
                            hasDecidedDirection = true

                            // 处理拖动
                            drag(change.id) { dragChange ->
                                val dragAmount = dragChange.position.x - dragChange.previousPosition.x
                                dragChange.consume()

                                scope.launch {
                                    val newOffset = (offsetX.value + dragAmount).coerceIn(-maxSwipe, maxSwipe)
                                    offsetX.snapTo(newOffset)
                                }
                            }

                            // 拖动结束，判断是否触发动作
                            scope.launch {
                                val currentOffset = offsetX.value

                                if (isHorizontalSwipe) {
                                    when {
                                        // 右滑编辑功能临时注释掉
                                        // currentOffset > triggerEditThreshold -> {
                                        //     onSwipeRight()
                                        //     offsetX.animateTo(0f, tween(300))
                                        // }
                                        currentOffset < -triggerDeleteThreshold -> {
                                            onSwipeLeft()
                                            offsetX.animateTo(0f, tween(300))
                                        }
                                        else -> {
                                            offsetX.animateTo(0f, tween(300))
                                        }
                                    }
                                } else {
                                    offsetX.animateTo(0f, tween(300))
                                }

                                hasDecidedDirection = false
                                isHorizontalSwipe = false
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }
}
