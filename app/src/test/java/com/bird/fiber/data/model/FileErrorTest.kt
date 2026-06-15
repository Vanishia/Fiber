package com.bird.fiber.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * FileError 单元测试
 *
 * 测试错误类型和错误消息转换
 */
class FileErrorTest {

    // ==================== FileError 类型测试 ====================

    @Test
    fun permissionDenied_toString_returnsCorrectMessage() {
        // Arrange
        val error = FileError.PermissionDenied("content://test/file.md")

        // Act
        val message = error.toString()

        // Assert
        assertTrue("应该包含 URI", message.contains("content://test/file.md"))
        assertTrue("应该包含权限提示", message.contains("没有权限"))
    }

    @Test
    fun notFound_toString_returnsCorrectMessage() {
        // Arrange
        val error = FileError.NotFound("content://test/missing.md")

        // Act
        val message = error.toString()

        // Assert
        assertTrue("应该包含 URI", message.contains("content://test/missing.md"))
        assertTrue("应该包含不存在提示", message.contains("不存在"))
    }

    @Test
    fun ioFailed_toString_returnsCorrectMessage() {
        // Arrange
        val cause = Exception("Disk full")
        val error = FileError.IOFailed("content://test/file.md", cause)

        // Act
        val message = error.toString()

        // Assert
        assertTrue("应该包含 URI", message.contains("content://test/file.md"))
        assertTrue("应该包含操作失败提示", message.contains("操作失败"))
        assertTrue("应该包含原因", message.contains("Disk full"))
    }

    @Test
    fun unknown_withCause_toString_returnsCorrectMessage() {
        // Arrange
        val cause = Exception("Unknown error")
        val error = FileError.Unknown("发生未知错误", cause)

        // Act
        val message = error.toString()

        // Assert
        assertTrue("应该包含错误消息", message.contains("发生未知错误"))
        assertTrue("应该包含原因", message.contains("Unknown error"))
    }

    @Test
    fun unknown_withoutCause_toString_returnsCorrectMessage() {
        // Arrange
        val error = FileError.Unknown("发生未知错误")

        // Act
        val message = error.toString()

        // Assert
        assertEquals("应该只包含错误消息", "发生未知错误", message)
    }

    // ==================== toUserMessage 扩展函数测试 ====================

    @Test
    fun toUserMessage_permissionDenied_returnsUserFriendlyMessage() {
        // Arrange
        val error = FileError.PermissionDenied("content://test/file.md")

        // Act
        val message = error.toUserMessage()

        // Assert
        assertEquals("没有权限访问", message)
    }

    @Test
    fun toUserMessage_notFound_returnsUserFriendlyMessage() {
        // Arrange
        val error = FileError.NotFound("content://test/missing.md")

        // Act
        val message = error.toUserMessage()

        // Assert
        assertEquals("文件或文件夹不存在", message)
    }

    @Test
    fun toUserMessage_ioFailed_returnsUserFriendlyMessage() {
        // Arrange
        val cause = Exception("Disk full")
        val error = FileError.IOFailed("content://test/file.md", cause)

        // Act
        val message = error.toUserMessage()

        // Assert
        assertTrue("应该包含操作失败", message.contains("操作失败"))
        assertTrue("应该包含原因", message.contains("Disk full"))
    }

    @Test
    fun toUserMessage_ioFailedWithoutCause_returnsUserFriendlyMessage() {
        // Arrange
        val cause = Exception()  // 无消息的异常
        val error = FileError.IOFailed("content://test/file.md", cause)

        // Act
        val message = error.toUserMessage()

        // Assert
        assertTrue("应该包含操作失败", message.contains("操作失败"))
        assertTrue("应该包含未知原因", message.contains("未知原因"))
    }

    @Test
    fun toUserMessage_unknown_returnsUserFriendlyMessage() {
        // Arrange
        val error = FileError.Unknown("发生未知错误")

        // Act
        val message = error.toUserMessage()

        // Assert
        assertEquals("发生未知错误", message)
    }

    // ==================== 边界情况测试 ====================

    @Test
    fun fileError_differentInstances_notEqual() {
        // Arrange
        val error1 = FileError.PermissionDenied("uri1")
        val error2 = FileError.PermissionDenied("uri2")

        // Assert
        assertNotEquals("不同 URI 的错误应该不相等", error1, error2)
    }

    @Test
    fun fileError_sameInstances_equal() {
        // Arrange
        val error1 = FileError.PermissionDenied("uri1")
        val error2 = FileError.PermissionDenied("uri1")

        // Assert
        assertEquals("相同 URI 的错误应该相等", error1, error2)
    }

    @Test
    fun fileError_differentTypes_notEqual() {
        // Arrange
        val error1 = FileError.PermissionDenied("uri1")
        val error2 = FileError.NotFound("uri1")

        // Assert
        assertNotEquals("不同类型的错误应该不相等", error1, error2)
    }
}
