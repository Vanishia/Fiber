package com.bird.fiber.data.local.library

import android.content.ContentResolver
import android.net.Uri
import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LibraryRepository 单元测试
 *
 * 测试笔记库管理功能：
 * - 获取库列表
 * - 添加/删除库
 * - 切换当前库
 * - URI 权限管理
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepositoryTest {

    private lateinit var libraryDao: LibraryDao
    private lateinit var contentResolver: ContentResolver
    private lateinit var repository: LibraryRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        libraryDao = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        repository = LibraryRepository(libraryDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== 库列表测试 ====================

    @Test
    fun getAllLibraries_returnsFlowFromDao() = runTest {
        // Arrange
        val libraries = listOf(
            LibraryEntity("1", "库1", "uri1", 1000, 2000, true),
            LibraryEntity("2", "库2", "uri2", 1000, 2000, false)
        )
        every { libraryDao.getAllLibraries() } returns flowOf(libraries)

        // Act
        val result = repository.getAllLibraries().first()

        // Assert
        assertEquals(2, result.size)
        assertEquals("库1", result[0].name)
    }

    @Test
    fun getActiveLibrary_returnsActiveLibrary() = runTest {
        // Arrange
        val activeLibrary = LibraryEntity("1", "活跃库", "uri1", 1000, 2000, true)
        every { libraryDao.getActiveLibrary() } returns flowOf(activeLibrary)

        // Act
        val result = repository.getActiveLibrary().first()

        // Assert
        assertNotNull(result)
        assertEquals("活跃库", result?.name)
        assertTrue(result?.isActive == true)
    }

    @Test
    fun getActiveLibrary_noActiveLibrary_returnsNull() = runTest {
        // Arrange
        every { libraryDao.getActiveLibrary() } returns flowOf(null)

        // Act
        val result = repository.getActiveLibrary().first()

        // Assert
        assertNull(result)
    }

    @Test
    fun getLibraryById_returnsLibrary() = runTest {
        // Arrange
        val library = LibraryEntity("1", "测试库", "uri1", 1000, 2000, true)
        coEvery { libraryDao.getLibraryById("1") } returns library

        // Act
        val result = repository.getLibraryById("1")

        // Assert
        assertNotNull(result)
        assertEquals("测试库", result?.name)
    }

    // ==================== 库管理测试 ====================

    @Test
    fun addLibrary_insertsToDao() = runTest {
        // Arrange
        val library = LibraryEntity("1", "新库", "uri1", 1000, 2000, false)
        coEvery { libraryDao.insertLibrary(library) } returns 1L

        // Act
        repository.addLibrary(library)

        // Assert
        coVerify { libraryDao.insertLibrary(library) }
    }

    @Test
    fun updateLibrary_updatesDao() = runTest {
        // Arrange
        val library = LibraryEntity("1", "更新的库", "uri1", 1000, 2000, true)
        coEvery { libraryDao.updateLibrary(library) } returns Unit

        // Act
        repository.updateLibrary(library)

        // Assert
        coVerify { libraryDao.updateLibrary(library) }
    }

    @Test
    fun switchLibrary_deactivatesAllAndActivatesTarget() = runTest {
        // Arrange
        val libraryId = "2"
        coEvery { libraryDao.deactivateAll() } returns Unit
        coEvery { libraryDao.activateLibrary(libraryId, any()) } returns Unit

        // Act
        repository.switchLibrary(libraryId)

        // Assert
        coVerify { libraryDao.deactivateAll() }
        coVerify { libraryDao.activateLibrary(libraryId, any()) }
    }

    // ==================== 删除库测试 ====================

    @Test
    fun deleteLibraryById_deletesFromDao() = runTest {
        // Arrange
        val libraryId = "1"
        coEvery { libraryDao.deleteLibraryById(libraryId) } returns Unit

        // Act
        repository.deleteLibraryById(libraryId)

        // Assert
        coVerify { libraryDao.deleteLibraryById(libraryId) }
    }

    @Test
    fun clearAllLibraries_deletesAllFromDao() = runTest {
        // Arrange
        coEvery { libraryDao.deleteAllLibraries() } returns Unit

        // Act
        repository.clearAllLibraries()

        // Assert
        coVerify { libraryDao.deleteAllLibraries() }
    }

    // ==================== 统计测试 ====================

    @Test
    fun getLibraryCount_returnsFlowFromDao() = runTest {
        // Arrange
        every { libraryDao.getLibraryCount() } returns flowOf(5)

        // Act
        val result = repository.getLibraryCount().first()

        // Assert
        assertEquals(5, result)
    }

    // ==================== 验证和清理测试 ====================

    @Test
    fun getAllLibrariesList_returnsListFromDao() = runTest {
        // Arrange
        val libraries = listOf(
            LibraryEntity("1", "库1", "uri1", 1000, 2000, true)
        )
        coEvery { libraryDao.getAllLibrariesList() } returns libraries

        // Act
        val result = repository.getAllLibrariesList()

        // Assert
        assertEquals(1, result.size)
        assertEquals("库1", result[0].name)
    }

    @Test
    fun validateAndCleanupInvalidLibraries_removesInvalidLibraries() = runTest {
        // Arrange
        val validLibrary = LibraryEntity("1", "有效库", "content://valid", 1000, 2000, false)
        val invalidLibrary = LibraryEntity("2", "无效库", "content://invalid", 1000, 2000, false)

        coEvery { libraryDao.getAllLibrariesList() } returns listOf(validLibrary, invalidLibrary)

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        // Mock URI 权限验证 - 只有 validLibrary 有权限
        val permission = mockk<android.content.UriPermission>(relaxed = true)
        val validUri = mockk<Uri>(relaxed = true)
        every { validUri.toString() } returns "content://valid"
        every { permission.uri } returns validUri
        every { contentResolver.persistedUriPermissions } returns listOf(permission)

        coEvery { libraryDao.deleteLibrary(any()) } returns Unit

        // Act
        val removedCount = repository.validateAndCleanupInvalidLibraries(contentResolver)

        // Assert
        assertEquals(1, removedCount)
        coVerify { libraryDao.deleteLibrary(invalidLibrary) }
        coVerify(exactly = 0) { libraryDao.deleteLibrary(validLibrary) }
    }

    @Test
    fun validateAndCleanupInvalidLibraries_allValid_removesNone() = runTest {
        // Arrange
        val library1 = LibraryEntity("1", "库1", "content://uri1", 1000, 2000, false)
        val library2 = LibraryEntity("2", "库2", "content://uri2", 1000, 2000, false)

        coEvery { libraryDao.getAllLibrariesList() } returns listOf(library1, library2)

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        val permission1 = mockk<android.content.UriPermission>(relaxed = true)
        val permission2 = mockk<android.content.UriPermission>(relaxed = true)
        val uri1 = mockk<Uri>(relaxed = true)
        val uri2 = mockk<Uri>(relaxed = true)
        every { uri1.toString() } returns "content://uri1"
        every { uri2.toString() } returns "content://uri2"
        every { permission1.uri } returns uri1
        every { permission2.uri } returns uri2
        every { contentResolver.persistedUriPermissions } returns listOf(permission1, permission2)

        // Act
        val removedCount = repository.validateAndCleanupInvalidLibraries(contentResolver)

        // Assert
        assertEquals(0, removedCount)
        coVerify(exactly = 0) { libraryDao.deleteLibrary(any()) }
    }
}
