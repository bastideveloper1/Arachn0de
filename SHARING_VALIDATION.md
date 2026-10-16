# Compartir con Arachn0de — cierre automático (2026-10-15)

Compartir está habilitado mediante SHARE_ENABLED=true y filtros ACTION_SEND /
CATEGORY_DEFAULT para text/plain, image/png e image/jpeg en MainActivity.
Se conserva singleTop, la recepción autenticada y el editor existente.
No se admiten múltiples adjuntos ni otros formatos por el selector.

Se ejecutaron únicamente los pendientes y dos casos adicionales de conexión
externa. Siete casos distintos aprobaron, sin omisiones finales:

| Clase / caso | Resultado acreditado |
| --- | --- |
| SharedActivityTest.receivedShareAndEditedJournalSurviveActivityRecreationWithoutRedelivery | Recepción, Bundle sin texto privado, cambio de orientación con recreación y conservación del diario editado sin nueva entrega |
| SharedActivityTest.lockedShareIsRejectedWithoutContentInMessagesOrPendingStorage | Rechazo bloqueado, mensaje neutro y repositorio anterior revocado |
| SharedCaptureIsolationTest.pendingIsBoundToAuthenticatedStoreAcrossLockAndProcessReopen | Cola aislada por almacén, bloqueo y recuperación tras recrear el gestor y reabrir la base |
| SharedCaptureUiTest.existingCaptureEditsSharedContentAndProcessesNextOnlyAfterCancelOrSave | Edición, restauración de estado Compose, guardar, avanzar y cancelar sin crear la segunda tarea |
| SharedCaptureUiTest.temporaryScreenProtectionSurvivesGrantAndReturnsToCurrentPermissionAfterClose | FLAG_SECURE con reservas simultáneas, restitución de autorización vigente y revocación |
| SharedExternalEntryTest.androidResolvesSupportedSharesToExportedMainActivityOnly | Resolución de los tres MIME hacia MainActivity exportada; exclusión de PDF y SEND_MULTIPLE |
| SharedExternalEntryTest.onCreateReceivesTextLinkAndNewIntentReceivesPngAndJpeg | Recepción inicial de enlace y recepción posterior de PNG/JPEG con reserva de adjunto |

No se repitieron los seis casos previamente aprobados de cola/enlaces ni títulos
y límites. Las aprobaciones de Activity/aislamiento preceden a los filtros externos;
las pruebas adicionales acreditan su conexión después de habilitarlos.

Los intentos iniciales revelaron problemas del arnés: cierre de ActivityScenario,
lock bloqueante en onTerminate de LegacyUiTestApplication, acceso a Activity desde
un hilo de trabajo, comprobación antes de recargar el editor y fsync Android de
directorios no soportado por ShadowLinux. Se diagnosticaron antes de reintentar.
Se usa SharingTestApplication solo en estas pruebas, cierre asíncrono con bombeo
del looper y el sincronizador host existente de BackupFixture para importar imágenes.
No se modificó seguridad, persistencia ni sincronización de producción.

Ejecuciones selectivas (logs temporales locales, no evidencia permanente):
- /tmp/arachnode-sharing-pending-3.log: cinco casos, tres aprobados y dos de UI fallidos.
- /tmp/arachnode-sharing-ui-4.log: únicamente esos dos casos de UI, aprobados.
- /tmp/arachnode-sharing-external.log: resolución aprobada; entrada con imagen fallida en fixture.
- /tmp/arachnode-sharing-external-2.log: únicamente entrada externa, aprobada.

También pasó git diff --check. No se ejecutó una batería global ni se generó APK,
se cambió versión/firma, se instaló en dispositivos o se hizo commit/push.

## Comprobación manual pendiente en Android

Usar una compilación que incluya estos cambios. Con un Proyecto existente y sesión
que siga desbloqueada al volver desde otra aplicación, compartir texto/enlace desde
un navegador y una imagen PNG/JPEG desde una galería, seleccionando Arachn0de.
Comprobar edición, destino, guardar, cancelar y envíos consecutivos. Girar mientras
se edita y revisar título, descripción, destino y adjunto. Comprobar capturas y la
miniatura de recientes durante el editor y la restitución del permiso vigente al
cerrarlo. Repetir con Arachn0de bloqueada: debe pedir desbloquear y volver a
compartir, sin mostrar ni guardar el contenido rechazado.

Pendientes: selector y permisos URI reales de otras apps, rotación/teclado reales,
capturas/recientes del sistema y cierre abrupto real del proceso/dispositivo.
Robolectric no acredita estos comportamientos físicos ni SQLCipher nativo.
