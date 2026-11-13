package com.goodbrainrot.app.ui

import com.goodbrainrot.app.R

// ===========================================================================
// Vroum Vrouuuuuuum — les 20 CIRCUITS. Chacun a son décor, ses objets, et au moins une SPÉCIFICITÉ de jeu
// (VTrait) : gravité, trafic, traverseurs, tremplins, glace, vent, tempête, fantômes, portiques laser…
// ===========================================================================

/** Couleur (r,g,b) pour les thèmes. */
internal class C3(val r: Float, val g: Float, val b: Float)

/**
 * Spécificité de jeu d'un circuit. [jumpMul] : durée des sauts (faible gravité) ; [trafficMul] : densité des voitures
 * dans notre sens ; [oncomingMul] : part du trafic en face conservée (0 = aucun) ; [coinMul] : valeur des pièces ;
 * [hazards] : trous, troncs et flaques ; [ramps] : fréquence des tremplins ; [puddles] : flaques en plus ; [ice] : le
 * changement de voie part en retard et glisse ; [wind] : rafales qui poussent d'une voie ; [sandstorm] : visibilité qui
 * tombe par vagues ; [boulders] : des rochers dévalent vers nous ; [ghosts] : voitures fantômes à traverser ; [gates] :
 * portiques laser à sauter ; [rivalsExtra] : rivaux en plus ; [ceilMul] : vitesse maximale ; [herd] : taille des
 * troupeaux qui traversent ; [rainbow] : route arc-en-ciel.
 */
internal class VTrait(
    val jumpMul: Float = 1f, val trafficMul: Float = 1f, val oncomingMul: Float = 1f, val coinMul: Int = 1, val hazards: Boolean = true,
    val ramps: Float = 0f, val puddles: Float = 0f, val ice: Boolean = false, val wind: Boolean = false, val sandstorm: Boolean = false,
    val boulders: Boolean = false, val ghosts: Boolean = false, val gates: Boolean = false, val rivalsExtra: Int = 0, val ceilMul: Float = 1f,
    val herd: Int = 1, val rainbow: Boolean = false
)

/**
 * Thème de circuit : ciel (4 bandes du haut vers l'horizon), astre, relief (montagnes ou skyline de gratte-ciel),
 * couleurs du sol / de la route, décor du bord de route (clé sprite + taille), objets bonus locaux,
 * particules d'ambiance (0 aucune, 1 lucioles, 2 braises, 3 pétales, 4 neige, 5 pluie, 6 bulles, 7 sable, 8 poussière
 * d'étoiles, 9 confettis), obstacles propres, traverseurs, spécificité de jeu et phrase qui l'explique.
 */
internal class VTheme(
    val key: String, val nameRes: Int, val card: String,
    val sky: List<C3>, val sun: C3, val sunR: Float, val stripes: Boolean, val stars: Boolean, val night: Boolean,
    val mount1: C3, val mount2: C3, val skyline: Boolean, val hills: C3,
    val grass: C3, val grass2: C3, val road: C3, val road2: C3, val edge: C3, val fog: C3,
    val decor: List<Pair<String, Float>>, val picks: List<String>, val ambient: Int,
    // obstacles à sauter propres au thème (décalque au sol / billboard en travers de la voie)
    val holeTex: String = "vv_hole", val logTex: String = "vv_log", val rampTex: String = "vv_wk_ramp",
    /** sprites de ce qui TRAVERSE la route (chat, rocher, crabe, vache…) ; vide = rien ne traverse */
    val crossers: List<String> = emptyList(),
    val trait: VTrait = VTrait(), val tip: Int = 0
)
internal val THEMES = listOf(
    VTheme("forest", R.string.vroum_play, "vv_title",
        listOf(C3(0.06f, 0.03f, 0.14f), C3(0.26f, 0.09f, 0.40f), C3(0.62f, 0.20f, 0.52f), C3(1.0f, 0.76f, 0.45f)), C3(1f, 0.75f, 0.5f), 0.46f, true, true, false,
        C3(0.36f, 0.20f, 0.50f), C3(0.27f, 0.14f, 0.40f), false, C3(0.09f, 0.23f, 0.13f),
        C3(0.19f, 0.48f, 0.21f), C3(0.16f, 0.43f, 0.19f), C3(0.24f, 0.21f, 0.33f), C3(0.27f, 0.24f, 0.37f), C3(0.88f, 0.28f, 0.25f), C3(1.0f, 0.76f, 0.55f),
        listOf("tree1" to 2.6f, "tree2" to 2.9f, "tree3" to 2.5f, "tree1" to 2.6f, "tree2" to 2.9f, "bush" to 1.1f, "rock" to 1.0f, "sign" to 0.9f),
        listOf("vv_acorn", "vv_mushroom", "vv_berry"), 0, tip = R.string.vroum_tip_forest),
    VTheme("night", R.string.vroum_theme_night, "vv_night",
        listOf(C3(0.01f, 0.01f, 0.05f), C3(0.03f, 0.03f, 0.12f), C3(0.06f, 0.06f, 0.22f), C3(0.12f, 0.10f, 0.30f)), C3(0.92f, 0.94f, 1f), 0.22f, false, true, true,
        C3(0.10f, 0.08f, 0.20f), C3(0.07f, 0.06f, 0.15f), false, C3(0.04f, 0.09f, 0.06f),
        C3(0.07f, 0.19f, 0.10f), C3(0.06f, 0.16f, 0.09f), C3(0.14f, 0.13f, 0.22f), C3(0.16f, 0.15f, 0.25f), C3(0.75f, 0.25f, 0.22f), C3(0.08f, 0.07f, 0.18f),
        listOf("tree1" to 2.6f, "lamp" to 2.4f, "tree3" to 2.5f, "owl_tree" to 2.6f, "tree2" to 2.9f, "bush" to 1.1f, "lamp" to 2.4f, "rock" to 1.0f),
        listOf("vv_acorn", "vv_mushroom", "vv_berry"), 5, tip = R.string.vroum_tip_night, trait = VTrait(puddles = 0.30f)),   // 5 = pluie
    VTheme("volcano", R.string.vroum_theme_volcano, "vv_volcano",
        listOf(C3(0.10f, 0.02f, 0.03f), C3(0.30f, 0.06f, 0.05f), C3(0.65f, 0.18f, 0.06f), C3(1.0f, 0.55f, 0.15f)), C3(1f, 0.35f, 0.1f), 0.40f, true, false, false,
        C3(0.16f, 0.06f, 0.05f), C3(0.10f, 0.04f, 0.04f), false, C3(0.12f, 0.05f, 0.04f),
        C3(0.22f, 0.13f, 0.10f), C3(0.19f, 0.11f, 0.09f), C3(0.16f, 0.14f, 0.14f), C3(0.19f, 0.16f, 0.16f), C3(1.0f, 0.45f, 0.1f), C3(0.55f, 0.18f, 0.08f),
        listOf("vol_rock1" to 1.4f, "vol_rock2" to 1.6f, "vol_geyser" to 1.5f, "vol_tree" to 2.4f, "vol_crystal" to 1.2f, "vol_rock1" to 1.4f, "vol_sign" to 0.9f, "vol_rock2" to 1.6f),
        listOf("vv_vol_p1", "vv_vol_p2", "vv_vol_p3"), 2, holeTex = "vv_vol_lava", logTex = "vv_vol_ridge", crossers = listOf("vr_rock"), tip = R.string.vroum_tip_volcano),
    VTheme("tokyo", R.string.vroum_theme_tokyo, "vv_tokyo",
        listOf(C3(0.02f, 0.01f, 0.08f), C3(0.08f, 0.03f, 0.22f), C3(0.30f, 0.08f, 0.45f), C3(0.85f, 0.25f, 0.55f)), C3(0.95f, 0.95f, 1f), 0.20f, false, true, true,
        C3(0.09f, 0.05f, 0.18f), C3(0.06f, 0.03f, 0.13f), true, C3(0.08f, 0.06f, 0.14f),
        C3(0.17f, 0.17f, 0.21f), C3(0.15f, 0.15f, 0.19f), C3(0.11f, 0.11f, 0.15f), C3(0.13f, 0.13f, 0.17f), C3(0.2f, 0.9f, 1.0f), C3(0.30f, 0.10f, 0.40f),
        listOf("tk_bld1" to 4.2f, "tk_bld2" to 2.6f, "tk_bld3" to 4.0f, "tk_torii" to 2.4f, "tk_lantern" to 2.2f, "tk_sakura" to 2.6f, "tk_bld2" to 2.6f, "tk_lantern" to 2.2f),
        listOf("vv_tk_p1", "vv_tk_p2", "vv_tk_p3"), 3, logTex = "vv_tk_cat", crossers = listOf("vv_tk_cat"), tip = R.string.vroum_tip_tokyo),
    VTheme("snow", R.string.vroum_theme_snow, "vv_snow",
        listOf(C3(0.45f, 0.62f, 0.90f), C3(0.62f, 0.76f, 0.95f), C3(0.80f, 0.88f, 0.98f), C3(0.98f, 0.92f, 0.85f)), C3(1f, 0.97f, 0.85f), 0.26f, false, false, false,
        C3(0.86f, 0.90f, 0.98f), C3(0.72f, 0.80f, 0.94f), false, C3(0.90f, 0.94f, 1.0f),
        C3(0.90f, 0.94f, 1.0f), C3(0.84f, 0.90f, 0.98f), C3(0.30f, 0.32f, 0.40f), C3(0.33f, 0.35f, 0.43f), C3(0.35f, 0.65f, 0.95f), C3(0.85f, 0.90f, 1.0f),
        listOf("sn_pine" to 2.8f, "sn_snowman" to 1.5f, "sn_pine" to 2.8f, "sn_gift" to 1.4f, "sn_santa" to 1.9f, "sn_cabin" to 2.8f, "sn_reindeer" to 1.6f, "sn_pine" to 2.6f),
        listOf("vv_sn_p1", "vv_sn_p2", "vv_sn_p3"), 4, holeTex = "vv_sn_ice", logTex = "vv_sn_drift", tip = R.string.vroum_tip_snow),
    VTheme("beach", R.string.vroum_theme_beach, "vv_bc",
        listOf(C3(0.2f, 0.55f, 0.95f), C3(0.35f, 0.7f, 0.98f), C3(0.6f, 0.85f, 1.0f), C3(0.95f, 0.97f, 0.9f)), C3(1f, 0.95f, 0.6f), 0.22f, false, false, false,
        C3(0.25f, 0.65f, 0.8f), C3(0.15f, 0.55f, 0.75f), false, C3(0.1f, 0.6f, 0.7f),
        C3(0.93f, 0.84f, 0.6f), C3(0.89f, 0.8f, 0.56f), C3(0.36f, 0.36f, 0.42f), C3(0.39f, 0.39f, 0.45f), C3(1f, 0.55f, 0.2f), C3(0.85f, 0.93f, 1.0f),
        listOf("bc_palm" to 3.0f, "bc_umbrella" to 1.5f, "bc_castle" to 1.2f, "bc_surf" to 1.6f, "bc_tower" to 2.4f, "bc_rock" to 1.0f, "bc_palm" to 3.0f, "bc_umbrella" to 1.5f),
        listOf("vv_bc_p1", "vv_bc_p2", "vv_bc_p3"), 0, crossers = listOf("vv_bc_crab"), trait = VTrait(ramps = 0.5f), tip = R.string.vroum_tip_beach),
    VTheme("desert", R.string.vroum_theme_desert, "vv_ds",
        listOf(C3(0.25f, 0.35f, 0.75f), C3(0.55f, 0.5f, 0.8f), C3(0.95f, 0.65f, 0.5f), C3(1.0f, 0.85f, 0.55f)), C3(1f, 0.8f, 0.4f), 0.34f, false, false, false,
        C3(0.7f, 0.35f, 0.25f), C3(0.58f, 0.27f, 0.22f), false, C3(0.75f, 0.45f, 0.28f),
        C3(0.88f, 0.66f, 0.4f), C3(0.84f, 0.62f, 0.37f), C3(0.4f, 0.33f, 0.3f), C3(0.43f, 0.36f, 0.33f), C3(0.85f, 0.35f, 0.2f), C3(0.98f, 0.82f, 0.6f),
        listOf("ds_cactus" to 2.6f, "ds_mesa" to 2.8f, "ds_wheel" to 1.1f, "ds_bush" to 0.9f, "ds_sign" to 1.8f, "ds_barrel" to 0.9f, "ds_cactus" to 2.6f, "ds_mesa" to 2.8f),
        listOf("vv_ds_p1", "vv_ds_p2", "vv_ds_p3"), 7, crossers = listOf("vv_ds_tumble"), trait = VTrait(sandstorm = true), tip = R.string.vroum_tip_desert),
    VTheme("city", R.string.vroum_theme_city, "vv_ct",
        listOf(C3(0.25f, 0.5f, 0.95f), C3(0.45f, 0.68f, 0.98f), C3(0.7f, 0.85f, 1.0f), C3(0.92f, 0.95f, 1.0f)), C3(1f, 1f, 0.85f), 0.18f, false, false, false,
        C3(0.55f, 0.62f, 0.78f), C3(0.45f, 0.52f, 0.7f), true, C3(0.4f, 0.48f, 0.6f),
        C3(0.7f, 0.72f, 0.76f), C3(0.66f, 0.68f, 0.72f), C3(0.25f, 0.26f, 0.3f), C3(0.28f, 0.29f, 0.33f), C3(1f, 0.8f, 0.1f), C3(0.85f, 0.9f, 1.0f),
        listOf("ct_bld" to 4.0f, "ct_shop" to 2.2f, "ct_light" to 2.2f, "ct_stop" to 1.6f, "ct_tree" to 2.0f, "ct_board" to 2.0f, "ct_bld" to 4.0f, "ct_shop" to 2.2f),
        listOf("vv_ct_p1", "vv_ct_p2", "vv_ct_p3"), 0, logTex = "vv_ct_barrier", trait = VTrait(trafficMul = 1.5f), tip = R.string.vroum_tip_city),
    VTheme("space", R.string.vroum_theme_space, "vv_sp",
        listOf(C3(0.02f, 0.01f, 0.08f), C3(0.08f, 0.03f, 0.2f), C3(0.2f, 0.06f, 0.38f), C3(0.45f, 0.15f, 0.6f)), C3(0.75f, 0.85f, 1.0f), 0.3f, false, true, false,
        C3(0.22f, 0.18f, 0.4f), C3(0.15f, 0.12f, 0.3f), false, C3(0.18f, 0.15f, 0.32f),
        C3(0.3f, 0.27f, 0.45f), C3(0.27f, 0.24f, 0.41f), C3(0.55f, 0.3f, 0.75f), C3(0.3f, 0.55f, 0.85f), C3(1f, 0.9f, 0.3f), C3(0.15f, 0.08f, 0.3f),
        listOf("sp_planet" to 2.2f, "sp_asteroid" to 1.4f, "sp_antenna" to 2.2f, "sp_plant" to 1.3f, "sp_rocket" to 3.0f, "sp_crater" to 1.2f, "sp_plant" to 1.3f, "sp_asteroid" to 1.4f),
        listOf("vv_sp_p1", "vv_sp_p2", "vv_sp_p3"), 8, logTex = "vv_sp_rock", trait = VTrait(jumpMul = 1.5f, oncomingMul = 0.6f, rainbow = true), tip = R.string.vroum_tip_space),
    VTheme("ocean", R.string.vroum_theme_ocean, "vv_oc",
        listOf(C3(0.02f, 0.1f, 0.3f), C3(0.04f, 0.22f, 0.48f), C3(0.08f, 0.4f, 0.62f), C3(0.2f, 0.62f, 0.75f)), C3(0.6f, 0.9f, 1.0f), 0.16f, false, false, false,
        C3(0.05f, 0.28f, 0.45f), C3(0.04f, 0.2f, 0.38f), false, C3(0.06f, 0.3f, 0.42f),
        C3(0.55f, 0.62f, 0.55f), C3(0.5f, 0.58f, 0.52f), C3(0.3f, 0.42f, 0.5f), C3(0.33f, 0.46f, 0.54f), C3(1f, 0.6f, 0.5f), C3(0.08f, 0.38f, 0.58f),
        listOf("oc_coral" to 1.5f, "oc_kelp" to 2.6f, "oc_chest" to 1.1f, "oc_clam" to 1.2f, "oc_anchor" to 2.0f, "oc_jelly" to 1.6f, "oc_coral" to 1.5f, "oc_kelp" to 2.6f),
        listOf("vv_oc_p1", "vv_oc_p2", "vv_oc_p3"), 6, rampTex = "vv_oc_bubble", trait = VTrait(ramps = 0.9f, jumpMul = 1.3f, oncomingMul = 0f), tip = R.string.vroum_tip_ocean),
    VTheme("candy", R.string.vroum_theme_candy, "vv_cd",
        listOf(C3(0.75f, 0.55f, 0.95f), C3(0.95f, 0.7f, 0.95f), C3(1.0f, 0.82f, 0.92f), C3(1.0f, 0.95f, 0.85f)), C3(1f, 1f, 0.9f), 0.2f, false, false, false,
        C3(0.95f, 0.65f, 0.8f), C3(0.8f, 0.55f, 0.85f), false, C3(0.7f, 0.9f, 0.75f),
        C3(0.75f, 0.95f, 0.8f), C3(0.7f, 0.9f, 0.76f), C3(0.45f, 0.28f, 0.2f), C3(0.5f, 0.32f, 0.23f), C3(1f, 0.4f, 0.6f), C3(1f, 0.88f, 0.95f),
        listOf("cd_lolli" to 2.6f, "cd_tree" to 2.6f, "cd_house" to 2.0f, "cd_bear" to 1.4f, "cd_cone" to 2.6f, "cd_donut" to 1.5f, "cd_lolli" to 2.6f, "cd_tree" to 2.6f),
        listOf("vv_cd_p1", "vv_cd_p2", "vv_cd_p3"), 9, trait = VTrait(coinMul = 2), tip = R.string.vroum_tip_candy),
    VTheme("farm", R.string.vroum_theme_farm, "vv_fm",
        listOf(C3(0.3f, 0.55f, 0.95f), C3(0.5f, 0.72f, 0.98f), C3(0.75f, 0.88f, 1.0f), C3(1.0f, 0.95f, 0.8f)), C3(1f, 0.95f, 0.6f), 0.2f, false, false, false,
        C3(0.35f, 0.6f, 0.35f), C3(0.28f, 0.52f, 0.3f), false, C3(0.3f, 0.58f, 0.25f),
        C3(0.5f, 0.72f, 0.25f), C3(0.46f, 0.68f, 0.23f), C3(0.55f, 0.45f, 0.32f), C3(0.58f, 0.48f, 0.35f), C3(0.95f, 0.85f, 0.5f), C3(0.9f, 0.95f, 0.85f),
        listOf("fm_barn" to 3.0f, "fm_hay" to 1.4f, "fm_mill" to 3.2f, "fm_scare" to 1.8f, "fm_apple" to 2.4f, "fm_fence" to 1.3f, "fm_apple" to 2.4f, "fm_hay" to 1.4f),
        listOf("vv_fm_p1", "vv_fm_p2", "vv_fm_p3"), 0, logTex = "vv_fm_bale", crossers = listOf("vv_fm_cow", "vv_fm_sheep", "vv_fm_hen"), tip = R.string.vroum_tip_farm),
    VTheme("jungle", R.string.vroum_theme_jungle, "vv_jg",
        listOf(C3(0.05f, 0.25f, 0.25f), C3(0.15f, 0.45f, 0.35f), C3(0.45f, 0.7f, 0.4f), C3(0.95f, 0.9f, 0.55f)), C3(1f, 0.9f, 0.5f), 0.25f, false, false, false,
        C3(0.1f, 0.38f, 0.25f), C3(0.07f, 0.3f, 0.2f), false, C3(0.08f, 0.35f, 0.15f),
        C3(0.14f, 0.45f, 0.18f), C3(0.12f, 0.41f, 0.16f), C3(0.42f, 0.36f, 0.28f), C3(0.45f, 0.39f, 0.3f), C3(0.9f, 0.75f, 0.3f), C3(0.55f, 0.8f, 0.5f),
        listOf("jg_tree" to 3.2f, "jg_idol" to 1.6f, "jg_pillar" to 2.4f, "jg_fern" to 1.4f, "jg_torch" to 1.8f, "jg_flower" to 1.3f, "jg_tree" to 3.2f, "jg_fern" to 1.4f),
        listOf("vv_jg_p1", "vv_jg_p2", "vv_jg_p3"), 1, trait = VTrait(boulders = true), tip = R.string.vroum_tip_jungle),
    VTheme("dino", R.string.vroum_theme_dino, "vv_dn",
        listOf(C3(0.35f, 0.25f, 0.45f), C3(0.75f, 0.4f, 0.4f), C3(0.98f, 0.62f, 0.35f), C3(1.0f, 0.85f, 0.5f)), C3(1f, 0.6f, 0.3f), 0.36f, false, false, false,
        C3(0.45f, 0.3f, 0.3f), C3(0.35f, 0.22f, 0.25f), false, C3(0.25f, 0.4f, 0.2f),
        C3(0.35f, 0.55f, 0.22f), C3(0.32f, 0.51f, 0.2f), C3(0.45f, 0.38f, 0.3f), C3(0.48f, 0.41f, 0.33f), C3(0.95f, 0.5f, 0.2f), C3(0.98f, 0.75f, 0.5f),
        listOf("dn_fern" to 3.0f, "dn_volcano" to 2.6f, "dn_bones" to 1.5f, "dn_nest" to 1.1f, "dn_arch" to 2.2f, "dn_long" to 3.6f, "dn_fern" to 3.0f, "dn_nest" to 1.1f),
        listOf("vv_dn_p1", "vv_dn_p2", "vv_dn_p3"), 2, crossers = listOf("vv_dn_raptor", "vv_dn_trike"), trait = VTrait(herd = 3), tip = R.string.vroum_tip_dino),
    VTheme("spooky", R.string.vroum_theme_spooky, "vv_hw",
        listOf(C3(0.03f, 0.02f, 0.08f), C3(0.1f, 0.05f, 0.2f), C3(0.25f, 0.1f, 0.35f), C3(0.5f, 0.25f, 0.45f)), C3(1f, 0.95f, 0.75f), 0.24f, false, true, true,
        C3(0.12f, 0.08f, 0.2f), C3(0.08f, 0.05f, 0.15f), false, C3(0.06f, 0.08f, 0.1f),
        C3(0.1f, 0.16f, 0.14f), C3(0.09f, 0.14f, 0.12f), C3(0.16f, 0.14f, 0.22f), C3(0.18f, 0.16f, 0.25f), C3(1f, 0.5f, 0.1f), C3(0.2f, 0.1f, 0.3f),
        listOf("hw_tree" to 2.8f, "hw_house" to 3.6f, "hw_tomb" to 1.0f, "hw_lamp" to 2.4f, "hw_scare" to 2.0f, "hw_pot" to 1.1f, "hw_tree" to 2.8f, "hw_lamp" to 2.4f),
        listOf("vv_hw_p1", "vv_hw_p2", "vv_hw_p3"), 1, trait = VTrait(ghosts = true), tip = R.string.vroum_tip_spooky),
    VTheme("circuit", R.string.vroum_theme_circuit, "vv_gp",
        listOf(C3(0.2f, 0.5f, 0.95f), C3(0.4f, 0.68f, 0.98f), C3(0.65f, 0.84f, 1.0f), C3(0.9f, 0.95f, 1.0f)), C3(1f, 1f, 0.85f), 0.18f, false, false, false,
        C3(0.4f, 0.6f, 0.45f), C3(0.32f, 0.52f, 0.4f), false, C3(0.25f, 0.55f, 0.25f),
        C3(0.25f, 0.62f, 0.25f), C3(0.22f, 0.57f, 0.23f), C3(0.22f, 0.22f, 0.26f), C3(0.25f, 0.25f, 0.29f), C3(0.9f, 0.1f, 0.1f), C3(0.85f, 0.92f, 1.0f),
        listOf("gp_stand" to 3.0f, "gp_tires" to 1.0f, "gp_pit" to 2.4f, "gp_flag" to 2.4f, "gp_board" to 1.4f, "gp_lights" to 3.4f, "gp_stand" to 3.0f, "gp_tires" to 1.0f),
        listOf("vv_gp_p1", "vv_gp_p2", "vv_gp_p3"), 0, trait = VTrait(hazards = false, rivalsExtra = 2, ceilMul = 1.12f, trafficMul = 0.6f, oncomingMul = 0.5f), tip = R.string.vroum_tip_circuit),
    VTheme("ice", R.string.vroum_theme_ice, "vv_ic",
        listOf(C3(0.1f, 0.2f, 0.45f), C3(0.25f, 0.45f, 0.7f), C3(0.55f, 0.8f, 0.9f), C3(0.85f, 0.97f, 1.0f)), C3(0.9f, 1f, 1f), 0.2f, false, false, false,
        C3(0.7f, 0.85f, 0.95f), C3(0.55f, 0.72f, 0.9f), false, C3(0.8f, 0.92f, 1.0f),
        C3(0.85f, 0.94f, 1.0f), C3(0.78f, 0.9f, 0.98f), C3(0.55f, 0.72f, 0.85f), C3(0.6f, 0.77f, 0.9f), C3(0.2f, 0.5f, 0.9f), C3(0.8f, 0.92f, 1.0f),
        listOf("ic_berg" to 2.6f, "ic_igloo" to 1.6f, "ic_peng" to 1.2f, "ic_spikes" to 1.5f, "ic_seal" to 1.1f, "ic_pine" to 2.6f, "ic_berg" to 2.6f, "ic_spikes" to 1.5f),
        listOf("vv_ic_p1", "vv_ic_p2", "vv_ic_p3"), 4, holeTex = "vv_sn_ice", logTex = "vv_sn_drift", trait = VTrait(ice = true), tip = R.string.vroum_tip_ice),
    VTheme("works", R.string.vroum_theme_works, "vv_wk",
        listOf(C3(0.35f, 0.55f, 0.9f), C3(0.55f, 0.72f, 0.95f), C3(0.8f, 0.88f, 0.98f), C3(1.0f, 0.93f, 0.75f)), C3(1f, 1f, 0.85f), 0.2f, false, false, false,
        C3(0.55f, 0.55f, 0.6f), C3(0.45f, 0.45f, 0.52f), false, C3(0.6f, 0.52f, 0.38f),
        C3(0.72f, 0.62f, 0.42f), C3(0.68f, 0.58f, 0.39f), C3(0.3f, 0.3f, 0.33f), C3(0.33f, 0.33f, 0.36f), C3(1f, 0.75f, 0.0f), C3(0.92f, 0.9f, 0.85f),
        listOf("wk_crane" to 4.0f, "wk_cones" to 1.0f, "wk_scaf" to 2.8f, "wk_bricks" to 1.1f, "wk_sign" to 1.8f, "wk_pipes" to 1.3f, "wk_crane" to 4.0f, "wk_cones" to 1.0f),
        listOf("vv_wk_p1", "vv_wk_p2", "vv_wk_p3"), 0, logTex = "vv_wk_cone", trait = VTrait(ramps = 1.0f), tip = R.string.vroum_tip_works),
    VTheme("sky", R.string.vroum_theme_sky, "vv_sk",
        listOf(C3(0.15f, 0.45f, 0.95f), C3(0.35f, 0.65f, 1.0f), C3(0.65f, 0.85f, 1.0f), C3(1.0f, 0.95f, 0.9f)), C3(1f, 1f, 0.8f), 0.22f, false, false, false,
        C3(1f, 1f, 1f), C3(0.9f, 0.95f, 1.0f), false, C3(0.95f, 0.97f, 1.0f),
        C3(0.96f, 0.98f, 1.0f), C3(0.9f, 0.94f, 1.0f), C3(0.8f, 0.7f, 0.95f), C3(0.7f, 0.85f, 0.98f), C3(1f, 0.6f, 0.8f), C3(0.85f, 0.93f, 1.0f),
        listOf("sk_cloud" to 2.0f, "sk_balloon" to 3.4f, "sk_island" to 3.0f, "sk_kite" to 2.4f, "sk_castle" to 3.0f, "sk_sock" to 2.0f, "sk_cloud" to 2.0f, "sk_balloon" to 3.4f),
        listOf("vv_sk_p1", "vv_sk_p2", "vv_sk_p3"), 0, trait = VTrait(wind = true, jumpMul = 1.15f), tip = R.string.vroum_tip_sky),
    VTheme("cyber", R.string.vroum_theme_cyber, "vv_cy",
        listOf(C3(0.01f, 0.01f, 0.06f), C3(0.05f, 0.02f, 0.18f), C3(0.18f, 0.04f, 0.35f), C3(0.6f, 0.1f, 0.55f)), C3(1f, 0.3f, 0.7f), 0.4f, true, true, true,
        C3(0.08f, 0.04f, 0.2f), C3(0.05f, 0.02f, 0.14f), true, C3(0.06f, 0.03f, 0.14f),
        C3(0.06f, 0.04f, 0.16f), C3(0.1f, 0.05f, 0.24f), C3(0.08f, 0.08f, 0.16f), C3(0.12f, 0.1f, 0.24f), C3(0.1f, 1.0f, 0.95f), C3(0.25f, 0.05f, 0.4f),
        listOf("cy_holo" to 2.6f, "cy_tower" to 3.6f, "cy_cubes" to 1.3f, "cy_palm" to 2.6f, "cy_robot" to 1.4f, "cy_dish" to 1.5f, "cy_holo" to 2.6f, "cy_tower" to 3.6f),
        listOf("vv_cy_p1", "vv_cy_p2", "vv_cy_p3"), 8, logTex = "vv_cy_gate", trait = VTrait(gates = true), tip = R.string.vroum_tip_cyber),
)
