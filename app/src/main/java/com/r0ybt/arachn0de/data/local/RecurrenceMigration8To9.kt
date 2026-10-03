package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object RecurrenceMigration8To9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS recurrence_rules (
            id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, parentId TEXT,
            title TEXT NOT NULL, description TEXT NOT NULL, amountMinor INTEGER, currencyCode TEXT,
            startDay INTEGER NOT NULL, frequency TEXT NOT NULL, interval INTEGER NOT NULL, endDay INTEGER,
            nextIndex INTEGER NOT NULL, status TEXT NOT NULL, zoneId TEXT NOT NULL,
            dueMinute INTEGER NOT NULL, startOffsetMillis INTEGER)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurrence_rules_status ON recurrence_rules(status)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS recurrence_occurrences (
            ruleId TEXT NOT NULL, day INTEGER NOT NULL, nodeId TEXT NOT NULL, PRIMARY KEY(ruleId, day),
            FOREIGN KEY(ruleId) REFERENCES recurrence_rules(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recurrence_occurrences_nodeId ON recurrence_occurrences(nodeId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS recurrence_person (
            ruleId TEXT NOT NULL, personId TEXT NOT NULL, PRIMARY KEY(ruleId, personId),
            FOREIGN KEY(ruleId) REFERENCES recurrence_rules(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(personId) REFERENCES persons(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurrence_person_personId ON recurrence_person(personId)")
    }
}
