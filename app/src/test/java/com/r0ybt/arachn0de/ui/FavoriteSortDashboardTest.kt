package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.access.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.NodeSortPreferences
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=NavigationWorkApplication::class,sdk=[28],qualifiers="w360dp-h1000dp")
class FavoriteSortDashboardTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as NavigationWorkApplication
    @After fun close() {
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        stopAppearanceTestApplication(app)
    }
    @Test fun rootCardsGroupFavoritesStablyAndPreservePinnedAndStoredPositions() {
        val projects=runBlocking {
            val created=(1..4).map {app.projectRepository.createProject("Núcleo $it")}
            app.projectRepository.setPinned(created.first().id,true)
            created.map {requireNotNull(app.projectRepository.getProject(it.id))}
        }
        val access=AccessRepository(app.database)
        val preferences=NodeSortPreferences()
        var favorite by mutableStateOf(setOf("project:${projects[2].id}"))
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalElementAccess provides access,LocalElementAccessState provides AccessState(favorites=favorite)) {
                ProjectDashboardScreen(app.projectRepository,projects,emptyMap(),sortPreferences=preferences)
            }
        }}
        fun order()=projects.sortedBy {compose.onNodeWithTag("project-card:${it.id}").fetchSemanticsNode().boundsInRoot.top}.map {it.id}
        val original=projects.map {it.id}
        for((choice,expected) in listOf(FavoriteOrder.FIRST to listOf(original[0],original[2],original[1],original[3]),
            FavoriteOrder.LAST to listOf(original[0],original[1],original[3],original[2]),FavoriteOrder.NONE to original)) {
            compose.runOnIdle {preferences.configure(SortConfigurationCodec.PROJECTS_CONTEXT,SortConfiguration(favorites=choice))}
            compose.mainClock.advanceTimeBy(1000)
            assertEquals(expected,order())
        }
        compose.runOnIdle {
            favorite=setOf("project:${projects[3].id}")
            preferences.configure(SortConfigurationCodec.PROJECTS_CONTEXT,SortConfiguration(favorites=FavoriteOrder.FIRST))
        }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(listOf(original[0],original[3],original[1],original[2]),order())
        assertEquals(projects,runBlocking {projects.map {app.projectRepository.getProject(it.id)}})
    }
}
