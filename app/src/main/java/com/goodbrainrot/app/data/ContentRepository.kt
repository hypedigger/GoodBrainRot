package com.goodbrainrot.app.data

import android.content.Context
import com.goodbrainrot.app.audio.Tts
import org.json.JSONArray

/**
 * Un élément de contenu visuel (animal, véhicule, dinosaure) pour les quiz image.
 * Le nom vient soit de [names] (multilingue, ajouté par le script Wikidata),
 * soit de [nameKey] (ressource string, pour le contenu de base).
 * Le visuel : image d'asset (folder/key.*) > drawable vectoriel > emoji > nom.
 */
data class ContentItem(
    val key: String,
    val names: Map<String, String>,
    val nameKey: String?,
    val emoji: String?,
    val drawable: String?,
    val folder: String,
    val rank: Int = 0
) {
    fun displayName(context: Context): String {
        val lang = Tts.currentLocale().language
        names[lang]?.let { return it }
        names["en"]?.let { return it }
        if (nameKey != null) {
            val id = context.resources.getIdentifier(nameKey, "string", context.packageName)
            if (id != 0) return context.getString(id)
        }
        return key
    }

    fun drawableRes(context: Context): Int {
        if (drawable == null) return 0
        return context.resources.getIdentifier(drawable, "drawable", context.packageName)
    }
}

/** Catégories de contenu visuel. */
enum class ContentCategory(val folder: String, val jsonFile: String) {
    ANIMAL("animals", "content/animals.json"),
    VEHICLE("vehicles", "content/vehicles.json"),
    DINO("dinos", "content/dinos.json")
}

/**
 * Charge le contenu depuis les JSON d'assets et sait quels items ont une image
 * réellement présente (pour ne proposer dans les quiz que des items « devinables ».)
 */
object ContentRepository {
    private val cache = HashMap<ContentCategory, List<ContentItem>>()
    private val imagePresent = HashMap<String, Set<String>>() // folder -> clés avec image

    private fun imagesIn(context: Context, folder: String): Set<String> =
        imagePresent.getOrPut(folder) {
            try {
                (context.assets.list(folder) ?: emptyArray())
                    .map { it.substringBeforeLast('.') }
                    .toSet()
            } catch (_: Exception) {
                emptySet()
            }
        }

    fun items(context: Context, category: ContentCategory): List<ContentItem> =
        cache.getOrPut(category) {
            try {
                val text = context.assets.open(category.jsonFile)
                    .bufferedReader().use { it.readText() }
                val arr = JSONArray(text)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val names = HashMap<String, String>()
                    o.optJSONObject("names")?.let { n ->
                        n.keys().forEach { k -> names[k] = n.getString(k) }
                    }
                    ContentItem(
                        key = o.getString("key"),
                        names = names,
                        nameKey = o.optString("nameKey").ifBlank { null },
                        emoji = o.optString("emoji").ifBlank { null },
                        drawable = o.optString("drawable").ifBlank { null },
                        folder = category.folder,
                        rank = o.optInt("rank", 0)
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** A-t-il un visuel utilisable dans un quiz « devine » (image, dessin ou emoji) ? */
    fun ContentItem.hasGuessableVisual(context: Context): Boolean =
        key in imagesIn(context, folder) ||
            (drawable != null && drawableRes(context) != 0) ||
            emoji != null

    /** Pool d'un quiz image : items réellement devinables. */
    fun guessPool(context: Context, categories: List<ContentCategory>): List<ContentItem> =
        categories.flatMap { items(context, it) }.filter { it.hasGuessableVisual(context) }

    /**
     * Pool devinable trié du plus **classique** au plus **exotique**.
     * Priorité : rank du JSON (pages vues Wikipédia) ; à défaut, ordre curé des
     * animaux très connus ; sinon alphabétique.
     */
    fun rankedGuessPool(context: Context, categories: List<ContentCategory>): List<ContentItem> =
        guessPool(context, categories).sortedWith(
            compareBy({ effectiveRank(it) }, { it.displayName(context).lowercase() })
        )

    private fun effectiveRank(item: ContentItem): Int {
        if (item.rank > 0) return item.rank
        val curated = CURATED_COMMON.indexOf(item.key)
        return if (curated >= 0) curated + 1 else 100_000
    }

    fun hasImage(context: Context, item: ContentItem): Boolean =
        item.key in imagesIn(context, item.folder)

    /** Ordre curé de repli : animaux/véhicules/dinos les plus familiers d'abord. */
    private val CURATED_COMMON = listOf(
        "dog", "cat", "cow", "horse", "sheep", "pig", "duck", "hen", "rabbit", "lion",
        "elephant", "monkey", "bear", "tiger", "giraffe", "zebra", "fox", "wolf",
        "frog", "fish", "bird", "owl", "mouse", "snake", "bee", "dolphin", "penguin",
        "panda", "kangaroo", "crocodile", "turtle", "butterfly", "ladybird", "spider",
        "car", "bus", "train", "plane", "boat", "fire_truck", "police", "bike", "rocket",
        "trex", "triceratops", "stegosaurus", "brachiosaurus", "velociraptor"
    )
}
