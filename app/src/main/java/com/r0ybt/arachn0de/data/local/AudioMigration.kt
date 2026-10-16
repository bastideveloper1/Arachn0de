package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object AudioMigration41To42:Migration(41,42) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE attachment_files ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE attachment_files ADD COLUMN playbackMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE attachment_files ADD COLUMN bookmarkMs INTEGER")
        db.execSQL("CREATE TABLE IF NOT EXISTS audio_recording_operations(id TEXT NOT NULL,state TEXT NOT NULL,nodeId TEXT,provisionalTitle TEXT,PRIMARY KEY(id))")
    }
}
