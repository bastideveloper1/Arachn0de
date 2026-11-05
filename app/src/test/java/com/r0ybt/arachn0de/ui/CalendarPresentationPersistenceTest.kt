package com.r0ybt.arachn0de.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.domain.model.CalendarDay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class CalendarPresentationPersistenceTest {
    @Test fun collapseIsGlobalAcrossScopesAndRestartsWithoutChangingFilters() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val storage=context.getSharedPreferences("calendar-test",0).apply {edit().clear().commit()}
        val today=CalendarDay(2026,10,28)
        val store=ContextCalendarStore().apply {bind(CalendarPresentation(storage))}
        val dashboard=store.scope("dashboard",today)
        dashboard.selected=today;dashboard.urgentOnly=true;dashboard.mode=0
        val layer=store.scope("layer",today)
        assertEquals(0,layer.mode)
        layer.mode=2;layer.expanded=2
        assertNotEquals(0,dashboard.mode);assertEquals(today,dashboard.selected);assertTrue(dashboard.urgentOnly)
        layer.mode=0
        val restarted=ContextCalendarStore().apply {bind(CalendarPresentation(storage))}
        assertEquals(0,restarted.scope("new-layer",today).mode)
        restarted.scope("new-layer",today).mode=1
        assertFalse(CalendarPresentation(storage).collapsed)
    }
}
