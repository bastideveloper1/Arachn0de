package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Separate completion from Sprint phase without rewriting any persisted data. */
object SprintMigration16To17 : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS nodes_sprint_insert")
        db.execSQL("DROP TRIGGER IF EXISTS nodes_sprint_update")
        SprintInvariants.install(db)
    }
}
