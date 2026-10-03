package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TagMigration9To10 : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tags (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, normalizedName TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_normalizedName ON tags(normalizedName)")
        for ((table, column, parent) in listOf(Triple("node_tag", "nodeId", "nodes"), Triple("recurrence_tag", "ruleId", "recurrence_rules"))) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $table ($column TEXT NOT NULL, tagId TEXT NOT NULL, PRIMARY KEY($column,tagId), FOREIGN KEY($column) REFERENCES $parent(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_$column ON $table($column)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_tagId ON $table(tagId)")
        }
    }
}
