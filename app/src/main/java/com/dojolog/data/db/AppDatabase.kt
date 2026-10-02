package com.dojolog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class,
        TechniqueEntity::class,
        SessionTechniqueEntity::class,
        OpponentEntity::class,
        SessionMatchupEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun techniqueDao(): TechniqueDao
    abstract fun opponentDao(): OpponentDao

    companion object {
        const val NAME = "dojolog.db"

        fun create(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** Version 2 adds opponents and the matchups against them; nothing else changes. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `opponents` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL COLLATE NOCASE, " +
                "`club` TEXT NOT NULL, " +
                "`grade` TEXT NOT NULL, " +
                "`weight` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_opponents_name` ON `opponents` (`name`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_matchups` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`opponentId` INTEGER NOT NULL, " +
                "`result` TEXT NOT NULL, " +
                "`rating` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`opponentId`) REFERENCES `opponents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_matchups_sessionId` ON `session_matchups` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_matchups_opponentId` ON `session_matchups` (`opponentId`)")
    }
}
