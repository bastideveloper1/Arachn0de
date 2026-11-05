package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.bank.*
import com.r0ybt.arachn0de.data.local.toNode
import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable internal fun BankAmountField(value:String,onChange:(String)->Unit,label:String,enabled:Boolean,modifier:Modifier=Modifier) {
    OutlinedTextField(value,onChange,label={Text(label)},prefix={Text("$")},singleLine=true,enabled=enabled,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=modifier.fillMaxWidth())
}

/** One payment form shared by Banco, accounts and existing cards. */
@Composable internal fun BankPaymentEditor(s:BankState,repo:BankRepository,account:BankAccount?,initialKind:String,old:BankOperation?,busy:Boolean,
    onDismiss:()->Unit,onError:(String)->Unit,onConfirm:(BankPreview,Map<String,Long>)->Unit,
    rootId:String=account?.rootId ?: old?.rootId.orEmpty(),selectedNodeId:String?=null,chooseSource:Boolean=false,
    onCorrectAmount:(()->Unit)?=null,prefillPending:Boolean=false) {
    val scope=rememberCoroutineScope()
    val requestId=rememberSaveable {java.util.UUID.randomUUID().toString()}
    val newBankId=rememberSaveable {java.util.UUID.randomUUID().toString()}
    var bank by rememberSaveable {mutableStateOf(rootId.ifEmpty {s.roots.singleOrNull()?.id.orEmpty()})}
    val actualBank=bank.ifEmpty {if(s.roots.isEmpty())newBankId else ""}
    var source by rememberSaveable {mutableStateOf(old?.sourceAccountId ?: account?.id.takeIf {initialKind!="DIRECT"})}
    var target by rememberSaveable {mutableStateOf(selectedNodeId ?: old?.let {o->s.allocations.singleOrNull {it.operationId==o.id}?.nodeId})}
    val originalParts=old?.let {o->s.allocations.filter {it.operationId==o.id}}.orEmpty()
    val correctingSplit=originalParts.size>1
    var split by rememberSaveable {mutableStateOf(originalParts.associate {it.nodeId to it.amount.toString()})}
    var payer by rememberSaveable {mutableStateOf(old?.sourcePersonId)}
    var amount by rememberSaveable {mutableStateOf(old?.amount?.toString() ?:
        if(prefillPending)s.nodes.find {it.id==selectedNodeId && it.amountMinor!=null}?.let {s.remaining(it).coerceAtLeast(0).toString()}.orEmpty() else "")}
    var name by rememberSaveable {mutableStateOf(old?.concept.orEmpty())}
    var loading by remember {mutableStateOf(false)}
    var confirmation by remember {mutableStateOf<BankPreview?>(null)}
    val effectiveResult=runCatching {repo.effectiveState(s,old?.id)}
    val effective=effectiveResult.getOrNull() ?: s
    val debt=effective.nodes.find {it.id==target && it.amountMinor!=null}
    val person=if(correctingSplit)old?.obligationPersonId else debt?.let {effective.owners(it.id).singleOrNull()}
    val number=bankAmount(amount)
    val parts=if(correctingSplit)split.mapNotNull {(id,value)->bankAmount(value)?.let {id to it}}.toMap() else if(target!=null && number!=null)mapOf(target!! to number) else emptyMap()
    val selectedAccount=effective.accounts.find {it.id==source}
    val direct=source==null
    val valid=effectiveResult.isSuccess && name.length<=1000 && actualBank.isNotEmpty() &&
        (if(correctingSplit) number!=null && number>0 && parts.size==originalParts.size &&
            runCatching {parts.values.fold(0L,Math::addExact)}.getOrNull()==number &&
            (direct || selectedAccount?.let {effective.balance(it.id)>=number}==true) &&
            parts.all {(id,n)->effective.nodes.find {it.id==id}?.let {effective.eligible(it,person) && n<=effective.remaining(it)}==true}
        else validBankPayment(effective,actualBank,person,source,direct,number,parts))
    val available=!busy && !loading && confirmation==null
    BankPanel("Pagar",onDismiss,{
        if(available && valid) {loading=true;scope.launch {try {
            val request=BankRequest(id=requestId,rootId=actualBank,contextAccountId=old?.contextAccountId ?: source,
                kind=if(direct)"DIRECT" else "PAYMENT",amount=requireNotNull(number),
                day=old?.day ?: LocalDate.now().toEpochDay(),concept=name,sourceAccountId=source,
                sourcePersonId=if(direct)payer else null,personId=person,correctsId=old?.id,createRoot=s.roots.isEmpty())
            confirmation=repo.preview(request)
        } catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(e:Exception) {onError(e.message ?: "No se pudo preparar el pago.")} finally {loading=false}}}
    },valid && available,"Confirmar pago",compactContent=true) {
        if(rootId.isEmpty() && effective.roots.size>1) BankChoice("Banco",bank,effective.roots.map {r->r.id to effective.projects.single {it.id==r.id}.name},enabled=available) {id->bank=id;source=null}
        if(effective.roots.isEmpty())Text("Se creará Banco al confirmar, sin cuentas.",style=MaterialTheme.typography.bodySmall)
        if(chooseSource) BankChoice("Origen",source.orEmpty(),listOf("" to "DIRECTO")+
            effective.accounts.filter {it.rootId==actualBank}.map {a->a.id to effective.nodes.single {it.id==a.id}.title},enabled=available) {id->source=id.takeIf {it.isNotEmpty()}}
        if(direct) {
            Text("DIRECTO · sin descontar de cuentas",style=MaterialTheme.typography.bodySmall)
            if(effective.people.isNotEmpty()) BankChoice("Pagador",payer.orEmpty(),listOf("" to "No identificado")+effective.people.map {it.id to it.name},enabled=available) {payer=it.takeIf {it.isNotEmpty()}}
        } else {
            Text(effective.nodes.find {it.id==source}?.title ?: "Cuenta no disponible",style=MaterialTheme.typography.titleSmall)
            Text("Saldo disponible: ${pesos(selectedAccount?.let {effective.balance(it.id)} ?: 0)}",style=MaterialTheme.typography.bodySmall)
        }
        if(correctingSplit) {
            originalParts.forEach {a->
                Text(a.title)
                BankAmountField(split[a.nodeId].orEmpty(),{value->split=split+(a.nodeId to value)},"Asignado",available,Modifier.testTag("bank-assigned:${a.nodeId}"))
            }
        } else if(selectedNodeId==null) {
            Text("Pagos pendientes",style=MaterialTheme.typography.titleSmall)
            val candidates=effective.candidates(null)
            candidates.forEach {n->
                val eligible=effective.owners(n.id).size<=1
                BankDebtCard(effective,n,if(n.id==target)number else null,n.id==target,
                    if(eligible && available)({target=n.id}) else null) {
                    if(!eligible)Text("Pago compartido no disponible",style=MaterialTheme.typography.bodySmall)
                }
            }
            if(candidates.isEmpty())Text("Sin pagos pendientes.")
        }
        debt?.let {n->
            fun money(value:Long)=if(n.currencyCode=="CLP")pesos(value) else com.r0ybt.arachn0de.domain.model.Money.format(java.math.BigInteger.valueOf(value),n.currencyCode ?: "CLP",java.util.Locale.forLanguageTag("es-CL"))
            Text(n.title,style=MaterialTheme.typography.titleSmall)
            Text("Importe original: ${money(n.amountMinor ?: 0)}")
            Text("Total abonado: ${money(effective.paid(n.id))}")
            Text("Saldo pendiente: ${money(effective.remaining(n))}")
            if(n.currencyCode!="CLP")Text("Este importe histórico está en ${n.currencyCode}. Se conserva; solo se admiten nuevos pagos en CLP.")
            else if(effective.owners(n.id).size>1)Text("Pago compartido no disponible; sus asignaciones se conservan.")
            else if(n.isCompleted && effective.remaining(n)>0)Text("La tarjeta está Completada y tiene deuda histórica. Cambia su estado explícitamente antes de pagar.")
            if(number!=null && number>effective.remaining(n))Text("La cantidad supera el saldo pendiente.",color=Arachn0deColors.Destructive)
        }
        BankAmountField(amount,{amount=it},"Cantidad",available && (debt==null || debt.currencyCode=="CLP"),Modifier.testTag("bank-payment-amount"))
        if(amount.isNotBlank() && number==null)Text("Introduce una cantidad válida.",color=Arachn0deColors.Destructive)
        if(!direct && number!=null && number>(selectedAccount?.let {effective.balance(it.id)} ?: 0))Text("Saldo insuficiente.",color=Arachn0deColors.Destructive)
        OutlinedTextField(name,{name=it},label={Text("Nombre (opcional)")},enabled=available,modifier=Modifier.fillMaxWidth())
        onCorrectAmount?.let {correct->TextButton(onClick=correct,enabled=available) {Text("Corregir importe original")}}
        effectiveResult.exceptionOrNull()?.message?.let {Text(it,color=Arachn0deColors.Destructive)}
    }
    confirmation?.let {p->
        BankPanel("¿Confirmar pago de ${pesos(p.request.amount)}?",{if(!busy)confirmation=null},{if(!busy)onConfirm(p,parts)},!busy,"Confirmar") {
            p.effective.nodes.filter {it.id in parts}.forEach {n->BankDebtCard(p.effective,n,parts[n.id],true,null,showAssignedSummary=true)}
        }
    }
}

/** The existing task card, with its skin, title and selection controls; no task-editing controls. */
@Composable internal fun BankDebtCard(s:BankState,node:NodeEntity,assigned:Long?,selected:Boolean,onSelect:(()->Unit)?,showAssignedSummary:Boolean=false,extra:@Composable ()->Unit={}) {
    NodeCard(node=node.toNode(false),progress=null,hasChildren=false,canToggleComplete=false,
        onOpen={},onEdit={},onDelete={},canMoveUp=false,canMoveDown=false,onReorder={_,_->},onToggleComplete={},
        selecting=true,selected=selected,onSelect=onSelect,selectionOnly=true,compactPaymentSelection=true,showOptions=false,tags=LocalBankTags.current.forNode(node.id),
        modifier=Modifier.testTag("bank-debt:${node.id}"),footer={
            Text("Pendiente: ${pesos(s.remaining(node))}",style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary)
            if(assigned!=null && showAssignedSummary)Text("Asignado: ${pesos(assigned)}",style=MaterialTheme.typography.bodySmall)
            extra()
        })
}
