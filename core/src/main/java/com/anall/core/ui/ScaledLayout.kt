package com.anall.core.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * 헤드유닛마다 해상도와 밀도가 제각각이라, 화면을 기준 크기로 디자인해 두고 통째로 확대·축소한다.
 *
 * - 가로 화면: 높이를 [landscapeHeightDp] 로 맞추고, 폭은 화면 비율만큼 늘어난다.
 * - 세로 화면: 폭을 [portraitWidthDp] 로 맞추고, 높이가 늘어난다.
 *
 * 방향이 바뀌면 [build] 로 자식 화면을 새로 만든다. 터치 좌표는 View 변환으로 자동 보정된다.
 */
class ScaledLayout(
    context: Context,
    private val landscapeHeightDp: Float = 540f,
    private val portraitWidthDp: Float = 600f,
    private val build: (landscape: Boolean) -> View,
) : ViewGroup(context) {

    private var landscape: Boolean? = null
    private var child: View? = null
    private var scale = 1f
    private var childW = 0
    private var childH = 0

    init {
        clipChildren = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        if (w == 0 || h == 0) return

        val land = w >= h
        if (land != landscape || child == null) {
            child?.let { removeViewInLayout(it) }
            val c = build(land)
            addViewInLayout(c, -1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT), true)
            child = c
            landscape = land
        }
        val density = resources.displayMetrics.density
        scale = if (land) h / (landscapeHeightDp * density) else w / (portraitWidthDp * density)
        childW = (w / scale).toInt()
        childH = (h / scale).toInt()
        child!!.measure(MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val c = child ?: return
        c.layout(0, 0, childW, childH)
        c.pivotX = 0f
        c.pivotY = 0f
        c.scaleX = scale
        c.scaleY = scale
    }

    /** 설정 변경 등으로 화면을 새로 그려야 할 때. */
    fun rebuild() {
        landscape = null
        requestLayout()
    }
}
