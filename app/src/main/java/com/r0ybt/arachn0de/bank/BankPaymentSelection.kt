package com.r0ybt.arachn0de.bank

import com.r0ybt.arachn0de.domain.model.Money
import java.util.Locale

internal fun defaultBankName(kind:String)=when(kind) {"INCOME"->"Ingreso";"EXPENSE"->"Retiro";"TRANSFER"->"Transferencia";else->"Pago"}
internal fun bankAmount(text:String):Long?=runCatching {Money.parse(text.trim().removePrefix("$").trim(),"CLP",Locale.forLanguageTag("es-CL"))}.getOrNull()

/** Pure immediate validation; transaction confirmation remains the final authority. */
internal fun validBankPayment(s:BankState,rootId:String,person:String?,account:String?,direct:Boolean,amount:Long?,parts:Map<String,Long>):Boolean {
    if(amount==null || amount<=0)return false
    if(!direct && (s.accounts.none {it.id==account && it.rootId==rootId} || s.balance(account!!) < amount))return false
    if(parts.size!=1 || parts.values.any {it<=0})return false
    if(runCatching {parts.values.fold(0L,Math::addExact)}.getOrNull()!=amount)return false
    return parts.all {(id,n)->s.nodes.find {it.id==id}?.let {s.eligible(it,person) && n<=s.remaining(it)}==true}
}
