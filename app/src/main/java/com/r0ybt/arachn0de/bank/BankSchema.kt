package com.r0ybt.arachn0de.bank

import androidx.room.*
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="bank_roots",foreignKeys=[ForeignKey(entity=ProjectEntity::class,parentColumns=["id"],childColumns=["id"],onDelete=ForeignKey.RESTRICT,deferred=false)],indices=[])
data class BankRoot(
    @PrimaryKey val id:String
)

@Entity(tableName="bank_accounts",foreignKeys=[ForeignKey(entity=BankRoot::class,parentColumns=["id"],childColumns=["rootId"],onDelete=ForeignKey.RESTRICT,deferred=false),ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["id"],onDelete=ForeignKey.RESTRICT,deferred=true)],indices=[Index(value=["rootId"])])
data class BankAccount(
    @PrimaryKey val id:String,
    val rootId:String,
    val personId:String
)

@Entity(tableName="bank_operations",foreignKeys=[ForeignKey(entity=BankRoot::class,parentColumns=["id"],childColumns=["rootId"],onDelete=ForeignKey.RESTRICT,deferred=false),ForeignKey(entity=BankAccount::class,parentColumns=["id"],childColumns=["contextAccountId"],onDelete=ForeignKey.RESTRICT,deferred=false),ForeignKey(entity=BankAccount::class,parentColumns=["id"],childColumns=["sourceAccountId"],onDelete=ForeignKey.RESTRICT,deferred=false),ForeignKey(entity=BankAccount::class,parentColumns=["id"],childColumns=["destinationAccountId"],onDelete=ForeignKey.RESTRICT,deferred=false),ForeignKey(entity=BankOperation::class,parentColumns=["id"],childColumns=["correctsId"],onDelete=ForeignKey.RESTRICT,deferred=true)],indices=[Index(value=["rootId"]),Index(value=["contextAccountId"]),Index(value=["sourceAccountId"]),Index(value=["destinationAccountId"]),Index(value=["correctsId"])])
data class BankOperation(
    @PrimaryKey val id:String,
    val rootId:String,
    val contextAccountId:String?,
    val kind:String,
    val amount:Long,
    val day:Long,
    val concept:String,
    val sourceAccountId:String?,
    val destinationAccountId:String?,
    val sourcePersonId:String?,
    val obligationPersonId:String?,
    val correctsId:String?,
    val voided:Boolean,
    val createdAt:Long
)

@Entity(tableName="bank_allocations",foreignKeys=[ForeignKey(entity=BankOperation::class,parentColumns=["id"],childColumns=["operationId"],onDelete=ForeignKey.RESTRICT,deferred=false),ForeignKey(entity=NodeEntity::class,parentColumns=["id"],childColumns=["nodeId"],onDelete=ForeignKey.RESTRICT,deferred=false),ForeignKey(entity=RecurrenceOccurrenceEntity::class,parentColumns=["ruleId","day"],childColumns=["ruleId","occurrenceDay"],onDelete=ForeignKey.RESTRICT,deferred=false)],indices=[Index(value=["operationId"]),Index(value=["nodeId"]),Index(value=["ruleId","occurrenceDay"])])
data class BankAllocation(
    @PrimaryKey val id:String,
    val operationId:String,
    val nodeId:String,
    val amount:Long,
    val personId:String?,
    val personName:String?,
    val sourcePersonId:String?,
    val sourcePersonName:String?,
    val title:String,
    val ruleId:String?,
    val occurrenceDay:Long?,
    val stateAt:Long?,
    val stateCount:Int
)

data class BankPaidRow(val nodeId:String,val paid:Long)

@Dao
interface BankDao {
    @Query("SELECT a.nodeId,SUM(a.amount) AS paid FROM bank_allocations a JOIN bank_operations o ON o.id=a.operationId WHERE o.voided=0 GROUP BY a.nodeId")
    fun observePaid():Flow<List<BankPaidRow>>
    @Query("SELECT * FROM bank_roots ORDER BY id") suspend fun roots():List<BankRoot>
    @Query("SELECT * FROM bank_roots ORDER BY id") fun observeRoots():Flow<List<BankRoot>>
    @Insert suspend fun insertRoots(rows:List<BankRoot>)
    @Query("DELETE FROM bank_roots") suspend fun clearRoots()
    @Query("SELECT * FROM bank_accounts ORDER BY id") suspend fun accounts():List<BankAccount>
    @Query("SELECT * FROM bank_accounts ORDER BY id") fun observeAccounts():Flow<List<BankAccount>>
    @Insert suspend fun insertAccounts(rows:List<BankAccount>)
    @Query("DELETE FROM bank_accounts") suspend fun clearAccounts()
    @Query("SELECT * FROM bank_operations ORDER BY id") suspend fun operations():List<BankOperation>
    @Query("SELECT * FROM bank_operations ORDER BY id") fun observeOperations():Flow<List<BankOperation>>
    @Insert suspend fun insertOperations(rows:List<BankOperation>)
    @Query("DELETE FROM bank_operations") suspend fun clearOperations()
    @Query("SELECT * FROM bank_allocations ORDER BY id") suspend fun allocations():List<BankAllocation>
    @Query("SELECT * FROM bank_allocations ORDER BY id") fun observeAllocations():Flow<List<BankAllocation>>
    @Insert suspend fun insertAllocations(rows:List<BankAllocation>)
    @Query("DELETE FROM bank_allocations") suspend fun clearAllocations()
    @Query("UPDATE bank_operations SET voided=1 WHERE id=:id") suspend fun annul(id:String)
}

internal object BankMigration52To53:androidx.room.migration.Migration(52,53) {
    override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS bank_roots (`id` TEXT NOT NULL PRIMARY KEY, FOREIGN KEY(`id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bank_accounts (`id` TEXT NOT NULL PRIMARY KEY, `rootId` TEXT NOT NULL, `personId` TEXT NOT NULL, FOREIGN KEY(`rootId`) REFERENCES `bank_roots`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`id`) REFERENCES `nodes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_accounts_rootId` ON `bank_accounts` (`rootId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bank_operations (`id` TEXT NOT NULL PRIMARY KEY, `rootId` TEXT NOT NULL, `contextAccountId` TEXT NOT NULL, `kind` TEXT NOT NULL, `amount` INTEGER NOT NULL, `day` INTEGER NOT NULL, `concept` TEXT NOT NULL, `sourceAccountId` TEXT, `destinationAccountId` TEXT, `sourcePersonId` TEXT, `obligationPersonId` TEXT, `correctsId` TEXT, `voided` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`rootId`) REFERENCES `bank_roots`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`contextAccountId`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`sourceAccountId`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`destinationAccountId`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`correctsId`) REFERENCES `bank_operations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_operations_rootId` ON `bank_operations` (`rootId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_operations_contextAccountId` ON `bank_operations` (`contextAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_operations_sourceAccountId` ON `bank_operations` (`sourceAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_operations_destinationAccountId` ON `bank_operations` (`destinationAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_operations_correctsId` ON `bank_operations` (`correctsId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bank_allocations (`id` TEXT NOT NULL PRIMARY KEY, `operationId` TEXT NOT NULL, `nodeId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `personId` TEXT NOT NULL, `personName` TEXT NOT NULL, `sourcePersonId` TEXT NOT NULL, `sourcePersonName` TEXT NOT NULL, `title` TEXT NOT NULL, `ruleId` TEXT, `occurrenceDay` INTEGER, `stateAt` INTEGER, `stateCount` INTEGER NOT NULL, FOREIGN KEY(`operationId`) REFERENCES `bank_operations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`nodeId`) REFERENCES `nodes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`ruleId`,`occurrenceDay`) REFERENCES `recurrence_occurrences`(`ruleId`,`day`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_allocations_operationId` ON `bank_allocations` (`operationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_allocations_nodeId` ON `bank_allocations` (`nodeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_allocations_ruleId_occurrenceDay` ON `bank_allocations` (`ruleId`,`occurrenceDay`)")
        BankInvariants.install(db)
    }
}
