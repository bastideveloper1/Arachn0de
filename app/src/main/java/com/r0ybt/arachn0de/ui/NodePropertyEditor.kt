package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.data.repository.TagRepository
import com.r0ybt.arachn0de.data.local.TechnologyEntity

internal fun EditorDraft.propertyValues(field:NodeProperty):List<String> = when(field) {
    NodeProperty.TITLE->listOf(title.trim())
    NodeProperty.DESCRIPTION->listOf(description.trim())
    NodeProperty.DATES->listOf(activeStart?.toString().orEmpty(),activeDue?.toString().orEmpty())
    NodeProperty.PRIORITY->listOf(priority.name)
    NodeProperty.PAYMENT->obligation().let {listOf(it?.amountMinor?.toString().orEmpty(),it?.currencyCode.orEmpty())}
    NodeProperty.PEOPLE->responsibleIds.sorted()
    NodeProperty.TAGS->tagIds.sorted()
    NodeProperty.TECHNOLOGIES->technologyIds
}

/** The same panel, draft and specialized fields used elsewhere, with one property at a time. */
@Composable internal fun NodePropertyEditor(draft:EditorDraft,field:NodeProperty,enabled:Boolean,maxHeight:Dp,
    people:List<Person>,tags:TagState,tagRepository:TagRepository,catalog:List<TechnologyEntity>,
    onCancel:()->Unit,onConfirm:()->Unit) {
    val available=enabled && !draft.attachmentBusy
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    fun cancel() {focus.clearFocus();keyboard?.hide();onCancel()}
    BackHandler {if(available) cancel()}
    if(field==NodeProperty.TITLE) {
        CorePropertyEditor("title",draft.title,{draft.title=it},available,maxHeight,::cancel,onConfirm,TitleLimits.NODE)
        return
    }
    val picker=rememberSaveable(draft.attachmentDraftId,saver=TaskDatePickerDraft.Saver) {TaskDatePickerDraft()}
    val requestFocus=remember(draft.attachmentDraftId) {FocusRequester()}
    LaunchedEffect(draft.attachmentsLoaded,field) {
        if(field==NodeProperty.DESCRIPTION && draft.attachmentsLoaded) keyboard?.show()
    }
    var confirmRemoval by rememberSaveable(draft.attachmentDraftId) {mutableStateOf(false)}
    val valid=when(field) {
        NodeProperty.DESCRIPTION->draft.attachmentsLoaded
        NodeProperty.DATES->(!draft.startEnabled || draft.startAt!=null) && (!draft.dueEnabled || draft.dueAt!=null) &&
            (draft.activeStart==null || draft.activeDue==null || draft.activeDue!!>=draft.activeStart!!)
        NodeProperty.PAYMENT->runCatching {draft.obligation()}.isSuccess
        else->true
    }
    ModularPanelSurface(Modifier.fillMaxWidth().heightIn(max=maxHeight).testTag("node-property-editor:${field.name}")) {
        ModularPanelLayout(header={},headerDivider=false,outlinedBody=false,compact=true,
            fieldOwnsScroll=field in setOf(NodeProperty.PEOPLE,NodeProperty.TAGS,NodeProperty.TECHNOLOGIES),
            actions={ModularPanelActions(cancel={
                PrimaryControlButton(enabled=available,onClick={cancel()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Cancelar")}
            },confirm={PrimaryControlButton(enabled=available && valid,onClick={
                if(field==NodeProperty.PAYMENT && draft.hadObligation && !draft.financialEnabled && !draft.financialRemovalConfirmed) confirmRemoval=true
                else {focus.clearFocus();keyboard?.hide();onConfirm()}
            },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Confirmar")}})}) {
            when(field) {
                NodeProperty.DESCRIPTION->AttachmentDescriptionEditor(draft,available,focusRequester=requestFocus,maxLines=8)
                NodeProperty.DATES->TaskDatesEditor(draft,available,picker,progressive=true)
                NodeProperty.PAYMENT->ObligationFields(draft,available)
                NodeProperty.PRIORITY->{
                    val prioritized=draft.priority==Priority.HIGH
                    FilterChip(selected=prioritized,onClick={draft.priority=if(prioritized) Priority.NONE else Priority.HIGH},enabled=available,
                        label={Text(if(prioritized) "Prioritario" else "Común")})
                }
                NodeProperty.PEOPLE->ResponsiblePickerBody(draft.creationId,people,draft.responsibleIds,!available) {draft.responsibleIds=it}
                NodeProperty.TAGS->{
                    TextButton(enabled=available,onClick={draft.tagIds=emptyList()}) {Text("Quitar etiqueta")}
                    TagPickerBody(tags.tags,draft.tagIds.toSet(),{draft.tagIds=it.toList()},repository=tagRepository,enabled=available)
                }
                NodeProperty.TECHNOLOGIES->TechnologyPickerBody(catalog,draft.technologyIds,available) {draft.technologyIds=it}
                NodeProperty.TITLE->Unit
            }
        }
    }
    if(confirmRemoval) RemoveObligationDialog(!available,false,onDismiss={confirmRemoval=false},onConfirm={
        draft.financialRemovalConfirmed=true;confirmRemoval=false;onConfirm()
    })
}
