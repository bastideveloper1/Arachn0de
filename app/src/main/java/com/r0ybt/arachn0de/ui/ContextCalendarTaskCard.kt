package com.r0ybt.arachn0de.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.r0ybt.arachn0de.domain.model.*

/** Dashboard results use NodeCard itself; actions enter the original task's existing editor/context. */
@Composable internal fun ContextCalendarTaskCard(node:Node,path:String,tags:List<Tag>,now:Long,people:List<Person> = emptyList(),paymentListState:androidx.compose.foundation.lazy.LazyListState?=null,onAction:(Node,String?)->Unit) {
    fun action(name:String?) {onAction(node,name)}
    val app=androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication
    val scope=androidx.compose.runtime.rememberCoroutineScope()
    val actions=androidx.compose.runtime.remember(app,scope) {app?.let {com.r0ybt.arachn0de.ui.state.NodeActions(it.nodeRepository,scope)}}
    if(actions!=null) OperationErrorDialog(actions.operation)
    NodeCard(node=node,progress=null,hasChildren=node.hasChildren,canToggleComplete=node.isCompletable,
        onOpen={action(null)},onEdit={action("edit")},onDelete={action("delete")},
        onContent=if(node.workState==null && !node.sprintMode) ({action("content")}) else null,
        canMoveUp=false,canMoveDown=false,onReorder={_,_->},onToggleComplete={if(actions!=null)actions.cycleTaskStatus(node.id) else action("toggle")},
        paymentListState=paymentListState,
        onRecurrence={action("recurrence")},onMove={action("move")},onResponsible={action("people")},onHistory={action("history")},
        onCopy={descendants->action(if(descendants) "copy-descendants" else "copy")},
        onMakeLayer={action("layer")},onConvert={done->done();action("convert")},
        onAdvanceWorkState=node.workState?.let {{action("advance")}},
        onChangeWorkState={action("state")},tags=tags,now=now,responsiblePeople=people,
        footer={Text(path,Modifier.testTag("context-calendar-location:${node.id}"),style=MaterialTheme.typography.labelSmall,color=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextSecondary,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)})
}
