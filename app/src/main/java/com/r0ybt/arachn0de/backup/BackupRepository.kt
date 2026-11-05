package com.r0ybt.arachn0de.backup
import com.r0ybt.arachn0de.data.local.ensurePrincipalProfile
import com.r0ybt.arachn0de.data.local.AttachmentStore
import com.r0ybt.arachn0de.data.local.ImageFileLifecycle

import com.r0ybt.arachn0de.security.SecureFiles
import android.content.Context
import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.GameStateEntity
import com.r0ybt.arachn0de.BuildConfig
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

internal class BackupRepository(
    private val database: Arachn0deDatabase,
    private val context: Context,
    private val avatars: BackupAvatarFiles = BackupAvatarFiles(context.applicationContext),
    private val attachments: BackupAttachmentFiles = BackupAttachmentFiles(context.applicationContext, avatars.syncDirectory),
    private val technologyIcons: BackupAvatarFiles = BackupAvatarFiles(context.applicationContext, "technology-icons", "technology-icon-journal", avatars.syncDirectory),
    private val projectPhotos: BackupAvatarFiles = BackupAvatarFiles(context.applicationContext, "project-photos", "project-photo-journal", avatars.syncDirectory),
) {
    private val themeOriginalFiles=BackupAvatarFiles(context.applicationContext,"theme-originals","theme-original-restore-journal",avatars.syncDirectory)
    private val galleryAttachmentStore=AttachmentStore(context.applicationContext,avatars.syncDirectory)
    private val galleryAvatarLifecycle=ImageFileLifecycle(context.applicationContext,"avatars",avatars.syncDirectory)
    private val vaultContext=context as? com.r0ybt.arachn0de.security.VaultContext
    private fun acceptStore(data:BackupData) {
        if(vaultContext!=null) require(data.storeKind==(if(vaultContext.primary) "primary" else "secondary")) { "El backup no corresponde a este almacén." }
    }

    private val encryptedRequired = context is com.r0ybt.arachn0de.security.VaultContext
    private val cache = File(context.cacheDir, "backups")
    private val mutex = Mutex()
    companion object { private val inspectedDirectories = java.util.concurrent.ConcurrentHashMap.newKeySet<String>() }
    private fun pendingMarker(name: String) = File(cache, "$name.keep")

    private suspend fun <T> withFiles(work: suspend () -> T): T =
        mutex.withLock { AttachmentRepository.fileOperations.withLock { work() } }

    suspend fun snapshot(): BackupData = withContext(Dispatchers.IO) {
        withFiles { snapshotLocked() }
    }

    private suspend fun snapshotLocked(streamImages: Boolean = false): BackupData = database.withTransaction {
        ensurePrincipalProfile(database)
        vaultContext?.let {com.r0ybt.arachn0de.guest.GuestCaptureQueue(it.baseContext).requireSettled(it.session.id.toString())}
        com.r0ybt.arachn0de.sharing.SharedCaptureQueue.requireSettled(database)
        com.r0ybt.arachn0de.audio.AudioDraftStore.requireSettled(context,database)
        val dao = database.backupDao()
        val projects = dao.projects()
        val nodes = dao.nodes()
        val people = dao.persons()
        val assignments = dao.assignments()
        require(projects.size.toLong() + nodes.size + people.size + assignments.size <= BackupLimits.RECORDS) { "Demasiados registros en el backup." }
        var totalBytes = 0L
        val imageFiles = mutableListOf<BackupImageFile>()
        val imageContents = linkedMapOf<String, File>()
        fun image(directory: String, store: BackupAvatarFiles, name: String): ByteArray? {
            if (streamImages) {
                val file = store.file(name)
                val row = inspectImage(directory, name, file)
                totalBytes += row.byteSize
                require(totalBytes <= BackupLimits.TOTAL_STREAMED_IMAGE_BYTES) { "Imágenes demasiado grandes (máximo 1 GiB)." }
                imageFiles.add(row); imageContents[row.key] = file
                return null
            }
            return store.read(name).also {
                totalBytes += it.size
                require(totalBytes <= BackupLimits.TOTAL_AVATAR_BYTES) { "El snapshot en memoria supera 8 MiB; usa la exportación por streaming." }
            }
        }
        val themeOriginals=com.r0ybt.arachn0de.backup.legacy.themeOriginalReferences(listOfNotNull(database.privatePreferenceDao().get(com.r0ybt.arachn0de.ui.theme.AppearancePreferences.FILE)))
        themeOriginals.forEach {(name,original)->
            com.r0ybt.arachn0de.backup.legacy.verifyThemeOriginal(original,themeOriginalFiles.read(name))
            val file=themeOriginalFiles.file(name);val row=inspectImage("theme-originals",name,file)
            totalBytes+=row.byteSize;require(totalBytes<=BackupLimits.TOTAL_STREAMED_IMAGE_BYTES)
            imageFiles.add(row);imageContents[row.key]=file
        }
        val gallery=database.personGalleryDao().photos()
        val images = (people.mapNotNull { it.avatarFile }.toSet()+gallery.map {it.file}).mapNotNull { name -> image("avatars", avatars, name)?.let { name to it } }.toMap()
        val technologies = database.technologyDao().catalog()
        val icons = technologies.mapNotNull { it.iconFile }.toSet().mapNotNull { name -> image("technology-icons", technologyIcons, name)?.let { name to it } }.toMap()
        val photos = database.projectPhotoDao().confirmed()
        val photoImages = photos.map { it.file }.toSet().mapNotNull { name -> image("project-photos", projectPhotos, name)?.let { name to it } }.toMap()
        val attachmentDao = database.attachmentDao()
        val nodeAttachments = attachmentDao.nodeAttachments()
        val projectAttachments = attachmentDao.projectAttachments()
        val ids = (nodeAttachments.map { it.attachmentId } + projectAttachments.map { it.attachmentId } + database.personMemoryDao().images().mapNotNull {it.attachmentId} + database.personProfileDao().banners().map {it.attachmentId}).toSet()
        // Editor reservations and DELETE_PENDING rows are not confirmed user data.
        val files = attachmentDao.files().filter { it.id in ids }
        val contents = files.associate { it.storageName to attachments.file(it.storageName) }
        files.forEach { verifyBackupAttachment(it, contents.getValue(it.storageName)) }
        BackupData(BuildConfig.VERSION_NAME, System.currentTimeMillis(), projects,
            nodes, people, assignments, images, database.recurrenceDao().rules(), database.recurrenceDao().occurrences(), database.recurrenceDao().assignments(), database.tagDao().tags(), database.tagDao().nodeTags(), database.tagDao().ruleTags(), database.nodeEventDao().all(), database.creationDefaultsDao().all(), database.creationDefaultsDao().tags(), database.creationDefaultsDao().people(), files, nodeAttachments, projectAttachments, contents, technologies = technologies, nodeTechnologies = database.technologyDao().nodes(), projectTechnologies = database.technologyDao().projects(), technologyIcons = icons, projectPhotos = photos, projectPhotoImages = photoImages,
            conversionRoots = database.conversionDao().roots(), conversionPeople = database.conversionDao().people(),
            conversionTags = database.conversionDao().tags(), conversionEvents = database.conversionDao().events(),
            conversionWorkStates = database.conversionDao().workStates(), nodeSortPreferences = database.nodeSortPreferenceDao().all(),
            bankRoots=database.bankDao().roots(),bankAccounts=database.bankDao().accounts(),bankOperations=database.bankDao().operations(),bankAllocations=database.bankDao().allocations(),calendarEvents=database.calendarEventDao().all(),calendarEventPeople=database.calendarEventDao().people(),personMemories=database.personMemoryDao().memories(),personMemoryPeople=database.personMemoryDao().people(),personMemoryImages=database.personMemoryDao().images(),personBanners=database.personProfileDao().banners(),personPhotos=gallery,personBirthdays=database.personGalleryDao().birthdays(), personTechnologies=database.personIntegrationDao().technologies(), personProfiles=database.personProfileDao().profiles(),personRelations=database.personProfileDao().relations(), imageFiles = imageFiles, imageContents = imageContents, storeId=vaultContext?.session?.id?.toString(),storeKind=if(vaultContext?.primary==false) "secondary" else "primary", privatePreferences = com.r0ybt.arachn0de.backup.legacy.LegacyPurchaseArchive.merge(database.privatePreferenceDao().all(),vaultContext?.let { com.r0ybt.arachn0de.backup.legacy.LegacyPurchaseInbox(it.baseContext).read() },vaultContext?.session?.id?.toString(),nodes.mapTo(hashSetOf()) {it.id}), savedTemplates = database.savedTemplateDao().all(), metroPreferences = database.metroDao().preferences()?.payload, metroLearningEvents = database.metroLearningDao().events(), metroJourneys = database.metroDao().journeys(), iterationPlans=database.iterationDao().plans(),iterationCycles=database.iterationDao().cycles(),iterationCommitments=database.iterationDao().commitments(),iterationClosures=database.iterationDao().closures(),managementParticipants=database.participationDao().participants(),participantRoles=database.participationDao().roles(),objectiveResponsibilities=database.participationDao().objectives(),nodeResponsibilities=database.participationDao().nodes(),managementRoles=database.backlogDao().roles(),objectiveEpics=database.backlogDao().objectiveEpics(),epicStories=database.backlogDao().epicStories(),storyTasks=database.backlogDao().storyTasks(),backlogEntries=database.backlogDao().entries(),projectCharters=database.planningDao().charters(), managementObjectives=database.planningDao().objectives(), acceptanceCriteria=database.planningDao().criteria(), sprintStories=database.cascadeRelationDao().stories(),objectiveWork=database.cascadeRelationDao().objectives(),localIdentities=database.cascadeRelationDao().identities(),managementLayers=database.managementLayerDao().all(), ganttAnnotations=database.ganttAnnotationDao().all(), managementContexts=database.managementContextDao().all(), sprintLinks=database.sprintLinkDao().all(), gameSession = database.gameStateDao().get()?.payload?.takeIf { it.isNotEmpty() }).also { it.validate() }
    }

    suspend fun writeSnapshot(output: java.io.OutputStream) = withContext(Dispatchers.IO) {
        withFiles { BackupContainer.writeBackup(snapshotLocked(streamImages = true),output) }
    }

    suspend fun create(password:CharArray?=null): File = withContext(Dispatchers.IO) {
        withFiles {
            // Serialize file cleanup/imports through completion, and capture all DB rows in one transaction.
            require(!encryptedRequired || (password!=null && password.size>=8)) { "Se necesita una contraseña de backup." }
            val data = snapshotLocked(streamImages = true)
            check(cache.isDirectory || cache.mkdirs())
            val temporary = File.createTempFile("backup-", ".part", cache)
            val complete = File(cache, "${UUID.randomUUID()}.arachnode")
            try {
                SecureFiles.output(temporary).use { output ->
                    if(password==null) BackupContainer.writeBackup(data, output)
                    else {
                        // The outer use owns fsync/close. Finalizing the portable cipher must
                        // not close that durable stream twice (a closed fd cannot be synced).
                        val borrowed=object:java.io.FilterOutputStream(output) {
                            override fun write(bytes:ByteArray,offset:Int,length:Int) {out.write(bytes,offset,length)}
                            override fun close() {flush()}
                        }
                        com.r0ybt.arachn0de.security.EncryptedBackup.output(borrowed,password).use { BackupContainer.writeBackup(data,it) }
                    }
                    if(password==null) output.flush()
                }
                if(password!=null) SecureFiles.input(temporary).use { raw ->
                    com.r0ybt.arachn0de.security.EncryptedBackup.input(raw,password).use { BackupContainer.readBackup(it) }
                }
                currentCoroutineContext().ensureActive()
                check(temporary.renameTo(complete))
                durableWrite(pendingMarker(complete.name), "pending SAF export".toByteArray())
                complete
            } catch (failure: Throwable) {
                complete.delete(); pendingMarker(complete.name).delete()
                throw failure
            } finally { temporary.delete() }
        }
    }

    suspend fun inspect(input: InputStream,password:CharArray?=null): BackupData = withContext(Dispatchers.IO) {
        check(cache.isDirectory || cache.mkdirs())
        val directory = File(cache, "inspect-${UUID.randomUUID()}").apply { check(mkdir()) }
        inspectedDirectories.add(directory.absolutePath)
        try { input.use { raw ->
            val buffered=java.io.PushbackInputStream(raw,8)
            val prefix=ByteArray(8);var count=0
            while(count<8) { val n=buffered.read(prefix,count,8-count);if(n<0) break;count+=n }
            buffered.unread(prefix,0,count)
            if(count==8 && com.r0ybt.arachn0de.security.EncryptedBackup.isEncrypted(prefix)) {
                requireNotNull(password) { "Se necesita la contraseña del backup." }
                com.r0ybt.arachn0de.security.EncryptedBackup.input(buffered,password).use { BackupContainer.readBackup(it,directory,::acceptStore) }
            } else {
                require(vaultContext?.primary!=false) {"Los backups históricos solo se importan en el almacén principal."}
                BackupContainer.readBackup(buffered,directory,::acceptStore)
            }
        } }
        catch (failure: Throwable) { inspectedDirectories.remove(directory.absolutePath); deleteInspection(directory); throw failure }
    }

    /** A failure/cancellation before commit rolls back Room and removes only new unreferenced files. */
    suspend fun restore(data: BackupData):Unit = withContext(Dispatchers.IO) {
        vaultContext?.let {com.r0ybt.arachn0de.guest.GuestCaptureQueue(it.baseContext).requireSettled(it.session.id.toString())}
        withFiles {
            com.r0ybt.arachn0de.sharing.SharedCaptureQueue.requireSettled(database)
        com.r0ybt.arachn0de.audio.AudioDraftStore.requireSettled(context,database)
            if(vaultContext!=null) {
                val active=(vaultContext.baseContext.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.current?.value
                require(active?.context!==vaultContext || !active.audio.state.value.visible) {"Cierra el audio antes de restaurar el respaldo."}
            }
            acceptStore(data)
            val ordered = data.validate()
            require(data.attachmentContents.keys == data.attachmentFiles.map { it.storageName }.toSet()) { "Faltan archivos adjuntos." }
            data.attachmentFiles.forEach { verifyBackupAttachment(it, data.attachmentContents.getValue(it.storageName)) }
            require(data.imageContents.keys == data.imageFiles.mapTo(hashSetOf()) { it.key }) { "Faltan imágenes del manifiesto." }
            data.imageFiles.forEach { verifyImage(it, data.imageContents.getValue(it.key)) }
            com.r0ybt.arachn0de.backup.legacy.themeOriginalReferences(data.privatePreferences).forEach {(name,o)->com.r0ybt.arachn0de.backup.legacy.verifyThemeOriginal(o,data.imageBytes("theme-originals",name))}
            recoverFiles() // Complete cleanup from a previous interrupted operation before replacing its journal.
            val photoNames = data.imageNames("project-photos").associateWith { "${UUID.randomUUID()}.png" }
            val iconNames = data.imageNames("technology-icons").associateWith { "${UUID.randomUUID()}.png" }
            val names = data.imageNames("avatars").associateWith { "${UUID.randomUUID()}.${it.substringAfterLast('.')}" }
            val themeNames=data.imageNames("theme-originals").associateWith {"${UUID.randomUUID()}.img"}
            val attachmentNames = data.attachmentFiles.associate { it.storageName to "${UUID.randomUUID()}.${it.storageName.substringAfterLast('.')}" }
            try {
                themeOriginalFiles.record(themeNames.values.toSet())
                data.imageNames("theme-originals").forEach {name->currentCoroutineContext().ensureActive();themeOriginalFiles.write(themeNames.getValue(name),data.imageBytes("theme-originals",name))}
                themeOriginalFiles.finishStaging()
                projectPhotos.record(photoNames.values.toSet())
                data.imageNames("project-photos").forEach { name ->
                    currentCoroutineContext().ensureActive()
                    projectPhotos.write(photoNames.getValue(name), data.imageBytes("project-photos", name))
                }
                projectPhotos.finishStaging()
                technologyIcons.record(iconNames.values.toSet())
                data.imageNames("technology-icons").forEach { name ->
                    currentCoroutineContext().ensureActive()
                    technologyIcons.write(iconNames.getValue(name), data.imageBytes("technology-icons", name))
                }
                technologyIcons.finishStaging()
                attachments.record(attachmentNames.values.toSet())
                data.attachmentFiles.forEach { row -> attachments.stage(row, data.attachmentContents.getValue(row.storageName), attachmentNames.getValue(row.storageName)) }
                attachments.finishStaging()
                avatars.record(names.values.toSet())
                data.imageNames("avatars").forEach { name ->
                    currentCoroutineContext().ensureActive()
                    avatars.write(names.getValue(name), data.imageBytes("avatars", name))
                }
                avatars.finishStaging()
                database.withTransaction {
                    database.bankDao().clearAllocations();database.bankDao().clearOperations();database.bankDao().clearAccounts();database.bankDao().clearRoots()
                    val previousMetroRevision = database.metroDao().journeys().maxOfOrNull { it.revision } ?: 0
                    database.cascadeRelationDao().clearStories();database.cascadeRelationDao().clearObjectives();database.cascadeRelationDao().clearIdentity()
                    database.ganttAnnotationDao().clear()
                    database.managementLayerDao().clear()
                    database.iterationDao().clear()
                    themeOriginalFiles.record(com.r0ybt.arachn0de.backup.legacy.themeOriginalReferences(listOfNotNull(database.privatePreferenceDao().get(com.r0ybt.arachn0de.ui.theme.AppearancePreferences.FILE))).keys+themeNames.values)
                    // Device-local security decisions are never imported, even from an authenticated
                    // backup. Capture the receiving state inside this same replacement transaction.
                    val localSecurity=database.privatePreferenceDao().get(com.r0ybt.arachn0de.security.ScreenCapturePreference.NAME)
                    database.privatePreferenceDao().clear()
                    localSecurity?.let {database.privatePreferenceDao().save(it)}
                    data.privatePreferences.filterNot {it.name==com.r0ybt.arachn0de.security.ScreenCapturePreference.NAME}.forEach { original ->
                        val row=if(original.name==com.r0ybt.arachn0de.backup.legacy.PurchaseCodec.NAME && vaultContext!=null) original.copy(payload=com.r0ybt.arachn0de.backup.legacy.PurchaseCodec.encode(com.r0ybt.arachn0de.backup.legacy.PurchaseCodec.decode(original.payload).copy(vault=vaultContext.session.id.toString()))) else original
                        database.privatePreferenceDao().save(com.r0ybt.arachn0de.backup.legacy.remapThemeOriginals(row,themeNames))
                    }
                    database.savedTemplateDao().clear()
                    database.metroLearningDao().clearEvents(); database.metroLearningDao().clearClock()
                    database.metroDao().clearJourneys(); database.metroDao().clearPreferences()
                    val photoDao = database.projectPhotoDao()
                    projectPhotos.record(photoDao.all().mapTo(hashSetOf()) { it.file } + photoNames.values)
                    photoDao.clear()
                    database.conversionDao().clear()
                    database.nodeSortPreferenceDao().clear()
                    val technologyDao = database.technologyDao()
                    technologyIcons.record(technologyDao.catalog().mapNotNull { it.iconFile }.toSet() + iconNames.values)
                    val attachmentDao = database.attachmentDao()
                    attachments.record(attachmentDao.files().map { it.storageName }.toSet() + attachmentNames.values)
                    val dao = database.backupDao()
                    val previous = dao.persons().mapNotNull { it.avatarFile }.toSet()+database.personGalleryDao().photos().map {it.file}
                    // Persist both sets before DB changes: recovery queries committed references after a crash.
                    avatars.record(previous + names.values)
                    val recurrence = database.recurrenceDao()
                    database.creationDefaultsDao().clear()
                    database.tagDao().clear()
                    recurrence.deleteOccurrences(); recurrence.deleteAssignments(); recurrence.deleteRules()
                    technologyDao.clear()
                    database.calendarEventDao().clear()
                    database.personMemoryDao().clear()
                    database.personProfileDao().clearBanners()
                    attachmentDao.clearNodes(); attachmentDao.clearProjects(); attachmentDao.clearFiles()
                    database.sprintLinkDao().clear()
                    dao.deleteAssignments()
                    dao.detachNodes() // Avoid SQLite's recursive cascade depth limit.
                    dao.deleteNodes(); dao.deleteProjects(); dao.deletePersons()
                    data.projects.forEach { database.projectDao().insert(it) }
                    data.persons.forEach { person -> database.personDao().insert(person.copy(avatarFile = person.avatarFile?.let(names::getValue))) }
                    data.personPhotos.forEach {database.personGalleryDao().save(it.copy(file=names.getValue(it.file)))}
                    data.calendarEvents.forEach {database.calendarEventDao().save(it)}
                    database.calendarEventDao().assign(data.calendarEventPeople)
                    data.personBirthdays.forEach {database.personGalleryDao().save(it)}
                    data.personProfiles.forEach {database.personProfileDao().save(it)}
                    data.personRelations.forEach {database.personProfileDao().insert(it)}
                    ordered.forEach { database.nodeDao().insert(it) }
                    data.projectPhotos.forEach { photoDao.save(it.copy(file = photoNames.getValue(it.file))) }
                    data.attachmentFiles.forEach { attachmentDao.insert(it.copy(storageName = attachmentNames.getValue(it.storageName))) }
                    data.personBanners.forEach {database.personProfileDao().save(it)}
                    data.personMemories.forEach {database.personMemoryDao().save(it)}
                    database.personMemoryDao().images(data.personMemoryImages)
                    database.personMemoryDao().people(data.personMemoryPeople)
                    attachmentDao.attachNode(data.nodeAttachments)
                    attachmentDao.attachProject(data.projectAttachments)
                    data.technologies.forEach { technologyDao.insert(it.copy(iconFile = it.iconFile?.let(iconNames::getValue))) }
                    data.personTechnologies.forEach {database.personIntegrationDao().save(it)}
                    technologyDao.assignNodes(data.nodeTechnologies)
                    technologyDao.assignProjects(data.projectTechnologies)
                    database.personDao().assign(data.assignments)
                    data.recurrenceRules.forEach { recurrence.insert(it) }
                    recurrence.assign(data.recurrenceAssignments)
                    data.recurrenceOccurrences.forEach { recurrence.record(it) }
                    database.tagDao().insert(data.tags)
                    database.tagDao().assignNodes(data.nodeTags)
                    database.tagDao().assignRules(data.recurrenceTags)
                    database.nodeEventDao().insertAll(data.nodeEvents)
                    data.creationDefaults.forEach { database.creationDefaultsDao().save(it) }
                    database.creationDefaultsDao().insertTags(data.defaultsTags)
                    database.creationDefaultsDao().insertPeople(data.defaultsPeople)
                    data.conversionRoots.forEach { database.conversionDao().save(it) }
                    database.conversionDao().people(data.conversionPeople); database.conversionDao().tags(data.conversionTags)
                    data.managementContexts.forEach {database.managementContextDao().save(it)}
                    data.managementLayers.forEach {database.managementLayerDao().insert(it)}
                    data.ganttAnnotations.forEach {database.ganttAnnotationDao().save(it)}
                    data.projectCharters.forEach {database.planningDao().save(it)}
                    data.managementObjectives.forEach {database.planningDao().save(it)}
                    data.sprintStories.forEach {database.cascadeRelationDao().insert(it)}
                    data.objectiveWork.forEach {database.cascadeRelationDao().insert(it)}
                    data.localIdentities.forEach {database.cascadeRelationDao().save(it)}
                    ensurePrincipalProfile(database)
                    data.acceptanceCriteria.forEach {database.planningDao().save(it)}
                    data.managementRoles.forEach {database.backlogDao().save(it)}
                    data.objectiveEpics.forEach {database.backlogDao().insert(it)}
                    data.epicStories.forEach {database.backlogDao().insert(it)}
                    data.storyTasks.forEach {database.backlogDao().insert(it)}
                    data.backlogEntries.forEach {database.backlogDao().save(it)}
                    data.managementParticipants.forEach {database.participationDao().insert(it)}
                    data.participantRoles.forEach {database.participationDao().insert(it)}
                    data.objectiveResponsibilities.forEach {database.participationDao().insert(it)}
                    data.nodeResponsibilities.forEach {database.participationDao().insert(it)}
                    data.sprintLinks.forEach {database.sprintLinkDao().insert(it)}
                    data.iterationPlans.forEach {database.iterationDao().save(it)}
                    data.iterationCycles.forEach {database.iterationDao().insert(it)}
                    data.iterationCommitments.forEach {database.iterationDao().insert(it)}
                    data.iterationClosures.forEach {database.iterationDao().insert(it)}
                    database.conversionDao().events(data.conversionEvents); database.conversionDao().workStates(data.conversionWorkStates)
                    data.nodeSortPreferences.forEach { database.nodeSortPreferenceDao().save(it) }
                    data.savedTemplates.forEach { database.savedTemplateDao().insert(it) }
                    data.metroPreferences?.let { database.metroDao().preferences(com.r0ybt.arachn0de.data.local.MetroPreferencesEntity(payload = it)) }
                    data.metroJourneys.forEach { row ->
                        val net=com.r0ybt.arachn0de.metro.MetroCodec.preferences(requireNotNull(data.metroPreferences)).network
                        val journey=com.r0ybt.arachn0de.metro.MetroCodec.journey(row.payload,net)
                        val restored=journey.copy(sessions=journey.sessions.map { s -> com.r0ybt.arachn0de.metro.MetroStages.portable(s) })
                        database.metroDao().save(row.copy(payload=com.r0ybt.arachn0de.metro.MetroCodec.journey(restored),revision=Math.addExact(maxOf(previousMetroRevision,row.revision),1)))
                    }
                    data.metroLearningEvents.forEach {database.metroLearningDao().insert(it)}
                    database.bankDao().insertRoots(data.bankRoots);database.bankDao().insertAccounts(data.bankAccounts);database.bankDao().insertOperations(data.bankOperations);database.bankDao().insertAllocations(data.bankAllocations)
                    val gameRevision = database.gameStateDao().get()?.revision ?: 0
                    database.gameStateDao().save(GameStateEntity(payload = data.gameSession ?: "", revision = Math.addExact(gameRevision, 1)))
                    currentCoroutineContext().ensureActive()
                }
                // Settings are committed with Room; refresh the session caches after commit.
                vaultContext?.reloadPreferences()
            } finally {
                // Room has either committed or rolled back. Cleanup failure must never report a committed restore as failed.
                withContext(NonCancellable) { runCatching { recoverFiles() } }
            }
        }
    }

    fun discard(data: BackupData?) {
        data?.inspectionDirectory?.takeIf { it.parentFile == cache && it.name.matches(Regex("inspect-[a-f0-9-]{36}")) }?.let {
            inspectedDirectories.remove(it.absolutePath); deleteInspection(it)
        }
    }

    suspend fun recover() = withContext(Dispatchers.IO) {
        withFiles {
            recoverFiles()
            cache.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000 }.forEach { file ->
                if (file.canonicalFile.parentFile != cache.canonicalFile) return@forEach
                when {
                    file.isFile && file.name.matches(Regex("[a-f0-9-]{36}\\.arachnode")) && !pendingMarker(file.name).exists() -> file.delete()
                    file.isFile && file.name.matches(Regex("backup-[a-zA-Z0-9-]+\\.part")) -> file.delete()
                    file.isDirectory && file.name.matches(Regex("inspect-[a-f0-9-]{36}")) && file.absolutePath !in inspectedDirectories -> deleteInspection(file)
                    file.isFile && file.name.matches(Regex("[a-f0-9-]{36}\\.arachnode\\.keep")) && !File(cache, file.name.removeSuffix(".keep")).exists() -> file.delete()
                }
            }
        }
    }

    private suspend fun recoverFiles() {
        val originalNames=themeOriginalFiles.recorded()
        database.withTransaction {
            val references=com.r0ybt.arachn0de.backup.legacy.themeOriginalReferences(listOfNotNull(database.privatePreferenceDao().get(com.r0ybt.arachn0de.ui.theme.AppearancePreferences.FILE))).keys
            originalNames.filterNot {it in references}.forEach {themeOriginalFiles.delete(it)}
        }
        themeOriginalFiles.clearJournal()
        val names = avatars.recorded()
        database.withTransaction {
            names.forEach { if (database.personDao().avatarReferences(it) == 0) {if(galleryAvatarLifecycle.reserved(it)) galleryAvatarLifecycle.queue(it) else avatars.delete(it)} }
        }
        avatars.clearJournal()
        val icons = technologyIcons.recorded()
        database.withTransaction { icons.forEach { if (database.technologyDao().iconReferences(it) == 0) technologyIcons.delete(it) } }
        technologyIcons.clearJournal()
        val photos = projectPhotos.recorded()
        database.withTransaction { photos.forEach { if (database.projectPhotoDao().references(it) == 0) projectPhotos.delete(it) } }
        projectPhotos.clearJournal()
        val attachmentNames = attachments.recorded()
        database.withTransaction {
            attachmentNames.forEach { if (database.attachmentDao().storageReferences(it) == 0 && it !in galleryAttachmentStore.protectedGalleryNames()) attachments.delete(it) }
        }
        attachments.clearJournal()
    }

    private fun deleteInspection(directory: File) {
        // Only generated, validated names in this owned inspection. Preserve unknown children.
        fun controlled(child:File,parent:File)=child.canonicalFile.parentFile==parent.canonicalFile && child.absoluteFile.normalize()==child.canonicalFile
        directory.listFiles().orEmpty().filter { it.isFile && controlled(it,directory) && BackupLimits.attachmentName.matches(it.name) }.forEach { it.delete() }
        val images=File(directory,"images")
        if(images.isDirectory && controlled(images,directory)) {
            for(area in listOf("avatars","technology-icons","project-photos","theme-originals")) {
                val folder=File(images,area)
                if(folder.isDirectory && controlled(folder,images)) {
                    folder.listFiles().orEmpty().filter {it.isFile && controlled(it,folder) && imageNameValid(area,it.name)}.forEach {it.delete()}
                    folder.delete() // Only succeeds after known files are gone; unknown files survive.
                }
            }
            images.delete()
        }
        directory.delete()
    }
    fun discardPending(name: String) {
        require(name.matches(Regex("[a-f0-9-]{36}\\.arachnode")))
        check(!pendingMarker(name).exists() || pendingMarker(name).delete())
        check(!File(cache, name).exists() || File(cache, name).delete())
    }
    fun pendingFile(name: String): File {
        require(name.matches(Regex("[a-f0-9-]{36}\\.arachnode")))
        return File(cache, name).also { require(it.isFile) { "El backup temporal ya no está disponible. Crea otro." } }
    }
}
