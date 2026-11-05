package com.r0ybt.arachn0de.backup.legacy

import com.r0ybt.arachn0de.data.local.PrivatePreferenceEntity

/** Preserve deprecated records in backups only. Does not write Room, clear files or create tasks. */
internal object LegacyPurchaseArchive {
    fun merge(rows:List<PrivatePreferenceEntity>,input:PurchaseState?,vault:String?,confirmed:Set<String>):List<PrivatePreferenceEntity> {
        if(input==null || vault==null || input.vault!=vault || (input.pending.isEmpty() && input.dismissed.isEmpty())) return rows
        val old=rows.singleOrNull {it.name==PurchaseCodec.NAME}?.payload?.let(PurchaseCodec::decode) ?: PurchaseState(vault=vault)
        val additions=input.pending.filter {e->e.fingerprint !in old.seen && e.id !in confirmed && old.pending.none {it.id==e.id}}
        val pending=(old.pending+additions).filterNot {it.id in input.dismissed}
        val merged=old.copy(vault=vault,pending=pending,seen=old.seen+input.seen,dismissed=old.dismissed+input.dismissed)
        val encoded=PurchaseCodec.encode(merged)
        PurchaseCodec.decode(encoded) // Same bounds and validation as historical backups; fail without deleting source.
        return rows.filterNot {it.name==PurchaseCodec.NAME}+PrivatePreferenceEntity(PurchaseCodec.NAME,encoded)
    }
}
