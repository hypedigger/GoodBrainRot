package com.goodbrainrot.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.goodbrainrot.app.Haptics
import com.goodbrainrot.app.R
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.audio.GameMusic
import com.goodbrainrot.app.audio.Sfx
import com.goodbrainrot.app.audio.SoundEngine
import com.goodbrainrot.app.data.HighScores
import com.goodbrainrot.app.ui.garden.GMode
import com.goodbrainrot.app.ui.garden.GTool
import com.goodbrainrot.app.ui.garden.GardenGame
import com.goodbrainrot.app.ui.garden.GardenGlView
import com.goodbrainrot.app.ui.garden.GardenRenderer
import com.goodbrainrot.app.ui.garden.TOOLS
import com.goodbrainrot.app.ui.garden.toolById
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// ===========================================================================
// Jardinier Zen — écrans Compose : accueil (3 modes, streak, pièces), atelier
// (2 filières d'outils), partie (vue 3D + HUD + traînée de coupe), résultats.
// ===========================================================================

private val G_GREEN = Color(0xFF3FA45B)
private val G_LEAF = Color(0xFF7FD17F)
private val G_GOLD = Color(0xFFFFC94D)
private val G_INK = Color(0xFF223322)
private val G_CARD = Color(0xF0FFFFFF)

private enum class GPhase { MENU, WORKSHOP, PLAY, RESULT }

private class GArt(private val ctx: android.content.Context) {
    private fun load(name: String): ImageBitmap? {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        return if (id != 0) try { ImageBitmap.imageResource(ctx.resources, id) } catch (_: Exception) { null } else null
    }
    val title = load("gz_title"); val shop = load("gz_shop")
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun dayNumber(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

@Composable
fun GardenScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val art = remember { GArt(ctx) }
    var phase by remember { mutableStateOf(GPhase.MENU) }
    var mode by remember { mutableStateOf(GMode.ZEN) }
    var runToken by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var earned by remember { mutableIntStateOf(0) }
    var dailyBonus by remember { mutableStateOf(false) }
    val lofiRes = remember { ctx.resources.getIdentifier("gz_lofi", "raw", ctx.packageName) }
    val gameKey = when (mode) { GMode.ZEN -> HighScores.Games.GARDEN_ZEN; GMode.RUSH -> HighScores.Games.GARDEN_RUSH; GMode.SLICE -> HighScores.Games.GARDEN_SLICE }
    val controller = remember(runToken, gameKey) { ArcadeController(gameKey, 0, withLevels = false, speedRamp = false) }
    val tool = toolById(Settings.gardenActiveTool.intValue)
    val labels = mapOf("bushdone" to stringResource(R.string.gz_bush_done), "combo" to stringResource(R.string.gz_combo),
        "missed" to stringResource(R.string.gz_missed), "fast" to stringResource(R.string.gz_fast))
    var sliceEasy by remember { mutableStateOf(true) }
    val game = remember(runToken) { if (phase == GPhase.PLAY) GardenGame(mode, tool, dayNumber(), labels, easy = mode == GMode.SLICE && sliceEasy) else null }
    val renderer = remember(game) { game?.let { g -> GardenRenderer(ctx).also { r -> r.onFrame = { rr, dt -> g.update(dt, rr); g.render(rr) } } } }
    val glView = remember(renderer) { renderer?.let { GardenGlView(ctx, it) } }

    DisposableEffect(Unit) { onDispose { GameMusic.stop() } }
    DisposableEffect(glView) { onDispose { glView?.onPause() } }
    LaunchedEffect(Unit) { if (lofiRes != 0) GameMusic.playRes(ctx, lofiRes) }

    LaunchedEffect(game) {
        val g = game ?: return@LaunchedEffect
        var motor = 0
        try {
            while (true) {
                withFrameNanos { }
                // moteur des outils électriques : boucle tant que le doigt est posé
                if (g.tool.electric) {
                    if (g.fingerDown && motor == 0) motor = Sfx.loop(if (g.tool.tier >= 4) "gz_chainsaw" else "gz_trimmer", 0.55f, 0.9f + g.tool.tier * 0.05f)
                    if (!g.fingerDown && motor != 0) { Sfx.stopStream(motor); motor = 0 }
                }
                while (true) {
                    val e = g.events.poll() ?: break
                    when (e) {
                        // (plus de « gz_snap » : ce claquement sonnait comme un « NO » — remplacé par un bzzt-crac court)
                        "cut" -> { if (g.tool.electric) Sfx.play("gz_zap", 0.7f, 0.95f + Random.nextFloat() * 0.15f) else Sfx.play(if (Random.nextBoolean()) "gz_snip" else "gz_snip2", 0.8f, 0.9f + Random.nextFloat() * 0.3f); Sfx.play("gz_rustle", 0.5f, 1f); Haptics.tap(ctx) }
                        "bamboo" -> { Sfx.play("gz_bamboo", 0.9f, 0.9f + Random.nextFloat() * 0.3f); Haptics.tap(ctx) }
                        "chew" -> Sfx.play("gz_clipper", 0.6f, 0.9f + Random.nextFloat() * 0.3f)
                        "oops" -> { Sfx.play("gz_error", 0.7f, 1f) }
                        "miss" -> { Sfx.play("gz_rustle", 0.8f, 0.8f) }
                        "sting", "nest" -> { Sfx.play("gz_error", 0.9f, 0.8f); Haptics.tap(ctx) }
                        "dead" -> SoundEngine.playVvGameOver()
                        "done" -> SoundEngine.playVvSuccess()
                        "newbush" -> Sfx.play("gz_rustle", 0.9f, 1.2f)
                        "multi" -> { Sfx.play("gz_slowmo", 0.9f, 1f); SoundEngine.playVvYahoo(); Haptics.tap(ctx) }
                        "timeup" -> SoundEngine.playVvGameOver()
                    }
                }
                tick++
                if (g.over && phase == GPhase.PLAY) {
                    // récompenses : pièces = score/10, bonus x2 sur la première partie du jour, streak
                    val day = today()
                    var coins = g.score / 10
                    dailyBonus = Settings.gardenDailyBonusDay.value != day
                    if (dailyBonus) { coins *= 2; Settings.setGardenDailyBonusDay(day) }
                    if (Settings.gardenLastDay.value != day) {
                        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - 86_400_000L))
                        val streak = if (Settings.gardenLastDay.value == yesterday) Settings.gardenStreak.intValue + 1 else 1
                        Settings.setGardenStreak(streak, day)
                    }
                    earned = coins
                    Settings.setGardenCoins(Settings.gardenCoins.intValue + coins)
                    controller.score = g.score; controller.finish()
                    phase = GPhase.RESULT
                    break
                }
                if (phase != GPhase.PLAY) break
            }
        } finally { Sfx.stopStream(motor) }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFCDEBC7), Color(0xFF8CC7A1))))) {
        when (phase) {
            GPhase.MENU -> MenuScreen(art, onBack = onBack, onWorkshop = { Sfx.play("gz_tick"); phase = GPhase.WORKSHOP },
                onPlay = { m, easy -> Sfx.play("gz_tick"); mode = m; sliceEasy = easy; phase = GPhase.PLAY; runToken++ })
            GPhase.WORKSHOP -> WorkshopScreen(art, onBack = { phase = GPhase.MENU })
            GPhase.PLAY -> {
                val g = game
                if (g != null && glView != null) {
                    AndroidView(factory = { glView.also { it.onResume() } }, modifier = Modifier.fillMaxSize())
                    SwipeLayer(g, glView, tick)
                    FloatingTexts(g, tick)
                    GardenHud(g, tool, tick)
                }
                BackButton(onBack = { phase = GPhase.MENU }, modifier = Modifier.align(Alignment.TopStart))
            }
            GPhase.RESULT -> {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2E5A3A), Color(0xFF1B3A26)))))
                run {
                    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        val g = game
                        Text(stringResource(if (game?.mode == GMode.SLICE && game.lives <= 0 && !game.easy) R.string.gz_no_lives else R.string.gz_time_up), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                        if (g?.mode == GMode.ZEN) Text(stringResource(R.string.gz_bushes) + " ${g.bushesDone}  ·  " + stringResource(R.string.gz_precision) + " ${g.precision} %", color = G_LEAF, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                        Text("${controller.score}", color = G_GOLD, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp)
                        Text(stringResource(R.string.gz_earned, earned), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        if (dailyBonus) Text(stringResource(R.string.gz_daily_bonus), color = G_GOLD, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(12.dp))
                        ShopOffer(onWorkshop = { phase = GPhase.WORKSHOP })
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.high_scores), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        HighScores.top(gameKey).take(5).forEachIndexed { i, e -> Text("${i + 1}.  ${e.name}  ${e.score}", color = Color.White, fontSize = 15.sp) }
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            GButton(stringResource(R.string.replay), listOf(G_GREEN, Color(0xFF2E8B57))) { phase = GPhase.PLAY; runToken++ }
                            GButton(stringResource(R.string.back), listOf(Color(0xFF7FA8C9), Color(0xFF4F7BA3))) { phase = GPhase.MENU }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------

/** Après une partie : si un outil de la filière suivante est abordable, on le propose directement (+ raccourci atelier). */
@Composable
private fun ShopOffer(onWorkshop: () -> Unit) {
    val coins by Settings.gardenCoins
    val manual by Settings.gardenManualTier
    val electric by Settings.gardenElectricTier
    val candidates = listOfNotNull(TOOLS.firstOrNull { !it.electric && it.tier == manual + 1 }, TOOLS.firstOrNull { it.electric && it.tier == electric + 1 })
    val offer = candidates.filter { it.cost <= coins }.minByOrNull { it.cost } ?: candidates.minByOrNull { it.cost }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x33FFFFFF)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("🧰", fontSize = 22.sp); Spacer(Modifier.width(8.dp)); Chip("🪙 $coins", G_GOLD) }
        if (offer != null) {
            Text(stringResource(R.string.gz_new_tool), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(offer.r, offer.g, offer.b)), contentAlignment = Alignment.Center) { Text(if (offer.electric) "⚡" else "✂", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(offer.nameRes), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                GButton(stringResource(R.string.gz_buy) + "  🪙 ${offer.cost}", listOf(G_GOLD, Color(0xFFFF9F1C)), enabled = coins >= offer.cost) {
                    Settings.setGardenCoins(coins - offer.cost)
                    if (offer.electric) Settings.setGardenElectricTier(offer.tier) else Settings.setGardenManualTier(offer.tier)
                    Settings.setGardenActiveTool(offer.id); Sfx.play("gz_buy")
                }
                GButton(stringResource(R.string.gz_workshop), listOf(Color(0xFF8D6E63), Color(0xFF5D4037)), onClick = onWorkshop)
            }
        } else GButton(stringResource(R.string.gz_workshop), listOf(Color(0xFF8D6E63), Color(0xFF5D4037)), onClick = onWorkshop)
    }
}

/** Textes flottants du monde 3D (+10, COMBO, Parfait !) projetés à l'écran par le renderer. */
@Composable
private fun FloatingTexts(g: GardenGame, frame: Int) {
    @Suppress("UNUSED_EXPRESSION") frame
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        for (t in g.textSnap) {
            if (t.sx < -500f) continue
            val a = (t.life / 0.5f).coerceIn(0f, 1f)
            val w = with(density) { if (t.big) 260.dp.roundToPx() else 120.dp.roundToPx() }
            Text(t.text, color = Color(t.r, t.g, t.b).copy(alpha = a), fontWeight = FontWeight.ExtraBold, fontSize = if (t.big) 30.sp else 20.sp, textAlign = TextAlign.Center,
                modifier = Modifier.offset { IntOffset((t.sx - w / 2).toInt(), (t.sy - 40).toInt()) }.width(with(density) { w.toDp() }))
        }
    }
}

@Composable
private fun GButton(label: String, colors: List<Color>, enabled: Boolean = true, onClick: () -> Unit) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .scale(press.value)
            .shadow(if (enabled) 8.dp else 0.dp, RoundedCornerShape(26.dp), clip = false)
            .clip(RoundedCornerShape(26.dp))
            .background(if (enabled) Brush.horizontalGradient(colors) else Brush.horizontalGradient(listOf(Color(0xFFBBBBBB), Color(0xFFA0A0A0))))
            .clickableNoRipple { if (enabled) { scope.launch { press.snapTo(0.93f); press.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 600f)) }; onClick() } }
            .padding(horizontal = 26.dp, vertical = 14.dp)
    ) { Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
}

@Composable
private fun MenuScreen(art: GArt, onBack: () -> Unit, onWorkshop: () -> Unit, onPlay: (GMode, Boolean) -> Unit) {
    val coins by Settings.gardenCoins
    val streak by Settings.gardenStreak
    val tool = toolById(Settings.gardenActiveTool.intValue)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            art.title?.let { Image(painter = BitmapPainter(it), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAACDEBC7)))))
            BackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.activity_garden), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip("🪙 $coins", G_GOLD); Spacer(Modifier.width(8.dp))
                    if (streak > 0) Chip("🔥 " + stringResource(R.string.gz_streak, streak), Color(0xFFFF8A65))
                }
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeCard(stringResource(R.string.gz_mode_zen), stringResource(R.string.gz_target_hint), listOf(Color(0xFF9BE3A8), Color(0xFF5DBE7A)), "✂", "1:00", Modifier.weight(1f)) { onPlay(GMode.ZEN, false) }
            ModeCard(stringResource(R.string.gz_mode_rush), stringResource(R.string.gz_mode_rush_desc), listOf(Color(0xFFFFD08A), Color(0xFFFF9F5A)), "»", "3:00", Modifier.weight(1f)) { onPlay(GMode.RUSH, false) }
            // Tranche : deux façons de jouer, choisies directement sur la carte
            ModeCard(stringResource(R.string.gz_mode_slice), stringResource(R.string.gz_mode_slice_desc), listOf(Color(0xFF9FD8FF), Color(0xFF5FAEE8)), "⟋", "1:00", Modifier.weight(1f),
                sub = listOf(("🙂 " + stringResource(R.string.gz_slice_easy)) to { onPlay(GMode.SLICE, true) }, ("💔 " + stringResource(R.string.gz_slice_classic)) to { onPlay(GMode.SLICE, false) })) { onPlay(GMode.SLICE, true) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(RoundedCornerShape(18.dp)).background(G_CARD).clickableNoRipple(onWorkshop).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(tool.r, tool.g, tool.b)), contentAlignment = Alignment.Center) { Text(if (tool.electric) "⚡" else "✂", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
                Column(Modifier.padding(start = 10.dp)) { Text(stringResource(R.string.gz_equipped), color = G_INK.copy(alpha = 0.7f), fontSize = 12.sp); Text(stringResource(tool.nameRes), color = G_INK, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
            GButton("🧰 " + stringResource(R.string.gz_workshop), listOf(Color(0xFF8D6E63), Color(0xFF5D4037)), onClick = onWorkshop)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(color).padding(horizontal = 12.dp, vertical = 4.dp)) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
private fun ModeCard(title: String, desc: String, colors: List<Color>, glyph: String, duration: String, modifier: Modifier,
                     sub: List<Pair<String, () -> Unit>> = emptyList(), onClick: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp), clip = false).clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(colors)).clickableNoRipple(onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) { Text(glyph, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold) }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(desc, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, maxLines = 2)
            Text("⏱ $duration", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (sub.isNotEmpty()) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, act) in sub) Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.92f)).clickableNoRipple(act).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(label, color = G_INK, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WorkshopScreen(art: GArt, onBack: () -> Unit) {
    val coins by Settings.gardenCoins
    val manual by Settings.gardenManualTier
    val electric by Settings.gardenElectricTier
    val active by Settings.gardenActiveTool
    Box(Modifier.fillMaxSize()) {
        art.shop?.let { Image(painter = BitmapPainter(it), contentDescription = null, modifier = Modifier.fillMaxSize().alpha(0.35f), contentScale = ContentScale.Crop) }
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) { BackButton(onBack = onBack); Text(stringResource(R.string.gz_workshop), color = G_INK, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp) }
                Chip("🪙 $coins", G_GOLD)
            }
            Text(stringResource(R.string.gz_tap_to_equip), color = G_INK.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                for (electricCol in listOf(false, true)) {
                    Text(stringResource(if (electricCol) R.string.gz_electric else R.string.gz_manual), color = G_INK, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 8.dp))
                    val owned = if (electricCol) electric else manual
                    for (t in TOOLS.filter { it.electric == electricCol }) {
                        val isOwned = t.tier <= owned
                        val isNext = t.tier == owned + 1
                        val isActive = t.id == active
                        ToolRow(t, isOwned, isNext, isActive, coins,
                            onBuy = { if (coins >= t.cost) { Settings.setGardenCoins(coins - t.cost); if (electricCol) Settings.setGardenElectricTier(t.tier) else Settings.setGardenManualTier(t.tier); Settings.setGardenActiveTool(t.id); Sfx.play("gz_buy") } else Sfx.play("gz_error") },
                            onEquip = { Settings.setGardenActiveTool(t.id); Sfx.play("gz_tick") })
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ToolRow(t: GTool, owned: Boolean, next: Boolean, active: Boolean, coins: Int, onBuy: () -> Unit, onEquip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).shadow(if (active) 8.dp else 2.dp, RoundedCornerShape(18.dp), clip = false).clip(RoundedCornerShape(18.dp))
            .background(if (active) Color(0xFFFFF4C2) else if (owned || next) G_CARD else Color(0xAAE8E8E8))
            .border(if (active) 3.dp else 0.dp, if (active) G_GOLD else Color.Transparent, RoundedCornerShape(18.dp))
            .clickableNoRipple { when { owned && !active -> onEquip(); next && coins >= t.cost -> onBuy(); else -> {} } }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(t.r, t.g, t.b)), contentAlignment = Alignment.Center) {
            Text(if (t.electric) "⚡" else "✂", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(stringResource(t.nameRes), color = if (owned || next) G_INK else G_INK.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(if (owned) (if (active) stringResource(R.string.gz_equipped) else "✓") else if (next) "🪙 ${t.cost}" else stringResource(R.string.gz_locked), color = G_INK.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        when {
            active -> {}
            owned -> GButton(stringResource(R.string.gz_equip), listOf(G_GREEN, Color(0xFF2E8B57)), onClick = onEquip)
            next -> GButton(stringResource(R.string.gz_buy), listOf(G_GOLD, Color(0xFFFF9F1C)), enabled = coins >= t.cost, onClick = onBuy)
            else -> {}
        }
    }
}

/** Couche tactile : envoie les points du swipe au jeu (thread GL) et dessine la traînée de coupe. */
@Composable
private fun SwipeLayer(g: GardenGame, glView: GardenGlView, tick: Int) {
    val pts = remember { ArrayList<Offset>() }
    var ver by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().pointerInput(g) {
        // Topiaire : un glisser qui COMMENCE dans le bas de l'écran (sous la plante) TOURNE la plante
        // au lieu de couper — la rotation auto se met alors en pause quelques secondes.
        var rotMode = false
        detectDragGestures(
            onDragStart = { p ->
                rotMode = g.mode == GMode.ZEN && p.y > size.height * 0.76f
                if (!rotMode) { pts.clear(); pts.add(p); g.fingerDown = true; g.fingerX = p.x; g.fingerY = p.y; g.swipe.add(floatArrayOf(p.x, p.y, 1f)) }
                ver++
            },
            onDrag = { change, drag -> change.consume()
                if (rotMode) { glView.queueEvent { g.rotateBy(-drag.x * 0.35f) } }
                else { val p = change.position; pts.add(p); if (pts.size > 18) pts.removeAt(0)
                    g.fingerX = p.x; g.fingerY = p.y; g.swipe.add(floatArrayOf(p.x, p.y, 1f)) }
                ver++ },
            onDragEnd = { rotMode = false; g.fingerDown = false; g.fingerX = -1f; g.swipe.add(floatArrayOf(0f, 0f, 0f)); pts.clear(); ver++ },
            onDragCancel = { rotMode = false; g.fingerDown = false; g.fingerX = -1f; g.swipe.add(floatArrayOf(0f, 0f, 0f)); pts.clear(); ver++ }
        )
    }) {
        @Suppress("UNUSED_EXPRESSION") tick; @Suppress("UNUSED_EXPRESSION") ver
        Canvas(Modifier.fillMaxSize()) {
            if (pts.size >= 2) {
                val p = Path(); p.moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
                drawPath(p, Color.White.copy(alpha = 0.35f), style = Stroke(width = size.width * g.tool.band * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(p, if (g.tool.electric && g.tool.tier == 5) Color(0xFF7CFF6B) else Color.White, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (g.flash > 0f) drawRect(Color(0xFFFF6B6B).copy(alpha = g.flash * 0.35f))
        }
    }
}

/** Pastille « verre » sombre (HUD des jeux). */
@Composable
private fun GPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x66000000)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) { content() }
}

/** Petite icône de pousse (tige inclinée + touffe orange), pour compter ce qui reste à tailler. */
@Composable
private fun TwigIcon(alpha: Float = 1f) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        drawLine(Color(0xFF8D6E63).copy(alpha = alpha), Offset(w * 0.2f, h * 0.9f), Offset(w * 0.7f, h * 0.3f), strokeWidth = w * 0.16f, cap = StrokeCap.Round)
        drawCircle(Color(0xFFFF9A3C).copy(alpha = alpha), radius = w * 0.2f, center = Offset(w * 0.72f, h * 0.28f))
        drawCircle(Color(0xFFFFC46B).copy(alpha = alpha), radius = w * 0.11f, center = Offset(w * 0.5f, h * 0.5f))
    }
}

@Composable
private fun GardenHud(g: GardenGame, tool: GTool, frame: Int) {
    // [frame] change à chaque image : force la recomposition (l'objet jeu, lui, est le même — Compose sauterait le HUD)
    @Suppress("UNUSED_EXPRESSION") frame
    @Suppress("UNUSED_EXPRESSION") tool
    Box(Modifier.fillMaxSize()) {
        // ---- haut : chrono à côté du bouton retour, score à droite (une seule rangée, rien d'autre)
        Row(Modifier.fillMaxWidth().padding(start = 62.dp, end = 12.dp, top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val secs = g.timeLeft.toInt().coerceAtLeast(0)
            GPill { Text("⏱", fontSize = 15.sp); Text("%d:%02d".format(secs / 60, secs % 60), color = if (secs <= 10) Color(0xFFFF6B6B) else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.padding(start = 6.dp)) }
            GPill {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${g.score}", color = G_GOLD, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 26.sp)
                    if (g.combo > 1) Text("x${g.mult}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 13.sp)
                }
            }
        }
        // ---- consignes (début de partie)
        if (g.elapsed < 4f) Column(Modifier.align(Alignment.TopCenter).padding(top = 84.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(if (g.mode == GMode.ZEN) R.string.gz_target_hint else if (g.mode == GMode.RUSH) R.string.gz_rush_hint else R.string.gz_swipe_hint),
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x88000000), Offset(0f, 2f), 6f)))
            if (g.mode == GMode.ZEN) Text(stringResource(R.string.gz_rotate_hint), color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x88000000), Offset(0f, 2f), 6f)), modifier = Modifier.padding(top = 4.dp))
        }
        else if (g.mode != GMode.ZEN && g.elapsed in 5f..8f) Text(stringResource(R.string.gz_bees_hint), color = Color(0xFFFFE08A), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 84.dp))
        // ---- bas : barre d'état du mode
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)) {
            when (g.mode) {
                GMode.ZEN -> GPill {
                    // icônes des pousses restantes (jusqu'à 14) + compteur, puis nombre de plantes finies
                    val left = g.outsideLeft
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(left.coerceIn(0, 14)) { TwigIcon() }; if (left > 14) Text("…", color = Color.White, fontSize = 14.sp) }
                    Text("$left", color = Color(0xFFFF9A3C), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, modifier = Modifier.padding(start = 10.dp))
                    Box(Modifier.padding(horizontal = 12.dp).size(1.dp, 26.dp).background(Color(0x66FFFFFF)))
                    Text("🌳 ${g.bushesDone}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                GMode.RUSH -> GPill {
                    Text("💨 ×%.1f".format(g.speed), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Box(Modifier.padding(start = 12.dp).width(120.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxWidth(if (g.combo > 0) (g.comboTimer / 2.2f).coerceIn(0f, 1f) else 0f).fillMaxHeight().background(Brush.horizontalGradient(listOf(G_GOLD, Color(0xFFFF8A65)))))
                    }
                    Text(if (g.combo > 0) "  x${g.mult}" else "", color = G_GOLD, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                GMode.SLICE -> GPill {
                    if (!g.easy) Text((0 until 3).joinToString(" ") { if (it < g.lives) "❤️" else "🖤" }, fontSize = 17.sp)
                    else Text("🙂 " + stringResource(R.string.gz_slice_easy), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Box(Modifier.padding(start = 12.dp).width(120.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxWidth(if (g.combo > 0) (g.comboTimer / 2.2f).coerceIn(0f, 1f) else 0f).fillMaxHeight().background(Brush.horizontalGradient(listOf(G_GOLD, Color(0xFFFF8A65)))))
                    }
                    Text(if (g.combo > 0) "  x${g.mult}" else "", color = G_GOLD, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }
    }
}
