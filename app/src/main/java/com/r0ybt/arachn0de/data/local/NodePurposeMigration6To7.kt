package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object NodePurposeMigration6To7 : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE nodes ADD COLUMN purpose TEXT NOT NULL DEFAULT 'ACTION'")
        NodePurposeInvariants.install(db)
    }
}

/** Additional guards leave the existing structure/cycle/completion triggers intact. */
internal object NodePurposeInvariants {
    fun install(db: SupportSQLiteDatabase) {
        for ((suffix, event) in listOf("insert" to "INSERT", "update" to "UPDATE OF purpose, isCompleted")) {
            db.execSQL("""
                CREATE TRIGGER nodes_purpose_$suffix BEFORE $event ON nodes
                WHEN NEW.purpose NOT IN ('ACTION', 'NOTE') OR
                    (NEW.purpose = 'NOTE' AND (NEW.isCompleted != 0 OR EXISTS(
                        SELECT 1 FROM nodes WHERE parentId = NEW.id
                    )))
                BEGIN SELECT RAISE(ABORT, 'Notes must be uncompleted leaves'); END
            """.trimIndent())
        }
        for ((suffix, event) in listOf("insert" to "INSERT", "move" to "UPDATE OF parentId")) {
            db.execSQL("""
                CREATE TRIGGER nodes_no_note_parent_$suffix BEFORE $event ON nodes
                WHEN EXISTS(SELECT 1 FROM nodes WHERE id = NEW.parentId AND purpose = 'NOTE')
                BEGIN SELECT RAISE(ABORT, 'Notes cannot receive children'); END
            """.trimIndent())
        }
    }
}
