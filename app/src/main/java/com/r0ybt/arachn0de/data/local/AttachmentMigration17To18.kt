package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AttachmentMigration17To18 : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE attachment_files (id TEXT NOT NULL PRIMARY KEY, storageName TEXT NOT NULL, originalName TEXT NOT NULL, mimeType TEXT NOT NULL, byteSize INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, sha256 TEXT NOT NULL, createdAt INTEGER NOT NULL, lifecycleState TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX index_attachment_files_storageName ON attachment_files(storageName)")
        for ((table, owner, parent) in listOf(Triple("node_attachments", "nodeId", "nodes"), Triple("project_attachments", "projectId", "projects"))) {
            db.execSQL("CREATE TABLE $table ($owner TEXT NOT NULL, attachmentId TEXT NOT NULL, PRIMARY KEY ($owner, attachmentId), FOREIGN KEY ($owner) REFERENCES $parent(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY (attachmentId) REFERENCES attachment_files(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX index_${table}_attachmentId ON $table(attachmentId)")
        }
        AttachmentInvariants.install(db)
    }
}

internal object AttachmentInvariants {
    fun install(db: SupportSQLiteDatabase) {
        for (table in listOf("node_attachments", "project_attachments")) {
            db.execSQL("CREATE TRIGGER IF NOT EXISTS ${table}_ready BEFORE INSERT ON $table WHEN NOT EXISTS (SELECT 1 FROM attachment_files WHERE id = NEW.attachmentId AND lifecycleState = 'READY') BEGIN SELECT RAISE(ABORT, 'Attachment not ready'); END")
        }
        db.execSQL("CREATE TRIGGER IF NOT EXISTS attachment_state_insert BEFORE INSERT ON attachment_files WHEN NEW.lifecycleState NOT IN ('READY','DELETE_PENDING') BEGIN SELECT RAISE(ABORT, 'Invalid attachment state'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS attachment_state_update BEFORE UPDATE OF lifecycleState ON attachment_files WHEN NEW.lifecycleState NOT IN ('READY','DELETE_PENDING') OR (NEW.lifecycleState = 'DELETE_PENDING' AND (EXISTS(SELECT 1 FROM node_attachments WHERE attachmentId = NEW.id) OR EXISTS(SELECT 1 FROM project_attachments WHERE attachmentId = NEW.id))) BEGIN SELECT RAISE(ABORT, 'Invalid attachment state'); END")
    }
}
