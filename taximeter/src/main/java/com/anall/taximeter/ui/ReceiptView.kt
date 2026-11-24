package com.anall.taximeter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.anall.taximeter.meter.Receipt
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private val won = NumberFormat.getIntegerInstance(Locale.KOREA)
private fun Int.fmt(): String = won.format(this)

@Composable
fun ReceiptView(r: Receipt, modifier: Modifier = Modifier) {
    val mono = FontFamily.Monospace
    val dateFmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.KOREA)
    Column(
        modifier
            .background(MeterColors.Paper, RoundedCornerShape(u(3)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = u(12), vertical = u(10)),
        verticalArrangement = Arrangement.spacedBy(u(3)),
    ) {
        Text(
            "영 수 증", Modifier.fillMaxWidth(), color = MeterColors.Ink, fontSize = us(17),
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(u(4)))
        ReceiptLine("거리", "%.2f km".format(r.distanceKm), mono)
        ReceiptLine("시간", formatDuration(r.drivingSec), mono)
        Dashes()
        ReceiptLine("기본요금 %.1f km".format(r.baseDistanceKm), r.baseFare.fmt(), mono)
        ReceiptLine("거리 ${r.unitDistanceM} m × ${r.distanceUnits}", r.distanceFare.fmt(), mono)
        ReceiptLine("시간 ${r.unitTimeSec}초 × ${r.timeUnits}", r.timeFare.fmt(), mono)
        ReceiptLine("할증 (심야·시계외)", r.surcharge.fmt(), mono)
        Dashes()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("합계", Modifier.weight(1f), color = MeterColors.Ink, fontSize = us(15), fontWeight = FontWeight.Bold)
            Text(
                "${r.total.fmt()}원", color = Color(0xFFD23A2E), fontSize = us(19),
                fontWeight = FontWeight.Bold, fontFamily = mono,
            )
        }
        Text(
            "${dateFmt.format(Date(r.startedAtMillis))} ~ ${timeFmt.format(Date(r.endedAtMillis))}",
            color = MeterColors.Ink.copy(alpha = 0.6f), fontSize = us(10), fontFamily = mono,
        )
        Spacer(Modifier.height(u(2)))
        Barcode(seed = r.endedAtMillis, modifier = Modifier.fillMaxWidth().height(u(30)))
        Text(
            "재미로 보는 영수증 · 실제 요금이 아닙니다", Modifier.fillMaxWidth(),
            color = MeterColors.Ink.copy(alpha = 0.5f), fontSize = us(9), textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReceiptLine(label: String, value: String, mono: FontFamily) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MeterColors.Ink, fontSize = us(12))
        Text(value, color = MeterColors.Ink, fontSize = us(12), fontFamily = mono)
    }
}

@Composable
private fun Dashes() {
    HorizontalDivider(Modifier.padding(vertical = u(3)), thickness = u(1), color = MeterColors.Ink.copy(alpha = 0.35f))
}

@Composable
private fun Barcode(seed: Long, modifier: Modifier) {
    Canvas(modifier) {
        val rnd = Random(seed)
        var x = 0f
        val unit = size.width / 160f
        var bar = true
        while (x < size.width) {
            val w = unit * (1 + rnd.nextInt(if (bar) 3 else 2))
            if (bar) drawRect(MeterColors.Ink, Offset(x, 0f), Size(w, size.height))
            x += w
            bar = !bar
        }
    }
}

fun formatDuration(totalSec: Long): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m < 100) "%02d:%02d".format(m, s) else "%d:%02d".format(m / 60, m % 60)
}
