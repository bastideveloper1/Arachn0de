package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CreationDefaultsMigration13To14:Migration(13,14) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS creation_defaults (id TEXT NOT NULL PRIMARY KEY, projectId TEXT, nodeId TEXT, purpose TEXT, obligation INTEGER, currency TEXT, priority TEXT, tagsOverride INTEGER NOT NULL, peopleOverride INTEGER NOT NULL, startRule TEXT, startNumber INTEGER, startMinute INTEGER, dueRule TEXT, dueNumber INTEGER, dueMinute INTEGER, FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE CASCADE, FOREIGN KEY(projectId,nodeId) REFERENCES nodes(projectId,id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_creation_defaults_projectId ON creation_defaults(projectId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_creation_defaults_projectId_nodeId ON creation_defaults(projectId,nodeId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS creation_defaults_tag (defaultsId TEXT NOT NULL, tagId TEXT NOT NULL, PRIMARY KEY(defaultsId,tagId), FOREIGN KEY(defaultsId) REFERENCES creation_defaults(id) ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS creation_defaults_person (defaultsId TEXT NOT NULL, personId TEXT NOT NULL, PRIMARY KEY(defaultsId,personId), FOREIGN KEY(defaultsId) REFERENCES creation_defaults(id) ON DELETE CASCADE, FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE CASCADE)")
        listOf("tag" to "tagId","person" to "personId").forEach { (table,column) ->
            db.execSQL("CREATE INDEX IF NOT EXISTS index_creation_defaults_${table}_defaultsId ON creation_defaults_$table(defaultsId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_creation_defaults_${table}_$column ON creation_defaults_$table($column)")
        }
    }
}
