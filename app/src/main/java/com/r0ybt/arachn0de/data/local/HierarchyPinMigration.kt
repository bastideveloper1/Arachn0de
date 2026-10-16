package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object HierarchyPinMigration40To41:Migration(40,41) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE nodes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    }
}
