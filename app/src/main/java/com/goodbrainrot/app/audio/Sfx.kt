package com.goodbrainrot.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.goodbrainrot.app.R

/**
 * Lecteur d'effets échantillonnés (fichiers .ogg libres de droit, CC0 – packs
 * Kenney « Sci-Fi Sounds » / « Impact Sounds ») pour le shmup Crimson Sky.
 *
 * Utilise un [SoundPool] : les sons courts sont décodés une fois puis rejoués
 * quasi sans latence, plusieurs fois en simultané. Si l'init échoue ou n'a pas
 * eu lieu, [play] renvoie false et l'appelant retombe sur la synthèse.
 */
object Sfx {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val ready = HashSet<Int>()

    // Clés logiques → ressource res/raw.
    private val RES = mapOf(
        "shoot" to R.raw.sfx_shoot,
        "explosion" to R.raw.sfx_explosion,
        "explosion2" to R.raw.sfx_explosion2,
        "explosion_big" to R.raw.sfx_explosion_big,
        "rumble" to R.raw.sfx_rumble,
        "debris" to R.raw.sfx_debris,
        "hit" to R.raw.sfx_hit,
        "bomb" to R.raw.sfx_bomb,
        "powerup" to R.raw.sfx_powerup,
        // Vroum Vrouuuuuuum (Kenney Impact/Interface Sounds CC0, Wikimedia Commons)
        "vv_horn" to R.raw.sfx_vv_horn,
        "vv_splash" to R.raw.sfx_vv_splash,
        "vv_bump" to R.raw.sfx_vv_bump,
        "vv_crush" to R.raw.sfx_vv_crush,
        "vv_land" to R.raw.sfx_vv_land,
        "vv_pick1" to R.raw.sfx_vv_pick1,
        "vv_pick2" to R.raw.sfx_vv_pick2,
        "vv_pick3" to R.raw.sfx_vv_pick3,
        "vv_fuel" to R.raw.sfx_vv_fuel,
        "vv_star" to R.raw.sfx_vv_star,
        "vv_lane" to R.raw.sfx_vv_lane,
        "vv_jump" to R.raw.sfx_vv_jump,
        "vv_turbo" to R.raw.sfx_vv_turbo,
        "vv_ui" to R.raw.sfx_vv_ui,
        "vv_thud" to R.raw.sfx_vv_thud,
        "vv_stall" to R.raw.sfx_vv_stall,
        "vv_engine" to R.raw.sfx_vv_engine,
        "vv_accel" to R.raw.sfx_vv_accel,
        "vv_boom" to R.raw.sfx_vv_boom,
        "vv_shot" to R.raw.sfx_vv_shot,
        "vv_coin2" to R.raw.sfx_vv_coin2,
        "vv_success" to R.raw.sfx_vv_success,
        "vv_roar" to R.raw.sfx_vv_roar,
        "vv_go" to R.raw.sfx_vv_go,
        "vv_fanfare" to R.raw.sfx_vv_fanfare,
        "vv_victory" to R.raw.sfx_vv_victory,
        "vv_yahoo" to R.raw.sfx_vv_yahoo,
        "vv_gameover" to R.raw.sfx_vv_gameover,
        "vv_tick" to R.raw.sfx_vv_tick,
        "vv_applause" to R.raw.sfx_vv_applause,
        "vv_fanfare2" to R.raw.sfx_vv_fanfare2,
        // Jardinier Zen
        "gz_zap" to R.raw.sfx_gz_zap,
        "gz_bamboo" to R.raw.sfx_gz_bamboo,
        "gz_slowmo" to R.raw.sfx_gz_slowmo,
        "gz_chainsaw" to R.raw.sfx_gz_chainsaw,
        "gz_trimmer" to R.raw.sfx_gz_trimmer,
        "gz_clipper" to R.raw.sfx_gz_clipper,
        "gz_snip" to R.raw.sfx_gz_snip,
        "gz_snip2" to R.raw.sfx_gz_snip2,
        "gz_snap" to R.raw.sfx_gz_snap,
        "gz_rustle" to R.raw.sfx_gz_rustle,
        "gz_thud" to R.raw.sfx_gz_thud,
        "gz_coin" to R.raw.sfx_gz_coin,
        "gz_buy" to R.raw.sfx_gz_buy,
        "gz_error" to R.raw.sfx_gz_error,
        "gz_tick" to R.raw.sfx_gz_tick,
    )

    fun init(context: Context) {
        if (pool != null) return
        val p = try {
            SoundPool.Builder()
                .setMaxStreams(12)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
        } catch (_: Exception) { return }
        p.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(ready) { ready.add(sampleId) }
        }
        val app = context.applicationContext
        for ((key, res) in RES) {
            try {
                val id = p.load(app, res, 1)
                ids[key] = id
            } catch (_: Exception) {}
        }
        pool = p
    }

    fun has(key: String): Boolean = ids.containsKey(key)

    /**
     * Joue l'échantillon [key]. [rate] = variation de hauteur (0.5..2.0) pour
     * éviter la répétition mécanique. Renvoie false si indisponible.
     */
    fun play(key: String, volume: Float = 1f, rate: Float = 1f): Boolean {
        if (SoundEngine.muted || SoundEngine.appPaused) return false
        val p = pool ?: return false
        val id = ids[key] ?: return false
        synchronized(ready) { if (!ready.contains(id)) return false }
        return try {
            p.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
            true
        } catch (_: Exception) { false }
    }

    /** Joue [key] EN BOUCLE (moteur d'outil) ; renvoie l'id de flux (0 si indisponible). */
    fun loop(key: String, volume: Float = 1f, rate: Float = 1f): Int {
        if (SoundEngine.muted || SoundEngine.appPaused) return 0
        val p = pool ?: return 0
        val id = ids[key] ?: return 0
        synchronized(ready) { if (!ready.contains(id)) return 0 }
        return try { p.play(id, volume, volume, 1, -1, rate.coerceIn(0.5f, 2f)) } catch (_: Exception) { 0 }
    }
    fun stopStream(streamId: Int) { if (streamId != 0) try { pool?.stop(streamId) } catch (_: Exception) {} }
    fun setStreamRate(streamId: Int, rate: Float) { if (streamId != 0) try { pool?.setRate(streamId, rate.coerceIn(0.5f, 2f)) } catch (_: Exception) {} }

    /** Met en pause tous les échantillons en cours (app en arrière-plan). */
    fun stopAll() { try { pool?.autoPause() } catch (_: Exception) {} }
}
