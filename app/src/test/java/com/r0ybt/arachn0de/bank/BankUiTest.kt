package com.r0ybt.arachn0de.bank

import androidx.compose.ui.unit.dp

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.ui.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[33],qualifiers="w360dp-h891dp")
class BankUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:BankRepository
    private var menus=0
    private var locks=0
    private fun await(text:String)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Before fun setup()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>();app.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(app);repo=BankRepository(db)
        db.personDao().insert(PersonEntity("yo","Yo",null));repo.createRoot("Banca","","bank");repo.createAccount("bank","Personal","yo")
        val core=ProjectRepository(db.projectDao(),db).getProject("bank")!!
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalGlobalNavigation provides GlobalNavigation({menus++},true,bottomAccess=true),LocalProjectLock provides ProjectLockAction(true){locks++}) {
            Column {Box(Modifier.weight(1f)) {BankWorkspace(core,db,{}) {_,_->}};GlobalBottomBar()}
        }}}
    }
    @After fun close() {
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        db.close()
    }
    @Test fun navigationLabelsAndNaturalOperationTitlesKeepExistingPanelsAndCancelWithoutWrites() {
        await("Personal (Yo)")
        val before=runBlocking {repo.state()}
        compose.onNodeWithTag("core-context:Content").assert(hasText("Editar")).performClick()
        compose.onNode(hasText("Editar") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("Nombre").assert(hasText("Banca"))
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("core-context:Creation").assert(hasText("Cuenta")).performClick()
        compose.onNodeWithText("Nueva cuenta").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Personal (Yo)").performClick()
        compose.onNodeWithTag("core-context:Content").assert(hasText("Contenido"))
        compose.onNodeWithTag("core-context:Options").assert(hasText("Opciones"))
        for(title in listOf("Ingresar dinero","Retirar dinero","Pagar")) {
            compose.onNodeWithTag("core-context:Creation").assert(hasText("Operar")).performClick()
            compose.onNodeWithText(title).performClick()
            compose.onNode(hasText(title) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onAllNodesWithText("Crear $title",ignoreCase=true).assertCountEquals(0)
            compose.onNodeWithText("Cancelar").performClick()
        }
        assertEquals(before,runBlocking {repo.state()})
    }

    @Test fun accountDirectSaveCancelAndAnnulThroughExistingPanels() {
        await("Personal (Yo)")
        compose.onNodeWithTag("global-menu-access").performClick();assertEquals(1,menus)
        compose.onNode(hasContentDescription("Bloquear") and hasAnyAncestor(hasTestTag("global-bottom-bar"))).performClick();assertEquals(1,locks)
        compose.onNodeWithTag("core-bottom-module").assertExists()
        compose.onNodeWithText("Cuenta").performClick();await("Nueva cuenta")
        compose.onAllNodes(hasTestTag("global-menu-access") and hasAnyAncestor(isDialog())).assertCountEquals(0)
        compose.onNodeWithText("Nombre").performTextInput("Ahorro")
        compose.onNodeWithText("Responsable: Seleccionar").performClick();compose.onNode(hasText("Yo") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(15000) {runBlocking {repo.state().accounts.size==2}}
        await("Ahorro (Yo)")
        assertEquals(2,runBlocking {repo.state().accounts.size})
        compose.onNodeWithText("Personal (Yo)").performClick();await("$0")
        compose.onNodeWithTag("global-menu-access").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        compose.onNodeWithText("Crear Capa").assertDoesNotExist()
        compose.onNodeWithText("Crear subcapa").assertDoesNotExist()
        compose.onNodeWithText("Operar").performClick();compose.onNodeWithText("Ingresar dinero").performClick()
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("Cantidad").performTextInput("1000")
        compose.onNodeWithText("Guardar").performClick();await("$1.000")
        assertEquals(1000L,runBlocking {repo.state().let {it.balance(it.accounts.single {a->it.nodes.single {n->n.id==a.id}.title=="Personal"}.id)}})
        compose.onNodeWithText("Transferir dinero").assertDoesNotExist()
        compose.onNodeWithText("Operar").performClick();compose.onNodeWithText("Retirar dinero").performClick()
        compose.onNodeWithText("Cantidad").performTextInput("100")
        compose.onNodeWithText("Guardar").assertIsEnabled()
        compose.onNodeWithText("Cantidad").performTextReplacement("2.000")
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(1,runBlocking {repo.state().operations.size})
        compose.onNodeWithText("Ingreso").performClick();compose.onNodeWithText("Anular").performClick()
        compose.onNodeWithText("Confirmar anulación").performClick()
        compose.waitUntil(15000) {runBlocking {repo.state().operations.single().voided}}
        assertEquals(0L,runBlocking {repo.state().let {it.balance(it.accounts.single {a->it.nodes.single {n->n.id==a.id}.title=="Personal"}.id)}})
    }
}
