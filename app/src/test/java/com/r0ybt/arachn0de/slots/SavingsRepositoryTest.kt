package com.r0ybt.arachn0de.slots

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.bank.*
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
class SavingsRepositoryTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var bank:BankRepository
    private lateinit var repo:SavingsRepository
    private lateinit var config:SavingsConfig
    private var result=listOf(0,1,2)
    private fun id()=UUID.randomUUID().toString()
    @Before fun setup()=runBlocking<Unit> {
        app.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(app)
        bank=BankRepository(db);db.personDao().insert(PersonEntity("yo","Yo",null))
        bank.createRoot("Caja","","bank");bank.createAccount("bank","Física","yo")
        repo=SavingsRepository(db,draw={result})
        config=SavingsConfig(id(),"bank",bank.state().accounts.single().id,table=SavingsPayTable.legacy())
        repo.configure(config)
    }
    @After fun close() {db.close()}
    private suspend fun state()=repo.read().book()
    private suspend fun buy(count:Long)=repo.buy(id(),count,config.id)
    private suspend fun fund(units:Long)=repo.topup(id(),units,config.id)
    private fun backup()=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory))

    private suspend fun existingCash(amount:Long) {
        bank.confirm(bank.preview(BankRequest(rootId="bank",contextAccountId=config.account,kind="INCOME",amount=amount,day=20000,concept="Saldo previo",destinationAccountId=config.account)))
    }
    @Test fun associationUsesExistingBalanceWithoutIncomeCreditsOrGeneratedSavings()=runBlocking<Unit> {
        db.privatePreferenceDao().delete(SavingsCodec.NAME)
        existingCash(10000);val bankBefore=bank.state()
        repo.configure(config)
        val b=state();assertEquals(10L,b.free);assertEquals(10L,b.assignedCapital)
        assertEquals(0L,b.protected);assertEquals(0L,b.credits);assertEquals(0L,b.topups);assertEquals(0L,b.deposited)
        assertEquals(bankBefore,bank.state());buy(1)
        assertTrue(state().canSpin);assertEquals(0L,state().deficit)
        val spin=repo.spin(id());assertEquals(0L,spin.units);assertEquals(0L,state().credits)
    }
    @Test fun alreadyAssignedAccountAdoptsOnlyUnallocatedBalanceAndDoesNotDuplicateOnReopen()=runBlocking<Unit> {
        existingCash(10500)
        val once=repo.read();assertEquals(10L,once.book().free);assertEquals(0L,once.book().credits)
        assertEquals(once,repo.read());repo.configure(config);assertEquals(once,repo.read())
        db.close();db=Arachn0deDatabase.create(app);bank=BankRepository(db);repo=SavingsRepository(db)
        assertEquals(once,repo.read());assertEquals(10500L,bank.state().balance(config.account))
        existingCash(500);assertEquals(11L,state().assignedCapital);assertEquals(2,bank.state().operations.size)
    }
    @Test fun attributedBalanceNeverReusesProtectedOrReservedMoney()=runBlocking<Unit> {
        existingCash(10000);buy(10);assertEquals(7L,state().protected);assertEquals(13L,state().free)
        result=listOf(6,6,6);val e=repo.spin(id());repo.acknowledge(e.id)
        val b=state();assertEquals(10L,b.assignedCapital);assertEquals(7L,b.protected);assertEquals(10L,b.reserved);assertEquals(3L,b.free)
        assertFalse(b.canSpin);assertEquals(7L,b.deficit);assertEquals(10L,state().assignedCapital)
        repo.resolve(id(),e.id,true);assertEquals(10000L,bank.state().balance(config.account));assertEquals(3L,state().free)
        bank.confirm(bank.preview(BankRequest(rootId="bank",contextAccountId=config.account,kind="EXPENSE",amount=1000,day=20000,concept="Retiro externo",sourceAccountId=config.account)))
        assertNotNull(state().reconciliation(bank.state()));assertEquals(10L,state().assignedCapital)
    }
    @Test fun assignedCapitalRoundTripsAndLegacyLedgerUpgradesWithoutDuplicatingBankMoney()=runBlocking<Unit> {
        buy(2)
        val old=repo.read();val values=com.r0ybt.arachn0de.security.EncryptedPreferences.decode(SavingsCodec.encode(old))
        val oldJson=org.json.JSONObject(values.getValue("state") as String).put("version",1)
        val oldConfigs=oldJson.getJSONArray("configs");repeat(oldConfigs.length()) {oldConfigs.getJSONObject(it).remove("pack");oldConfigs.getJSONObject(it).remove("denominator")}
        val legacy=com.r0ybt.arachn0de.security.EncryptedPreferences.encode(mapOf("state" to oldJson.toString()))
        db.privatePreferenceDao().save(PrivatePreferenceEntity(SavingsCodec.NAME,legacy))
        existingCash(10000);val beforeBank=bank.state();val adopted=repo.read()
        assertEquals(10L,adopted.book().assignedCapital);assertEquals(2L,adopted.book().credits);assertEquals(beforeBank,bank.state())
        val b=backup();val data=BackupJson.decode(BackupJson.encode(b.snapshot()));data.validate();b.restore(data)
        assertEquals(adopted,repo.read());assertEquals(beforeBank,bank.state())
        val bad=data.copy(privatePreferences=data.privatePreferences.map {if(it.name==SavingsCodec.NAME)it.copy(payload="corrupt") else it})
        assertTrue(runCatching {b.restore(bad)}.isFailure);assertEquals(adopted,repo.read())
    }
    @Test fun jungleMaskPaysExactlyCartelAmountAndRoundTripsWithoutChangingLegacyCredits()=runBlocking<Unit> {
        buy(1) // Historical seven-symbol credit.
        val jungle=SavingsConfig(id(),"bank",config.account)
        repo.configure(jungle);repo.buy(id(),5,jungle.id);repo.topup(id(),30,jungle.id)
        result=listOf(0,0,0);val old=repo.spin(id());assertEquals(config.id,old.config);assertEquals(7L,old.units)
        repo.acknowledge(old.id);repo.resolve(id(),old.id,false)
        result=listOf(7,7,7);val mask=repo.spin(id());assertEquals(jungle.id,mask.config);assertEquals(20L,mask.units)
        repo.acknowledge(mask.id);repo.resolve(id(),mask.id,true)
        assertEquals("Tragaperras — Premio Máscara dorada",bank.state().operations.single {it.kind=="EXPENSE"}.concept)
        val saved=repo.read();val b=backup();b.restore(BackupJson.decode(BackupJson.encode(b.snapshot())))
        assertEquals(saved,repo.read());assertEquals(4L,state().credits)
    }
    @Test fun monetaryDepositsBuyFivePerThousandWithoutDuplicatingBankIncome()=runBlocking<Unit> {
        val c=SavingsConfig(id(),"bank",config.account);repo.configure(c)
        val first=id();repo.depositAndBuy(first,20000,c.root,c.account);repo.depositAndBuy(first,20000,c.root,c.account)
        assertEquals(100L,state().credits);assertEquals(20000L,bank.state().operations.single().amount)
        repo.depositAndBuy(id(),40000,c.root,c.account);assertEquals(300L,state().credits)
        assertEquals(60000L,bank.state().balance(config.account));assertEquals(2,bank.state().operations.size)
        val before=repo.read();assertTrue(runCatching {repo.depositAndBuy(id(),1500,c.root,c.account)}.isFailure);assertTrue(runCatching {repo.depositAndBuy(id(),0,c.root,c.account)}.isFailure);assertEquals(before,repo.read())
        val saved=backup().snapshot();backup().restore(BackupJson.decode(BackupJson.encode(saved)));assertEquals(before,repo.read())
    }
    @Test fun exactUserCashExampleKeepsPrizePendingUntilSingleWithdrawal()=runBlocking<Unit> {
        existingCash(40000)
        val c=SavingsConfig(id(),"bank",config.account);repo.configure(c)
        assertEquals(40000L,bank.state().balance(c.account));assertEquals(40L,state().free)
        repo.depositAndBuy(id(),1000,c.root,c.account)
        assertEquals(41000L,bank.state().balance(c.account));assertEquals(5L,state().credits)
        var prizeId=""
        repeat(5) {i->
            result=if(i==4)listOf(1,1,1) else listOf(0,1,2)
            val e=repo.spin(id());repo.acknowledge(e.id);if(i==4)prizeId=e.id
            assertEquals(41000L,bank.state().balance(c.account))
        }
        assertEquals(0L,state().credits);assertEquals(2L,state().reserved);assertEquals(41L,state().funds)
        assertEquals(2,bank.state().operations.size) // Prior balance + one purchase, no per-spin expense.
        val b=backup();b.restore(BackupJson.decode(BackupJson.encode(b.snapshot())))
        assertEquals(2L,state().reserved);assertEquals(41000L,bank.state().balance(c.account))
        val withdrawal=id();repo.resolve(withdrawal,prizeId,true);repo.resolve(withdrawal,prizeId,true)
        val after=repo.read();assertTrue(runCatching {repo.resolve(id(),prizeId,true)}.isFailure);assertEquals(after,repo.read())
        assertEquals(39000L,bank.state().balance(c.account));assertEquals(39L,state().funds);assertEquals(0L,state().reserved)
        assertEquals(3,bank.state().operations.size);assertEquals(2000L,bank.state().operations.single {it.kind=="EXPENSE"}.amount)
    }
    @Test fun purchasesRoundCumulativelyPersistAndDoNotCountCapitalAsSavings()=runBlocking<Unit> {
        repeat(10) {buy(1)}
        var b=state();assertEquals(10L,b.credits);assertEquals(7L,b.protected);assertEquals(3L,b.free)
        fund(7);b=state();assertEquals(7L,b.protected);assertEquals(10L,b.free);assertEquals(7L,b.topups)
        assertEquals(17000L,bank.state().balance(config.account))
        db.close();db=Arachn0deDatabase.create(app);bank=BankRepository(db);repo=SavingsRepository(db)
        assertEquals(10L,state().credits)
        assertEquals(10,bank.state().operations.count {it.concept=="Tragaperras — Compra de tiradas"})
    }
    @Test fun emptyReserveBlocksWithoutConsumingCreditsAndTopupIsIdempotent()=runBlocking<Unit> {
        assertTrue(runCatching {repo.spin(id())}.isFailure);assertEquals(0L,state().credits)
        val purchase=id();repo.buy(purchase,5,config.id);repo.buy(purchase,5,config.id)
        val before=repo.read();assertEquals(5L,before.book().credits);assertEquals(8L,before.book().deficit)
        assertTrue(runCatching {repo.spin(id())}.isFailure);assertEquals(before,repo.read())
        val top=id();repo.topup(top,8,config.id);repo.topup(top,8,config.id)
        assertEquals(8L,state().topups);assertEquals(2,bank.state().operations.size)
        val spin=id();val e=repo.spin(spin);assertEquals(result,e.result)
        assertEquals(e,repo.spin(spin));assertEquals(e,repo.spin(id())) // Unfinished animation resumes the same committed spin.
        assertEquals(4L,state().credits)
        repo.acknowledge(spin);assertNull(repo.read().animation)
    }
    @Test fun consecutiveWinsReserveIndividuallyAndWithdrawalOrKeepingIsAtomic()=runBlocking<Unit> {
        buy(10);fund(12);result=listOf(6,6,6)
        val first=repo.spin(id());repo.acknowledge(first.id)
        assertEquals(10L,state().reserved);assertEquals(5L,state().free)
        val credits=state().credits;assertTrue(runCatching {repo.spin(id())}.isFailure);assertEquals(credits,state().credits)
        fund(5);val second=repo.spin(id());repo.acknowledge(second.id)
        assertEquals(20L,state().reserved);assertEquals(0L,state().free)
        val payout=id();repo.resolve(payout,first.id,true);repo.resolve(payout,first.id,true)
        assertEquals(17000L,bank.state().balance(config.account));assertEquals(10L,state().withdrawn)
        assertTrue(bank.state().operations.any {it.id==payout && it.concept=="Tragaperras — Premio Siete"})
        val ops=bank.state().operations.size;repo.resolve(id(),second.id,false)
        assertEquals(ops,bank.state().operations.size);assertEquals(17L,state().protected);assertEquals(0L,state().reserved)
        assertTrue(runCatching {repo.resolve(id(),first.id,true)}.isFailure)
        assertEquals(state().funds*1000,bank.state().balance(config.account))
    }
    @Test fun bankFailureRollsBackIncomeAndCreditsAndExternalMovementsFreezePlay()=runBlocking<Unit> {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_savings BEFORE INSERT ON private_preferences WHEN NEW.name='slot_savings' BEGIN SELECT RAISE(ABORT,'injected'); END")
        val before=bank.state();assertTrue(runCatching {buy(5)}.isFailure)
        assertEquals(before,bank.state());assertEquals(0L,state().credits)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_savings")
        buy(5);fund(8)
        val external=BankRequest(rootId="bank",contextAccountId=config.account,kind="EXPENSE",amount=1000,day=20000,concept="Ajeno",sourceAccountId=config.account)
        bank.confirm(bank.preview(external))
        val saved=repo.read();assertNotNull(saved.book().reconciliation(bank.state()))
        assertTrue(runCatching {repo.spin(id())}.isFailure);assertEquals(saved,repo.read())
        // Backup preserves an inconsistent-but-valid historical ledger for manual reconciliation.
        val archive=backup().snapshot();BackupJson.decode(BackupJson.encode(archive)).validate()
        bank.annul(external.id)
        val linked=saved.events.first {it.kind=="BUY"}.id
        assertTrue(runCatching {bank.annul(linked)}.isFailure)
        // Imported/historical broken link still freezes safely, rather than changing odds or discarding money.
        db.bankDao().annul(linked)
        assertNotNull(state().reconciliation(bank.state()))
        assertTrue(runCatching {repo.spin(id())}.isFailure)
        BackupJson.decode(BackupJson.encode(backup().snapshot())).validate()
    }
    @Test fun configChangesDoNotRepriceCreditsOrResetRoundingAndAccountCannotChangeWithFunds()=runBlocking<Unit> {
        buy(1)
        val other=config.copy(id=id(),table=config.table.copy(priceUnits=2,prizeUnits=config.table.prizeUnits.map {it*2}))
        repo.configure(other);repo.buy(id(),1,other.id)
        assertEquals(2L,state().protected);assertEquals(1L,state().free) // floor((1+2)*.7), not separate per-config rounding.
        assertEquals(10L,state().maximum) // Old purchased credit keeps its original table.
        assertTrue(runCatching {repo.configure(other.copy(id=id(),targetBps=4000))}.isFailure)
        bank.createAccount("bank","Otra","yo")
        val second=bank.state().accounts.first {it.id!=config.account}
        assertTrue(runCatching {repo.configure(other.copy(id=id(),account=second.id))}.isFailure)
        repo.topup(id(),9,other.id);result=listOf(6,6,6)
        val e=repo.spin(id());assertEquals(10L,e.units);repo.acknowledge(e.id);repo.resolve(id(),e.id,false)
        assertEquals(20L,state().maximum)
    }
    @Test fun pendingAnimationPrizesBackupHistoricalAndCorruptionPreserveData()=runBlocking<Unit> {
        buy(2);fund(10);result=listOf(0,0,0)
        val e=repo.spin(id());val before=repo.read();assertEquals(7L,before.book().reserved)
        db.close();db=Arachn0deDatabase.create(app);bank=BankRepository(db);repo=SavingsRepository(db)
        assertEquals(e,repo.spin(id()));assertEquals(1L,state().credits)
        val b=backup();val snapshot=b.snapshot();val encoded=BackupJson.encode(snapshot)
        assertEquals(54,org.json.JSONObject(encoded.toString(Charsets.UTF_8)).getInt("dataVersion"))
        val decoded=BackupJson.decode(encoded);b.restore(decoded);assertEquals(before,repo.read())
        val bad=decoded.copy(privatePreferences=decoded.privatePreferences.map {if(it.name==SavingsCodec.NAME)it.copy(payload="bad") else it})
        assertTrue(runCatching {b.restore(bad)}.isFailure);assertEquals(before,repo.read())
        val missing=decoded.copy(bankOperations=decoded.bankOperations.filterNot {it.id==snapshot.bankOperations.first().id})
        assertTrue(runCatching {missing.validate()}.isFailure)
        assertTrue(BackupJson.decode(BackupJson.encode(BackupFixture.empty())).privatePreferences.isEmpty())
        repo.acknowledge(e.id);repo.resolve(id(),e.id,false)
    }
}
