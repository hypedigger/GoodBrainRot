package com.goodbrainrot.app.ui

import com.goodbrainrot.app.R

// ---------------------------------------------------------------------------------------------------------------------
// GRANDS ÉVÉNEMENTS : une fois par course (vers le milieu), chaque circuit a son moment « waouh ». Un géant survole la
// route, la franchit d'un bond ou la traverse à pied en faisant trembler l'écran, et sème une traînée de gros bonus ;
// sur le volcan et la banquise, c'est une pluie de projectiles annoncés par un disque rouge sur la voie visée.
// ---------------------------------------------------------------------------------------------------------------------

/**
 * [kind] : 0 = survol (il traverse le ciel), 1 = bond en arc par-dessus la route, 2 = marcheur géant, 3 = pluie de projectiles.
 * [tex] : sprite `vv_set_<clé>` (vu de profil, tourné vers la droite) ; [width] : largeur en mètres ; [r],[g],[b] : teinte des projectiles.
 */
internal class VSetDef(val tex: String, val kind: Int, val labelRes: Int, val width: Float, val r: Float = 1f, val g: Float = 1f, val b: Float = 1f)

private val BALLOON = VSetDef("vv_set_balloon", 0, R.string.vroum_set_balloon, 9f)
private val BLIMP = VSetDef("vv_set_blimp", 0, R.string.vroum_set_blimp, 20f)
private val WHALE = VSetDef("vv_set_whale", 1, R.string.vroum_set_whale, 17f)
private val UFO = VSetDef("vv_set_ufo", 0, R.string.vroum_set_ufo, 12f)

internal val VROUM_SETS: Map<String, VSetDef> = mapOf(
    "forest" to BALLOON, "night" to VSetDef("vv_set_owl", 0, R.string.vroum_set_owl, 13f),
    "volcano" to VSetDef("", 3, R.string.vroum_set_lava, 0f, 1f, 0.45f, 0.1f),
    "tokyo" to VSetDef("vv_set_dragon", 0, R.string.vroum_set_dragon, 20f), "snow" to VSetDef("vv_set_yeti", 2, R.string.vroum_set_yeti, 12f),
    "beach" to WHALE, "desert" to VSetDef("vv_set_worm", 1, R.string.vroum_set_worm, 17f), "city" to BLIMP, "space" to UFO, "ocean" to WHALE,
    "candy" to BALLOON, "farm" to VSetDef("vv_set_biplane", 0, R.string.vroum_set_biplane, 11f),
    "jungle" to VSetDef("vv_set_gorilla", 2, R.string.vroum_set_gorilla, 13f), "dino" to VSetDef("vv_set_trex", 2, R.string.vroum_set_trex, 16f),
    "spooky" to VSetDef("vv_set_witch", 0, R.string.vroum_set_witch, 8f), "circuit" to VSetDef("vv_set_jets", 0, R.string.vroum_set_jets, 18f),
    "ice" to VSetDef("", 3, R.string.vroum_set_avalanche, 0f, 0.93f, 0.97f, 1f), "works" to BLIMP, "sky" to BALLOON, "cyber" to UFO,
)

/** Projectile de la pluie : il tombe [FALL] secondes sur la voie [lane], à [z] (qui se rapproche de nous comme la route). */
internal class VBomb(val lane: Int, var z: Float) { var t = 0f
    companion object { const val FALL = 1.5f } }
