package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ObligationMigration7To8 : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE nodes ADD COLUMN amountMinor INTEGER")
        db.execSQL("ALTER TABLE nodes ADD COLUMN currencyCode TEXT")
        ObligationInvariants.install(db)
    }
}

internal object ObligationInvariants {
    fun install(db: SupportSQLiteDatabase) {
        for ((suffix, event) in listOf("insert" to "INSERT", "update" to "UPDATE OF amountMinor, currencyCode, purpose")) {
            db.execSQL("""
                CREATE TRIGGER nodes_obligation_$suffix BEFORE $event ON nodes
                WHEN (NEW.amountMinor IS NULL) != (NEW.currencyCode IS NULL) OR
                    (NEW.amountMinor IS NOT NULL AND (
                        typeof(NEW.amountMinor) != 'integer' OR NEW.amountMinor <= 0 OR
                        typeof(NEW.currencyCode) != 'text' OR length(NEW.currencyCode) != 3 OR
                        NEW.currencyCode GLOB '*[^A-Z]*' OR NEW.purpose != 'ACTION' OR
                        EXISTS(SELECT 1 FROM nodes WHERE parentId = NEW.id)
                    ))
                BEGIN SELECT RAISE(ABORT, 'Invalid obligation'); END
            """.trimIndent())
        }
        for ((suffix, event) in listOf("insert" to "INSERT", "move" to "UPDATE OF parentId")) {
            db.execSQL("""
                CREATE TRIGGER nodes_no_obligation_parent_$suffix BEFORE $event ON nodes
                WHEN EXISTS(SELECT 1 FROM nodes WHERE id = NEW.parentId AND amountMinor IS NOT NULL)
                BEGIN SELECT RAISE(ABORT, 'Obligations cannot receive children'); END
            """.trimIndent())
        }
    }
}
