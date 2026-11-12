package com.goodbrainrot.app.ui

import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Banc d'équilibrage de Vroum : des robots de trois niveaux (enfant de 5 ans, joueur moyen, expert) jouent des centaines
 * de parties sur le vrai modèle de jeu ; le tableau imprimé sert à régler chrono, étoiles et essence (VTUNES,
 * VROUM_STAR_SCORE, VROUM_STAR_DIST). Lancer : gradlew testDebugUnitTest --tests "*VroumBalanceTest*" -i
 */
class VroumBalanceTest {
    /** [react] : secondes entre deux décisions ; [look] : anticipation (s) ; [err] : part de décisions ratées ; [jumps] : sait sauter ; [daring] : cherche le frôlement. */
    private class Bot(val name: String, val react: Float, val look: Float, val err: Float, val jumps: Float, val daring: Boolean)
    private val bots = listOf(
        Bot("enfant", 0.75f, 0.9f, 0.25f, 0.15f, false),
        Bot("moyen", 0.30f, 1.0f, 0.06f, 0.85f, false),
        Bot("expert", 0.10f, 1.6f, 0.01f, 1.0f, true),
    )

    private class Run(val cleared: Boolean, val time: Float, val score: Int, val dist: Float, val bumps: Int, val nears: Int, val combo: Int, val avgSpeed: Float)

    private fun play(endless: Boolean, diff: Int, bot: Bot, seed: Long): Run {
        val g = VroumGame(endless, 0, diff, THEMES[0], "T", "C", seed = seed)
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
        return Run(g.cleared, g.raceT, g.score, g.dist, g.bumpCount, g.nearCount, g.bestCombo, if (g.raceT > 0f) g.dist / g.raceT else 0f)
    }

    private fun decide(g: VroumGame, bot: Bot, r: Random) {
        if (r.nextFloat() < bot.err) { if (r.nextBoolean()) g.moveLane(if (r.nextBoolean()) 1 else -1); return }
        val sp = g.mps.coerceAtLeast(1f)
        val pz = VroumGame.PLAYER_Z
        val n = g.lanes
        val danger = FloatArray(n) { 99f }      // secondes avant le premier danger de la voie
        val reward = FloatArray(n)
        val giant = g.giant > 0 || g.superT > 0f
        for (e in g.enemies) {
            if (e.crushed >= 0f || e.lane !in 0 until n) continue
            val gap = e.z - pz; val closing = sp - e.mps
            if (gap < -1.6f || closing <= 0.5f) continue
            val t = ((gap - 2f) / closing).coerceAtLeast(0f)
            if (giant || g.cannonT > 0f) { if (t < bot.look * 2f) reward[e.lane] += 100f } else if (t < danger[e.lane]) danger[e.lane] = t
        }
        var wallAhead = 99f
        val hazRow = HashMap<Int, Int>()
        for (p in g.picks) {
            if (p.lane !in 0 until n) continue
            val t = (p.z - pz) / sp
            if (t < -0.05f) continue
            when (p.type) {
                VItem.HOLE, VItem.LOG -> { if (!giant && t < danger[p.lane]) danger[p.lane] = t; val k = (p.z / 3f).toInt(); hazRow[k] = (hazRow[k] ?: 0) + 1 }
                VItem.PUDDLE -> if (!giant && t < bot.look) reward[p.lane] -= 60f
                VItem.FUEL -> if (t < bot.look * 2.5f) reward[p.lane] += if (g.fuelOn) (if (g.fuel < 70f) 600f else 80f) else 0f
                VItem.STAR, VItem.SUPERBOOST -> if (t < bot.look * 2.5f) reward[p.lane] += 300f
                VItem.CANNON, VItem.PASSENGER -> if (t < bot.look * 2.5f) reward[p.lane] += 180f
                VItem.TURBO -> if (t < bot.look * 1.5f) reward[p.lane] += 45f
                VItem.ACORN -> if (t < bot.look * 1.5f) reward[p.lane] += 10f
                VItem.MUSHROOM -> if (t < bot.look * 1.5f) reward[p.lane] += 20f
                VItem.BERRY -> if (t < bot.look * 1.5f) reward[p.lane] += 30f
            }
        }
        val cur = g.lane.coerceIn(0, n - 1)
        // l'expert reste volontairement derrière une voiture jusqu'au dernier moment (frôlement) si une sortie est libre
        val nearT = g.tune.nearT
        fun blocked(l: Int, horizon: Float) = danger[l] < horizon
        val exitFree = (cur > 0 && !blocked(cur - 1, 0.9f)) || (cur < n - 1 && !blocked(cur + 1, 0.9f))
        val horizonCur = if (bot.daring && exitFree) nearT * 0.7f else bot.look
        var best = cur; var bestVal = -1e9f
        for (l in 0 until n) {
            val h = if (l == cur) horizonCur else bot.look
            var v = reward[l] - abs(l - cur) * 8f
            if (blocked(l, h)) v -= 1000f * (1f + (h - danger[l]))
            // on ne traverse pas une voie bouchée tout de suite
            if (abs(l - cur) == 2 && blocked((l + cur) / 2, 0.35f)) v -= 800f
            if (v > bestVal) { bestVal = v; best = l }
        }
        // saut : danger imminent dans ma voie et aucune voie libre, ou obstacle à sauter juste devant
        val imminent = danger[cur] < 0.42f
        if (imminent && (best == cur || blocked(best, 0.5f)) && g.jumpCd <= 0f && !g.jumping && r.nextFloat() < bot.jumps) { g.jump(); return }
        if (best != cur) { g.moveLane(if (best > cur) 1 else -1); if (bot.react < 0.2f && abs(best - cur) == 2) g.moveLane(if (best > cur) 1 else -1) }
    }

    private fun pct(xs: List<Float>, p: Float): Float { val s = xs.sorted(); return s[((s.size - 1) * p).toInt()] }

    @Test
    fun balance() {
        val n = 150
        val sb = StringBuilder("\n=== VROUM — équilibrage ($n parties par case) ===\n")
        for (endless in listOf(false, true)) for (diff in 0..2) {
            sb.append("\n${if (endless) "ARCADE" else "COURSE"} ${listOf("Facile", "Moyen", "Difficile")[diff]}" +
                (if (!endless) "  (chrono ${VTUNES[diff].timeLimit.toInt()} s, étoiles ${VROUM_STAR_SCORE[diff].toList()})" else "  (étoiles ${VROUM_STAR_DIST[diff].toList()} m)") + "\n")
            for (bot in bots) {
                val runs = (0 until n).map { play(endless, diff, bot, 1000L + it) }
                val stars = runs.map { vroumStarCount(endless, it.cleared, it.score, it.dist.toInt(), diff) }
                val sc = runs.map { it.score.toFloat() }; val ds = runs.map { it.dist }; val tm = runs.map { it.time }
                sb.append(String.format(java.util.Locale.US,
                    "  %-7s arrivé %3d%%  temps %5.1f/%5.1f/%5.1f s  v.moy %4.1f m/s  score %5.0f/%5.0f/%5.0f  dist %5.0f/%5.0f/%5.0f  chocs %4.1f  frôlés %4.1f  série %3.0f  ★ %d%%/%d%%/%d%%/%d%%\n",
                    bot.name, runs.count { it.cleared } * 100 / n, pct(tm, 0.1f), pct(tm, 0.5f), pct(tm, 0.9f), runs.map { it.avgSpeed }.average(),
                    pct(sc, 0.1f), pct(sc, 0.5f), pct(sc, 0.9f), pct(ds, 0.1f), pct(ds, 0.5f), pct(ds, 0.9f),
                    runs.map { it.bumps }.average(), runs.map { it.nears }.average(), runs.map { it.combo }.average(),
                    stars.count { it == 0 } * 100 / n, stars.count { it == 1 } * 100 / n, stars.count { it == 2 } * 100 / n, stars.count { it == 3 } * 100 / n))
            }
        }
        println(sb)
        java.io.File("build/vroum_balance.txt").apply { parentFile?.mkdirs() }.writeText(sb.toString())
    }
}
