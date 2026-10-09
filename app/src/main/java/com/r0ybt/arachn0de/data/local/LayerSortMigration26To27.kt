package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object LayerSortMigration26To27:Migration(26,27) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE node_sort_preferences ADD COLUMN layersFirst INTEGER NOT NULL DEFAULT 0")
    }
}
