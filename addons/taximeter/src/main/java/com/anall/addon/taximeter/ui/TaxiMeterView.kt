package com.anall.addon.taximeter.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.anall.addon.taximeter.meter.FareConfig
import com.anall.addon.taximeter.meter.IdleSign
import com.anall.addon.taximeter.meter.MeterUiState
import com.anall.addon.taximeter.meter.Receipt
import com.anall.core.ui.MATCH
import com.anall.core.ui.Palette
import com.anall.core.ui.ScaledLayout
import com.anall.core.ui.SevenSegmentView
import com.anall.core.ui.WRAP
import com.anall.core.ui.add
import com.anall.core.ui.dp
import com.anall.core.ui.dpf
import com.anall.core.ui.frame
import com.anall.core.ui.horizontal
import com.anall.core.ui.onClick
import com.anall.core.ui.padding
import com.anall.core.ui.rounded
import com.anall.core.ui.space
import com.anall.core.ui.text
import com.anall.core.ui.vertical
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 미터기 화면. 960x540(가로) / 600xN(세로) 기준으로 만들고 [ScaledLayout] 이 화면에 맞춘다. */
class TaxiMeterView(context: Context, private val actions: Actions) : FrameLayout(context) {

    interface Actions {
        fun onStartTrip()
        fun onEndTrip()
        fun onVacant()
        fun onToggleIdleSign(sign: IdleSign)
        fun onToggleOutOfCity()
        fun onHome()
        fun onSettings()
    }

    private var widgets: Widgets? = null
    private var last: MeterUiState? = null

    init {
        setBackgroundColor(Palette.BACKGROUND)
        addView(
            ScaledLayout(context) { landscape ->
                Widgets(context, landscape, actions).also { w ->
                    widgets = w
                    last?.let { w.render(it) }
                }.root
            },
            LayoutParams(MATCH, MATCH),
        )
    }

    fun render(state: MeterUiState) {
        last = state
        widgets?.render(state)
    }
}

private class Widgets(private val ctx: Context, landscape: Boolean, private val actions: TaxiMeterView.Actions) {

    private val clockFmt = SimpleDateFormat("HH:mm", Locale.KOREA)

    // 상단
    private lateinit var gpsDot: View
    private lateinit var gpsText: TextView
    private lateinit var clock: TextView

    // 미터기
    private lateinit var surchargeLabel: TextView
    private lateinit var chipVacant: TextView
    private lateinit var chipDriving: TextView
    private lateinit var chipSurcharge: TextView
    private lateinit var chipReserved: TextView
    private lateinit var chipCall: TextView
    private lateinit var fareTitle: TextView
    private lateinit var horse: PixelHorseView
    private lateinit var fareSeg: SevenSegmentView
    private lateinit var progress: ProgressBarView
    private lateinit var nextText: TextView
    private lateinit var distSeg: SevenSegmentView
    private lateinit var timeSeg: SevenSegmentView
    private lateinit var lowSeg: SevenSegmentView
    private lateinit var tripButton: LinearLayout
    private lateinit var tripLamp: View
    private lateinit var tripText: TextView
    private lateinit var info1: TextView
    private lateinit var info2: TextView

    // 오른쪽
    private lateinit var modeTag: TextView
    private lateinit var speedSeg: SevenSegmentView
    private lateinit var speedBar: SpeedBarView
    private lateinit var accText: TextView
    private lateinit var placeholder: TextView
    private lateinit var receiptScroll: ScrollView
    private var shownReceipt: Receipt? = null
    private var running: Boolean? = null

    val root: View = ctx.vertical {
        setBackgroundColor(Palette.BACKGROUND)
        padding(10)
        add(topBar(), MATCH, 38)
        val body = if (landscape) {
            ctx.horizontal {
                gravity = Gravity.TOP
                add(meterPanel(), 0, MATCH, weight = 0.66f)
                add(tripPanel(), 0, MATCH, weight = 0.34f, marginStart = 10)
            }
        } else {
            ctx.vertical {
                add(meterPanel(), MATCH, 500)
                add(tripPanel(), MATCH, 0, weight = 1f, marginTop = 10)
            }
        }
        add(body, MATCH, 0, weight = 1f, marginTop = 8)
    }

    // ------------------------------------------------------------ 만들기

    private fun pill(label: String, action: () -> Unit): TextView =
        ctx.text(label, 15, Palette.TEXT, bold = true, gravity = Gravity.CENTER).apply {
            background = ctx.rounded(Palette.CHIP_IDLE, 8)
            padding(12, 0)
            onClick(action)
        }

    private fun topBar() = ctx.horizontal {
        add(pill("‹ 홈") { actions.onHome() }, WRAP, MATCH)
        add(ctx.text("//", 22, Palette.RED, bold = true, italic = true), marginStart = 14)
        add(ctx.text("TAXI METER", 20, Palette.TEXT, bold = true, italic = true), marginStart = 6)
        add(ctx.space(), 0, 1, weight = 1f)
        gpsDot = add(View(ctx), 10, 10) as View
        gpsText = add(ctx.text("", 14, Palette.AMBER, bold = true), marginStart = 6) as TextView
        clock = add(ctx.text("", 20, Palette.TEXT, bold = true), marginStart = 16) as TextView
        add(pill("설정") { actions.onSettings() }, WRAP, MATCH, marginStart = 12)
    }

    private fun panel(block: LinearLayout.() -> Unit) = ctx.vertical {
        background = ctx.rounded(Palette.PANEL, 14, Palette.PANEL_BORDER, 1.5)
        padding(12)
        block()
    }

    private fun innerBox(radius: Number = 10) = ctx.rounded(Palette.INNER, radius, Palette.PANEL_BORDER, 1)

    private fun meterPanel() = panel {
        add(
            ctx.horizontal {
                add(ctx.text("KOREA DIGITAL TAXIMETER", 13, Palette.TEXT, bold = true))
                add(ctx.space(), 0, 1, weight = 1f)
                surchargeLabel = add(ctx.text("", 12, Palette.AMBER, bold = true)) as TextView
            },
            MATCH, WRAP,
        )
        add(
            ctx.horizontal {
                chipVacant = chip(this, "빈차", 0) { actions.onVacant() }
                chipDriving = chip(this, "주행", 8) { if (running != true) actions.onStartTrip() }
                chipSurcharge = chip(this, "할증", 8) { actions.onToggleOutOfCity() }
                chipReserved = chip(this, "예약", 8) { actions.onToggleIdleSign(IdleSign.RESERVED) }
                chipCall = chip(this, "호출", 8) { actions.onToggleIdleSign(IdleSign.CALL) }
            },
            MATCH, 38, marginTop = 9,
        )
        add(fareBox(), MATCH, 0, weight = 1f, marginTop = 9)
        add(
            ctx.horizontal {
                distSeg = statBox(this, "주행 거리 km", 0)
                timeSeg = statBox(this, "주행 시간", 8)
                lowSeg = statBox(this, "저속 시간", 8)
            },
            MATCH, WRAP, marginTop = 9,
        )
        add(
            ctx.horizontal {
                tripButton = ctx.horizontal {
                    padding(16, 0)
                    tripLamp = add(View(ctx), 14, 14) as View
                    tripText = add(ctx.text("", 22, 0xFFFFFFFF.toInt(), bold = true, gravity = Gravity.CENTER), 0, WRAP, weight = 1f) as TextView
                    onClick { if (running == true) actions.onEndTrip() else actions.onStartTrip() }
                }
                add(tripButton, 250, 56)
                add(
                    ctx.vertical {
                        info1 = add(ctx.text("", 12, Palette.TEXT_DIM)) as TextView
                        info2 = add(ctx.text("", 12, Palette.TEXT_DIM), marginTop = 3) as TextView
                    },
                    0, WRAP, weight = 1f, marginStart = 14,
                )
            },
            MATCH, WRAP, marginTop = 9,
        )
    }

    private fun chip(row: LinearLayout, label: String, marginStart: Int, action: () -> Unit): TextView {
        val tv = ctx.text(label, 16, Palette.TEXT_DIM, gravity = Gravity.CENTER).apply { onClick(action) }
        row.add(tv, 0, MATCH, weight = 1f, marginStart = marginStart)
        return tv
    }

    private fun fareBox() = ctx.frame {
        background = ctx.rounded(Palette.INNER, 12, Palette.INNER_BORDER, 1.5)
        padding(12, 8)
        add(
            ctx.vertical {
                fareTitle = add(ctx.text("", 13, Palette.TEXT, bold = true)) as TextView
                horse = PixelHorseView(ctx).apply {
                    background = ctx.rounded(0xFF1A2228.toInt(), 6)
                    padding(6)
                }
                add(horse, 108, 72, marginTop = 6)
            },
        )
        add(
            ctx.vertical {
                gravity = Gravity.END
                add(
                    ctx.horizontal {
                        gravity = Gravity.BOTTOM
                        fareSeg = SevenSegmentView(ctx, 6)
                        add(fareSeg, WRAP, 96)
                        add(ctx.text("원", 20, Palette.TEXT, bold = true), marginStart = 6)
                    },
                )
                progress = ProgressBarView(ctx)
                add(progress, 300, 7, marginTop = 8, marginEnd = 28)
            },
            gravity = Gravity.CENTER_VERTICAL or Gravity.END,
        )
        nextText = add(ctx.text("", 13, Palette.TEXT_DIM), gravity = Gravity.BOTTOM or Gravity.START) as TextView
    }

    private fun statBox(row: LinearLayout, label: String, marginStart: Int): SevenSegmentView {
        val seg = SevenSegmentView(ctx, 4)
        row.add(
            ctx.vertical {
                background = innerBox()
                padding(10, 6)
                add(ctx.text(label, 12, Palette.TEXT_DIM))
                add(seg, WRAP, 38, marginTop = 2, gravity = Gravity.END)
            },
            0, WRAP, weight = 1f, marginStart = marginStart,
        )
        return seg
    }

    private fun tripPanel() = panel {
        add(ctx.text("TRIP MONITOR", 13, Palette.TEXT, bold = true))
        add(
            ctx.vertical {
                background = innerBox()
                padding(10, 8)
                add(
                    ctx.horizontal {
                        add(ctx.text("현재 속도", 12, Palette.TEXT_DIM))
                        add(ctx.space(), 0, 1, weight = 1f)
                        modeTag = add(ctx.text("", 11, Palette.AMBER, bold = true).apply { padding(5, 1) }) as TextView
                    },
                    MATCH, WRAP,
                )
                add(
                    ctx.horizontal {
                        gravity = Gravity.END or Gravity.BOTTOM
                        speedSeg = SevenSegmentView(ctx, 3)
                        add(speedSeg, WRAP, 54)
                        add(ctx.text("km/h", 14, Palette.TEXT, bold = true), marginStart = 6)
                    },
                    MATCH, WRAP, marginTop = 2,
                )
                speedBar = SpeedBarView(ctx)
                add(speedBar, MATCH, 6, marginTop = 6)
                accText = add(ctx.text("", 11, Palette.TEXT_DIM), marginTop = 4) as TextView
            },
            MATCH, WRAP, marginTop = 8,
        )
        add(
            ctx.frame {
                background = innerBox()
                padding(8)
                placeholder = add(ctx.text("", 13, Palette.TEXT_DIM, gravity = Gravity.CENTER), MATCH, WRAP, Gravity.CENTER) as TextView
                receiptScroll = ScrollView(ctx).apply {
                    isVerticalScrollBarEnabled = false
                    visibility = View.GONE
                }
                add(receiptScroll, MATCH, MATCH)
            },
            MATCH, 0, weight = 1f, marginTop = 8,
        )
    }

    // ------------------------------------------------------------ 그리기

    fun render(s: MeterUiState) {
        val c = s.config
        val blink = blinkPhase()

        // 상단
        val (gpsColor, gpsLabel) = when {
            s.simulation -> Palette.PURPLE to "SIM 모의주행"
            s.gpsProblem != null -> Palette.RED to s.gpsProblem
            s.hasFix -> Palette.GREEN to "GPS ${s.satellitesUsed}/${s.satellitesVisible}"
            else -> Palette.AMBER to "GPS 수신 중 ${s.satellitesUsed}/${s.satellitesVisible}"
        }
        gpsText.setTextColor(gpsColor)
        gpsText.setIfChanged(gpsLabel)
        setOval(gpsDot, gpsColor)
        gpsDot.alpha = if (s.hasFix || s.simulation || blink) 1f else 0.25f
        clock.setIfChanged(clockFmt.format(Date()))

        // 표시등
        val idle = !s.running
        surchargeLabel.setIfChanged(surchargeLabel(s))
        chip(chipVacant, idle && s.idleSign == IdleSign.NONE, Palette.RED)
        chip(chipDriving, s.running, Palette.GREEN)
        chip(chipSurcharge, s.surchargeActive, Palette.AMBER)
        chip(chipReserved, idle && s.idleSign == IdleSign.RESERVED, Palette.BLUE)
        chip(chipCall, idle && s.idleSign == IdleSign.CALL, Palette.PURPLE)

        // 요금
        fareTitle.setIfChanged(if (s.running) "요금 · 주행" else if (s.fare != null) "요금 · 지불" else "요금 · 빈차")
        horse.speedKmh = if (s.running) s.speedKmh else 0.0
        fareSeg.text = s.fare?.toString() ?: ""
        progress.progress = if (s.running) s.progressToNextUnit else 0f
        nextText.setIfChanged(nextUnitText(s))

        val km = s.distanceKm
        distSeg.text = if (km < 100) "%.2f".format(km) else "%.1f".format(km)
        timeSeg.text = formatDuration(s.drivingSec)
        lowSeg.text = formatDuration(s.lowSpeedSec)

        if (running != s.running) {
            running = s.running
            val base = if (s.running) Palette.RED else Palette.GREEN_DARK
            tripButton.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(base, darker(base)),
            ).apply {
                cornerRadius = ctx.dpf(10)
                setStroke(ctx.dp(1.5), 0x40FFFFFF)
            }
            tripText.text = if (s.running) "운행 종료" else "운행 시작"
        }
        setOval(tripLamp, if (s.running) Palette.AMBER else 0xFFBFF5CC.toInt())
        tripLamp.alpha = if (!s.running || blink) 1f else 0.3f

        info1.setIfChanged(
            "기본 %,d원 (%.1fkm) · %dm / %d초당 %,d원".format(
                c.baseFare, c.baseDistanceM / 1000, c.unitDistanceM.toInt(), c.unitTimeSec.toInt(), c.unitFare,
            ),
        )
        info2.setIfChanged(nightInfo(c))

        // 속도
        val (mode, modeColor) = if (s.lowSpeed) "시간 요금" to Palette.AMBER else "거리 요금" to Palette.GREEN
        if (modeTag.text.toString() != mode) {
            modeTag.text = mode
            modeTag.setTextColor(modeColor)
            modeTag.background = ctx.rounded(0, 4, modeColor, 1)
        }
        speedSeg.text = s.speedKmh.roundToInt().toString()
        speedBar.set(s.speedKmh, c.lowSpeedKmh, s.lowSpeed)
        val acc = s.accuracyM?.takeIf { !s.simulation }
        accText.setVisible(acc != null)
        if (acc != null) accText.setIfChanged("GPS 정확도 ±${acc.roundToInt()} m")

        // 영수증
        placeholder.setIfChanged(if (s.running) "운행 중…\n운행을 마치면 영수증이 나옵니다" else "운행을 마치면 영수증이 나옵니다")
        if (s.receipt !== shownReceipt) {
            shownReceipt = s.receipt
            showReceipt(s.receipt, animate = true)
        }
        placeholder.setVisible(s.receipt == null)
    }

    private fun showReceipt(r: Receipt?, animate: Boolean) {
        receiptScroll.removeAllViews()
        if (r == null) {
            receiptScroll.visibility = View.GONE
            return
        }
        receiptScroll.addView(buildReceipt(ctx, r))
        receiptScroll.visibility = View.VISIBLE
        if (animate) {
            // 영수증이 위에서부터 인쇄되어 나오는 느낌
            receiptScroll.post {
                val h = receiptScroll.height.toFloat().coerceAtLeast(1f)
                ObjectAnimator.ofFloat(receiptScroll, View.TRANSLATION_Y, -h, 0f).apply {
                    duration = 1_400
                    interpolator = LinearInterpolator()
                    start()
                }
            }
        }
    }

    private fun chip(tv: TextView, active: Boolean, color: Int) {
        val tag = if (active) color else 0
        if (tv.tag == tag) return
        tv.tag = tag
        tv.background = if (active) {
            ctx.rounded(color, 8, 0x8CFFFFFF.toInt(), 1.5)
        } else {
            ctx.rounded(Palette.CHIP_IDLE, 8)
        }
        tv.setTextColor(if (active) Palette.DARK_TEXT else Palette.TEXT_DIM)
        tv.setTypeface(tv.typeface, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    /** 동그란 표시등 색을 바꾼다. 색이 같으면 아무것도 하지 않는다. */
    private fun setOval(v: View, color: Int) {
        if (v.tag == color) return
        v.tag = color
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun darker(c: Int): Int {
        val r = ((c shr 16) and 0xFF) * 7 / 10
        val g = ((c shr 8) and 0xFF) * 7 / 10
        val b = (c and 0xFF) * 7 / 10
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun surchargeLabel(s: MeterUiState): String {
        val parts = mutableListOf("${s.config.regionName} 요금")
        if (s.nightRate > 0) parts += "심야 ${(s.nightRate * 100).roundToInt()}%"
        if (s.outOfCity) parts += "시계외 ${(s.config.outOfCityRate * 100).roundToInt()}%"
        return parts.joinToString(" · ")
    }

    private fun nightInfo(c: FareConfig): String {
        val parts = mutableListOf<String>()
        if (c.nightStartHour != c.nightEndHour) {
            var night = "심야 %02d~%02d시 %d%%".format(c.nightStartHour, c.nightEndHour, (c.nightRate * 100).roundToInt())
            if (c.peakStartHour != c.peakEndHour) {
                night += " (%02d~%02d시 %d%%)".format(c.peakStartHour, c.peakEndHour, (c.peakRate * 100).roundToInt())
            }
            parts += night
        }
        parts += "시계외 %d%%".format((c.outOfCityRate * 100).roundToInt())
        return parts.joinToString(" · ")
    }

    private fun nextUnitText(s: MeterUiState): String {
        if (!s.running) return if (s.fare != null) "운행 종료 · 지불 대기" else "운행 시작을 누르면 미터기가 돌아갑니다"
        val unit = "%,d".format(s.config.unitFare)
        return if (s.lowSpeed) {
            val sec = (s.metersToNextUnit / s.config.metersPerLowSpeedSecond).roundToInt()
            "다음 ${unit}원까지 ${sec}초 (저속)"
        } else {
            "다음 ${unit}원까지 ${s.metersToNextUnit.roundToInt()} m"
        }
    }
}
