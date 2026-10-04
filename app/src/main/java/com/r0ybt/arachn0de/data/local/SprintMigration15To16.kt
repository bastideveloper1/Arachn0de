package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SprintMigration15To16 : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE nodes ADD COLUMN sprintMode INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE nodes ADD COLUMN workState TEXT")
        SprintInvariants.install(db, requireMatchingCompletion = true)
    }
}

/** Row guards allow atomic context normalization inside a transaction. */
internal object SprintInvariants {
    fun install(db: SupportSQLiteDatabase, requireMatchingCompletion: Boolean = false) {
        for ((suffix, event) in listOf("insert" to "INSERT", "update" to "UPDATE")) {
            db.execSQL("""
                CREATE TRIGGER nodes_sprint_$suffix BEFORE $event ON nodes
                WHEN (NEW.sprintMode NOT IN (0,1)) OR
                    (NEW.sprintMode=1 AND NEW.purpose!='LAYER') OR
                    (NEW.workState IS NOT NULL AND (
                        NEW.purpose!='ACTION' OR
                        NEW.workState NOT IN ('UNPLANNED','PLANNED','DOING','DONE','VALIDATED')
                        ${if (requireMatchingCompletion) "OR NEW.isCompleted != CASE WHEN NEW.workState IN ('DONE','VALIDATED') THEN 1 ELSE 0 END" else ""}))
                BEGIN SELECT RAISE(ABORT,'Invalid Sprint mode or work state'); END
            """.trimIndent())
        }
    }
}
