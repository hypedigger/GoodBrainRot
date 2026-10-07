package com.goodbrainrot.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tous les sons de l'app (piano, xylophone, victoire, erreur, véhicules…)
 * sont synthétisés programmatiquement : aucun fichier audio à embarquer.
 */
object SoundEngine {
    private const val SAMPLE_RATE = 44100
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val pcmCache = ConcurrentHashMap<String, ShortArray>()

    // Volumes par catégorie pour le shmup (0..1), réglables dans les options.
    @Volatile var shotVolume = 0.85f      // tirs du vaisseau
    @Volatile var explosionVolume = 0.85f // explosions (ennemis, boss)
    @Volatile var pickupVolume = 0.55f    // bruits des objets ramassés (masquaient la musique)

    /**
     * Génère les fréquences de `count` touches blanches consécutives.
     * L'octave de départ descend quand il y a beaucoup de touches, pour
     * garder un registre agréable (15 touches = Do4→Do6 comme un vrai piano).
     */
    fun whiteKeyFrequencies(count: Int): List<Double> {
        val pattern = listOf(0, 2, 4, 5, 7, 9, 11) // touches blanches en demi-tons depuis Do
        val startMidi = when {
            count <= 15 -> 60 // Do4
            count <= 29 -> 48 // Do3
            else -> 36        // Do2
        }
        return (0 until count).map { i ->
            val midi = startMidi + (i / 7) * 12 + pattern[i % 7]
            440.0 * 2.0.pow((midi - 69) / 12.0)
        }
    }

    // ------------------------------------------------------------------
    // Synthèse de base
    // ------------------------------------------------------------------

    /**
     * Timbre piano plus réaliste : partiels légèrement inharmoniques, chacun avec
     * sa propre décroissance (les aigus s'éteignent plus vite), + un court bruit
     * d'attaque (le « marteau ») et une longue résonance de la fondamentale.
     */
    private val pianoAmp = doubleArrayOf(1.0, 0.62, 0.40, 0.26, 0.16, 0.10, 0.06)
    private val pianoDecay = doubleArrayOf(1.6, 2.4, 3.2, 4.2, 5.6, 7.2, 9.0)

    private fun synthPiano(freq: Double, durationMs: Int): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val data = ShortArray(n)
        val rnd = java.util.Random(1234)
        val twoPi = 2 * PI
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var s = 0.0
            for (p in 1..pianoAmp.size) {
                // inharmonicité : les partiels montent un peu plus haut que les multiples exacts
                val fp = freq * p * (1.0 + 0.0007 * p * p)
                if (fp > SAMPLE_RATE / 2) break
                s += pianoAmp[p - 1] * exp(-pianoDecay[p - 1] * t) * sin(twoPi * fp * t)
            }
            // marteau : petit bruit sur les 8 premières ms
            if (t < 0.008) s += (rnd.nextDouble() * 2 - 1) * 0.6 * (1.0 - t / 0.008)
            val attack = min(1.0, t / 0.003)
            s *= attack * 0.30
            data[i] = (s * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    /** Timbre « xylophone » : brillant, métallique, décroissance rapide. */
    private fun synthXylo(freq: Double, durationMs: Int): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val data = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var s = sin(2 * PI * freq * t) +
                    0.50 * sin(2 * PI * freq * 3.0 * t) +
                    0.25 * sin(2 * PI * freq * 6.3 * t) // partiel inharmonique métallique
            val attack = min(1.0, t / 0.003)
            val release = exp(-9.0 * t)
            s = s * attack * release / 1.75
            data[i] = (s * 0.8 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    /** Balayage de fréquence (sirènes, avion, fusée…). */
    private fun synthSweep(fromHz: Double, toHz: Double, durationMs: Int): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val data = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / n
            val f = fromHz + (toHz - fromHz) * progress
            phase += 2 * PI * f / SAMPLE_RATE
            var s = sin(phase) + 0.3 * sin(phase * 2)
            val attack = min(1.0, t / 0.01)
            val fadeOut = min(1.0, (n - i).toDouble() / (SAMPLE_RATE * 0.05))
            s = s * attack * fadeOut / 1.3
            data[i] = (s * 0.75 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    /**
     * Klaxon d'alarme d'arcade : onde carrée (harsh) + léger vibrato, enveloppe attaque/relâche.
     * Utilisé pour les alarmes WARNING / CAUTION.
     */
    private fun synthBuzzer(freqHz: Double, durationMs: Int, vibHz: Double, harsh: Double): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val data = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val f = freqHz * (1.0 + 0.02 * sin(2 * PI * vibHz * t))
            val ph = (t * f) % 1.0
            val square = if (ph < 0.5) 1.0 else -1.0
            var s = square * harsh + sin(2 * PI * f * t) * (1.0 - harsh)
            val attack = min(1.0, t / 0.006)
            val fadeOut = min(1.0, (n - i).toDouble() / (SAMPLE_RATE * 0.03))
            s *= attack * fadeOut
            data[i] = (s * 0.5 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private val activeTracks = java.util.Collections.synchronizedSet(HashSet<AudioTrack>())

    /** Coupe tous les sons synthétisés en cours (app en arrière-plan). */
    fun stopAll() {
        synchronized(activeTracks) {
            for (t in activeTracks.toList()) {
                try { t.pause() } catch (_: Exception) {}
                try { t.flush() } catch (_: Exception) {}
            }
        }
        try { Sfx.stopAll() } catch (_: Exception) {}
    }

    /** Coupe tous les SFX synthétisés (ex. démo d'attract-mode derrière le tableau des scores). */
    @Volatile var muted = false
    /** App en arrière-plan : coupe TOUT SFX (le directeur du jeu tourne encore en coroutine). */
    @Volatile var appPaused = false

    private fun play(pcm: ShortArray, durationMs: Int, volume: Float) {
        if (muted || appPaused) return
        scope.launch {
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
            } catch (e: Exception) {
                return@launch
            }
            activeTracks.add(track)
            try {
                track.write(pcm, 0, pcm.size)
                track.setVolume(volume)
                track.play()
                delay(durationMs.toLong() + 100)
            } finally {
                activeTracks.remove(track)
                try { track.release() } catch (_: Exception) {}
            }
        }
    }

    private fun cached(key: String, builder: () -> ShortArray): ShortArray =
        pcmCache.getOrPut(key, builder)

    // ------------------------------------------------------------------
    // API publique
    // ------------------------------------------------------------------

    fun playTone(freq: Double, durationMs: Int = 700, volume: Float = 1f) {
        val pcm = cached("p:$freq:$durationMs") { synthPiano(freq, durationMs) }
        play(pcm, durationMs, volume)
    }

    fun playXylo(freq: Double, durationMs: Int = 600, volume: Float = 1f) {
        val pcm = cached("x:$freq:$durationMs") { synthXylo(freq, durationMs) }
        play(pcm, durationMs, volume)
    }

    fun playBuzzer(freqHz: Double, durationMs: Int, vibHz: Double, harsh: Double, volume: Float) {
        val pcm = cached("bz:$freqHz:$durationMs:$vibHz:$harsh") { synthBuzzer(freqHz, durationMs, vibHz, harsh) }
        play(pcm, durationMs, volume)
    }

    fun playSweep(fromHz: Double, toHz: Double, durationMs: Int, volume: Float = 0.8f) {
        val pcm = cached("s:$fromHz:$toHz:$durationMs") { synthSweep(fromHz, toHz, durationMs) }
        play(pcm, durationMs, volume)
    }

    /** Pop de bulle : blip descendant très court. */
    fun playPop() {
        playSweep(900.0 + Random.nextDouble(0.0, 300.0), 300.0, 90, 0.7f)
    }

    /** Éclatement zen : petit « plop » doux d'eau, descente sinusoïdale courte. */
    fun playZenPop() {
        val start = 560.0 + Random.nextDouble(-70.0, 90.0)
        playSweep(start, start * 0.55, 170, 0.4f)
    }

    /**
     * Pop de bulle agréable (« bloup » d'eau) : la hauteur monte vite puis
     * s'éteint en cloche douce. Hauteur aléatoire → varié et plaisant à répéter.
     */
    fun playBubblePop() {
        val durMs = 130
        val n = SAMPLE_RATE * durMs / 1000
        val base = 430.0 + Random.nextDouble(0.0, 360.0)
        val data = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val prog = i.toDouble() / n
            val f = base * (1.0 + 1.5 * prog) // glisse vers l'aigu
            phase += 2 * PI * f / SAMPLE_RATE
            var s = sin(phase) + 0.22 * sin(phase * 2)
            s *= sin(PI * prog) // enveloppe en cloche (attaque + extinction douces)
            data[i] = (s * 0.5 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        play(data, durMs, 0.6f)
    }

    /** Fracas : grosse branche / guitare qui se casse (bois sec + esquilles + cordes qui cèdent). */
    fun playSmash() {
        scope.launch {
            // Gros craquement de bois (sec, sourd) + thud grave
            play(synthNoiseBurst(95, 34.0, 0.22, Random.nextInt(9999)), 95, 1f)
            playSweep(125.0, 45.0, 230, 0.9f)
            // Esquilles / éclats de bois
            repeat(4) {
                delay(Random.nextLong(35, 95))
                val dur = Random.nextInt(40, 85)
                play(synthNoiseBurst(dur, 26.0, 0.28 + Random.nextDouble(0.0, 0.3), Random.nextInt(9999)), dur, 0.7f)
            }
            // Cordes qui cèdent : cluster grave dissonant, timbre métal
            for (f in listOf(82.41, 116.5, 155.0)) {
                playGuitar(f * (0.58 + Random.nextDouble(0.0, 0.12)), GuitarTimbre.METAL, 500, 0.55f)
            }
        }
    }

    /** Petit son « annuler » (dessin) : blip descendant doux. */
    fun playUndo() = playSweep(720.0, 340.0, 150, 0.4f)

    /** Son « effacer tout » (dessin) : balayage feutré. */
    fun playErase() {
        play(synthNoiseBurst(300, 7.0, 0.55, Random.nextInt(10000)), 300, 0.4f)
    }

    /** Shimmer magique (grosse bulle arc-en-ciel + licornes) : arpège cristallin. */
    fun playMagic() {
        scope.launch {
            for (f in listOf(659.25, 783.99, 987.77, 1318.5, 1567.98, 2093.0)) {
                playXylo(f, 600, 0.7f)
                delay(70)
            }
        }
    }

    /** Carillon : note cristalline aléatoire (pentatonique aiguë). */
    fun playChime() {
        val notes = listOf(1046.5, 1174.7, 1318.5, 1568.0, 1760.0, 2093.0)
        playXylo(notes.random(), 900, 0.8f)
    }

    /** Petit arpège montant Do-Mi-Sol-Do : son de victoire. */
    fun playVictory() {
        scope.launch {
            for (freq in listOf(523.25, 659.25, 783.99, 1046.50)) {
                playTone(freq, 350, 0.9f)
                delay(110)
            }
        }
    }

    /** Fanfare plus longue pour l'écran de notation de fin de série. */
    fun playFanfare() {
        scope.launch {
            for ((freq, ms) in listOf(523.25 to 160, 523.25 to 160, 587.33 to 160, 659.25 to 320,
                                      659.25 to 160, 587.33 to 160, 659.25 to 160, 783.99 to 480)) {
                playTone(freq, ms + 200, 0.9f)
                delay(ms.toLong())
            }
        }
    }

    /** Deux notes douces descendantes : son d'erreur non effrayant. */
    fun playError() {
        scope.launch {
            playTone(311.13, 400, 0.45f)
            delay(200)
            playTone(261.63, 500, 0.45f)
        }
    }

    /** Lance une séquence arbitraire de sons (utilisé par les véhicules). */
    fun sequence(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    // ------------------------------------------------------------------
    // Sons arcade / fun (bruit blanc + enveloppes)
    // ------------------------------------------------------------------

    private fun synthNoiseBurst(durationMs: Int, decay: Double, lowpass: Double, seed: Int): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val data = ShortArray(n)
        val rnd = java.util.Random(seed.toLong())
        var last = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val white = rnd.nextDouble() * 2 - 1
            last += (white - last) * lowpass // filtre passe-bas simple
            val env = exp(-decay * t)
            val s = last * env
            data[i] = (s * 0.9 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    /** Pétard / explosion de jeu d'enfant : claquement bruité. */
    fun playFirecracker() {
        scope.launch {
            val pcm = cached("fc") { synthNoiseBurst(450, 11.0, 0.5, 7) }
            play(pcm, 450, 1f)
            delay(40)
            // petits crépitements
            repeat(3) {
                val p = cached("crk$it") { synthNoiseBurst(120, 30.0, 0.7, 11 + it) }
                play(p, 120, 0.6f)
                delay(70)
            }
        }
    }

    /** Alerte « il reste 5 secondes » : bips urgents aigus. */
    fun playAlert() {
        scope.launch {
            repeat(2) {
                playTone(1318.5, 140, 0.7f)
                delay(170)
            }
        }
    }

    /** Coup sec (taper sur l'icône d'accueil). */
    fun playHit() {
        val pcm = cached("hit") { synthNoiseBurst(110, 40.0, 0.35, 3) }
        play(pcm, 110, 1f)
        playTone(110.0, 90, 0.5f)
    }

    /** Arpège épique gaming pour l'étoile spéciale (efface tout + bonus). */
    fun playSuperStar() {
        scope.launch {
            for ((freq, ms) in listOf(
                523.25 to 55, 659.25 to 55, 783.99 to 55, 1046.5 to 55, 1318.5 to 55
            )) {
                playXylo(freq, ms + 350, 0.95f)
                delay(ms.toLong())
            }
            delay(60)
            playXylo(2093.0, 700, 1f)
            playSweep(350.0, 3200.0, 350, 0.55f)
            delay(250)
            for (f in listOf(1046.5, 1318.5, 1568.0, 2093.0)) {
                playXylo(f, 350, 0.9f)
                delay(70)
            }
        }
    }

    /** Jingle « nouveau record battu » pendant la partie. */
    fun playNewHighScore() {
        scope.launch {
            playXylo(1046.5, 200, 0.9f)
            delay(120)
            playXylo(1318.5, 200, 0.9f)
            delay(120)
            playXylo(1568.0, 350, 0.9f)
        }
    }

    // ------------------------------------------------------------------
    // Shmup (Crimson Sky) : tirs, explosions, alarmes
    // ------------------------------------------------------------------

    /** Tir du vaisseau : échantillon laser doux (CC0 Kenney), sinon repli synthé. */
    fun playShoot() {
        if (Sfx.play("shoot", 0.28f * shotVolume, 0.95f + Random.nextFloat() * 0.12f)) return
        val pcm = cached("sh_laser") { synthSweepPcm(1750.0, 1120.0, 42) }
        play(pcm, 42, 0.2f * shotVolume)
    }

    /** Explosion. [big] = boss / mini-boss (boom sourd + souffle qui traîne). */
    fun playExplosion(big: Boolean = false) {
        val ev = explosionVolume
        if (big) {
            // boss : on SUPERPOSE plusieurs samples (grave + crunch + débris) décalés
            // → un boom riche et cinématique au lieu d'un « pouf » unique.
            if (Sfx.play("explosion_big", 1f * ev, 0.72f)) {
                Sfx.play("rumble", 0.9f * ev, 0.85f)
                scope.launch { delay(70); Sfx.play("explosion2", 0.8f * ev, 0.9f) }
                scope.launch { delay(180); Sfx.play("debris", 0.6f * ev, 1.0f) }
                scope.launch { delay(300); Sfx.play("explosion", 0.5f * ev, 0.8f) }
                return
            }
        } else {
            if (Sfx.play("explosion", 0.55f * ev, 0.9f + Random.nextFloat() * 0.2f)) return
        }
        scope.launch {
            if (big) {
                play(synthBoom(120.0, 40.0, 260), 260, 1f * ev)          // boom grave
                play(synthNoiseBurst(650, 4.5, 0.42, Random.nextInt(99999)), 650, 0.85f * ev) // souffle
                repeat(5) {
                    delay(Random.nextLong(45, 120))
                    val d = Random.nextInt(80, 170)
                    play(synthNoiseBurst(d, 13.0, 0.45, Random.nextInt(99999)), d, 0.6f * ev)
                }
            } else {
                play(synthBoom(220.0, 90.0, 130), 130, 0.6f * ev)
                play(synthNoiseBurst(180, 15.0, 0.45, Random.nextInt(99999)), 180, 0.55f * ev)
            }
        }
    }

    /** Petit « tic » cristallin (comptage du récap de fin de stage). */
    fun playTick() = playXylo(1568.0, 55, 0.35f * pickupVolume)

    /** Impulsion sinus grave descendante (corps d'une explosion). */
    private fun synthBoom(startHz: Double, endHz: Double, durMs: Int): ShortArray {
        val n = SAMPLE_RATE * durMs / 1000
        val data = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val f = endHz + (startHz - endHz) * exp(-16.0 * t)
            phase += 2 * PI * f / SAMPLE_RATE
            val s = kotlin.math.tanh(sin(phase) * 1.6) * exp(-9.0 * t)
            data[i] = (s * 0.9 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    /** Le vaisseau du joueur encaisse un coup. */
    fun playPlayerHit() {
        if (Sfx.play("hit", 0.85f, 0.9f + Random.nextFloat() * 0.14f)) return
        scope.launch {
            play(synthNoiseBurst(260, 11.0, 0.4, Random.nextInt(99999)), 260, 0.9f)
            playSweep(320.0, 80.0, 320, 0.7f)
        }
    }

    /** Envol du vaisseau (fin de niveau) : whoosh ascendant. */
    fun playWhoosh() {
        scope.launch {
            playSweep(300.0, 2600.0, 650, 0.5f)
            play(synthNoiseBurst(650, 4.0, 0.35, Random.nextInt(99999)), 650, 0.35f)
        }
    }

    /** Bombe / smash d'écran : gros boom sub + souffle montant. */
    fun playBomb() {
        if (Sfx.play("bomb", 1f, 0.85f)) return
        scope.launch {
            playSweep(140.0, 38.0, 520, 0.95f)
            play(synthNoiseBurst(520, 4.5, 0.5, Random.nextInt(99999)), 520, 0.85f)
            delay(40)
            playSweep(2000.0, 260.0, 340, 0.55f)
        }
    }

    /**
     * Alarme WARNING (vrai boss) : sirène « red alert » PROPRE — glissando sinus qui monte puis
     * descend en boucle, sur une nappe grave. Fini le klaxon carré strident.
     */
    fun playBossWarning() {
        scope.launch {
            Sfx.play("rumble", 0.6f, 0.6f)
            repeat(4) {
                playSweep(520.0, 1040.0, 300, 0.5f)   // montée douce
                delay(300)
                playSweep(1040.0, 520.0, 300, 0.5f)   // descente
                delay(300)
            }
        }
    }

    /** Alarme CAUTION (mini-boss) : sirène courte et plus douce, montante répétée. */
    fun playCaution() {
        scope.launch {
            repeat(3) {
                playSweep(680.0, 980.0, 230, 0.42f)
                delay(240)
            }
        }
    }

    /** Pièce ramassée. [tier] 0..3 (valeurs 1/5/10/100) → son différent. */
    fun playCoin(tier: Int) {
        when (tier) {
            0 -> playXylo(784.0, 120, 0.40f * pickupVolume)     // 1 : grave
            1 -> playXylo(1046.5, 120, 0.45f * pickupVolume)    // 5
            2 -> playXylo(1396.9, 130, 0.50f * pickupVolume)    // 10 : brillant
            else -> scope.launch {               // 100 : rare, petite fanfare cristalline
                for (f in listOf(1046.5, 1318.5, 1568.0, 2093.0)) { playXylo(f, 220, 0.6f * pickupVolume); delay(55) }
            }
        }
    }

    /** Clic d'interface (menus, sélection) : petit blip cristallin à deux notes. */
    fun playUi() {
        scope.launch {
            playXylo(880.0, 80, 0.5f)
            delay(38)
            playXylo(1318.5, 110, 0.5f)
        }
    }

    /** Bonus ramassé (arme améliorée, etc.). */
    fun playPowerUp() {
        if (Sfx.play("powerup", 0.7f * pickupVolume)) return
        scope.launch {
            for (f in listOf(523.25, 659.25, 783.99, 1046.5)) {
                playXylo(f, 200, 0.7f * pickupVolume)
                delay(52)
            }
        }
    }

    /** Déclenchement du mode « BREAK » (multiplicateur max) : éclat épique. */
    fun playBreak() = playSuperStar()

    /** Variante non cachée de [synthSweep] (les sons de tir doivent rester identiques). */
    private fun synthSweepPcm(fromHz: Double, toHz: Double, durationMs: Int): ShortArray =
        cached("sweepraw:$fromHz:$toHz:$durationMs") { synthSweep(fromHz, toHz, durationMs) }

    // ------------------------------------------------------------------
    // Vroum Vrouuuuuuum (jeu de voiture) : klaxons, écrasement, flaques, saut…
    // ------------------------------------------------------------------

    /** Klaxon propre à chaque véhicule (0..9 = ordre de la liste des bolides). */
    fun playHorn(variant: Int) {
        scope.launch {
            when (variant) {
                0 -> { playBuzzer(440.0, 160, 0.0, 0.5, 0.6f); delay(150); playBuzzer(554.0, 220, 0.0, 0.5, 0.6f) }   // voiture : pouet-pouet
                1 -> { playBuzzer(110.0, 420, 6.0, 0.8, 0.8f); playBuzzer(165.0, 420, 6.0, 0.6, 0.5f) }              // monster truck : grondement
                2 -> { repeat(2) { playSweep(620.0, 980.0, 260, 0.5f); delay(260); playSweep(980.0, 620.0, 260, 0.5f); delay(260) } } // pompiers : sirène
                3 -> { playBuzzer(180.0, 120, 0.0, 0.9, 0.5f); delay(90); playSweep(200.0, 900.0, 380, 0.55f) }      // moto : coup de gaz
                4 -> { play(synthBoom(90.0, 45.0, 380), 380, 0.9f); playBuzzer(98.0, 380, 0.0, 0.7, 0.6f) }          // char : corne grave
                5 -> { play(cached("roar1") { synthNoiseBurst(520, 5.0, 0.25, 41) }, 520, 0.8f); playSweep(260.0, 90.0, 520, 0.7f) } // dragon : rugissement
                6 -> { playSweep(700.0, 1400.0, 180, 0.45f); delay(160); playSweep(1400.0, 800.0, 260, 0.45f); delay(120)
                       for (f in listOf(1568.0, 2093.0, 2637.0)) { playXylo(f, 140, 0.4f); delay(45) } }             // licorne : hennissement + paillettes
                7 -> { play(cached("roar2") { synthNoiseBurst(650, 4.0, 0.18, 43) }, 650, 0.9f); playSweep(180.0, 60.0, 650, 0.8f) } // t-rex : gros rugissement
                8 -> { playSweep(220.0, 2400.0, 520, 0.5f); play(cached("rk") { synthNoiseBurst(520, 5.0, 0.4, 47) }, 520, 0.35f) } // fusée : whoosh
                else -> { playBuzzer(130.0, 150, 0.0, 0.8, 0.6f); delay(210); playBuzzer(130.0, 150, 0.0, 0.8, 0.6f) } // tracteur : pan-pan
            }
        }
    }

    /** Coup contre un véhicule ennemi : choc métallique (Kenney) + klaxon réel. */
    fun playBump() {
        if (Sfx.play("vv_bump", 0.9f, 0.9f + Random.nextFloat() * 0.2f)) {
            scope.launch { delay(90); Sfx.play("vv_horn", 0.6f, 0.8f) }
            return
        }
        scope.launch {
            play(cached("bump") { synthNoiseBurst(140, 30.0, 0.35, 51) }, 140, 0.7f)
            playBuzzer(330.0, 260, 0.0, 0.6, 0.55f)
        }
    }

    /** Véhicule écrasé en mode géant : gros impact mou (Kenney) + pop. */
    fun playCrush() {
        if (Sfx.play("vv_crush", 1f, 0.85f + Random.nextFloat() * 0.2f)) {
            scope.launch { delay(60); Sfx.play("vv_thud", 0.6f, 0.9f) }
            return
        }
        scope.launch {
            play(cached("crush") { synthNoiseBurst(220, 14.0, 0.5, 53) }, 220, 0.8f)
            playSweep(520.0, 140.0, 240, 0.6f)
        }
    }

    /** Flaque : vraie éclaboussure (Wikimedia Commons, domaine public). */
    fun playSplash() {
        if (Sfx.play("vv_splash", 0.9f, 0.95f + Random.nextFloat() * 0.1f)) return
        scope.launch {
            play(cached("splash") { synthNoiseBurst(260, 12.0, 0.8, 57) }, 260, 0.6f)
            delay(60); playBubblePop()
        }
    }

    fun playJump() { if (!Sfx.play("vv_jump", 0.7f, 1.1f)) playSweep(320.0, 980.0, 160, 0.5f) }
    fun playLand() { if (!Sfx.play("vv_land", 0.8f, 0.9f)) play(cached("land") { synthNoiseBurst(90, 45.0, 0.3, 59) }, 90, 0.6f) }
    fun playLaneTick() { if (!Sfx.play("vv_lane", 0.5f, 1f)) playSweep(500.0, 700.0, 60, 0.25f) }
    /** Glands / champignons / baies : petits sons de confirmation (Kenney). */
    fun playPick(tier: Int) { if (!Sfx.play("vv_pick${tier.coerceIn(1, 3)}", 0.7f, 1f)) playCoin(tier - 1) }
    fun playStarPick() { if (!Sfx.play("vv_star", 0.9f, 1f)) playSuperStar() }
    fun playVroumUi() { if (!Sfx.play("vv_ui", 0.7f, 1f)) playUi() }
    /** Klaxon générique réel (voiture) — repli quand le véhicule n'a pas de son propre. */
    fun playRealHorn(rate: Float = 1f): Boolean = Sfx.play("vv_horn", 0.9f, rate)
    // Échantillons de qualité (OpenGameArt CC0 / Kenney jingles CC0) pour Vroum
    fun playRoar(): Boolean = Sfx.play("vv_roar", 1f, 0.9f + Random.nextFloat() * 0.2f)
    fun playBoom() { if (!Sfx.play("vv_boom", 1f, 0.95f + Random.nextFloat() * 0.1f)) playExplosion(true) }
    fun playShot() { if (!Sfx.play("vv_shot", 0.5f, 1.1f + Random.nextFloat() * 0.3f)) playShoot() }
    fun playVvCoin() { if (!Sfx.play("vv_coin2", 0.8f, 1f)) playCoin(2) }
    fun playVvSuccess() { if (!Sfx.play("vv_success", 0.9f, 1f)) playPowerUp() }
    fun playVvGo() { if (!Sfx.play("vv_go", 1f, 1f)) playFanfare() }
    fun playVvFanfare() { if (!Sfx.play("vv_fanfare", 1f, 1f)) playFanfare() }
    fun playVvVictory() { if (!Sfx.play("vv_victory", 1f, 1f)) playVictory() }
    fun playVvYahoo() { if (!Sfx.play("vv_yahoo", 1f, 1f)) playMagic() }
    fun playVvGameOver() { if (!Sfx.play("vv_gameover", 1f, 1f)) playError() }
    fun playVvTick(pitch: Float = 1f) { if (!Sfx.play("vv_tick", 0.8f, pitch)) playXylo(880.0 * pitch, 120, 0.6f) }
    fun playStall() { if (!Sfx.play("vv_stall", 0.9f, 1f)) playSputter() }
    /** Ronronnement moteur en boucle ; renvoie l'id de flux à ajuster avec [Sfx.setStreamRate]. */
    fun startEngine(): Int = Sfx.loop("vv_engine", 0.28f, 0.9f)

    /** Plus d'essence : le moteur toussote et s'éteint. */
    fun playSputter() {
        scope.launch {
            for (i in 0 until 4) {
                playBuzzer(150.0 - i * 22.0, 120, 0.0, 0.9, 0.55f)
                delay(230L + i * 70L)
            }
            playSweep(160.0, 50.0, 500, 0.4f)
        }
    }

    /** Flèche turbo : accélération. */
    fun playTurbo() {
        if (Sfx.play("vv_turbo", 0.9f, 1f)) return
        scope.launch {
            playSweep(200.0, 1600.0, 420, 0.55f)
            play(cached("turbo") { synthNoiseBurst(420, 6.0, 0.4, 61) }, 420, 0.4f)
        }
    }

    /** Bidon d'essence : glouglou qui remplit. */
    fun playFuel() {
        if (Sfx.play("vv_fuel", 0.9f, 1f)) { scope.launch { delay(180); Sfx.play("vv_fuel", 0.7f, 1.25f) }; return }
        scope.launch {
            for (i in 0 until 3) { playSweep(300.0 + i * 90.0, 620.0 + i * 90.0, 110, 0.5f); delay(120) }
            playXylo(1318.5, 200, 0.5f)
        }
    }

    /** Bruit de prout synthétisé, varié selon [variant] (0..N). */
    fun playFart(variant: Int) {
        scope.launch {
            val n = SAMPLE_RATE * 600 / 1000
            val pcm = cached("fart$variant") {
                val data = ShortArray(n)
                val rnd = java.util.Random(variant * 97L + 13)
                val baseHz = 70.0 + variant * 18.0
                val wobbleHz = 7.0 + variant * 2.5
                val wobbleDepth = 0.35 + (variant % 3) * 0.12
                var phase = 0.0
                for (i in 0 until n) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val prog = i.toDouble() / n
                    // pitch qui retombe + vibrato (le « flap »)
                    val f = baseHz * (1.0 - 0.4 * prog) * (1.0 + wobbleDepth * sin(2 * PI * wobbleHz * t))
                    phase += 2 * PI * f / SAMPLE_RATE
                    // onde « buzz » riche en harmoniques + un peu de souffle
                    var s = sin(phase)
                    s += 0.5 * sin(phase * 2)
                    s += 0.3 * sin(phase * 3)
                    s = kotlin.math.tanh(s * 1.8) // saturation = côté graveleux
                    s += (rnd.nextDouble() * 2 - 1) * 0.08
                    val env = min(1.0, t / 0.02) * (1.0 - prog * 0.2)
                    data[i] = (s * env * 0.55 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                }
                data
            }
            play(pcm, 600, 1f)
        }
    }

    const val FART_VARIANTS = 6

    // ------------------------------------------------------------------
    // Guitare (synthèse Karplus-Strong : corde pincée)
    // ------------------------------------------------------------------

    /** Accordage standard d'une guitare (mi-la-ré-sol-si-mi), grave -> aigu. */
    val GUITAR_STRINGS = listOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)

    /**
     * Corde pincée (Karplus-Strong) paramétrée par un [GuitarTimbre] :
     * - damping : longueur du sustain (proche de 1 = long)
     * - brightenPasses : lissages initiaux (plus = plus doux/feutré)
     * - distortion : saturation de sortie (1 = clean, >1 = crunch/métal)
     */
    private fun synthGuitar(freq: Double, durationMs: Int, timbre: GuitarTimbre): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val p = (SAMPLE_RATE / freq).toInt().coerceAtLeast(2)
        val buf = DoubleArray(p) { Random.nextDouble(-1.0, 1.0) }
        val out = ShortArray(n)
        var idx = 0
        repeat(timbre.brightenPasses) {
            for (k in 0 until p) buf[k] = (buf[k] + buf[(k + 1) % p]) * 0.5
        }
        val dist = timbre.distortion
        val norm = if (dist > 1.0) kotlin.math.tanh(dist) else 1.0
        for (i in 0 until n) {
            val cur = buf[idx]
            var s = cur
            if (dist > 1.0) s = kotlin.math.tanh(s * dist) / norm
            out[i] = (s * 0.5 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            val nextIdx = (idx + 1) % p
            buf[idx] = (cur + buf[nextIdx]) * 0.5 * timbre.damping
            idx = nextIdx
        }
        return out
    }

    fun playGuitar(
        freq: Double,
        timbre: GuitarTimbre = GuitarTimbre.FOLK,
        durationMs: Int = 1300,
        volume: Float = 1f
    ) {
        val key = "g:$freq:$durationMs:${timbre.id}"
        val pcm = cached(key) { synthGuitar(freq, durationMs, timbre) }
        play(pcm, durationMs, volume)
        if (timbre.echo) {
            scope.launch {
                delay(190); play(pcm, durationMs, volume * 0.5f)
                delay(190); play(pcm, durationMs, volume * 0.28f)
            }
        }
    }

    /** Strum de guitare : joue les fréquences en séquence rapide (grave→aigu ou inverse). */
    fun playStrum(freqs: List<Double>, timbre: GuitarTimbre, goingDown: Boolean = true) {
        scope.launch {
            val ordered = if (goingDown) freqs else freqs.reversed()
            for (freq in ordered) {
                playGuitar(freq, timbre, 1200)
                delay(28)
            }
        }
    }

    // ------------------------------------------------------------------
    // Batterie 808/909 synthétisée pour les MPC
    // ------------------------------------------------------------------

    /** Kick 808 : sinus avec enveloppe de hauteur descendante + click d'attaque. */
    private fun synthKick(durMs: Int, startHz: Double, endHz: Double, decay: Double): ShortArray {
        val n = SAMPLE_RATE * durMs / 1000
        val data = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val f = endHz + (startHz - endHz) * exp(-40.0 * t)
            phase += 2 * PI * f / SAMPLE_RATE
            var s = sin(phase) * exp(-decay * t)
            if (t < 0.006) s += sin(2 * PI * 1100 * t) * (1.0 - t / 0.006) * 0.5 // click
            data[i] = (s * 0.95 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    /** Bruit filtré + décroissance (charley, snare, cymbales). highpass : brillance. */
    private fun synthNoise(durMs: Int, decay: Double, highpass: Boolean, seed: Int): ShortArray {
        val n = SAMPLE_RATE * durMs / 1000
        val data = ShortArray(n)
        val rnd = java.util.Random(seed.toLong())
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val white = rnd.nextDouble() * 2 - 1
            lp += (white - lp) * 0.45
            val v = if (highpass) white - lp else lp
            data[i] = (v * exp(-decay * t) * 0.9 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    /** Snare 909 : corps tonal + souffle brillant. */
    private fun synthSnare(): ShortArray {
        val durMs = 200
        val n = SAMPLE_RATE * durMs / 1000
        val data = ShortArray(n)
        val rnd = java.util.Random(77)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val tone = (sin(2 * PI * 175 * t) + 0.5 * sin(2 * PI * 330 * t)) * exp(-24.0 * t)
            val white = rnd.nextDouble() * 2 - 1
            lp += (white - lp) * 0.5
            val noise = (white - lp) * exp(-16.0 * t)
            val s = tone * 0.5 + noise * 0.7
            data[i] = (s * 0.85 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    /** Clap : 3 rebonds rapides + traîne diffuse. */
    private fun synthClap(): ShortArray {
        val durMs = 320
        val n = SAMPLE_RATE * durMs / 1000
        val data = ShortArray(n)
        val rnd = java.util.Random(91)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val white = rnd.nextDouble() * 2 - 1
            lp += (white - lp) * 0.5
            val hp = white - lp
            // enveloppe : 3 impulsions puis traîne
            val env = when {
                t < 0.01 -> 1.0
                t < 0.02 -> if ((t - 0.01) < 0.004) 1.0 else 0.3
                t < 0.035 -> if ((t - 0.02) < 0.004) 1.0 else 0.3
                else -> exp(-22.0 * (t - 0.035))
            }
            data[i] = (hp * env * 0.8 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    /** Cowbell 808 : deux carrés métalliques. */
    private fun synthCowbell(): ShortArray {
        val durMs = 220
        val n = SAMPLE_RATE * durMs / 1000
        val data = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            fun sq(f: Double) = if (sin(2 * PI * f * t) >= 0) 1.0 else -1.0
            val s = (sq(540.0) * 0.5 + sq(845.0) * 0.5) * exp(-10.0 * t)
            data[i] = (s * 0.55 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    fun playDrum(kind: DrumKind) {
        when (kind) {
            DrumKind.KICK -> play(cached("d_kick") { synthKick(500, 115.0, 48.0, 6.0) }, 500, 1f)
            DrumKind.BASS -> play(cached("d_808") { synthKick(700, 90.0, 42.0, 3.2) }, 700, 1f) // 808 sub long
            DrumKind.SNARE -> play(cached("d_snare") { synthSnare() }, 200, 0.9f)
            DrumKind.RIM -> play(cached("d_rim") { synthNoise(45, 70.0, true, 31) }, 45, 0.8f)
            DrumKind.HAT -> play(cached("d_hat") { synthNoise(55, 55.0, true, 9) }, 55, 0.55f)
            DrumKind.OPENHAT -> play(cached("d_ohat") { synthNoise(320, 9.0, true, 13) }, 320, 0.5f)
            DrumKind.CLAP -> play(cached("d_clap") { synthClap() }, 320, 0.85f)
            DrumKind.TOM -> play(cached("d_tom") { synthKick(300, 180.0, 95.0, 9.0) }, 300, 0.85f)
            DrumKind.COWBELL -> play(cached("d_cow") { synthCowbell() }, 220, 0.6f)
            DrumKind.RIDE -> play(cached("d_ride") { synthNoise(500, 5.0, true, 41) }, 500, 0.4f)
            DrumKind.CRASH -> play(cached("d_crash") { synthNoise(800, 3.2, true, 43) }, 800, 0.5f)
            DrumKind.ZAP -> playSweep(1400.0, 200.0, 160, 0.7f)
            DrumKind.STAB -> { playTone(261.63, 220, 0.5f); playTone(311.13, 220, 0.5f); playTone(392.0, 220, 0.5f) }
            DrumKind.SWEEP -> playSweep(200.0, 2000.0, 420, 0.5f)
            DrumKind.LASER -> playSweep(2200.0, 300.0, 200, 0.6f)
            DrumKind.BLIP -> playXylo(880.0, 160, 0.7f)
            DrumKind.SHAKER -> play(cached("d_shaker") { synthNoiseBurst(70, 32.0, 0.55, 71) }, 70, 0.45f)
            DrumKind.WOODBLOCK -> play(
                cached("d_woodblock") {
                    val n = SAMPLE_RATE * 55 / 1000
                    ShortArray(n) { i ->
                        val t = i.toDouble() / SAMPLE_RATE
                        (sin(2 * PI * 2050 * t) * exp(-85.0 * t) * 0.8 * Short.MAX_VALUE)
                            .toInt().coerceIn(-32767, 32767).toShort()
                    }
                }, 55, 0.85f
            )
            DrumKind.CLAVES -> {
                playXylo(2090.0, 60, 0.8f)
                scope.launch { delay(18); playXylo(2350.0, 45, 0.5f) }
            }
            DrumKind.VINYL -> scope.launch {
                playSweep(700.0, 1500.0, 55, 0.75f)
                delay(28)
                playSweep(1500.0, 200.0, 110, 0.55f)
            }
        }
    }
}

enum class DrumKind {
    KICK, SNARE, HAT, OPENHAT, CLAP, TOM, RIM, COWBELL, ZAP, STAB, BASS, SWEEP, LASER, BLIP, RIDE, CRASH,
    SHAKER, WOODBLOCK, CLAVES, VINYL
}

/** Timbre de guitare (preset de synthèse). */
data class GuitarTimbre(
    val id: String,
    val damping: Double,
    val brightenPasses: Int,
    val distortion: Double,
    val echo: Boolean
) {
    companion object {
        val FOLK = GuitarTimbre("folk", 0.997, 2, 1.0, false)
        val POP = GuitarTimbre("pop", 0.996, 1, 1.0, false)
        val BLUES = GuitarTimbre("blues", 0.9965, 2, 1.8, false)
        val ROCK = GuitarTimbre("rock", 0.995, 1, 3.0, false)
        val METAL = GuitarTimbre("metal", 0.991, 0, 4.5, false)
        val PSYCHE = GuitarTimbre("psyche", 0.997, 1, 1.8, true)
        val UKULELE = GuitarTimbre("ukulele", 0.9935, 3, 1.0, false)
        val BASS = GuitarTimbre("bass", 0.9985, 3, 1.2, false)
    }
}
