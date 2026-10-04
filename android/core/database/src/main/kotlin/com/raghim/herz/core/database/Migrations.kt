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
