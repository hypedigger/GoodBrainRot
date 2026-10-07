package com.goodbrainrot.app.data

import android.content.ContentValues
import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Statistiques & maîtrise par **profil enfant** (clé = id du profil courant),
 * persistées en **SQLite** ([Db]) :
 *  - temps passé par jour,
 *  - réponses justes/fausses par catégorie,
 *  - items « appris » par catégorie + **boîte Leitner** (0..5) par item,
 *  - **snapshot quotidien** du total d'items maîtrisés → courbe de progression.
 *
 * Pour préserver le pattern réactif Compose existant, les données du profil
 * courant sont tenues en cache mémoire (rechargé à la volée au changement de
 * profil) et chaque écriture est répercutée immédiatement en base.
 */
object Stats {
    private lateinit var db: Db
    private const val LEARN_THRESHOLD = 2
    const val MAX_BOX = 5
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Incrémenté à chaque écriture pour déclencher les recompositions. */
    val version = mutableIntStateOf(0)

    // ---- Cache mémoire du profil chargé ----
    private var loadedPid: String? = null
    private val timeByDay = HashMap<String, Long>()
    private val boxMap = HashMap<String, Int>()
    private val passMap = HashMap<String, Int>()
    private val learnedMap = HashMap<String, MutableSet<String>>()
    private val correctMap = HashMap<String, Int>()
    private val wrongMap = HashMap<String, Int>()

    fun init(context: Context) {
        if (::db.isInitialized) return
        db = Db.get(context)
        migrateFromPrefsIfNeeded(context)
    }

    /** Préfixe = profil courant → données séparées par enfant. */
    private fun pid(): String = Profiles.currentId.value.ifBlank { "default" }
    private fun today(): String = dateFmt.format(Date())
    private fun cacheKey(category: String, item: String) = "$category|$item"

    /** Recharge le cache si le profil courant a changé. */
    private fun ensureLoaded() {
        val p = pid()
        if (loadedPid == p) return
        loadedPid = p
        timeByDay.clear(); boxMap.clear(); passMap.clear()
        learnedMap.clear(); correctMap.clear(); wrongMap.clear()
        val d = db.readableDatabase
        d.rawQuery("SELECT day, seconds FROM ${Db.TABLE_DAY_TIME} WHERE profile_id=?", arrayOf(p)).use {
            while (it.moveToNext()) timeByDay[it.getString(0)] = it.getLong(1)
        }
        d.rawQuery(
            "SELECT category, item_key, box, pass_count, learned FROM ${Db.TABLE_MASTERY} WHERE profile_id=?",
            arrayOf(p)
        ).use {
            while (it.moveToNext()) {
                val cat = it.getString(0); val item = it.getString(1)
                boxMap[cacheKey(cat, item)] = it.getInt(2)
                passMap[cacheKey(cat, item)] = it.getInt(3)
                if (it.getInt(4) == 1) learnedMap.getOrPut(cat) { HashSet() }.add(item)
            }
        }
        d.rawQuery(
            "SELECT category, correct, wrong FROM ${Db.TABLE_CAT_COUNTERS} WHERE profile_id=?",
            arrayOf(p)
        ).use {
            while (it.moveToNext()) {
                correctMap[it.getString(0)] = it.getInt(1)
                wrongMap[it.getString(0)] = it.getInt(2)
            }
        }
    }

    // ---- Temps ----

    fun addTime(seconds: Long) {
        if (seconds <= 0) return
        ensureLoaded()
        val day = today()
        val total = (timeByDay[day] ?: 0L) + seconds
        timeByDay[day] = total
        db.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO ${Db.TABLE_DAY_TIME}(profile_id, day, seconds) VALUES(?, ?, ?)",
            arrayOf<Any>(pid(), day, total)
        )
        version.intValue++
    }

    fun timeLastDays(days: Int): List<Pair<String, Long>> {
        ensureLoaded()
        val cal = Calendar.getInstance()
        val out = ArrayList<Pair<String, Long>>()
        for (i in days - 1 downTo 0) {
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -i)
            val date = dateFmt.format(c.time)
            out.add(date to (timeByDay[date] ?: 0L))
        }
        return out
    }

    fun totalTimeSeconds(): Long {
        ensureLoaded()
        return timeByDay.values.sum()
    }

    // ---- Réponses & maîtrise ----

    fun recordAnswer(category: String, item: String, correct: Boolean) {
        ensureLoaded()
        val ik = cacheKey(category, item)
        var box = boxMap[ik] ?: 0
        var pass = passMap[ik] ?: 0
        var learnedChanged = false
        if (correct) {
            correctMap[category] = (correctMap[category] ?: 0) + 1
            pass += 1
            box = (box + 1).coerceAtMost(MAX_BOX)
            if (pass >= LEARN_THRESHOLD) {
                learnedChanged = learnedMap.getOrPut(category) { HashSet() }.add(item)
            }
        } else {
            wrongMap[category] = (wrongMap[category] ?: 0) + 1
            box = (box - 1).coerceAtLeast(0)
        }
        boxMap[ik] = box
        passMap[ik] = pass

        val p = pid()
        val d = db.writableDatabase
        d.execSQL(
            "INSERT OR REPLACE INTO ${Db.TABLE_MASTERY}" +
                "(profile_id, category, item_key, box, pass_count, learned) VALUES(?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(p, category, item, box, pass, if (isLearned(category, item)) 1 else 0)
        )
        d.insertWithOnConflict(
            Db.TABLE_CAT_COUNTERS, null,
            ContentValues().apply {
                put("profile_id", p)
                put("category", category)
                put("correct", correctMap[category] ?: 0)
                put("wrong", wrongMap[category] ?: 0)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        if (learnedChanged) writeSnapshot(p, today(), totalLearned())
        version.intValue++
    }

    private fun isLearned(category: String, item: String): Boolean =
        learnedMap[category]?.contains(item) == true

    /** Boîte Leitner d'un item (0 = jamais réussi … 5 = bien maîtrisé). */
    fun box(category: String, item: String): Int {
        ensureLoaded()
        return boxMap[cacheKey(category, item)] ?: 0
    }

    fun learnedKeys(category: String): Set<String> {
        ensureLoaded()
        return learnedMap[category] ?: emptySet()
    }

    fun learnedCount(category: String): Int = learnedKeys(category).size

    fun correctCount(category: String): Int {
        ensureLoaded(); return correctMap[category] ?: 0
    }

    fun wrongCount(category: String): Int {
        ensureLoaded(); return wrongMap[category] ?: 0
    }

    /** Total d'items maîtrisés (toutes catégories) pour le profil courant. */
    fun totalLearned(): Int {
        ensureLoaded()
        return learnedMap.values.sumOf { it.size }
    }

    /**
     * Courbe de progression : total cumulé d'items maîtrisés pour chacun des
     * [days] derniers jours. Les jours sans snapshot reprennent la dernière
     * valeur connue (report en avant), la courbe est donc monotone croissante.
     */
    fun learnedTotalLastDays(days: Int): List<Pair<String, Int>> {
        ensureLoaded()
        val snaps = ArrayList<Pair<String, Int>>()
        db.readableDatabase.rawQuery(
            "SELECT day, learned_total FROM ${Db.TABLE_SNAPSHOT} WHERE profile_id=? ORDER BY day ASC",
            arrayOf(pid())
        ).use {
            while (it.moveToNext()) snaps.add(it.getString(0) to it.getInt(1))
        }
        val cal = Calendar.getInstance()
        val out = ArrayList<Pair<String, Int>>()
        for (i in days - 1 downTo 0) {
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -i)
            val date = dateFmt.format(c.time)
            val value = snaps.lastOrNull { it.first <= date }?.second ?: 0
            out.add(date to value)
        }
        return out
    }

    private fun writeSnapshot(profileId: String, day: String, total: Int) {
        db.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO ${Db.TABLE_SNAPSHOT}(profile_id, day, learned_total) VALUES(?, ?, ?)",
            arrayOf<Any>(profileId, day, total)
        )
    }

    /** Réinitialise les données du profil courant uniquement. */
    fun resetAll() {
        val p = pid()
        deleteProfileData(p)
        // Trace la remise à zéro du jour pour que la courbe reflète le reset.
        writeSnapshot(p, today(), 0)
        version.intValue++
    }

    /** Supprime toutes les stats d'un profil (utilisé aussi à la suppression du profil). */
    fun deleteProfileData(profileId: String) {
        val d = db.writableDatabase
        val where = "profile_id=?"
        val args = arrayOf(profileId)
        d.delete(Db.TABLE_DAY_TIME, where, args)
        d.delete(Db.TABLE_MASTERY, where, args)
        d.delete(Db.TABLE_CAT_COUNTERS, where, args)
        d.delete(Db.TABLE_SNAPSHOT, where, args)
        if (loadedPid == profileId) loadedPid = null // force un rechargement propre
    }

    // ---- Migration unique des anciens SharedPreferences ----

    private fun migrateFromPrefsIfNeeded(context: Context) {
        val d = db.writableDatabase
        val done = d.rawQuery(
            "SELECT value FROM ${Db.TABLE_META} WHERE key='stats_migrated'", null
        ).use { it.moveToFirst() }
        if (done) return

        val sp = context.applicationContext
            .getSharedPreferences("goodbrainrot_stats", Context.MODE_PRIVATE)
        d.beginTransaction()
        try {
            // Chaque clé est de la forme "<pid>|<reste>".
            for ((key, value) in sp.all) {
                val sep = key.indexOf('|')
                if (sep <= 0) continue
                val p = key.substring(0, sep)
                val rest = key.substring(sep + 1)
                when {
                    rest.startsWith("t_") -> {
                        val day = rest.substring(2)
                        d.execSQL(
                            "INSERT OR REPLACE INTO ${Db.TABLE_DAY_TIME}(profile_id, day, seconds) VALUES(?, ?, ?)",
                            arrayOf<Any>(p, day, (value as? Long) ?: 0L)
                        )
                    }
                    rest.startsWith("correct_") -> upsertCounter(d, p, rest.substring(8), correct = (value as? Int) ?: 0, wrong = null)
                    rest.startsWith("wrong_") -> upsertCounter(d, p, rest.substring(6), correct = null, wrong = (value as? Int) ?: 0)
                    rest.startsWith("box_") -> migrateMasteryField(d, p, rest.substring(4), field = "box", intValue = (value as? Int) ?: 0)
                    rest.startsWith("pc_") -> migrateMasteryField(d, p, rest.substring(3), field = "pass_count", intValue = (value as? Int) ?: 0)
                    rest.startsWith("learned_") -> {
                        val cat = rest.substring(8)
                        @Suppress("UNCHECKED_CAST")
                        val set = (value as? Set<String>) ?: emptySet()
                        for (item in set) migrateMasteryField(d, p, "${cat}_$item", field = "learned", intValue = 1, catItemPreSplit = cat to item)
                    }
                }
            }
            putMeta(d, "stats_migrated", "1")
            d.setTransactionSuccessful()
        } catch (_: Exception) {
        } finally {
            d.endTransaction()
        }
    }

    private fun upsertCounter(
        d: android.database.sqlite.SQLiteDatabase, p: String, category: String, correct: Int?, wrong: Int?
    ) {
        val cur = d.rawQuery(
            "SELECT correct, wrong FROM ${Db.TABLE_CAT_COUNTERS} WHERE profile_id=? AND category=?",
            arrayOf(p, category)
        ).use { if (it.moveToFirst()) it.getInt(0) to it.getInt(1) else 0 to 0 }
        d.execSQL(
            "INSERT OR REPLACE INTO ${Db.TABLE_CAT_COUNTERS}(profile_id, category, correct, wrong) VALUES(?, ?, ?, ?)",
            arrayOf<Any>(p, category, correct ?: cur.first, wrong ?: cur.second)
        )
    }

    /**
     * Les anciennes clés box_/pc_ encodent "category_item" sans séparateur fiable
     * (le nom de catégorie peut contenir des '_'). On retrouve le couple via les
     * catégories connues ; à défaut on tente un découpage best-effort.
     */
    private fun migrateMasteryField(
        d: android.database.sqlite.SQLiteDatabase, p: String, catUnderItem: String,
        field: String, intValue: Int, catItemPreSplit: Pair<String, String>? = null
    ) {
        val (cat, item) = catItemPreSplit ?: splitCatItem(catUnderItem)
        // Lecture des valeurs existantes pour ne pas écraser les autres champs.
        val existing = d.rawQuery(
            "SELECT box, pass_count, learned FROM ${Db.TABLE_MASTERY} WHERE profile_id=? AND category=? AND item_key=?",
            arrayOf(p, cat, item)
        ).use { if (it.moveToFirst()) Triple(it.getInt(0), it.getInt(1), it.getInt(2)) else Triple(0, 0, 0) }
        var (box, pass, learned) = existing
        when (field) {
            "box" -> box = intValue
            "pass_count" -> pass = intValue
            "learned" -> learned = intValue
        }
        d.execSQL(
            "INSERT OR REPLACE INTO ${Db.TABLE_MASTERY}" +
                "(profile_id, category, item_key, box, pass_count, learned) VALUES(?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(p, cat, item, box, pass, learned)
        )
    }

    private val KNOWN_CATEGORIES = listOf(
        "animals", "vehicles", "dinos", "number", "letter", "color", "shape", "count"
    )

    private fun splitCatItem(s: String): Pair<String, String> {
        for (c in KNOWN_CATEGORIES) {
            if (s.startsWith("${c}_")) return c to s.substring(c.length + 1)
        }
        val i = s.indexOf('_')
        return if (i >= 0) s.substring(0, i) to s.substring(i + 1) else s to s
    }

    private fun putMeta(d: android.database.sqlite.SQLiteDatabase, key: String, value: String) {
        d.execSQL(
            "INSERT OR REPLACE INTO ${Db.TABLE_META}(key, value) VALUES(?, ?)",
            arrayOf(key, value)
        )
    }
}
