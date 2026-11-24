package com.anall.taximeter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

object MeterColors {
    val Background = Color(0xFF090C0F)
    val Panel = Color(0xFF141A1F)
    val PanelBorder = Color(0xFF2B343C)
    val Inner = Color(0xFF0D1216)
    val InnerBorder = Color(0xFF3A4650)
    val LedOn = Color(0xFFEAF8FF)
    val LedOff = Color(0xFF1C2830)
    val Text = Color(0xFFE6ECF0)
    val TextDim = Color(0xFF8593A0)
    val ChipIdle = Color(0xFF262E35)
    val Amber = Color(0xFFFFB547)
    val Green = Color(0xFF5BE07A)
    val Red = Color(0xFFE5483D)
    val Blue = Color(0xFF58A8FF)
    val Purple = Color(0xFFB98BFF)
    val Cyan = Color(0xFF6FD3FF)
    val Paper = Color(0xFFF3F0E8)
    val Ink = Color(0xFF26282B)
}

/**
 * 헤드유닛마다 해상도/밀도가 제각각이라 960x540 기준으로 디자인하고 화면 크기에 맞춰 통째로 확대·축소한다.
 */
val LocalUiScale = staticCompositionLocalOf { 1f }

@Composable
@ReadOnlyComposable
fun u(v: Number): Dp = (v.toFloat() * LocalUiScale.current).dp

/** 시스템 글꼴 크기 설정에 휘둘리지 않도록 dp 기준으로 sp 를 만든다. */
@Composable
@ReadOnlyComposable
fun us(v: Number): TextUnit = with(LocalDensity.current) { (v.toFloat() * LocalUiScale.current).dp.toSp() }
