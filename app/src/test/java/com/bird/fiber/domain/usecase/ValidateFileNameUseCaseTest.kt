package com.bird.fiber.domain.usecase

import org.junit.Assert.*
import org.junit.Test

/**
 * ValidateFileNameUseCase 单元测试
 *
 * 测试文件名验证逻辑：
 * - 空文件名检查
 * - .md 后缀检查
 * - 返回类型验证
 */
class ValidateFileNameUseCaseTest {

    private val useCase = ValidateFileNameUseCase()

    // ==================== 基本验证规则测试 ====================

    @Test
    fun invoke_validFileNameWithMdSuffix_returnsSuccess() {
        // Arrange
        val fileName = "测试文件.md"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(fileName, result.getOrNull())
    }

    @Test
    fun invoke_validFileNameWithUpperCaseMdSuffix_returnsSuccess() {
        // Arrange
        val fileName = "测试文件.MD"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun invoke_validFileNameWithMixedCaseMdSuffix_returnsSuccess() {
        // Arrange
        val fileName = "测试文件.Md"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun invoke_fileNameWithoutMdSuffix_returnsFailure() {
        // Arrange
        val fileName = "测试文件"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertEquals("文件名必须以 .md 结尾", exception?.message)
    }

    @Test
    fun invoke_fileNameWithWrongExtension_returnsFailure() {
        // Arrange
        val fileName = "测试文件.txt"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isFailure)
    }

    // ==================== 空文件名测试 ====================

    @Test
    fun invoke_emptyFileName_returnsFailure() {
        // Arrange
        val fileName = ""

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertEquals("文件名不能为空", exception?.message)
    }

    @Test
    fun invoke_blankFileName_returnsFailure() {
        // Arrange
        val fileName = "   "

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertEquals("文件名不能为空", exception?.message)
    }

    @Test
    fun invoke_whitespaceOnlyFileNameWithMdSuffix_returnsSuccess() {
        // Arrange - 纯空格但带 .md 后缀应该通过
        val fileName = "   .md"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isSuccess)
    }

    // ==================== 边界情况测试 ====================

    @Test
    fun invoke_fileNameWithOnlyMdSuffix_returnsSuccess() {
        // Arrange
        val fileName = ".md"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun invoke_singleCharFileNameWithMdSuffix_returnsSuccess() {
        // Arrange
        val fileName = "a.md"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun invoke_longFileNameWithMdSuffix_returnsSuccess() {
        // Arrange
        val fileName = "a".repeat(200) + ".md"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isSuccess)
    }

    // ==================== 特殊字符测试 ====================

    @Test
    fun invoke_fileNameWithChineseCharactersAndMdSuffix_returnsSuccess() {
        // Arrange
        val fileName = "中文笔记.md"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun invoke_fileNameWithSpecialCharactersAndMdSuffix_returnsSuccess() {
        // Arrange - 特殊字符应该允许
        val fileName = "my-note_2025 (v1).md"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun invoke_fileNameWithPathSeparatorsAndMdSuffix_returnsSuccess() {
        // Arrange - 根据实际实现，路径分隔符是允许的
        // 实际验证应该在调用 UseCase 之前进行
        val fileName = "folder/subfolder/note.md"

        // Act
        val result = useCase(fileName)

        // Assert - 当前实现允许路径分隔符
        assertTrue(result.isSuccess)
    }

    @Test
    fun invoke_fileNameWithSpacesAndMdSuffix_returnsSuccess() {
        // Arrange
        val fileName = "我的 笔记 文件.md"

        // Act
        val result = useCase(fileName)

        // Assert
        assertTrue(result.isSuccess)
    }

    // ==================== Result 类型测试 ====================

    @Test
    fun invoke_successResult_containsOriginalFileName() {
        // Arrange
        val fileName = "测试.md"

        // Act
        val result = useCase(fileName)

        // Assert
        assertEquals(fileName, result.getOrNull())
    }

    @Test
    fun invoke_failureResult_containsException() {
        // Arrange
        val fileName = "测试.txt"

        // Act
        val result = useCase(fileName)

        // Assert
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
    }

    @Test
    fun invoke_canUseGetOrElseOnFailure() {
        // Arrange
        val fileName = "测试"

        // Act
        val result = useCase(fileName)
        val recovered = result.getOrElse { "default.md" }

        // Assert
        assertEquals("default.md", recovered)
    }
}
