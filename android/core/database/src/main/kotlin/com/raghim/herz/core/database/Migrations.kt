// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the doors table. Cameras and sessions are unchanged. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `doors` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`area` TEXT, " +
                "`linkedCameraIds` TEXT NOT NULL, " +
                "`isOnline` INTEGER NOT NULL, " +
                "`addedAtEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

/** Lets a door be chosen for the Live door panel. Existing doors stay off the panel. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `doors` ADD COLUMN `onLivePanel` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Sensors, plus the alarm id and highlight flag stored with each live-wall camera. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `active_sessions` ADD COLUMN `alarmId` TEXT")
        db.execSQL("ALTER TABLE `active_sessions` ADD COLUMN `highlight` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sensors` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`area` TEXT, " +
                "`linkedCameraIds` TEXT NOT NULL, " +
                "`highlightOnAlarm` INTEGER NOT NULL, " +
                "`alarmStyle` TEXT NOT NULL, " +
                "`soundId` TEXT NOT NULL, " +
                "`addedAtEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

/** Remembers the order the user arranged on the Cameras tab. Existing cameras keep name order. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cameras` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
        val cursor = db.query("SELECT id FROM cameras ORDER BY name COLLATE NOCASE")
        cursor.use { rows ->
            var index = 0
            while (rows.moveToNext()) {
                db.execSQL(
                    "UPDATE cameras SET sortOrder = ? WHERE id = ?",
                    arrayOf(index, rows.getString(0)),
                )
                index++
            }
        }
    }
}

/** Remembers the order the user arranged on the Doors tab. Existing doors keep name order. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `doors` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
        val cursor = db.query("SELECT id FROM doors ORDER BY name COLLATE NOCASE")
        cursor.use { rows ->
            var index = 0
            while (rows.moveToNext()) {
                db.execSQL(
                    "UPDATE doors SET sortOrder = ? WHERE id = ?",
                    arrayOf(index, rows.getString(0)),
                )
                index++
            }
        }
    }
}

/** Remembers the order the user arranged on the Sensors tab. Existing sensors keep name order. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sensors` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
        val cursor = db.query("SELECT id FROM sensors ORDER BY name COLLATE NOCASE")
        cursor.use { rows ->
            var index = 0
            while (rows.moveToNext()) {
                db.execSQL(
                    "UPDATE sensors SET sortOrder = ? WHERE id = ?",
                    arrayOf(index, rows.getString(0)),
                )
                index++
            }
        }
    }
}
