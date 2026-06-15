package com.bird.fiber

import android.app.Application
import com.bird.fiber.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Fiber应用入口
 * 使用Hilt进行依赖注入
 */
@HiltAndroidApp
class FiberApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化 Timber 日志库
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
