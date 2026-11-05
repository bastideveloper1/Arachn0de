package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.bank.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val LocalBankTags=staticCompositionLocalOf {com.r0ybt.arachn0de.domain.model.TagState()}
internal val LocalBankRootIds=staticCompositionLocalOf<Set<String>> {emptySet()}
internal val LocalBankPaid=staticCompositionLocalOf<Map<String,Long>> {emptyMap()}

internal fun pesos(n:Long)="$"+java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CL")).format(n)
private fun bankKind(k:String)=when(k) {"INCOME"->"Ingresar dinero";"EXPENSE"->"Retirar dinero";"TRANSFER"->"Transferir dinero";"PAYMENT"->"Pagar";else->"Pago directo"}

@Composable internal fun BankChoice(label:String,value:String?,options:List<Pair<String,String>>,enabled:Boolean=true,onChoose:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {TextButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("$label: ${options.find {it.first==value}?.second ?: "Seleccionar"}")}
        DropdownMenu(expanded,{expanded=false},modifier=Modifier.heightIn(max=280.dp)) {options.forEach {(id,name)->DropdownMenuItem(text={Text(name)},enabled=enabled,onClick={onChoose(id);expanded=false})}}
    }
}

@Composable internal fun BankPanel(title:String,onDismiss:()->Unit,onConfirm:()->Unit,enabled:Boolean=true,confirmLabel:String="Guardar",compactContent:Boolean=false,content:@Composable ()->Unit) {
    ModularPanelDialog(onDismissRequest=onDismiss,title={Text(title)},text={CompositionLocalProvider(LocalContentColor provides Arachn0deColors.TextPrimary) {Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(if(compactContent) 4.dp else 8.dp)) {content()}}},
        compact=compactContent,
        confirmButton={PrimaryControlButton(onClick=onConfirm,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(confirmLabel)}},dismissButton={PrimaryControlButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Cancelar")}})
}

@Composable internal fun BankWorkspace(project:Project,db:Arachn0deDatabase,onBack:()->Unit,initialAccountId:String?=null,onOriginal:(String,String)->Unit) {
    val app=androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication
    val repo=remember(db,app) {if(app==null)BankRepository(db) else BankRepository(db,nodeRepository=app.nodeRepository)};val scope=rememberCoroutineScope()
    var state by remember {mutableStateOf<BankState?>(null)}
    var accountId by rememberSaveable(project.id) {mutableStateOf(initialAccountId)}
    val nameSearch=rememberInlineSearch("bank:${project.id}:${accountId.orEmpty()}")
    val originalSearchListState=androidx.compose.foundation.lazy.rememberLazyListState()
    val filteredSearchListState=androidx.compose.foundation.lazy.rememberLazyListState()

    var bottomContext by remember {mutableStateOf<CoreBottomContext?>(null)}
    var form by remember {mutableStateOf<String?>(null)}
    var detailId by remember {mutableStateOf<String?>(null)}
    var correction by remember {mutableStateOf<BankOperation?>(null)}
    var deletingAccount by remember {mutableStateOf<BankAccountDeletion?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    fun act(work:suspend ()->Unit) {if(!busy) {busy=true;scope.launch {try {work()} catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(e:Exception) {error=bankMessage(e.message ?: "No se pudo completar la operación.")} finally {busy=false}}}}
    LaunchedEffect(db) {db.invalidationTracker.createFlow("bank_roots","bank_accounts","bank_operations","bank_allocations","nodes","projects","persons","node_person","recurrence_occurrences").collect {state=repo.state()}}
    BackHandler(enabled=!nameSearch.open) {if(bottomContext!=null)bottomContext=null else if(accountId!=null)accountId=null else onBack()}
    val s=state
    val account=s?.accounts?.find {it.id==accountId}
    val node=s?.nodes?.find {it.id==accountId}
    Surface(Modifier.fillMaxSize(),color=Arachn0deColors.Background,contentColor=Arachn0deColors.TextPrimary) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            HeaderBar(onMenuClick={},title=node?.title ?: project.name,compactLock=true,showTitle=false,
                onBack={if(accountId!=null)accountId=null else onBack()})
            Text(node?.title ?: project.name,Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.titleLarge)
            if(account!=null && s!=null) {
                Text(pesos(s.balance(account.id)),Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text(s.people.find {it.id==account.personId}?.name.orEmpty(),Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.labelLarge)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=if(nameSearch.active)filteredSearchListState else originalSearchListState,contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                if(s==null) item {Text("Cargando…")}
                if(s!=null) {
                    val hierarchyIssues=BankHierarchy.issues(project.id,s.accounts,s.nodes)
                    if(hierarchyIssues.isNotEmpty()) item {
                        Text("Hay registros de Banca con una jerarquía incompatible. Se conservan sin mover ni eliminar.")
                        hierarchyIssues.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
                        Text("Revisa estos registros antes de autorizar una corrección de su ubicación.")
                    }
                }
                if(s==null) {}
                else if(account==null) {
                    items(s.accounts.filter {it.rootId==project.id && (!nameSearch.active || nameMatches(s.nodes.find {n->n.id==it.id}?.title.orEmpty(),nameSearch.query))},key={it.id}) {a->
                        val n=s.nodes.find {it.id==a.id}
                        if(n!=null) BankAccountCard(n.title,s.people.find {it.id==a.personId}?.name.orEmpty(),s.balance(a.id),a.id) {accountId=a.id;bottomContext=null}
                    }
                }
                if(s!=null) items(s.history(project.id,account?.id).filter {!nameSearch.active || nameMatches(it.concept,nameSearch.query)}.sortedWith(compareByDescending<BankOperation> {it.day}.thenByDescending {it.createdAt}),key={it.id}) {o->
                    val incoming=account!=null && o.destinationAccountId==account.id;val outgoing=account!=null && o.sourceAccountId==account.id
                    val sign=if(incoming)"+" else if(outgoing)"−" else ""
                    Surface(color=Arachn0deColors.Surface,contentColor=Arachn0deColors.TextPrimary,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth().clickable {detailId=o.id}.heightIn(min=48.dp)) {
                        Column(Modifier.padding(horizontal=12.dp,vertical=6.dp)) {
                            Row {Text(o.concept,Modifier.weight(1f));Text(sign+pesos(o.amount),color=if(o.voided)Arachn0deColors.TextSecondary else if(incoming)Arachn0deColors.Completed else if(outgoing)Arachn0deColors.Destructive else Arachn0deColors.TextPrimary,fontWeight=FontWeight.Bold)}
                            Text("${bankKind(o.kind)} · ${LocalDate.ofEpochDay(o.day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"+if(o.voided)" · Anulada" else "",style=MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            ContextualBottomPanel {
            CoreBottomModule(context=bottomContext,onContext={next->
                if(!busy && s!=null) {
                    if(next==CoreBottomContext.Content) {bottomContext=null;form="edit"}
                    else if(next==CoreBottomContext.Creation && account==null) {bottomContext=null;form="account"}
                    else if(next==CoreBottomContext.Options && account==null) {bottomContext=null;form="DIRECT";correction=null}
                    else bottomContext=next
                }
            },maxHeight=320.dp,contextActions={
                if(account!=null)action("Eliminar cuenta",androidx.compose.material.icons.Icons.Default.Delete,{
                    bottomContext=null;act {deletingAccount=repo.previewAccountDeletion(account.id)}
                },enabled=!busy,destructive=true)
            },habitualLabel=if(account==null)"Cuenta" else "Operar",contentLabel=if(account==null)"Editar" else "Contenido",contentActions=emptyList(),
                optionsLabel=if(account==null)"Pagar" else "Opciones",visibleContexts=CoreBottomContext.entries,creationActions={
                    Column {
                        listOf("INCOME","EXPENSE","PAYMENT").forEach {kind->
                            TextButton(onClick={form=kind;correction=null;bottomContext=null},enabled=!busy,
                                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(bankKind(kind))}
                        }
                    }
                })
            }
        }
    }

    deletingAccount?.let {confirmation->
        ModularPanelDialog(onDismissRequest={if(!busy)deletingAccount=null},title={Text("¿Eliminar cuenta?")},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(confirmation.original.nodes.find {it.id==confirmation.accountId}?.title.orEmpty())
                Text("Se eliminarán esta cuenta y ${confirmation.operationIds.size} movimientos, incluidos sus abonos y transferencias vinculadas. Los saldos y pagos pendientes se recalcularán; las tareas y personas se conservarán. No se mueve dinero real. Esta acción no tiene Deshacer.")
                if(confirmation.removesSavings)Text("También se eliminarán la configuración, créditos, fondos contables y premios de Ahorro asociados a esta cuenta.")
                error?.let {Text(it,color=Arachn0deColors.Destructive)}
            }
        },confirmButton={DestructiveAction("Eliminar cuenta",{act {repo.deleteAccount(confirmation);deletingAccount=null;accountId=null;bottomContext=null}},enabled=!busy)},
            dismissButton={PrimaryControlButton(onClick={deletingAccount=null},enabled=!busy) {Text("Cancelar")}})
    }
    if(s!=null && (form=="account" || form=="edit")) {
        var title by remember(form,accountId) {mutableStateOf(if(form=="edit")node?.title ?: project.name else "")}
        var description by remember(form,accountId) {mutableStateOf(if(form=="edit")node?.description ?: project.description else "")}
        var owner by remember(form,accountId) {mutableStateOf(account?.personId)}
        BankPanel(if(form=="account")"Nueva cuenta" else if(accountId==null)"Editar" else "Contenido",{if(!busy)form=null},{act {
            if(form=="account")repo.createAccount(project.id,title,requireNotNull(owner) {"Selecciona una persona."})
            else if(accountId==null)check(ProjectRepository(db.projectDao(),db).updateProject(project.id,title,description))
            else check(NodeRepository(db).updateNode(requireNotNull(accountId),title,description))
            form=null
        }},!busy && title.isNotBlank() && (form!="account" || owner!=null)) {
            OutlinedTextField(title,{title=it},label={Text("Nombre")},modifier=Modifier.fillMaxWidth())
            if(form=="account")BankChoice("Responsable",owner,s.people.map {it.id to it.name}) {owner=it}
            else OutlinedTextField(description,{description=it},label={Text("Descripción")},modifier=Modifier.fillMaxWidth())
        }
    }
    if(s!=null && (account!=null || form=="DIRECT") && form in setOf("INCOME","EXPENSE","PAYMENT","DIRECT")) {
        val tags by remember(db) {com.r0ybt.arachn0de.data.repository.TagRepository(db).observe()}.collectAsState(initial=com.r0ybt.arachn0de.domain.model.TagState())
        CompositionLocalProvider(LocalBankTags provides tags) {
            BankOperationEditor(s,repo,account,requireNotNull(form),correction,busy,rootId=project.id,
                onDismiss={if(!busy) {form=null;correction=null}},onError={error=it},onConfirm={preview,parts->act {
                    if(preview.request.kind in setOf("PAYMENT","DIRECT"))repo.confirmPayment(preview,parts) else repo.confirm(preview,parts)
                    form=null;correction=null
                }})
        }
    }
    val detail=s?.operations?.find {it.id==detailId}
    if(detail!=null && s!=null) {
        var confirmVoid by remember(detail.id) {mutableStateOf(false)}
        ModularPanelDialog(onDismissRequest={if(!busy)detailId=null},title={Text(if(detail.voided)"Operación anulada" else detail.concept,color=Arachn0deColors.TextPrimary)},text={CompositionLocalProvider(LocalContentColor provides Arachn0deColors.TextPrimary) {Column {
            Text("${bankKind(detail.kind)} · ${pesos(detail.amount)}")
            Text(LocalDate.ofEpochDay(detail.day).toString())
            detail.sourceAccountId?.let {id->Text("Desde: ${s.nodes.find {it.id==id}?.title}")}
            detail.destinationAccountId?.let {id->Text("Hacia: ${s.nodes.find {it.id==id}?.title}")}
            detail.correctsId?.let {Text("Corrige operación: $it")}
            s.allocations.filter {it.operationId==detail.id}.forEach {a->
                Text("Quién paga: ${a.sourcePersonName ?: "No identificado"} · Pago de: ${a.personName ?: "Sin responsable"}")
                TextButton(onClick={s.nodes.find {it.id==a.nodeId}?.let {n->n.projectId?.let {onOriginal(it,n.id)}}}) {Text("${a.title}: ${pesos(a.amount)}")}
            }
            if(!detail.voided) {
                if(confirmVoid)Text("¿Anular esta operación? Se conservará el historial.")
                TextButton(onClick={if(!confirmVoid)confirmVoid=true else act {repo.annul(detail.id);detailId=null}},enabled=!busy) {Text(if(confirmVoid)"Confirmar anulación" else "Anular")}
                if(detail.kind!="TRANSFER") TextButton(onClick={correction=detail;accountId=detail.contextAccountId.takeUnless {detail.kind=="DIRECT"};form=detail.kind;detailId=null},enabled=!busy) {Text("Corregir")}
            }
        }}},confirmButton={TextButton(onClick={detailId=null}) {Text("Cerrar")}})
    }
    error?.let {message->AlertDialog(onDismissRequest={error=null},title={Text("Banca")},text={Text(message)},confirmButton={TextButton(onClick={error=null}) {Text("Aceptar")}})}
}

@Composable private fun BankOperationEditor(s:BankState,repo:BankRepository,account:BankAccount?,initialKind:String,old:BankOperation?,busy:Boolean,
    onDismiss:()->Unit,onError:(String)->Unit,onConfirm:(BankPreview,Map<String,Long>)->Unit,rootId:String) {
    key(old?.id,initialKind,account?.id) {
        if(initialKind in setOf("PAYMENT","DIRECT")) BankPaymentEditor(s,repo,account,initialKind,old,busy,onDismiss,onError,onConfirm,rootId=rootId)
        else BankCashEditor(s,repo,requireNotNull(account),initialKind,old,busy,onDismiss,onError,onConfirm)
    }
}

@Composable private fun BankCashEditor(s:BankState,repo:BankRepository,account:BankAccount,kind:String,old:BankOperation?,busy:Boolean,
    onDismiss:()->Unit,onError:(String)->Unit,onConfirm:(BankPreview,Map<String,Long>)->Unit) {
    val scope=rememberCoroutineScope()
    val requestId=rememberSaveable {java.util.UUID.randomUUID().toString()}
    var accountId by rememberSaveable {mutableStateOf(old?.sourceAccountId ?: old?.destinationAccountId ?: account.id)}
    var amount by rememberSaveable {mutableStateOf(old?.amount?.toString().orEmpty())}
    var name by rememberSaveable {mutableStateOf(old?.concept.orEmpty())}
    var loading by remember {mutableStateOf(false)}
    val effective=runCatching {repo.effectiveState(s,old?.id)}
    val amountValue=bankAmount(amount)
    val balance=effective.getOrNull()?.balance(accountId)
    val accounts=s.accounts.filter {it.rootId==account.rootId}.mapNotNull {a->s.nodes.find {it.id==a.id}?.let {a.id to it.title}}
    val valid=effective.isSuccess && amountValue!=null && accounts.any {it.first==accountId} && name.length<=1000 &&
        (if(kind=="EXPENSE")amountValue<=balance!! else amountValue<=Long.MAX_VALUE-balance!!)
    BankPanel(if(old==null)bankKind(kind) else "Corregir operación",onDismiss,{
        if(!loading) {loading=true;scope.launch {try {
            val request=BankRequest(id=requestId,rootId=account.rootId,contextAccountId=account.id,kind=kind,amount=requireNotNull(amountValue),
                day=old?.day ?: LocalDate.now().toEpochDay(),concept=name,
                sourceAccountId=accountId.takeIf {kind=="EXPENSE"},destinationAccountId=accountId.takeIf {kind=="INCOME"},correctsId=old?.id)
            onConfirm(repo.preview(request),emptyMap())
        } catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(e:Exception) {onError(bankMessage(e.message ?: "Revisa los datos."))} finally {loading=false}}}
    },valid && !busy && !loading) {
        BankChoice("Cuenta",accountId,accounts,enabled=!busy && !loading) {accountId=it}
        if(kind=="EXPENSE")Text("Saldo disponible: ${pesos(balance ?: 0)}")
        BankAmountField(amount,{amount=it},"Cantidad",!busy && !loading)
        OutlinedTextField(name,{name=it},label={Text("Nombre (opcional)")},enabled=!busy && !loading,modifier=Modifier.fillMaxWidth())
        if(amount.isNotBlank() && amountValue==null)Text("Introduce una cantidad válida.",color=Arachn0deColors.Destructive)
        else if(kind=="EXPENSE" && amountValue!=null && balance!=null && amountValue>balance)Text("Saldo insuficiente.",color=Arachn0deColors.Destructive)
        effective.exceptionOrNull()?.message?.let {Text(bankMessage(it),color=Arachn0deColors.Destructive)}
    }
}


private fun bankMessage(message:String)=message.replace("Importe","Cantidad").replace("importe","cantidad").replace("concepto","nombre").replace("Concepto","Nombre")

/** Contextual content of the existing card, with no task or technology controls. */
@Composable internal fun BankAccountCard(name:String,person:String,balance:Long,id:String,onOpen:()->Unit) {
    GeneralCard(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("bank-account:$id"),
        shape=RoundedCornerShape(8.dp),border=BorderStroke(1.dp,Arachn0deColors.Outline),bashEntry=true,
        colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface),onClick=onOpen) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(if(person.isBlank()) name else "$name ($person)",color=Arachn0deColors.TextPrimary,style=MaterialTheme.typography.titleMedium,
                    minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.testTag("bank-account-name:$id"))
                Text(pesos(balance),color=Arachn0deColors.TextPrimary,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier)
            }
            FavoriteToggle("node:$id",compactSquare=true)
        }
    }
}
