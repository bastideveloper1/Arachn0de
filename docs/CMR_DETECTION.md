# Integración CMR retirada

La detección automática por notificaciones ha sido eliminada del proyecto. No hay listener, acceso especial, consulta de paquetes bancarios, controles, avisos de sugerencias ni apertura de editor desde CMR. El contenido siguiente es histórico y no describe funcionalidad vigente.

Se conserva exclusivamente compatibilidad de datos en `backup/legacy`: codec histórico, recuperación/lectura del antiguo archivo cifrado y preservación de su contenido en respaldos. No crea claves, entradas, tareas ni movimientos. No borra datos ni archivos históricos. Room y el formato de respaldo conservan su versión. Detalles: BACKUP_FORMAT.md.

## Registro histórico

# Detector local de compras CMR

Estado: compilación y siete pruebas focalizadas aprobadas; **sin emisores autorizados todavía**. No se debe anunciar detección real disponible hasta verificar el paquete que emite una notificación y su certificado de firma. La ficha de Google Play identifica Banco Falabella Chile como `cl.android` (https://play.google.com/store/apps/details?id=cl.android); esto no demuestra por sí solo que sea el emisor del aviso recibido. No hay dispositivo conectado por ADB durante esta intervención.

## Integración

En Ajustes → Seguridad y respaldos aparece Detectar compras, inicialmente desactivado. Explica que Android concede acceso amplio a notificaciones, aunque el código filtra estrictamente un emisor autorizado antes de leer campos. Hasta completar la verificación, «Entendido» cierra la explicación y no solicita un permiso sensible que todavía no puede utilizar. Al incorporar una identidad verificada en `PurchaseIssuers.certificates`, el mismo flujo permite consentimiento local y acceso oficial Android; no necesita rediseño.

`NotificationListenerService` realiza reconocimiento local conservador. No lee SMS, correo ni accesibilidad, ni incorpora conexiones bancarias/analítica. Extrae solo CLP entero, comercio y recepción. Una notificación propia no contiene datos financieros y ofrece Crear tarea / Descartar. La actividad transporta únicamente el UUID; el editor se compone dentro de la sesión desbloqueada. Las restricciones Android y la entrega del emisor pueden impedir recibir avisos: no se reconstruyen transacciones ni se promete un historial completo.

Se reutilizan NodeDialog, EditorDraft, campos de Pago/Money, selector basado en ElementSearch y el panel AccessDialog compartido, temas, NodeRepository, operaciones de adjuntos y asignaciones del editor. La tarea se crea únicamente tras confirmación, con Pago pendiente, sin vencimiento ni responsables/cuentas automáticos. La fecha de recepción se propone como Inicio, editable. No se insertan operaciones ni asignaciones en Banca.

## Persistencia y recuperación

Se reutilizan SQLCipher/private_preferences, EncryptedPreferences, DurableVaultFile y el bloqueo común AttachmentRepository.fileOperations. La entrada bloqueada usa AES-GCM con clave AndroidKeyStore de dispositivo, sin retener la clave de bóveda. El traslado a la bóveda confirma Room antes de retirar la entrada; sus UUID/hashes permiten reintentar sin duplicar. Cancelar el editor libera sus reservas de adjuntos; no elimina la sugerencia ni crea tareas. Descartar conserva solo el control mínimo de duplicados. No hay eliminación por antigüedad de sugerencias pendientes.

Los datos incorporados a la bóveda se exportan y restauran con el snapshot existente. JSON 50 añade validación de esta preferencia sin modificar Room 53 ni tablas financieras; formatos 1–49 siguen admitidos, con detector desactivado si no existe la preferencia. El ingreso temporal debe estar incorporado antes de respaldar o restaurar, o se informa un error sin sustituir datos. La identidad operativa de bóveda se ajusta al destino durante la restauración. Se validan límites, tipos, UUID, hashes, importes y ausencia de colisiones con tareas confirmadas. Véase BACKUP_FORMAT.md.

## Archivos de esta intervención

Nuevos:

- app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseRecognition.kt
- app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseState.kt
- app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseDeviceInbox.kt
- app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseRepository.kt
- app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseBridge.kt
- app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseListener.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/PurchaseUi.kt
- app/src/test/java/com/r0ybt/arachn0de/purchases/PurchaseDetectionTest.kt
- app/src/test/java/com/r0ybt/arachn0de/purchases/PurchaseUiTest.kt
- docs/CMR_DETECTION.md

Integraciones modificadas conservando cambios anteriores:

- app/src/main/AndroidManifest.xml
- app/src/main/java/com/r0ybt/arachn0de/MainActivity.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/AppRoot.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/ElementAccessUi.kt (visibilidad interna del diálogo existente; su comportamiento no cambia)
- app/src/main/java/com/r0ybt/arachn0de/security/SecurityUi.kt
- app/src/main/java/com/r0ybt/arachn0de/backup/BackupData.kt
- app/src/main/java/com/r0ybt/arachn0de/backup/BackupJson.kt
- app/src/main/java/com/r0ybt/arachn0de/backup/BackupRepository.kt
- ARCHITECTURE.md
- BACKUP_FORMAT.md

## Permisos y comprobación física pendiente

El servicio declara `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` como protección de enlace para Android. No se declara como permiso ordinario solicitado por la aplicación. El permiso POST_NOTIFICATIONS ya existía y se reutiliza para los avisos propios. El manifiesto añade visibilidad del paquete candidato `cl.android`; no se autoriza por ello su procesamiento. El receptor Descartar no está exportado.

Falta comprobar con un teléfono: emisor real y certificado obtenido de una instalación oficial, consentimiento y revocación, permisos de avisos, entrega en segundo plano, recepción con bóveda bloqueada, apertura tras desbloqueo y presentación del editor/teclado; también la estabilidad de la identidad al actualizar un aviso y dos compras iguales sucesivas. La autorización de emisores exige paquete y certificados explícitos; no debe sustituirse por aceptación de cualquier aplicación que escriba «Compraste».

No se genera APK, no se cambia 0.16.0/code30, no se hace commit ni push.

## Validación dirigida

Se ejecutan exclusivamente PurchaseDetectionTest y PurchaseUiTest, con filtros por método al corregir casos concretos. No se ejecuta la suite financiera anterior ni pruebas generales. Las siete pruebas cubren:

1. Patrón conocido, cantidad CLP, comercio, rechazo de ambigüedades y emisores desconocidos.
2. Estado inicial, activación del modelo, identidad de entrega y duplicados sin fusionar entregas distintas del mismo comercio/importe.
3. Cifrado AES-GCM de entrada, reemplazo durable, fallo de fsync, recuperación, descarte y conservación de pendientes.
4. Aviso genérico, acciones, visibilidad secreta, falta de permiso y rechazo temprano de aplicaciones desconocidas.
5. Respaldo y restauración, lectura histórica, validación de corrupción, tarea Pago pendiente tras confirmación, cancelación sin tarea, rollback inyectado, confirmación repetida y ausencia de operaciones/asignaciones en Banca.
6. Editor habitual: selección de destino, botón bloqueado sin destino, cancelación y creación confirmada sin vencimiento automático.
7. Ajustes desactivados por defecto, explicación cancelable, emisor no supuesto y prioridad de la preferencia de la bóveda abierta sobre la copia operativa tras desactivar/restaurar. La entrada bloqueada no abre ninguna base de datos ni retiene su clave.

Los cinco primeros casos y los dos de interfaz se verifican con Robolectric; la prueba de cifrado utiliza una clave AES inyectada. Eso no sustituye la validación de AndroidKeyStore, acceso especial y entregas reales en un teléfono. La lista de emisores autorizados vacía impide comprobar una entrega real de CMR hasta completar su identificación.

Resultado final: `BUILD SUCCESSFUL` en la compilación y validación final. Siete métodos aprobados combinando ejecuciones focalizadas: cinco casos válidos de la primera ejecución, los dos casos corregidos en `/tmp/arachnode-purchases-recheck.log`, y los dos afectados por el último refuerzo en `/tmp/arachnode-purchases-activation.log`. Los casos no afectados no se repitieron. El inventario de fuentes no cambió durante la última compilación (517 archivos verificados). `git diff --check` y la inspección del manifiesto pasaron. No se declara validada una entrega real del banco ni AndroidKeyStore en dispositivo físico.
