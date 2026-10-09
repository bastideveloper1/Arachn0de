package com.r0ybt.arachn0de.ui

import android.content.Context
import com.r0ybt.arachn0de.Arachn0deApplication

/** UI media reads capture this session; an Activity's filesDir is the legacy unscoped root. */
internal fun privateStorageContext(context:Context):Context =
    (context.applicationContext as? Arachn0deApplication)?.privateContext ?: context.applicationContext
