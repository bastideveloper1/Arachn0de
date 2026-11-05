package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
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
class AntisocialFinalAdjustmentsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private lateinit var people:PersonRepository
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    private fun text(value:String)=await {compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}
    @Before fun setup()=runBlocking {
        people=PersonRepository(app.database,AvatarStore(app.security.current.value!!.context,syncDirectory=BackupFixture::syncDirectory))
        people.save("person","Ana",null)
        people.saveProfile(PersonProfileEntity("person",alias="Ana",description="Bio",notes="Notas conservadas",phones="123",qualities="Cualidad",documentId="Privado"))
    }
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump();runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}};app.database.close()
    }
    private fun openProfile() {
        compose.onNodeWithTag("person-edit-profile").performClick();text("Soy yo")
        compose.onNodeWithText("Guardar").assertDoesNotExist()
    }
    @Test fun cameraRequestUsesTemporaryGrantsAndOpeningFailureCanRetryWithoutLosingPendingCapture() {
        val context=compose.activity
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.CAMERA)
        val authority="${app.packageName}.photo-camera"
        val info=requireNotNull(app.packageManager.resolveContentProvider(authority,android.content.pm.PackageManager.GET_META_DATA))
        val provider=com.r0ybt.arachn0de.photos.CameraCaptureProvider().apply {attachInfo(app,info)}
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(authority,provider)
        val files=com.r0ybt.arachn0de.photos.CameraCaptureFiles(app,BackupFixture::syncDirectory)
        val owner=app.security.current.value!!.access.id
        val old=files.prepare(owner);files.file(old).writeBytes(byteArrayOf(1,2,3))
        var attempts=0;var request=0;var fail=true;var error:String?=null;var opened:android.content.Intent?=null
        val registry=object:androidx.activity.result.ActivityResultRegistry() {
            override fun <I,O> onLaunch(code:Int,contract:androidx.activity.result.contract.ActivityResultContract<I,O>,input:I,options:androidx.core.app.ActivityOptionsCompat?) {
                attempts++;request=code;opened=contract.createIntent(context,input)
                if(fail)throw android.content.ActivityNotFoundException("synthetic missing camera")
            }
        }
        val registryOwner=object:androidx.activity.result.ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner) {
                val camera=rememberPrivatePhotoCamera(true,{error=it},captureFiles=files) {_,_->throw AssertionError("Unexpected photo after cancellation")}
                androidx.compose.material3.TextButton(onClick=camera) {androidx.compose.material3.Text("Cámara de prueba")}
            }
        }}
        try {
            repeat(2) {attempt->
                error=null
                compose.onNodeWithText("Cámara de prueba").performClick()
                await {attempts==attempt+1 || error!=null}
                assertEquals(org.robolectric.shadows.ShadowLog.getLogsForTag("CameraCapture").joinToString {it.throwable?.toString().orEmpty()},attempt+1,attempts)
                await {error!=null}
                await {files.pending(owner).size==1}
                assertArrayEquals(byteArrayOf(1,2,3),files.file(old).readBytes())
            }
            val intent=requireNotNull(opened)
            val uri=requireNotNull(intent.getParcelableExtra<android.net.Uri>(android.provider.MediaStore.EXTRA_OUTPUT))
            assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE,intent.action)
            assertEquals(authority,uri.authority);assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
            assertEquals(3,intent.flags and 3)
            assertEquals(0,intent.flags and android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            fail=false;error=null
            compose.onNodeWithText("Cámara de prueba").performClick();await {attempts==3}
            compose.runOnUiThread {registry.dispatchResult(request,android.app.Activity.RESULT_CANCELED,android.content.Intent())}
            await {files.pending(owner).size==1}
            assertArrayEquals(byteArrayOf(1,2,3),files.file(old).readBytes())
        } finally {files.finish(old)}
    }

    @Test fun bannerHasOneEntryAndBottomBarOpensPhotoBannerAndInformationDirectly() {
        compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        await {compose.onAllNodesWithTag("profile-banner").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("profile-banner").assertIsDisplayed().performClick()
        await {compose.onAllNodesWithText("Elegir imagen").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("profile-inline-panel").assertDoesNotExist()
        compose.onNodeWithTag("person-banner-panel").assertIsDisplayed()
        val editor=compose.onNodeWithText("Elegir imagen").fetchSemanticsNode().boundsInRoot
        val cancel=compose.onNodeWithText("Cancelar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(editor.bottom<=cancel.top)
        compose.onNodeWithText("Cancelar").performClick()
        await {compose.onAllNodesWithTag("profile-banner").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Opciones de fotografía").assertDoesNotExist()
        compose.onNodeWithText("Opciones",substring=false).assertDoesNotExist()
        val buttons=listOf("profile-photo","profile-banner","profile-information").map {compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot}
        buttons.zipWithNext().forEach {(a,b)->assertTrue(a.right<=b.left)}
        compose.onNodeWithTag("profile-photo").performClick()
        compose.onNodeWithTag("profile-camera").assertExists()
        compose.onNodeWithContentDescription("Volver a Inicio").performClick()
        compose.onNodeWithTag("profile-information").performClick()
        compose.onNodeWithTag("profile-inline-panel").assertDoesNotExist()
        compose.onNodeWithTag("modular-field:0").assertExists()

    }
    @Test fun contactStarTogglesImmediatelyAndFavoritesListUsesSameStoredState() {
        runBlocking {people.save("contact","Bruno",null);people.save("other","Carla",null)}
        compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person",initialSection="Personas") {}}}
        await {compose.onAllNodesWithTag("person-favorite:contact").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("person-favorite:contact").performClick().assertIsSelected()
        await {runBlocking {"contact" in people.favoritePeople()}}
        compose.onNodeWithTag("network-contact:contact").assertExists()
        compose.onNodeWithTag("person-network-favorites").performClick()
        compose.onNodeWithTag("network-contact:contact").assertExists()
        compose.onNodeWithTag("network-contact:other").assertDoesNotExist()
        compose.onNodeWithTag("person-favorite:contact").performClick()
        await {runBlocking {"contact" !in people.favoritePeople()}}
        await {compose.onAllNodesWithTag("network-contact:contact").fetchSemanticsNodes().isEmpty()}
        assertTrue(runBlocking {PersonRepository(app.database,AvatarStore(app.security.current.value!!.context,syncDirectory=BackupFixture::syncDirectory)).favoritePeople().isEmpty()})
    }
    @Test fun bannerReplacementAndPickerCancellationKeepAvatarAndPreviousImage() {
        val attachments=AttachmentRepository(app.database,AttachmentStore(app.security.current.value!!.context,BackupFixture::syncDirectory))
        fun image(name:String,color:Int):AttachmentFileEntity=runBlocking {
            val source=java.io.File(app.cacheDir,name)
            val bitmap=android.graphics.Bitmap.createBitmap(90,30,android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            source.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            attachments.importGallery(android.net.Uri.fromFile(source),"banner-seed")
        }
        val first=image("first-banner.png",android.graphics.Color.BLUE)
        val second=image("second-banner.png",android.graphics.Color.GREEN)
        val banners=PersonBannerRepository(app.database,attachments)
        runBlocking {
            app.database.personMemoryDao().save(PersonMemoryEntity("banner-gallery","person",null,"Referencias",0))
            app.database.personMemoryDao().images(listOf(first,second).mapIndexed {i,f->PersonMemoryImageEntity("banner-image-$i","banner-gallery",f.id,null,i)})
            banners.save(PersonBannerEntity("person",first.id),"banner-seed")
        }
        var open by mutableStateOf(true)
        compose.setContent {Arachn0deTheme {
            val rows by banners.observe().collectAsState(emptyList())
            val files by attachments.observeFiles().collectAsState(emptyList())
            if(open && rows.isNotEmpty()) PersonBannerEditor("person",rows.first(),files,listOf(first.id,second.id),attachmentRepository=attachments) {open=false}
        }}
        await {compose.onAllNodesWithText("Elegir imagen").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(first.originalName).assertDoesNotExist()
        compose.onNodeWithText(second.originalName).assertDoesNotExist()
        compose.onNodeWithText("Elegir imagen").performClick()
        val launch=checkNotNull(org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult)
        compose.runOnUiThread {org.robolectric.Shadows.shadowOf(compose.activity).receiveResult(launch.intent,android.app.Activity.RESULT_CANCELED,android.content.Intent())}
        assertEquals(first.id,runBlocking {banners.observe().first().single().attachmentId})
        compose.onNodeWithText("Elegir imagen").performClick()
        val picked=checkNotNull(org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult)
        compose.runOnUiThread {org.robolectric.Shadows.shadowOf(compose.activity).receiveResult(picked.intent,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.fromFile(java.io.File(app.cacheDir,"second-banner.png"))))}
        val preview=compose.onNodeWithTag("person-banner-preview").fetchSemanticsNode().boundsInRoot
        assertEquals(3f,preview.width/preview.height,.02f)
        await {compose.onAllNodesWithText("Cancelar").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Cancelar").performClick()
        await {!open}
        assertEquals(first.id,runBlocking {banners.observe().first().single().attachmentId})
        compose.runOnIdle {open=true}
        await {compose.onAllNodesWithText("Elegir imagen").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Elegir imagen").performClick()
        val repicked=checkNotNull(org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult)
        compose.runOnUiThread {org.robolectric.Shadows.shadowOf(compose.activity).receiveResult(repicked.intent,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.fromFile(java.io.File(app.cacheDir,"second-banner.png"))))}
        await {compose.onAllNodesWithText("Guardar").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_banner BEFORE INSERT ON person_banners BEGIN SELECT RAISE(ABORT,'synthetic banner failure'); END")
        compose.onNodeWithText("Guardar").performClick()
        await {compose.onAllNodesWithText("synthetic banner failure",substring=true).fetchSemanticsNodes().isNotEmpty()}
        assertEquals(first.id,runBlocking {banners.observe().first().single().attachmentId})
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_banner")
        compose.onNodeWithText("Guardar").performClick()
        await {!open}
        assertEquals(second.id,runBlocking {banners.observe().first().single().attachmentId})
        assertNull(runBlocking {people.observePeople().first().first {it.id=="person"}.avatarFile})
    }
    @Test fun inlineProfileKeepsConsultationInteractiveAndConfirmedPersonalDataVisible() {
        runBlocking {people.saveProfile(PersonProfileEntity("person",alias="Ana",description="Bio completa",givenName="Nombre guardado",familyName="Apellido guardado",documentId="Documento guardado",phones="123"))}
        compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        text("Fotografías");openProfile()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithTag("person-publications").assertIsDisplayed()
        compose.onNodeWithTag("person-section:General").performClick();text("Información del perfil")
        val consultation=compose.onNodeWithTag("person-profile-consultation")
        consultation.performScrollToNode(hasText("Nombre guardado"))
        compose.onNodeWithText("Nombre guardado").assertIsDisplayed()
        consultation.performScrollToNode(hasText("Documento guardado"))
        compose.onNodeWithText("Documento guardado").assertIsDisplayed()
        compose.onNodeWithTag("profile-information").performClick()
        compose.onNodeWithTag("modular-field:7").performScrollTo().performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Nombre confirmado")
        compose.onNodeWithText("Confirmar").performClick()
        text("Nombre confirmado")
        consultation.performScrollToNode(hasText("Nombre confirmado"))
        compose.onNodeWithText("Nombre confirmado").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("profile-self").assertExists()
        val visible=profileConsultationFields(PersonProfileEntity("person",givenName="Nombre",documentId="Documento"),"General")
        assertEquals(listOf("Nombre" to "Nombre","DNI / RUT" to "Documento"),visible)
    }

    @Test fun informationEditsOneFieldAndSelfToggleConfirmsReplacementAndPersists() {
        runBlocking {people.save("other","Otro",null);people.identifySelf("other")}
        compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        text("Fotografías");compose.onNodeWithTag("person-delete-profile").assert(hasText("Eliminar perfil"));openProfile()
        compose.onNodeWithTag("profile-information").performClick()
        compose.onNodeWithTag("modular-field:3").performScrollTo().performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Descartar")
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals("123",runBlocking {app.database.personProfileDao().profiles().first {it.personId=="person"}.phones})
        compose.onNodeWithTag("modular-field:7").performScrollTo().performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Nombre privado")
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.database.personProfileDao().profiles().first {it.personId=="person"}.givenName=="Nombre privado"}}
        compose.onNodeWithTag("modular-field:9").performScrollTo().assert(hasText("DNI / RUT"))
        compose.onNodeWithText("Volver a opciones").performScrollTo().performClick()
        compose.onNodeWithTag("profile-self").assertIsOff().performClick();text("Cambiar mi perfil")
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals("other",runBlocking {people.observeSelf().first()!!.personId})
        compose.onNodeWithTag("profile-self").performClick();compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {people.observeSelf().first()?.personId=="person"}}
        compose.onNodeWithTag("profile-self").assertIsOn()
        compose.onNodeWithContentDescription("Cerrar perfil").performClick();openProfile()
        compose.onNodeWithTag("profile-self").assertIsOn().performClick()
        await {runBlocking {people.observeSelf().first()==null}}
        val profile=runBlocking {app.database.personProfileDao().profiles().first {it.personId=="person"}}
        assertEquals("Privado",profile.documentId);assertEquals("Notas conservadas",profile.notes);assertEquals("Cualidad",profile.qualities)
    }
    @Test fun photoFramingCancellationAndAvatarRemovalPreserveSharedImage() {
        val file=runBlocking {
            val source=java.io.File(app.cacheDir,"avatar-shared.png").apply {writeBytes(BackupFixture.png())}
            val imported=people.importAvatar(android.net.Uri.fromFile(source))
            people.savePhoto(PersonPhotoEntity("shared","person",imported,0),"seed")
            people.save("person","Ana",imported,isNew=false);imported
        }
        val restore=androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restore.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person") {}}}
        text("Fotografías");openProfile();compose.onNodeWithTag("profile-photo").performClick()
        compose.onNodeWithTag("profile-frame").performClick();text("Ampliar")
        compose.onNodeWithText("Ampliar").performClick();compose.onNodeWithText("Cancelar").performClick()
        assertEquals(1f,runBlocking {app.database.personDao().get("person")!!.avatarZoom})
        fun pick() {
            compose.onNodeWithTag("profile-change-photo").performClick()
            val launched=checkNotNull(org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult)
            val source=java.io.File(app.cacheDir,"picked-avatar.png").apply {writeBytes(BackupFixture.png())}
            compose.runOnUiThread {compose.activity.activityResultRegistry.dispatchResult(launched.requestCode,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.fromFile(source)))}
            text("Ampliar")
        }
        val store=AvatarStore(app.security.current.value!!.context)
        pick();restore.emulateSavedInstanceStateRestore();text("Ampliar")
        compose.onNodeWithText("Cancelar").performClick()
        await {java.io.File(app.security.current.value!!.context.filesDir,"avatars").listFiles().orEmpty().size==1}
        assertEquals(file,runBlocking {app.database.personDao().get("person")!!.avatarFile})
        pick();compose.onNodeWithText("Ampliar").performClick();compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.database.personDao().get("person")!!.avatarFile!=file}}
        val replacement=runBlocking {app.database.personDao().get("person")!!.avatarFile!!}
        assertTrue(store.exists(file));assertTrue(store.exists(replacement))
        compose.onNodeWithTag("profile-remove-avatar").performClick()
        await {runBlocking {app.database.personDao().get("person")!!.avatarFile==null}}
        await {!store.exists(replacement)}
        compose.onNodeWithContentDescription("Volver a opciones").performClick()
        compose.onNodeWithContentDescription("Cerrar perfil").performClick()
        assertTrue(AvatarStore(app.security.current.value!!.context).exists(file))
        assertEquals(file,runBlocking {app.database.personGalleryDao().photos().single().file})
    }
    @Test fun technologySelectorStaysCompactSearchesAndReturnsThroughItsLevels() {
        runBlocking {repeat(30) {app.database.technologyDao().insert(TechnologyEntity("tech$it","Tecnología $it",null))}}
        compose.setContent {Arachn0deTheme {PeopleScreen(people,initialPersonId="person",initialSection="Tecnologías") {}}}
        text("Agregar Tecnología")
        val compact=compose.onNodeWithTag("person-technology-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(compact.height<300*compose.activity.resources.displayMetrics.density)
        compose.onNodeWithTag("person-add-technology").performClick()
        compose.onNodeWithText("Buscar Tecnologías").performTextReplacement("29")
        compose.onNodeWithText("Tecnología 29").performClick();text("Guardar dominio")
        compose.onNodeWithText("Experto").performClick();compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Buscar Tecnologías").assertIsDisplayed()
        assertTrue(runBlocking {app.database.personIntegrationDao().technologies().isEmpty()})
        compose.onNodeWithText("Tecnología 29").performClick();compose.onNodeWithText("Experto").performClick()
        compose.onNodeWithText("Guardar dominio").performClick()
        await {runBlocking {app.database.personIntegrationDao().technologies().singleOrNull()?.proficiency=="EXPERT"}}
        compose.onNodeWithTag("person-add-technology").assertIsDisplayed()
        compose.onNodeWithTag("person-technology:tech29").performClick();text("✓ Experto")
        compose.onNodeWithText("Retirar asociación").performScrollTo().performClick()
        await {runBlocking {app.database.personIntegrationDao().technologies().isEmpty()}}
        compose.onNodeWithTag("person-add-technology").assertIsDisplayed()
        assertEquals(30,runBlocking {app.database.technologyDao().catalog().size})
    }
    @Test fun compactWorkCardsKeepRealDestinationsAndSecondaryResponsibilities() {
        val task=runBlocking {
            val project=app.projectRepository.createProject("Núcleo de trabajo")
            val task=app.nodeRepository.createNode(project.id,null,"Tarea con título largo para comprobar su tarjeta compacta","")
            people.save("first","Aarón",null)
            people.setResponsiblePeople(task.id,setOf("person","first"));task
        }
        var destination:PersonDestination?=null
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalPersonNavigation provides {destination=it}) {
            PersonWorkSection("person",people)
        }}}
        text(task.title)
        compose.onNodeWithTag("person-work-item:${task.id}").assertIsDisplayed()
        compose.onNodeWithText("Tarea · Pendiente").assertExists()
        compose.onNodeWithText("Responsable tradicional").assertExists()
        await {compose.onAllNodesWithTag("person-photo:first",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("person-photo:first",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("person-work-project:${task.projectId}").onChildren().filter(hasAnyDescendant(hasTestTag("person-photo:first"))).assertCountEquals(0)
        compose.onNodeWithTag("person-work-item:${task.id}").performClick()
        assertEquals(task.id,destination?.nodeId);assertEquals(task.projectId,destination?.projectId)
        compose.onNodeWithTag("person-work-project:${task.projectId}").performScrollTo().performClick()
        assertEquals(null,destination?.nodeId);assertEquals(task.projectId,destination?.projectId)
    }
}
