package com.goodbrainrot.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import java.util.UUID

/** Un profil enfant : prénom, avatar emoji, âge (mois), XP cumulés. */
data class ChildProfile(
    val id: String,
    val name: String,
    val avatar: String,
    val ageMonths: Int,
    val xp: Int
)

/**
 * Profils enfants (multi-enfants par appareil), persistés en **SQLite** ([Db]).
 * Toutes les données d'apprentissage ([Stats], répétition espacée) sont
 * rattachées au profil **courant** via son id.
 */
object Profiles {
    private lateinit var db: Db
    private const val XP_PER_LEVEL = 100

    val list = mutableStateListOf<ChildProfile>()
    val currentId = mutableStateOf("")
    /** Bump pour forcer la recomposition après une modification. */
    val version = mutableStateOf(0)

    val AVATARS = listOf("🐣", "🦊", "🐱", "🐯", "🐰", "🐻", "🦁", "🐸", "🐵", "🐧", "🦄", "🐨")

    fun init(context: Context) {
        if (::db.isInitialized) return
        db = Db.get(context)
        migrateFromPrefsIfNeeded(context)
        load()
        if (list.isEmpty()) {
            // Profil par défaut au premier lancement
            add(name = "Bébé", avatar = "🐣", ageMonths = 0)
        }
    }

    /** Migration unique des anciens SharedPreferences vers SQLite. */
    private fun migrateFromPrefsIfNeeded(context: Context) {
        val d = db.writableDatabase
        val migrated = d.rawQuery(
            "SELECT value FROM ${Db.TABLE_META} WHERE key='profiles_migrated'", null
        ).use { it.moveToFirst() }
        if (migrated) return

        val sp = context.applicationContext
            .getSharedPreferences("goodbrainrot_profiles", Context.MODE_PRIVATE)
        val raw = sp.getString("profiles", "") ?: ""
        if (raw.isNotBlank()) {
            try {
                val arr = JSONArray(raw)
                d.beginTransaction()
                try {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        d.insertWithOnConflict(
                            Db.TABLE_PROFILES, null,
                            ContentValues().apply {
                                put("id", o.getString("id"))
                                put("name", o.optString("name", "Enfant"))
                                put("avatar", o.optString("avatar", "🐣"))
                                put("age_months", o.optInt("age", 0))
                                put("xp", o.optInt("xp", 0))
                                put("sort", i)
                            },
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    val current = sp.getString("current", "") ?: ""
                    putMeta(d, "current", current)
                    d.setTransactionSuccessful()
                } finally {
                    d.endTransaction()
                }
            } catch (_: Exception) {
            }
        }
        putMeta(d, "profiles_migrated", "1")
    }

    private fun load() {
        list.clear()
        val d = db.readableDatabase
        d.rawQuery(
            "SELECT id, name, avatar, age_months, xp FROM ${Db.TABLE_PROFILES} ORDER BY sort ASC", null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    ChildProfile(
                        id = c.getString(0),
                        name = c.getString(1),
                        avatar = c.getString(2),
                        ageMonths = c.getInt(3),
                        xp = c.getInt(4)
                    )
                )
            }
        }
        currentId.value = getMeta(d, "current") ?: ""
        if (list.none { it.id == currentId.value }) {
            currentId.value = list.firstOrNull()?.id ?: ""
        }
    }

    /** Réécrit la table profils (données minuscules → réécriture complète). */
    private fun save() {
        val d = db.writableDatabase
        d.beginTransaction()
        try {
            d.delete(Db.TABLE_PROFILES, null, null)
            list.forEachIndexed { i, p ->
                d.insert(
                    Db.TABLE_PROFILES, null,
                    ContentValues().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("avatar", p.avatar)
                        put("age_months", p.ageMonths)
                        put("xp", p.xp)
                        put("sort", i)
                    }
                )
            }
            putMeta(d, "current", currentId.value)
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
        version.value++
    }

    fun current(): ChildProfile =
        list.firstOrNull { it.id == currentId.value } ?: list.firstOrNull()
        ?: ChildProfile("default", "Enfant", "🐣", 0, 0)

    fun add(name: String, avatar: String, ageMonths: Int = 0) {
        val p = ChildProfile(UUID.randomUUID().toString(), name.ifBlank { "Enfant" }, avatar, ageMonths, 0)
        list.add(p)
        currentId.value = p.id
        save()
    }

    fun select(id: String) {
        if (list.any { it.id == id }) {
            currentId.value = id
            save()
        }
    }

    fun update(id: String, name: String, avatar: String, ageMonths: Int) {
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(name = name.ifBlank { "Enfant" }, avatar = avatar, ageMonths = ageMonths)
            save()
        }
    }

    fun delete(id: String) {
        list.removeAll { it.id == id }
        Stats.deleteProfileData(id)
        if (list.isEmpty()) add("Bébé", "🐣", 0)
        if (list.none { it.id == currentId.value }) currentId.value = list.first().id
        save()
    }

    fun addXp(amount: Int) {
        if (amount <= 0) return
        val idx = list.indexOfFirst { it.id == currentId.value }
        if (idx >= 0) {
            list[idx] = list[idx].copy(xp = list[idx].xp + amount)
            save()
        }
    }

    fun level(xp: Int): Int = 1 + xp / XP_PER_LEVEL
    fun xpInLevel(xp: Int): Int = xp % XP_PER_LEVEL
    fun xpPerLevel(): Int = XP_PER_LEVEL

    // ---- Helpers meta ----

    private fun putMeta(d: SQLiteDatabase, key: String, value: String) {
        d.execSQL(
            "INSERT OR REPLACE INTO ${Db.TABLE_META}(key, value) VALUES(?, ?)",
            arrayOf(key, value)
        )
    }

    private fun getMeta(d: SQLiteDatabase, key: String): String? =
        d.rawQuery("SELECT value FROM ${Db.TABLE_META} WHERE key=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
}
