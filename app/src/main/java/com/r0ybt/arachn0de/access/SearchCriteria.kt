package com.r0ybt.arachn0de.access

/** Session-only query, never a preference or backup field. All active criteria intersect. */
internal data class SearchCriteria(val favorites:Boolean=false,val attention:Boolean=false,
    val obligations:Boolean=false,val recurrences:Boolean=false,val tag:String?=null) {
    val active get()=favorites || attention || obligations || recurrences || tag!=null
}
internal data class SearchFacts(val attention:Set<String> = emptySet(),val obligations:Set<String> = emptySet(),
    val recurring:Set<String> = emptySet(),val tags:Map<String,Set<String>> = emptyMap())

/** Existing visit receipts only change priority; no matching result is discarded by ordering. */
internal fun searchElements(index:List<ElementResult>,query:String,mode:String,criteria:SearchCriteria,
    access:AccessState,facts:SearchFacts,normalized:Map<String,String>?=null):List<ElementResult> {
    val q=ElementSearch.normalize(query)
    val matches=if(normalized==null)ElementSearch.find(index,query) else index.filter {q.isEmpty() || q in normalized[it.key].orEmpty()}
    val filtered=matches.filter {
        (!criteria.favorites || it.key in access.favorites) &&
        (!criteria.attention || it.key in facts.attention) &&
        (!criteria.obligations || it.key in facts.obligations) &&
        (!criteria.recurrences || it.key in facts.recurring) &&
        (criteria.tag==null || criteria.tag in facts.tags[it.key].orEmpty())
    }
    return when(mode) {
        "Favoritos"->filtered.filter {it.key in access.favorites} // Historical caller compatibility.
        "Recientes"->{val ranks=access.recent.withIndex().associate {it.value to it.index};filtered.sortedBy {ranks[it.key] ?: Int.MAX_VALUE}}
        "Frecuentes"->filtered.sortedByDescending {access.counts[it.key] ?: 0}
        else->filtered
    }
}
