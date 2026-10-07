package com.goodbrainrot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.Haptics
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.audio.Instruments
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.ui.theme.PastelRainbow

/**
 * Xylophone : 8 lames colorées (Do5→Do6). On tape une lame ou on **glisse** le
 * doigt d'une lame à l'autre (glissando), multi-touch. Timbre métallique
 * synthétisé, ou échantillon réaliste si disponible.
 */
@Composable
fun XylophoneScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val frequencies = remember { SoundEngine.whiteKeyFrequencies(15).drop(7) } // Do5→Do6
    val count = frequencies.size
    val pressed = remember { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(Unit) { Instruments.xylophone.preload(context) }
    fun strike(freq: Double) {
        if (!(Settings.realisticInstruments.value && Instruments.xylophone.play(freq))) {
            SoundEngine.playXylo(freq)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BackButton(onBack = onBack)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .pointerInput(count) {
                    awaitPointerEventScope {
                        val pointerToBar = HashMap<PointerId, Int>()
                        fun barAt(y: Float): Int =
                            (y / size.height * count).toInt().coerceIn(0, count - 1)
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                if (change.pressed) {
                                    val b = barAt(change.position.y)
                                    if (pointerToBar[change.id] != b) {
                                        pointerToBar[change.id] = b
                                        strike(frequencies[b])
                                        Haptics.tap(context)
                                    }
                                } else {
                                    pointerToBar.remove(change.id)
                                }
                            }
                            pressed.value = pointerToBar.values.toSet()
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                frequencies.forEachIndexed { index, _ ->
                    val widthFraction = 1f - index * 0.055f
                    val isOn = index in pressed.value
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(widthFraction)
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = if (isOn) 0.97f else 1f
                                scaleY = if (isOn) 0.9f else 1f
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .background(PastelRainbow[index % PastelRainbow.size])
                    )
                }
            }
        }
    }
}
