package com.bird.fiber.domain.usecase

import com.bird.fiber.data.model.FileError
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.data.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CreateMarkdownFileUseCase 单元测试
 *
 * 测试文件创建的业务逻辑：
 * - 内容验证
 * - 文件名生成
 * - 库 URI 获取
 * - Repository 调用
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateMarkdownFileUseCaseTest {

    private lateinit var fileRepository: FileRepository
    private lateinit var generateFileName: GenerateFileNameUseCase
    private lateinit var useCase: CreateMarkdownFileUseCase

    @Before
    fun setup() {
        fileRepository = mockk()
        generateFileName = mockk()
        useCase = CreateMarkdownFileUseCase(fileRepository, generateFileName)
    }

    // ==================== 基本功能测试 ====================

    @Test
    fun invoke_withValidContent_createsFile() = runTest {
        // Arrange
        val content = "测试内容"
        val folderUri = "content://test/folder"
        val fileName = "测试文件"
        val expectedMeta = MarkdownFileMeta(
            uri = "content://test/file.md",
            name = fileName,
            path = "$fileName.md",
            lastModified = System.currentTimeMillis(),
            size = content.length.toLong(),
            preview = ""
        )

        every { fileRepository.currentFolderUri } returns flowOf(folderUri)
        coEvery {
            fileRepository.createMarkdownFile(folderUri, fileName, content)
        } returns FileResult.Success(expectedMeta)

        // Act
        val result = useCase(folderUri = null, content = content, fileName = fileName)

        // Assert
        assertTrue(result is FileResult.Success)
        assertEquals(expectedMeta, (result as FileResult.Success).data)
        coVerify { fileRepository.createMarkdownFile(folderUri, fileName, content) }
    }

    @Test
    fun invoke_withBlankContent_returnsError() = runTest {
        // Arrange
        val content = "   "

        // Act
        val result = useCase(folderUri = "content://test", content = content)

        // Assert
        assertTrue(result is FileResult.Error)
        val error = (result as FileResult.Error).error
        assertTrue(error is FileError.Unknown)
        assertEquals("内容不能为空", (error as FileError.Unknown).message)
    }

    @Test
    fun invoke_withEmptyContent_returnsError() = runTest {
        // Arrange
        val content = ""

        // Act
        val result = useCase(folderUri = "content://test", content = content)

        // Assert
        assertTrue(result is FileResult.Error)
    }

    // ==================== 文件名生成测试 ====================

    @Test
    fun invoke_withoutFileName_generatesFileName() = runTest {
        // Arrange
        val content = "测试内容"
        val folderUri = "content://test/folder"
        val generatedName = "2025-01-01-12-00-00"
        val expectedMeta = MarkdownFileMeta(
            uri = "content://test/file.md",
            name = generatedName,
            path = "$generatedName.md",
            lastModified = System.currentTimeMillis(),
            size = content.length.toLong(),
            preview = ""
        )

        every { fileRepository.currentFolderUri } returns flowOf(folderUri)
        every { generateFileName() } returns generatedName
        coEvery {
            fileRepository.createMarkdownFile(folderUri, generatedName, content)
        } returns FileResult.Success(expectedMeta)

        // Act
        val result = useCase(folderUri = null, content = content, fileName = null)

        // Assert
        assertTrue(result is FileResult.Success)
        verify { generateFileName() }
    }

    @Test
    fun invoke_withProvidedFileName_usesProvidedName() = runTest {
        // Arrange
        val content = "测试内容"
        val folderUri = "content://test/folder"
        val providedName = "自定义文件名"

        every { fileRepository.currentFolderUri } returns flowOf(folderUri)
        coEvery {
            fileRepository.createMarkdownFile(any(), any(), any())
        } returns FileResult.Success(mockk())

        // Act
        useCase(folderUri = null, content = content, fileName = providedName)

        // Assert
        coVerify { fileRepository.createMarkdownFile(folderUri, providedName, content) }
        verify(exactly = 0) { generateFileName() }
    }

    // ==================== 文件夹 URI 测试 ====================

    @Test
    fun invoke_withProvidedFolderUri_usesProvidedUri() = runTest {
        // Arrange
        val content = "测试内容"
        val providedFolderUri = "content://provided/folder"
        val fileName = "测试文件"

        coEvery {
            fileRepository.createMarkdownFile(providedFolderUri, fileName, content)
        } returns FileResult.Success(mockk())

        // Act
        useCase(folderUri = providedFolderUri, content = content, fileName = fileName)

        // Assert
        coVerify { fileRepository.createMarkdownFile(providedFolderUri, fileName, content) }
    }

    @Test
    fun invoke_withoutFolderUri_usesCurrentLibrary() = runTest {
        // Arrange
        val content = "测试内容"
        val currentLibraryUri = "content://current/library"
        val fileName = "测试文件"

        every { fileRepository.currentFolderUri } returns flowOf(currentLibraryUri)
        coEvery {
            fileRepository.createMarkdownFile(any(), any(), any())
        } returns FileResult.Success(mockk())

        // Act
        useCase(folderUri = null, content = content, fileName = fileName)

        // Assert
        coVerify { fileRepository.createMarkdownFile(currentLibraryUri, fileName, content) }
    }

    @Test
    fun invoke_noLibrarySelected_returnsError() = runTest {
        // Arrange
        val content = "测试内容"
        every { fileRepository.currentFolderUri } returns flowOf(null)

        // Act
        val result = useCase(folderUri = null, content = content)

        // Assert
        assertTrue(result is FileResult.Error)
        val error = (result as FileResult.Error).error
        assertTrue(error is FileError.Unknown)
        assertEquals("未选择笔记库，请先添加库", (error as FileError.Unknown).message)
    }

    // ==================== 错误处理测试 ====================

    @Test
    fun invoke_repositoryError_returnsError() = runTest {
        // Arrange
        val content = "测试内容"
        val folderUri = "content://test/folder"
        val fileName = "测试文件"
        val error = FileError.IOFailed(folderUri, Exception("磁盘已满"))

        every { fileRepository.currentFolderUri } returns flowOf(folderUri)
        every { generateFileName() } returns fileName
        coEvery {
            fileRepository.createMarkdownFile(eq(folderUri), eq(fileName), eq(content))
        } returns FileResult.Error(error)

        // Act
        val result = useCase(folderUri = null, content = content)

        // Assert
        assertTrue(result is FileResult.Error)
        assertEquals(error, (result as FileResult.Error).error)
        verify { generateFileName() }
    }

    @Test
    fun invoke_repositoryThrowsException_returnsError() = runTest {
        // Arrange
        val content = "测试内容"
        every { fileRepository.currentFolderUri } throws RuntimeException("数据库错误")

        // Act
        val result = useCase(folderUri = null, content = content)

        // Assert
        assertTrue(result is FileResult.Error)
        val error = (result as FileResult.Error).error
        assertTrue(error is FileError.Unknown)
    }
}
