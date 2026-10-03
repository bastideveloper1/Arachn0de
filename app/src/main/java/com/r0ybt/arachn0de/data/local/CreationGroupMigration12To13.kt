package com.r0ybt.arachn0de.data.local
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
object CreationGroupMigration12To13 : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE nodes ADD COLUMN creationGroupId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_nodes_creationGroupId ON nodes(creationGroupId)")
    }
}
