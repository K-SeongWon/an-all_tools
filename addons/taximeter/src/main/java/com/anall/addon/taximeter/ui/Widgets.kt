package com.anall.addon.taximeter.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import com.anall.core.ui.Palette
import com.anall.core.ui.dpf
import kotlin.math.min
import kotlin.random.Random

/** 같은 값이면 setText 를 건너뛴다 (불필요한 레이아웃 방지). */
internal fun TextView.setIfChanged(value: CharSequence) {
    if (text.toString() != value.toString()) text = value
}

internal fun View.setVisible(v: Boolean) {
    val target = if (v) View.VISIBLE else View.GONE
    if (visibility != target) visibility = target
}

/** 다음 요금 단위까지 진행 막대. */
internal class ProgressBarView(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.LED_OFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var shown = 0f
    private var animator: ValueAnimator? = null

    var progress: Float = 0f
        set(v) {
            val t = v.coerceIn(0f, 1f)
            if (t == field) return
            field = t
            animator?.cancel()
            if (t < shown) {
                // 100원이 올라 0 으로 돌아갈 때는 바로 비운다.
                shown = t
                invalidate()
            } else {
                animator = ValueAnimator.ofFloat(shown, t).apply {
                    duration = 450
                    addUpdateListener { shown = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fill.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Palette.CYAN, Palette.LED_ON, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, track)
        if (shown > 0f) {
            rect.right = width * shown
            canvas.drawRoundRect(rect, r, r, fill)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

/** 0~120km/h 속도 막대와 저속 기준선. */
internal class SpeedBarView(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.LED_OFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = Paint().apply { color = 0xFFFFFFFF.toInt() }
    private val rect = RectF()
    private var speedFrac = 0f
    private var lowFrac = 0f
    private var low = true

    fun set(speedKmh: Double, lowSpeedKmh: Double, isLow: Boolean) {
        val s = (speedKmh / MAX).toFloat().coerceIn(0f, 1f)
        val l = (lowSpeedKmh / MAX).toFloat().coerceIn(0f, 1f)
        if (s == speedFrac && l == lowFrac && isLow == low) return
        speedFrac = s
        lowFrac = l
        low = isLow
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, track)
        fill.color = if (low) Palette.AMBER else Palette.GREEN
        rect.right = width * speedFrac
        if (speedFrac > 0f) canvas.drawRoundRect(rect, r, r, fill)
        val x = width * lowFrac
        canvas.drawRect(x, 0f, x + dpf(2), height.toFloat(), marker)
    }

    private companion object {
        const val MAX = 120.0
    }
}

/** 영수증 바코드 (장식용). */
internal class BarcodeView(context: Context, private val seed: Long) : View(context) {
    private val paint = Paint().apply { color = Palette.INK }

    override fun onDraw(canvas: Canvas) {
        val rnd = Random(seed)
        val unit = width / 160f
        var x = 0f
        var bar = true
        while (x < width) {
            val w = unit * (1 + rnd.nextInt(if (bar) 3 else 2))
            if (bar) canvas.drawRect(x, 0f, x + w, height.toFloat(), paint)
            x += w
            bar = !bar
        }
    }
}

/** 속도가 빠를수록 빨리 달리는 도트 말. 정차 중에는 서 있고, 화면에 없으면 멈춘다. */
internal class PixelHorseView(context: Context) : View(context) {
    private val paint = Paint().apply { color = Palette.LED_ON }
    private var frame = -1
    private var scheduled = false

    var speedKmh: Double = 0.0
        set(v) {
            field = v
            if (v < MIN_SPEED) {
                if (frame != -1) {
                    frame = -1
                    invalidate()
                }
            } else {
                schedule()
            }
        }

    private val advance = Runnable {
        scheduled = false
        if (speedKmh < MIN_SPEED) return@Runnable
        frame = (frame + 1).mod(GALLOP.size)
        invalidate()
        schedule()
    }

    private fun schedule() {
        if (scheduled || !isAttachedToWindow) return
        scheduled = true
        val delay = (2400.0 / (speedKmh + 10.0)).toLong().coerceIn(55L, 320L)
        postDelayed(advance, delay)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (speedKmh >= MIN_SPEED) schedule()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(advance)
        scheduled = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val rows = if (frame < 0) STAND else GALLOP[frame]
        val cols = COLS
        val cw = (width - paddingLeft - paddingRight).toFloat()
        val ch = (height - paddingTop - paddingBottom).toFloat()
        val cell = min(cw / cols, ch / rows.size)
        val ox = paddingLeft + (cw - cell * cols) / 2f
        val oy = paddingTop + (ch - cell * rows.size) / 2f
        val px = cell * 0.9f
        for (y in rows.indices) {
            val row = rows[y]
            for (x in row.indices) {
                if (row[x] == 'X') {
                    val l = ox + x * cell
                    val t = oy + y * cell
                    canvas.drawRect(l, t, l + px, t + px, paint)
                }
            }
        }
    }

    private companion object {
        const val MIN_SPEED = 2.0
        const val COLS = 28

        // 오른쪽을 보고 달리는 도트 말. 몸통은 공통이고 다리만 프레임마다 바뀐다.
        val BODY = listOf(
            "....................XX......",
            "...................XXXX.....",
            "..................XXXXXXX...",
            ".................XXXXXXXXXX.",
            "................XXXXX..XXXXX",
            "...............XXXXX....XX..",
            "..XXX.........XXXXX.........",
            ".XXXXXXXXXXXXXXXXXX.........",
            "XX.XXXXXXXXXXXXXXXXX........",
            "X..XXXXXXXXXXXXXXXXX........",
            "X..XXXXXXXXXXXXXXXXX........",
            "....XXXXXXXXXXXXXXX.........",
        )
        val STAND = BODY + listOf(
            "....XX..XX....XX..XX........",
            "....XX..XX....XX..XX........",
            "....XX..XX....XX..XX........",
            "....XX..XX....XX..XX........",
            "....XX..XX....XX..XX........",
            "...XXX.XXX...XXX.XXX........",
        )
        val STRETCH = BODY + listOf(
            "...XXX.........XXX..........",
            "..XX............XXX.........",
            ".XX...............XX........",
            "XX.................XX.......",
            "X...................XX......",
            "............................",
        )
        val MID = BODY + listOf(
            "...XXX.X.....XX.X...........",
            "...XX..XX....XX..X..........",
            "..XX....X...XX...XX.........",
            "..X.....X...X.....X.........",
            "..X.....XX..X.....XX........",
            "............................",
        )
        val GATHER = BODY + listOf(
            "....XXX.......XXX...........",
            ".....XX.......XX............",
            "......XX.....XX.............",
            ".......XX...XX..............",
            "........X...X...............",
            "............................",
        )
        val GALLOP = listOf(STRETCH, MID, GATHER, MID)
    }
}

/** 0.6초 주기로 깜빡이는 상태. 매 틱마다 읽어 별도 애니메이션 없이 깜빡이게 한다. */
internal fun blinkPhase(): Boolean = (SystemClock.uptimeMillis() / 600) % 2 == 0L
