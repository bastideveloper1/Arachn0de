package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.relationKey
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[33],qualifiers="w360dp-h891dp")
class AntisocialContextualSectionsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private lateinit var people:PersonRepository
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    private fun text(value:String)=await {compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}
    @Before fun setup()=runBlocking {
        people=PersonRepository(app.database,AvatarStore(app.security.current.value!!.context,syncDirectory=BackupFixture::syncDirectory))
        people.save("person","Apodo visible",null)
        people.saveProfile(PersonProfileEntity("person",alias="Apodo visible",givenName="Nombre guardado",familyName="Apellido guardado",description="Biografía guardada",documentId="Documento guardado",notes="Notas guardadas",phones="123\n456",emails="correo@example.org",addresses="Dirección",links="https://example.org",qualities="Cualidad",skills="Habilidad",interests="Interés",availability="Disponible"))
        people.saveBirthday("person","2000-01-02")
    }
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump();val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin()}
        runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}};app.database.close()
    }
    private fun open() {compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}};text("Fotografías")}
    private fun section(label:String) {compose.onNodeWithTag("person-section:$label").performScrollTo().performClick()}
    private fun field(index:Int) {compose.onNodeWithTag("modular-field:$index").performScrollTo().performClick()}
    private fun profile()=runBlocking {app.database.personProfileDao().profiles().single {it.personId=="person"}}

    @Test fun fourSectionsExposeOnlyApprovedFieldsAndPreserveHistoricalData() {
        runBlocking {people.save("other","Otra persona",null)}
        open()
        compose.onNodeWithTag("principal-profile-badge").assertExists()
        compose.onAllNodesWithText("Nombre guardado").assertCountEquals(1)
        compose.onNodeWithText("Soy yo").assertDoesNotExist()
        listOf("General","Contacto","Perfil","Relaciones","Proyectos y trabajo").forEach {compose.onNodeWithTag("person-section:$it").assertDoesNotExist()}
        antisocialSections.forEach {compose.onNodeWithTag("person-section:$it").assertExists()}
        compose.onNodeWithText("Opciones").performClick()
        compose.onNodeWithTag("profile-information").performClick()
        listOf("Apellidos","Apodo","DNI/RUT","Direcciones","Cualidades","Habilidades","Intereses","Disponibilidad","Notas").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
        val before=profile()
        field(1)
        compose.onNode(hasSetTextAction()).performTextReplacement("Biografía actualizada")
        compose.onNodeWithText("Confirmar").performClick()
        await {profile().description=="Biografía actualizada"}
        assertEquals(before.copy(description="Biografía actualizada"),profile())
        compose.onNodeWithText("Volver a Inicio").performClick()
        section("Personas")
        text("Otra persona")
        compose.onNodeWithTag("person-add-relation").assertExists()
        compose.onNodeWithTag("network-contact:other").performClick()
        text("Otra persona")
        compose.onNodeWithTag("principal-profile-badge").assertDoesNotExist()
        compose.onNodeWithText("Volver").performClick()
        await {compose.onAllNodesWithTag("person-network-list").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("person",runBlocking {people.ensurePrincipal()})
    }
    @Test fun badgeUsesTheActiveAccentWithoutActingAsAButton() {
        var palette by androidx.compose.runtime.mutableStateOf(com.r0ybt.arachn0de.ui.theme.AppearanceTheme.Arachn0de.palette)
        compose.setContent {Arachn0deTheme {
            androidx.compose.runtime.CompositionLocalProvider(com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette provides palette) {PrincipalProfileBadge()}
        }}
        listOf(com.r0ybt.arachn0de.ui.theme.AppearanceTheme.Arachn0de,com.r0ybt.arachn0de.ui.theme.AppearanceTheme.Dark,com.r0ybt.arachn0de.ui.theme.AppearanceTheme.GreenBash).forEach {theme->
            compose.runOnIdle {palette=theme.palette}
            compose.onNodeWithTag("principal-profile-badge").assertHasNoClickAction().assertContentDescriptionEquals("Perfil principal")
            val view=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            compose.runOnUiThread {view.draw(android.graphics.Canvas(bitmap))}
            val pixels=bitmap.asImageBitmap().toPixelMap()
            val accent=theme.palette.primary
            assertTrue((0 until pixels.width).any {x->(0 until pixels.height).any {y->
                val p=pixels[x,y];p.alpha>.9f && kotlin.math.abs(p.red-accent.red)<.03f && kotlin.math.abs(p.green-accent.green)<.03f && kotlin.math.abs(p.blue-accent.blue)<.03f
            }})
            bitmap.recycle()
        }
    }
}
