package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object VerificationMigration49To50:Migration(49,50) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN verificationEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE nodes ADD COLUMN awaitingVerification INTEGER NOT NULL DEFAULT 0")
        VerificationInvariants.install(db,legacy=true)
    }
}
internal object VerificationInvariants {
    fun install(db:SupportSQLiteDatabase,legacy:Boolean=false) {
        for(event in listOf("INSERT","UPDATE")) db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS verification_node_${event.lowercase()} BEFORE $event ON nodes
            WHEN NEW.awaitingVerification NOT IN (0,1) OR (NEW.awaitingVerification=1 AND (
              NEW.isCompleted<>0 OR NEW.purpose<>'ACTION' OR NEW.workState IS NOT NULL OR
              NEW.projectId IS NULL OR ${if(legacy) "NOT EXISTS(SELECT 1 FROM projects WHERE id=NEW.projectId AND verificationEnabled=1) OR" else ""}
              EXISTS(SELECT 1 FROM nodes WHERE parentId=NEW.id)))
            BEGIN SELECT RAISE(ABORT,'Estado Por verificar incompatible'); END
        """.trimIndent())
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS verification_project_update BEFORE UPDATE OF verificationEnabled ON projects
            WHEN NEW.verificationEnabled NOT IN (0,1) OR ${if(legacy) "(NEW.verificationEnabled=0 AND EXISTS(SELECT 1 FROM nodes WHERE projectId=NEW.id AND awaitingVerification=1))" else "0"}
            BEGIN SELECT RAISE(ABORT,'Hay tareas por verificar'); END""")
        for(event in listOf("INSERT","UPDATE")) db.execSQL("""CREATE TRIGGER IF NOT EXISTS verification_parent_${event.lowercase()} BEFORE $event ON nodes
            WHEN NEW.parentId IS NOT NULL AND EXISTS(SELECT 1 FROM nodes WHERE id=NEW.parentId AND awaitingVerification=1)
            BEGIN SELECT RAISE(ABORT,'Verifica o reabre la tarea antes de añadir contenido'); END""")
    }
}
