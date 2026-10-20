package com.bird.fiber.ui.screens.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.text.Spanned
import android.util.Log
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.bird.fiber.data.model.*
import com.bird.fiber.data.repository.AttachmentRepository
import com.bird.fiber.domain.markdown.DocumentSnapshot
import com.bird.fiber.domain.usecase.RenderMarkdownUseCase
import com.bird.fiber.ui.theme.FiberTheme
import com.bird.fiber.utils.MarkdownChunker
import com.bird.fiber.utils.MarkdownUtils
import com.bird.fiber.utils.ProgressiveCoilImagesPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.AsyncDrawableSpan
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 使用真实 Android Spanned、TextView 和 Compose；不以 mock 代替原生渲染。 */
@OptIn(ExperimentalTestApi::class)
class EditorRefactorInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private class Attachments : AttachmentRepository {
        val resolved = mutableListOf<String>()
        override fun resolveUri(markdownFileUri: String, relativePath: String): String {
            resolved += relativePath
            return "content://test/image"
        }
        override suspend fun copyImage(sourceUri: String, target: LibraryTarget?): FileResult<Attachment> = error("unused")
        override suspend fun delete(uri: String): FileResult<Unit> = error("unused")
        override suspend fun listForLibrary(libraryId: String): FileResult<List<ManagedAttachment>> = error("unused")
        override suspend fun loadReferencesForLibrary(libraryId: String, attachments: List<ManagedAttachment>): FileResult<Map<String, List<AttachmentReference>>> = error("unused")
        override suspend fun deleteOrphans(libraryId: String, uris: Set<String>): FileResult<AttachmentDeletionSummary> = error("unused")
    }

    @Test
    fun rawParserPreservesCodeAndResolvesOnlyRealImagesIncludingReferences() {
        val attachments = Attachments()
        val renderer = RenderMarkdownUseCase(context, attachments)
        val raw = "第一行\r\n第二行\r\n\r\n```\r\n代码  \r\n![假图](code.png)\r\n```\r\n\r\n" +
            "`![行内假图](inline.png)`\n\n![引用][image]\n\n[image]: <含 空格.png>\n"
        val snapshot = DocumentSnapshot("content://note", 3, 9, raw)
        val parsed = renderer.parse(snapshot)
        assertEquals(snapshot, parsed.document)
        assertTrue(parsed.blocks.any { it.type == "FencedCodeBlock" })
        assertTrue(parsed.blocks.all { it.sourceRange == null })
        val text = renderer.render(parsed).toString()
        assertTrue(text.contains("第一行\n第二行"))
        assertTrue(text.contains("代码  \n![假图](code.png)"))
        assertTrue(text.contains("![行内假图](inline.png)"))
        assertEquals(listOf("含 空格.png"), attachments.resolved)
        renderer.render(parsed)
        assertEquals(listOf("含 空格.png", "含 空格.png"), attachments.resolved)
        assertEquals(raw, parsed.document.content)
    }

    @Test
    fun nativeTablesReflowAndResourcesReleaseWithPluginHooks() {
        val renderer = RenderMarkdownUseCase(context, Attachments())
        val raw = "# 标题\n\n- 列表\n- [x] 任务\n\n> 引用\n\n| 表头 | B |\n| --- | --- |\n| " +
            "单元格长内容".repeat(20) + " | 内容 |\n\n结束"
        lateinit var view: TextView
        var rendered by mutableStateOf(renderer.render(raw))
        var fontSize by mutableStateOf(16f)
        compose.setContent {
            AndroidView(
                modifier = Modifier.width(240.dp),
                factory = { TextView(it).also { textView -> view = textView } },
                onRelease = NativePreviewBinding::release,
                update = {
                    it.textSize = fontSize
                    it.setTextColor(if (fontSize == 16f) Color.BLACK else Color.WHITE)
                    it.setBackgroundColor(if (fontSize == 16f) Color.WHITE else Color.BLACK)
                    NativePreviewBinding.apply(it, rendered, renderer::apply)
                }
            )
        }
        compose.waitForIdle()
        var smallHeight = 0
        compose.runOnIdle {
            assertTrue(view.isAttachedToWindow)
            smallHeight = view.measuredHeight
            val spans = (view.text as Spanned).getSpans(0, view.length(), TableRowSpan::class.java)
            assertEquals(2, spans.size)
            assertTrue(spans.last().cellWidth() > 0)
            assertTrue(view.text.toString().contains("结束"))
            // 与主题/字号改变时的新渲染请求一致，使用新 span 重建表格缓存。
            fontSize = 24f
            rendered = renderer.render(raw)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(view.measuredHeight > smallHeight)
            NativePreviewBinding.release(view)
            assertEquals("", view.text.toString())
        }
    }

    @Test
    fun toolbarPreservesFocusAndReversedSelection_andSharesKeyboardUndo() {
        val session = EditorSession()
        session.reset(TextFieldValue("前中文😀后"))
        var value by mutableStateOf(session.value)
        compose.setContent {
            FiberTheme {
                EditorEditPane(
                    value = value,
                    onContentChange = { session.update(it); value = session.value },
                    onImageSelected = { _, _ -> },
                    captureImageInsertion = { null },
                    canUndo = session.canUndo,
                    canRedo = session.canRedo,
                    onUndo = { session.undo(); value = session.value },
                    onRedo = { session.redo(); value = session.value },
                    onFormat = { format -> session.perform { EditorOperations.format(it, format) }; value = session.value },
                    isAddingImage = false,
                    topContentInset = 0.dp,
                    bottomContentInset = 0.dp
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        compose.runOnIdle {
            session.update(TextFieldValue(value.text, TextRange(5, 1)))
            value = session.value
        }
        compose.onNodeWithText("加粗").performClick()
        field.assertIsFocused()
        compose.runOnIdle {
            assertEquals("前**中文😀**后", value.text)
            assertEquals(TextRange(7, 3), value.selection)
        }
        field.performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Z); keyUp(Key.CtrlLeft) }
        compose.runOnIdle { assertEquals("前中文😀后", value.text) }
        compose.onNodeWithContentDescription("重做").performClick()
        compose.runOnIdle { assertEquals("前**中文😀**后", value.text) }
    }

    @Test
    fun localImageLoadsAndReleaseDetachesAsyncDrawable() {
        val renderer = RenderMarkdownUseCase(context, Attachments())
        val file = File.createTempFile("editor-image-", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val rendered = renderer.render("![图](${file.toURI()})")
            val drawable = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java).single().drawable
            lateinit var view: TextView
            compose.setContent {
                AndroidView(
                    modifier = Modifier.width(240.dp),
                    factory = { TextView(it).also { textView -> view = textView } },
                    onRelease = NativePreviewBinding::release,
                    update = { NativePreviewBinding.apply(it, rendered, renderer::apply) }
                )
            }
            compose.waitUntil(timeoutMillis = 10_000) { drawable.result is BitmapDrawable }
            compose.runOnIdle {
                assertTrue(drawable.isAttached)
                NativePreviewBinding.release(view)
                assertFalse(drawable.isAttached)
                assertEquals("", view.text.toString())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun largeDocumentRecordsBaselineAndSnapshotCosts() {
        val renderer = RenderMarkdownUseCase(context, Attachments())
        val legacy = Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(ImagesPlugin.create())
            .usePlugin(ProgressiveCoilImagesPlugin.create(context)).build()
        val content = ("# 标题\n\n普通段落，包含 **加粗** 和 [链接](https://example.com)。\n\n").repeat(22_000)
        assertTrue(content.length > 1_000_000)
        val chunks = MarkdownChunker.split(content, 250_000)
        fun measure(name: String, render: (String) -> Spanned) {
            System.gc()
            val runtime = Runtime.getRuntime()
            fun used() = runtime.totalMemory() - runtime.freeMemory()
            val before = used()
            var sampledPeak = before
            val start = System.nanoTime()
            val parts = chunks.map {
                render(it).also { sampledPeak = maxOf(sampledPeak, used()) }
            }
            val result = renderer.concat(parts)
            sampledPeak = maxOf(sampledPeak, used())
            assertTrue(result.isNotEmpty())
            Log.i("EditorRefactorBenchmark", "$name chars=${content.length} chunks=${chunks.size} ms=${(System.nanoTime() - start) / 1_000_000} sampledHeapDelta=${sampledPeak - before}")
        }
        // 预热，避免把初始化时间仅计入旧路径。
        legacy.toMarkdown("预热")
        renderer.render("预热")
        measure("legacy") { legacy.toMarkdown(MarkdownUtils.preprocessMarkdownForHardBreaks(it)) }
        var parseNanos = 0L
        var renderNanos = 0L
        measure("snapshot") {
            val start = System.nanoTime()
            val parsed = renderer.parse(DocumentSnapshot(null, 1, 1, it))
            val parsedAt = System.nanoTime()
            parseNanos += parsedAt - start
            renderer.render(parsed).also { renderNanos += System.nanoTime() - parsedAt }
        }
        Log.i("EditorRefactorBenchmark", "snapshot parseMs=${parseNanos / 1_000_000} renderMs=${renderNanos / 1_000_000}")
    }
}
