package com.r0ybt.arachn0de.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProjectEntity::class, NodeEntity::class, PersonEntity::class, NodePersonEntity::class, RecurrenceRuleEntity::class, RecurrenceOccurrenceEntity::class, RecurrencePersonEntity::class, TagEntity::class, NodeTagEntity::class, RecurrenceTagEntity::class, NodeEventEntity::class], version = 13, exportSchema = true)
abstract class Arachn0deDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun nodeDao(): NodeDao
    abstract fun recurrenceDao(): RecurrenceDao
    abstract fun nodeEventDao(): NodeEventDao
    abstract fun tagDao(): TagDao
    abstract fun personDao(): PersonDao
    internal abstract fun backupDao(): BackupDao

    companion object {
        /** The application owner should retain one instance and close it when no longer needed. */
        fun create(context: Context): Arachn0deDatabase = Room.databaseBuilder(
            context.applicationContext,
            Arachn0deDatabase::class.java,
            "arachn0de.db",
        )
            .addMigrations(MIGRATION_1_2, NodeMigration2To3, ProjectMigration3To4, PersonMigration4To5, TaskDatesMigration5To6, NodePurposeMigration6To7, ObligationMigration7To8, RecurrenceMigration8To9, TagMigration9To10, NodeEventMigration10To11, PriorityMigration11To12, CreationGroupMigration12To13)
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    NodeInvariants.install(db)
                    NodePurposeInvariants.install(db)
                    ObligationInvariants.install(db)
                }
            })
            .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nodes (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        parentId TEXT,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        isStructural INTEGER NOT NULL,
                        isCompletable INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE CASCADE ON UPDATE CASCADE,
                        FOREIGN KEY(parentId) REFERENCES nodes(id) ON DELETE CASCADE ON UPDATE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nodes_projectId ON nodes(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nodes_parentId ON nodes(parentId)")
            }
        }
    }
}
