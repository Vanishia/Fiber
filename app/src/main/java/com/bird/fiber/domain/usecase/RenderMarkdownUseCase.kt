package com.bird.fiber.domain.usecase

import android.content.Context
import android.text.Spanned
import com.bird.fiber.utils.MarkdownUtils
import com.bird.fiber.utils.ProgressiveCoilImagesPlugin
import com.bird.fiber.data.repository.AttachmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.ImagesPlugin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RenderMarkdownUseCase @Inject constructor(
    @ApplicationContext context: Context,
    private val attachmentRepository: AttachmentRepository
) {
    private val appContext = context.applicationContext

    private val markwon: Markwon by lazy(LazyThreadSafetyMode.NONE) {
        Markwon.builder(appContext)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(appContext))
            .usePlugin(TaskListPlugin.create(appContext))
            .usePlugin(ImagesPlugin.create())
            // 渐进加载：图片未就绪时先显示文字和加载圈，且限制解码尺寸
            .usePlugin(ProgressiveCoilImagesPlugin.create(appContext))
            .build()
    }

    fun render(content: String, markdownFileUri: String? = null): Spanned {
        val resolved = if (markdownFileUri == null) {
            content
        } else {
            resolveAttachmentReferences(content, markdownFileUri)
        }
        val processed = MarkdownUtils.preprocessMarkdownForHardBreaks(resolved)
        val parsed = markwon.parse(processed)
        return markwon.render(parsed)
    }

    /**
     * 把分块渲染的结果按顺序拼接为一个 Spanned，保留各块的样式和图片 span
     */
    fun concat(parts: List<Spanned>): Spanned {
        val builder = android.text.SpannableStringBuilder()
        parts.forEach { builder.append(it) }
        return builder
    }

    private fun resolveAttachmentReferences(content: String, markdownFileUri: String): String {
        return IMAGE_PATTERN.replace(content) { match ->
            val alt = match.groupValues[1]
            val destination = match.groupValues[2].ifEmpty { match.groupValues[3] }.trim()
            val resolved = attachmentRepository.resolveUri(markdownFileUri, destination)
                ?: return@replace match.value
            "![$alt]($resolved)"
        }
    }

    companion object {
        private val IMAGE_PATTERN = Regex("""!\[([^\]]*)]\((?:<([^>]+)>|([^)]+))\)""")
    }
}
