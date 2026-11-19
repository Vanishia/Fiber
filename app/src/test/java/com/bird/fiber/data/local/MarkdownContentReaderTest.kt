package com.bird.fiber.data.local

import android.content.ContentResolver
import android.net.Uri
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException

class MarkdownContentReaderTest {
    private val resolver = mockk<ContentResolver>()
    private val uri = mockk<Uri>(relaxed = true)
    private val reader = MarkdownContentReader()

    @Test fun actualEmptyFileIsSuccessfulAndLineEndingsArePreserved() {
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf())
        assertEquals("", reader.read(resolver, uri))
        val raw = "#标签\r\n正文\r\n\r\n"
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(raw.toByteArray())
        assertEquals(raw, reader.read(resolver, uri))
    }

    @Test fun unavailableStreamsAndReadFailuresAreNotEmptyContent() {
        every { resolver.openInputStream(uri) } returns null
        assertThrows(FileNotFoundException::class.java) { reader.read(resolver, uri) }
        every { resolver.openInputStream(uri) } throws IOException("read failed")
        assertThrows(IOException::class.java) { reader.read(resolver, uri) }
    }
}
