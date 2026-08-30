package com.bird.fiber.domain.usecase

import android.content.Context
import android.text.Spanned
import coil.Coil
import coil.request.Disposable
import coil.request.ImageRequest
import com.bird.fiber.utils.MarkdownUtils
import com.bird.fiber.data.repository.AttachmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RenderMarkdownUseCase @Inject constructor(
    @ApplicationContext context: Context,
    private val attachmentRepository: AttachmentRepository
) {
    private val appContext = context.applicationContext

    // 预览内联图片按上限尺寸解码，避免手机全尺寸照片拖慢渲染、撑爆内存；
    // 2048px 与附件页大图预览保持一致
    private val previewCoilStore = object : CoilImagesPlugin.CoilStore {
        override fun load(drawable: AsyncDrawable): ImageRequest {
            return ImageRequest.Builder(appContext)
                .data(drawable.destination)
                .size(MAX_INLINE_IMAGE_PIXELS)
                .build()
        }

        override fun cancel(disposable: Disposable) {
            disposable.dispose()
        }
    }

    private val markwon: Markwon by lazy(LazyThreadSafetyMode.NONE) {
        Markwon.builder(appContext)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(appContext))
            .usePlugin(TaskListPlugin.create(appContext))
            .usePlugin(ImagesPlugin.create())
            .usePlugin(CoilImagesPlugin.create(previewCoilStore, Coil.imageLoader(appContext)))
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

        /** 预览内联图片的最大解码边长 */
        private const val MAX_INLINE_IMAGE_PIXELS = 2048
    }
}
