package com.anall.addon.taximeter

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.anall.core.Addon
import com.anall.core.AddonHost
import com.anall.core.AddonInfo
import com.anall.core.AddonSession
import com.anall.core.SettingSpec
import com.anall.core.ui.Palette
import com.anall.core.ui.SevenSegmentPainter

/** 택시 미터기 애드온 진입점. addon.properties 의 entryClass 가 이 클래스를 가리킨다. */
class TaxiMeterAddon : Addon() {

    override val info = AddonInfo(
        id = "taximeter",
        name = "택시 미터기",
        versionCode = BuildConfig.ADDON_VERSION_CODE,
        versionName = BuildConfig.ADDON_VERSION_NAME,
        description = "GPS로 속도와 거리를 재서 실제 택시처럼 요금이 오르는 디지털 미터기",
        accentColor = 0xFFFFC531.toInt(),
    )

    override fun settings(): List<SettingSpec> = TaxiSettings.ALL

    override fun createIcon(context: Context): Drawable = TaxiIcon()

    override fun createSession(host: AddonHost): AddonSession = TaxiMeterSession(host)
}

/** 노란 지붕 표시등 + LED 숫자 미터기 아이콘. */
private class TaxiIcon : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seg = SevenSegmentPainter()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val x0 = b.left.toFloat()
        val y0 = b.top.toFloat()

        paint.color = 0xFFFFC531.toInt()
        rect.set(x0 + w * 0.32f, y0 + h * 0.12f, x0 + w * 0.68f, y0 + h * 0.28f)
        canvas.drawRoundRect(rect, h * 0.04f, h * 0.04f, paint)

        paint.color = 0xFF1E272E.toInt()
        rect.set(x0 + w * 0.1f, y0 + h * 0.32f, x0 + w * 0.9f, y0 + h * 0.84f)
        canvas.drawRoundRect(rect, h * 0.06f, h * 0.06f, paint)

        val segH = h * 0.32f
        val text = "48"
        val segW = seg.measureWidth(text, 2, segH)
        seg.draw(canvas, text, 2, x0 + (w - segW) / 2f, y0 + h * 0.42f, segH)

        paint.color = Palette.RED
        canvas.drawCircle(x0 + w * 0.8f, y0 + h * 0.76f, h * 0.035f, paint)
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
