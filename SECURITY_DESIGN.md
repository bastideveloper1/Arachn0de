# Sprint de seguridad — diseño previo y auditoría

Estado: diseño registrado antes de modificar almacenamiento; resultados y límites de validación en SECURITY_SPRINT.md.
Versión comercial conservada: 0.3.0 / 9. Se parte de Room 25 y JSON 17. Las preferencias privadas requieren Room 26 para participar en la misma transacción de restauración; JSON 18 añadirá sus namespaces y valores tipados. El cifrado no justifica por sí solo cambiar el esquema.

## Inventario encontrado antes de editar almacenamiento

- Room `Arachn0deDatabase`, migraciones 1→25, relaciones/triggers y todos los módulos de productividad, juego, Metro y Guardados.
- `files/avatars`, `technology-icons`, `project-photos`, `attachments` (también staging/leases), diarios JSON de restauración y `image-lifecycle`.
- SharedPreferences: apariencia y las fuentes históricas de ordenación y partida. Los datos vigentes de ordenación y juego están en Room.
- `cache/backups`: exportaciones `.arachnode`, inspecciones, marcas `.keep` y blobs originales temporales.
- `cache/obligation-reports`: informes PNG privados compartibles mediante FileProvider. Su proveedor debe respetar sesión y bloqueo.
- Caché de bitmaps en memoria: `PrivateImageCache`, más estado Compose/editor y referencias a repositorios.
- APK descargadas en `cache/updates`: artefactos públicos, no datos del usuario; excluidos del cifrado privado.
- Assets del juego y catálogo Metro: datos públicos inmutables, excluidos del cifrado.
- Acceso temprano: Application abre Room para recuperación, MainActivity materializa recurrencias al volver y MetroTrackingService puede reiniciarse. Deben quedar bajo la misma autorización que la UI.
- Exportación/restauración actualmente usa contenedor 2 con SHA-256: detecta corrupción accidental, no ofrece confidencialidad ni autenticación frente a un atacante.
- Rutas concretas revisadas: AvatarStore/TechnologyIconStore/ProjectPhotoStore, AttachmentStore/Viewer, BackupRepository/Container/AvatarFiles/AttachmentFiles/ImageFiles/Documents, ImageFileLifecycle, AppearancePreferences, ObligationReportFiles/ReportFileProvider, Application/MainActivity y MetroTrackingService.

## Decisiones

- AES-256-GCM mediante JCA, etiquetas de 128 bits. Envoltura de claves aleatorias independientes, nunca contraseña como clave de datos ni contraseña almacenada.
- Argon2id v1.3 de Bouncy Castle 1.86, 64 MiB, tres pasadas, un lane, salida 32 bytes, salt aleatorio 32 bytes. Núcleo Kotlin/JVM sin Android. No registrar proveedor global ni sustituir el proveedor Android.
- Cada almacén tendrá UUID independiente y material propio. Identidad, finalidad, versión y parámetros forman parte de la autenticación; contraseña diferente no basta para aislar rutas.
- Archivos/backups grandes: registros GCM autenticados de 64 KiB, subclave HKDF-SHA256 por objeto aleatorio, contador de nonce, registro final obligatorio y rechazo de bytes adicionales. No publicar datos/restaurar antes de la validación completa.
- Room: SQLCipher Android 4.19.1 de Zetetic y su SupportOpenHelperFactory; no construir cifrado SQLite propio. Cifrado de páginas e integridad según SQLCipher, diferente del GCM empleado para archivos.
- Migración: crear destino junto al origen, inventario/backup consistente verificable, copia controlada, comparación de filas/relaciones/archivos, reapertura, activación durable al final. Un destino incompleto jamás sustituye el origen. Eliminar únicamente elementos del inventario confirmado, bajo bloqueo común; explicar las limitaciones del borrado flash.
- Cambiar contraseña reenvelopa claves de datos, no recifra todos los archivos. Restauración portable con contraseña, sin dependencia exclusiva del Keystore.
- No prometer invisibilidad forense: directorios, UUID, tamaños, tiempos y número de almacenes siguen siendo metadatos observables con acceso al almacenamiento.
- Al bloquear: cancelar operaciones/scope, cerrar Room, retirar UI privada y referencias, vaciar cachés, detener servicio privado y borrar buffers de claves. No convertir el bloqueo en eliminación de datos.

## Fuentes primarias consultadas

- https://github.com/sqlcipher/sqlcipher-android/blob/master/README.md (Room 2, SupportOpenHelperFactory y artefacto mantenido)
- https://www.zetetic.net/sqlcipher/sqlcipher-for-android-migration/ (no reutilizar la biblioteca Android antigua)
- https://www.bouncycastle.org/download/bouncy-castle-java/ (biblioteca Java mantenida)
- https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html

## Alcance de validación

El cierre exige pruebas criptográficas, archivos, sesiones, migración, backups, volumen y regresión, además de compilar debug. La validación nativa de SQLCipher requiere Android real; compilar sus pruebas no equivale a ejecutarlas. Ningún paso parcial autoriza certificar la migración física.

Compatibilidad comprobada: 4.19.0 exigía compileSdk 37; 4.19.1 corrige su requisito a 36. Se selecciona 4.19.1, sin cambiar el SDK del proyecto. Fuente: https://github.com/sqlcipher/sqlcipher-android/releases/tag/v4.19.1

## Formatos del núcleo implementado

Todos los enteros son big-endian. Contraseñas: UTF-8 sin normalización Unicode; el usuario debe introducir la misma secuencia. Argon2id v0x13, m=65536 KiB, t=3, p=1, 32 bytes.

`ANKEY001`: magic ASCII de 8 bytes, UUID (dos int64), m/t/p (tres int32), salt de 32 bytes, nonce de 12 bytes, ciphertext de 32 bytes y tag GCM de 16 bytes. Cabecera de 80 bytes íntegramente AAD. Tamaño total 128 bytes. El UUID esperado debe coincidir; parámetros distintos se rechazan antes del KDF.

`ANENC001`: magic de 8 bytes + salt de objeto de 32 bytes. HKDF-SHA256 deriva una subclave de 32 bytes del DEK con info UTF-8 `stream-v1:<finalidad>` y bloque HKDF 0x01. Cada registro: longitud int32 (0..65536), ciphertext y tag de 16 bytes. Nonce: cuatro bytes cero + contador int64 empezando en cero. AAD: cabecera completa + finalidad UTF-8 + contador int64 + longitud int32. Registro vacío final obligatorio y EOF inmediato. Cada objeto tiene salt independiente; no se reutiliza contador bajo la misma subclave. No hay límite artificial de tamaño del documento; el contador no puede desbordar. Las longitudes tienen límites estructurales de bloque para impedir asignaciones arbitrarias.

`ANBACK01`: magic de 8 bytes, UUID de backup de 16 bytes, envoltura ANKEY001 de 128 bytes, stream ANENC001 con finalidad `backup-v1:<UUID>`. DEK y salt nuevos por exportación; nunca incluye DEK del almacén ni contraseña. Payload será el contenedor lógico anterior, conservando su validación interna.

Los streams autenticados validan también la cola al cerrar: un decodificador que deje de leer antes del EOF no puede ocultar una alteración o truncación al final. Los consumidores de backup deben validar completamente en staging antes de cualquier reemplazo.

Room 26 incorpora únicamente `private_preferences(name,payload)`. JSON 18 añade namespaces/valores tipados de esas preferencias. JSON 18 también incluye storeId y storeKind. Una fuente histórica sin preferencias ni identidad puede emitir JSON 17; los lectores admiten 1–18. La tabla se reemplaza en la misma transacción que el resto del backup. La tabla se cifra con la base SQLCipher; no se duplican valores privados en XML de Android.

## Preparación 0.3.0 / 10: validación nativa

SQLCipher ATTACH hereda el indicador CREATE de la conexión origen. La copia exige primero un archivo fuente existente y un destino inexistente, y abre con OPEN_READWRITE | CREATE_IF_NECESSARY para permitir crear el adjunto. Nunca cifra ni reemplaza el origen in situ. La clave derivada se transmite como 64 caracteres ASCII hexadecimales; otras representaciones se rechazan antes de abrir archivos para evitar sustituciones UTF-8 silenciosas. Se verifica la integridad y la huella tipada de la copia al reabrirla.

Las tres pruebas nativas pasaron en Android API 37, x86_64 con páginas de 16 KiB; incluyen respaldo de recuperación y reintento después de una interrupción antes de la activación. Esto verifica el motor nativo en emulador; no equivale a una prueba física en cada teléfono.
