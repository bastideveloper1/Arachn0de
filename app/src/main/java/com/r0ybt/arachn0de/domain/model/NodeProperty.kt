package com.r0ybt.arachn0de.domain.model

/** An editor patch over existing data, never a new persisted property. */
enum class NodeProperty { TITLE, DESCRIPTION, DATES, PRIORITY, PAYMENT, PEOPLE, TAGS, TECHNOLOGIES }

fun nodePropertyValues(field:NodeProperty,node:Node,tags:Collection<String> = emptyList(),people:Collection<String> = emptyList(),technologies:List<String> = emptyList()):List<String> = when(field) {
    NodeProperty.TITLE->listOf(node.title)
    NodeProperty.DESCRIPTION->listOf(node.description)
    NodeProperty.DATES->listOf(node.startAt?.toString().orEmpty(),node.dueAt?.toString().orEmpty())
    NodeProperty.PRIORITY->listOf(node.priority.name)
    NodeProperty.PAYMENT->listOf(node.obligation?.amountMinor?.toString().orEmpty(),node.obligation?.currencyCode.orEmpty())
    NodeProperty.PEOPLE->people.sorted()
    NodeProperty.TAGS->tags.sorted()
    NodeProperty.TECHNOLOGIES->technologies
}
