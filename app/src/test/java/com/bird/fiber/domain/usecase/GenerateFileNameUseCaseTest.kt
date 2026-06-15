package com.bird.fiber.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

/**
 * GenerateFileNameUseCase 单元测试
 *
 * 测试文件名生成逻辑：
 * - 时间戳格式
 * - 唯一性
 * - 格式正确性
 */
class GenerateFileNameUseCaseTest {

    private val useCase = GenerateFileNameUseCase()

    @Test
    fun invoke_returnsNonEmptyString() {
        // Act
        val result = useCase()

        // Assert
        assertTrue(result.isNotBlank())
    }

    @Test
    fun invoke_returnsUniqueNames() {
        // Act
        val name1 = useCase()
        Thread.sleep(1100) // 秒级格式，确保不同秒
        val name2 = useCase()

        // Assert
        assertNotEquals(name1, name2)
    }

    @Test
    fun invoke_returnsValidFormat() {
        // Act
        val result = useCase()

        // Assert
        // 实现格式：yy-MM-dd_HH-mm-ss
        val pattern = Regex("""^\d{2}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}$""")
        assertTrue("文件名格式应该为 yy-MM-dd_HH-mm-ss", pattern.matches(result))
    }

    @Test
    fun invoke_returnsCurrentTime() {
        // Arrange
        val before = System.currentTimeMillis()

        // Act
        val result = useCase()
        val after = System.currentTimeMillis()

        // Assert
        val formatter = SimpleDateFormat("yy-MM-dd_HH-mm-ss", Locale.getDefault()).apply {
            isLenient = false
        }
        val resultTime = formatter.parse(result)?.time ?: 0

        // 验证生成的时间在 before 和 after 之间（允许 1 秒误差）
        assertTrue(resultTime >= before - 1000)
        assertTrue(resultTime <= after + 1000)
    }

    @Test
    fun invoke_multipleCalls_returnsChronologicalOrder() {
        // Act
        val names = mutableListOf<String>()
        repeat(3) {
            names.add(useCase())
            Thread.sleep(100) // 确保时间戳不同
        }

        // Assert
        val formatter = SimpleDateFormat("yy-MM-dd_HH-mm-ss", Locale.getDefault()).apply {
            isLenient = false
        }
        val times = names.map { formatter.parse(it)?.time ?: 0 }

        // 验证时间递增
        for (i in 1 until times.size) {
            assertTrue(times[i] >= times[i - 1])
        }
    }
}
