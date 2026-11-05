package com.r0ybt.arachn0de.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

internal fun AppearancePalette.role(key:String):Color=when(key) {"background"->background;"middle"->middle;"end"->end;"surface","cards"->surface;"raised"->raised;"panel"->panel;"taskPanel"->taskPanel;"drawer"->background;"header"->surface;"text"->text;"muted"->muted;"outline"->outline;"primary"->primary;"icons"->text;"secondary"->secondary;"buttons"->solidPrimary;"onButtons"->onSolidPrimary;"controls"->controls;"disabled"->if(light)muted else BlackWhite90.Inactive;"selection"->selection;"accentSurface"->accentSurface;"priority","warning"->SemanticColors.HighPriority;"error"->SemanticColors.Error;"success"->Color(0xff98ca9d);else->scrim}

internal fun Modifier.appearancePaint(role:String,fallback:Color):Modifier = background(fallback)
