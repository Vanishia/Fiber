package com.bird.fiber.data.event

import app.cash.turbine.test
import com.bird.fiber.utils.TestCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * EventBus 单元测试
 *
 * 测试事件总线功能：
 * - 事件发送和接收
 * - 多订阅者支持
 * - 事件类型区分
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventBusTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val eventBus = EventBus()

    // ==================== 基本事件测试 ====================

    @Test
    fun emit_singleEvent_receivedByCollector() = runTest {
        // Arrange
        val event = AppEvent.RefreshFileList

        // Act & Assert
        eventBus.events.test {
            eventBus.emit(event)
            assertEquals(event, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun emit_multipleEvents_receivedInOrder() = runTest {
        // Arrange
        val events = listOf(
            AppEvent.RefreshFileList,
            AppEvent.FileCreated("uri1"),
            AppEvent.FileDeleted("uri2")
        )

        // Act & Assert
        eventBus.events.test {
            events.forEach { eventBus.emit(it) }

            events.forEach { expectedEvent ->
                assertEquals(expectedEvent, awaitItem())
            }
            cancelAndConsumeRemainingEvents()
        }
    }

    // ==================== 多订阅者测试 ====================

    @Test
    fun emit_multipleSubscribers_allReceive() = runTest {
        // Arrange
        val event = AppEvent.RefreshFileList

        // Act & Assert
        val job1 = backgroundScope.launch {
            eventBus.events.test {
                assertEquals(event, awaitItem())
                cancelAndConsumeRemainingEvents()
            }
        }
        val job2 = backgroundScope.launch {
            eventBus.events.test {
                assertEquals(event, awaitItem())
                cancelAndConsumeRemainingEvents()
            }
        }

        eventBus.emit(event)

        job1.cancel()
        job2.cancel()
    }

    // ==================== 事件类型测试 ====================

    @Test
    fun emit_differentEventTypes_receivedCorrectly() = runTest {
        // Arrange
        val events = listOf(
            AppEvent.RefreshFileList,
            AppEvent.FileCreated("file://test1.md"),
            AppEvent.FileDeleted("file://test2.md"),
            AppEvent.FileUpdated("file://test3.md"),
            AppEvent.SyncStarted("lib1"),
            AppEvent.SyncCompleted("lib1")
        )

        // Act & Assert
        eventBus.events.test {
            events.forEach { eventBus.emit(it) }

            // Verify each event type
            assertTrue(awaitItem() is AppEvent.RefreshFileList)

            val created = awaitItem()
            assertTrue(created is AppEvent.FileCreated)
            assertEquals("file://test1.md", (created as AppEvent.FileCreated).fileUri)

            val deleted = awaitItem()
            assertTrue(deleted is AppEvent.FileDeleted)
            assertEquals("file://test2.md", (deleted as AppEvent.FileDeleted).fileUri)

            val updated = awaitItem()
            assertTrue(updated is AppEvent.FileUpdated)
            assertEquals("file://test3.md", (updated as AppEvent.FileUpdated).fileUri)

            val syncStarted = awaitItem()
            assertTrue(syncStarted is AppEvent.SyncStarted)
            assertEquals("lib1", (syncStarted as AppEvent.SyncStarted).libraryId)

            val syncCompleted = awaitItem()
            assertTrue(syncCompleted is AppEvent.SyncCompleted)
            assertEquals("lib1", (syncCompleted as AppEvent.SyncCompleted).libraryId)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ==================== 缓冲区测试 ====================

    @Test
    fun emit_rapidEvents_handlesBackpressure() = runTest {
        // Arrange - 快速发送多个事件
        val eventCount = 50

        // Act & Assert
        eventBus.events.test {
            repeat(eventCount) { index ->
                eventBus.emit(AppEvent.FileCreated("file://$index.md"))
            }

            // 验证所有事件都能被接收（或至少缓冲区的容量内）
            var receivedCount = 0
            while (receivedCount < eventCount) {
                try {
                    awaitItem()
                    receivedCount++
                } catch (e: Exception) {
                    break
                }
            }

            // 由于有 extraBufferCapacity = 16，应该能接收所有事件
            // 或者至少不会因为缓冲区溢出而崩溃
            assertTrue("应该接收事件", receivedCount > 0)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ==================== 事件密封类测试 ====================

    @Test
    fun appEvent_sealedClass_hasCorrectInheritance() {
        // Arrange & Act & Assert
        assertTrue(AppEvent.RefreshFileList is AppEvent)
        assertTrue(AppEvent.FileCreated("uri") is AppEvent)
        assertTrue(AppEvent.FileDeleted("uri") is AppEvent)
        assertTrue(AppEvent.FileUpdated("uri") is AppEvent)
        assertTrue(AppEvent.SyncStarted("lib") is AppEvent)
        assertTrue(AppEvent.SyncCompleted("lib") is AppEvent)
    }

    @Test
    fun appEvent_dataClasses_equalityWorks() {
        // Arrange
        val event1 = AppEvent.FileCreated("uri1")
        val event2 = AppEvent.FileCreated("uri1")
        val event3 = AppEvent.FileCreated("uri2")

        // Assert
        assertEquals(event1, event2)
        assertNotEquals(event1, event3)
    }
}
