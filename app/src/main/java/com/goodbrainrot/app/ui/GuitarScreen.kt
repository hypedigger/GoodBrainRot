package com.goodbrainrot.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.goodbrainrot.app.Haptics
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.audio.Instruments
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.data.GuitarTypes
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

private val FRET_MARKERS = setOf(3, 5, 7, 9, 15, 17, 19)
private const val DOUBLE_MARKER = 12

/**
 * Guitare / basse en **manche vertical** : la tête est en haut, les frettes
 * descendent (lignes), les cordes vont de gauche à droite (colonnes). Repères
 * ronds comme sur un vrai manche. On joue plusieurs cases (accords) et on glisse
 * le doigt. Son réaliste (échantillons) ou synthétisé selon le réglage.
 */
@Composable
fun GuitarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val typeIndex by Settings.guitarTypeIndex
    val type = remember(typeIndex) { GuitarTypes.byIndex(typeIndex) }
    val strings = Settings.guitarStrings.intValue.coerceIn(4, 6)
    val frets = Settings.guitarFrets.intValue.coerceIn(6, 20)
    val openFreqs = remember(type.id, strings) { type.stringFrequencies(strings) }
    val pressed = remember { mutableStateOf(emptySet<Int>()) }
    val realistic by Settings.realisticInstruments

    val sampleSet = remember(type.sampleSet) { Instruments.guitarSet(type.sampleSet) }
    LaunchedEffect(type.sampleSet) { sampleSet.preload(context) }

    fun freqAt(fretRow: Int, stringCol: Int): Double =
        openFreqs[stringCol] * 2.0.pow(fretRow / 12.0)

    fun pluck(freq: Double) {
        if (!(realistic && sampleSet.play(freq))) SoundEngine.playGuitar(freq, type.timbre)
    }

    fun strum(fretRow: Int, goingDown: Boolean) {
        val freqs = (0 until strings).map { col -> freqAt(fretRow, col) }
        SoundEngine.playStrum(freqs, type.timbre, goingDown)
    }

    val neck = Color(type.neckColor)

    // Smash : un mouvement brusque du téléphone casse le manche
    var smashToken by remember { mutableStateOf(0) }
    var smashing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val isLinear = sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION
        val threshold = if (isLinear) 24f else 30f
        var lastTrigger = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val mag = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                val now = System.currentTimeMillis()
                if (mag > threshold && now - lastTrigger > 2500 && !smashing) {
                    lastTrigger = now
                    smashToken++
                    SoundEngine.playSmash()
                    Haptics.success(context)
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    LaunchedEffect(smashToken) {
        if (smashToken > 0) {
            smashing = true
            kotlinx.coroutines.delay(1050)
            smashing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1A140F))) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack = onBack)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GuitarTypes.ALL.forEachIndexed { i, gt ->
                    val sel = i == typeIndex
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) Color(0xFFE8A0C0) else Color(0x33FFFFFF))
                            .clickableNoRipple { Settings.setGuitarTypeIndex(i) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = gt.emoji + " " + stringResource(gt.labelRes),
                            color = if (sel) Color.White else Color(0xFFF0E6DA),
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Manche : largeur limitée et centrée pour ressembler à un vrai manche
        Box(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.72f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(neck)
                    .pointerInput(strings, frets, type.id) {
                        val pointerToCell = HashMap<PointerId, Int>()
                        val pointerPrevY = HashMap<PointerId, Float>()
                        val strumThresholdPx = 52f
                        fun cellAt(x: Float, y: Float): Pair<Int, Int> {
                            val col = (x / size.width * strings).toInt().coerceIn(0, strings - 1)
                            val row = (y / size.height * frets).toInt().coerceIn(0, frets - 1)
                            return row to col
                        }
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    if (change.pressed) {
                                        val (row, col) = cellAt(change.position.x, change.position.y)
                                        val id = row * strings + col
                                        val prevY = pointerPrevY[change.id]
                                        val dy = if (prevY != null) change.position.y - prevY else 0f
                                        pointerPrevY[change.id] = change.position.y
                                        if (prevY != null && abs(dy) > strumThresholdPx) {
                                            // Strum : glissement vertical rapide → arpège
                                            strum(row, dy > 0)
                                            Haptics.tap(context)
                                            pointerToCell[change.id] = id
                                        } else if (pointerToCell[change.id] != id) {
                                            pointerToCell[change.id] = id
                                            pluck(freqAt(row, col))
                                            Haptics.tap(context)
                                        }
                                    } else {
                                        pointerToCell.remove(change.id)
                                        pointerPrevY.remove(change.id)
                                    }
                                }
                                pressed.value = pointerToCell.values.toSet()
                            }
                        }
                    }
            ) {
                if (smashing) {
                    NeckShards(neck = neck, token = smashToken)
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        for (fretRow in 0 until frets) {
                            Row(
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            ) {
                                for (stringCol in 0 until strings) {
                                    val id = fretRow * strings + stringCol
                                    val on = id in pressed.value
                                    FretCell(
                                        stringCol = stringCol,
                                        strings = strings,
                                        on = on,
                                        showMarker = stringCol == strings / 2 &&
                                            (fretRow in FRET_MARKERS || fretRow == DOUBLE_MARKER),
                                        nutRow = fretRow == 0,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FretCell(
    stringCol: Int,
    strings: Int,
    on: Boolean,
    showMarker: Boolean,
    nutRow: Boolean,
    modifier: Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Corde : trait vertical clair au centre de la colonne, épaisseur croissante pour les graves
        val stringThickness = (1 + (strings - 1 - stringCol)).dp
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(stringThickness)
                .background(Color(0xCCEDE6D8))
        )
        // Frette : trait horizontal en haut de la case (barrette métallique)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(if (nutRow) 4.dp else 2.dp)
                .background(if (nutRow) Color(0xFFF2EFE6) else Color(0x66C9BFA8))
        )
        if (showMarker) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0x66FFFFFF))
            )
        }
        if (on) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(GuitarFingerColors[stringCol % GuitarFingerColors.size])
            )
        }
    }
}

private val GuitarFingerColors = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFD166), Color(0xFF7BE0A0),
    Color(0xFF5BC8FF), Color(0xFFB18CFF), Color(0xFFFF8FD1)
)

/** Le manche éclate en morceaux qui partent vers le bas/les côtés, puis disparaît. */
@Composable
private fun NeckShards(neck: Color, token: Int) {
    val cols = 3
    val rows = 7
    val progress = remember(token) { Animatable(0f) }
    LaunchedEffect(token) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1000, easing = FastOutLinearInEasing))
    }
    val darker = Color(neck.red * 0.82f, neck.green * 0.82f, neck.blue * 0.82f, 1f)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cw = maxWidth / cols
        val ch = maxHeight / rows
        val t = progress.value
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val centerFrac = (c + 0.5f) / cols - 0.5f       // -0.5..0.5
                val dx = centerFrac * 760f * t                  // écartement latéral
                val dy = 560f * t * t + 260f * t                // chute (gravité)
                val rot = (((r + c) % 2) * 2 - 1) * 460f * t     // rotation alternée
                Box(
                    modifier = Modifier
                        .offset(x = cw * c + dx.dp, y = ch * r + dy.dp)
                        .size(width = cw - 3.dp, height = ch - 3.dp)
                        .graphicsLayer {
                            rotationZ = rot
                            alpha = (1f - t).coerceIn(0f, 1f)
                        }
                        .clip(RoundedCornerShape(3.dp))
                        .background(if ((r + c) % 2 == 0) neck else darker)
                )
            }
        }
    }
}
