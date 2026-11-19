package com.bird.fiber.data.local

import android.content.ContentResolver
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagBackfillCoordinatorTest {
    @Test fun failedNotesRemainPendingAndRetryOnlyProcessesRemainingNotes() = runTest {
        val indexer = mockk<FileIndexer>()
        val resolver = mockk<ContentResolver>()
        val pending = mutableSetOf("a", "b")
        val count = MutableStateFlow(2)
        var failB = true
        every { indexer.observePendingTags() } returns count
        coEvery { indexer.countPendingTags() } answers { pending.size }
        coEvery { indexer.pendingTagUris(any(), any()) } answers {
            pending.filter { it > firstArg<String>() }.sorted()
        }
        coEvery { indexer.backfillTag(resolver, any()) } coAnswers {
            val uri = secondArg<String>()
            if (uri == "b" && failB) throw java.io.IOException("temporary failure")
            pending.remove(uri)
            count.value = pending.size
        }
        val coordinator = TagBackfillCoordinator(indexer, resolver, backgroundScope)
        coordinator.start()
        runCurrent()
        assertTrue(coordinator.state.value.failed)
        assertEquals(1, coordinator.state.value.completed)
        assertEquals(1, coordinator.state.value.pending)
        failB = false
        coordinator.start()
        runCurrent()
        assertFalse(coordinator.state.value.visible)
        coVerify(exactly = 1) { indexer.backfillTag(resolver, "a") }
        coVerify(exactly = 2) { indexer.backfillTag(resolver, "b") }
    }

    @Test fun concurrentNormalIndexRepairAutomaticallyDismissesFailedProgress() = runTest {
        val indexer = mockk<FileIndexer>()
        val resolver = mockk<ContentResolver>()
        val count = MutableStateFlow(1)
        every { indexer.observePendingTags() } returns count
        coEvery { indexer.countPendingTags() } answers { count.value }
        coEvery { indexer.pendingTagUris(any(), any()) } answers {
            if (firstArg<String>().isEmpty()) listOf("a") else emptyList()
        }
        coEvery { indexer.backfillTag(resolver, "a") } throws java.io.IOException("permission")
        val coordinator = TagBackfillCoordinator(indexer, resolver, backgroundScope)
        coordinator.start()
        runCurrent()
        assertTrue(coordinator.state.value.failed)
        count.value = 0 // A successful save or library sync rebuilt the same record.
        runCurrent()
        assertFalse(coordinator.state.value.visible)
        assertEquals(1, coordinator.state.value.completed)
    }
}
