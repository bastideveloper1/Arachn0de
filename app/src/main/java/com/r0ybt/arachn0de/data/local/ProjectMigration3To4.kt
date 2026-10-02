package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Rebuilds the projects table without losing data and assigns a deterministic persistent order. */
internal object ProjectMigration3To4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects RENAME TO projects_legacy")
        db.execSQL(
            """
            CREATE TABLE projects (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                position INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO projects(id, name, description, position, createdAt, updatedAt)
            SELECT id, name, description,
                   ROW_NUMBER() OVER (ORDER BY createdAt ASC, id ASC) - 1,
                   createdAt, updatedAt
            FROM projects_legacy
            ORDER BY createdAt ASC, id ASC
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE projects_legacy")
    }
}
