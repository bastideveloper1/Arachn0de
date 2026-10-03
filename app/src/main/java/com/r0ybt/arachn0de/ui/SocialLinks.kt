package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.R

internal data class SocialLink(val label: String, val url: String, val icon: Int?)
internal object AuthorLinks {
    val profiles = listOf(
        SocialLink("Instagram · @itsbasti_an","https://www.instagram.com/itsbasti_an/",R.drawable.ic_social_instagram),
        SocialLink("GitHub · bastideveloper1","https://github.com/bastideveloper1",R.drawable.ic_social_github),
        SocialLink("Mastodon · @Yll@infosec.exchange","https://infosec.exchange/@Yll",R.drawable.ic_social_mastodon),
        SocialLink("Web · 27thdeer.com","https://27thdeer.com/",null),
    )
    val source = SocialLink("github.com/bastideveloper1/Arachn0de","https://github.com/bastideveloper1/Arachn0de",R.drawable.ic_social_github)
}

@Composable
internal fun SocialLinkRow(link: SocialLink, onClick: () -> Unit, source: Boolean = false) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
        contentDescription = (if(source) "Código fuente, Arachn0de" else link.label.replace(" · ",", ")) + ", abrir enlace externo"
    }) {
        if(link.icon == null) Icon(Icons.Default.Language,null,Modifier.size(24.dp))
        else Icon(painterResource(link.icon),null,Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(link.label,Modifier.weight(1f))
        Spacer(Modifier.width(8.dp)); Icon(Icons.Default.OpenInNew,null,Modifier.size(18.dp))
    }
}
