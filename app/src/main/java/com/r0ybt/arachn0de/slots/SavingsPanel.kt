package com.r0ybt.arachn0de.slots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.bank.bankAmount
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.domain.model.Money
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.state.*
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import java.math.BigInteger
import java.math.BigDecimal
import java.util.UUID

internal fun savingsCash(units:Long)=Money.format(BigInteger.valueOf(units)*BigInteger.valueOf(1000),"CLP",java.util.Locale.forLanguageTag("es-CL"))
internal fun savingsPercent(f:SavingsFraction):String {
    val percent=f*SavingsFraction(100)
    return if(percent.denominator==BigInteger.ONE)"${percent.numerator} %" else "$percent % (≈ ${"%.4f".format(java.util.Locale.ROOT,percent.approximate())} %)"
}
private fun bps(text:String)=runCatching {BigDecimal(text.trim().replace(',','.')).multiply(BigDecimal(100)).intValueExact()}.getOrNull()
private fun percent(n:Int)=BigDecimal(n).movePointLeft(2).stripTrailingZeros().toPlainString()
private fun probabilityTickets(text:String)=runCatching {BigDecimal(text.trim().replace(',','.')).multiply(BigDecimal(500)).intValueExact()}.getOrNull()
private fun probabilityText(ticks:Int)=BigDecimal(ticks).divide(BigDecimal(500)).stripTrailingZeros().toPlainString()
private fun units(text:String)=bankAmount(text)?.takeIf {it>0 && it%1000==0L}?.div(1000)

@Composable internal fun SavingsPanel(view:SavingsView,repo:SavingsRepository,db:Arachn0deDatabase,initialPage:String="home",
    onDismiss:()->Unit,onOpenBank:(String,String?)->Unit,onBought:()->Unit) {
    val state=view.state;val book=remember(state) {state.book()};val current=state.active
    val scope=rememberCoroutineScope();val op=remember(repo,scope) {OperationState(scope)}
    var page by rememberSaveable {mutableStateOf(if(current==null && initialPage!="buy")"config" else initialPage)}
    var request by rememberSaveable(page) {mutableStateOf(UUID.randomUUID().toString())}
    var selectedRoot by rememberSaveable {mutableStateOf(current?.root.orEmpty())}
    var selectedAccount by rememberSaveable {mutableStateOf(current?.account.orEmpty())}
    val price="1000"
    var saving by rememberSaveable {mutableStateOf(percent(current?.savingsBps ?: 7000))}
    var target by rememberSaveable {mutableStateOf(percent(current?.takeIf {it.table.symbolCount==8 && it.table.creditsPerPack==5}?.targetBps ?: 3000))}
    var prizes by rememberSaveable {mutableStateOf((current?.table?.takeIf {it.symbolCount==8 && it.creditsPerPack==5} ?: SavingsPayTable()).prizeUnits.map {savingsMoney(it).toString()})}
    var probabilities by rememberSaveable {mutableStateOf((current?.table?.takeIf {it.symbolCount==8 && it.creditsPerPack==5} ?: SavingsPayTable()).winningTickets.map(::probabilityText))}
    var deposit by rememberSaveable {mutableStateOf("1000")}
    var reserve by rememberSaveable {mutableStateOf(savingsMoney(book.deficit.coerceAtLeast(1)).toString())}
    var confirmation by rememberSaveable {mutableStateOf(false)}
    var accountReturn by rememberSaveable {mutableStateOf("config")}
    var query by rememberSaveable {mutableStateOf("")}
    var newBank by rememberSaveable(stateSaver=EditorDraft.Saver) {mutableStateOf<EditorDraft?>(null)}
    val problem=book.reconciliation(view.bank)
    fun submit(work:suspend ()->Unit,after:()->Unit={page="home";confirmation=false}) {
        op.submit("No se pudo confirmar. No se han aplicado cambios parciales.",{work();true},after)
    }
    if(page=="account") {
        val roots=view.bank.roots.filter {r->query.isBlank() || view.bank.projects.find {it.id==r.id}?.name?.contains(query,true)==true || view.bank.accounts.any {it.rootId==r.id && view.bank.nodes.find {n->n.id==it.id}?.title?.contains(query,true)==true}}
        ModularPanelDialog(onDismissRequest={if(!op.busy)page=accountReturn},title={Text("Banco y cuenta")},fieldOwnsScroll=true,confirmButton={},actionsContent={
            Column {
                OutlinedTextField(query,{query=it},singleLine=true,label={Text("Buscar Banco o cuenta")},modifier=Modifier.fillMaxWidth().testTag("savings-account-search"))
                Row {TextButton(onClick={page=accountReturn}) {Text("Cancelar")};TextButton(onClick={newBank=EditorDraft(null,null,"","",creationId=UUID.randomUUID().toString()).apply {bankType=true}}) {Text("Crear Banco")}}
            }
        },text={LazyColumn(Modifier.heightIn(max=320.dp).testTag("savings-account-list")) {
            items(roots,key={it.id}) {r->
                Text(view.bank.projects.find {it.id==r.id}?.name.orEmpty(),color=Arachn0deColors.TextPrimary)
                view.bank.accounts.filter {it.rootId==r.id}.forEach {a->TextButton(onClick={selectedRoot=r.id;selectedAccount=a.id;page=accountReturn},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(view.bank.nodes.find {it.id==a.id}?.title.orEmpty()+" · "+view.bank.people.find {it.id==a.personId}?.name.orEmpty())}}
                TextButton(onClick={onDismiss();onOpenBank(r.id,null)}) {Text("Abrir Banco / crear cuenta")}
            }
        }})
    } else if(page=="config") {
        val parsed=runCatching {
            SavingsConfig(request,selectedRoot,selectedAccount,SavingsPayTable(requireNotNull(units(price)),prizes.map {requireNotNull(units(it))},probabilities.map {requireNotNull(probabilityTickets(it))}),requireNotNull(bps(saving)),requireNotNull(bps(target))).also {it.validate()}
        }
        val preview=runCatching {SavingsPayTable(requireNotNull(units(price)),prizes.map {requireNotNull(units(it))},probabilities.map {requireNotNull(probabilityTickets(it))})}.getOrNull()
        BankPanel("Configurar Pago",onDismiss,{parsed.getOrNull()?.let {c->submit({repo.configure(c)})}},parsed.isSuccess && !op.busy,"Guardar") {
            val account=view.bank.nodes.find {it.id==selectedAccount}
            TextButton(enabled=!op.busy && book.credits==0L && book.funds==0L,onClick={accountReturn="config";page="account"},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(account?.title ?: "Seleccionar Banco y cuenta")}
            Text("$1.000 CLP = 5 tiradas · $200 por giro")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SavingsField(saving,{saving=it},"Ahorro %",Modifier.weight(1f));SavingsField(target,{target=it},"RTP objetivo %",Modifier.weight(1f))
            }
            Text("Fondo: ${bps(saving)?.takeIf {it in 0..10000}?.let {percent(10000-it)} ?: "—"} % · RTP ≤ fondo",style=MaterialTheme.typography.bodySmall)
            (0..7).forEach {i->
                Text(savingsSymbolNames[i],style=MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    SavingsField(prizes[i],{v->prizes=prizes.mapIndexed {n,x->if(n==i)v else x}},"Premio CLP",Modifier.weight(1f))
                    SavingsField(probabilities[i],{v->probabilities=probabilities.mapIndexed {n,x->if(n==i)v else x}},"Prob. %",Modifier.weight(1f))
                }
            }
            preview?.let {t->runCatching {"RTP calculado: ${savingsPercent(t.rtp)} · Ventaja: ${savingsPercent(t.houseEdge)}"}.getOrNull()?.let {Text(it,style=MaterialTheme.typography.bodySmall)}}
            if(parsed.isFailure)Text(parsed.exceptionOrNull()?.message?.takeUnless {it=="Failed requirement." || it=="Required value was null."} ?: "Selecciona una cuenta e introduce CLP positivos en múltiplos de $1.000 y porcentajes válidos.",color=Arachn0deColors.Destructive,style=MaterialTheme.typography.bodySmall)
            Text("El saldo de la cuenta seleccionada queda disponible para premios en múltiplos de $1.000, sin registrar otro ingreso. Las compras existentes conservan su precio y tabla. Probabilidades por combinación; resultados perdedores equiprobables.",style=MaterialTheme.typography.bodySmall)
        }
    } else if(page=="home") {
        JunglePanel("Opciones de Pago",onDismiss,onDismiss) {
            Text(view.bank.nodes.find {it.id==current?.account}?.title ?: "Seleccionar caja",color=JungleGold)
            Text("Disponible para premios: ${savingsCash(book.free)}",color=JungleGold,style=MaterialTheme.typography.bodySmall)
            Text("Créditos: ${book.credits} · Premios: ${book.pending.size}",color=JungleGold,style=MaterialTheme.typography.bodySmall)
            Row {TextButton(onClick={page="config"}) {Text("Configurar",color=JungleGold)};TextButton(onClick={page="prizes"}) {Text("Premios",color=JungleGold)}}
            Row {TextButton(onClick={page="history"}) {Text("Historial",color=JungleGold)};TextButton(onClick={page="stats"}) {Text("Estadísticas",color=JungleGold)}}
            problem?.let {Text(it,color=JungleGold,style=MaterialTheme.typography.bodySmall)}
        }
    } else if(page=="win" || page=="prizes") {
        val prize=if(page=="win")book.pending.values.lastOrNull() else book.pending.values.firstOrNull()
        androidx.activity.compose.BackHandler(enabled=!op.busy) {onDismiss()}
        Box(Modifier.fillMaxWidth().testTag("slot-withdrawal-inline")) {
            Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp)) {
                if(prize==null)Text("Sin premios pendientes.",color=JungleGold)
                else Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Disponible: ${savingsCash(prize.units)}",Modifier.weight(1f),color=JungleGold,style=MaterialTheme.typography.bodyMedium)
                    PrimaryControlButton(onClick={
                        submit({repo.resolve(UUID.randomUUID().toString(),prize.id,true)},{if(book.pending.size==1)onDismiss() else page="prizes"})
                    },enabled=!op.busy && problem==null && state.animation==null,modifier=Modifier.heightIn(min=48.dp)) {Text("RETIRAR")}
                }
                problem?.let {Text(it,color=JungleGold,style=MaterialTheme.typography.bodySmall)}
            }
            if(page=="win")JungleConfetti(Modifier.matchParentSize())
        }
    } else if(page=="buy" || page=="topup") {
        val buying=page=="buy"
        val amount=bankAmount(if(buying)deposit else reserve)
        val count=if(buying && amount!=null)runCatching {savingsCreditsForDeposit(amount,SavingsPayTable())}.getOrNull() else null
        val account=view.bank.accounts.find {it.id==selectedAccount && it.rootId==selectedRoot}
        val valid=amount!=null && amount%1000==0L && (!buying || count!=null) && account!=null && problem==null
        JunglePanel(if(buying)"Depositar" else "Recargar reserva",{if(!op.busy)onDismiss()},{
            if(amount!=null && account!=null) {
                val id=request
                if(buying)submit({repo.depositAndBuy(id,amount,account.rootId,account.id)},{onBought();onDismiss()})
                else if(current!=null)submit({repo.topup(id,amount/1000,current.id)},{onDismiss()})
            }
        },"DEPOSITAR",enabled=!op.busy && valid,showClose=true,depositFrame=buying) {
            if(account==null)TextButton(onClick={accountReturn="buy";page="account"}) {Text("Seleccionar cuenta",color=JungleGold)}
            else {
                Text(view.bank.nodes.find {it.id==account.id}?.title.orEmpty(),color=JungleGold)
                Text("Saldo: ${com.r0ybt.arachn0de.ui.pesos(view.bank.balance(account.id))}",color=JungleGold,style=MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(if(buying)deposit else reserve,{if(buying)deposit=it else reserve=it},singleLine=true,label={Text("Depósito CLP")},modifier=Modifier.fillMaxWidth().testTag("slot-deposit-clp"),
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=JungleGold,unfocusedTextColor=JungleGold,focusedLabelColor=JungleGold,unfocusedLabelColor=JungleGold,focusedBorderColor=JungleGold,unfocusedBorderColor=JungleGold))
            if(buying && count!=null)Text("Recibirás $count tiradas",color=JungleGold)
            if(!buying)Text("Solo fondo de premios. No compra tiradas.",color=JungleGold,style=MaterialTheme.typography.bodySmall)
            problem?.let {Text(it,color=JungleGold,style=MaterialTheme.typography.bodySmall)}
        }
    } else {
        com.r0ybt.arachn0de.ui.ModularPanelDialog(onDismiss,title={Text(if(page=="history")"Historial" else "Estadísticas")},confirmButton={PrimaryControlButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Cerrar")}},text={
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(page=="history") {
                state.events.asReversed().forEach {e->Text("${java.time.Instant.ofEpochMilli(e.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()} · ${when(e.kind){"BUY"->"Compra ${e.count} tiradas";"TOPUP"->"Recarga de reserva";"ASSIGN"->"Saldo existente asignado";"SPIN"->"Tirada · "+e.result.joinToString(" / ") {savingsName(book.configs.getValue(e.config).table,it)};"KEEP"->"Premio conservado";else->"Premio retirado"}} · ${savingsCash(e.units)}",style=MaterialTheme.typography.bodySmall)}
            } else {
                Text("Créditos: ${book.credits} · Premio máximo: ${savingsCash(book.maximum)}")
                if(problem!=null) {Text(problem,color=Arachn0deColors.Destructive);TextButton(onClick={current?.let {onDismiss();onOpenBank(it.root,it.account)}}) {Text("Revisar Banco")}}
                if(book.deficit>0) {
                    Text("Fondo insuficiente",color=Arachn0deColors.Destructive)
                    Text("Fondo disponible: ${savingsCash(book.free)}\nDebes ingresar ${savingsCash(book.deficit)} para habilitar las tiradas.")
                    TextButton(enabled=problem==null,onClick={reserve=savingsMoney(book.deficit).toString();page="topup"}) {Text("Ingresar fondos")}
                }
                Row {TextButton(enabled=problem==null,onClick={page=if(current?.table?.let {it.symbolCount==7 || it.creditsPerPack!=5}==true)"config" else "buy"}) {Text("Comprar tiradas")};TextButton(onClick={page="prizes"}) {Text("Premios (${book.pending.size})")}}
                Row {TextButton(onClick={page="config"}) {Text("Configurar")};TextButton(onClick={page="history"}) {Text("Historial")}}
                Text("Ahorro",style=MaterialTheme.typography.titleSmall)
                Text("Compras: ${savingsCash(book.deposited)}\nProtegido: ${savingsCash(book.protected)}\nProtegido por compras: ${savingsCash(book.protected-book.kept)}\nFondo libre: ${savingsCash(book.free)}\nPremios reservados: ${savingsCash(book.reserved)}\nSaldo de cuenta asignado: ${savingsCash(book.assignedCapital)}\nRecargas de capital: ${savingsCash(book.topups)}\nRetirado físicamente: ${savingsCash(book.withdrawn)}\nSaldo de la máquina: ${savingsCash(book.funds)}",style=MaterialTheme.typography.bodySmall)
                Text("Juego",style=MaterialTheme.typography.titleSmall)
                Text("Compradas: ${book.purchased} · Usadas: ${book.spins} · Disponibles: ${book.credits}\nPremios: ${book.awardCount} · Retirados: ${book.withdrawnCount} · Pendientes: ${book.pending.size} · Conservados: ${book.keptCount} (${savingsCash(book.kept)})",style=MaterialTheme.typography.bodySmall)
                Text("Matemáticas",style=MaterialTheme.typography.titleSmall)
                current?.table?.let {t->Text("RTP teórico actual: ${savingsPercent(t.rtp)}\nVentaja: ${savingsPercent(t.houseEdge)}\nProbabilidad de premio: ${savingsPercent(t.winProbability)}\nFrecuencia esperada: ${t.expectedSpinsPerPrize} tiradas/premio\nPremio esperado: ${t.expectedPrizeUnits*SavingsFraction(1000)} CLP/tirada\nVarianza: ${t.varianceUnits*SavingsFraction(1000000)} CLP²\nDesviación ≈ ${"%.2f".format(kotlin.math.sqrt(t.varianceUnits.approximate())*1000)} CLP",style=MaterialTheme.typography.bodySmall)}
                Text(if(book.spins==0L)"Sin tiradas para calcular RTP observado." else "RTP observado: ${savingsPercent(SavingsFraction(savingsMoney(book.awarded),book.playedCostMinor))}\nFrecuencia real de premios: ${savingsPercent(SavingsFraction(book.awardCount,book.spins))}",style=MaterialTheme.typography.bodySmall)
                Text("El ahorro protegido no es rendimiento financiero. Las recargas son aportaciones propias.",style=MaterialTheme.typography.bodySmall)
            }
        }
        })
    }
    newBank?.let {draft->
        val projects=remember(db) {ProjectRepository(db.projectDao(),db)}
        val context=androidx.compose.ui.platform.LocalContext.current
        val attachments=(context.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.current?.value?.attachments
        val actions=remember(projects,scope,attachments) {ProjectActions(projects,scope,attachments)}
        ProjectDialog(draft,actions.operation.busy,{actions.discardDraft(draft) {newBank=null}},{name,description->actions.save(null,name,description,draft.creationId,draft) {newBank=null;onDismiss();onOpenBank(draft.creationId,null)}})
        OperationErrorDialog(actions.operation)
    }
    OperationErrorDialog(op)
}

@Composable private fun SavingsField(value:String,onChange:(String)->Unit,label:String,modifier:Modifier=Modifier) {
    OutlinedTextField(value,onChange,singleLine=true,label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=modifier.fillMaxWidth())
}
