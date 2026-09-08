package com.bird.fiber.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val checker = UpdateChecker()

    @Test
    fun `remote 更高时返回 true`() {
        assertTrue(checker.isNewerVersion("1.2.2", "1.2.1"))
        assertTrue(checker.isNewerVersion("1.3.0", "1.2.9"))
        assertTrue(checker.isNewerVersion("2.0.0", "1.9.9"))
        assertTrue(checker.isNewerVersion("1.2.1.1", "1.2.1"))
    }

    @Test
    fun `remote 相同或更低时返回 false`() {
        assertFalse(checker.isNewerVersion("1.2.1", "1.2.1"))
        assertFalse(checker.isNewerVersion("1.2.0", "1.2.1"))
        assertFalse(checker.isNewerVersion("1.2", "1.2.1"))
        assertFalse(checker.isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun `无法解析的段按 0 处理`() {
        assertTrue(checker.isNewerVersion("1.2.2", "1.2.x"))
        assertFalse(checker.isNewerVersion("1.2.x", "1.2.1"))
    }
}
