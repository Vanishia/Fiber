package com.bird.fiber.domain.usecase

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import com.bird.fiber.domain.markdown.DocumentSnapshot
import com.bird.fiber.domain.markdown.MarkdownBlock
import com.bird.fiber.domain.markdown.ParsedDocument
import com.bird.fiber.utils.ProgressiveCoilImagesPlugin
import com.bird.fiber.data.repository.AttachmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.node.SoftLineBreak
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

    private val markwon: Markwon by lazy {
        Markwon.builder(appContext)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(appContext))
            .usePlugin(TaskListPlugin.create(appContext))
            .usePlugin(ImagesPlugin.create())
            // 渐进加载：图片未就绪时先显示文字和加载圈，且限制解码尺寸
            .usePlugin(ProgressiveCoilImagesPlugin.create(appContext))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                    builder.on(SoftLineBreak::class.java) { visitor, _ -> visitor.forceNewLine() }
                }
            })
            .build()
    }

    @Synchronized
    fun parse(document: DocumentSnapshot): ParsedDocument {
        val root = markwon.parse(document.content)
        val blocks = generateSequence(root.firstChild) { it.next }
            .map { MarkdownBlock(it.javaClass.simpleName) }.toList()
        return ParsedDocument(document, blocks, root)
    }

    @Synchronized
    fun render(parsed: ParsedDocument): Spanned {
        val fileUri = parsed.document.fileUri
        if (fileUri != null) {
            parsed.root.accept(object : AbstractVisitor() {
                override fun visit(image: Image) {
                    image.destination = attachmentRepository.resolveUri(fileUri, image.destination) ?: image.destination
                    super.visit(image)
                }
            })
        }
        return markwon.render(parsed.root)
    }

    @Synchronized
    fun render(content: String, markdownFileUri: String? = null): Spanned {
        return render(parse(DocumentSnapshot(markdownFileUri, 0, 0, content)))
    }

    @Synchronized
    fun render(document: DocumentSnapshot): Spanned = render(parse(document))

    /** setParsedMarkdown 会调用全部插件的 beforeSetText/afterSetText，不再绕过插件。 */
    fun apply(textView: TextView, markdown: Spanned) {
        markwon.setParsedMarkdown(textView, markdown)
    }

    /**
     * 把分块渲染的结果按顺序拼接为一个 Spanned，保留各块的样式和图片 span
     */
    fun concat(parts: List<Spanned>): Spanned {
        val builder = android.text.SpannableStringBuilder()
        parts.forEach { builder.append(it) }
        return builder
    }

}
