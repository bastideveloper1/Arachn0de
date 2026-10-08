package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object TechnologyMigration18To19 : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS technologies (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, iconFile TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS node_technologies (nodeId TEXT NOT NULL, technologyId TEXT NOT NULL, PRIMARY KEY(nodeId, technologyId), FOREIGN KEY(nodeId) REFERENCES nodes(id) ON DELETE CASCADE, FOREIGN KEY(technologyId) REFERENCES technologies(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_node_technologies_technologyId ON node_technologies(technologyId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS project_technologies (projectId TEXT NOT NULL, technologyId TEXT NOT NULL, PRIMARY KEY(projectId, technologyId), FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE CASCADE, FOREIGN KEY(technologyId) REFERENCES technologies(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_project_technologies_technologyId ON project_technologies(technologyId)")
    }
}
