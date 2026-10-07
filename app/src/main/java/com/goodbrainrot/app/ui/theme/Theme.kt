package com.goodbrainrot.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Palette arc-en-ciel pastel utilisée partout (piano, cartes, chiffres, lettres). */
val PastelRainbow = listOf(
    Color(0xFFFFB3BA), // rouge
    Color(0xFFFFD8B1), // orange
    Color(0xFFFFFFBA), // jaune
    Color(0xFFBAFFC9), // vert
    Color(0xFFB1E8E0), // turquoise
    Color(0xFFB3D9FF), // bleu
    Color(0xFFD7BAFF), // violet
    Color(0xFFFFBAE1)  // rose
)

/** Couleurs vives assorties pour les textes posés sur les pastels. */
val DeepRainbow = listOf(
    Color(0xFFD9534F),
    Color(0xFFE08E2B),
    Color(0xFFC9A800),
    Color(0xFF3FA45B),
    Color(0xFF2D9D8F),
    Color(0xFF3A78C9),
    Color(0xFF8A5BC9),
    Color(0xFFD9569B)
)

val InkColor = Color(0xFF5D4E6D)
val BackgroundColor = Color(0xFFF8F4FB)

private val PastelColorScheme = lightColorScheme(
    primary = Color(0xFFE8A0C0),
    onPrimary = Color.White,
    secondary = Color(0xFFB3D9FF),
    background = BackgroundColor,
    surface = Color.White,
    onBackground = InkColor,
    onSurface = InkColor
)

@Composable
fun GoodBrainRotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PastelColorScheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
