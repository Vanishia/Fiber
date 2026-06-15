package com.bird.fiber.data.event

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用事件总线
 *
 * 职责：
 * - 提供全局事件广播机制
 * - 解耦 ViewModel 之间的通信
 *
 * 使用 SharedFlow 实现多播：
 * - 多个订阅者可以同时监听
 * - 新订阅者不会收到历史事件
 * - 粘性事件需要特殊处理（本例不需要）
 */
@Singleton
class EventBus @Inject constructor() {

    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,  // 不重放历史事件
        onBufferOverflow = BufferOverflow.DROP_OLDEST,  // 缓冲区溢出时丢弃最旧的事件
        extraBufferCapacity = 16  // 缓冲区大小
    )

    /**
     * 事件流，供 ViewModel 监听
     */
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    /**
     * 发送事件
     *
     * @param event 要发送的事件
     */
    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }
}
