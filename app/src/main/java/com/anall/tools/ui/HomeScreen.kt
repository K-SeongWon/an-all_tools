package com.anall.tools.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.anall.core.ui.MATCH
import com.anall.core.ui.Palette
import com.anall.core.ui.ScaledLayout
import com.anall.core.ui.WRAP
import com.anall.core.ui.add
import com.anall.core.ui.frame
import com.anall.core.ui.horizontal
import com.anall.core.ui.onClick
import com.anall.core.ui.padding
import com.anall.core.ui.rounded
import com.anall.core.ui.space
import com.anall.core.ui.text
import com.anall.core.ui.vertical
import com.anall.core.ui.withAlpha
import com.anall.tools.AnAllApp
import com.anall.tools.MainActivity
import com.anall.tools.addon.AddonEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 홈: 애드온 타일 목록. */
class HomeScreen(private val activity: MainActivity, private val app: AnAllApp) : FrameLayout(activity) {

    private val clockFmt = SimpleDateFormat("HH:mm", Locale.KOREA)
    private var clock: TextView? = null
    private val clockTick = object : Runnable {
        override fun run() {
            clock?.text = clockFmt.format(Date())
            postDelayed(this, 10_000)
        }
    }

    init {
        setBackgroundColor(Palette.BACKGROUND)
        addView(ScaledLayout(activity) { landscape -> build(landscape) }, LayoutParams(MATCH, MATCH))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(clockTick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(clockTick)
        super.onDetachedFromWindow()
    }

    private fun build(landscape: Boolean): View = with(activity) {
        vertical {
            padding(16, 12)
            add(
                horizontal {
                    add(text("안올툴", 26, Palette.TEXT, bold = true))
                    add(text("An-All Tools", 15, Palette.TEXT_DIM, bold = true, italic = true), marginStart = 10, gravity = Gravity.BOTTOM)
                    add(space(), 0, 1, weight = 1f)
                    clock = add(text(clockFmt.format(Date()), 20, Palette.TEXT, bold = true)) as TextView
                    add(pillButton("설정") { activity.showHostSettings() }, WRAP, 40, marginStart = 14)
                },
                MATCH, 44,
            )
            add(text(statusLine(), 13, Palette.TEXT_DIM), MATCH, WRAP, marginTop = 6)

            val columns = if (landscape) 4 else 3
            val entries = app.manager.entries
            var row: LinearLayout? = null
            entries.forEachIndexed { i, e ->
                if (i % columns == 0) {
                    row = horizontal { gravity = Gravity.TOP }
                    add(row!!, MATCH, 200, marginTop = 14)
                }
                row!!.add(tile(e), 0, MATCH, weight = 1f, marginStart = if (i % columns == 0) 0 else 14)
            }
            // 마지막 줄 빈칸 채우기 (타일 폭 유지)
            val rest = (columns - entries.size % columns) % columns
            repeat(rest) { row?.add(space(), 0, MATCH, weight = 1f, marginStart = 14) }

            if (entries.isEmpty()) add(text("설치된 애드온이 없습니다", 16, Palette.TEXT_DIM, gravity = Gravity.CENTER), MATCH, 200)
        }
    }

    private fun statusLine(): String {
        val u = app.updater
        val parts = mutableListOf("애드온 ${app.manager.entries.size}개")
        if (u.lastCheckMillis > 0) {
            parts += "업데이트 확인 " + SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).format(Date(u.lastCheckMillis))
            if (u.lastSummary.isNotBlank()) parts += u.lastSummary
        } else {
            parts += if (u.autoUpdate) "자동 업데이트 켜짐" else "자동 업데이트 꺼짐"
        }
        return parts.joinToString(" · ")
    }

    private fun tile(e: AddonEntry): View = with(activity) {
        val info = e.addon.info
        val busy = app.manager.isBusy(e.id)
        vertical {
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(Palette.PANEL, 16, if (busy) Palette.GREEN else Palette.PANEL_BORDER, if (busy) 2 else 1.5)
            padding(12)
            onClick { activity.openAddon(e.id) }

            val icon = runCatching { e.addon.createIcon(activity) }.getOrNull() ?: LetterIcon(info.name.take(1), info.accentColor)
            add(
                frame {
                    background = rounded(info.accentColor.withAlpha(0.14f), 18, info.accentColor.withAlpha(0.5f), 1.5)
                    add(View(activity).apply { background = icon }, MATCH, MATCH, margin = 8)
                },
                96, 96, marginTop = 4,
            )
            add(text(info.name, 18, Palette.TEXT, bold = true, gravity = Gravity.CENTER), MATCH, WRAP, marginTop = 12)
            val sub = buildString {
                append("v").append(info.versionName)
                append(if (e.source == AddonEntry.Source.OTA) " · OTA" else " · 내장")
                if (busy) append(" · 실행 중")
            }
            add(text(sub, 12, if (busy) Palette.GREEN else Palette.TEXT_DIM, gravity = Gravity.CENTER), MATCH, WRAP, marginTop = 6)
        }
    }
}

/** 아이콘이 없는 애드온용 글자 아이콘. */
internal class LetterIcon(private val letter: String, private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        paint.color = color
        paint.textSize = b.height() * 0.6f
        val y = b.exactCenterY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, b.exactCenterX(), y, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal fun MainActivity.pillButton(label: String, color: Int = Palette.CHIP_IDLE, textColor: Int = Palette.TEXT, action: () -> Unit): TextView =
    text(label, 15, textColor, bold = true, gravity = Gravity.CENTER).apply {
        background = rounded(color, 8)
        padding(14, 0)
        onClick(action)
    }
