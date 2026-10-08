# Respaldo offline de Arachn0de

La extensión sigue siendo `.arachnode`. El esquema actual es Room 20 (migraciones 18→19 para tecnologías y 19→20 para encuadres); no cambia el protocolo de release.

## Contenedor v2 y datos v12

Todos los enteros del contenedor usan big endian. El encabezado conserva la estructura de v1:

- Firma ASCII `ARACHNODE\n` (10 bytes).
- Versión del contenedor: int32 `2`.
- Codificación: int32 `0` (JSON UTF-8).
- Longitud del JSON: int64 (1 a 16 MiB).
- SHA-256 del encabezado anterior y el JSON: 32 bytes.
- JSON de datos v12.
- Bytes originales de cada adjunto, en el orden de `attachmentFiles`, exactamente `byteSize` bytes por archivo. No se permite contenido adicional.

El JSON conserva los campos de v9, incluidos los avatares PNG en base64. Datos v10 añadió `attachmentFiles`, `nodeAttachments` y `projectAttachments`; datos v11 añade `technologies`, `nodeTechnologies`, `projectTechnologies` y `technologyIcons`. Cada archivo incluye su identidad, nombre portable de almacenamiento, nombre original, MIME, tamaño, dimensiones, SHA-256, fecha y estado. Las rutas del dispositivo nunca se serializan.

El lector sigue admitiendo contenedor v1 y datos v1–v11, con las conversiones anteriores. Las aplicaciones antiguas rechazan explícitamente el nuevo contenedor. Los nuevos respaldos necesitan esta implementación para restaurarse.

## Consistencia y recuperación

La exportación captura todas las tablas contempladas por el respaldo en una transacción. Comparte el bloqueo de operaciones físicas de adjuntos para impedir que importaciones o limpieza retiren archivos mientras se exportan. Solo incluye adjuntos confirmados de proyectos y nodos; las reservas de editores y los archivos pendientes de eliminación no forman parte de los datos confirmados. Una referencia ausente, un archivo inaccesible, un tamaño distinto o un SHA-256 incorrecto impiden crear un artefacto exitoso.

La inspección valida el encabezado, el JSON, identidades, jerarquías, relaciones y referencias en descripciones. Extrae adjuntos por streaming a una carpeta privada temporal, valida tamaño/hash y compara MIME/dimensiones con los metadatos. La restauración vuelve a validar antes de escribir datos.

La restauración copia los archivos a nombres nuevos en almacenamiento privado, sincroniza los archivos y directorios, y registra los nombres en diarios durables antes de sustituir registros dentro de una única transacción Room. Conserva los IDs usados en descripciones y asociaciones; cambia únicamente los nombres internos de almacenamiento. No sobrescribe archivos anteriores.

Si falla antes del commit, Room revierte y la limpieza elimina únicamente los nombres registrados que no están referenciados por la base de datos. Tras el commit elimina los archivos antiguos registrados. Si la limpieza falla, conserva el diario para reintentar en recuperación; una restauración ya confirmada no se informa como fallida por ese motivo. La recuperación también cubre interrupciones del proceso. Los archivos de inspección se eliminan al cancelar la confirmación o completar la restauración; los temporales abandonados y sin protección expiran tras 24 horas. Los contenedores pendientes de guardar conservan una marca durable `.keep` hasta guardar/cancelar; las inspecciones activas tampoco se barren. Las reservas de imágenes de editores protegen esos archivos durante la recuperación de diarios.

## Límites

Se mantienen los límites anteriores para JSON, avatares y registros. Los iconos de tecnologías comparten con los avatares el máximo agregado de 8 MiB; cada imagen PNG es de hasta 1 MiB y 512×512. Tecnologías y asociaciones cuentan para el máximo de 100.000 registros. Adjuntos: 20 MiB por archivo, 2 GiB en total. Actualmente Arachn0de permite adjuntos PNG/JPEG; este cambio respalda todos los tipos existentes y no añade tipos de importación nuevos. La operación necesita espacio temporal para el archivo de backup y, al restaurar, para los adjuntos inspeccionados y sus nuevas copias, además de los archivos antiguos hasta el commit.

## Archivos de esta implementación

Producción (rutas relativas a `app/src/main/java/com/r0ybt/arachn0de/`):

- `backup/BackupAttachmentFiles.kt` (nuevo): streaming, verificación física y diario de adjuntos.
- `backup/BackupAvatarFiles.kt`: reutilización de la sincronización durable existente.
- `backup/BackupContainer.kt`: escritura v2, lectura v1/v2 y verificación de los bytes.
- `backup/BackupData.kt`: metadatos, relaciones, límites y validación de referencias.
- `backup/BackupJson.kt`: datos v10 y lectura de las versiones anteriores.
- `backup/BackupRepository.kt`: captura consistente, exportación, inspección, staging, transacción y recuperación.
- `backup/BackupDocuments.kt`: verificación del nuevo contenedor antes de exportar mediante SAF.
- `data/local/AttachmentDao.kt`: consultas de respaldo y restauración; sin cambios de esquema.
- `data/repository/AttachmentRepository.kt`: acceso interno al mutex existente para coordinar operaciones físicas.
- `ui/BackupSection.kt`: número de adjuntos en la confirmación.
- `ui/state/BackupActions.kt`: errores de adjuntos y limpieza de candidatos temporales.

Pruebas (rutas relativas a `app/src/test/java/com/r0ybt/arachn0de/`):

- `backup/AttachmentBackupTest.kt` (nuevo): los casos dirigidos de adjuntos, rollback, cancelación y recuperación.
- `backup/BackupFormatTest.kt`, `backup/BackupUiTest.kt`: nueva versión y contenedor.
- `backup/CreationDefaultsBackupTest.kt`, `backup/CreationGroupBackupTest.kt`, `backup/ExplicitLayerBackupTest.kt`, `backup/NodeEventBackupTest.kt`, `backup/PriorityBackupTest.kt`, `backup/RecurrenceBackupTest.kt`, `backup/SprintBackupTest.kt`, `backup/TagsBackupTest.kt`: actualización de fixtures históricos para omitir campos de v10 y conservar pruebas de compatibilidad.
- `data/AttachmentRepositoryTest.kt`: reemplazo de la expectativa del bloqueo por captura de adjuntos confirmados.

Documentación: este archivo `BACKUP_FORMAT.md` (nuevo).


## Tecnologías: datos v11 y recuperación tras reinstalación

`technologies` exporta todos los registros, incluso los no asignados, con `id`, `name` e `iconFile` opcional. `nodeTechnologies` y `projectTechnologies` incluyen IDs de propietario y tecnología. `technologyIcons` contiene una entrada `{name, png}` por nombre de archivo utilizado, con PNG en base64. Varias tecnologías que referencian el mismo archivo no lo duplican dentro del respaldo. Las apariciones de una tecnología en varios elementos comparten siempre una única referencia.

Los iconos viven en `files/technology-icons`; el JSON solo contiene nombres portables. Su integridad se valida con los mismos controles PNG (firma, estructura, CRC, decodificación y dimensiones) de los avatares y con el SHA-256 del payload. Se exige que el conjunto de archivos exportados coincida exactamente con los iconos referenciados. Una referencia, icono o asociación ausente/inválida impide exportar o restaurar.

La restauración valida primero todos los PNG y las relaciones. Escribe iconos a nombres nuevos y durables, registra archivos nuevos/anteriores en `technology-icon-journal.json` y sustituye tablas junto con el resto de datos en una sola transacción. Conserva IDs y nombres; las asociaciones apuntan a los mismos IDs y el nombre interno del icono se reconstruye. La recuperación consulta `technologies.iconFile` después del commit/rollback para borrar únicamente los archivos registrados sin referencias. La biblioteca usa el mismo diario y bloqueo para editar/eliminar iconos.

Respaldos de datos v1–v10 restauran un catálogo vacío y relaciones vacías. Una instalación vacía no necesita archivos del dispositivo original: catálogo, iconos y relaciones se reconstruyen exclusivamente desde el backup. El archivo `.arachnode` debe conservarse fuera del almacenamiento privado de la aplicación antes de desinstalarla.

Archivos añadidos para tecnologías: `TechnologyEntities.kt`, `TechnologyDao.kt`, `TechnologyMigration18To19.kt`, `TechnologyIconStore.kt`, `TechnologyRepository.kt`, `TechnologiesScreen.kt`, `TechnologyComponents.kt`, `TechnologyActions.kt`, el esquema Room `19.json` y pruebas `TechnologyRepositoryTest.kt`, `TechnologyMigrationTest.kt`, `TechnologyBackupTest.kt`, `TechnologyUiTest.kt`. Se extienden `Arachn0deDatabase.kt`, `Arachn0deApplication.kt`, `AvatarStore.kt`, el backup existente y las pantallas/tarjetas para navegación y asignación. Los fixtures históricos y las expectativas del número de esquema actual se actualizan a datos v11/Room 19. El contrato permanente está en `ARCHITECTURE.md`, `AGENTS.md` y la lista complementaria de `RELEASING.md`.

### Inventario de la ampliación de tecnologías

Archivos de producción añadidos (base `app/src/main/java/com/r0ybt/arachn0de/`):

- `data/local/TechnologyEntities.kt`
- `data/local/TechnologyDao.kt`
- `data/local/TechnologyMigration18To19.kt`
- `data/local/TechnologyIconStore.kt`
- `data/repository/TechnologyRepository.kt`
- `ui/TechnologiesScreen.kt`
- `ui/TechnologyComponents.kt`
- `ui/state/TechnologyActions.kt`

Archivos de producción ampliados (misma base):

- `Arachn0deApplication.kt`
- `data/local/Arachn0deDatabase.kt`
- `data/local/AvatarStore.kt`
- `data/repository/NodeRepository.kt` y `domain/model/CreationUndo.kt`: Deshacer debe detectar tecnologías asignadas después de la creación.
- `backup/BackupAvatarFiles.kt`, `backup/BackupData.kt`, `backup/BackupJson.kt`, `backup/BackupRepository.kt`
- `ui/AppRoot.kt`, `ui/NavigationChrome.kt`, `ui/ProjectsScreen.kt`, `ui/ProjectScreen.kt`
- `ui/ProjectComponents.kt`, `ui/NodeComponents.kt`, `ui/BackupSection.kt`

Esquema añadido: `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/19.json`.

Documentación: `ARCHITECTURE.md`, `RELEASING.md`, `BACKUP_FORMAT.md` y `AGENTS.md`.

Pruebas nuevas: `data/TechnologyRepositoryTest.kt`, `data/TechnologyMigrationTest.kt`, `backup/TechnologyBackupTest.kt`, `ui/TechnologyUiTest.kt` (base `app/src/test/java/com/r0ybt/arachn0de/`). Los fixtures existentes del paquete `backup` omiten los campos de v11 cuando representan formatos antiguos y comprueban v11 en el formato actual. Las pruebas existentes que verificaban el número del esquema abierto se actualizan de 18 a 19, sin modificar sus escenarios.

### Validación dirigida

Se aprobaron 153 casos distintos entre ejecuciones dirigidas: backup (incluidos 9 casos nuevos de tecnologías y lectura genuina v10 con adjunto), catálogo/repositorio (8), migración 18→19 en SDK 24/28, migración anterior de adjuntos, interfaz de tecnologías (5), regresiones de Personas y tarjetas y protección de Deshacer. Se verificó recuperación desde una base vacía con los iconos privados originales eliminados, preservación de IDs/relaciones/bytes, archivos ausentes, PNG corruptos, referencias inválidas, límites, fsync fallido, rollback y recuperación de diarios.

`compileDebugKotlin`, la compilación incluida por las pruebas y `git diff --check` finalizaron correctamente. Las pruebas de imágenes e interfaz utilizan Robolectric con gráficos nativos en SDK 28; no sustituyen una comprobación manual en dispositivo físico. No se ejecutó la suite global ni se construyó/publicó un release.

## Encuadres de Personas: datos v12

Cada registro de `persons` contiene los nuevos números `avatarZoom` (1–5), `avatarX` y `avatarY` (-1–1). Son finitos; su significado y geometría están documentados en ARCHITECTURE.md. No se serializan rutas ni bitmaps recortados: `avatars` sigue incluyendo el PNG completo de cada fotografía privada referenciada, una sola vez por archivo compartido. Las miniaturas de caché no se exportan.

Respaldos v1–v11 reciben zoom 1 y desplazamiento 0. v12 exige los tres campos y rechaza tipos/rangos inválidos antes de sustituir datos. Solo estos campos permiten decimales; fechas, importes e identidades numéricas conservan su validación entera estricta. El SHA-256 del contenedor protege conjuntamente PNG y parámetros. La restauración conserva IDs y encuadres, prepara archivos nuevos y confirma todo en la misma transacción; un rollback conserva fotografías y parámetros anteriores. No cambian los límites ni el contenedor v2.

### Archivos del bloque de editor de avatares

Base de producción: `app/src/main/java/com/r0ybt/arachn0de/`.

Añadidos: `domain/model/AvatarFraming.kt`, `data/local/AvatarMigration19To20.kt`, `ui/AvatarEditor.kt` y `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/20.json`.

Ampliados: `domain/model/Person.kt`, `data/local/PersonEntity.kt`, `data/local/PersonDao.kt`, `data/local/Arachn0deDatabase.kt`, `data/repository/PersonRepository.kt`, `backup/BackupData.kt`, `backup/BackupJson.kt`, `ui/PeopleScreen.kt`, `ui/PersonComponents.kt`, `ui/state/PersonActions.kt`.

Documentación: ARCHITECTURE.md y BACKUP_FORMAT.md. No se modifica AGENTS.md ni RELEASING.md en este bloque: sus contratos y protocolo existentes siguen vigentes.

Pruebas añadidas (base `app/src/test/java/com/r0ybt/arachn0de/`): `data/AvatarFramingTest.kt`, `data/AvatarMigrationTest.kt`, `ui/AvatarEditorUiTest.kt`. Las expectativas de versión actual se actualizan a Room 20 y JSON 12. Los fixtures de versiones anteriores omiten los tres campos de encuadre; los snapshots de migraciones antiguas comparan las columnas anteriores de Personas y conservan sus escenarios.

Pruebas existentes actualizadas:

- `backup/AttachmentBackupTest.kt`
- `backup/BackupFormatTest.kt`
- `backup/BackupRepositoryTest.kt`
- `backup/CreationDefaultsBackupTest.kt`
- `backup/CreationGroupBackupTest.kt`
- `backup/ExplicitLayerBackupTest.kt`
- `backup/NodeEventBackupTest.kt`
- `backup/PriorityBackupTest.kt`
- `backup/RecurrenceBackupTest.kt`
- `backup/SprintBackupTest.kt`
- `backup/TagsBackupTest.kt`
- `backup/TechnologyBackupTest.kt`
- `data/AttachmentMigrationTest.kt`
- `data/AttentionRepositoryTest.kt`
- `data/CalendarFiltersRepositoryTest.kt`
- `data/CalendarRepositoryTest.kt`
- `data/CreationDefaultsMigrationTest.kt`
- `data/CreationGroupMigrationTest.kt`
- `data/ExplicitLayerMigrationTest.kt`
- `data/FinancialRepositoryTest.kt`
- `data/NodeBatchRepositoryTest.kt`
- `data/NodeEventMigrationTest.kt`
- `data/NodeEventRepositoryTest.kt`
- `data/NodeMigrationTest.kt`
- `data/NodePurposeTest.kt`
- `data/ObligationMigrationTest.kt`
- `data/ObligationRepositoryTest.kt`
- `data/PersonRepositoryTest.kt`
- `data/PriorityMigrationTest.kt`
- `data/RecurrenceMigrationTest.kt`
- `data/SprintMigrationTest.kt`
- `data/TagMigrationTest.kt`
- `data/TaskDatesRepositoryTest.kt`
- `data/TechnologyMigrationTest.kt`
- `export/NodeCopyUiTest.kt`
- `game/GameGameplayTest.kt`
- `report/ObligationReportUiTest.kt`
- `ui/theme/AppearancePreferencesTest.kt`

Validación del bloque: **159 ejecuciones dirigidas correctas**, incluidas 15 nuevas ejecuciones de encuadres/migración/editor. Cubren 100 Personas con avatar, persistencia al reabrir, parámetros compartidos en responsables, píxeles del renderer común, arrastre y continuidad geométrica, zoom accesible, centrado/restablecimiento, cancelación en ambas etapas, reemplazo, rollback, backup/restauración tras eliminar archivos privados originales, lectores históricos v1–v11 y limpieza. `assembleDebug` y `git diff --check` correctos. No se ejecuta la suite global ni se genera release. La respuesta de gestos y apariencia en el celular quedan para la prueba conjunta solicitada.
