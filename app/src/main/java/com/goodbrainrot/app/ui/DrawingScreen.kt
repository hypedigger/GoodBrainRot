package com.goodbrainrot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.ui.theme.DeepRainbow
import kotlin.random.Random

private class DrawStroke(val color: Color, val width: Float) {
    val points = mutableStateListOf<Offset>()
}

/**
 * Gribouillage : dessin au doigt, chaque trait prend la couleur suivante de
 * l'arc-en-ciel et déclenche une petite note. La poubelle efface tout.
 */
@Composable
fun DrawingScreen(onBack: () -> Unit) {
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    val colorIndex = remember { mutableIntStateOf(0) }
    // Notes pentatoniques douces jouées au début de chaque trait
    val notes = listOf(523.25, 587.33, 659.25, 783.99, 880.0)

    fun startStroke(offset: Offset): DrawStroke {
        val stroke = DrawStroke(
            color = DeepRainbow[colorIndex.intValue % DeepRainbow.size],
            width = Random.nextInt(14, 30).toFloat()
        )
        stroke.points.add(offset)
        strokes.add(stroke)
        colorIndex.intValue++
        SoundEngine.playXylo(notes.random(), 400, 0.35f)
        return stroke
    }

    val babyMode by Settings.babyMode

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> startStroke(offset) },
                        onDrag = { change, _ ->
                            strokes.lastOrNull()?.points?.add(change.position)
                        }
                    )
                }
                .pointerInput(Unit) {
                    // Un simple tap pose un point
                    detectTapGestures(onTap = { offset -> startStroke(offset) })
                }
        ) {
            strokes.forEach { stroke ->
                val pts = stroke.points
                if (pts.size == 1) {
                    drawCircle(stroke.color, stroke.width / 2, pts.first())
                } else if (pts.size > 1) {
                    val path = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        for (i in 1 until pts.size) {
                            lineTo(pts[i].x, pts[i].y)
                        }
                    }
                    drawPath(
                        path,
                        stroke.color,
                        style = Stroke(
                            width = stroke.width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BackButton(onBack = onBack)
            Row {
                if (!babyMode) {
                    IconButton(
                        onClick = {
                            if (strokes.removeLastOrNull() != null) SoundEngine.playUndo()
                        },
                        enabled = strokes.isNotEmpty()
                    ) {
                        Text(text = "↩", fontSize = 24.sp)
                    }
                }
                IconButton(onClick = {
                    if (strokes.isNotEmpty()) { SoundEngine.playErase(); strokes.clear() }
                }) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
