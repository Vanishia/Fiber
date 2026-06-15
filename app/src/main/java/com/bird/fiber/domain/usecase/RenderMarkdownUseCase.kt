package com.bird.fiber.domain.usecase

import android.content.Context
import android.text.Spanned
import com.bird.fiber.utils.MarkdownUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RenderMarkdownUseCase @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext

    private val markwon: Markwon by lazy(LazyThreadSafetyMode.NONE) {
        Markwon.builder(appContext)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(appContext))
            .usePlugin(TaskListPlugin.create(appContext))
            .build()
    }

    fun render(content: String): Spanned {
        val processed = MarkdownUtils.preprocessMarkdownForHardBreaks(content)
        val parsed = markwon.parse(processed)
        return markwon.render(parsed)
    }
}
