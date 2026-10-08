package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AvatarMigration19To20 : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE persons ADD COLUMN avatarZoom REAL NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE persons ADD COLUMN avatarX REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE persons ADD COLUMN avatarY REAL NOT NULL DEFAULT 0")
    }
}
