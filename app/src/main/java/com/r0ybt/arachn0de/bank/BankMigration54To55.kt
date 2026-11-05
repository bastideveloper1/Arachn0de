package com.r0ybt.arachn0de.bank

/** Room runs this reconstruction atomically. No business record is changed. */
internal object BankMigration54To55:androidx.room.migration.Migration(54,55) {
    override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
        // Stage exact rows without constraints before rebuilding the two related tables.
        db.execSQL("CREATE TEMP TABLE bank_operations_54 AS SELECT * FROM bank_operations")
        db.execSQL("CREATE TEMP TABLE bank_allocations_54 AS SELECT * FROM bank_allocations")
        val triggers=db.query("SELECT name,sql FROM sqlite_master WHERE type='trigger' AND tbl_name IN ('bank_operations','bank_allocations')").use {c->
            buildList {while(c.moveToNext())add(c.getString(0) to c.getString(1))}
        }
        // Restore triggers only after the exact rows, without executing them during the copy.
        triggers.forEach {(name,_)->db.execSQL("DROP TRIGGER `${name.replace("`","``")}`")}
        db.execSQL("DROP TABLE bank_allocations")
        // Remove only staged self-links before DROP (RESTRICT is immediate). Restored verbatim below.
        db.execSQL("UPDATE bank_operations SET correctsId=NULL")
        db.execSQL("DROP TABLE bank_operations")
        db.execSQL("CREATE TABLE IF NOT EXISTS bank_operations (`id` TEXT NOT NULL PRIMARY KEY, `rootId` TEXT NOT NULL, `contextAccountId` TEXT, `kind` TEXT NOT NULL, `amount` INTEGER NOT NULL, `day` INTEGER NOT NULL, `concept` TEXT NOT NULL, `sourceAccountId` TEXT, `destinationAccountId` TEXT, `sourcePersonId` TEXT, `obligationPersonId` TEXT, `correctsId` TEXT, `voided` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`rootId`) REFERENCES `bank_roots`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`contextAccountId`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`sourceAccountId`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`destinationAccountId`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`correctsId`) REFERENCES `bank_operations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_operations_rootId` ON `bank_operations` (`rootId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_operations_contextAccountId` ON `bank_operations` (`contextAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_operations_sourceAccountId` ON `bank_operations` (`sourceAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_operations_destinationAccountId` ON `bank_operations` (`destinationAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_operations_correctsId` ON `bank_operations` (`correctsId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bank_allocations (`id` TEXT NOT NULL PRIMARY KEY, `operationId` TEXT NOT NULL, `nodeId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `personId` TEXT, `personName` TEXT, `sourcePersonId` TEXT, `sourcePersonName` TEXT, `title` TEXT NOT NULL, `ruleId` TEXT, `occurrenceDay` INTEGER, `stateAt` INTEGER, `stateCount` INTEGER NOT NULL, FOREIGN KEY(`operationId`) REFERENCES `bank_operations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`nodeId`) REFERENCES `nodes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`ruleId`,`occurrenceDay`) REFERENCES `recurrence_occurrences`(`ruleId`,`day`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_allocations_operationId` ON `bank_allocations` (`operationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_allocations_nodeId` ON `bank_allocations` (`nodeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_allocations_ruleId_occurrenceDay` ON `bank_allocations` (`ruleId`,`occurrenceDay`)")
        db.execSQL("INSERT INTO bank_operations SELECT * FROM bank_operations_54")
        db.execSQL("INSERT INTO bank_allocations SELECT * FROM bank_allocations_54")
        for(table in listOf("bank_operations","bank_allocations")) {
            db.query("SELECT * FROM $table EXCEPT SELECT * FROM ${table}_54").use {check(!it.moveToFirst())}
            db.query("SELECT * FROM ${table}_54 EXCEPT SELECT * FROM $table").use {check(!it.moveToFirst())}
        }
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()) {"Relaciones financieras inválidas."}}
        triggers.forEach {(_,sql)->db.execSQL(sql)}
        db.execSQL("DROP TABLE bank_allocations_54")
        db.execSQL("DROP TABLE bank_operations_54")
    }
}
