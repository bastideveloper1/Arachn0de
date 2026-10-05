package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.model.DescriptionParser

/** Human clipboard text only. The portable stored description and export defaults stay intact. */
internal fun descriptionClipboardText(text: String, originalNames: Map<String, String>): String =
    DescriptionParser.parse(text.replace(Regex("""\\+(?=\[\[arachnode:image:)"""), "")).joinToString("") { part ->
        if (part.imageId == null) part.text
        else "[Imagen: ${part.label ?: originalNames[part.imageId] ?: "Imagen no disponible"}]"
    }
