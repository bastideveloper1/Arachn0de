package com.r0ybt.arachn0de.access

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.RecurrenceOccurrenceEntity
import com.r0ybt.arachn0de.data.local.RecurrenceRuleEntity
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.flow.map
import java.util.TimeZone

/** Read-only session projections. No bank state, recurrence cursor or access history is written. */
internal data class SearchRecurrences(val rules:List<RecurrenceRuleEntity> = emptyList(),val occurrences:List<RecurrenceOccurrenceEntity> = emptyList())
internal fun observeSearchRecurrences(database:Arachn0deDatabase)=database.invalidationTracker
    .createFlow("recurrence_rules","recurrence_occurrences").map {
        database.withTransaction {SearchRecurrences(database.recurrenceDao().rules(),database.recurrenceDao().occurrences())}
    }
internal data class SearchRecurrenceDetail(val nextDay:Long?,val status:String)
internal enum class SearchPaymentState { PENDING, PARTIAL, PAID, COMPLETED, VERIFY }
internal data class SearchDetail(val people:List<String> = emptyList(),val amount:Obligation?=null,val payment:SearchPaymentState?=null,
    val partial:Boolean=false,val dueAt:Long?=null,val recurrence:SearchRecurrenceDetail?=null,val attention:List<AttentionReason> = emptyList())
internal data class SearchPresentation(val facts:SearchFacts,val details:Map<String,SearchDetail>)

internal fun searchPresentation(nodes:List<Node>,tags:TagState,paid:Map<String,Long>,people:Map<String,List<Person>>,
    recurrences:SearchRecurrences,now:Long,zone:TimeZone):SearchPresentation {
    val parents=nodes.mapNotNullTo(hashSetOf()) {it.parentId}
    val recurring=recurrences.occurrences.associate {it.nodeId to it.ruleId}
    val rules=recurrences.rules.associateBy {it.id}
    // Calculate a rule's next date once, even when many historical occurrences are visible.
    val ruleDetails=rules.mapValues {(_,rule)->
        val status=if(rule.isDeleted)"DELETED" else rule.status
        val next=if(status!="ACTIVE")null else runCatching {
            val frequency=RecurrenceFrequency.valueOf(rule.frequency)
            val today=RecurrenceSchedule.localDay(now,rule.zoneId)
            var i=maxOf(rule.nextIndex,RecurrenceSchedule.indexOnOrAfter(rule.startDay,frequency,rule.interval,today))
            var day=RecurrenceSchedule.date(rule.startDay,frequency,rule.interval,i)
            if(RecurrenceSchedule.timestamp(day,rule.zoneId,rule.dueMinute)<=now) {
                i=Math.addExact(i,1);day=RecurrenceSchedule.date(rule.startDay,frequency,rule.interval,i)
            }
            day.takeIf {rule.endDay==null || it<=rule.endDay}
        }.getOrNull()
        SearchRecurrenceDetail(next,status)
    }
    val details=nodes.associate {node->
        val leaf=node.id !in parents
        val actual=node.copy(hasChildren=!leaf)
        val amount=node.obligation?.takeIf {leaf && actual.purpose==NodePurpose.ACTION}
        val abono=paid[node.id] ?: 0L
        val remaining=amount?.let {(it.amountMinor-abono).coerceAtLeast(0)}
        val payment=amount?.let {when {
            abono>0 && remaining==0L->SearchPaymentState.PAID
            node.isCompleted->SearchPaymentState.COMPLETED
            node.awaitingVerification->SearchPaymentState.VERIFY
            abono>0->SearchPaymentState.PARTIAL
            else->SearchPaymentState.PENDING
        }}
        val shownAmount=amount?.let {if(abono>0 && requireNotNull(remaining)>0)it.copy(amountMinor=requireNotNull(remaining)) else it}
        "node:${node.id}" to SearchDetail(
            people=people[node.id].orEmpty().distinctBy {it.id}.map {it.name}.filter {it.isNotBlank()},
            amount=shownAmount,payment=payment,partial=amount!=null && abono>0 && remaining!=null && remaining>0,dueAt=node.dueAt,
            recurrence=recurring[node.id]?.let {ruleDetails[it] ?: SearchRecurrenceDetail(null,"")},
            attention=if(leaf)AttentionPolicy.reasons(actual,now,zone) else emptyList())
    }
    return SearchPresentation(SearchFacts(
        attention=details.filterValues {it.attention.isNotEmpty()}.keys,
        obligations=nodes.filter {it.projectId!=null && it.id !in parents && it.purpose==NodePurpose.ACTION && it.obligation!=null}.mapTo(hashSetOf()) {"node:${it.id}"},
        recurring=recurring.keys.mapTo(hashSetOf()) {"node:$it"},
        tags=tags.nodeIds.mapKeys {"node:${it.key}"}+tags.projectIds.mapKeys {"project:${it.key}"}),details)
}
