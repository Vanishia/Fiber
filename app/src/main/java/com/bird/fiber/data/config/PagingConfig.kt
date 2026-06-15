package com.bird.fiber.data.config

/**
 * 分页配置
 *
 * 集中管理分页相关的魔法数字
 */
object PagingConfig {
    /**
     * 每页显示的文件数量
     *
     * 为什么是 20：
     * - 20 条文件可以在一个屏幕内完整显示
     * - 不会让滑动列表时感觉卡顿
     * - 不会一次性加载太多浪费内存
     */
    const val PAGE_SIZE = 20

    /**
     * 初始加载时的倍数
     *
     * 为什么是 2：
     * - 初始加载 2 页（40 条），让用户一打开就能看到足够多的内容
     * - 避免打开时立即触发翻页
     */
    const val INITIAL_LOAD_MULTIPLIER = 2

    /**
     * 初始加载数量
     */
    const val INITIAL_LOAD_SIZE = PAGE_SIZE * INITIAL_LOAD_MULTIPLIER

    /**
     * 预加载距离
     *
     * 为什么是 10：
     * - 距离底部还有 10 条时就开始加载下一页
     * - 让用户几乎感觉不到加载延迟
     * - 不会过早加载浪费资源
     */
    const val PREFETCH_DISTANCE = 10
}
