package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object PersonMigration4To5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS persons (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, avatarFile TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS node_person (nodeId TEXT NOT NULL, personId TEXT NOT NULL, PRIMARY KEY(nodeId, personId), FOREIGN KEY(nodeId) REFERENCES nodes(id) ON DELETE CASCADE, FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_node_person_personId ON node_person(personId)")
    }
}
