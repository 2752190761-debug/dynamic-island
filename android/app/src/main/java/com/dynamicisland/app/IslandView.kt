package com.dynamicisland.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import kotlin.math.abs

/**
 * 自绘的“灵动岛”胶囊视图。
 *
 * 收起态：一个窄胶囊，显示应用名。
 * 展开态：变宽变高，显示应用名 / 标题 / 正文。
 * 尺寸变化通过 ValueAnimator 驱动，通知宿主（悬浮窗）同步调整窗口大小。
 */
class IslandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Callback {
        fun onSizeChanged(widthPx: Int, heightPx: Int)
        fun onTap()
        fun onDrag(dx: Int, dy: Int)
        fun onDismiss()
    }

    var callback: Callback? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    val collapsedWidth = dp(112f)
    val collapsedHeight = dp(34f)
    val expandedWidth = dp(300f)
    val expandedHeight = dp(96f)

    /** 0 = 完全收起，1 = 完全展开 */
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B0B0F")
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#34C759")
    }
    private val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9AA0AE")
        textSize = dp(10f)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(13f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B8BCC8")
        textSize = dp(11.5f)
    }
    private val dotRect = RectF()

    private var content: IslandContent? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false

    init {
        isClickable = true
    }

    fun applyContent(c: IslandContent) {
        content = c
        dotPaint.color = c.accentColor
        invalidate()
    }

    fun isExpanded(): Boolean = progress > 0.5f

    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        val target = if (expanded) 1f else 0f
        if (abs(progress - target) < 0.02f && animator?.isRunning != true) return
        animator?.cancel()

        if (!animate) {
            progress = target
            notifySizeChanged()
            invalidate()
            return
        }

        val anim = ValueAnimator.ofFloat(progress, target)
        anim.duration = if (expanded) 420L else 300L
        anim.interpolator = if (expanded) {
            PathInterpolator(0.22f, 0.92f, 0.12f, 1f)
        } else {
            PathInterpolator(0.4f, 0f, 0.2f, 1f)
        }
        anim.addUpdateListener { va ->
            progress = va.animatedValue as Float
            notifySizeChanged()
            invalidate()
        }
        animator = anim
        anim.start()
    }

    private fun lerp(from: Float, to: Float) = from + (to - from) * progress

    private fun notifySizeChanged() {
        callback?.onSizeChanged(
            lerp(collapsedWidth, expandedWidth).toInt(),
            lerp(collapsedHeight, expandedHeight).toInt()
        )
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = lerp(collapsedWidth, expandedWidth).toInt()
        val h = lerp(collapsedHeight, expandedHeight).toInt()
        setMeasuredDimension(
            resolveSize(w, widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = h / 2f
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, pillPaint)

        // 左侧指示点：收起时略大，展开时收敛
        val dotRadius = dp(4f) + dp(1.6f) * (1f - progress)
        val dotCx = dp(18f)
        val dotCy = h / 2f
        dotRect.set(dotCx - dotRadius, dotCy - dotRadius, dotCx + dotRadius, dotCy + dotRadius)
        canvas.drawCircle(dotCx, dotCy, dotRadius, dotPaint)

        val c = content ?: return
        val textLeft = dp(32f)
        val maxTextWidth = w - textLeft - dp(16f)

        if (progress < 0.02f) {
            canvas.drawText(
                ellipsize(c.appName, maxTextWidth, appPaint),
                textLeft,
                dotCy + dp(3.5f),
                appPaint
            )
            return
        }

        val fade = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
        appPaint.alpha = (fade * 200).toInt()
        titlePaint.alpha = (fade * 255).toInt()
        textPaint.alpha = (fade * 255).toInt()

        canvas.drawText(ellipsize(c.appName, maxTextWidth, appPaint), textLeft, dp(26f), appPaint)
        canvas.drawText(ellipsize(c.title, maxTextWidth, titlePaint), textLeft, dp(48f), titlePaint)
        canvas.drawText(ellipsize(c.text, maxTextWidth, textPaint), textLeft, dp(70f), textPaint)
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (maxWidth <= 0f) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val suffix = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + suffix) > maxWidth) {
            end--
        }
        return if (end <= 0) "" else text.substring(0, end) + suffix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = event.rawX
                lastY = event.rawY
                moved = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!moved && (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop)) {
                    moved = true
                }
                if (moved) {
                    callback?.onDrag((event.rawX - lastX).toInt(), (event.rawY - lastY).toInt())
                    lastX = event.rawX
                    lastY = event.rawY
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    if (callback != null) callback?.onTap() else performClick()
                } else if (downY - event.rawY > dp(72f)) {
                    callback?.onDismiss()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }
}
