package com.bird.fiber.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 协程测试规则
 *
 * 用于在测试中替换主线程调度器，避免 "Module with the Main dispatcher is missing" 错误
 *
 * 使用方法：
 * ```
 * @get:Rule
 * val coroutineRule = TestCoroutineRule()
 * ```
 */
@ExperimentalCoroutinesApi
class TestCoroutineRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
) : TestWatcher() {

    override fun starting(description: Description) {
        super.starting(description)
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        super.finished(description)
        Dispatchers.resetMain()
    }
}
