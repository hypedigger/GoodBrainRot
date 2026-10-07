package com.goodbrainrot.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.data.HighScores
import com.goodbrainrot.app.ui.theme.InkColor
import androidx.compose.ui.res.stringResource

// ---------------------------------------------------------------------------
// Thèmes : néon (étoiles, look borne d'arcade) et pastel zen (bulles)
// ---------------------------------------------------------------------------

val ArcadeBg = Brush.verticalGradient(
    listOf(Color(0xFF0B0C2A), Color(0xFF241B4A), Color(0xFF0B0C2A))
)
val NeonYellow = Color(0xFFFFE94A)
val NeonPink = Color(0xFFFF5DA2)
val NeonCyan = Color(0xFF49E0FF)
val NeonGreen = Color(0xFF6BFF8F)

/** Palette d'un jeu d'arcade (couleurs du HUD, des overlays, du fond). */
data class ArcadePalette(
    val background: Brush,
    val font: FontFamily,
    val title: Color,
    val timeNormal: Color,
    val timeUrgent: Color,
    val score: Color,
    val listText: Color,
    val highlight: Color,
    val flash: Color,
    val scrim: Color,
    val levelColors: List<Color>,
    val buttonColors: List<Color>,
    val retroStyle: Boolean = false
)

val NeonPalette = ArcadePalette(
    background = ArcadeBg, font = FontFamily.Monospace,
    title = NeonYellow, timeNormal = NeonCyan, timeUrgent = NeonPink, score = NeonYellow,
    listText = Color.White, highlight = NeonGreen, flash = Color(0x55FF2D6F),
    scrim = Color(0xEE0B0C2A),
    levelColors = listOf(NeonGreen, NeonCyan, NeonPink),
    buttonColors = listOf(NeonGreen, NeonCyan),
    retroStyle = true
)

val PastelPalette = ArcadePalette(
    background = Brush.verticalGradient(
        listOf(Color(0xFFFFF1F8), Color(0xFFEAF3FF), Color(0xFFF3ECFF))
    ),
    font = FontFamily.Default,
    title = Color(0xFFD9569B), timeNormal = Color(0xFF3A78C9), timeUrgent = Color(0xFFD9569B),
    score = Color(0xFFE08E2B), listText = InkColor, highlight = Color(0xFF3FA45B),
    flash = Color(0x55F4AFC9), scrim = Color(0xF2F8F4FB),
    levelColors = listOf(Color(0xFF9BD3A0), Color(0xFF8AB0DE), Color(0xFFE89AA0)),
    buttonColors = listOf(Color(0xFF9BD3A0), Color(0xFF8AB0DE))
)

enum class ArcadePhase { LEVEL_SELECT, PLAYING, NAME_ENTRY, GAME_OVER }
enum class ArcadeLevel { BABY, CHILD, EXPERT }

/** État d'une partie arcade (timer, score, record, niveau). */
@Stable
class ArcadeController(
    val gameKey: String,
    val durationSec: Int,
    val withLevels: Boolean,
    val speedRamp: Boolean
) {
    var phase by mutableStateOf(if (withLevels) ArcadePhase.LEVEL_SELECT else ArcadePhase.PLAYING)
    var level by mutableStateOf(ArcadeLevel.CHILD)
    var score by mutableIntStateOf(0)
    var pops by mutableIntStateOf(0)          // bulles / étoiles éclatées (affiché à côté du score)
    var timeLeftMs by mutableLongStateOf(durationSec * 1000L)
    var flashing by mutableStateOf(false)
    var startToken by mutableIntStateOf(0)
    var banishToken by mutableIntStateOf(0)

    var bestAtStart = HighScores.best(gameKey)
    var beatRecordPlayed = false
    var alertPlayed = false
    var isNewTopScore = false

    val scored: Boolean get() = !(withLevels && level == ArcadeLevel.BABY)

    val elapsedFraction: Float
        get() = (1f - timeLeftMs / (durationSec * 1000f)).coerceIn(0f, 1f)

    fun speedFactor(): Float {
        if (!speedRamp) return 1f
        val f = elapsedFraction
        return when (level) {
            ArcadeLevel.BABY -> 0.8f                 // un peu plus lent
            ArcadeLevel.CHILD -> 1f + f * 1.0f
            ArcadeLevel.EXPERT -> 1.7f + f * 2.4f
        }
    }

    fun startGame(lvl: ArcadeLevel) {
        level = lvl
        score = 0; pops = 0
        timeLeftMs = durationSec * 1000L
        flashing = false
        beatRecordPlayed = false
        alertPlayed = false
        isNewTopScore = false
        bestAtStart = HighScores.best(gameKey)
        startToken++
        phase = ArcadePhase.PLAYING
    }

    fun addPoints(n: Int) {
        if (!scored) return
        score += n
        if (!beatRecordPlayed && bestAtStart > 0 && score > bestAtStart) {
            beatRecordPlayed = true
            SoundEngine.playNewHighScore()
        }
    }

    fun banishAll(bonusPoints: Int) {
        addPoints(bonusPoints)
        banishToken++
    }

    fun finish() {
        // plus de saisie de nom : le score est enregistré avec la date du jour, direct à l'écran de fin
        if (scored) isNewTopScore = HighScores.record(gameKey, score)
        phase = ArcadePhase.GAME_OVER
    }
}

@Composable
fun rememberArcadeController(
    gameKey: String,
    durationSec: Int,
    withLevels: Boolean,
    speedRamp: Boolean
): ArcadeController = remember(gameKey, durationSec, withLevels, speedRamp) {
    ArcadeController(gameKey, durationSec, withLevels, speedRamp)
}

/** Boucle de temps : décompte, alerte + clignotement à 5 s, fin à 0. */
@Composable
fun ArcadeTimer(controller: ArcadeController) {
    LaunchedEffect(controller.startToken, controller.phase) {
        if (controller.phase != ArcadePhase.PLAYING) return@LaunchedEffect
        // Mode bébé (non scoré) : jeu libre, aucun chrono.
        if (!controller.scored) {
            controller.timeLeftMs = controller.durationSec * 1000L
            return@LaunchedEffect
        }
        val total = controller.durationSec * 1000L
        val startedAt = System.currentTimeMillis()
        while (true) {
            val left = (total - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0)
            controller.timeLeftMs = left
            if (left in 1..5000) {
                if (!controller.alertPlayed) {
                    controller.alertPlayed = true
                    SoundEngine.playAlert()
                }
                controller.flashing = ((System.currentTimeMillis() / 250) % 2L) == 0L
            } else {
                controller.flashing = false
            }
            if (left <= 0L) {
                controller.flashing = false
                controller.finish()
                break
            }
            kotlinx.coroutines.delay(40)
        }
    }
}

/** Bandeau du haut (style « verre ») : chrono à gauche du bouton retour, compteur d'éclatés + score à droite. */
@Composable
fun ArcadeHud(controller: ArcadeController, palette: ArcadePalette, modifier: Modifier = Modifier) {
    val secs = ((controller.timeLeftMs + 999) / 1000).toInt()
    val urgent = controller.timeLeftMs in 1..5000
    if (!controller.scored) return   // pas de chrono ni de score en mode bébé
    val popIcon = if (controller.gameKey == HighScores.Games.STARS) "⭐" else "🫧"
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 62.dp, end = 12.dp, top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        GlassChip {
            Text("⏱", fontSize = 16.sp)
            Text("%d:%02d".format(secs / 60, secs % 60), fontFamily = palette.font, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                color = if (urgent) palette.timeUrgent else Color.White, modifier = Modifier.padding(start = 6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip {
                Text(popIcon, fontSize = 16.sp)
                Text("${controller.pops}", fontFamily = palette.font, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(start = 6.dp))
            }
            GlassChip {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${controller.score}", fontFamily = palette.font, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = palette.score, lineHeight = 26.sp)
                    Text(stringResource(R.string.score_label), fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f), lineHeight = 11.sp)
                }
            }
        }
    }
}

/** Pastille translucide sombre à liseré clair : conteneur des infos de jeu. */
@Composable
fun GlassChip(content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x66000000)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
fun FlashOverlay(controller: ArcadeController, palette: ArcadePalette) {
    if (controller.flashing) {
        Box(modifier = Modifier.fillMaxSize().background(palette.flash))
    }
}

// ---------------------------------------------------------------------------
// Sélection du niveau
// ---------------------------------------------------------------------------

@Composable
fun LevelSelect(controller: ArcadeController, palette: ArcadePalette, onBack: () -> Unit) {
    if (palette.retroStyle) {
        RetroLevelSelect(controller, palette, onBack)
    } else {
        ZenLevelSelect(controller, palette, onBack)
    }
}

/** Sélection de niveau zen (bulles) : fond pastel, boutons doux. */
@Composable
private fun ZenLevelSelect(controller: ArcadeController, palette: ArcadePalette, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.choose_level),
                color = palette.title, fontFamily = palette.font, fontWeight = FontWeight.Bold,
                fontSize = 28.sp, textAlign = TextAlign.Center
            )
            Box(modifier = Modifier.size(24.dp))
            LevelButton(stringResource(R.string.level_baby), palette.levelColors[0], palette.font) {
                controller.startGame(ArcadeLevel.BABY)
            }
            LevelButton(stringResource(R.string.level_child), palette.levelColors[1], palette.font) {
                controller.startGame(ArcadeLevel.CHILD)
            }
            LevelButton(stringResource(R.string.level_expert), palette.levelColors[2], palette.font) {
                controller.startGame(ArcadeLevel.EXPERT)
            }
        }
        BackButton(onBack = onBack)
    }
}

/** Sélection de niveau rétro style cave game 90s (étoiles). */
@Composable
private fun RetroLevelSelect(controller: ArcadeController, palette: ArcadePalette, onBack: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "retro")
    val blink by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = InfiniteRepeatableSpec(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink"
    )
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = InfiniteRepeatableSpec(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "scan"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF000000))
    ) {
        // Scanlines effect
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x12FFFFFF), Color.Transparent),
                    startY = scanY * 2000f - 200f,
                    endY = scanY * 2000f + 60f
                )
            )
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "★ STAR RAIN ★",
                color = NeonYellow, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.6f + 0.4f * blink)
            )
            Text(
                text = "INSERT COIN",
                color = NeonCyan.copy(alpha = blink), fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            RetroLevelButton(
                label = "[ 1UP ] BABY",
                sublabel = "FREE PLAY",
                color = NeonGreen
            ) { controller.startGame(ArcadeLevel.BABY) }

            RetroLevelButton(
                label = "[ 2UP ] PLAYER",
                sublabel = "NORMAL MODE",
                color = NeonCyan
            ) { controller.startGame(ArcadeLevel.CHILD) }

            RetroLevelButton(
                label = "[ 3UP ] EXPERT",
                sublabel = "HARD CORE",
                color = NeonPink
            ) { controller.startGame(ArcadeLevel.EXPERT) }

            Text(
                text = "HI-SCORE  ${"%04d".format(HighScores.best(controller.gameKey))}",
                color = NeonYellow, fontFamily = FontFamily.Monospace,
                fontSize = 13.sp, modifier = Modifier.padding(top = 24.dp)
            )
        }
        BackButton(onBack = onBack)
    }
}

@Composable
private fun RetroLevelButton(label: String, sublabel: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .border(2.dp, color, RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.08f))
            .clickableNoRipple(onClick)
            .padding(vertical = 14.dp, horizontal = 20.dp)
    ) {
        Column {
            Text(
                text = label, color = color, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp
            )
            Text(
                text = sublabel, color = color.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun LevelButton(label: String, color: Color, font: FontFamily, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.30f))
            .clickableNoRipple(onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label, color = InkColor, fontFamily = font, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Saisie du nom (3 lettres) + écran de fin
// ---------------------------------------------------------------------------

@Composable
fun NameEntryOverlay(controller: ArcadeController, palette: ArcadePalette, onDone: () -> Unit) {
    val letters = remember { mutableStateOf(charArrayOf('A', 'A', 'A')) }
    Box(
        modifier = Modifier.fillMaxSize().background(palette.scrim),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.new_high_score),
                color = palette.title, fontFamily = palette.font, fontWeight = FontWeight.Bold,
                fontSize = 24.sp, textAlign = TextAlign.Center
            )
            Text(
                text = "%04d".format(controller.score),
                color = palette.timeUrgent, fontFamily = palette.font,
                fontWeight = FontWeight.Bold, fontSize = 40.sp
            )
            Box(modifier = Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (i in 0..2) {
                    LetterPicker(
                        letter = letters.value[i], palette = palette,
                        onUp = {
                            val c = letters.value.copyOf()
                            c[i] = if (c[i] == 'Z') 'A' else c[i] + 1
                            letters.value = c
                        },
                        onDown = {
                            val c = letters.value.copyOf()
                            c[i] = if (c[i] == 'A') 'Z' else c[i] - 1
                            letters.value = c
                        }
                    )
                }
            }
            Box(modifier = Modifier.size(24.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.buttonColors[0])
                    .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .clickableNoRipple {
                        HighScores.add(controller.gameKey, String(letters.value), controller.score)
                        controller.phase = ArcadePhase.GAME_OVER
                        onDone()
                    }
                    .padding(horizontal = 44.dp, vertical = 14.dp)
            ) {
                Text("OK", color = Color(0xFF14121F), fontFamily = palette.font,
                    fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun LetterPicker(letter: Char, palette: ArcadePalette, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("▲", color = palette.timeNormal, fontSize = 28.sp,
            modifier = Modifier.clickableNoRipple(onUp).padding(8.dp))
        Text(
            text = letter.toString(), color = palette.score, fontFamily = palette.font,
            fontWeight = FontWeight.Bold, fontSize = 48.sp,
            modifier = Modifier.width(46.dp), textAlign = TextAlign.Center
        )
        Text("▼", color = palette.timeNormal, fontSize = 28.sp,
            modifier = Modifier.clickableNoRipple(onDown).padding(8.dp))
    }
}

@Composable
fun GameOverOverlay(
    controller: ArcadeController,
    palette: ArcadePalette,
    onReplay: () -> Unit,
    onBack: () -> Unit
) {
    @Suppress("UNUSED_EXPRESSION") HighScores.version.value
    val scores = remember(HighScores.version.value, controller.gameKey) {
        HighScores.top(controller.gameKey)
    }
    val titlePulse = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        titlePulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    var burst by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (controller.isNewTopScore) {
            SoundEngine.playFanfare()
            repeat(5) { burst++; kotlinx.coroutines.delay(500) }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(palette.scrim),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 60.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (controller.isNewTopScore) stringResource(R.string.champion)
                else stringResource(R.string.game_over),
                color = if (controller.isNewTopScore) palette.score else palette.timeUrgent,
                fontFamily = palette.font, fontWeight = FontWeight.Bold,
                fontSize = if (controller.isNewTopScore) 40.sp else 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    scaleX = titlePulse.value; scaleY = titlePulse.value
                }
            )
            if (controller.scored) {
                // carte de résultat : score en grand, nombre d'éclatés, puis le top 5 daté
                val popIcon = if (controller.gameKey == HighScores.Games.STARS) "⭐" else "🫧"
                // carte « verre » : sombre sur les palettes rétro (fond noir), blanche sur les palettes pastel
                val dark = palette.retroStyle
                Column(
                    Modifier.padding(top = 14.dp).fillMaxWidth(0.86f).shadow(10.dp, RoundedCornerShape(24.dp), clip = false).clip(RoundedCornerShape(24.dp))
                        .background(if (dark) Color(0x80000000) else Color(0xEEFFFFFF))
                        .border(1.dp, if (dark) Color(0x55FFFFFF) else Color(0x33000000), RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${controller.score}", color = palette.score, fontFamily = palette.font, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 46.sp)
                    Text("$popIcon ${controller.pops}", color = if (dark) Color.White else com.goodbrainrot.app.ui.theme.InkColor, fontFamily = palette.font, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Box(modifier = Modifier.size(10.dp))
                    Text(text = stringResource(R.string.high_scores), color = palette.title, fontFamily = palette.font, fontSize = 15.sp)
                    scores.take(5).forEachIndexed { i, e ->
                        val mine = controller.isNewTopScore && i == 0
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${i + 1}.  ${e.name}", color = if (mine) palette.highlight else palette.listText, fontFamily = palette.font, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                            Text("${e.score}", color = if (mine) palette.highlight else palette.listText, fontFamily = palette.font, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                        }
                    }
                }
            }
            Box(modifier = Modifier.size(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ArcadeTextButton(stringResource(R.string.replay), palette.buttonColors[0], palette.font, onReplay)
                ArcadeTextButton(stringResource(R.string.back), palette.buttonColors[1], palette.font, onBack)
            }
        }
        ConfettiBurst(trigger = burst, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ArcadeTextButton(label: String, color: Color, font: FontFamily, onClick: () -> Unit) {
    // Bouton plein couleur vive + texte foncé : lisible sur n'importe quel fond.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp)
    ) {
        Text(label, color = Color(0xFF14121F), fontFamily = font, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}
