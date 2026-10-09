package com.r0ybt.arachn0de.templates

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.defaults.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.metro.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

internal enum class TemplateDateKind { NONE, TODAY, TOMORROW, IN_DAYS, DAY_NEXT_MONTH, FIRST_DAY_NEXT, LAST_DAY }
internal data class TemplateDate(val kind: TemplateDateKind = TemplateDateKind.NONE, val number: Int = 0, val minute: Int = 0) {
    init { require(minute in 0..1439); require(when(kind) { TemplateDateKind.IN_DAYS -> number in 1..3650; TemplateDateKind.DAY_NEXT_MONTH -> number in 1..31; else -> number == 0 }) }
    fun resolve(now: Long, zone: TimeZone): Long? {
        if(kind == TemplateDateKind.LAST_DAY) {
            val calendar=Calendar.getInstance(zone).apply { timeInMillis=now; set(Calendar.DAY_OF_MONTH,getActualMaximum(Calendar.DAY_OF_MONTH)) }
            return DefaultDateResolver.instant(DefaultDate(DefaultDateKind.TODAY),DefaultTime.Minute(minute),calendar.timeInMillis,zone,false)
        }
        val mapped=when(kind) {
            TemplateDateKind.NONE->DefaultDateKind.NONE; TemplateDateKind.TODAY->DefaultDateKind.TODAY; TemplateDateKind.TOMORROW->DefaultDateKind.TOMORROW
            TemplateDateKind.IN_DAYS->DefaultDateKind.IN_DAYS; TemplateDateKind.DAY_NEXT_MONTH->DefaultDateKind.DAY_NEXT_MONTH
            TemplateDateKind.FIRST_DAY_NEXT->DefaultDateKind.FIRST_DAY_NEXT; TemplateDateKind.LAST_DAY->error("unreachable")
        }
        return DefaultDateResolver.instant(DefaultDate(mapped,number),DefaultTime.Minute(minute),now,zone,false)
    }
}
internal data class SavedTaskConfiguration(
    val title: String, val description: String = "", val purpose: NodePurpose = NodePurpose.ACTION,
    val priority: Priority = Priority.NONE, val amount: Long? = null, val currency: String? = null,
    val people: List<String> = emptyList(), val technologies: List<String> = emptyList(), val tags: List<String> = emptyList(),
    val start: TemplateDate = TemplateDate(), val due: TemplateDate = TemplateDate(),
    val metroCatalog: String? = null, val metroPlan: String? = null, val traveler: String? = null,
) {
    fun validate() {
        require(title.isNotBlank() && TitleLimits.count(title)<=TitleLimits.NODE && description.length<=262144)
        require(AttachmentReferences.ids(description).isEmpty()) { "Las plantillas conservan texto, sin adjuntos de la tarea original." }
        require(purpose != NodePurpose.LAYER)
        require((amount==null)==(currency==null)); amount?.let { Obligation(it,requireNotNull(currency)); require(purpose==NodePurpose.ACTION) }
        listOf(people,technologies,tags).forEach { require(it.size<=4096 && it.distinct().size==it.size && it.all { id->id.isNotBlank() && id.length<=256 }) }
        require((metroCatalog==null)==(metroPlan==null))
        metroPlan?.let { require(purpose==NodePurpose.ACTION); require(MetroCodec.journey(it,MetroNetwork.decode(requireNotNull(metroCatalog))).sessions.isEmpty()) }
        require(traveler==null || (metroPlan!=null && traveler.isNotBlank() && traveler.length<=256))
    }
    fun apply(draft: EditorDraft, now: Long, zone: TimeZone) {
        validate(); draft.title=title;draft.description=description;draft.purpose=purpose;draft.priority=priority
        draft.financialEnabled=amount!=null;draft.amountText=amount?.let { Money.input(it,requireNotNull(currency),Locale.forLanguageTag(draft.moneyLocaleTag)) }.orEmpty();draft.currencyCode=currency ?: "CLP"
        draft.responsibleIds=people;draft.tagIds=tags;draft.technologyIds=technologies
        draft.startAt=start.resolve(now,zone);draft.startEnabled=draft.startAt!=null;draft.dueAt=due.resolve(now,zone);draft.dueEnabled=draft.dueAt!=null
        draft.metroJourneyId=null;draft.metroJourneyRevision=null;draft.metroOriginalPlan=null;draft.metroOriginalTraveler=null
        draft.metroEditorRevision++
        draft.templateMetroPlan=metroPlan; draft.templateMetroCatalog=metroCatalog;draft.templateTraveler=traveler
        draft.batchEnabled=false;draft.recurrenceFrequency="NONE";draft.recurrenceStart="";draft.recurrenceEnd=""
    }
}
internal object SavedTemplateCodec {
    private fun date(d:TemplateDate)=JSONObject().put("kind",d.kind.name).put("number",d.number).put("minute",d.minute)
    fun encode(c: SavedTaskConfiguration): String { c.validate(); return JSONObject().apply {
        put("version",1);put("title",c.title);put("description",c.description);put("purpose",c.purpose.name);put("priority",c.priority.name)
        put("amount",c.amount ?: JSONObject.NULL);put("currency",c.currency ?: JSONObject.NULL)
        put("people",JSONArray(c.people));put("technologies",JSONArray(c.technologies));put("tags",JSONArray(c.tags));put("start",date(c.start));put("due",date(c.due))
        put("metroCatalog",c.metroCatalog ?: JSONObject.NULL);put("metroPlan",c.metroPlan ?: JSONObject.NULL);put("traveler",c.traveler ?: JSONObject.NULL)
    }.toString() }
    private fun JSONObject.text(k:String)=get(k) as? String ?: error("Texto de plantilla inválido")
    private fun JSONObject.number(k:String):Long { val n=get(k);require(n is Int || n is Long);return (n as Number).toLong() }
    private fun JSONObject.nullText(k:String)=if(isNull(k)) null else text(k)
    private fun JSONObject.fields(vararg names:String) { require(keys().asSequence().toSet()==names.toSet()) }
    fun decode(raw:String):SavedTaskConfiguration {
        val o=MetroCodec.boundedObject(raw);o.fields("version","title","description","purpose","priority","amount","currency","people","technologies","tags","start","due","metroCatalog","metroPlan","traveler");require(o.number("version")==1L)
        fun ids(k:String):List<String> { val a=o.getJSONArray(k);return (0 until a.length()).map { a.get(it) as? String ?: error("Referencia inválida") } }
        fun readDate(k:String):TemplateDate { val d=o.getJSONObject(k);d.fields("kind","number","minute");val n=d.number("number");val m=d.number("minute");require(n in 0..3650 && m in 0..1439);return TemplateDate(TemplateDateKind.valueOf(d.text("kind")),n.toInt(),m.toInt()) }
        return SavedTaskConfiguration(o.text("title"),o.text("description"),NodePurpose.valueOf(o.text("purpose")),Priority.valueOf(o.text("priority")),if(o.isNull("amount")) null else o.number("amount"),o.nullText("currency"),ids("people"),ids("technologies"),ids("tags"),readDate("start"),readDate("due"),o.nullText("metroCatalog"),o.nullText("metroPlan"),o.nullText("traveler")).also { it.validate() }
    }
    fun validate(row:SavedTemplateEntity) { require(row.id.isNotBlank() && row.id.length<=256 && row.name.isNotBlank() && row.name.length<=240);decode(row.payload) }
}
internal data class TemplateApplication(val configuration:SavedTaskConfiguration,val warnings:List<String>)
internal class SavedTemplateRepository(private val db:Arachn0deDatabase) {
    fun observe()=db.savedTemplateDao().observe()
    suspend fun save(row:SavedTemplateEntity,new:Boolean)=withContext(Dispatchers.IO) { SavedTemplateCodec.validate(row);db.withTransaction {
        if(new) db.savedTemplateDao().insert(row) else check(db.savedTemplateDao().update(row)==1)
    } }
    suspend fun delete(id:String)=withContext(Dispatchers.IO) { db.savedTemplateDao().delete(id) }
    suspend fun fromTask(id:String):SavedTaskConfiguration=withContext(Dispatchers.IO) { db.withTransaction {
        val n=requireNotNull(db.nodeDao().getById(id));require(n.purpose!="LAYER")
        val journey=db.metroDao().forNode(id);val catalog=db.metroDao().preferences()?.let { MetroCodec.preferences(it.payload).catalog }
        val plan=if(journey?.enabled==true) MetroCodec.journey(MetroJourneyData(MetroCodec.journey(journey.payload,MetroNetwork.decode(requireNotNull(catalog))).plan)) else null
        fun relative(at:Long?):TemplateDate { if(at==null) return TemplateDate();val c=Calendar.getInstance().apply { timeInMillis=at };return TemplateDate(TemplateDateKind.TODAY,minute=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE)) }
        SavedTaskConfiguration(n.title,AttachmentReferences.withoutReferences(n.description),NodePurpose.valueOf(n.purpose),Priority.valueOf(n.priority),n.amountMinor,n.currencyCode,
            db.personDao().assignmentIds(id),db.technologyDao().forNodes(listOf(id)).sortedWith(compareBy({it.position},{it.technologyId})).map {it.technologyId},
            db.tagDao().nodeTags().filter { it.nodeId==id }.map { it.tagId },relative(n.startAt),relative(n.dueAt),catalog.takeIf { plan!=null },plan,journey?.personId.takeIf { plan!=null }).also { it.validate() }
    } }
    suspend fun prepare(row:SavedTemplateEntity):TemplateApplication=withContext(Dispatchers.IO) { db.withTransaction {
        val c=SavedTemplateCodec.decode(row.payload);val warnings=mutableListOf<String>()
        fun filter(ids:List<String>,valid:Set<String>,label:String)=ids.filter { id->(id in valid).also { if(!it) warnings.add("$label ya no existe: $id") } }
        val people=filter(c.people,db.backupDao().persons().map { it.id }.toSet(),"Persona")
        val technologies=filter(c.technologies,db.technologyDao().catalog().map { it.id }.toSet(),"Tecnología")
        val tags=filter(c.tags,db.tagDao().tags().map { it.id }.toSet(),"Etiqueta")
        val traveler=c.traveler?.takeIf { db.personDao().get(it)!=null }.also { if(c.traveler!=null && it==null) warnings.add("La persona viajera ya no existe") }
        var plan=c.metroPlan;var catalog=c.metroCatalog
        if(plan!=null) {
            val stored=requireNotNull(catalog);val route=MetroCodec.journey(plan,MetroNetwork.decode(stored)).plan
            val preferences=db.metroDao().preferences()?.let { MetroCodec.preferences(it.payload) }
            if(preferences!=null) {
                val current=preferences.planningNetwork
                val updated=if(route.stops.all {it in current.stations}) MetroPlanner.plan(current,route.stops,route.express,preferences.restrictions) else null
                if(updated==null) {plan=null;catalog=null;warnings.add("El plan Metro ya no es compatible con las estaciones o restricciones; se omitirá")}
                else {if(updated.steps!=route.steps) warnings.add("El plan Metro se ajustará al catálogo y las restricciones vigentes");catalog=preferences.catalog;plan=MetroCodec.journey(MetroJourneyData(updated))}
            }
        }
        TemplateApplication(c.copy(people=people,technologies=technologies,tags=tags,metroPlan=plan,metroCatalog=catalog,traveler=traveler.takeIf { plan!=null }),warnings)
    } }
}
