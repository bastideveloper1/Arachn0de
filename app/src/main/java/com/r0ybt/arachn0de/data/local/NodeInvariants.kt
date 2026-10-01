package com.r0ybt.arachn0de.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/** Installed both on fresh databases and by migration. Room does not export triggers in its JSON. */
internal object NodeInvariants {
    fun install(db: SupportSQLiteDatabase) {
        for ((suffix, event) in listOf("insert" to "INSERT", "move" to "UPDATE OF parentId")) {
            db.execSQL(
                """
                CREATE TRIGGER nodes_no_cycle_$suffix BEFORE $event ON nodes
                WHEN NEW.parentId IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Cycle in node hierarchy') WHERE NEW.id IN (
                        WITH RECURSIVE ancestors(id) AS (
                            SELECT NEW.parentId
                            UNION
                            SELECT nodes.parentId FROM nodes JOIN ancestors ON nodes.id = ancestors.id
                            WHERE nodes.parentId IS NOT NULL
                        )
                        SELECT id FROM ancestors
                    );
                END
                """.trimIndent(),
            )
        }
        db.execSQL(
            """
            CREATE TRIGGER nodes_identity_immutable BEFORE UPDATE OF id, projectId ON nodes
            WHEN NEW.id != OLD.id OR NEW.projectId != OLD.projectId
            BEGIN SELECT RAISE(ABORT, 'Node identity and project are immutable'); END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER nodes_leaf_completion BEFORE UPDATE OF isCompleted ON nodes
            WHEN NEW.isCompleted != 0 AND EXISTS(
                SELECT 1 FROM nodes WHERE projectId = NEW.projectId AND parentId = NEW.id
            )
            BEGIN SELECT RAISE(ABORT, 'Only leaves can be completed manually'); END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER nodes_parent_on_insert AFTER INSERT ON nodes
            WHEN NEW.parentId IS NOT NULL
            BEGIN
                UPDATE nodes SET isCompleted = 0 WHERE id = NEW.parentId;
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER nodes_parents_on_move AFTER UPDATE OF parentId ON nodes
            WHEN OLD.parentId IS NOT NEW.parentId
            BEGIN
                UPDATE nodes SET isCompleted = 0 WHERE id = OLD.parentId OR id = NEW.parentId;
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER nodes_parent_on_delete AFTER DELETE ON nodes
            WHEN OLD.parentId IS NOT NULL
            BEGIN
                UPDATE nodes SET isCompleted = 0 WHERE id = OLD.parentId;
            END
            """.trimIndent(),
        )
    }
}
