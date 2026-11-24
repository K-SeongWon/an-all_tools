package com.anall.taximeter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.math.min

// 오른쪽을 보고 달리는 도트 말. 몸통은 공통이고 다리만 프레임마다 바뀐다.
private val BODY = listOf(
    "....................XX......",
    "...................XXXX.....",
    "..................XXXXXXX...",
    ".................XXXXXXXXXX.",
    "................XXXXX..XXXXX",
    "...............XXXXX....XX..",
    "..XXX.........XXXXX.........",
    ".XXXXXXXXXXXXXXXXXX.........",
    "XX.XXXXXXXXXXXXXXXXX........",
    "X..XXXXXXXXXXXXXXXXX........",
    "X..XXXXXXXXXXXXXXXXX........",
    "....XXXXXXXXXXXXXXX.........",
)

private val LEGS_STAND = listOf(
    "....XX..XX....XX..XX........",
    "....XX..XX....XX..XX........",
    "....XX..XX....XX..XX........",
    "....XX..XX....XX..XX........",
    "....XX..XX....XX..XX........",
    "...XXX.XXX...XXX.XXX........",
)

private val LEGS_STRETCH = listOf(
    "...XXX.........XXX..........",
    "..XX............XXX.........",
    ".XX...............XX........",
    "XX.................XX.......",
    "X...................XX......",
    "............................",
)

private val LEGS_MID = listOf(
    "...XXX.X.....XX.X...........",
    "...XX..XX....XX..X..........",
    "..XX....X...XX...XX.........",
    "..X.....X...X.....X.........",
    "..X.....XX..X.....XX........",
    "............................",
)

private val LEGS_GATHER = listOf(
    "....XXX.......XXX...........",
    ".....XX.......XX............",
    "......XX.....XX.............",
    ".......XX...XX..............",
    "........X...X...............",
    "............................",
)

private val STAND = BODY + LEGS_STAND
private val GALLOP = listOf(BODY + LEGS_STRETCH, BODY + LEGS_MID, BODY + LEGS_GATHER, BODY + LEGS_MID)

/** 속도가 빠를수록 빨리 달리는 도트 말. 정차 중에는 서 있다. */
@Composable
fun PixelHorse(speedKmh: Double, modifier: Modifier = Modifier, color: Color = MeterColors.LedOn) {
    val speed by rememberUpdatedState(speedKmh)
    var frame by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            val s = speed
            if (s < 2.0) {
                frame = -1
                delay(200)
            } else {
                frame = (frame + 1).mod(GALLOP.size)
                delay((2400.0 / (s + 10.0)).toLong().coerceIn(55L, 320L))
            }
        }
    }
    val rows = if (frame < 0) STAND else GALLOP[frame]
    Canvas(modifier) {
        val cols = rows.maxOf { it.length }
        val cell = min(size.width / cols, size.height / rows.size)
        val ox = (size.width - cell * cols) / 2f
        val oy = (size.height - cell * rows.size) / 2f
        val px = Size(cell * 0.9f, cell * 0.9f)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c == 'X') drawRect(color, Offset(ox + x * cell, oy + y * cell), px)
            }
        }
    }
}
