package com.r0ybt.arachn0de.bank

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.Obligation
import com.r0ybt.arachn0de.slots.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class BankAccountDeletionTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:BankRepository
    private lateinit var cash:String
    private fun uuid()=UUID.randomUUID().toString()
    @Before fun setup()=runBlocking<Unit> {
        app.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(app);repo=BankRepository(db)
        db.personDao().insert(PersonEntity("yo","Yo",null));repo.createRoot("Banco","","bank");repo.createAccount("bank","Personal","yo")
        cash=repo.state().accounts.single().id
    }
    @After fun close() {db.close()}
    private suspend fun income(id:String=cash,amount:Long=10000)=repo.confirm(repo.preview(BankRequest(rootId="bank",contextAccountId=id,kind="INCOME",amount=amount,day=20000,concept="Ingreso",destinationAccountId=id)))
    private suspend fun backup()=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory)).snapshot()
    @Test fun emptyAndPopulatedAccountsDeleteWithoutRemovingBankPeopleOrOtherAccount()=runBlocking<Unit> {
        repo.createAccount("bank","Otra","yo");val other=repo.state().accounts.single {it.id!=cash}.id
        income(other);val before=repo.state();val preview=repo.previewAccountDeletion(cash)
        assertEquals(before,repo.state()) // Preview/cancel does not write.
        repo.deleteAccount(preview);assertTrue(repo.state().roots.any {it.id=="bank"});assertNotNull(db.personDao().get("yo"))
        assertEquals(10000L,repo.state().balance(other));assertEquals(1,repo.state().operations.size)
        repo.deleteAccount(repo.previewAccountDeletion(other));assertTrue(repo.state().accounts.isEmpty());assertTrue(repo.state().operations.isEmpty())
        BackupJson.decode(BackupJson.encode(backup())).validate()
    }
    @Test fun deletingPaymentHistoryRestoresPendingDebtAndPreservesTask()=runBlocking<Unit> {
        income();ProjectRepository(db.projectDao(),db).createProject("Pagos",creationId="tasks")
        NodeRepository(db).createNode("tasks",null,"Pago",creationId="task",obligation=Obligation(5000,"CLP"),responsibleIds=setOf("yo"))
        repo.confirm(repo.preview(BankRequest(rootId="bank",contextAccountId=cash,kind="PAYMENT",amount=5000,day=20000,concept="Pago",sourceAccountId=cash,personId="yo")),mapOf("task" to 5000L))
        assertEquals(5000L,repo.state().paid("task"))
        assertTrue(runCatching {repo.previewAccountDeletion(cash)}.isFailure)
        NodeRepository(db).setTaskStatus("task",com.r0ybt.arachn0de.domain.model.TaskStatus.PENDING)
        repo.deleteAccount(repo.previewAccountDeletion(cash))
        val s=repo.state();assertEquals(0L,s.paid("task"));assertEquals(5000L,s.remaining(s.nodes.single {it.id=="task"}));assertFalse(s.nodes.single {it.id=="task"}.isCompleted)
        BackupJson.decode(BackupJson.encode(backup())).validate()
    }
    @Test fun savingsRemovalIsExplicitAndAtomicAndStaleConfirmationsFail()=runBlocking<Unit> {
        val savings=SavingsRepository(db);val c=SavingsConfig(uuid(),"bank",cash);savings.configure(c)
        savings.buy(uuid(),5,c.id);val stale=repo.previewAccountDeletion(cash);assertTrue(stale.removesSavings)
        income();assertTrue(runCatching {repo.deleteAccount(stale)}.isFailure);assertEquals(5L,savings.read().book().credits)
        val preview=repo.previewAccountDeletion(cash)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_account_delete BEFORE DELETE ON nodes BEGIN SELECT RAISE(ABORT,'simulated failure'); END")
        assertTrue(runCatching {repo.deleteAccount(preview)}.isFailure);assertEquals(preview.original,repo.state());assertEquals(5L,savings.read().book().credits)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_account_delete")
        repo.deleteAccount(preview);assertNull(db.privatePreferenceDao().get(SavingsCodec.NAME));assertTrue(repo.state().accounts.isEmpty())
        BackupJson.decode(BackupJson.encode(backup())).validate()
    }
    @Test fun transferHistoryCannotLeaveAnotherAccountInDebt()=runBlocking<Unit> {
        income();repo.createAccount("bank","Otra","yo");val other=repo.state().accounts.single {it.id!=cash}.id
        repo.confirm(repo.preview(BankRequest(rootId="bank",contextAccountId=cash,kind="TRANSFER",amount=5000,day=20000,concept="Transferencia",sourceAccountId=cash,destinationAccountId=other)))
        repo.confirm(repo.preview(BankRequest(rootId="bank",contextAccountId=other,kind="EXPENSE",amount=1000,day=20000,concept="Retiro",sourceAccountId=other)))
        val before=repo.state();assertTrue(runCatching {repo.previewAccountDeletion(cash)}.isFailure);assertEquals(before,repo.state())
    }
}
