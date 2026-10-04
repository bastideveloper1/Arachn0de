package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.defaults.*
import java.util.TimeZone

/** Defaults initialize latent ACTION fields too; NOTE hides them and saves no operational data. */
internal object CreationDefaultsDraftFactory {
    fun create(parentId:String?,defaults:EffectiveCreationDefaults,now:Long,zone:TimeZone):EditorDraft = EditorDraft(
        null,parentId,"","",startAt=DefaultDateResolver.instant(defaults.start,defaults.startTime,now,zone,false),
        dueAt=DefaultDateResolver.instant(defaults.due,defaults.dueTime,now,zone,true),purpose=defaults.purpose,priority=defaults.priority).apply {
        defaultStartMinute=(defaults.startTime as? DefaultTime.Minute)?.value
        defaultDueMinute=(defaults.dueTime as? DefaultTime.Minute)?.value
        financialEnabled=defaults.obligation;currencyCode=defaults.currency
        tagIds=defaults.tags.sorted();responsibleIds=defaults.people.sorted()
    }
}
