package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Never split an existing name or discard it when promoting the nickname to public identity. */
internal object PersonPersonalDataMigration46To47 : Migration(46,47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("givenName","familyName","documentId").forEach {
            db.execSQL("ALTER TABLE person_profiles ADD COLUMN $it TEXT NOT NULL DEFAULT ''")
        }
        db.execSQL("""INSERT OR IGNORE INTO person_profiles
            (personId,alias,description,notes,phones,emails,addresses,links,qualities,skills,interests,availability)
            SELECT id,'','','','','','','','','','','' FROM persons""")
        db.execSQL("UPDATE person_profiles SET givenName=(SELECT name FROM persons WHERE persons.id=person_profiles.personId)")
        db.execSQL("""UPDATE persons SET name=COALESCE(NULLIF(TRIM(
            (SELECT alias FROM person_profiles WHERE person_profiles.personId=persons.id)),''),name)""")
    }
}
