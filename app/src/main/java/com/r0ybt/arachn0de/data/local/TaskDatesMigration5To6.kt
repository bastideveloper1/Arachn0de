package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object TaskDatesMigration5To6 : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE nodes ADD COLUMN startAt INTEGER")
        db.execSQL("ALTER TABLE nodes ADD COLUMN dueAt INTEGER")
    }
}
