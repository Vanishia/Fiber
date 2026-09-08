package com.bird.fiber.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * 渐进式图片加载插件（替代 markwon image-coil）
 *
 * - 图片加载期间在图片位置显示灰色 1:1 图片图标占位（呼吸动画），不再一片空白
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
            return ImagePlaceholderDrawable(
                sizePx = (PLACEHOLDER_SIZE_DP * density).toInt(),
                cornerRadiusPx = PLACEHOLDER_CORNER_DP * density
            )
        }

        private companion object {
            /** 邮票大小的 1:1 占位方块 */
            const val PLACEHOLDER_SIZE_DP = 56f
            const val PLACEHOLDER_CORNER_DP = 6f
        }
    }

    /**
     * 图片加载占位 drawable：1:1 浅灰圆角方块，中央山形图片图标做呼吸动画
     *
     * 首次绘制时懒启动动画，按帧失效重绘；
     * 回调被摘除（脱离视图或被真实图片替换）时自动停止
     */
    private class ImagePlaceholderDrawable(
        private val sizePx: Int,
        private val cornerRadiusPx: Float
    ) : Drawable(), Animatable {

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            // 淡灰底，亮暗主题下都可见
            color = Color.argb(32, 128, 128, 128)
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            // 中性灰图标，透明度由呼吸动画驱动
            color = Color.rgb(128, 128, 128)
        }
        private val rect = RectF()
        private val mountainPath = Path()
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

        override fun getIntrinsicWidth(): Int = sizePx
        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            // Drawable.setCallback 是 final，无法重写感知附着；改为首次绘制时懒启动
            if (!running) start()
            val b = bounds
            if (b.isEmpty) return

            // 呼吸动画：图标透明度在 ICON_MIN_ALPHA..255 间平滑往返
            val phase = (SystemClock.uptimeMillis() % PULSE_PERIOD_MS).toFloat() / PULSE_PERIOD_MS
            val wave = (1f - cos(phase * 2 * PI).toFloat()) / 2f
            iconPaint.alpha = (ICON_MIN_ALPHA + (255 - ICON_MIN_ALPHA) * wave).roundToInt()

            rect.set(b)
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, backgroundPaint)

            val w = b.width().toFloat()
            val left = b.left.toFloat()
            val top = b.top.toFloat()
            // 太阳
            canvas.drawCircle(left + w * 0.36f, top + w * 0.34f, w * 0.075f, iconPaint)
            // 山形（两座）
            mountainPath.reset()
            mountainPath.moveTo(left + w * 0.20f, top + w * 0.74f)
            mountainPath.lineTo(left + w * 0.40f, top + w * 0.50f)
            mountainPath.lineTo(left + w * 0.52f, top + w * 0.65f)
            mountainPath.lineTo(left + w * 0.64f, top + w * 0.50f)
            mountainPath.lineTo(left + w * 0.80f, top + w * 0.74f)
            mountainPath.close()
            canvas.drawPath(mountainPath, iconPaint)
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
            backgroundPaint.alpha = alpha
            iconPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            backgroundPaint.colorFilter = colorFilter
            iconPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        private companion object {
            const val FRAME_MS = 16L
            const val PULSE_PERIOD_MS = 1600L
            const val ICON_MIN_ALPHA = 90
        }
    }
}
