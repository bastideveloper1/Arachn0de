package com.r0ybt.arachn0de.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Rebuilds only nodes, preserving user data and repairing invalid legacy parent links. */
internal object NodeMigration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val nodes = readNodes(db)
        val projects = mutableSetOf<String>()
        db.query("SELECT id FROM projects").use { cursor ->
            while (cursor.moveToNext()) projects.add(cursor.getString(0))
        }
        // An absent project cannot be repaired without inventing user content. Fail atomically.
        check(nodes.all { it.projectId in projects }) { "Cannot migrate a node without its project" }
        val originalById = nodes.associateBy { it.id }
        val originalParents = nodes.mapNotNull { node ->
            node.parentId?.takeIf { originalById[it]?.projectId == node.projectId }
        }.toSet()
        val repaired = repairParents(nodes)
        val containers = repaired.mapNotNull { it.parentId }.toSet()

        // Room runs this migration in a transaction. Detach legacy links before dropping the old
        // table so its recursive CASCADE cannot overflow on a deep tree or corrupt cycle.
        db.execSQL("UPDATE nodes SET parentId = NULL")
        db.execSQL("DROP TABLE nodes")
        db.execSQL(
            """
            CREATE TABLE nodes (
                id TEXT NOT NULL PRIMARY KEY,
                projectId TEXT NOT NULL,
                parentId TEXT,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                isCompleted INTEGER NOT NULL,
                position INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE CASCADE ON UPDATE NO ACTION,
                FOREIGN KEY(projectId, parentId) REFERENCES nodes(projectId, id) ON DELETE CASCADE ON UPDATE NO ACTION
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX index_nodes_projectId_id ON nodes(projectId, id)")
        db.execSQL("CREATE INDEX index_nodes_projectId_parentId ON nodes(projectId, parentId)")
        val children = repaired.groupBy { it.parentId }
        val pending = java.util.ArrayDeque<NodeEntity>()
        children[null].orEmpty().forEach(pending::addLast)
        var inserted = 0
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            db.execSQL(
                "INSERT INTO nodes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    node.id, node.projectId, node.parentId, node.title, node.description,
                    if (node.isCompleted && node.id !in containers && node.id !in originalParents) 1 else 0,
                    node.position, node.createdAt, node.updatedAt,
                ),
            )
            inserted++
            children[node.id].orEmpty().forEach(pending::addLast)
        }
        check(inserted == nodes.size) { "Migration did not preserve every node" }
        NodeInvariants.install(db)
    }

    private fun readNodes(db: SupportSQLiteDatabase): List<NodeEntity> = buildList {
        db.query("SELECT id, projectId, parentId, title, description, isCompleted, position, createdAt, updatedAt FROM nodes").use { c ->
            while (c.moveToNext()) {
                add(NodeEntity(
                    c.getString(0), c.getString(1), if (c.isNull(2)) null else c.getString(2),
                    c.getString(3), c.getString(4), c.getInt(5) != 0,
                    c.getInt(6), c.getLong(7), c.getLong(8),
                ))
            }
        }
    }

    private fun repairParents(nodes: List<NodeEntity>): List<NodeEntity> {
        val byId = nodes.associateBy { it.id }
        val parents = nodes.associate { node ->
            node.id to node.parentId?.takeIf { byId[it]?.projectId == node.projectId }
        }.toMutableMap()
        val finished = mutableSetOf<String>()
        for (node in nodes) {
            val path = mutableListOf<String>()
            val pathIndex = mutableMapOf<String, Int>()
            var id: String? = node.id
            while (id != null && id !in finished) {
                val cycleStart = pathIndex[id]
                if (cycleStart != null) {
                    // Deterministic repair: detach the smallest ID in the cycle, not its descendants.
                    parents[path.subList(cycleStart, path.size).minOrNull()!!] = null
                    break
                }
                pathIndex[id] = path.size
                path.add(id)
                id = parents[id]
            }
            finished.addAll(path)
        }
        return nodes.map { it.copy(parentId = parents[it.id]) }
    }
}
