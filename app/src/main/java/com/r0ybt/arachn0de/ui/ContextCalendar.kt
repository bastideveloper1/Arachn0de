package com.r0ybt.arachn0de.ui

import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import com.r0ybt.arachn0de.calendar.CalendarBirthdays
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

/** Transient navigation state only, retained in the application's private saveable registry. */
internal class CalendarPresentation(private val storage:android.content.SharedPreferences) {
    var collapsed by mutableStateOf(storage.all[KEY] as? Boolean ?: false)
        private set
    fun reload() {collapsed=storage.all[KEY] as? Boolean ?: false}
    fun updateCollapsed(value:Boolean) {
        if(value==collapsed)return
        if(!runCatching {storage.edit().putBoolean(KEY,value).commit()}.getOrDefault(false))return
        collapsed=value
    }
    companion object {const val NAME=CalendarPresentationPreference.NAME;const val KEY=CalendarPresentationPreference.KEY}
}

internal class ContextCalendarState(today:CalendarDay,internal var presentation:CalendarPresentation?=null) {
    var conditions by mutableStateOf(com.r0ybt.arachn0de.domain.model.CardConditionFilter())
    var urgentOnly:Boolean
        get()=conditions.urgentOnly
        set(value) {conditions=conditions.copy(urgentOnly=value)}
    var dueCondition:com.r0ybt.arachn0de.domain.model.AttentionReason?
        get()=conditions.due
        set(value) {conditions=conditions.copy(due=value)}
    var favorites by mutableStateOf(false)
    val filtering get()=selected!=null || conditions.active || favorites
    fun clearCardFilters() {conditions=com.r0ybt.arachn0de.domain.model.CardConditionFilter();favorites=false}
    fun bind(value:CalendarPresentation) {if(ownMode==0)ownMode=expanded;presentation=value}
    private var ownMode by mutableIntStateOf(1)
    var mode:Int
        get()=if(presentation?.collapsed==true)0 else ownMode
        set(value) {presentation?.updateCollapsed(value==0);ownMode=if(presentation!=null && value==0)expanded else value}
    var expanded by mutableIntStateOf(1)
    internal var followsToday by mutableStateOf(true)
        private set
    private var anchorValue by mutableStateOf(today)
    private var selectedValue by mutableStateOf<CalendarDay?>(null)
    var anchor:CalendarDay
        get()=anchorValue
        set(value) {followsToday=false;anchorValue=value}
    var selected:CalendarDay?
        get()=selectedValue
        set(value) {followsToday=false;selectedValue=value}

    fun current(today:CalendarDay) {
        anchorValue=today
        if(selectedValue!=null) selectedValue=today
        followsToday=true
    }
    fun refreshToday(today:CalendarDay) {if(followsToday) current(today)}
}
internal class ContextCalendarStore {
    private var presentation:CalendarPresentation?=null
    fun bind(value:CalendarPresentation) {presentation=value;states.values.forEach {it.bind(value)}}
    private val states=mutableMapOf<String,ContextCalendarState>()
    fun scope(key:String,today:CalendarDay)=states.getOrPut(key) {ContextCalendarState(today,presentation)}.also {it.refreshToday(today)}
    companion object {
        val Saver=listSaver<ContextCalendarStore,String>(save={store->listOf("v3")+store.states.flatMap {(key,s)->
            listOf(key,s.mode.toString(),s.expanded.toString(),s.anchor.year.toString(),s.anchor.month.toString(),s.anchor.day.toString(),s.selected?.year?.toString().orEmpty(),s.selected?.month?.toString().orEmpty(),s.selected?.day?.toString().orEmpty(),s.urgentOnly.toString(),s.favorites.toString(),s.followsToday.toString(),s.dueCondition?.name.orEmpty())
        }},restore={rows->ContextCalendarStore().also {store->
            val current=rows.firstOrNull()=="v3"
            val versioned=current || rows.firstOrNull()=="v2"
            (if(versioned) rows.drop(1) else rows).chunked(if(current)13 else if(versioned)12 else 11).forEach {r->
            store.states[r[0]]=ContextCalendarState(CalendarDay(r[3].toInt(),r[4].toInt(),r[5].toInt())).apply {
                // The retired compound flag is not reinterpreted as an individual condition.
                if(current) {urgentOnly=r[9].toBoolean();dueCondition=r[12].takeIf {it.isNotEmpty()}?.let(com.r0ybt.arachn0de.domain.model.AttentionReason::valueOf)}
                favorites=r[10].toBoolean()
                mode=r[1].toInt();expanded=r[2].toInt();selected=if(r[6].isEmpty()) null else CalendarDay(r[6].toInt(),r[7].toInt(),r[8].toInt())
                // Older saved navigation has no explicit intent: preserve its dates.
                if(versioned && r[11].toBoolean()) current(anchor)
            }
        }}})
    }
}
@Composable internal fun rememberContextCalendarStore():ContextCalendarStore {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val privateContext=(context as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.current?.value?.context ?: context
    val storage=remember(privateContext) {privateContext.getSharedPreferences(CalendarPresentation.NAME,0)}
    val presentation=remember(storage) {CalendarPresentation(storage)}
    DisposableEffect(storage,presentation) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->if(key==null || key==CalendarPresentation.KEY)presentation.reload()}
        storage.registerOnSharedPreferenceChangeListener(listener)
        onDispose {storage.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return rememberSaveable(saver=ContextCalendarStore.Saver) {ContextCalendarStore()}.also {it.bind(presentation)}
}
@Composable internal fun rememberContextCalendarIndex(tree:NodeTreeSnapshot,zoneId:String,projectId:String?=null,parentId:String?=null):State<ContextCalendarIndex?> {
    val result=remember(tree,zoneId,projectId,parentId) {mutableStateOf<ContextCalendarIndex?>(null)}
    LaunchedEffect(result) {result.value=withContext(Dispatchers.Default) {ContextCalendarIndex(tree,TimeZone.getTimeZone(zoneId),projectId,parentId)}}
    return result
}

@Composable internal fun ContextCalendar(state:ContextCalendarState,index:ContextCalendarIndex?,today:CalendarDay,resultCount:Int?=null,birthdays:CalendarBirthdays?=null) {
    state.refreshToday(today)
    val modeScope=rememberCoroutineScope()
    var savingMode by remember {mutableStateOf(false)}
    fun changeMode(value:Int) {
        if(savingMode)return
        savingMode=true
        modeScope.launch {
            try {withContext(Dispatchers.IO+kotlinx.coroutines.NonCancellable) {state.mode=value}}
            finally {savingMode=false}
        }
    }
    val locale=LocalConfiguration.current.locales[0]
    val dateFormat=remember(locale) {DateFormat.getDateInstance(DateFormat.MEDIUM,locale).apply {timeZone=TimeZone.getTimeZone("UTC")}}
    val monthFormat=remember(locale) {SimpleDateFormat("MMM yyyy",locale).apply {timeZone=TimeZone.getTimeZone("UTC")}}
    val collapsedFormat=remember {SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy",Locale("es")).apply {timeZone=TimeZone.getTimeZone("UTC")}}
    val selected=state.selected
    val count=resultCount ?: selected?.let {index?.tasksOn(it)?.size} ?: 0
    val reference=selected ?: state.anchor
    GeneralCard(modifier=Modifier.fillMaxWidth().testTag("context-calendar"),colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface)) {
        Column(Modifier.padding(horizontal=4.dp,vertical=2.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            fun shift(delta:Int) {state.anchor=if(state.expanded==2) state.anchor.calendarMonth.shifted(delta).selecting(state.anchor.day) else TemporalRanges.shift(state.anchor,7*delta)}
            var periodOpen by remember {mutableStateOf(false)}
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val directToday=maxWidth>=288.dp
                val directMode=maxWidth>=240.dp
                Row(Modifier.fillMaxWidth().then(calendarMonthSwipe(state,state.mode!=0)).testTag("context-calendar-toolbar"),verticalAlignment=Alignment.CenterVertically) {
                    if(state.mode==0) {
                        Text(collapsedFormat.format(Date(CalendarDates.labelInstant(reference))).replaceFirstChar {it.titlecase(Locale("es"))},
                        modifier=Modifier.weight(1f).padding(horizontal=8.dp).testTag("context-calendar-collapsed-date"),
                        style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Row(horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(index?.tasksOn(reference)?.isNotEmpty()==true) Box(Modifier.size(4.dp).background(Arachn0deColors.Accent,RoundedCornerShape(2.dp)).testTag("context-collapsed-task-marker"))
                            if(birthdays?.on(reference)?.isNotEmpty()==true) Icon(Icons.Default.Cake,"Cumpleaños",Modifier.size(12.dp).testTag("context-collapsed-birthday-marker"),tint=cardIndicatorColor("birthday",true))
                        }
                    } else {
                        Box(Modifier.weight(1f)) {
                            TextButton(onClick={periodOpen=true},contentPadding=PaddingValues(2.dp),modifier=Modifier.fillMaxWidth().height(48.dp).testTag("context-calendar-period").semantics {contentDescription="Navegar ${monthFormat.format(Date(CalendarDates.labelInstant(state.anchor)))}"}) {
                                CalendarToolbarLabel(monthFormat.format(Date(CalendarDates.labelInstant(state.anchor))),Icons.Default.DateRange)
                            }
                            DropdownMenu(expanded=periodOpen,onDismissRequest={periodOpen=false}) {
                                Row(Modifier.padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                                    IconButton(onClick={shift(-1)},enabled=state.anchor.year>1,modifier=Modifier.size(48.dp).testTag("context-calendar-previous")) {Icon(Icons.Default.ChevronLeft,"Periodo anterior",Modifier.size(20.dp))}
                                    Text(monthFormat.format(Date(CalendarDates.labelInstant(state.anchor))),style=MaterialTheme.typography.labelMedium)
                                    IconButton(onClick={shift(1)},enabled=state.anchor.year<9999,modifier=Modifier.size(48.dp).testTag("context-calendar-next")) {Icon(Icons.Default.ChevronRight,"Periodo siguiente",Modifier.size(20.dp))}
                                }
                                if(!directToday) DropdownMenuItem(text={Text("Hoy")},onClick={state.current(today);periodOpen=false},modifier=Modifier.testTag("context-calendar-today"))
                                if(!directMode) DropdownMenuItem(enabled=!savingMode,text={Text(if(state.expanded==1)"Semana" else "Mes")},onClick={if(!savingMode) {state.expanded=if(state.expanded==1)2 else 1;changeMode(state.expanded);periodOpen=false}},modifier=Modifier.testTag("context-calendar-mode"))
                                if(state.filtering) {
                                    Text(if(index==null) "Cargando tareas…" else (selected?.let {dateFormat.format(Date(CalendarDates.labelInstant(it)))+" · "}.orEmpty()+"$count resultados"),Modifier.padding(horizontal=12.dp).testTag("context-calendar-count"),style=MaterialTheme.typography.labelMedium)
                                    DropdownMenuItem(text={Text("Ver todas")},onClick={state.selected=null;state.clearCardFilters();periodOpen=false},modifier=Modifier.testTag("context-calendar-clear"))
                                }
                                DropdownMenuItem(text={Text("Cerrar")},onClick={periodOpen=false},modifier=Modifier.testTag("context-calendar-period-close"))
                            }
                        }
                        if(directToday) TextButton(onClick={state.current(today)},contentPadding=PaddingValues(2.dp),modifier=Modifier.size(48.dp).testTag("context-calendar-today").semantics {contentDescription="Hoy"}) {CalendarToolbarLabel("Hoy",Icons.Default.Today)}
                        if(directMode) TextButton(enabled=!savingMode,onClick={if(!savingMode) {state.expanded=if(state.expanded==1) 2 else 1;if(state.mode!=0) changeMode(state.expanded)}},contentPadding=PaddingValues(2.dp),modifier=Modifier.size(48.dp).testTag("context-calendar-mode").semantics {contentDescription=if(state.expanded==1)"Semana" else "Mes"}) {CalendarToolbarLabel(if(state.expanded==1) "Sem" else "Mes",if(state.expanded==1)Icons.Default.CalendarViewWeek else Icons.Default.CalendarMonth)}
                    }
                    IconButton(enabled=!savingMode,onClick={periodOpen=false;changeMode(if(state.mode==0) state.expanded else 0)},modifier=Modifier.size(48.dp).testTag("context-calendar-fold")) {
                        LucideIcon(if(state.mode==0) Icons.Default.ExpandMore else Icons.Default.ExpandLess,if(state.mode==0) "Desplegar calendario" else "Plegar calendario",Modifier.size(20.dp),tint=LocalContentColor.current)
                    }
                }
            }
            if(state.mode!=0) {
                val cells=remember(state.anchor,state.mode) {if(state.mode==2) state.anchor.calendarMonth.cells(Calendar.MONDAY) else TemporalRanges.weekStart(state.anchor,Calendar.MONDAY).let {start->List(7) {TemporalRanges.shift(start,it)}}}
                val names=remember(locale) {DateFormatSymbols(locale).shortWeekdays}
                val cellWidth=48.dp*LocalDensity.current.fontScale.coerceAtLeast(1f)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val scroll=rememberScrollState()
                    val overflow=cellWidth*7>maxWidth
                    Column(Modifier.then(if(overflow)Modifier.horizontalScroll(scroll) else calendarMonthSwipe(state))
                        .width(maxOf(maxWidth,cellWidth*7)).testTag("context-calendar-grid")) {
                        cells.chunked(7).forEach {week->Row(Modifier.fillMaxWidth()) {week.forEachIndexed {i,day->
                            if(day==null) Spacer(Modifier.weight(1f).height(48.dp)) else {
                                val active=day==selected;val count=index?.tasksOn(day)?.size ?: 0
                                val birthdayCount=birthdays?.on(day)?.size ?: 0
                                Column(Modifier.weight(1f).heightIn(min=48.dp)
                                    .background(if(active) Arachn0deColors.Selection else Arachn0deColors.Surface,RoundedCornerShape(8.dp))
                                    .then(if(day==today) Modifier.border(1.dp,Arachn0deColors.Accent,RoundedCornerShape(8.dp)) else Modifier)
                                    .testTag("context-calendar-day:${day.key}")
                                    .semantics {this.selected=active;contentDescription="${dateFormat.format(Date(CalendarDates.labelInstant(day)))}, $count tareas, $birthdayCount cumpleaños"}
                                    .clickable(role=Role.Button) {state.selected=if(active) null else day;state.anchor=day},horizontalAlignment=Alignment.CenterHorizontally) {
                                    Text(names[(i+1)%7+1],style=MaterialTheme.typography.labelSmall,maxLines=1)
                                    Text(day.day.toString(),style=MaterialTheme.typography.labelLarge)
                                    Row(Modifier.padding(bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
                                        if(count>0) Box(Modifier.size(4.dp).background(Arachn0deColors.Accent,RoundedCornerShape(2.dp)))
                                        if(birthdayCount>0) Icon(Icons.Default.Cake,"Cumpleaños",Modifier.size(12.dp).testTag("context-birthday-marker:${day.key}"),tint=cardIndicatorColor("birthday",true))
                                    }
                                }
                            }
                        }}}
                    }
                }
            }

        }
    }
}

/** Axis-locked drag cancels day/button clicks; overflowing grids keep their own scroll. */
@Composable private fun calendarMonthSwipe(state:ContextCalendarState,enabled:Boolean=true):Modifier {
    val threshold=with(LocalDensity.current) {48.dp.toPx()}
    return if(!enabled)Modifier else Modifier.pointerInput(state,threshold) {
        var distance=0f
        detectHorizontalDragGestures(onDragStart={distance=0f},onDragCancel={distance=0f},onDragEnd={
            if(kotlin.math.abs(distance)>=threshold) {
                val month=state.anchor.calendarMonth
                val next=distance<0
                if((next && (month.year<9999 || month.month<12)) || (!next && (month.year>1 || month.month>1)))
                    state.anchor=month.shifted(if(next)1 else -1).selecting(state.anchor.day)
            }
            distance=0f
        },onHorizontalDrag={change,amount->change.consume();distance+=amount})
    }
}


/** Keep labels when they fit; large fonts use the same control's accessible icon. */
@Composable private fun CalendarToolbarLabel(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector) {
    val density=LocalDensity.current
    val style=MaterialTheme.typography.labelSmall
    val measured=androidx.compose.ui.text.rememberTextMeasurer().measure(label,style,softWrap=false).size
    BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        if(measured.width<=with(density) {maxWidth.toPx()} && measured.height<=with(density) {maxHeight.toPx()}) Text(label,style=style,maxLines=1)
        else Icon(icon,null,Modifier.size(20.dp))
    }
}
