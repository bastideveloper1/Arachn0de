# Retirada de detección automática CMR

## Alcance

Se elimina la capacidad de leer notificaciones ajenas: servicio, acceso especial, consulta del paquete bancario, receptor Descartar, avisos de compra, reconocimiento y lista de emisores, activación/configuración y editor desde sugerencias. Se retiran los puntos de entrada de MainActivity, AppRoot y Ajustes. No se cambia la navegación o apariencia fuera de esos controles.

Banco, cuentas, pagos, abonos parciales, recurrencias y tareas existentes permanecen sin cambios. Los permisos de cámara, micrófono, avisos propios, servicios, Internet y actualizador son idénticos a los anteriores. No cambia Room 53 ni la versión de aplicación 0.17.0/code31. No se genera APK, commit ni push.

## Archivos

Eliminados:

- `app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseListener.kt`
- `app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseRecognition.kt`
- `app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseRepository.kt`
- `app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseBridge.kt`
- `app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseState.kt`
- `app/src/main/java/com/r0ybt/arachn0de/purchases/PurchaseDeviceInbox.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/PurchaseUi.kt`
- Tests de la integración retirada: `purchases/PurchaseDetectionTest.kt` y `purchases/PurchaseUiTest.kt`.

Modificados:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/r0ybt/arachn0de/MainActivity.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/AppRoot.kt`
- `app/src/main/java/com/r0ybt/arachn0de/security/SecurityUi.kt`
- `app/src/main/java/com/r0ybt/arachn0de/backup/BackupData.kt`
- `app/src/main/java/com/r0ybt/arachn0de/backup/BackupJson.kt`
- `app/src/main/java/com/r0ybt/arachn0de/backup/BackupRepository.kt`
- `ARCHITECTURE.md`, `BACKUP_FORMAT.md`, `docs/CMR_DETECTION.md` (señalizado como histórico).

Compatibilidad residual nueva ubicación:

- `app/src/main/java/com/r0ybt/arachn0de/backup/legacy/LegacyPurchaseCodec.kt`
- `app/src/main/java/com/r0ybt/arachn0de/backup/legacy/LegacyPurchaseInbox.kt`
- `app/src/main/java/com/r0ybt/arachn0de/backup/legacy/LegacyPurchaseArchive.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/legacy/LegacyPurchaseRemovalTest.kt`

## Datos históricos

Se conserva lectura, validación, exportación y restauración de JSON 1–50. La preferencia histórica sigue cifrada y no activa ninguna función. Una entrada antigua cifrada de la misma bóveda puede incorporarse al snapshot como dato archivado, sin escritura en Room, creación de tareas ni movimientos. No se generan claves ni entradas nuevas. La lectura usa la clave existente y el diario durable anterior bajo el bloqueo común; no borra el archivo. Corrupción o ausencia de clave provocan error explícito sin descartar datos.

No se introduce limpieza de archivos históricos: permanecen conservados; los bytes necesarios se incorporan al payload del backup y no se exportan rutas locales ni claves de dispositivo. Se omiten entradas cuyo ID ya tenga tarea confirmada. Restituir estos datos no restituye un listener ni permisos.

## Validación

Compilación de aplicación y tests correcta; seis métodos aprobados mediante ejecuciones focalizadas:

1. Backup/restauración histórica, formato anterior y corrupción sin pérdida de preferencia; sin creación de tareas ni operaciones.
2. Archivo de entrada histórica, bóveda correspondiente, idempotencia, datos de origen intactos y omisión de tareas ya confirmadas.
3. Lectura AES-GCM histórica y corrupción sin borrar archivo.
4. Manifiesto fusionado sin servicio de acceso a notificaciones ni receptor CMR; permisos de las demás funciones conservados.
5. Pago desde saldo, selección y reparto manual, validación inmediata, cancelación y abonos.
6. Pago directo sin cuenta y cancelación final sin escrituras financieras.

El primer intento pasó cinco casos; el sexto se repitió aisladamente tras corregir únicamente su expectativa de activación archivada. La última ejecución terminó con `BUILD SUCCESSFUL`. Fuentes de aplicación idénticas durante la compilación. `git diff --check` correcto. Se verificó por hashes que Banco y la configuración de versión permanecen intactos.

La comprobación de Play Protect requiere una futura APK autorizada y prueba en el teléfono. La retirada no garantiza por sí sola una clasificación concreta de Google. La APK oficial 0.17.0/code31 ya existente conserva la integración anterior: no se ha reconstruido ni sustituido.
