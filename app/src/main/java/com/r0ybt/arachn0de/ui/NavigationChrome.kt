package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.R

@Composable
internal fun FutureNavigationDrawer(onDismiss: () -> Unit) {
    val items = listOf(
        "Inicio",
        "Inbox",
        "Hoy",
        "Próximas",
        "Proyectos",
        "Etiquetas",
        "Personas",
        "Calendario",
        "Panel",
        "Notas",
        "Archivos",
        "Configuración",
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(Arachn0deColors.SurfaceRaised.copy(alpha = 0.98f))
            .padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.arachn0de_logo),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Arachn0de",
                    color = Arachn0deColors.Accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                    contentDescription = "Cerrar menú",
                    tint = Arachn0deColors.Accent,
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        items.forEachIndexed { index, item ->
            val selected = index == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Arachn0deColors.Selection.copy(alpha = 0.75f) else Color.Transparent)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) Arachn0deColors.Accent else Arachn0deColors.ControlSurface),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item,
                    color = if (selected) Arachn0deColors.TextPrimary else Arachn0deColors.TextSecondary.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "v0.1.0",
            color = Arachn0deColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
internal fun HeaderBar(onMenuClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(42.dp)
                .background(Arachn0deColors.SurfaceRaised, RoundedCornerShape(12.dp)),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Abrir menú",
                tint = Arachn0deColors.Accent,
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Arachn0de",
                color = Arachn0deColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
            )
        }

        IconButton(
            onClick = {},
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Arachn0deColors.Accent,
            )
        }
    }
}

