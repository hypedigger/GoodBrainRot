package com.goodbrainrot.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.goodbrainrot.app.Haptics
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.audio.Instruments
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.ui.theme.PastelRainbow
import kotlin.math.pow

/**
 * Piano en deux modes :
 * - Fantasy : grille colorée arc-en-ciel, multi-touch + glissando
 * - Classique : touches blanches et noires comme un vrai piano
 *
 * Le bouton 🎹 switche entre les deux modes.
 */
@Composable
fun PianoScreen(onBack: () -> Unit, onOpenOptions: () -> Unit) {
    val babyMode by Settings.babyMode
    val context = LocalContext.current
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val rows = (if (isLandscape) Settings.pianoRowsLandscape else Settings.pianoRowsPortrait)
        .intValue.coerceIn(1, 5)
    val keysPerRow =
        (if (isLandscape) Settings.pianoKeysPerRowLandscape else Settings.pianoKeysPerRowPortrait)
            .intValue.coerceIn(1, 15)
    val total = rows * keysPerRow

    LaunchedEffect(Unit) { Instruments.piano.preload(context) }

    var classicMode by remember { mutableStateOf(false) }
    val pressedKeys = remember { mutableStateOf(emptySet<Int>()) }

    fun midiToFreq(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun strike(freq: Double) {
        if (!(Settings.realisticInstruments.value && Instruments.piano.play(freq))) {
            SoundEngine.playTone(freq)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onBack = onBack)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Bouton switch mode classique / fantasy
                IconButton(onClick = { classicMode = !classicMode }) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(
                            if (classicMode) R.drawable.ic_rainbow else R.drawable.ic_piano
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (!babyMode) {
                    IconButton(onClick = onOpenOptions) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_gear),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        if (classicMode) {
            // Mode piano classique : touches blanches + noires
            val octaves = if (isLandscape) 3 else 2
            val startMidi = 48  // Do3

            ClassicPianoLayout(
                octaves = octaves,
                startMidi = startMidi,
                pressedMidis = pressedKeys.value,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .pointerInput(octaves, startMidi) {
                        val whiteOffsets = listOf(0, 2, 4, 5, 7, 9, 11)
                        val blackOffsets = listOf(1, 3, 6, 8, 10)
                        val blackPositions = listOf(0.67f, 1.67f, 3.67f, 4.67f, 5.67f)
                        val totalWhite = octaves * 7

                        fun keyMidiAt(x: Float, y: Float): Int {
                            val wkw = size.width.toFloat() / totalWhite
                            val bkh = size.height * 0.62f
                            val bkw = wkw * 0.64f
                            // Test touches noires d'abord (elles sont au-dessus visuellement)
                            if (y < bkh) {
                                val octIdx = (x / (wkw * 7)).toInt().coerceIn(0, octaves - 1)
                                val posInOct = x / wkw - octIdx * 7
                                for ((idx, pos) in blackPositions.withIndex()) {
                                    val left = pos - bkw / wkw / 2
                                    val right = pos + bkw / wkw / 2
                                    if (posInOct in left..right) {
                                        return startMidi + octIdx * 12 + blackOffsets[idx]
                                    }
                                }
                            }
                            // Touche blanche
                            val whiteIdx = (x / wkw).toInt().coerceIn(0, totalWhite - 1)
                            val octIdx = whiteIdx / 7
                            val posInOct = whiteIdx % 7
                            return startMidi + octIdx * 12 + whiteOffsets[posInOct]
                        }

                        val pointerToMidi = HashMap<PointerId, Int>()
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    if (change.pressed) {
                                        val midi = keyMidiAt(change.position.x, change.position.y)
                                        if (pointerToMidi[change.id] != midi) {
                                            pointerToMidi[change.id] = midi
                                            strike(midiToFreq(midi))
                                            Haptics.tap(context)
                                        }
                                    } else {
                                        pointerToMidi.remove(change.id)
                                    }
                                }
                                pressedKeys.value = pointerToMidi.values.toSet()
                            }
                        }
                    }
            )
        } else {
            // Mode fantasy : grille colorée avec multi-touch + glissando
            val frequencies = remember(total) { SoundEngine.whiteKeyFrequencies(total) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .pointerInput(rows, keysPerRow) {
                        awaitPointerEventScope {
                            val pointerToKey = HashMap<PointerId, Int>()

                            fun keyAt(x: Float, y: Float): Int {
                                val col = (x / size.width * keysPerRow).toInt()
                                    .coerceIn(0, keysPerRow - 1)
                                val rowIdx = (y / size.height * rows).toInt()
                                    .coerceIn(0, rows - 1)
                                return (rowIdx * keysPerRow + col).coerceIn(0, total - 1)
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    if (change.pressed) {
                                        val key = keyAt(change.position.x, change.position.y)
                                        if (pointerToKey[change.id] != key) {
                                            pointerToKey[change.id] = key
                                            strike(frequencies[key])
                                            Haptics.tap(context)
                                        }
                                    } else {
                                        pointerToKey.remove(change.id)
                                    }
                                }
                                pressedKeys.value = pointerToKey.values.toSet()
                            }
                        }
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(rows) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(keysPerRow) { col ->
                                val index = row * keysPerRow + col
                                PianoKey(
                                    color = PastelRainbow[index % PastelRainbow.size],
                                    pressed = index in pressedKeys.value,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Rendu du piano classique avec Canvas pour les touches blanches et noires. */
@Composable
private fun ClassicPianoLayout(
    octaves: Int,
    startMidi: Int,
    pressedMidis: Set<Int>,
    modifier: Modifier = Modifier
) {
    val whiteOffsets = listOf(0, 2, 4, 5, 7, 9, 11)
    val blackOffsets = listOf(1, 3, 6, 8, 10)
    val blackPositions = listOf(0.67f, 1.67f, 3.67f, 4.67f, 5.67f)
    val totalWhite = octaves * 7

    Canvas(modifier = modifier.background(Color(0xFF222222))) {
        val wkw = size.width / totalWhite
        val bkh = size.height * 0.62f
        val bkw = wkw * 0.60f

        // Touches blanches
        for (w in 0 until totalWhite) {
            val octIdx = w / 7
            val posInOct = w % 7
            val midi = startMidi + octIdx * 12 + whiteOffsets[posInOct]
            val pressed = midi in pressedMidis
            val x = w * wkw
            drawRect(
                color = if (pressed) Color(0xFFBBDDFF) else Color.White,
                topLeft = Offset(x + 1.5f, 1f),
                size = Size(wkw - 3f, size.height - 2f)
            )
            // Bord inférieur arrondi : juste un dégradé de bas
            drawRect(
                color = Color(0x22000000),
                topLeft = Offset(x + 1.5f, size.height - 12f),
                size = Size(wkw - 3f, 10f)
            )
            // Séparateur
            drawRect(
                color = Color(0xFF888888),
                topLeft = Offset(x, 0f),
                size = Size(1.5f, size.height)
            )
            // Label Do
            if (whiteOffsets[posInOct] == 0) {
                drawCircle(
                    color = Color(0x44000000),
                    radius = 10f,
                    center = Offset(x + wkw / 2, size.height - 18f)
                )
            }
        }

        // Touches noires (par-dessus)
        for (oct in 0 until octaves) {
            for ((idx, pos) in blackPositions.withIndex()) {
                val midi = startMidi + oct * 12 + blackOffsets[idx]
                val pressed = midi in pressedMidis
                val x = (oct * 7 + pos) * wkw - bkw / 2
                drawRoundRect(
                    color = if (pressed) Color(0xFF1A3A6A) else Color(0xFF1A1A1A),
                    topLeft = Offset(x, 0f),
                    size = Size(bkw, bkh),
                    cornerRadius = CornerRadius(5f, 5f)
                )
                // Reflet haut
                drawRoundRect(
                    color = Color(0x33FFFFFF),
                    topLeft = Offset(x + 3f, 3f),
                    size = Size(bkw - 6f, bkh * 0.25f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
        }
    }
}

/** Touche purement visuelle : l'interaction est gérée au niveau du clavier. */
@Composable
private fun PianoKey(
    color: Color,
    pressed: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = if (pressed) 0.94f else 1f
                scaleY = if (pressed) 0.94f else 1f
            }
            .clip(RoundedCornerShape(12.dp))
            .background(color)
    )
}
