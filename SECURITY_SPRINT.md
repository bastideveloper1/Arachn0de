# Sprint de seguridad local — Arachn0de

Implementación funcional en el repositorio, con validación JVM/Robolectric y compilación debug. **La migración y el cifrado de páginas SQLCipher todavía requieren ejecutar las pruebas nativas en Android real. No se certifica esa validación física a partir de la compilación.** No se realizó instalación, release, firma de distribución, commit ni push.

La versión comercial permanece **0.3.0 / versionCode 9**, applicationId `com.r0ybt.arachn0de`. La firma oficial y los cinco PNG del juego se conservan.

## Diseño y bibliotecas

La auditoría previa, decisiones, fuentes primarias y especificación interoperable están en [SECURITY_DESIGN.md](SECURITY_DESIGN.md). Bouncy Castle **1.86** aporta Argon2id; SQLCipher Android de Zetetic **4.19.1** aporta el motor SQLite cifrado y autenticado. No se sustituyó Room por un motor propio, ni se cambiaron el SDK o la configuración de firma.

Cada almacén tiene UUID, DEK aleatorio de 256 bits y envoltura independiente. Argon2id v1.3 usa 64 MiB, tres pasadas, un lane y salt aleatorio de 32 bytes. La envoltura usa AES-256-GCM; archivos y backups usan registros GCM autenticados de 64 KiB, subclave HKDF-SHA256 por objeto, nonce con contador y registro final obligatorio. Se rechazan corrupción, truncación, reordenación y bytes adicionales. Contraseñas UTF-8 sin normalización; no se guardan contraseñas ni claves en texto claro. El núcleo no depende de Android Keystore y permite implementación futura Desktop; no se implementaron redes, cuentas, biometría o LAN.

## Comportamiento implementado

- Creación/confirmación, desbloqueo, bloqueo manual, cambio de contraseña con la anterior y retrasos progresivos de intentos incorrectos. Cambiar contraseña reenvelopa el mismo DEK y conserva datos/archivos.
- Inicio de proceso bloqueado. Segundo plano cubre la UI, oculta su semántica accesible y conserva los editores durante la gracia. Plazo por almacén: 30 segundos por defecto, inmediato, 1 minuto o 5 minutos. Al expirar se cierran los recursos. La rotación conserva navegación y borradores únicamente en memoria, fuera del Bundle persistente.
- Configuración inicial de contraseña señuelo desde el principal, con contraseña principal y distinta. Base, archivos, preferencias, claves y repositorios independientes; sin indicador de modo en el almacén abierto. Cerrar sesión cancela/espera trabajos, cierra Room y streams, vacía cachés y claves, detiene Metro/notificaciones y revoca informes.
- Exportación ANBACK01 con contraseña independiente y validación completa antes de publicar. Restauración cifrada y formatos históricos, staging cifrado, confirmación y transacción. El señuelo rechaza backups principales e importaciones históricas sin cifrar. Preferencias se restauran en la misma transacción y se recargan sus cachés.
- Lectura de avatares/iconos/fotos desde el contexto capturado de la sesión; la caché comprueba autorización antes de entregar un bitmap. Informes privados cifrados y compartidos por pipe, sin crear PNG persistentes en claro. Portapapeles explícito marcado sensible y limpieza propia al bloquear cuando Android lo permite.

## Datos protegidos y metadatos visibles

Protegidos: todas las tablas Room (incluidos Metro/Guardados/juego), preferencias privadas, avatares, iconos, fotografías, adjuntos, staging, reservas, diarios de restauración/liberación, informes privados, backups pendientes, inspecciones y copia de recuperación/migración. SQLCipher usa seguridad de memoria y temporales SQLite en memoria; los archivos usan AES-GCM. Las exclusiones del backup siguen siendo cachés regenerables y datos no confirmados, no datos funcionales necesarios.

Visibles con acceso al sistema de archivos: UUID, número de almacenes, nombres portables, extensiones, tamaños, fechas, índice y cabeceras/parámetros/salts criptográficos. Los assets públicos del juego/Metro y APK de actualización siguen públicos. No se promete invisibilidad forense. Los cinco PNG empaquetados se compararon con sus originales del repositorio: bytes idénticos.

Texto copiado, informes guardados/compartidos y archivos exportados voluntariamente pasan al control de Android/proveedor/destinatario. Los nuevos backups exportados permanecen cifrados; copias externas históricas en claro no pueden retirarse automáticamente. La criptografía no protege frente a root, un sistema comprometido, una contraseña débil o extracción de memoria de una sesión abierta. Se vacían buffers controlados, pero JVM, Compose, bibliotecas nativas y GC impiden garantizar borrado de todas las copias de strings/bitmaps en memoria. Los retrasos progresivos son de la sesión del proceso; Argon2id también encarece el ataque offline.

## Migración y recuperación

La migración usa exclusivamente el principal: captura preferencias, genera un snapshot consistente, lo lee y restaura en un probe, checkpoint/cierra la fuente, inventaría archivos y crea copias cifradas junto al origen. SQLCipher exporta a otra base; verifica esquema/filas tipadas/identificadores, FK, integridad y reapertura. Archivos y directorios se sincronizan antes del índice durable que activa el almacén. Los errores anteriores a esa decisión conservan el origen y un UUID pendiente para reintentar con la misma contraseña. No hay recifrado destructivo in situ ni reemplazo de la base original.

La limpieza posterior usa un manifiesto cifrado y elimina solo archivos inventariados cuyos bytes originales y copias cifradas siguen coincidiendo, bajo el bloqueo común. Es idempotente y reintentable al desbloquear. No caducan reservas por antigüedad ni se barre el almacenamiento privado. No es posible garantizar borrado físico irreversible en flash.

Se retienen la copia inicial cifrada y los originales cifrados como punto de recuperación del principal; no se confunden con un backup actual ni se comparten con el señuelo. La pantalla bloqueada permite exportar explícitamente esa copia inicial con la contraseña del almacén y otra contraseña de backup, sin abrir una base nativa dañada y sin sustituir datos. Puede restaurarse en otra instalación principal, previa validación y confirmación. Es una copia del momento de migración y puede no contener modificaciones posteriores. Perder la contraseña no tiene recuperación mediante puerta trasera.

## Persistencia y archivos modificados

Room **25→26** añade únicamente `private_preferences(name,payload)`; conserva tablas, relaciones y migraciones históricas. JSON **18** añade preferencias tipadas y procedencia/clase de almacén dentro de la envoltura cifrada. Los lectores conservan JSON 1–18 y contenedores v1/v2. Versiones anteriores no pueden abrir los nuevos almacenes ni hacer downgrade de Room; la versión comercial no cambia.

Archivos principales, agrupados por responsabilidad:

- `security/`: `VaultCrypto`, `VaultStreams`, `EncryptedBackup`, `SecureFiles`, `DurableVaultFile`, `EncryptedPreferences`, `VaultContext`, `VaultSession`, `VaultManager`, `VaultFileMigration`, `SqlCipherMigration`, `SecurityUi`.
- `Arachn0deApplication.kt`, `MainActivity.kt`, `Arachn0deDatabase.kt`, `PrivatePreferenceEntity.kt`, esquema Room `26.json` y dependencias en `app/build.gradle.kts`.
- `backup/BackupData`, `BackupJson`, `BackupContainer`, `BackupRepository`, `BackupDocuments`, adaptadores de imágenes/adjuntos/diarios; `ui/BackupSection` y `ui/state/BackupActions`.
- Stores de avatares, iconos, adjuntos y su lifecycle; `PrivateImageCache`; componentes de imágenes y `PrivateStorageContext`; tema/apariencia; informes y `ReportFileProvider`; servicio Metro y `ContextClipboard`; ciclo de vida de la caché observable de Tecnologías.
- Pruebas dirigidas `security/*`, pruebas nativas `NativeVaultTest`, fixture de regresión `LegacyUiTestApplication` y ajustes de expectativas de esquema/flujos de backup. El fixture histórico solo existe en test: no acredita cifrado de páginas ni se incluye en la aplicación.
- `ARCHITECTURE.md`, `BACKUP_FORMAT.md`, `RELEASING.md` y los dos informes de seguridad. Los cambios anteriores del repositorio se mantuvieron; el diff completo incluye trabajo previo y no debe atribuirse íntegramente a este sprint.

## Validación real

La tanda de regresión dirigida aprobó **379 pruebas, cero fallos**: seguridad 30, Metro 56, Guardados 15, backups 136, repositorios 69, UI 35, informes 20 y juego 18. Incluye Proyectos/Capas, conversiones, Sprint, finanzas, creación, edición, navegación y borradores. Se corrigieron expectativas históricas de esquema, confirmación de contraseñas y una pérdida real de navegación por capturar el registro Compose después de disponer sus proveedores.

La prueba de volumen restaura **100 Personas adicionales (103 con el fixture histórico)** y **500 Tecnologías**, con 600 imágenes propias, IDs, asociaciones, jerarquías y preferencias. Comprueba bytes originales tras descifrado, almacenamiento físico cifrado, contraseña equivocada sin reemplazo y eliminación de la inspección. Room del fixture es en memoria: esta prueba verifica repositorios, backup y multimedia, y no sustituye el ensayo nativo de SQLCipher.

Pruebas de seguridad cubren vector conocido AES-256-GCM, KDF/envolturas/salts/nonce, separación de claves y finalidades, tamper/truncación/registros, streams revocados, raíces/symlinks, preferencias/rollback, fallos de sincronización durable, poco espacio/lectura/interrupción/reintento, limpieza que conserva fuentes cambiadas, almacenes y cambio de contraseña, reinicio/bloqueo/gracia de segundo plano, gate de UI, multimedia cifrada real en componentes e informes revocados.

Después de los ajustes finales pasaron 57 casos de seguridad y módulos afectados; el controlador se verificó aparte con sus 3 casos aprobados, incluidos vaciado de la caché de Tecnologías y separación de almacenes. No se suman estas repeticiones al total de 379 pruebas de regresión. Logs locales: `/tmp/security-verified.log`, `/tmp/security-final-session-tests.log` y `/tmp/security-final-controller-build.log`. No se ejecutó la suite global.

## Validación física pendiente

`app/src/androidTest/java/com/r0ybt/arachn0de/security/NativeVaultTest.kt` contiene dos pruebas **compiladas, no ejecutadas**: exportación nativa con NULL/BLOB/UTF-8/rechazo de clave incorrecta, y migración real de Room a SQLCipher con limpieza, conservación de IDs/archivo y almacenes independientes. No se instaló ningún APK para ejecutarlas, conforme a la restricción del sprint.

Antes de distribuir, ejecutar esas pruebas y dogfooding en un entorno Android autorizado con copia recuperable: migración completa de datos representativos, inspección de base/WAL/temporales, interrupción/reinicio y espacio insuficiente, backup cifrado en otro dispositivo, bloqueo/SAF y notificaciones Metro. La estimación Metro mantiene su sesión persistida; mientras está bloqueado se detiene el servicio privado y su notificación debe recuperarse desde el módulo al desbloquear. No se afirma que políticas de batería o motor nativo hayan sido verificadas en un teléfono.


## Nota de cierre de compilación

`assembleDebug` y `assembleDebugAndroidTest`: **BUILD SUCCESSFUL**. `git diff --check`: aprobado. La última compilación y el controlador se verificaron en `/tmp/security-final-controller-build.log`.

- APK debug: `/home/r0ybt/AndroidStudioProjects/Arachn0de/app/build/outputs/apk/debug/app-debug.apk`, **113,338,653 bytes**.
- APK de pruebas nativas: `/home/r0ybt/AndroidStudioProjects/Arachn0de/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, **1,198,714 bytes**, compilado sin instalar ni ejecutar.
- Metadatos del APK: `com.r0ybt.arachn0de`, `0.3.0 / 9`. Incluye SQLCipher para arm64-v8a, armeabi-v7a, x86 y x86_64.
- Los cinco PNG del juego incluidos en el APK tienen bytes idénticos a `app/src/main/assets/game/`, incluido `araña.png`; la comprobación respeta la codificación real de los nombres ZIP.
- Estos artefactos debug no sustituyen el APK oficial de distribución ni prueban compatibilidad de actualización con su firma. No se solicitó ni utilizó la firma oficial.
