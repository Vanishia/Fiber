package com.bird.fiber.data.local

import android.content.ContentResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

data class TagBackfillState(
    val running: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val pending: Int = 0,
    val failed: Boolean = false
) {
    val visible get() = running || pending > 0
}

@Singleton
class TagBackfillCoordinator internal constructor(
    private val indexer: FileIndexer,
    private val contentResolver: ContentResolver,
    private val scope: CoroutineScope
) {
    @Inject constructor(indexer: FileIndexer, contentResolver: ContentResolver) :
        this(indexer, contentResolver, CoroutineScope(SupervisorJob() + Dispatchers.IO))
    private var job: Job? = null
    private val mutableState = MutableStateFlow(TagBackfillState())
    val state: StateFlow<TagBackfillState> = mutableState.asStateFlow()

    init {
        scope.launch {
            indexer.observePendingTags().collect { remaining ->
                mutableState.update { current ->
                    if (!current.visible) current else {
                        val total = maxOf(current.total, remaining)
                        current.copy(total = total, completed = total - remaining, pending = remaining,
                            running = current.running && remaining > 0, failed = current.failed && remaining > 0)
                    }
                }
            }
        }
    }

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            mutableState.value = TagBackfillState(running = true)
            var total = 0
            try {
                total = indexer.countPendingTags()
                mutableState.value = TagBackfillState(true, total, pending = total)
                var after = ""
                while (true) {
                    val uris = indexer.pendingTagUris(after)
                    if (uris.isEmpty()) break
                    for (uri in uris) {
                        try {
                            indexer.backfillTag(contentResolver, uri)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Failed records stay below the parser version and can be retried.
                        }
                        after = uri
                        val remaining = indexer.countPendingTags()
                        total = maxOf(total, remaining)
                        mutableState.value = TagBackfillState(true, total,
                            (total - remaining).coerceAtLeast(0), remaining)
                        yield() // Release the index lock between individual notes.
                    }
                }
                val remaining = indexer.countPendingTags()
                mutableState.value = TagBackfillState(false, total,
                    (total - remaining).coerceAtLeast(0), remaining, remaining > 0)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = TagBackfillState(false, total, pending = maxOf(total, 1), failed = true)
            }
        }
    }
}
