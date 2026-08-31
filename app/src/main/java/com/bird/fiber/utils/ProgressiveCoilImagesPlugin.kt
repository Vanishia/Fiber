package com.bird.fiber.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import coil.Coil
import coil.request.Disposable
import coil.request.ImageRequest
import coil.target.Target
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableLoader
import io.noties.markwon.image.DrawableUtils
import io.noties.markwon.image.ImageSpanFactory
import org.commonmark.node.Image
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 渐进式图片加载插件（替代 markwon image-coil）
 *
 * - 图片加载期间在图片位置显示旋转加载圈占位，不再一片空白
 * - 解码尺寸限制为 [MAX_INLINE_IMAGE_PIXELS]，避免全尺寸照片拖慢渲染、撑爆内存
 *
 * 加载逻辑照搬 CoilImagesPlugin 的 CoilAsyncDrawableLoader（含同步返回竞争处理），
 * 仅增加占位图与尺寸上限
 */
class ProgressiveCoilImagesPlugin private constructor(
    private val loader: Loader
) : AbstractMarkwonPlugin() {

    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
        builder.setFactory(Image::class.java, ImageSpanFactory())
    }

    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
        builder.asyncDrawableLoader(loader)
    }

    companion object {
        /** 预览内联图片的最大解码边长，与附件页大图预览一致 */
        const val MAX_INLINE_IMAGE_PIXELS = 2048

        fun create(context: Context): ProgressiveCoilImagesPlugin {
            return ProgressiveCoilImagesPlugin(Loader(context.applicationContext))
        }
    }

    private class Loader(context: Context) : AsyncDrawableLoader() {
        private val appContext = context.applicationContext
        private val imageLoader = Coil.imageLoader(appContext)
        private val disposables = HashMap<AsyncDrawable, Disposable>()

        override fun load(drawable: AsyncDrawable) {
            // enqueue 可能先从内存缓存同步回调（早于 disposable 入缓存），
            // 用 loaded 标记规避结果丢失（同 CoilImagesPlugin 4.5.1 的处理）
            val loaded = AtomicBoolean(false)
            val target = object : Target {
                override fun onSuccess(result: Drawable) {
                    if (disposables.remove(drawable) != null || !loaded.get()) {
                        loaded.set(true)
                        if (drawable.isAttached) {
                            DrawableUtils.applyIntrinsicBoundsIfEmpty(result)
                            drawable.setResult(result)
                        }
                    }
                }

                override fun onError(error: Drawable?) {
                    disposables.remove(drawable)
                    if (error != null && drawable.isAttached) {
                        DrawableUtils.applyIntrinsicBoundsIfEmpty(error)
                        drawable.setResult(error)
                    }
                }
            }
            val request = ImageRequest.Builder(appContext)
                .data(drawable.destination)
                .size(MAX_INLINE_IMAGE_PIXELS)
                .target(target)
                .build()
            val disposable = imageLoader.enqueue(request)
            if (!loaded.get()) {
                loaded.set(true)
                disposables[drawable] = disposable
            }
        }

        override fun cancel(drawable: AsyncDrawable) {
            disposables.remove(drawable)?.dispose()
        }

        override fun placeholder(drawable: AsyncDrawable): Drawable {
            val density = appContext.resources.displayMetrics.density
            return SpinnerDrawable(
                diameterPx = (PLACEHOLDER_DIAMETER_DP * density).toInt(),
                strokePx = PLACEHOLDER_STROKE_DP * density
            )
        }

        private companion object {
            const val PLACEHOLDER_DIAMETER_DP = 36f
            const val PLACEHOLDER_STROKE_DP = 3f
        }
    }

    /**
     * 旋转加载圈占位 drawable
     *
     * 首次绘制时懒启动旋转动画，按帧失效重绘；
     * 回调被摘除（脱离视图或被真实图片替换）时自动停止
     */
    private class SpinnerDrawable(
        private val diameterPx: Int,
        strokePx: Float
    ) : Drawable(), Animatable {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            // 中性灰，亮暗主题下都可见
            color = Color.argb(140, 128, 128, 128)
        }
        private val arcBounds = RectF()
        private var running = false

        private val frameCallback = object : Runnable {
            override fun run() {
                // 回调被摘除（脱离视图/被真实图片替换）后自动停止，不再重绘
                if (!running || callback == null) {
                    running = false
                    return
                }
                invalidateSelf()
                scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS)
            }
        }

        override fun getIntrinsicWidth(): Int = diameterPx
        override fun getIntrinsicHeight(): Int = diameterPx

        override fun draw(canvas: Canvas) {
            // Drawable.setCallback 是 final，无法重写感知附着；改为首次绘制时懒启动
            if (!running) start()
            val b = bounds
            if (b.isEmpty) return
            val inset = paint.strokeWidth / 2f
            arcBounds.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
            val startAngle = (SystemClock.uptimeMillis() % ROTATION_PERIOD_MS).toFloat() /
                ROTATION_PERIOD_MS * 360f - 90f
            canvas.drawArc(arcBounds, startAngle, SWEEP_DEGREES, false, paint)
        }

        override fun start() {
            if (!running) {
                running = true
                scheduleSelf(frameCallback, SystemClock.uptimeMillis())
            }
        }

        override fun stop() {
            running = false
            unscheduleSelf(frameCallback)
        }

        override fun isRunning(): Boolean = running

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        private companion object {
            const val FRAME_MS = 16L
            const val ROTATION_PERIOD_MS = 1200L
            const val SWEEP_DEGREES = 270f
        }
    }
}
