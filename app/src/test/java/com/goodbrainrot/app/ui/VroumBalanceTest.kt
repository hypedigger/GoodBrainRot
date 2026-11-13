package com.goodbrainrot.app.ui

import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Banc d'équilibrage de Vroum : des robots de trois niveaux (enfant de 5 ans, joueur moyen, expert) jouent des centaines
 * de parties sur le vrai modèle de jeu ; le tableau imprimé sert à régler chrono, étoiles et essence (VTUNES,
 * VROUM_STAR_SCORE, VROUM_STAR_DIST). Lancer : gradlew testDebugUnitTest --tests "*VroumBalanceTest*"
 * (résultat dans app/build/vroum_balance.txt).
 */
class VroumBalanceTest {
    /** [react] : secondes entre deux décisions ; [look] : anticipation (s) ; [err] : part de décisions ratées ; [jumps] : sait sauter ; [daring] : cherche le frôlement ; [boosts] : tient le bouton de boost. */
    private class Bot(val name: String, val react: Float, val look: Float, val err: Float, val jumps: Float, val daring: Boolean, val boosts: Float)
    private val bots = listOf(
        Bot("enfant", 0.75f, 0.9f, 0.25f, 0.15f, false, 0.3f),
        Bot("moyen", 0.30f, 1.0f, 0.06f, 0.85f, false, 0.8f),
        Bot("expert", 0.10f, 1.6f, 0.01f, 1.0f, true, 1.0f),
    )

    private class Run(val cleared: Boolean, val time: Float, val score: Int, val dist: Float, val bumps: Int, val nears: Int, val combo: Int, val avgSpeed: Float, val takedowns: Int, val stomp: Int)

    private fun play(endless: Boolean, diff: Int, bot: Bot, seed: Long, theme: VTheme = THEMES[0]): Run {
        val g = VroumGame(endless, 0, diff, theme, "T", "C", seed = seed)
        val r = Random(seed xor 0x5DEECE66DL)
        val dt = 1f / 60f
        var think = 0f
        var steps = 0
        while (!g.over && !g.cleared && steps < 60 * 60 * 20) {
            steps++
            think -= dt
            if (think <= 0f && !g.inIntro) {
                think = bot.react * (0.7f + r.nextFloat() * 0.6f)
                decide(g, bot, r)
            }
            g.update(dt)
            g.events.clear(); if (g.parts.size > 40) g.parts.clear()
        }
        return Run(g.cleared, g.raceT, g.score, g.dist, g.bumpCount, g.nearCount, g.bestCombo, if (g.raceT > 0f) g.dist / g.raceT else 0f, g.takedowns, g.bestStomp)
    }

    private fun decide(g: VroumGame, bot: Bot, r: Random) {
        // boost : le joueur le tient dès qu'il en a (plus ou moins assidûment selon son niveau)
        g.boostHeld = g.boost > 0.3f && r.nextFloat() < bot.boosts || (g.boosting && r.nextFloat() < bot.boosts)
        if (r.nextFloat() < bot.err) { if (r.nextBoolean()) g.moveLane(if (r.nextBoolean()) 1 else -1); return }
        val sp = g.mps.coerceAtLeast(1f)
        val pz = VroumGame.PLAYER_Z
        val n = g.lanes
        val danger = FloatArray(n) { 99f }      // secondes avant le premier danger de la voie
        val reward = FloatArray(n)
        val giant = g.giant > 0 || g.superT > 0f
        val powered = g.boosting || g.turboT > 0f
        for (e in g.enemies) {
            if (!e.alive || e.lane !in 0 until n || e.rival) continue
            val gap = e.z - pz; val closing = sp - e.mps
            if (gap < -1.6f || closing <= 0.5f) continue
            val t = ((gap - 2f) / closing).coerceAtLeast(0f)
            val bad = !giant && !e.ghost && (e.oncoming || e.boulder || (e.heavy && !powered))
            if (bad) { if (t < danger[e.lane]) danger[e.lane] = t } else if (t < bot.look * 2f) reward[e.lane] += if (e.ghost) 40f else 70f
        }
        for (p in g.picks) {
            if (p.lane !in 0 until n) continue
            val t = (p.z - pz) / sp
            if (t < -0.05f) continue
            when (p.type) {
                VItem.HOLE, VItem.LOG -> if (!giant && t < danger[p.lane]) danger[p.lane] = t
                VItem.PUDDLE -> if (!giant && t < bot.look) reward[p.lane] -= 60f
                VItem.FUEL -> if (t < bot.look * 2.5f) reward[p.lane] += if (g.fuelOn) (if (g.fuel < 70f) 600f else 80f) else 0f
                VItem.STAR, VItem.SUPERBOOST, VItem.ITEMBOX -> if (t < bot.look * 2.5f) reward[p.lane] += 300f
                VItem.CANNON, VItem.PASSENGER -> if (t < bot.look * 2.5f) reward[p.lane] += 180f
                VItem.TURBO, VItem.RAMP -> if (t < bot.look * 1.5f) reward[p.lane] += 45f
                VItem.ACORN -> if (t < bot.look * 1.5f) reward[p.lane] += 10f
                VItem.MUSHROOM -> if (t < bot.look * 1.5f) reward[p.lane] += 20f
                VItem.BERRY -> if (t < bot.look * 1.5f) reward[p.lane] += 30f
            }
        }
        val cur = g.lane.coerceIn(0, n - 1)
        // l'expert reste volontairement face au danger jusqu'au dernier moment (frôlement) si une sortie est libre
        val nearT = g.tune.nearT
        fun blocked(l: Int, horizon: Float) = danger[l] < horizon
        val exitFree = (cur > 0 && !blocked(cur - 1, 0.9f)) || (cur < n - 1 && !blocked(cur + 1, 0.9f))
        val horizonCur = if (bot.daring && exitFree) nearT * 0.7f else bot.look
        var best = cur; var bestVal = -1e9f
        for (l in 0 until n) {
            val h = if (l == cur) horizonCur else bot.look
            var v = reward[l] - abs(l - cur) * 8f
            if (blocked(l, h)) v -= 1000f * (1f + (h - danger[l]))
            if (abs(l - cur) == 2 && blocked((l + cur) / 2, 0.35f)) v -= 800f
            if (v > bestVal) { bestVal = v; best = l }
        }
        // saut : danger imminent dans ma voie et aucune voie libre
        val imminent = danger[cur] < 0.42f
        if (imminent && (best == cur || blocked(best, 0.5f)) && g.jumpCd <= 0f && !g.jumping && r.nextFloat() < bot.jumps) { g.jump(); return }
        if (best != cur) { g.moveLane(if (best > cur) 1 else -1); if (bot.react < 0.2f && abs(best - cur) == 2) g.moveLane(if (best > cur) 1 else -1) }
    }

    private fun pct(xs: List<Float>, p: Float): Float { val s = xs.sorted(); return s[((s.size - 1) * p).toInt()] }

    private fun line(sb: StringBuilder, bot: Bot, runs: List<Run>, endless: Boolean, diff: Int) {
        val n = runs.size
        val stars = runs.map { vroumStarCount(endless, it.cleared, it.score, it.dist.toInt(), diff) }
        val sc = runs.map { it.score.toFloat() }; val ds = runs.map { it.dist }; val tm = runs.map { it.time }
        sb.append(String.format(java.util.Locale.US,
            "  %-7s arrivé %3d%%  temps %5.1f/%5.1f/%5.1f s  v.moy %4.1f  score %5.0f/%5.0f/%5.0f  dist %5.0f/%5.0f/%5.0f  chocs %4.1f  frôlés %4.1f  série %3.0f  takedowns %4.1f  rebonds %3.1f  ★ %d%%/%d%%/%d%%/%d%%\n",
            bot.name, runs.count { it.cleared } * 100 / n, pct(tm, 0.1f), pct(tm, 0.5f), pct(tm, 0.9f), runs.map { it.avgSpeed }.average(),
            pct(sc, 0.1f), pct(sc, 0.5f), pct(sc, 0.9f), pct(ds, 0.1f), pct(ds, 0.5f), pct(ds, 0.9f),
            runs.map { it.bumps }.average(), runs.map { it.nears }.average(), runs.map { it.combo }.average(), runs.map { it.takedowns }.average(), runs.map { it.stomp }.average(),
            stars.count { it == 0 } * 100 / n, stars.count { it == 1 } * 100 / n, stars.count { it == 2 } * 100 / n, stars.count { it == 3 } * 100 / n))
    }

    // ---- Règles du jeu, vérifiées une par une sur une route vidée -----------------------------------------------

    /** Partie Moyen lancée (décompte passé), route vide, joueur au centre. */
    private fun emptyRoad(): VroumGame {
        val g = VroumGame(false, 0, 1, THEMES[0], "T", "C", seed = 42L)
        repeat(60 * 4) { g.update(1f / 60f) }
        g.enemies.clear(); g.picks.clear(); g.events.clear()
        return g
    }
    private fun run(g: VroumGame, seconds: Float, each: (VroumGame) -> Unit = {}) { repeat((seconds * 60).toInt()) { each(g); g.update(1f / 60f) } }

    @Test
    fun ramming_a_small_car_from_behind_is_a_takedown() {
        val g = emptyRoad()
        g.enemies.add(VEnemy(1, VroumGame.PLAYER_Z + 12f, 0, 7f))
        run(g, 2f) { it.picks.clear() }
        org.junit.Assert.assertEquals("takedown", 1, g.takedowns)
        org.junit.Assert.assertEquals("pas d'accident", 0, g.bumpCount)
    }

    @Test
    fun oncoming_car_is_a_crash_and_costs_momentum() {
        val g = emptyRoad()
        g.flow = 1f
        g.enemies.add(VEnemy(1, VroumGame.PLAYER_Z + 40f, 0, -10f).also { it.oncoming = true })
        run(g, 2f) { it.picks.clear() }
        org.junit.Assert.assertEquals("accident", 1, g.bumpCount)
        org.junit.Assert.assertEquals("pas de takedown", 0, g.takedowns)
        org.junit.Assert.assertTrue("l'élan retombe", g.flow < 0.6f)
    }

    @Test
    fun heavy_truck_needs_boost() {
        val a = emptyRoad()
        a.enemies.add(VEnemy(1, VroumGame.PLAYER_Z + 12f, 2, 7f).also { it.heavy = true })
        run(a, 2f) { it.picks.clear() }
        org.junit.Assert.assertEquals("sans boost : accident", 1, a.bumpCount)
        val b = emptyRoad()
        b.boost = 1f; b.boostHeld = true
        b.enemies.add(VEnemy(1, VroumGame.PLAYER_Z + 16f, 2, 7f).also { it.heavy = true })
        run(b, 2f) { it.picks.clear() }
        org.junit.Assert.assertEquals("en boost : takedown", 1, b.takedowns)
        org.junit.Assert.assertEquals(0, b.bumpCount)
    }

    @Test
    fun landing_on_cars_chains_bounces() {
        val g = emptyRoad()
        val closing = g.mps - 7f; val hop = closing * g.JUMP_DUR * 0.8f
        // trois voitures dans ma voie, espacées d'un rebond : je saute une fois, je rebondis trois fois
        for (k in 1..3) g.enemies.add(VEnemy(1, VroumGame.PLAYER_Z + hop * k, 0, 7f))
        g.jump()
        run(g, 5f) { it.picks.clear() }
        org.junit.Assert.assertTrue("chaîne de rebonds (obtenu ${g.bestStomp})", g.bestStomp >= 3)
        org.junit.Assert.assertEquals("aucun accident", 0, g.bumpCount)
        org.junit.Assert.assertEquals(3, g.takedowns)
    }

    @Test
    fun shoving_a_rival_off_the_road_is_a_takedown() {
        val g = emptyRoad()
        val r = VEnemy(2, VroumGame.PLAYER_Z, 0, g.mps).also { it.rival = true; it.fx = 2f; it.target = 2; it.aiCd = 99f; it.want = 0f }
        g.enemies.add(r)
        g.moveLane(1)                // je me rabats sur lui : il est au bord, il sort
        run(g, 0.5f) { it.picks.clear() }
        org.junit.Assert.assertEquals(1, g.takedowns)
        org.junit.Assert.assertEquals(2, g.lane)
    }

    @Test
    fun pressing_boost_right_before_go_gives_a_rocket_start() {
        fun run(pressAt: Float): VroumGame {
            val g = VroumGame(false, 0, 1, THEMES[0], "T", "C", seed = 42L)
            while (g.inIntro) { if (g.introT <= pressAt) g.boostHeld = true; g.update(1f / 60f) }
            return g
        }
        org.junit.Assert.assertTrue("bon timing", run(0.3f).rocketStart)
        org.junit.Assert.assertFalse("trop tôt", run(2.0f).rocketStart)
        org.junit.Assert.assertFalse("sans appuyer", run(-1f).rocketStart)
    }

    @Test
    fun every_circuit_can_be_finished_on_easy() {
        for (th in THEMES) {
            val r = play(false, 0, bots[0], 7L, th)
            org.junit.Assert.assertTrue("circuit ${th.key} : pas arrivé", r.cleared)
        }
    }

    @Test
    fun balance() {
        val n = 120
        val sb = StringBuilder("\n=== VROUM — équilibrage ($n parties par case) ===\n")
        for (endless in listOf(false, true)) for (diff in 0..2) {
            sb.append("\n${if (endless) "ARCADE" else "COURSE"} ${listOf("Facile", "Moyen", "Difficile")[diff]}" +
                (if (!endless) "  (chrono ${VTUNES[diff].timeLimit.toInt()} s, étoiles ${VROUM_STAR_SCORE[diff].toList()})" else "  (étoiles ${VROUM_STAR_DIST[diff].toList()} m)") + "\n")
            for (bot in bots) line(sb, bot, (0 until n).map { play(endless, diff, bot, 1000L + it) }, endless, diff)
        }
        // chaque circuit : il doit rester jouable (Course, Moyen, joueur moyen)
        sb.append("\nPAR CIRCUIT — Course Moyen, joueur moyen puis enfant en Facile\n")
        for (th in THEMES) {
            sb.append("${th.key}\n")
            line(sb, bots[1], (0 until 60).map { play(false, 1, bots[1], 3000L + it, th) }, false, 1)
            line(sb, bots[0], (0 until 60).map { play(false, 0, bots[0], 3000L + it, th) }, false, 0)
        }
        println(sb)
        java.io.File("build/vroum_balance.txt").apply { parentFile?.mkdirs() }.writeText(sb.toString())
    }
}
