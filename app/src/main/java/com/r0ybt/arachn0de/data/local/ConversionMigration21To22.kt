package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class ConversionMigration21To22(private val legacySort: Map<String, *> = emptyMap<String, String>()) : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE conversion_roots (id TEXT NOT NULL PRIMARY KEY, projectId TEXT, nodeId TEXT, projectIdentity TEXT NOT NULL, nodeIdentity TEXT NOT NULL, projectPosition INTEGER NOT NULL, nodePosition INTEGER NOT NULL, startAt INTEGER, dueAt INTEGER, priority TEXT NOT NULL, creationGroupId TEXT, sprintMode INTEGER NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(nodeId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX index_conversion_roots_projectId ON conversion_roots(projectId)")
        db.execSQL("CREATE UNIQUE INDEX index_conversion_roots_nodeId ON conversion_roots(nodeId)")
        for ((table, column, reference) in listOf(Triple("conversion_people", "personId", "persons"), Triple("conversion_tags", "tagId", "tags"))) {
            db.execSQL("CREATE TABLE $table (rootId TEXT NOT NULL, $column TEXT NOT NULL, PRIMARY KEY(rootId, $column), FOREIGN KEY(rootId) REFERENCES conversion_roots(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY($column) REFERENCES $reference(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_${table}_$column ON $table($column)")
        }
        db.execSQL("CREATE TABLE conversion_events (id TEXT NOT NULL PRIMARY KEY, rootId TEXT NOT NULL, type TEXT NOT NULL, occurredAt INTEGER NOT NULL, FOREIGN KEY(rootId) REFERENCES conversion_roots(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_conversion_events_rootId ON conversion_events(rootId)")
        db.execSQL("CREATE TABLE conversion_work_states (nodeId TEXT NOT NULL PRIMARY KEY, rootId TEXT NOT NULL, workState TEXT NOT NULL, FOREIGN KEY(rootId) REFERENCES conversion_roots(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(nodeId) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_conversion_work_states_rootId ON conversion_work_states(rootId)")
        db.execSQL("CREATE TABLE node_sort_preferences (context TEXT NOT NULL PRIMARY KEY, mode TEXT NOT NULL)")
        ConversionInvariants.install(db)
        legacySort.forEach { (key, value) ->
            if (key.length in 1..256 && db.query("SELECT 1 FROM projects WHERE ? = id || ':project-root' UNION SELECT 1 FROM nodes WHERE ? = projectId || ':' || id", arrayOf(key, key)).use { it.moveToFirst() } && value in listOf("MANUAL", "DUE_ASC", "DUE_DESC", "DUE_PRIORITY", "PRIORITY", "CREATED_NEWEST", "CREATED_OLDEST"))
                db.execSQL("INSERT INTO node_sort_preferences(context, mode) VALUES (?, ?)", arrayOf(key, value))
        }
    }
}

internal object ConversionInvariants {
    fun install(db: SupportSQLiteDatabase) {
        for (event in listOf("INSERT", "UPDATE")) {
            db.execSQL("CREATE TRIGGER conversion_owner_${event.lowercase()} BEFORE $event ON conversion_roots WHEN (NEW.projectId IS NULL) = (NEW.nodeId IS NULL) OR (NEW.projectId IS NOT NULL AND NEW.projectIdentity != NEW.projectId) OR (NEW.nodeId IS NOT NULL AND (NEW.nodeIdentity != NEW.nodeId OR NOT EXISTS(SELECT 1 FROM nodes WHERE id = NEW.nodeId AND purpose = 'LAYER'))) BEGIN SELECT RAISE(ABORT, 'Invalid permanent conversion owner'); END")
        }
        db.execSQL("CREATE TRIGGER conversion_sort_node_delete AFTER DELETE ON nodes BEGIN DELETE FROM node_sort_preferences WHERE context = OLD.projectId || ':' || OLD.id; END")
        db.execSQL("CREATE TRIGGER conversion_sort_project_delete AFTER DELETE ON projects BEGIN DELETE FROM node_sort_preferences WHERE context = OLD.id || ':project-root'; END")
        db.execSQL("CREATE TRIGGER conversion_work_context AFTER UPDATE OF parentId, projectId, purpose ON nodes BEGIN DELETE FROM conversion_work_states WHERE nodeId = NEW.id AND (NEW.parentId IS NOT NULL OR NEW.purpose != 'ACTION' OR NOT EXISTS(SELECT 1 FROM conversion_roots WHERE id = conversion_work_states.rootId AND projectId = NEW.projectId)); END")
        db.execSQL("CREATE TRIGGER conversion_layer_purpose BEFORE UPDATE OF purpose ON nodes WHEN NEW.purpose != 'LAYER' AND EXISTS(SELECT 1 FROM conversion_roots WHERE nodeId = OLD.id) BEGIN SELECT RAISE(ABORT, 'Converted root must remain a layer; convert it to a project first'); END")
    }
}
