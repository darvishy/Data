package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [IdentityEntity::class, IdentityFtsEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nexus_hud_identity.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                db.execSQL("PRAGMA synchronous = NORMAL")
                                db.execSQL("PRAGMA temp_store = MEMORY")
                                db.execSQL("PRAGMA cache_size = -32000")
                                db.execSQL("PRAGMA mmap_size = 134217728")
                            } catch (_: Exception) {
                                // Safe fallback if specific PRAGMA unsupported on platform SQLite build
                            }
                        }
                    })
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
