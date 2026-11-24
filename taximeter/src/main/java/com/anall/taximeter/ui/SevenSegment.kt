package com.anall.taximeter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp

/*
 * 7-세그먼트 LED 숫자. 꺼진 세그먼트도 흐리게 그려서 실제 미터기처럼 "8" 잔상이 보이게 한다.
 *
 *    aaa
 *   f   b
 *    ggg
 *   e   c
 *    ddd
 */

private const val A = 1
private const val B = 2
private const val C = 4
private const val D = 8
private const val E = 16
private const val F = 32
private const val G = 64
private const val ALL = 127

private fun maskOf(ch: Char): Int = when (ch) {
    '0' -> A or B or C or D or E or F
    '1' -> B or C
    '2' -> A or B or G or E or D
    '3' -> A or B or G or C or D
    '4' -> F or G or B or C
    '5' -> A or F or G or C or D
    '6' -> A or F or G or E or C or D
    '7' -> A or B or C
    '8' -> ALL
    '9' -> A or B or C or D or F or G
    '-' -> G
    else -> 0
}

private sealed interface Cell {
    data class Digit(val ch: Char, var dot: Boolean = false) : Cell
    data class Colon(val lit: Boolean) : Cell
}

private const val DIGIT_W = 0.56f
private const val GAP = 0.15f
private const val COLON_W = 0.24f
private const val SKEW = 0.07f

private fun parse(text: String, minDigits: Int): List<Cell> {
    val cells = mutableListOf<Cell>()
    for (ch in text) {
        when (ch) {
            '.' -> (cells.lastOrNull() as? Cell.Digit)?.dot = true
            ':' -> cells += Cell.Colon(true)
            else -> cells += Cell.Digit(ch)
        }
    }
    val digits = cells.count { it is Cell.Digit }
    repeat((minDigits - digits).coerceAtLeast(0)) { cells.add(0, Cell.Digit(' ')) }
    return cells
}

private fun widthFactor(cells: List<Cell>): Float {
    var w = 0f
    cells.forEachIndexed { i, c ->
        w += if (c is Cell.Digit) DIGIT_W else COLON_W
        if (i != cells.lastIndex) w += GAP
    }
    return w + SKEW
}

/**
 * @param text 표시할 문자열. 숫자, 공백(꺼짐), '-', '.', ':' 지원.
 * @param minDigits 왼쪽을 꺼진 숫자로 채울 최소 자릿수.
 */
@Composable
fun SevenSegment(
    text: String,
    minDigits: Int,
    height: Dp,
    modifier: Modifier = Modifier,
    on: Color = MeterColors.LedOn,
    off: Color = MeterColors.LedOff,
) {
    val cells = parse(text, minDigits)
    Canvas(modifier.size(height * widthFactor(cells), height)) {
        val h = size.height
        val canvas = drawContext.canvas
        canvas.save()
        canvas.translate(SKEW * h, 0f)
        canvas.skew(-SKEW, 0f)
        var x = 0f
        cells.forEach { cell ->
            when (cell) {
                is Cell.Digit -> {
                    drawDigit(x, h, maskOf(cell.ch), on, off)
                    if (cell.dot) {
                        val t = h * 0.11f
                        drawCircle(on, radius = t * 0.55f, center = Offset(x + DIGIT_W * h + GAP * h / 2f, h - t / 2f))
                    }
                    x += DIGIT_W * h
                }
                is Cell.Colon -> {
                    val r = h * 0.055f
                    val cx = x + COLON_W * h / 2f
                    val color = if (cell.lit) on else off
                    drawCircle(color, r, Offset(cx, h * 0.3f))
                    drawCircle(color, r, Offset(cx, h * 0.7f))
                    x += COLON_W * h
                }
            }
            x += GAP * h
        }
        canvas.restore()
    }
}

private fun DrawScope.drawDigit(x0: Float, h: Float, mask: Int, on: Color, off: Color) {
    val w = DIGIT_W * h
    val t = h * 0.11f
    val g = t * 0.18f
    val l = t / 2f
    val r = w - t / 2f
    val top = t / 2f
    val mid = h / 2f
    val bot = h - t / 2f

    val segs = arrayOf(
        A to horizontal(l + g, r - g, top, t),
        B to vertical(r, top + g, mid - g, t),
        C to vertical(r, mid + g, bot - g, t),
        D to horizontal(l + g, r - g, bot, t),
        E to vertical(l, mid + g, bot - g, t),
        F to vertical(l, top + g, mid - g, t),
        G to horizontal(l + g, r - g, mid, t),
    )
    translate(left = x0) {
        segs.forEach { (bit, path) -> if (mask and bit == 0) drawPath(path, off) }
        segs.forEach { (bit, path) ->
            if (mask and bit != 0) {
                drawPath(path, on.copy(alpha = 0.22f), style = Stroke(width = t * 0.55f, join = StrokeJoin.Round))
                drawPath(path, on)
            }
        }
    }
}

private fun horizontal(x0: Float, x1: Float, y: Float, t: Float) = Path().apply {
    val ht = t / 2f
    moveTo(x0, y)
    lineTo(x0 + ht, y - ht)
    lineTo(x1 - ht, y - ht)
    lineTo(x1, y)
    lineTo(x1 - ht, y + ht)
    lineTo(x0 + ht, y + ht)
    close()
}

private fun vertical(x: Float, y0: Float, y1: Float, t: Float) = Path().apply {
    val ht = t / 2f
    moveTo(x, y0)
    lineTo(x + ht, y0 + ht)
    lineTo(x + ht, y1 - ht)
    lineTo(x, y1)
    lineTo(x - ht, y1 - ht)
    lineTo(x - ht, y0 + ht)
    close()
}
