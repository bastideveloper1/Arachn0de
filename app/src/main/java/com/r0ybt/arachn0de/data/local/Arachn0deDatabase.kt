package com.r0ybt.arachn0de.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProjectEntity::class, NodeEntity::class, PersonEntity::class, NodePersonEntity::class, RecurrenceRuleEntity::class, RecurrenceOccurrenceEntity::class, RecurrencePersonEntity::class, TagEntity::class, NodeTagEntity::class, RecurrenceTagEntity::class, NodeEventEntity::class, CreationDefaultsEntity::class, CreationDefaultsTagEntity::class, CreationDefaultsPersonEntity::class, AttachmentFileEntity::class, NodeAttachmentEntity::class, ProjectAttachmentEntity::class, TechnologyEntity::class, NodeTechnologyEntity::class, ProjectTechnologyEntity::class, ProjectPhotoEntity::class, ConversionRootEntity::class, ConversionPersonEntity::class, ConversionTagEntity::class, ConversionEventEntity::class, ConversionWorkStateEntity::class, NodeSortPreferenceEntity::class, GameStateEntity::class, MetroPreferencesEntity::class, MetroJourneyEntity::class, SavedTemplateEntity::class, PrivatePreferenceEntity::class], version = 27, exportSchema = true)
abstract class Arachn0deDatabase : RoomDatabase() {
    internal abstract fun privatePreferenceDao(): PrivatePreferenceDao
    internal abstract fun savedTemplateDao(): SavedTemplateDao
    internal abstract fun metroDao(): MetroDao
    abstract fun gameStateDao(): GameStateDao
    abstract fun conversionDao(): ConversionDao
    abstract fun nodeSortPreferenceDao(): NodeSortPreferenceDao
    abstract fun projectPhotoDao(): ProjectPhotoDao
    abstract fun technologyDao(): TechnologyDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun creationDefaultsDao(): CreationDefaultsDao
    abstract fun projectDao(): ProjectDao
    abstract fun nodeDao(): NodeDao
    abstract fun recurrenceDao(): RecurrenceDao
    abstract fun nodeEventDao(): NodeEventDao
    abstract fun tagDao(): TagDao
    abstract fun personDao(): PersonDao
    internal abstract fun backupDao(): BackupDao

    companion object {
        /** The application owner should retain one instance and close it when no longer needed. */
        fun create(context: Context, encryptedKey: ByteArray? = null): Arachn0deDatabase = Room.databaseBuilder(
            context.applicationContext,
            Arachn0deDatabase::class.java,
            "arachn0de.db",
        ).apply {
            if (encryptedKey != null) {
                System.loadLibrary("sqlcipher")
                val session=(context as? com.r0ybt.arachn0de.security.VaultContext)?.session
                val hook=object:net.zetetic.database.sqlcipher.SQLiteDatabaseHook {
                    override fun preKey(connection:net.zetetic.database.sqlcipher.SQLiteConnection) { session?.checkOpen() }
                    override fun postKey(connection:net.zetetic.database.sqlcipher.SQLiteConnection) {
                        session?.checkOpen();connection.execute("PRAGMA cipher_memory_security = ON", emptyArray(), null)
                        connection.execute("PRAGMA temp_store = MEMORY", emptyArray(), null)
                    }
                }
                openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory((context as? com.r0ybt.arachn0de.security.VaultContext)?.rememberSecret(encryptedKey.copyOf()) ?: encryptedKey.copyOf(),hook,false))
            }
        }
            .addMigrations(MIGRATION_1_2, NodeMigration2To3, ProjectMigration3To4, PersonMigration4To5, TaskDatesMigration5To6, NodePurposeMigration6To7, ObligationMigration7To8, RecurrenceMigration8To9, TagMigration9To10, NodeEventMigration10To11, PriorityMigration11To12, CreationGroupMigration12To13, CreationDefaultsMigration13To14, ExplicitLayerMigration14To15, SprintMigration15To16, SprintMigration16To17, AttachmentMigration17To18, TechnologyMigration18To19, AvatarMigration19To20, ProjectPhotoMigration20To21, ConversionMigration21To22(if(encryptedKey==null) context.getSharedPreferences("node_sort_preferences", Context.MODE_PRIVATE).all else emptyMap()), GameMigration22To23(if(encryptedKey==null) context.getSharedPreferences("experimental_game", Context.MODE_PRIVATE).getString("session", "") ?: "" else ""), MetroMigration23To24, SavedTemplatesMigration24To25, SecurityMigration25To26, LayerSortMigration26To27)
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    importLegacyGame(db, if(encryptedKey==null) context.getSharedPreferences("experimental_game", Context.MODE_PRIVATE).getString("session", "") ?: "" else "")
                    ConversionInvariants.install(db)
                    AttachmentInvariants.install(db)
                    NodeInvariants.install(db)
                    ExplicitLayerInvariants.install(db)
                    SprintInvariants.install(db)
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
