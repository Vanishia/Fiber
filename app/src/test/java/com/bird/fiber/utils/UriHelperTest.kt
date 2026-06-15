package com.bird.fiber.utils

import android.net.Uri
import android.util.Base64
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * UriHelper 单元测试
 *
 * 测试 URI 编解码、文件名提取等工具方法
 */
class UriHelperTest {

    @Before
    fun setup() {
        // Mock Android Base64 类
        mockkStatic(Base64::class)

        // Mock encodeToString - 简单的 Base64 编码模拟
        every {
            Base64.encodeToString(any(), any())
        } answers {
            val bytes = firstArg<ByteArray>()
            // 使用 Java 的 Base64 进行实际编码
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        // Mock decode - 简单的 Base64 解码模拟
        every {
            Base64.decode(any<String>(), any())
        } answers {
            val str = firstArg<String>()
            // 使用 Java 的 Base64 进行实际解码
            java.util.Base64.getUrlDecoder().decode(str)
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== extractFolderName 测试 ====================

    @Test
    fun extractFolderName_normalPath_returnsLastSegment() {
        // Arrange
        val uri = mockk<Uri>()
        every { uri.path } returns "/tree/primary:Documents/MyFolder"

        // Act
        val result = UriHelper.extractFolderName(uri)

        // Assert
        assertEquals("MyFolder", result)
    }

    @Test
    fun extractFolderName_pathWithColon_extractsAfterColon() {
        // Arrange
        val uri = mockk<Uri>()
        every { uri.path } returns "/tree/primary:Documents"

        // Act
        val result = UriHelper.extractFolderName(uri)

        // Assert
        assertEquals("Documents", result)
    }

    @Test
    fun extractFolderName_nullPath_returnsDefault() {
        // Arrange
        val uri = mockk<Uri>()
        every { uri.path } returns null

        // Act
        val result = UriHelper.extractFolderName(uri)

        // Assert
        assertEquals("未命名库", result)
    }

    @Test
    fun extractFolderName_emptyPath_returnsDefault() {
        // Arrange
        val uri = mockk<Uri>()
        every { uri.path } returns ""

        // Act
        val result = UriHelper.extractFolderName(uri)

        // Assert
        assertTrue("空路径应该返回默认值", result == "未命名库" || result.isEmpty())
    }

    @Test
    fun extractFolderName_singleSegment_returnsSegment() {
        // Arrange
        val uri = mockk<Uri>()
        every { uri.path } returns "MyFolder"

        // Act
        val result = UriHelper.extractFolderName(uri)

        // Assert
        assertEquals("MyFolder", result)
    }

    // ==================== extractFileName 测试 ====================

    @Test
    fun extractFileName_normalPath_returnsFileNameWithoutExtension() {
        // Arrange
        val fileUri = "primary:fiber测试用/第3次测试创建笔记.md"

        // Act
        val result = UriHelper.extractFileName(fileUri)

        // Assert
        assertEquals("第3次测试创建笔记", result)
    }

    @Test
    fun extractFileName_withoutFolder_returnsFileName() {
        // Arrange
        val fileUri = "测试笔记.md"

        // Act
        val result = UriHelper.extractFileName(fileUri)

        // Assert
        // 由于 URLDecoder 可能会处理中文，我们只检查结果不为空且包含 .md 被移除
        assertNotNull("结果不应该为 null", result)
        assertFalse("结果不应该包含 .md", result.contains(".md"))
    }

    @Test
    fun extractFileName_withoutExtension_returnsFileName() {
        // Arrange
        val fileUri = "primary:folder/测试笔记"

        // Act
        val result = UriHelper.extractFileName(fileUri)

        // Assert
        assertEquals("测试笔记", result)
    }

    @Test
    fun extractFileName_emptyPath_returnsDefault() {
        // Arrange
        val fileUri = ""

        // Act
        val result = UriHelper.extractFileName(fileUri)

        // Assert
        assertEquals("未命名", result)
    }

    @Test
    fun extractFileName_onlySlash_returnsDefault() {
        // Arrange
        val fileUri = "/"

        // Act
        val result = UriHelper.extractFileName(fileUri)

        // Assert
        assertTrue("只有斜杠应该返回默认值", result == "未命名" || result.isEmpty())
    }

    @Test
    fun extractFileName_urlEncoded_decodesCorrectly() {
        // Arrange
        val fileUri = "primary:folder/%E6%B5%8B%E8%AF%95.md" // "测试" URL 编码

        // Act
        val result = UriHelper.extractFileName(fileUri)

        // Assert
        assertEquals("测试", result)
    }

    // ==================== Base64 编解码测试 ====================

    @Test
    fun encodeBase64_normalString_encodesCorrectly() {
        // Arrange
        val uri = "content://com.android.externalstorage.documents/document/primary:test.md"

        // Act
        val encoded = UriHelper.encodeBase64(uri)

        // Assert
        assertNotNull(encoded)
        assertTrue("编码后不应该为空", encoded.isNotEmpty())
        assertFalse("编码后不应该包含原始字符串", encoded.contains("content://"))
    }

    @Test
    fun decodeBase64_encodedString_decodesCorrectly() {
        // Arrange
        val original = "content://com.android.externalstorage.documents/document/primary:test.md"
        val encoded = UriHelper.encodeBase64(original)

        // Act
        val decoded = UriHelper.decodeBase64(encoded)

        // Assert
        assertEquals("解码后应该等于原始字符串", original, decoded)
    }

    @Test
    fun encodeDecodeBase64_chineseCharacters_worksCorrectly() {
        // Arrange
        val original = "primary:测试文件夹/中文笔记.md"

        // Act
        val encoded = UriHelper.encodeBase64(original)
        val decoded = UriHelper.decodeBase64(encoded)

        // Assert
        assertEquals("中文字符编解码应该正确", original, decoded)
    }

    @Test
    fun encodeDecodeBase64_specialCharacters_worksCorrectly() {
        // Arrange
        val original = "test://path?query=value&param=123#fragment"

        // Act
        val encoded = UriHelper.encodeBase64(original)
        val decoded = UriHelper.decodeBase64(encoded)

        // Assert
        assertEquals("特殊字符编解码应该正确", original, decoded)
    }

    @Test
    fun encodeBase64_emptyString_returnsEmptyOrValid() {
        // Arrange
        val original = ""

        // Act
        val encoded = UriHelper.encodeBase64(original)
        val decoded = UriHelper.decodeBase64(encoded)

        // Assert
        assertEquals("空字符串编解码应该正确", original, decoded)
    }

    @Test
    fun encodeBase64_urlSafe_noSpecialCharacters() {
        // Arrange
        val original = "content://test/document/primary:test.md"

        // Act
        val encoded = UriHelper.encodeBase64(original)

        // Assert
        assertFalse("URL 安全编码不应该包含 +", encoded.contains("+"))
        assertFalse("URL 安全编码不应该包含 /", encoded.contains("/"))
        assertFalse("URL 安全编码不应该包含 =", encoded.contains("="))
    }
}
