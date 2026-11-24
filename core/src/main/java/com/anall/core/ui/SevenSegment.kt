package com.anall.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/*
 * 7-세그먼트 LED 숫자. 꺼진 세그먼트도 흐리게 그려 "8" 잔상이 보이게 한다.
 *
 *    aaa
 *   f   b
 *    ggg
 *   e   c
 *    ddd
 */
class SevenSegmentPainter {
    var onColor = Palette.LED_ON
    var offColor = Palette.LED_OFF

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    fun measureWidth(text: CharSequence, minDigits: Int, height: Float): Float {
        var digits = 0
        var colons = 0
        for (c in text) when (c) {
            '.' -> Unit
            ':' -> colons++
            else -> digits++
        }
        digits = maxOf(digits, minDigits)
        val cells = digits + colons
        if (cells == 0) return 0f
        return height * (digits * DIGIT_W + colons * COLON_W + (cells - 1) * GAP + SKEW)
    }

    /** 오른쪽 정렬이 필요하면 [minDigits] 로 왼쪽을 꺼진 숫자로 채운다. */
    fun draw(canvas: Canvas, text: CharSequence, minDigits: Int, left: Float, top: Float, height: Float) {
        var digits = 0
        for (c in text) if (c != '.' && c != ':') digits++
        val h = height
        canvas.save()
        canvas.translate(left + SKEW * h, top)
        canvas.skew(-SKEW, 0f)
        var x = 0f
        repeat((minDigits - digits).coerceAtLeast(0)) {
            drawDigit(canvas, x, h, 0)
            x += (DIGIT_W + GAP) * h
        }
        for (c in text) {
            when (c) {
                '.' -> {
                    val t = h * THICK
                    fill.color = onColor
                    canvas.drawCircle(x - GAP * h / 2f, h - t / 2f, t * 0.55f, fill)
                }
                ':' -> {
                    val r = h * 0.055f
                    val cx = x + COLON_W * h / 2f
                    fill.color = onColor
                    canvas.drawCircle(cx, h * 0.3f, r, fill)
                    canvas.drawCircle(cx, h * 0.7f, r, fill)
                    x += (COLON_W + GAP) * h
                }
                else -> {
                    drawDigit(canvas, x, h, maskOf(c))
                    x += (DIGIT_W + GAP) * h
                }
            }
        }
        canvas.restore()
    }

    private fun drawDigit(canvas: Canvas, x0: Float, h: Float, mask: Int) {
        val w = DIGIT_W * h
        val t = h * THICK
        val g = t * 0.18f
        val l = x0 + t / 2f
        val r = x0 + w - t / 2f
        val top = t / 2f
        val mid = h / 2f
        val bot = h - t / 2f
        glow.strokeWidth = t * 0.55f
        glow.color = onColor.withAlpha(0.22f)
        for (bit in 0 until 7) {
            when (bit) {
                0 -> horizontal(l + g, r - g, top, t)
                1 -> vertical(r, top + g, mid - g, t)
                2 -> vertical(r, mid + g, bot - g, t)
                3 -> horizontal(l + g, r - g, bot, t)
                4 -> vertical(l, mid + g, bot - g, t)
                5 -> vertical(l, top + g, mid - g, t)
                else -> horizontal(l + g, r - g, mid, t)
            }
            if (mask and (1 shl bit) != 0) {
                canvas.drawPath(path, glow)
                fill.color = onColor
            } else {
                fill.color = offColor
            }
            canvas.drawPath(path, fill)
        }
    }

    private fun horizontal(x0: Float, x1: Float, y: Float, t: Float) {
        val ht = t / 2f
        path.rewind()
        path.moveTo(x0, y)
        path.lineTo(x0 + ht, y - ht)
        path.lineTo(x1 - ht, y - ht)
        path.lineTo(x1, y)
        path.lineTo(x1 - ht, y + ht)
        path.lineTo(x0 + ht, y + ht)
        path.close()
    }

    private fun vertical(x: Float, y0: Float, y1: Float, t: Float) {
        val ht = t / 2f
        path.rewind()
        path.moveTo(x, y0)
        path.lineTo(x + ht, y0 + ht)
        path.lineTo(x + ht, y1 - ht)
        path.lineTo(x, y1)
        path.lineTo(x - ht, y1 - ht)
        path.lineTo(x - ht, y0 + ht)
        path.close()
    }

    private companion object {
        const val DIGIT_W = 0.56f
        const val GAP = 0.15f
        const val COLON_W = 0.24f
        const val SKEW = 0.07f
        const val THICK = 0.11f

        // 비트: a=0 b=1 c=2 d=3 e=4 f=5 g=6
        fun maskOf(c: Char): Int = when (c) {
            '0' -> 0b0111111
            '1' -> 0b0000110
            '2' -> 0b1011011
            '3' -> 0b1001111
            '4' -> 0b1100110
            '5' -> 0b1101101
            '6' -> 0b1111101
            '7' -> 0b0000111
            '8' -> 0b1111111
            '9' -> 0b1101111
            '-' -> 0b1000000
            else -> 0
        }
    }
}

/** 높이는 레이아웃에서 정하고, 폭은 글자 수에 맞춰 스스로 정한다. */
class SevenSegmentView(context: Context, minDigits: Int = 1) : View(context) {
    private val painter = SevenSegmentPainter()

    var minDigits: Int = minDigits
        set(v) {
            if (field != v) {
                field = v
                requestLayout()
            }
        }

    var text: String = ""
        set(v) {
            if (field == v) return
            val relayout = cellCount(field) != cellCount(v)
            field = v
            if (relayout) requestLayout()
            invalidate()
        }

    var onColor: Int
        get() = painter.onColor
        set(v) {
            painter.onColor = v
            invalidate()
        }

    private fun cellCount(s: String) = s.count { it != '.' }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val w = painter.measureWidth(text, minDigits, h.toFloat()).toInt() + 1
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = painter.measureWidth(text, minDigits, h)
        painter.draw(canvas, text, minDigits, width - w, 0f, h)
    }
}
