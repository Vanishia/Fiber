package com.bird.fiber.ui.screens.editor

import android.text.Spanned
import android.widget.TextView
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.image.AsyncDrawableScheduler

/** 视图释放和文本替换都先解除旧资源；注册交还给 Markwon 的插件钩子。 */
internal object NativePreviewBinding {
    fun apply(view: TextView, text: Spanned?, setter: (TextView, Spanned) -> Unit) {
        release(view)
        if (text != null) setter(view, text)
    }

    fun release(view: TextView) {
        AsyncDrawableScheduler.unschedule(view)
        (view.text as? Spanned)?.let { text ->
            text.getSpans(0, text.length, TableRowSpan::class.java).forEach { it.invalidator(null) }
        }
        view.text = ""
    }
}
