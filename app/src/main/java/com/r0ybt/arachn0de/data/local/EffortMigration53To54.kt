package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object EffortMigration53To54:Migration(53,54) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE nodes ADD COLUMN effort INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE conversion_roots ADD COLUMN effort INTEGER NOT NULL DEFAULT 0")
        EffortInvariants.install(db)
    }
}
internal object EffortInvariants {
    fun install(db:SupportSQLiteDatabase) {
        for(table in listOf("nodes","conversion_roots")) for(event in listOf("INSERT","UPDATE")) {
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS effort_${table}_${event.lowercase()} BEFORE $event ON $table
                WHEN typeof(NEW.effort)<>'integer' OR NEW.effort NOT BETWEEN 0 AND 5
                BEGIN SELECT RAISE(ABORT,'Esfuerzo inválido'); END""")
        }
    }
}
