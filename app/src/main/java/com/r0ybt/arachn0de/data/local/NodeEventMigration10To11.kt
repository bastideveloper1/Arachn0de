package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object NodeEventMigration10To11 : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS node_events (id TEXT NOT NULL PRIMARY KEY, nodeId TEXT NOT NULL, type TEXT NOT NULL, occurredAt INTEGER NOT NULL, FOREIGN KEY(nodeId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_node_events_nodeId_occurredAt ON node_events(nodeId, occurredAt)")
        // Existing createdAt remains intact. No reconstructed events or invented completion dates.
    }
}
