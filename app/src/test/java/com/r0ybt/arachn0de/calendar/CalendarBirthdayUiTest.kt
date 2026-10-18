package com.r0ybt.arachn0de.calendar

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.CalendarScreen
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class)
class CalendarBirthdayUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun scroll(tag:String) {compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag(tag))}
    private fun await(tag:String,present:Boolean=true) {compose.waitUntil(10000) {compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()==present}}
    @Test fun liveBirthdaysOpenTheCorrectExistingProfileAndReflectEditsRemovalsAndFilter() {
        val app=compose.activity.application as Arachn0deApplication
        val repo=com.r0ybt.arachn0de.data.repository.PersonRepository(app.database,com.r0ybt.arachn0de.data.local.AvatarStore(app.privateContext,syncDirectory=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory))
        val day=CalendarDay(2026,7,16)
        runBlocking {repo.save("birthday-ana","Ana cumpleaños",null,birthday="1990-07-16");repo.save("birthday-bruno","Bruno cumpleaños",null,birthday="1980-07-16")}
        val storage=app.privateContext.getSharedPreferences(CalendarSpecialPreferences.NAME,0)
        storage.edit().clear().commit()
        val preferences=CalendarSpecialPreferences(storage)
        compose.setContent {Arachn0deTheme {CalendarScreen(CalendarSnapshot(NodeTreeSnapshot(emptyList()),TimeZone.getTimeZone("UTC")),emptyList(),emptyMap(),true,CalendarDates.labelInstant(day),"UTC",{},{},birthdayRepository=repo,specialPreferences=preferences)}}
        compose.waitUntil(10000) {compose.onAllNodesWithTag("calendar-day:${day.key}").fetchSemanticsNodes().any {it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) {""}.contains("2 cumpleaños")}}
        scroll("calendar-birthday:birthday-ana")
        compose.onNodeWithTag("calendar-birthday:birthday-ana").assert(hasText("Cumple 36 años"))
        scroll("calendar-birthday:birthday-bruno")
        scroll("calendar-birthday:birthday-ana")
        compose.onNodeWithTag("calendar-birthday:birthday-ana").performClick()
        compose.waitUntil(10000) {compose.onAllNodes(hasText("Antisocial · Ana cumpleaños") and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText("Volver") and hasAnyAncestor(isDialog())).performClick()
        runBlocking {repo.saveBirthday("birthday-ana","1991-07-16")}
        scroll("calendar-birthday:birthday-ana")
        compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("calendar-birthday:birthday-ana") and hasText("Cumple 35 años")).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("calendar-filters").performClick()
        compose.onNodeWithTag("calendar-special-toggle:Cumpleaños de Personas").performScrollTo().performClick()
        compose.onNodeWithText("Listo").performClick()
        await("calendar-birthday:birthday-ana",false)
        assertFalse(CalendarSpecialPreferences(storage).settings.birthdays)
        compose.onNodeWithTag("calendar-filters").performClick()
        compose.onNodeWithTag("calendar-special-toggle:Cumpleaños de Personas").performScrollTo().performClick()
        compose.onNodeWithText("Listo").performClick()
        scroll("calendar-birthday:birthday-ana")
        runBlocking {repo.saveBirthday("birthday-ana","1991-07-17")}
        await("calendar-birthday:birthday-ana",false)
        runBlocking {repo.saveBirthday("birthday-bruno","")}
        await("calendar-birthdays-heading",false)
        runBlocking {repo.saveBirthday("birthday-ana","1991-07-16")}
        scroll("calendar-birthday:birthday-ana")
        runBlocking {repo.delete("birthday-ana")}
        await("calendar-birthdays-heading",false)
        assertTrue(runBlocking {app.database.calendarEventDao().all().isEmpty()})
    }
}
