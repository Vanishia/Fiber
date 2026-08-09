package com.bird.fiber.data.local

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bird.fiber.data.local.library.FiberDatabase
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.toTarget
import com.bird.fiber.data.model.Attachment
import com.bird.fiber.data.model.FileResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentRepositorySafTest {

    @Test
    fun copyAndDelete_usesRealSafProviderAndPreservesOriginalName() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val database = FiberDatabase.getInstance(context)
        val libraryRepository = LibraryRepository(database.libraryDao())
        val activeLibrary = libraryRepository.getActiveLibrary().first()
        assumeNotNull(activeLibrary)
        val target = requireNotNull(activeLibrary).toTarget()
        val repository = AttachmentRepositoryImpl(context, libraryRepository)

        var sourceUri: Uri? = null
        val createdAttachments = mutableListOf<Attachment>()
        try {
            sourceUri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "diagram.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FiberInstrumentation")
                }
            )
            assertNotNull(sourceUri)
            resolver.openOutputStream(requireNotNull(sourceUri))?.use { output ->
                output.write("fiber-saf-test".toByteArray())
            }

            repeat(2) {
                val copyResult = repository.copyImage(requireNotNull(sourceUri).toString(), target)
                require(copyResult is FileResult.Success) { "copy failed: $copyResult" }
                createdAttachments += copyResult.data
            }

            assertTrue(createdAttachments.all { it.displayName.matches(FILE_NAME_PATTERN) })
            assertEquals(2, createdAttachments.map { it.uri }.distinct().size)
            assertTrue(canRead(resolver, createdAttachments[0].uri))
            assertTrue(canRead(resolver, createdAttachments[1].uri))

            val deleteResult = repository.delete(createdAttachments[0].uri)
            assertTrue(deleteResult is FileResult.Success)
            assertFalse(canRead(resolver, createdAttachments[0].uri))
            assertTrue(canRead(resolver, createdAttachments[1].uri))
            createdAttachments.removeAt(0)
        } finally {
            createdAttachments.forEach { repository.delete(it.uri) }
            sourceUri?.let { resolver.delete(it, null, null) }
        }
    }

    companion object {
        private val FILE_NAME_PATTERN = Regex("diagram-\\d{8}-\\d{6}-[a-f0-9]{4}\\.png")

        private fun canRead(resolver: android.content.ContentResolver, uri: String): Boolean {
            return runCatching {
                resolver.openInputStream(Uri.parse(uri))?.use { it.read() }
            }.getOrNull() != null
        }
    }
}
