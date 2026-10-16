package com.r0ybt.arachn0de.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.r0ybt.arachn0de.domain.model.CalendarDay
import java.util.*

@Entity(tableName="calendar_events", indices=[Index("date")])
data class CalendarEventEntity(@PrimaryKey val id:String, val title:String, val date:String,
    val minute:Int?, val description:String, @ColumnInfo(defaultValue="'LIGHT_GREY'") val color:String="LIGHT_GREY")
@Entity(tableName="calendar_event_people", primaryKeys=["eventId","personId"], foreignKeys=[
    ForeignKey(entity=CalendarEventEntity::class,parentColumns=["id"],childColumns=["eventId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=PersonEntity::class,parentColumns=["id"],childColumns=["personId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("personId")])
data class CalendarEventPersonEntity(val eventId:String,val personId:String)
@Dao interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY date,minute,title,id") suspend fun all():List<CalendarEventEntity>
    @Query("SELECT * FROM calendar_event_people ORDER BY eventId,personId") suspend fun people():List<CalendarEventPersonEntity>
    @Query("SELECT * FROM calendar_events WHERE id=:id") suspend fun get(id:String):CalendarEventEntity?
    @Upsert suspend fun save(event:CalendarEventEntity)
    @Insert suspend fun assign(rows:List<CalendarEventPersonEntity>)
    @Query("DELETE FROM calendar_event_people WHERE eventId=:id") suspend fun clearPeople(id:String)
    @Query("DELETE FROM calendar_events WHERE id=:id") suspend fun delete(id:String):Int
    @Query("DELETE FROM calendar_events") suspend fun clear()
}
enum class CalendarEventColor(val label:String,val argb:Long) {
    LIGHT_GREY("Gris claro",0xFFD6D6D6), RED("Rojo",0xFFFF8C8C), ORANGE("Naranja",0xFFFFAD70),
    YELLOW("Amarillo",0xFFE6D477), GREEN("Verde",0xFF98CA9D), BLUE("Azul",0xFF83BFFF), VIOLET("Violeta",0xFFC3A1E6)
}
object CalendarEventDates {
    fun format(day:CalendarDay)=String.format(Locale.ROOT,"%04d-%02d-%02d",day.year,day.month,day.day)
    fun day(date:String):CalendarDay {
        require(date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        val day=CalendarDay(date.take(4).toInt(),date.substring(5,7).toInt(),date.takeLast(2).toInt())
        calendar(day,null,TimeZone.getTimeZone("UTC")).timeInMillis
        return day
    }
    private fun calendar(day:CalendarDay,minute:Int?,zone:TimeZone)=GregorianCalendar(zone,Locale.ROOT).apply {
        require(day.year in 1..9999 && (minute==null || minute in 0..1439))
        clear();isLenient=false;set(day.year,day.month-1,day.day,minute?.div(60) ?: 12,minute?.rem(60) ?: 0)
    }
    fun validateLocal(date:String,minute:Int?,zone:TimeZone) {
        val day=day(date)
        if(minute!=null) calendar(day,minute,zone).timeInMillis // Reject DST gaps; repeated times use GregorianCalendar's later offset.
    }
}
internal fun validateCalendarEvents(events:List<CalendarEventEntity>,links:List<CalendarEventPersonEntity>,people:Set<String>) {
    require(events.map {it.id}.distinct().size==events.size && links.distinct().size==links.size)
    val ids=events.mapTo(hashSetOf()) {it.id}
    events.forEach {
        require(UUID.fromString(it.id).toString()==it.id)
        require(it.title.isNotBlank() && it.title.codePointCount(0,it.title.length)<=100 && it.description.length<=16000)
        require(CalendarEventColor.entries.any {color->color.name==it.color}) {"Color de Evento inválido."}
        CalendarEventDates.day(it.date);require(it.minute==null || it.minute in 0..1439)
    }
    links.forEach {require(it.eventId in ids && it.personId in people)}
}
internal object CalendarEventMigration44To45:Migration(44,45) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE calendar_events (id TEXT NOT NULL PRIMARY KEY,title TEXT NOT NULL,date TEXT NOT NULL,minute INTEGER,description TEXT NOT NULL)")
        db.execSQL("CREATE INDEX index_calendar_events_date ON calendar_events(date)")
        db.execSQL("CREATE TABLE calendar_event_people (eventId TEXT NOT NULL,personId TEXT NOT NULL,PRIMARY KEY(eventId,personId),FOREIGN KEY(eventId) REFERENCES calendar_events(id) ON DELETE CASCADE,FOREIGN KEY(personId) REFERENCES persons(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_calendar_event_people_personId ON calendar_event_people(personId)")
    }
}

internal object CalendarEventColorMigration45To46:Migration(45,46) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN color TEXT NOT NULL DEFAULT 'LIGHT_GREY'")
    }
}
