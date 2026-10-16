package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import java.util.TimeZone

internal data class CalendarEventState(val events:List<CalendarEventEntity> = emptyList(),val people:List<CalendarEventPersonEntity> = emptyList()) {
    fun matching(person:String?)=events.filter {person==null || people.any {link->link.eventId==it.id && link.personId==person}}
}
internal class CalendarEventRepository(private val db:Arachn0deDatabase) {
    private val dao=db.calendarEventDao()
    fun observe()=db.invalidationTracker.createFlow("calendar_events","calendar_event_people").map {
        db.withTransaction {CalendarEventState(dao.all(),dao.people())}
    }.flowOn(Dispatchers.IO)
    suspend fun save(event:CalendarEventEntity,people:Set<String>,isNew:Boolean,zone:TimeZone=TimeZone.getDefault())=withContext(Dispatchers.IO) {
        CalendarEventDates.validateLocal(event.date,event.minute,zone)
        db.withTransaction {
            val links=people.map {CalendarEventPersonEntity(event.id,it)}
            validateCalendarEvents(listOf(event),links,db.backupDao().persons().mapTo(hashSetOf()) {it.id})
            require((dao.get(event.id)==null)==isNew) {"El Evento cambió o ya no existe."}
            dao.save(event);dao.clearPeople(event.id);dao.assign(links)
        }
    }
    suspend fun delete(id:String)=withContext(Dispatchers.IO) {db.withTransaction {dao.delete(id)>0}}
}
