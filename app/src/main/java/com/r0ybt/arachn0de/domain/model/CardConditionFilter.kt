package com.r0ybt.arachn0de.domain.model

import java.util.TimeZone

/** Independent queries over the existing Attention policy; no new temporal rules. */
data class CardConditionFilter(val urgentOnly:Boolean=false,val due:AttentionReason?=null) {
    init {require(due==null || due in listOf(AttentionReason.OVERDUE,AttentionReason.DUE_TODAY,AttentionReason.UPCOMING))}
    val active get()=urgentOnly || due!=null
    fun matches(node:Node,now:Long,zone:TimeZone):Boolean {
        if(!active)return true
        val reasons=AttentionPolicy.reasons(node,now,zone)
        return (!urgentOnly || AttentionReason.HIGH_PRIORITY in reasons) && (due==null || due in reasons)
    }
}
