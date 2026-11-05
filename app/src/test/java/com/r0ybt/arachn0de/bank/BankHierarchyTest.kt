package com.r0ybt.arachn0de.bank

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.NodePurpose
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class BankHierarchyTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var app:Application
    private lateinit var repo:BankRepository
    @Before fun setup()=runBlocking<Unit> {
        app=ApplicationProvider.getApplicationContext();app.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(app);repo=BankRepository(db)
        db.personDao().insert(PersonEntity("person","Persona",null))
        repo.createRoot("Banca","","bank")
        repo.createAccount("bank","Uno","person");repo.createAccount("bank","Dos","person")
        ProjectRepository(db.projectDao(),db).createProject("Normal",creationId="normal")
    }
    @After fun close(){db.close()}
    @Test fun flatAccountsAndIndirectMutationsPreserveOrdinaryHierarchy()=runBlocking<Unit> {
        val nodes=NodeRepository(db);val original=repo.state();val account=original.accounts.first().id
        assertEquals(2,original.accounts.size);assertTrue(original.accounts.all {it.personId=="person"})
        assertTrue(BankHierarchy.issues("bank",original.accounts,original.nodes).isEmpty())
        assertTrue(runCatching {nodes.createNode("bank",account,"Subcapa",purpose=NodePurpose.LAYER)}.isFailure)
        assertTrue(runCatching {nodes.createNode("bank",null,"Capa",purpose=NodePurpose.LAYER)}.isFailure)
        val accountNode=original.nodes.single {it.id==account}
        assertTrue(runCatching {db.nodeDao().insert(accountNode.copy(id="copied-account",parentId=account))}.isFailure)
        assertTrue(runCatching {db.nodeDao().insert(accountNode.copy(id="copied-root-account"))}.isFailure)
        val sql=db.openHelper.writableDatabase
        assertTrue(runCatching {sql.execSQL("UPDATE nodes SET parentId=? WHERE id=?",arrayOf(original.accounts.last().id,account))}.isFailure)
        assertTrue(runCatching {sql.execSQL("UPDATE nodes SET projectId='normal' WHERE id=?",arrayOf(account))}.isFailure)
        val nesting=ProjectNestingRepository(db)
        assertTrue(runCatching {nesting.move("bank","normal",null)}.isFailure)
        assertTrue(runCatching {nesting.move("normal","bank",account)}.isFailure)
        assertTrue(runCatching {nesting.promote(account)}.isFailure)
        assertEquals(original,repo.state())
        val parent=nodes.createNode("normal",null,"Capa",purpose=NodePurpose.LAYER)
        val child=nodes.createNode("normal",parent.id,"Subcapa",purpose=NodePurpose.LAYER)
        assertEquals(parent.id,db.nodeDao().getById(child.id)!!.parentId)
        assertTrue(runCatching {db.bankDao().insertAccounts(listOf(BankAccount(child.id,"bank","person")))}.isFailure)
    }
    @Test fun restoreRejectsNestedAccountsAndDiagnosisNeverMovesRecords()=runBlocking<Unit> {
        val backup=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory))
        val snapshot=backup.snapshot();val before=repo.state();val a=before.accounts
        val malformed=snapshot.copy(nodes=snapshot.nodes.map {if(it.id==a[1].id)it.copy(parentId=a[0].id) else it})
        assertTrue(runCatching {backup.restore(malformed)}.isFailure);assertEquals(before,repo.state())
        val bad=before.nodes.map {if(it.id==a[1].id)it.copy(parentId=a[0].id) else it}
        val issues=BankHierarchy.issues("bank",a,bad)
        assertEquals(1,issues.size);assertTrue(issues.single().contains(a[1].id));assertTrue(issues.single().contains(a[0].id))
        assertEquals(before,repo.state())
        // Existing version 53 gains guards on open, without changing or moving rows.
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER bank_account_flat_insert")
        db.close();db=Arachn0deDatabase.create(app);repo=BankRepository(db)
        assertEquals(before,repo.state());assertEquals(55,db.openHelper.writableDatabase.version)
        db.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE name='bank_account_flat_insert'").use {assertTrue(it.moveToFirst())}
    }
}
