package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PriorityMigration11To12 : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE nodes ADD COLUMN priority TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE recurrence_rules ADD COLUMN priority TEXT NOT NULL DEFAULT 'NONE'")
    }
}
