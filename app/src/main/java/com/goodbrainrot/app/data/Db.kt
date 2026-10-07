package com.goodbrainrot.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Persistance locale **SQLite** (remplace les anciens SharedPreferences).
 *
 * Une seule base partagée par les profils ([Profiles]) et les statistiques
 * d'apprentissage ([Stats]). Les données restent minuscules (quelques enfants
 * × quelques centaines d'items) : l'accès est synchrone direct, exactement
 * comme l'ancien cache mémoire des SharedPreferences, ce qui préserve le
 * pattern réactif Compose existant.
 *
 * La table [TABLE_SNAPSHOT] (total d'items maîtrisés par jour) alimente la
 * courbe de progression de l'espace parents.
 */
internal class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_PROFILES (" +
                "id TEXT PRIMARY KEY, name TEXT, avatar TEXT, " +
                "age_months INTEGER, xp INTEGER, sort INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE $TABLE_DAY_TIME (" +
                "profile_id TEXT, day TEXT, seconds INTEGER, " +
                "PRIMARY KEY(profile_id, day))"
        )
        db.execSQL(
            "CREATE TABLE $TABLE_MASTERY (" +
                "profile_id TEXT, category TEXT, item_key TEXT, " +
                "box INTEGER, pass_count INTEGER, learned INTEGER, " +
                "PRIMARY KEY(profile_id, category, item_key))"
        )
        db.execSQL(
            "CREATE TABLE $TABLE_CAT_COUNTERS (" +
                "profile_id TEXT, category TEXT, correct INTEGER, wrong INTEGER, " +
                "PRIMARY KEY(profile_id, category))"
        )
        db.execSQL(
            "CREATE TABLE $TABLE_SNAPSHOT (" +
                "profile_id TEXT, day TEXT, learned_total INTEGER, " +
                "PRIMARY KEY(profile_id, day))"
        )
        db.execSQL("CREATE TABLE $TABLE_META (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schéma v1 : rien à migrer pour l'instant. Les évolutions futures
        // (sync cloud, etc.) ajouteront des branches ici.
    }

    companion object {
        private const val NAME = "goodbrainrot.db"
        private const val VERSION = 1

        const val TABLE_PROFILES = "profiles"
        const val TABLE_DAY_TIME = "day_time"
        const val TABLE_MASTERY = "mastery"
        const val TABLE_CAT_COUNTERS = "cat_counters"
        const val TABLE_SNAPSHOT = "learn_snapshot"
        const val TABLE_META = "meta"

        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(context).also { instance = it }
            }
    }
}
