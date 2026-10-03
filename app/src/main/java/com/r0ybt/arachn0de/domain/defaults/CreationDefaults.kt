package com.r0ybt.arachn0de.domain.defaults

import com.r0ybt.arachn0de.domain.model.*
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

sealed interface DefaultValue<out T> {
    data object Inherit : DefaultValue<Nothing>
    data class Own<T>(val value: T) : DefaultValue<T>
}
enum class DefaultDateKind { NONE, TODAY, TOMORROW, IN_DAYS, FIRST_DAY, FIRST_DAY_NEXT, DAY_OF_MONTH, DAY_NEXT_MONTH, FIRST_MONDAY, FIRST_MONDAY_NEXT }
data class DefaultDate(val kind: DefaultDateKind = DefaultDateKind.NONE, val number: Int = 0) {
    init { require(when(kind) {
        DefaultDateKind.IN_DAYS -> number in 1..3650
        DefaultDateKind.DAY_OF_MONTH, DefaultDateKind.DAY_NEXT_MONTH -> number in 1..31
        else -> number == 0
    }) { "Parámetro de fecha inválido." } }
}
sealed interface DefaultTime {
    data object Unspecified : DefaultTime
    data class Minute(val value: Int) : DefaultTime { init { require(value in 0..1439) } }
}
sealed interface DefaultsScope {
    data object Global : DefaultsScope
    data class Project(val projectId: String) : DefaultsScope
    data class Layer(val projectId: String, val nodeId: String) : DefaultsScope
}
data class CreationDefaults(
    val purpose: DefaultValue<NodePurpose> = DefaultValue.Inherit,
    val obligation: DefaultValue<Boolean> = DefaultValue.Inherit,
    val currency: DefaultValue<String> = DefaultValue.Inherit,
    val priority: DefaultValue<Priority> = DefaultValue.Inherit,
    val tags: DefaultValue<Set<String>> = DefaultValue.Inherit,
    val people: DefaultValue<Set<String>> = DefaultValue.Inherit,
    val start: DefaultValue<DefaultDate> = DefaultValue.Inherit,
    val startTime: DefaultValue<DefaultTime> = DefaultValue.Inherit,
    val due: DefaultValue<DefaultDate> = DefaultValue.Inherit,
    val dueTime: DefaultValue<DefaultTime> = DefaultValue.Inherit,
) {
    init { if(currency is DefaultValue.Own) require(currency.value in setOf("CLP","USD","EUR")) }
    fun applyTo(base: EffectiveCreationDefaults) = EffectiveCreationDefaults(
        purpose.resolve(base.purpose),obligation.resolve(base.obligation),currency.resolve(base.currency),priority.resolve(base.priority),
        tags.resolve(base.tags).toSet(),people.resolve(base.people).toSet(),start.resolve(base.start),startTime.resolve(base.startTime),due.resolve(base.due),dueTime.resolve(base.dueTime))
}
private fun <T> DefaultValue<T>.resolve(base:T):T = when(this) { DefaultValue.Inherit -> base; is DefaultValue.Own -> value }
data class EffectiveCreationDefaults(
    val purpose:NodePurpose = NodePurpose.ACTION, val obligation:Boolean = false, val currency:String = "CLP",
    val priority:Priority = Priority.NONE, val tags:Set<String> = emptySet(), val people:Set<String> = emptySet(),
    val start:DefaultDate = DefaultDate(), val startTime:DefaultTime = DefaultTime.Unspecified,
    val due:DefaultDate = DefaultDate(), val dueTime:DefaultTime = DefaultTime.Unspecified,
)
object CreationDefaultsResolver {
    /** One indexed collection, iterative ancestry; root defaults precede the most specific layer. */
    fun resolve(projectId:String, parentId:String?, nodes:Map<String,Node>, global:CreationDefaults,
        project:CreationDefaults, layers:Map<String,CreationDefaults>):EffectiveCreationDefaults {
        val chain=ArrayList<String>();val visited=HashSet<String>();var cursor=parentId
        while(cursor!=null) {
            require(visited.add(cursor)) { "Ciclo en los valores predeterminados." }
            val node=requireNotNull(nodes[cursor]) { "La capa ya no existe." }
            require(node.projectId==projectId) { "Contexto de otro proyecto." }
            chain.add(cursor);cursor=node.parentId
        }
        var result=project.applyTo(global.applyTo(EffectiveCreationDefaults()))
        chain.asReversed().forEach { layers[it]?.let { defaults -> result=defaults.applyTo(result) } }
        return result
    }
}
object DefaultDateResolver {
    fun instant(rule:DefaultDate, time:DefaultTime, now:Long, zone:TimeZone, due:Boolean):Long? {
        if(rule.kind==DefaultDateKind.NONE) return null
        val today=RecurrenceSchedule.localDay(now,zone.id)
        val day=GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { timeInMillis=today*86_400_000L }
        fun month(offset:Int, monday:Boolean=false, date:Int=1) {
            day.set(Calendar.DAY_OF_MONTH,1);day.add(Calendar.MONTH,offset)
            day.set(Calendar.DAY_OF_MONTH,minOf(date,day.getActualMaximum(Calendar.DAY_OF_MONTH)))
            if(monday) day.add(Calendar.DAY_OF_MONTH,(Calendar.MONDAY-day.get(Calendar.DAY_OF_WEEK)+7)%7)
        }
        when(rule.kind) {
            DefaultDateKind.NONE -> return null
            DefaultDateKind.TODAY -> Unit
            DefaultDateKind.TOMORROW -> day.add(Calendar.DAY_OF_MONTH,1)
            DefaultDateKind.IN_DAYS -> day.add(Calendar.DAY_OF_MONTH,rule.number)
            DefaultDateKind.FIRST_DAY -> month(0)
            DefaultDateKind.FIRST_DAY_NEXT -> month(1)
            DefaultDateKind.DAY_OF_MONTH -> { month(0,date=rule.number);if(due && day.timeInMillis/86_400_000L<today) month(1,date=rule.number) }
            DefaultDateKind.DAY_NEXT_MONTH -> month(1,date=rule.number)
            DefaultDateKind.FIRST_MONDAY -> { month(0,monday=true);if(due && day.timeInMillis/86_400_000L<today) month(1,monday=true) }
            DefaultDateKind.FIRST_MONDAY_NEXT -> month(1,monday=true)
        }
        require(day.get(Calendar.ERA)==GregorianCalendar.AD && day.get(Calendar.YEAR) in 1..9999) { "Fecha fuera de rango." }
        return RecurrenceSchedule.timestamp(day.timeInMillis/86_400_000L,zone.id,(time as? DefaultTime.Minute)?.value ?: 0)
    }
}
