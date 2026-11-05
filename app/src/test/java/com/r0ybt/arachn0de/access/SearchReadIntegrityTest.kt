package com.r0ybt.arachn0de.access

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.bank.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
class SearchReadIntegrityTest {
    @Test fun searchReadsActualAbonosAndRecurrencesWithoutChangingMoneyTasksOrHistories()=runBlocking<Unit> {
        val app=ApplicationProvider.getApplicationContext<Application>();app.deleteDatabase("arachn0de.db")
        val db=Arachn0deDatabase.create(app)
        try {
            val now=java.time.Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
            val projects=ProjectRepository(db.projectDao(),db);val nodes=NodeRepository(db)
            db.personDao().insert(PersonEntity("ana","Ana",null))
            val p=projects.createProject("Finanzas");val task=nodes.createNode(p.id,null,"Internet",obligation=Obligation(25000,"CLP"),responsibleIds=setOf("ana"))
            val start=RecurrenceSchedule.parse("2026-10-05")
            db.recurrenceDao().insert(RecurrenceRuleEntity("r",p.id,null,"Internet","",25000,"CLP",start,"MONTHLY",1,null,1,"ACTIVE","UTC",540,null))
            db.recurrenceDao().record(RecurrenceOccurrenceEntity("r",start,task.id))
            val bank=BankRepository(db);bank.createRoot("Banco","","bank");bank.createAccount("bank","Cuenta","ana")
            val account=bank.state().accounts.single().id
            bank.confirm(bank.preview(BankRequest(rootId="bank",contextAccountId=account,kind="INCOME",amount=40000,day=start,concept="",destinationAccountId=account)))
            bank.confirm(bank.preview(BankRequest(rootId="bank",contextAccountId=account,kind="PAYMENT",amount=10000,day=start,concept="",sourceAccountId=account,personId="ana")),mapOf(task.id to 10000))
            val access=AccessRepository(db);access.opened("node:${task.id}");access.toggle("node:${task.id}")
            val before=bank.state();val preferences=db.privatePreferenceDao().all();val ruleRows=db.recurrenceDao().rules()
            val recurrence=observeSearchRecurrences(db).first()
            val paid=db.bankDao().observePaid().first().associate {it.nodeId to it.paid}
            val nodeRows=nodes.getProjectNodes(p.id)
            val projection=searchPresentation(nodeRows,TagState(),paid,mapOf(task.id to listOf(Person("ana","Ana"))),recurrence,now,TimeZone.getTimeZone("UTC"))
            val results=searchElements(ElementSearch.index(listOf(p),nodeRows),"INTERNET","Frecuentes",SearchCriteria(favorites=true,obligations=true,recurrences=true),access.snapshot(),projection.facts)
            assertEquals(listOf(task.id),results.map {it.nodeId});assertEquals(15000L,projection.details.getValue("node:${task.id}").amount!!.amountMinor)
            assertEquals(30000L,bank.state().balance(account));assertEquals(before,bank.state())
            assertEquals(preferences,db.privatePreferenceDao().all());assertEquals(ruleRows,db.recurrenceDao().rules())
            assertEquals(before.occurrences,db.recurrenceDao().occurrences())
        } finally {db.close();app.deleteDatabase("arachn0de.db")}
    }
}
