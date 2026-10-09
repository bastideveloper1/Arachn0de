package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ProjectPhotoMigration20To21 : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_photos (id TEXT NOT NULL PRIMARY KEY, projectId TEXT, nodeId TEXT, file TEXT NOT NULL, photoZoom REAL NOT NULL, photoX REAL NOT NULL, photoY REAL NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(nodeId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_photos_projectId ON project_photos(projectId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_photos_nodeId ON project_photos(nodeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_project_photos_file ON project_photos(file)")
    }
}
