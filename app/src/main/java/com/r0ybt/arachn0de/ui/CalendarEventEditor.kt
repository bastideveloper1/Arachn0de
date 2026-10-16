package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.CalendarEventRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.OperationState
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import java.util.*

internal class CalendarEventDraft(val id:String,val isNew:Boolean,title:String,date:String,minute:Int?,description:String,people:List<String>,color:String="LIGHT_GREY") {
    var title by mutableStateOf(title);var date by mutableStateOf(date)
    var minute by mutableStateOf(minute);var description by mutableStateOf(description)
    var people by mutableStateOf(people)
    var color by mutableStateOf(color)
    fun entity()=CalendarEventEntity(id,title.trim(),date,minute,description,color)
    companion object {
        val Saver=listSaver<CalendarEventDraft?,String>(save={if(it==null) emptyList() else listOf("__event_color_v1",it.color)+listOf(it.id,it.isNew.toString(),it.title,it.date,it.minute?.toString().orEmpty(),it.description)+it.people},
            restore={stored->
                val colored=stored.firstOrNull()=="__event_color_v1"
                val values=if(colored) stored.drop(2) else stored
                if(values.isEmpty()) null else CalendarEventDraft(values[0],values[1].toBoolean(),values[2],values[3],values[4].toIntOrNull(),values[5],values.drop(6),if(colored) stored[1] else "LIGHT_GREY")
            })
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarEventEditor(draft:CalendarEventDraft,repo:CalendarEventRepository,people:List<Person>,peopleLoaded:Boolean,onClose:()->Unit) {
    key(draft.id) {EventEditorPilot(draft,repo,people,peopleLoaded,onClose)}
}

private fun CalendarEventDraft.signature()=listOf(title,date,minute?.toString().orEmpty(),description,color)+people.sorted()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorPilot(draft:CalendarEventDraft,repo:CalendarEventRepository,people:List<Person>,peopleLoaded:Boolean,onClose:()->Unit) {
    val scope=rememberCoroutineScope();val operation=remember(draft.id,repo) {OperationState(scope)}
    val baseline=rememberSaveable {draft.signature()}
    var mode by rememberSaveable {mutableStateOf("COMPACT")}
    var started by rememberSaveable {mutableStateOf(emptyList<String>())}
    var confirmDiscard by rememberSaveable {mutableStateOf(false)}
    var deleting by rememberSaveable {mutableStateOf(false)}
    val provisionalState=rememberSaveable(stateSaver=CalendarEventDraft.Saver) {
        mutableStateOf<CalendarEventDraft?>(CalendarEventDraft(draft.id,draft.isNew,draft.title,draft.date,draft.minute,draft.description,draft.people,draft.color))
    }
    val provisional=requireNotNull(provisionalState.value)
    var title by rememberSaveable(stateSaver=androidx.compose.ui.text.input.TextFieldValue.Saver) {mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(draft.title))}
    var description by rememberSaveable(stateSaver=androidx.compose.ui.text.input.TextFieldValue.Saver) {mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(draft.description))}
    var descriptionFull by rememberSaveable {mutableStateOf(false)}
    var peopleQuery by rememberSaveable {mutableStateOf("")}
    val dateState=rememberDatePickerState(initialSelectedDateMillis=CalendarDates.labelInstant(CalendarEventDates.day(draft.date)),yearRange=1..9999)
    val timeState=rememberTimePickerState(initialHour=(draft.minute ?: 720)/60,initialMinute=(draft.minute ?: 720)%60,is24Hour=true)
    fun provisionalDate()=dateState.selectedDateMillis?.let {CalendarEventDates.format(CalendarDates.localDay(it,TimeZone.getTimeZone("UTC")))}
    fun provisionalMinute()=if(provisional.minute==null) null else timeState.hour*60+timeState.minute
    fun changed(property:String)=when(property) {
        "TITLE"->title.text!=draft.title
        "DATE"->provisionalDate()!=draft.date
        "TIME"->provisionalMinute()!=draft.minute
        "DESCRIPTION"->description.text!=draft.description
        "PEOPLE"->provisional.people.toSet()!=draft.people.toSet()
        "COLOR"->provisional.color!=draft.color
        else->false
    }
    val pending=started.any(::changed)
    fun requestClose() {if(draft.signature()!=baseline || pending) confirmDiscard=true else onClose()}
    val busy=operation.busy
    val focus=LocalFocusManager.current
    fun open(property:String) {
        focus.clearFocus()
        if(property !in started) {
            when(property) {
                "TITLE"->title=androidx.compose.ui.text.input.TextFieldValue(draft.title)
                "DATE"->{dateState.selectedDateMillis=CalendarDates.labelInstant(CalendarEventDates.day(draft.date));dateState.displayedMonthMillis=requireNotNull(dateState.selectedDateMillis)}
                "TIME"->{provisional.minute=draft.minute;timeState.hour=(draft.minute ?: 720)/60;timeState.minute=(draft.minute ?: 720)%60}
                "DESCRIPTION"->description=androidx.compose.ui.text.input.TextFieldValue(draft.description)
                "PEOPLE"->{provisional.people=draft.people;peopleQuery=""}
                "COLOR"->provisional.color=draft.color
            }
            started=started+property
        }
        mode=property
    }
    fun reduce() {
        // TextField collapses a selection when losing focus; reducing must keep it.
        val retainedTitle=title;val retainedDescription=description
        focus.clearFocus();mode="COMPACT";descriptionFull=false
        title=retainedTitle;description=retainedDescription
    }
    fun confirm() {
        when(mode) {
            "TITLE"->draft.title=title.text
            "DATE"->draft.date=requireNotNull(provisionalDate())
            "TIME"->draft.minute=provisionalMinute()
            "DESCRIPTION"->draft.description=description.text
            "PEOPLE"->draft.people=provisional.people.filter {id->people.any {it.id==id}}
            "COLOR"->draft.color=provisional.color
        }
        started=started-mode;reduce()
    }
    val labels=mapOf("TITLE" to "Título","DATE" to "Fecha","TIME" to "Hora","DESCRIPTION" to "Descripción","PEOPLE" to "Responsables","COLOR" to "Color")
    val validProperty=when(mode) {
        "TITLE"->title.text.isNotBlank() && TitleLimits.count(title.text)<=100
        "DATE"->dateState.selectedDateMillis!=null
        "DESCRIPTION"->description.text.length<=16000
        "PEOPLE"->peopleLoaded
        else->true
    }
    // Only explicit actions may dispose of the private draft.
    fun reduceOrClose() {if(mode=="DESCRIPTION" && descriptionFull) descriptionFull=false else if(mode!="COMPACT") reduce() else requestClose()}
    ModularPanelSurface(modifier=Modifier.fillMaxWidth().testTag("event-sheet"),shadowElevation=0.dp) {
        BackHandler {if(!busy) reduceOrClose()}
        key(mode) {ModularPanelLayout(outlinedBody=mode!="COMPACT",headerDivider=mode!="COMPACT",bodyModifier=Modifier.testTag("event-fields"),fullScreen=mode=="DESCRIPTION" && descriptionFull,fieldOwnsScroll=mode=="DESCRIPTION",
            header={Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(labels[mode] ?: if(draft.isNew) "Nuevo evento" else "Editar evento",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f).padding(bottom=if(mode=="COMPACT") 0.dp else 8.dp))
                if(draft.isNew && mode=="COMPACT") IconButton(enabled=!busy,onClick=::requestClose,modifier=Modifier.testTag("event-close")) {Icon(Icons.Default.Close,"Cerrar")}
            }},
            actions={Column(Modifier.fillMaxWidth().testTag("event-actions")) {
                if(mode=="DESCRIPTION" && !descriptionFull) TextButton(enabled=!busy,onClick={descriptionFull=true},modifier=Modifier.testTag("event-description-expand")) {Text("Ampliar")}
                ModularPanelActions(cancel={
                    if(mode!="COMPACT") TextButton(enabled=!busy,onClick={started=started-mode;reduce()},modifier=Modifier.testTag("event-${mode.lowercase(Locale.ROOT)}-cancel")) {Text("Cancelar")}
                    else PrimaryControlButton(enabled=!busy,onClick=::requestClose,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("event-cancel")) {Text("Cancelar")}
                },confirm={
                    if(mode!="COMPACT") TextButton(enabled=!busy && validProperty,onClick=::confirm,modifier=Modifier.testTag("event-${mode.lowercase(Locale.ROOT)}-confirm")) {Text("Confirmar")}
                    else PrimaryControlButton(enabled=!busy && !pending && peopleLoaded && draft.title.isNotBlank() && TitleLimits.count(draft.title)<=100 && draft.description.length<=16000,onClick={
                        operation.submit("No se pudo guardar. Revisa la fecha, la hora local y los responsables; una hora inexistente por cambio de horario no es válida.",{
                            repo.save(draft.entity(),draft.people.toSet(),draft.isNew);true
                        },onClose)
                    },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("event-save")) {Text("Guardar")}
                })
            }}) {
            when(mode) {
                "COMPACT" -> {
                    ModularOptionGrid(listOf(
                        ModularPanelOption("Título",Icons.Default.Title,"event-option:title",!busy) {open("TITLE")},
                        ModularPanelOption("Fecha",Icons.Default.Event,"event-date",!busy) {open("DATE")},
                        ModularPanelOption("Hora",Icons.Default.AccessTime,"event-option:time",!busy) {open("TIME")},
                        ModularPanelOption("Descripción",Icons.Default.Subject,"event-option:description",!busy) {open("DESCRIPTION")},
                        ModularPanelOption("Responsables",Icons.Default.Person,"event-option:people",!busy && peopleLoaded) {open("PEOPLE")},
                        ModularPanelOption("Color",Icons.Default.RadioButtonChecked,"event-color",!busy) {open("COLOR")}
                    ),Modifier.testTag("event-compact"))
                    if(!draft.isNew) TextButton(enabled=!busy,onClick={deleting=true},modifier=Modifier.heightIn(min=48.dp).testTag("event-delete")) {Text("Eliminar")}
                }
                "TITLE" -> OutlinedTextField(title,{if(mode=="TITLE") title=it},enabled=!busy,label={Text("Título")},singleLine=true,
                    isError=TitleLimits.count(title.text)>100,modifier=modularPanelFieldModifier(autoFocus=true).testTag("event-title"))
                "DATE" -> DatePicker(dateState,modifier=Modifier.testTag("event-date-editor"),title=null)
                "TIME" -> {
                    Row(Modifier.heightIn(min=48.dp).toggleable(value=provisional.minute==null,enabled=!busy,role=Role.Checkbox,
                        onValueChange={provisional.minute=if(it) null else 720}).testTag("event-all-day"),verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=provisional.minute==null,enabled=!busy,onCheckedChange=null)
                        Text("Todo el día",Modifier.padding(end=8.dp))
                    }
                    if(provisional.minute!=null) TimeInput(timeState,modifier=Modifier.testTag("event-time"))
                }
                "DESCRIPTION" -> OutlinedTextField(description,{if(mode=="DESCRIPTION") description=it},enabled=!busy,label={Text("Descripción")},
                    isError=description.text.length>16000,minLines=3,modifier=modularPanelFieldModifier(autoFocus=true).testTag("event-description").then(if(descriptionFull) Modifier.fillMaxHeight() else Modifier))
                "PEOPLE" -> {
                    OutlinedTextField(peopleQuery,{peopleQuery=it},enabled=!busy,label={Text("Buscar personas")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("event-people-query"))
                    val filtered=people.filter {it.name.contains(peopleQuery.trim(),ignoreCase=true)}
                    if(people.isEmpty()) Text("No hay Personas. Puedes crearlas desde Personas en el menú.")
                    else if(filtered.isEmpty()) Text("No hay Personas que coincidan")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        filtered.sortedWith(compareBy<Person> {it.id !in provisional.people}.thenBy {it.name.lowercase()}.thenBy {it.id}).forEach {person->
                            ParticipantCard(person,if(person.id in provisional.people) "Seleccionada" else "Disponible",person.id in provisional.people,!busy,modifier=Modifier.widthIn(max=120.dp).testTag("person-choice:${person.id}")) {
                                provisional.people=if(person.id in provisional.people) provisional.people-person.id else provisional.people+person.id
                            }
                        }
                    }
                }
                "COLOR" -> FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    CalendarEventColor.entries.forEach {color->
                        FilterChip(selected=provisional.color==color.name,enabled=!busy,onClick={provisional.color=color.name},
                            label={Text(color.label)},leadingIcon={Box(Modifier.size(16.dp).background(Color(color.argb),CircleShape))},modifier=Modifier.testTag("event-color:${color.name}"))
                    }
                }
            }
        }}
    }
    if(deleting) AlertDialog(onDismissRequest={if(!busy) deleting=false},containerColor=Arachn0deColors.Surface,title={Text("Eliminar evento")},text={Text("¿Eliminar este Evento? Esta acción no se puede deshacer.")},
        confirmButton={TextButton(enabled=!busy,onClick={operation.submit("No se pudo eliminar el Evento.",{repo.delete(draft.id)},onClose)},modifier=Modifier.testTag("event-confirm-delete")) {Text("Eliminar")}},
        dismissButton={TextButton(enabled=!busy,onClick={deleting=false}) {Text("Cancelar")}})
    if(confirmDiscard) AlertDialog(onDismissRequest={confirmDiscard=false},containerColor=Arachn0deColors.Surface,
        title={Text("¿Descartar cambios del Evento?")},text={Text("Se descartarán el borrador y las modificaciones provisionales. El Evento guardado se conserva.")},
        confirmButton={TextButton(enabled=!busy,onClick=onClose,modifier=Modifier.testTag("event-discard-confirm")) {Text("Descartar cambios")}},
        dismissButton={TextButton(onClick={confirmDiscard=false},modifier=Modifier.testTag("event-continue")) {Text("Seguir editando")}})
    OperationErrorDialog(operation)
}


