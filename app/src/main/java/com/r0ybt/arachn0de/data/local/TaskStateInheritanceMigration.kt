package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TaskStateInheritanceMigration50To51:Migration(50,51) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE nodes ADD COLUMN taskStateCount INTEGER DEFAULT NULL")
        for(name in listOf("verification_node_insert","verification_node_update","verification_project_update")) db.execSQL("DROP TRIGGER IF EXISTS $name")
        VerificationInvariants.install(db)
        TaskStateCountInvariants.install(db)
    }
}
internal object TaskStateCountInvariants {
    fun install(db:SupportSQLiteDatabase) {
        for(event in listOf("INSERT","UPDATE")) db.execSQL("""CREATE TRIGGER IF NOT EXISTS task_state_count_${event.lowercase()} BEFORE $event ON nodes
            WHEN NEW.taskStateCount IS NOT NULL AND NEW.taskStateCount NOT IN (2,3)
            BEGIN SELECT RAISE(ABORT,'Cantidad de estados inválida'); END""")
    }
}
