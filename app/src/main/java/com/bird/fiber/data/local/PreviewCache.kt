package com.bird.fiber.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreviewCache @Inject constructor() {

    companion object {
        private const val MAX_CACHE_SIZE = 100
    }

    private val cache = object : LinkedHashMap<String, String>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun getPreview(fileUri: String): String? {
        return synchronized(cache) {
            cache[fileUri]
        }
    }

    fun setPreview(fileUri: String, preview: String) {
        var updatedVersion: Long? = null

        synchronized(cache) {
            if (cache[fileUri] != preview) {
                cache[fileUri] = preview
                _version.update {
                    (it + 1).also { nextVersion -> updatedVersion = nextVersion }
                }
            }
        }

        updatedVersion?.let { version ->
            Timber.d("PreviewCache: cache preview $fileUri (version=$version)")
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
        _version.value = 0
        Timber.d("PreviewCache: cleared all cache")
    }

    fun remove(fileUri: String) {
        synchronized(cache) {
            cache.remove(fileUri)
        }
        Timber.d("PreviewCache: removed cache $fileUri")
    }
}
