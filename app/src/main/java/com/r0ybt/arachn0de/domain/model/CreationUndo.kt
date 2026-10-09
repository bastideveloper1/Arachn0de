package com.r0ybt.arachn0de.domain.model

data class CreationUndo(val nodes: List<Node>, val tags: Map<String, Set<String>>, val people: Map<String, Set<String>>, val events: Map<String, List<NodeEvent>>, val attachments: Map<String, Set<String>> = emptyMap(), val technologies: Map<String, Set<String>> = emptyMap(), val technologyOrder: Map<String,List<String>> = emptyMap())
