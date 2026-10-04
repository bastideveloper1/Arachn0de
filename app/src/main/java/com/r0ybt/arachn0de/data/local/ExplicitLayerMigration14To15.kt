package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Infer containers while old relationships are still intact, then enforce explicit kinds. */
object ExplicitLayerMigration14To15 : Migration(14,15) {
    override fun migrate(db:SupportSQLiteDatabase) {
        ExplicitLayerInvariants.dropOldPurposeGuards(db)
        db.execSQL("UPDATE nodes SET purpose='LAYER', isCompleted=0 WHERE EXISTS (SELECT 1 FROM nodes child WHERE child.parentId=nodes.id)")
        ExplicitLayerInvariants.install(db)
    }
}

internal object ExplicitLayerInvariants {
    fun dropOldPurposeGuards(db:SupportSQLiteDatabase) {
        listOf("nodes_purpose_insert","nodes_purpose_update","nodes_no_note_parent_insert","nodes_no_note_parent_move").forEach { db.execSQL("DROP TRIGGER IF EXISTS $it") }
    }
    fun install(db:SupportSQLiteDatabase) {
        dropOldPurposeGuards(db)
        for((suffix,event) in listOf("insert" to "INSERT","update" to "UPDATE OF purpose, isCompleted")) {
            db.execSQL("""
                CREATE TRIGGER nodes_purpose_$suffix BEFORE $event ON nodes
                WHEN NEW.purpose NOT IN ('ACTION','NOTE','LAYER') OR
                    (NEW.purpose != 'ACTION' AND NEW.isCompleted != 0) OR
                    (NEW.purpose != 'LAYER' AND EXISTS(SELECT 1 FROM nodes WHERE parentId=NEW.id))
                BEGIN SELECT RAISE(ABORT,'Invalid node purpose or completion'); END
            """.trimIndent())
        }
        for((suffix,event) in listOf("insert" to "INSERT","move" to "UPDATE OF parentId")) {
            db.execSQL("""
                CREATE TRIGGER nodes_no_note_parent_$suffix BEFORE $event ON nodes
                WHEN EXISTS(SELECT 1 FROM nodes WHERE id=NEW.parentId AND purpose!='LAYER')
                BEGIN SELECT RAISE(ABORT,'Only layers can receive children'); END
            """.trimIndent())
        }
    }
}
