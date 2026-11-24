package com.anall.taximeter.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import com.anall.taximeter.meter.IdleSign
import com.anall.taximeter.meter.MeterUiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

interface MeterActions {
    fun start()
    fun end()
    fun vacant()
    fun toggleOutOfCity()
    fun toggleIdleSign(sign: IdleSign)
    fun setSimulation(on: Boolean)
    fun setAutoNight(on: Boolean)
}

@Composable
fun MeterScreen(state: MeterUiState, actions: MeterActions) {
    var showSettings by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MeterColors.Background),
    ) {
        val landscape = maxWidth >= maxHeight
        val scale = if (landscape) {
            min(maxWidth / 960.dp, maxHeight / 540.dp)
        } else {
            min(maxWidth / 620.dp, maxHeight / 1000.dp)
        }
        CompositionLocalProvider(LocalUiScale provides scale) {
            Column(Modifier.fillMaxSize().padding(u(10))) {
                TopBar(state, onSettings = { showSettings = true })
                Spacer(Modifier.height(u(8)))
                if (landscape) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(u(10))) {
                        MeterPanel(state, actions, Modifier.weight(0.66f).fillMaxHeight())
                        TripPanel(state, Modifier.weight(0.34f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(u(10))) {
                        MeterPanel(state, actions, Modifier.fillMaxWidth().height(u(500)))
                        TripPanel(state, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
            if (showSettings) SettingsDialog(state, actions) { showSettings = false }
        }
    }
}

// ---------------------------------------------------------------- 상단 바

@Composable
private fun TopBar(state: MeterUiState, onSettings: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().height(u(38)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("//", color = MeterColors.Red, fontSize = us(22), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        Spacer(Modifier.width(u(6)))
        Text("TAXI METER", color = MeterColors.Text, fontSize = us(20), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        Spacer(Modifier.weight(1f))
        GpsBadge(state)
        Spacer(Modifier.width(u(16)))
        Text(
            SimpleDateFormat("HH:mm", Locale.KOREA).format(now),
            color = MeterColors.Text, fontSize = us(20), fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(u(12)))
        Box(
            Modifier
                .size(u(38))
                .clip(RoundedCornerShape(u(8)))
                .background(MeterColors.ChipIdle)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚙", color = MeterColors.Text, fontSize = us(20))
        }
    }
}

@Composable
private fun GpsBadge(state: MeterUiState) {
    val (color, label) = when {
        state.simulation -> MeterColors.Purple to "SIM 모의주행"
        state.gpsProblem != null -> MeterColors.Red to state.gpsProblem
        state.hasFix -> MeterColors.Green to "GPS ${state.satellitesUsed}/${state.satellitesVisible}"
        else -> MeterColors.Amber to "GPS 수신 중 ${state.satellitesUsed}/${state.satellitesVisible}"
    }
    val blink by rememberInfiniteTransition(label = "gps").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "gpsBlink",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(u(10))
                .alpha(if (state.hasFix || state.simulation) 1f else blink)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(u(6)))
        Text(label, color = color, fontSize = us(14), fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- 미터기 본체

@Composable
private fun MeterPanel(state: MeterUiState, actions: MeterActions, modifier: Modifier) {
    Column(
        modifier
            .background(MeterColors.Panel, RoundedCornerShape(u(14)))
            .border(u(1.5), MeterColors.PanelBorder, RoundedCornerShape(u(14)))
            .padding(u(12)),
        verticalArrangement = Arrangement.spacedBy(u(9)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("KOREA DIGITAL TAXIMETER", color = MeterColors.Text, fontSize = us(13), fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(surchargeLabel(state), color = MeterColors.Amber, fontSize = us(12), fontWeight = FontWeight.SemiBold)
        }
        SignRow(state, actions)
        FareBox(state, Modifier.fillMaxWidth().weight(1f, fill = true))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u(8))) {
            val km = state.distanceKm
            StatBox("주행 거리 km", if (km < 100) "%.2f".format(km) else "%.1f".format(km), 4, Modifier.weight(1f))
            StatBox("주행 시간", formatDuration(state.drivingSec), 4, Modifier.weight(1f))
            StatBox("저속 시간", formatDuration(state.lowSpeedSec), 4, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TripButton(state.running, onClick = { if (state.running) actions.end() else actions.start() })
            Spacer(Modifier.width(u(14)))
            Column(verticalArrangement = Arrangement.spacedBy(u(2))) {
                val c = state.config
                Text(
                    "기본 %,d원 (%.1f km) · %d m / %d초당 %d원".format(
                        c.baseFare, c.baseDistanceM / 1000, c.unitDistanceM.toInt(), c.unitTimeSec.toInt(), c.unitFare,
                    ),
                    color = MeterColors.TextDim, fontSize = us(12),
                )
                Text(
                    "심야 22~04시 최대 40%% · 시계외 20%% · 저속 %.1f km/h 이하".format(c.lowSpeedKmh),
                    color = MeterColors.TextDim, fontSize = us(12),
                )
            }
        }
    }
}

private fun surchargeLabel(s: MeterUiState): String {
    val parts = mutableListOf("${s.config.regionName} 요금")
    if (s.nightRate > 0) parts += "심야 ${(s.nightRate * 100).roundToInt()}%"
    if (s.outOfCity) parts += "시계외 ${(s.config.outOfCityRate * 100).roundToInt()}%"
    return parts.joinToString(" · ")
}

@Composable
private fun SignRow(state: MeterUiState, actions: MeterActions) {
    Row(Modifier.fillMaxWidth().height(u(38)), horizontalArrangement = Arrangement.spacedBy(u(8))) {
        val idle = !state.running
        SignChip("빈차", idle && state.idleSign == IdleSign.NONE, MeterColors.Red, Modifier.weight(1f)) { actions.vacant() }
        SignChip("주행", state.running, MeterColors.Green, Modifier.weight(1f)) { if (!state.running) actions.start() }
        SignChip("할증", state.surchargeActive, MeterColors.Amber, Modifier.weight(1f)) { actions.toggleOutOfCity() }
        SignChip("예약", idle && state.idleSign == IdleSign.RESERVED, MeterColors.Blue, Modifier.weight(1f)) {
            actions.toggleIdleSign(IdleSign.RESERVED)
        }
        SignChip("호출", idle && state.idleSign == IdleSign.CALL, MeterColors.Purple, Modifier.weight(1f)) {
            actions.toggleIdleSign(IdleSign.CALL)
        }
    }
}

@Composable
private fun SignChip(label: String, active: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(u(8))
    Box(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(if (active) color else MeterColors.ChipIdle)
            .border(u(1.5), if (active) Color.White.copy(alpha = 0.55f) else Color.Transparent, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color(0xFF101418) else MeterColors.TextDim,
            fontSize = us(16), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun FareBox(state: MeterUiState, modifier: Modifier) {
    val shape = RoundedCornerShape(u(12))
    Box(
        modifier
            .background(MeterColors.Inner, shape)
            .border(u(1.5), MeterColors.InnerBorder, shape)
            .padding(horizontal = u(12), vertical = u(8)),
    ) {
        Column(Modifier.align(Alignment.TopStart)) {
            Text(
                if (state.running) "요금 · 주행" else if (state.fare != null) "요금 · 지불" else "요금 · 빈차",
                color = MeterColors.Text, fontSize = us(13), fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(u(6)))
            PixelHorse(
                speedKmh = if (state.running) state.speedKmh else 0.0,
                modifier = Modifier
                    .size(u(108), u(72))
                    .background(Color(0xFF1A2228), RoundedCornerShape(u(6)))
                    .padding(u(6)),
            )
        }
        Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                SevenSegment(text = state.fare?.toString() ?: "", minDigits = 6, height = u(96))
                Spacer(Modifier.width(u(6)))
                Text("원", color = MeterColors.Text, fontSize = us(20), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(u(8)))
            val progress by animateFloatAsState(
                if (state.running) state.progressToNextUnit else 0f, tween(450), label = "progress",
            )
            Box(
                Modifier
                    .padding(end = u(28))
                    .width(u(300))
                    .height(u(7))
                    .clip(RoundedCornerShape(u(4)))
                    .background(MeterColors.LedOff),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(Brush.horizontalGradient(listOf(MeterColors.Cyan, MeterColors.LedOn))),
                )
            }
        }
        Text(
            nextUnitText(state),
            Modifier.align(Alignment.BottomStart),
            color = MeterColors.TextDim, fontSize = us(13),
        )
    }
}

private fun nextUnitText(s: MeterUiState): String {
    if (!s.running) return if (s.fare != null) "운행 종료 · 지불 대기" else "운행 시작을 누르면 미터기가 돌아갑니다"
    val unit = s.config.unitFare
    return if (s.lowSpeed) {
        val sec = (s.metersToNextUnit / s.config.metersPerLowSpeedSecond).roundToInt()
        "다음 ${unit}원까지 ${sec}초 (저속)"
    } else {
        "다음 ${unit}원까지 ${s.metersToNextUnit.roundToInt()} m"
    }
}

@Composable
private fun StatBox(label: String, value: String, digits: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(u(10))
    Column(
        modifier
            .background(MeterColors.Inner, shape)
            .border(u(1), MeterColors.PanelBorder, shape)
            .padding(horizontal = u(10), vertical = u(6)),
    ) {
        Text(label, color = MeterColors.TextDim, fontSize = us(12))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            SevenSegment(text = value, minDigits = digits, height = u(38))
        }
    }
}

@Composable
private fun TripButton(running: Boolean, onClick: () -> Unit) {
    val base = if (running) MeterColors.Red else Color(0xFF2E9E4F)
    val shape = RoundedCornerShape(u(10))
    val lamp by rememberInfiniteTransition(label = "lamp").animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "lampAlpha",
    )
    Row(
        Modifier
            .width(u(250))
            .height(u(56))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(base.copy(alpha = 0.95f), Color(base.red * 0.7f, base.green * 0.7f, base.blue * 0.7f))))
            .border(u(1.5), Color.White.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = u(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(u(14))
                .alpha(if (running) lamp else 1f)
                .background(if (running) MeterColors.Amber else Color(0xFFBFF5CC), CircleShape),
        )
        Text(
            if (running) "운행 종료" else "운행 시작",
            Modifier.weight(1f),
            color = Color.White, fontSize = us(22), fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- 오른쪽 패널

@Composable
private fun TripPanel(state: MeterUiState, modifier: Modifier) {
    Column(
        modifier
            .background(MeterColors.Panel, RoundedCornerShape(u(14)))
            .border(u(1.5), MeterColors.PanelBorder, RoundedCornerShape(u(14)))
            .padding(u(12)),
        verticalArrangement = Arrangement.spacedBy(u(8)),
    ) {
        Text("TRIP MONITOR", color = MeterColors.Text, fontSize = us(13), fontWeight = FontWeight.Bold)
        SpeedBox(state)
        ReceiptSlot(state)
    }
}

@Composable
private fun SpeedBox(state: MeterUiState) {
    val shape = RoundedCornerShape(u(10))
    Column(
        Modifier
            .fillMaxWidth()
            .background(MeterColors.Inner, shape)
            .border(u(1), MeterColors.PanelBorder, shape)
            .padding(horizontal = u(10), vertical = u(8)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("현재 속도", color = MeterColors.TextDim, fontSize = us(12))
            Spacer(Modifier.weight(1f))
            val (txt, col) = if (state.lowSpeed) "시간 요금" to MeterColors.Amber else "거리 요금" to MeterColors.Green
            Text(
                txt, color = col, fontSize = us(11), fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .border(u(1), col, RoundedCornerShape(u(4)))
                    .padding(horizontal = u(5), vertical = u(1)),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
            SevenSegment(text = state.speedKmh.roundToInt().toString(), minDigits = 3, height = u(54))
            Spacer(Modifier.width(u(6)))
            Text("km/h", color = MeterColors.Text, fontSize = us(14), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(u(4)))
        // 저속 기준선 대비 속도 막대 (0~120 km/h)
        val frac = (state.speedKmh / 120.0).toFloat().coerceIn(0f, 1f)
        val lowFrac = (state.config.lowSpeedKmh / 120.0).toFloat()
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(u(6))
                .clip(RoundedCornerShape(u(3)))
                .background(MeterColors.LedOff),
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(frac)
                    .background(if (state.lowSpeed) MeterColors.Amber else MeterColors.Green),
            )
            Box(Modifier.padding(start = maxWidth * lowFrac).width(u(2)).fillMaxHeight().background(Color.White))
        }
        state.accuracyM?.takeIf { !state.simulation }?.let {
            Spacer(Modifier.height(u(4)))
            Text("GPS 정확도 ±${it.roundToInt()} m", color = MeterColors.TextDim, fontSize = us(11))
        }
    }
}

@Composable
private fun ColumnScope.ReceiptSlot(state: MeterUiState) {
    val shape = RoundedCornerShape(u(10))
    Box(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(MeterColors.Inner, shape)
            .border(u(1), MeterColors.PanelBorder, shape)
            .padding(u(8)),
    ) {
        if (state.receipt == null) {
            Text(
                if (state.running) "운행 중…\n운행을 마치면 영수증이 나옵니다" else "운행을 마치면 영수증이 나옵니다",
                Modifier.align(Alignment.Center),
                color = MeterColors.TextDim, fontSize = us(13), textAlign = TextAlign.Center,
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = state.receipt != null,
            enter = expandVertically(tween(1_400, easing = LinearEasing), expandFrom = Alignment.Top),
            exit = fadeOut(),
        ) {
            state.receipt?.let { ReceiptView(it, Modifier.fillMaxSize()) }
        }
    }
}

// ---------------------------------------------------------------- 설정

@Composable
private fun SettingsDialog(state: MeterUiState, actions: MeterActions, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MeterColors.Panel,
        titleContentColor = MeterColors.Text,
        textContentColor = MeterColors.Text,
        title = { Text("설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingRow(
                    "모의 주행",
                    "GPS 없이 가짜 주행 데이터로 미터기를 돌려봅니다.",
                    state.simulation, actions::setSimulation,
                )
                SettingRow(
                    "심야 할증 자동 적용",
                    "22~23시·02~04시 20%, 23~02시 40%",
                    state.autoNight, actions::setAutoNight,
                )
                Text(
                    "할증 버튼은 시계외 할증(20%)을 켜고 끕니다.\n예약·호출 표시등은 빈차일 때만 켤 수 있습니다.",
                    color = MeterColors.TextDim, fontSize = 13.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기", color = MeterColors.Cyan) } },
    )
}

@Composable
private fun SettingRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MeterColors.Text, fontWeight = FontWeight.SemiBold)
            Text(desc, color = MeterColors.TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MeterColors.Green),
        )
    }
}

