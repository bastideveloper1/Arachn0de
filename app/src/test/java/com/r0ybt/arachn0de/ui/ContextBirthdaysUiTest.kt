package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class)
class ContextBirthdaysUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun todayPreservesModeAndBirthdayCardsTrackSelectedDayAndLiveData() {
        val app=compose.activity.application as Arachn0deApplication
        val repo=com.r0ybt.arachn0de.data.repository.PersonRepository(app.database,com.r0ybt.arachn0de.data.local.AvatarStore(app.privateContext,syncDirectory=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory))
        val today=CalendarDay(2026,7,16)
        val state=ContextCalendarState(today)
        runBlocking {
            repo.save("context-ana","Ana",null,birthday="1990-07-16")
            repo.save("context-bruno","Bruno",null,birthday="1980-07-16")
        }
        compose.setContent {Arachn0deTheme {
            val birthdays=rememberContextBirthdays(repo)
            Column {
                ContextCalendar(state,null,today,birthdays=birthdays)
                ContextBirthdayCards(birthdays,state.selected)
            }
        }}
        compose.waitUntil(10000) {compose.onAllNodesWithTag("context-birthday-marker:${today.key}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("calendar-birthday:context-ana").assertDoesNotExist()
        compose.onNodeWithTag("context-calendar-today").performClick()
        compose.runOnIdle {assertNull(state.selected)}
        compose.onNodeWithTag("context-calendar-day:${today.key}").performClick()
        compose.onNodeWithTag("calendar-birthday:context-ana").assertExists()
        compose.onNodeWithTag("calendar-birthday:context-bruno").assertExists()
        compose.runOnIdle {assertEquals(today,state.selected);assertEquals(1,state.mode)}
        compose.onNodeWithTag("context-calendar-today").performClick()
        compose.runOnIdle {assertEquals(today,state.selected);assertEquals(1,state.mode)}
        compose.onNodeWithTag("calendar-birthday:context-ana").assertExists()
        compose.onNodeWithTag("calendar-birthday:context-ana").performClick()
        compose.waitUntil(10000) {compose.onAllNodes(hasText("Antisocial · Ana") and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText("Volver") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("context-calendar-mode").performClick()
        compose.onNodeWithTag("context-calendar-period").performClick()
        compose.onNodeWithTag("context-calendar-next").performClick()
        compose.onNodeWithTag("context-calendar-period-close").performClick()
        compose.onNodeWithTag("context-calendar-today").performClick()
        compose.runOnIdle {assertEquals(today,state.anchor);assertEquals(2,state.mode)}
        runBlocking {repo.saveBirthday("context-ana","1990-07-17")}
        compose.waitUntil(10000) {compose.onAllNodesWithTag("calendar-birthday:context-ana").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("context-calendar-day:2026-7-17").performClick()
        compose.onNodeWithTag("calendar-birthday:context-ana").assertExists()
        compose.onNodeWithTag("calendar-birthday:context-bruno").assertDoesNotExist()
        runBlocking {repo.saveBirthday("context-ana","")}
        compose.waitUntil(10000) {compose.onAllNodesWithTag("calendar-birthday:context-ana").fetchSemanticsNodes().isEmpty()}
    }
}
