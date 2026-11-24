package com.anall.core.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** 안올툴 공통 색. 차 안 야간 주행을 고려한 어두운 팔레트. */
object Palette {
    const val BACKGROUND = 0xFF090C0F.toInt()
    const val PANEL = 0xFF141A1F.toInt()
    const val PANEL_BORDER = 0xFF2B343C.toInt()
    const val INNER = 0xFF0D1216.toInt()
    const val INNER_BORDER = 0xFF3A4650.toInt()
    const val LED_ON = 0xFFEAF8FF.toInt()
    const val LED_OFF = 0xFF1C2830.toInt()
    const val TEXT = 0xFFE6ECF0.toInt()
    const val TEXT_DIM = 0xFF8593A0.toInt()
    const val CHIP_IDLE = 0xFF262E35.toInt()
    const val AMBER = 0xFFFFB547.toInt()
    const val GREEN = 0xFF5BE07A.toInt()
    const val GREEN_DARK = 0xFF2E9E4F.toInt()
    const val RED = 0xFFE5483D.toInt()
    const val BLUE = 0xFF58A8FF.toInt()
    const val PURPLE = 0xFFB98BFF.toInt()
    const val CYAN = 0xFF6FD3FF.toInt()
    const val PAPER = 0xFFF3F0E8.toInt()
    const val INK = 0xFF26282B.toInt()
    const val DARK_TEXT = 0xFF101418.toInt()
}

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Number): Float = v.toFloat() * resources.displayMetrics.density
fun View.dp(v: Number): Int = context.dp(v)
fun View.dpf(v: Number): Float = context.dpf(v)

fun Int.withAlpha(alpha: Float): Int = (this and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

/** 둥근 사각형 배경. */
fun Context.rounded(fill: Int, radiusDp: Number, stroke: Int = 0, strokeDp: Number = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dpf(radiusDp)
        if (strokeDp.toFloat() > 0f) setStroke(dp(strokeDp), stroke)
    }

/** 시스템 글꼴 크기 설정에 흔들리지 않도록 dp 단위로 글자 크기를 정한 TextView. */
fun Context.text(
    value: CharSequence = "",
    sizeDp: Number = 14,
    color: Int = Palette.TEXT,
    bold: Boolean = false,
    italic: Boolean = false,
    gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL,
): TextView = TextView(this).apply {
    text = value
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp.toFloat())
    setTextColor(color)
    includeFontPadding = false
    val style = when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    setTypeface(Typeface.DEFAULT, style)
    this.gravity = gravity
}

fun Context.vertical(block: LinearLayout.() -> Unit = {}): LinearLayout =
    LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; block() }

fun Context.horizontal(block: LinearLayout.() -> Unit = {}): LinearLayout =
    LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; block() }

fun Context.frame(block: FrameLayout.() -> Unit = {}): FrameLayout = FrameLayout(this).apply(block)

fun Context.space(): View = View(this)

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** LinearLayout 에 자식 추가. 크기는 dp (MATCH/WRAP 은 그대로). */
fun LinearLayout.add(
    child: View,
    width: Int = WRAP,
    height: Int = WRAP,
    weight: Float = 0f,
    marginStart: Number = 0,
    marginTop: Number = 0,
    marginEnd: Number = 0,
    marginBottom: Number = 0,
    gravity: Int = -1,
): View {
    val lp = LinearLayout.LayoutParams(size(width), size(height), weight).apply {
        setMargins(dp(marginStart), dp(marginTop), dp(marginEnd), dp(marginBottom))
        if (gravity != -1) this.gravity = gravity
    }
    addView(child, lp)
    return child
}

fun FrameLayout.add(
    child: View,
    width: Int = WRAP,
    height: Int = WRAP,
    gravity: Int = Gravity.TOP or Gravity.START,
    margin: Number = 0,
): View {
    val lp = FrameLayout.LayoutParams(size(width), size(height), gravity).apply {
        val m = dp(margin)
        setMargins(m, m, m, m)
    }
    addView(child, lp)
    return child
}

private fun View.size(v: Int): Int = if (v == MATCH || v == WRAP) v else dp(v)

fun View.padding(all: Number) = setPadding(dp(all), dp(all), dp(all), dp(all))
fun View.padding(h: Number, v: Number) = setPadding(dp(h), dp(v), dp(h), dp(v))

fun View.onClick(action: () -> Unit) = setOnClickListener { action() }
