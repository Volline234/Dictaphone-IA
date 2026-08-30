package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.VoiceNote

@Database(entities = [VoiceNote::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voiceNoteDao(): VoiceNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // =====================================================================
        // MIGRATIONS — Add a new Migration block here for each schema update.
        //
        // Example for V1 → V2 (adding a new column):
        //   val MIGRATION_1_2 = object : Migration(1, 2) {
        //       override fun migrate(db: SupportSQLiteDatabase) {
        //           db.execSQL("ALTER TABLE voice_notes ADD COLUMN newField TEXT DEFAULT '' NOT NULL")
        //       }
        //   }
        //
        // Then add it to .addMigrations(MIGRATION_1_2) below.
        // NEVER use fallbackToDestructiveMigration() — it silently deletes
        // all user data (recordings, transcriptions, favorites) on schema changes.
        // =====================================================================

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dictaphone_ia_database"
                )
                // Add future migrations here, e.g.: .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
