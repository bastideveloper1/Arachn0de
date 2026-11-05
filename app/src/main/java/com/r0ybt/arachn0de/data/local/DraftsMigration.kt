package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Rebuilds the location column without losing CASCADE / SET NULL dependants.
 * Room's migration transaction makes both the schema and every restored row atomic.
 * Temporary snapshots stay in memory in encrypted vaults (temp_store=MEMORY).
 */
internal object DraftsMigration48To49 : Migration(48, 49) {
    override fun migrate(db:SupportSQLiteDatabase) {
        fun quote(s:String)="\"${s.replace("\"","\"\"")}\""
        val tables=mutableListOf<String>()
        val triggers=mutableListOf<Pair<String,String>>()
        val indexes=mutableListOf<String>()
        var nodeSql=""
        db.query("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE sql IS NOT NULL").use {c->
            while(c.moveToNext()) {
                val type=c.getString(0);val name=c.getString(1);val table=c.getString(2);val sql=c.getString(3)
                if(type=="table" && !name.startsWith("sqlite_") && name!="room_master_table" && name!="android_metadata") tables+=name
                if(type=="trigger") triggers+=name to sql
                if(type=="index" && table=="nodes") indexes+=sql
                if(type=="table" && name=="nodes") nodeSql=sql
            }
        }
        val nullable=nodeSql.replace(Regex("([`\"]?projectId[`\"]?\\s+TEXT)\\s+NOT\\s+NULL",RegexOption.IGNORE_CASE),"$1")
        check(nullable!=nodeSql) {"Expected non-null project location"}
        db.execSQL("PRAGMA defer_foreign_keys=ON")
        tables.forEachIndexed {i,t->db.execSQL("CREATE TEMP TABLE drafts_snapshot_$i AS SELECT * FROM ${quote(t)}")}
        triggers.forEach {(name,_)->db.execSQL("DROP TRIGGER ${quote(name)}")}
        // RESTRICT is an immediate action even for deferred FKs on some SQLite versions.
        // Remove those dependants only after snapshotting; their complete rows (and any
        // cascaded cycles) are restored below in the same migration transaction.
        tables.forEach {table->
            var restrictsNodes=false
            db.query("PRAGMA foreign_key_list(${quote(table)})").use {c->while(c.moveToNext()) {
                if(c.getString(2)=="nodes" && c.getString(6)=="RESTRICT") restrictsNodes=true
            }}
            if(restrictsNodes) db.execSQL("DELETE FROM ${quote(table)}")
        }
        // Avoid recursive self-CASCADE for deep existing hierarchies.
        db.execSQL("UPDATE nodes SET parentId=NULL")
        db.execSQL("DROP TABLE nodes")
        db.execSQL(nullable)
        indexes.forEach(db::execSQL)
        // Restore missing rows first, then values changed by SET NULL. Never REPLACE an
        // existing owner: REPLACE would cascade into dependants restored earlier.
        tables.forEachIndexed {i,t->db.execSQL("INSERT OR IGNORE INTO ${quote(t)} SELECT * FROM drafts_snapshot_$i")}
        tables.forEachIndexed {i,t->
            val columns=mutableListOf<String>();val keys=mutableListOf<String>()
            db.query("PRAGMA table_info(${quote(t)})").use {c->while(c.moveToNext()) {columns+=c.getString(1);if(c.getInt(5)>0) keys+=c.getString(1)}}
            check(keys.isNotEmpty()) {"Missing primary key: $t"}
            db.execSQL("CREATE UNIQUE INDEX drafts_snapshot_key_$i ON drafts_snapshot_$i (${keys.joinToString(",",transform=::quote)})")
            val match=keys.joinToString(" AND ") {"s.${quote(it)} IS ${quote(t)}.${quote(it)}"}
            val values=columns.filter {it !in keys}.joinToString(",") {"${quote(it)}=(SELECT s.${quote(it)} FROM drafts_snapshot_$i s WHERE $match)"}
            if(values.isNotEmpty()) db.execSQL("UPDATE ${quote(t)} SET $values")
            db.query("SELECT * FROM ${quote(t)} EXCEPT SELECT * FROM drafts_snapshot_$i").use {check(!it.moveToFirst()) {"Migration changed $t"}}
            db.query("SELECT * FROM drafts_snapshot_$i EXCEPT SELECT * FROM ${quote(t)}").use {check(!it.moveToFirst()) {"Migration lost $t"}}
        }
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()) {"Invalid migrated references"}}
        triggers.filter {it.first!="nodes_identity_immutable"}.forEach {db.execSQL(it.second)}
        db.execSQL("CREATE TRIGGER nodes_identity_immutable BEFORE UPDATE OF id ON nodes WHEN NEW.id != OLD.id BEGIN SELECT RAISE(ABORT,'Node identity is immutable'); END")
        DraftsInvariants.install(db)
        tables.indices.forEach {db.execSQL("DROP TABLE drafts_snapshot_$it")}
    }
}

internal object DraftsInvariants {
    fun install(db:SupportSQLiteDatabase) {
        for(event in listOf("INSERT","UPDATE")) db.execSQL("""
            CREATE TRIGGER drafts_location_${event.lowercase()} BEFORE $event ON nodes
            WHEN (NEW.projectId IS NULL AND (NEW.parentId IS NOT NULL OR NEW.purpose='LAYER' OR NEW.creationGroupId IS NOT NULL OR NEW.sprintMode!=0 OR NEW.workState IS NOT NULL))
              OR (NEW.parentId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM nodes p WHERE p.id=NEW.parentId AND p.projectId IS NEW.projectId))
            BEGIN SELECT RAISE(ABORT,'Invalid draft location'); END
        """.trimIndent())
        db.execSQL("""CREATE TRIGGER drafts_sort_delete AFTER DELETE ON nodes WHEN OLD.projectId IS NULL
            BEGIN DELETE FROM node_sort_preferences WHERE context='draft:' || OLD.id; END""")
        installMoveGuard(db)
    }

    /** Existing Room installations receive the same guard without rewriting any data. */
    fun installMoveGuard(db:SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS drafts_project_move")
        db.execSQL("""CREATE TRIGGER drafts_project_move BEFORE UPDATE OF projectId ON nodes
            WHEN NEW.projectId IS NOT OLD.projectId AND NEW.projectId IS NULL AND
              (NEW.purpose='LAYER' OR NEW.creationGroupId IS NOT NULL OR EXISTS(SELECT 1 FROM nodes WHERE parentId=OLD.id))
            BEGIN SELECT RAISE(ABORT,'Only independent notes and tasks can move to drafts'); END""")
    }
}
