package com.anall.addon.taximeter.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.anall.addon.taximeter.meter.Receipt
import com.anall.core.ui.MATCH
import com.anall.core.ui.Palette
import com.anall.core.ui.WRAP
import com.anall.core.ui.add
import com.anall.core.ui.horizontal
import com.anall.core.ui.padding
import com.anall.core.ui.rounded
import com.anall.core.ui.text
import com.anall.core.ui.vertical
import com.anall.core.ui.withAlpha
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val won = NumberFormat.getIntegerInstance(Locale.KOREA)
private fun Int.fmt(): String = won.format(this)

fun formatDuration(totalSec: Long): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m < 100) "%02d:%02d".format(m, s) else "%d:%02d".format(m / 60, m % 60)
}

/** 영수증 종이. */
internal fun buildReceipt(context: Context, r: Receipt): View = with(context) {
    val dateFmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.KOREA)
    vertical {
        background = rounded(Palette.PAPER, 3)
        padding(12, 10)

        add(text("영 수 증", 17, Palette.INK, bold = true, gravity = Gravity.CENTER), MATCH, WRAP, marginBottom = 6)
        line(this, "거리", "%.2f km".format(r.distanceKm))
        line(this, "시간", formatDuration(r.drivingSec))
        divider(this)
        line(this, "기본요금 %.1f km".format(r.baseDistanceKm), r.baseFare.fmt())
        line(this, "거리 ${r.unitDistanceM} m × ${r.distanceUnits}", r.distanceFare.fmt())
        line(this, "시간 ${r.unitTimeSec}초 × ${r.timeUnits}", r.timeFare.fmt())
        line(this, "할증 (심야·시계외)", r.surcharge.fmt())
        divider(this)
        add(
            horizontal {
                add(text("합계", 15, Palette.INK, bold = true), 0, WRAP, weight = 1f)
                add(text("${r.total.fmt()}원", 19, 0xFFD23A2E.toInt(), bold = true))
            },
            MATCH, WRAP,
        )
        add(
            text("${dateFmt.format(Date(r.startedAtMillis))} ~ ${timeFmt.format(Date(r.endedAtMillis))}", 10, Palette.INK.withAlpha(0.6f)),
            MATCH, WRAP, marginTop = 3,
        )
        add(BarcodeView(context, r.endedAtMillis), MATCH, 30, marginTop = 6)
        add(
            text("재미로 보는 영수증 · 실제 요금이 아닙니다", 9, Palette.INK.withAlpha(0.5f), gravity = Gravity.CENTER),
            MATCH, WRAP, marginTop = 3,
        )
    }
}

private fun Context.line(parent: LinearLayout, label: String, value: String) {
    parent.add(
        horizontal {
            add(text(label, 12, Palette.INK), 0, WRAP, weight = 1f)
            add(text(value, 12, Palette.INK).apply { typeface = android.graphics.Typeface.MONOSPACE })
        },
        MATCH, WRAP, marginTop = 3,
    )
}

private fun Context.divider(parent: LinearLayout) {
    parent.add(View(this).apply { setBackgroundColor(Palette.INK.withAlpha(0.35f)) }, MATCH, 1, marginTop = 5, marginBottom = 2)
}
