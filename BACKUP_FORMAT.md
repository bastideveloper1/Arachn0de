# Respaldo offline de Arachn0de

## Datos personales de Personas — JSON 39 / Room 47

JSON 39 conserva `persons.name` como identidad pública (Apodo) y añade a cada
`personProfiles` las cadenas opcionales `givenName`, `familyName` y `documentId`
(Nombre, Apellidos y DNI / RUT). Se emite cuando hay Personas; los archivos sin
Personas conservan la selección histórica de versión. No cambia el contenedor
v2 ni la envoltura cifrada ANBACK01.

Los tres campos se incluyen en el snapshot transaccional, la estimación de bytes,
la exportación y la restauración genérica de perfiles. Se validan tipos,
propietarios, unicidad y límites antes de reemplazar datos. Un DNI / RUT es texto
opcional; no se infiere país ni se exige un formato nacional. Apellidos y documento
siguen el límite de 16.000 caracteres de las secciones. Nombre conserva la
capacidad histórica de `persons.name`, sin truncar nombres antiguos; siguen
vigentes los límites globales de tamaño del respaldo.

El lector admite versiones 1–39. En fuentes 1–38 conserva el antiguo Nombre
completo en `givenName`, sin separarlo automáticamente; inicializa Apellidos y
DNI / RUT vacíos. Utiliza el Apodo histórico no vacío como identidad pública o,
si falta, conserva el nombre visible anterior como Apodo hasta que se edite.
Genera la ficha personal correspondiente cuando una Persona antigua carecía
de ella. Valida los perfiles originales antes de transformarlos para no ocultar
duplicados ni referencias inválidas. Los lectores anteriores rechazan JSON 39.

La migración no destructiva Room 46→47 aplica la misma regla, conserva los IDs,
relaciones, fotografías y asignaciones, y añade únicamente tres columnas de texto
a `person_profiles`. Datos personales permanece dentro de la base cifrada de su
bóveda; no se copia a preferencias externas, índices públicos ni servicios.

La extensión sigue siendo `.arachnode`. El esquema actual es Room **47**, con la cadena histórica completa de migraciones. Las secciones siguientes conservan la documentación de los formatos anteriores. No cambia el protocolo de firma.

## Seguridad local: formato actual

Toda exportación desde un almacén protegido usa la envoltura autenticada **ANBACK01**, con su propia contraseña, UUID, salt y clave aleatoria. El payload conserva el contenedor v2 y sus manifiestos/SHA-256. Los backups antiguos sin cifrar se importan mediante la validación y confirmación existentes; nunca se exporta un backup nuevo sin contraseña desde un almacén protegido.

JSON **18** añade `privatePreferences` (namespaces y valores tipados), `storeId` (UUID portable o null para fuentes históricas) y `storeKind` (`primary`/`secondary`). No transporta contraseñas ni claves del almacén. Los lectores admiten JSON 1–29 y contenedores v1/v2. Una fuente histórica sin preferencias ni identidad puede conservar JSON 17. Todas las exportaciones de sesiones protegidas incluyen su identidad y emiten 18–29 según los datos presentes. Versiones antiguas rechazan explícitamente el nuevo formato.

Las preferencias se restauran en la tabla Room `private_preferences` dentro de la misma transacción que los demás datos. Las cachés de preferencias se recargan tras el commit. Room 25→26 solo añade esa tabla; conserva todas las anteriores. No se serializan rutas absolutas.

Una sesión principal acepta backups principales, incluidos los históricos; una sesión señuelo acepta exclusivamente backups señuelo. La comprobación sucede antes de mostrar metadatos o extraer multimedia. El UUID de origen se conserva en el archivo como procedencia; restaurar en otro dispositivo principal no requiere el mismo UUID local. Los backups nunca transfieren claves de autenticación ni crean automáticamente otro almacén.

La contraseña del backup se requiere al inspeccionar ANBACK01. Contraseña errónea, corrupción, truncación, registros cambiados de orden y contenido extra fallan antes de reemplazar datos. La inspección, los blobs, los diarios y los contenedores pendientes se almacenan cifrados con la clave de la sesión; SAF recibe únicamente el backup portable cifrado. SHA-256 por sí solo no se presenta como protección criptográfica.

Parámetros y especificación binaria: [SECURITY_DESIGN.md](SECURITY_DESIGN.md). No se añade un límite de capacidad al cifrado por streams; siguen vigentes los límites estructurales históricos de los formatos e importadores descritos abajo. La prueba de volumen contempla 100 Personas y 500 Tecnologías con imágenes.

Las secciones siguientes describen también los formatos históricos y sus migraciones.

## Dogfooding Sprint 2: JSON 19 y Room 27

Room 26→27 añade únicamente `node_sort_preferences.layersFirst`, booleano con valor inicial falso. `INHERIT` conserva el criterio heredado cuando solo se configura esta opción. El orden manual continúa en las posiciones de los nodos. No cambia claves ni identidades de almacenes.

JSON 19 añade `layersFirst` a las preferencias de orden y admite `INHERIT`. Se emite cuando hay fotografías de proyectos o estas preferencias nuevas; en los demás casos se conservan los formatos 17/18. Lectores históricos 1–18 inicializan la opción a falso. La validación estricta rechaza campos, valores o referencias inválidos antes de restaurar.

Las fotografías de proyectos admiten PNG de hasta 2048 píxeles por lado y 20 MiB por archivo; avatares e iconos mantienen 512 píxeles y 1 MiB. Los bytes y encuadres se incluyen íntegramente en los manifiestos y streams, conservando SHA-256 y el límite total histórico de 1 GiB. El formato inline conserva su límite agregado de 8 MiB: para colecciones grandes se utiliza el contenedor con streaming. No se exportan rutas absolutas.

`cache/project-thumbnails` contiene únicamente derivados regenerables cifrados; queda fuera del backup. Su limpieza limitada a nombres controlados no elimina fuentes ni reservas. Las fotografías confirmadas mantienen el diario, bloqueo, referencias compartidas y reservas existentes.

## Contenedor v2 y datos v16

Todos los enteros del contenedor usan big endian. El encabezado conserva la estructura de v1:

- Firma ASCII `ARACHNODE\n` (10 bytes).
- Versión del contenedor: int32 `2`.
- Codificación: int32 `0` (JSON UTF-8).
- Longitud del JSON: int64 (1 a 16 MiB).
- SHA-256 del encabezado anterior y el JSON: 32 bytes.
- JSON de datos v16.
- Bytes originales de cada adjunto, en el orden de `attachmentFiles`, exactamente `byteSize` bytes por archivo.
- Bytes PNG de `imageFiles`, en el orden del manifiesto, exactamente `byteSize` bytes por imagen. No se permite contenido adicional.

El JSON conserva los campos de v9, incluidos los avatares PNG en base64. Datos v10 añadió `attachmentFiles`, `nodeAttachments` y `projectAttachments`; datos v11 añade `technologies`, `nodeTechnologies`, `projectTechnologies` y `technologyIcons`. Cada archivo incluye su identidad, nombre portable de almacenamiento, nombre original, MIME, tamaño, dimensiones, SHA-256, fecha y estado. Las rutas del dispositivo nunca se serializan.

El lector sigue admitiendo contenedor v1 y datos v1–v15, con las conversiones anteriores. Las aplicaciones antiguas rechazan explícitamente los datos JSON 16. Los nuevos respaldos necesitan esta implementación para restaurarse.

## Consistencia y recuperación

La exportación captura todas las tablas contempladas por el respaldo en una transacción. Comparte el bloqueo de operaciones físicas de adjuntos para impedir que importaciones o limpieza retiren archivos mientras se exportan. Solo incluye adjuntos confirmados de proyectos y nodos; las reservas de editores y los archivos pendientes de eliminación no forman parte de los datos confirmados. Una referencia ausente, un archivo inaccesible, un tamaño distinto o un SHA-256 incorrecto impiden crear un artefacto exitoso.

La inspección valida el encabezado, el JSON, identidades, jerarquías, relaciones y referencias en descripciones. Extrae adjuntos por streaming a una carpeta privada temporal, valida tamaño/hash y compara MIME/dimensiones con los metadatos. La restauración vuelve a validar antes de escribir datos.

La restauración copia los archivos a nombres nuevos en almacenamiento privado, sincroniza los archivos y directorios, y registra los nombres en diarios durables antes de sustituir registros dentro de una única transacción Room. Conserva los IDs usados en descripciones y asociaciones; cambia únicamente los nombres internos de almacenamiento. No sobrescribe archivos anteriores.

Si falla antes del commit, Room revierte y la limpieza elimina únicamente los nombres registrados que no están referenciados por la base de datos. Tras el commit elimina los archivos antiguos registrados. Si la limpieza falla, conserva el diario para reintentar en recuperación; una restauración ya confirmada no se informa como fallida por ese motivo. La recuperación también cubre interrupciones del proceso. Los archivos de inspección se eliminan al cancelar la confirmación o completar la restauración; los temporales abandonados y sin protección expiran tras 24 horas. Los contenedores pendientes de guardar conservan una marca durable `.keep` hasta guardar/cancelar; las inspecciones activas tampoco se barren. Las reservas de imágenes de editores protegen esos archivos durante la recuperación de diarios.

## Límites

JSON: 16 MiB y 100.000 registros; se mantienen las validaciones estrictas de estructura, jerarquía y referencias. Los nuevos backups transportan las imágenes mediante streaming: máximo agregado de 1 GiB; cada PNG sigue limitado a 1 MiB y 512×512, con validación de contenido y SHA-256. El formato histórico con base64 y la captura diagnóstica `snapshot()` conservan el máximo agregado de 8 MiB. La exportación normal `create()` no usa esa captura en memoria. Tecnologías y asociaciones cuentan para el máximo de 100.000 registros. Adjuntos: 20 MiB por archivo, 2 GiB en total. Actualmente Arachn0de permite adjuntos PNG/JPEG; este cambio respalda todos los tipos existentes y no añade tipos de importación nuevos. La operación necesita espacio temporal para el archivo de backup y, al restaurar, para los adjuntos inspeccionados y sus nuevas copias, además de los archivos antiguos hasta el commit.

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

## Fotografías de proyectos: datos v13 / Room 21

La migración 20→21 agrega `project_photos` sin modificar registros existentes. Cada recurso tiene `id`, un propietario `projectId` o `nodeId`, `file` portable y `photoZoom`, `photoX`, `photoY`. La asociación a un nodo conserva el recurso oculto en el traslado existente hacia una capa; no se añade conversión nueva. Las filas sin propietario son registros operativos de limpieza pendiente y no se exportan.

`projectPhotos` incluye asociaciones confirmadas y `projectPhotoImages` incluye entradas `{name, png}` con bytes PNG en base64, una por archivo compartido. Se valida propietario único existente, nodo de propósito LAYER, identidades, encuadre, nombres portables y correspondencia exacta de bytes. Datos v1–v12 restauran fotografías vacías. El contenedor sigue en v2.

Las imágenes completas normalizadas se guardan en `files/project-photos`; el encuadre no recorta el original. Se reutilizan el límite de importación de 20 MiB, la normalización hasta 512 píxeles, los límites PNG y el máximo agregado de imágenes de 8 MiB. La caché privada compartida de miniaturas permanece acotada a 4 MiB y es regenerable, excluida del backup.

Edición, exportación, restauración y limpieza usan el bloqueo común. `project-photo-journal` registra staging y sustituciones durables; las reservas y marcas de limpieza viven en `image-lifecycle/project-photos`, sin caducidad por antigüedad. Solo se elimina un archivo después del commit y cuando carece de referencias y reservas. Cancelar libera exclusivamente las reservas del editor. Fallos de limpieza tras commit se reintentan al reiniciar y no convierten un guardado confirmado en fallo.

Inventario del Bloque 4A (base `app/src/main/java/com/r0ybt/arachn0de/`):

- Nuevos: `domain/model/ProjectPhoto.kt`; `data/local/ProjectPhotoEntity.kt`, `ProjectPhotoDao.kt`, `ProjectPhotoStore.kt`, `ProjectPhotoMigration20To21.kt`; `data/repository/ProjectPhotoRepository.kt`; `ui/ProjectPhotoComponents.kt`.
- Integración: `Arachn0deApplication.kt`; `domain/model/Project.kt`; `data/local/Arachn0deDatabase.kt`, `ProjectDao.kt`, `AvatarStore.kt`, `ImageFileLifecycle.kt`; `data/repository/ProjectRepository.kt`, `ProjectNestingRepository.kt`; `ui/AvatarEditor.kt`, `ProjectComponents.kt`, `ProjectsScreen.kt`, `ProjectScreen.kt`; `backup/BackupAvatarFiles.kt`, `BackupData.kt`, `BackupJson.kt`, `BackupRepository.kt`.
- Esquema: `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/21.json`.
- Pruebas nuevas: `data/ProjectPhotoRepositoryTest.kt`, `ProjectPhotoMigrationTest.kt`, `backup/ProjectPhotoBackupTest.kt`, `ui/ProjectPhotoUiTest.kt`; actualización de expectativas Room y fixtures JSON históricos existentes.
- Documentación: `ARCHITECTURE.md` y `BACKUP_FORMAT.md`. Sin cambios en el protocolo de `RELEASING.md`.

## Datos v14: conversión reversible y ordenación

Room 22 añade `conversion_roots`, `conversion_people`, `conversion_tags`, `conversion_events`, `conversion_work_states` y `node_sort_preferences`. El JSON incluye las correspondientes listas `conversionRoots`, `conversionPeople`, `conversionTags`, `conversionEvents`, `conversionWorkStates` y `nodeSortPreferences`. Cada raíz tiene exactamente un propietario confirmado: Proyecto o nodo LAYER. Las relaciones ocultas solo pertenecen a raíces que actualmente son Proyecto; los estados Sprint ocultos solo corresponden a tareas directas de ese Proyecto. La validación comprueba propietarios, identidades, estados, tipos y referencias antes de sustituir datos. Las versiones anteriores inicializan estas listas vacías.

Se conservan identidades preferidas para ambas representaciones, posiciones y propiedades exclusivas de la raíz. No se serializa una copia antigua del árbol: los descendientes, títulos y relaciones vigentes son los datos reales. Las fotografías ocultas permanecen en `projectPhotos`, con propietario nodo, encuadre y bytes incluidos. Las preferencias de ordenación migran de SharedPreferences a Room y desde esta versión participan de la captura transaccional y la restauración.

`imageFiles` contiene únicamente directorio lógico (`avatars`, `technology-icons` o `project-photos`), nombre UUID portable, tamaño y SHA-256. No contiene rutas del dispositivo. Los bytes siguen a los adjuntos en el contenedor v2; el encabezado no cambia. Las aplicaciones anteriores rechazan JSON 14. El lector actual conserva soporte para JSON 1–13 e imágenes históricas en base64. La inspección extrae y verifica una imagen cada vez; la restauración usa el staging, diarios, reservas y bloqueo común existentes. Se requiere espacio para contenedor, inspección y copias nuevas además de los archivos anteriores hasta el commit.

Producción nueva: `data/local/ConversionEntities.kt`, `data/local/ConversionMigration21To22.kt`, `data/repository/NodeSortPreferenceRepository.kt` y `backup/BackupImageFiles.kt`. Se amplían `ProjectNestingRepository`, `ProjectRepository`, `NodeRepository`, `Arachn0deDatabase`, `BackupData`, `BackupJson`, `BackupContainer` y `BackupRepository`. Pruebas nuevas: `ProjectConversionTest`, `ProjectConversionMigrationTest`, `ProjectConversionBackupTest`, `ProjectConversionUiTest` y fixture `ConversionFixture`.


## Juego offline: datos v15, Room 23

`gameSession` es null cuando no hay partida; en otro caso contiene el JSON completo y validado de `GameSessionCodec` v3 (lector de partidas v1/v2/v3). Incluye mapa y semilla, terrenos y paredes, jugadores y dificultades, posiciones e historial del recorrido, objetos/habilidades particulares, pantanos, araña y combate, exploración individual y Ojo, fases pendientes, movimiento y vencimiento del resultado. Una partida antigua conserva el tablero original; no se regenera desde su semilla.

El payload de partida está limitado a 256 KiB, con tipos estrictos, sin claves duplicadas ni referencias rotas; la estructura del mapa generado debe ser contigua, conectada y sin ciclos. Cuenta como un registro y su texto cuenta para el límite de metadatos. Una partida corrupta impide exportar o restaurar antes de reemplazar datos. La captura de `game_state` y productividad es una transacción; su restauración comparte la misma transacción de sustitución y revierte ambos ante fallos o cancelación. La revisión CAS local se incrementa, no se importa desde el respaldo, para rechazar acciones antiguas.

Los backups v1–v14 producen ausencia de partida: se escribe una fila vacía durable que impide resucitar la antigua preferencia `experimental_game/session`. La migración 22→23 importa esa preferencia válida una sola vez. Una preferencia corrupta no es jugable; su texto original se conserva en la preferencia histórica. La partida confirmada vigente reside exclusivamente en Room.

Los PNG del tablero son recursos estáticos empaquetados en el APK y no se serializan como archivos personales. La carga mantiene como máximo cinco bitmaps de ≤512×512 en memoria, sin caché persistente ni archivos de juego que liberar. No se añaden exclusiones de archivos privados ni rutas absolutas.


## Metro de Santiago Beta 1: datos v16, Room 24

La migración aditiva 23→24 crea `metro_preferences` y `metro_journeys`, sin modificar tablas existentes. Las preferencias contienen una copia íntegra y versionada del catálogo offline, Casa, favoritos, cierres de pasajeros, preferencias de evitar e interrupciones físicas. Guardar el catálogo con los datos permite restaurar rutas históricas sin reinterpretarlas según un catálogo posterior.

El manifiesto incorpora `metroPreferences` (payload JSON nullable) y `metroJourneys` (id, nodeId nullable, personId nullable, enabled, payload, revision). Una asociación por tarea; las referencias Room a tareas y Personas usan SET NULL al eliminar el original y permiten conservar el viaje de forma independiente. No se copian avatares: sus bytes y encuadres se exportan mediante el mecanismo existente de Personas.

Cada payload de viaje incluye el plan completo y sesiones con ID, ruta activa y original, Persona viajera, anclas de reloj civil/monotónico y arranque, inicio, pausa, tiempo pausado, confirmaciones, correcciones, cambios de recorrido y finalización explícita. Las estadísticas se derivan de estos valores; no son datos ficticios ni modifican el coste global por tramo.

Validación previa: UTF-8 acotado a 1 MiB por payload, estructura/profundidad/listas/números y duplicación de claves, catálogo coherente, identificadores, segmentos adyacentes, dirección/servicio, combinaciones y paradas, referencias a tareas y Personas, revisiones no negativas y una sola sesión activa. Las exportaciones capturan todo en la transacción existente. La restauración valida antes de sustituir y usa la misma transacción y protocolo durable de archivos; cualquier fallo conserva los datos anteriores. Sube las revisiones para rechazar acciones antiguas de notificaciones y marca las sesiones importadas como inciertas: el usuario debe confirmar su posición antes de retomar la estimación. Se conservan las pausas y el historial. Las duraciones recuperadas usan reloj civil cuando las referencias monotónicas proceden de una restauración o de otro arranque; se muestra esta limitación y no se interpreta el contador monotónico de otro dispositivo como duración real.

Los lectores históricos v1–v15 producen preferencias y viajes Metro vacíos. No hay nuevos archivos privados, reservas ni copias multimedia que liberar. Eliminar un viaje inactivo elimina sus datos e historial Metro; volver a modo normal conserva esos datos y todas las propiedades de la tarea. No se permite eliminar o desactivar un viaje mientras tenga seguimiento activo.


### Metro 1: administración independiente, sin cambio de formato

Se reutilizan los viajes independientes y vínculos opcionales existentes. Vincular o desvincular conserva el payload íntegro y cambia solo la referencia y su revisión dentro de una transacción. Duplicar crea otro ID con el mismo plan y viajero, sin copiar sesiones ni historial. Eliminar una tarea conserva el viaje con SET NULL; borrar el viaje requiere confirmación y no borra su tarea. No se añade migración ni versión JSON: Room 36 / máximo JSON 28 permanecen iguales. Los lectores, exportación completa y restauración portable existentes sirven para ambas modalidades. Ver METRO_INDEPENDENCE.md.

### Revisiones del catálogo Metro original y r1

La corrección de referencia r1 cambia únicamente Rodrigo de Araya (R), Carlos Valdovinos (V), Camino Agrícola (R) y San Joaquín (C) en L5. No modifica el snapshot persistido ni el formato v16. `MetroCatalogRevision` reconoce ambas definiciones completas por su versión y huella de estaciones/líneas y resuelve la versión específica de cada ruta. Los planes nuevos usan r1; el historial y las sesiones originales conservan su definición. La regla determinista debe permanecer disponible para restaurar backups con ambas revisiones sin consultar Internet. Las revisiones o huellas desconocidas no se convierten por conjetura. La Beta anterior puede rechazar una ruta r1, pues no conoce esa revisión. La prueba dirigida restaura un backup mixto en una base vacía y comprueba conservación de rutas, pausa, eventos y preferencias. Detalles en METRO_MASTER_VALIDATION.md.

## Guardados y orden de Tecnologías: datos v17 / Room 25

La migración aditiva 24→25 añade `position` a `node_technologies` y `project_technologies`, y crea `saved_templates`. El JSON v17 exige posición entera no negativa en las asociaciones y `savedTemplates` con `{id,name,payload}`. El contenedor continúa en v2. Los lectores de datos 1–16 producen biblioteca vacía y reconstruyen el orden de asignaciones que mostraba el catálogo anterior (nombre con NOCASE ASCII e ID).

El payload de plantilla v1 tiene estructura estricta y acotada; contiene configuración reutilizable, reglas relativas de fechas/horas, referencias de Personas/Tecnologías/Etiquetas y, opcionalmente, catálogo y plan Metro sin sesiones. Las referencias de entidades eliminadas se conservan como configuración y se informan/omiten al aplicar; nunca se insertan relaciones inválidas. No se copian identidad de tarea, estados completados, eventos, historial de pagos, progreso, sesiones ni reglas recurrentes. Se rechazan identidades de plantilla repetidas, listas duplicadas, tipos/rangos inválidos, claves JSON duplicadas y planes con sesiones. Captura/restauración usa la transacción existente y valida antes de sustituir; el rollback conserva la biblioteca anterior.

Las plantillas no poseen archivos ni rutas del dispositivo: las imágenes de Personas y Tecnologías siguen perteneciendo a sus catálogos y participan del backup existente. Los adjuntos de descripción se excluyen expresamente al guardar como plantilla; no se crean referencias a bytes ausentes. Edición/eliminación de una plantilla libera solo su fila, sin borrar medios de otras entidades. Detalles del sprint y validación en SPRINT_PRE_RELEASE.md.

### Rediseño Metro: avance automático y payload de viaje v2

Room permanece en **25**, JSON de backup en **17** y contenedor en **2**. No se añaden tablas, archivos ni preferencias. El payload interno de viaje pasa a **v2** y exige `automatic` booleano por sesión. Los viajes nuevos avanzan automáticamente por los tramos de 2 minutos, combinaciones/cambios de 4 minutos y paradas intermedias sin duración de permanencia. Alcanzar el destino no finaliza la sesión ni completa la tarea. Pausa, correcciones, historial y recuperación incierta se conservan.

El lector admite payloads de viaje v1 y v2: las sesiones v1 reciben `automatic=false`, conservando su comportamiento histórico hasta que el usuario pulse «Activar avance automático». La activación conserva anclas, pausa, historial e incertidumbre. Las preferencias Metro siguen usando payload v1. Los lectores de la Beta anterior rechazan el payload de viaje v2; no se promete restaurar backups nuevos con aplicaciones anteriores.

El snapshot y la restauración existentes incluyen el booleano dentro del payload íntegro; restaurar sigue marcando sesiones activas como inciertas antes de permitir su avance. Se verifican round-trip, compatibilidad v1, tipos/campos/versiones inválidos, preservación de sesión activa al editar el plan de una tarea y rechazo de un backup corrupto sin sustituir datos. Los borradores y plantillas contienen solo planes, nunca sesiones ni historiales; no añaden archivos al ciclo de limpieza.

### Cierre Metro: asociación y edición desde el formulario normal

Se reutiliza `metro_journeys.nodeId`: un viaje independiente se vincula a la nueva tarjeta sin copiarlo, conservando ID, modo activo/desactivado, sesiones, eventos, pausas y rutas históricas. Un viaje de otra tarjeta no se traslada ni duplica implícitamente. La revisión guardada en el borrador se comprueba dentro de la misma transacción de creación/edición; un vínculo concurrente, viaje eliminado, revisión obsoleta o plan incompleto falla antes de dejar una tarjeta parcial.

El formulario edita solo configuración; las sesiones permanecen en el repositorio. Cambiar el plan no reemplaza una sesión activa. Cambiar la Persona viajera actualiza su referencia activa mediante la misma lógica de persistencia, sin copiar archivos. Un guardado de metadatos conserva un plan histórico aunque el catálogo o las restricciones actuales lo afecten. El backup v17 y payload Metro v2 ya incluyen los vínculos y todo su contenido: no hay migración, tabla ni archivo nuevo. Prueba dirigida verifica exportación/restauración con nueva asociación en Subcapa y una sesión activa, además de rollback ante conflictos.

El estado temporal del editor añade un prefijo opcional `__metro_link_v1` con identidad/revisión y baseline Metro. El lector conserva los formatos anteriores, sin vínculo por defecto. Este estado no es dato confirmado ni se exporta a backups; al aplicar Guardados se limpia la referencia al viaje original y se conserva solo el plan reutilizable.

## Dogfooding 1: orden de asignaciones

La cuadrícula y reordenación reutilizan `position` en `nodeTechnologies` y `projectTechnologies`. El orden es propio de cada dueño y no cambia la biblioteca. No se incrementa JSON 18 ni Room 26: el formato ya transporta posiciones, asociaciones, metadatos y bytes de iconos. La prueba de restauración de Tecnologías comprueba explícitamente orden inverso en un proyecto y una tarea, además de integridad de iconos y compatibilidad histórica. Los cambios visuales no crean archivos ni modifican su liberación. Se mantiene ANBACK01, la separación principal/señuelo y la validación antes de sustituir datos.

## Dogfooding Sprint 4A: seguimiento Metro por etapas

El payload de `metro_journeys` pasa a **v3**; Room continúa en 27 y el formato exterior de backup conserva sus versiones 17/18/19 según los datos presentes. No se añaden tablas, columnas, preferencias ni archivos. Los lectores admiten payloads Metro v1–v3: los históricos conservan sus sesiones y derivan la etapa desde su offset, sin reescribir filas al consultar. Aplicaciones anteriores pueden rechazar payloads v3.

Cada sesión transporta `control` (nulo para sesiones históricas), con etapa inicial, ancla temporal, pausas base, estado RIDING/ARRIVED/TRANSFERRING, tiempo detenido por una llegada deshecha, inicio del transbordo, duraciones confirmadas por etapa y última llegada reversible. `undo` incluye la sesión anterior completa sin otro undo anidado, la hora de llegada y su reversibilidad. Se validan tipos, campos, rangos, coherencia con la ruta, identidad y anclas originales de la sesión anterior antes de reemplazar datos. Los eventos nuevos también forman parte del payload íntegro.

La restauración conserva IDs, asociaciones, pausas, duraciones y recuperación de llegada. Marca como no confiables los relojes locales de control y del snapshot de undo; las sesiones activas quedan inciertas como en el protocolo anterior. Un embarque confirmado crea una nueva ancla local. No se inventan duraciones históricas inexistentes. Los indicadores y vistas previas se derivan del almacén abierto y no crean datos persistentes ni archivos que liberar. El rechazo de un payload corrupto mantiene el estado anterior.

## Dogfooding Sprint 4B: cambios de destino y archivo de recorridos

El payload Metro pasa a **v4**, con lector compatible con v1–v3. Room permanece en 27 y el backup exterior conserva 17/18/19. Cada sesión añade `routeArchives`: instantáneas de los recorridos sustituidos, con ruta, offsets, estaciones confirmadas, eventos, pausas, controles y duraciones capturadas. Son sesiones con la misma identidad y origen original, sin otros archivos de recorridos anidados ni undo propio. Se conserva también `routeHistory` para compatibilidad con consultas existentes. La ruta vigente y el plan se actualizan en la misma transacción y revisión, manteniendo el ID, origen original, inicio y relaciones.

`READY` representa una ruta recalculada esperando embarque: offset cero, estación confirmada igual al origen de esa ruta y ningún avance ferroviario hasta la acción explícita. Si el nuevo destino coincide con la ubicación elegida, el plan sin tramos requiere confirmar Llegué para finalizar. Las pausas se mantienen. Un transbordo en curso conserva su tiempo previo en el archivo y continúa su intervalo pendiente hasta embarcar; las dos duraciones se conservan separadas.

La selección, ubicación elegida y ruta propuesta son borradores de UI; no se exportan porque no son datos confirmados. Cancelar no escribe. Una ruta imposible o una revisión obsoleta falla antes del commit. Se validan los archivos de recorridos usando el mismo codec estricto y catálogo histórico, rechazando identidades ajenas y anidamiento recursivo. Restaurar aplica a sus relojes el mismo tratamiento portable que al control y undo actuales. No se crean archivos privados nuevos ni cambian reservas, limpieza o cifrado.

Las aplicaciones con lector hasta v3 rechazarán los payloads v4; no se promete compatibilidad de lectura hacia versiones anteriores de la aplicación. Los backups históricos sí son admitidos sin reescribir filas por consultar.

## Dogfooding Sprint 4C: escenarios de servicio Metro v5

El payload interno admite v1–v5 y emite v5 si algún plan/ruta original/historial contiene `departure`, instante civil del escenario horario de referencia en milisegundos Unix. Es opcional y ausente en rutas manuales, que conservan v4. Las fechas son enteros no negativos sin desbordamiento del recorrido; no se guardan cadenas locales ambiguas. Se preservan también dentro de archivos de recorridos y undo. v5 admite servicios normales y expresos en distintas etapas según llegada. Se valida continuidad, geometría, colores y referencia horaria antes de sustituir; v1–v4 rechazan datos programados. La referencia implementada sigue siendo histórica, sin garantía de operación vigente, festivos ni excepciones.

Room permanece en 27 y el contenedor/JSON exterior en los formatos existentes 17/18/19. No hay nuevos archivos, tablas, claves ni limpieza. La exportación incluye todo el payload confirmado y la restauración aplica las mismas reglas de posición incierta/relojes portables, conservando identidad, asociaciones, pausas y datos anteriores ante rechazo. Los borradores de regreso sin guardar son temporales; al guardar se crea un viaje independiente sin copiar el vínculo de ida. Aplicaciones con lector hasta v4 pueden rechazar los escenarios v5. Las rutas históricas manuales siguen siendo legibles sin reescribir la fila por consultar.

## Carrera táctica: payload de juego v4

El juego jugable sustituye la batalla anterior por una carrera. Se reutiliza `game_state` y el campo `gameSession`, sin cambiar Room ni el contenedor. `RaceCodec` escribe el payload **v4**, limitado a 256 KiB; el validador de backups acepta también `GameSessionCodec` v1–v3. Las partidas antiguas se conservan exportables/restaurables, pero no se ofrecen como otro modo de juego. Iniciar una nueva carrera requiere confirmar la sustitución del único guardado. Una aplicación anterior que solo admite partidas v1–v3 rechaza una carrera v4; no puede restaurarla parcialmente.

V4 guarda el circuito completo y su progreso normalizado, cajas estables, configuración de balance, vueltas, diez identidades y controles, casillas e historial, inventarios con turno de disponibilidad, recogidas por vuelta, estados de protección/invisibilidad/aturdimiento, ronda y turno, PRNG, dado y pasos pendientes, premio pendiente, trampas con expiración, dinamitas y vigilancias con propietario, turno de resolución y zonas exactas fijadas, uso/coste del objeto, segunda oportunidad, contador independiente de impactos compartidos y ganador. No se regenera el circuito ni se vuelve a sortear el premio al restaurar.

Se validan límites, tipos JSON sin coerción, claves duplicadas, topología y progreso canónicos de v4, referencias, historial conectado, fases, inventarios y zonas realizables antes de guardar/exportar/restaurar. La revisión CAS local se incrementa después de restaurar; callbacks anteriores no pueden consumir pasos ni objetos. El snapshot y la restauración siguen compartiendo transacciones con productividad y conservan los datos anteriores ante corrupción, cancelación o fallo SQL.

Las veinte imágenes originales son recursos estáticos del APK, sin archivos privados ni rutas de dispositivo en el backup. Se conservan íntegros sus bytes y proporciones. El cargador mantiene como máximo veinte bitmaps muestreados de hasta 256 px por lado, liberados con la composición, sin caché persistente. No introduce nuevas reservas ni limpieza de archivos privados. Las cinco texturas históricas siguen empaquetadas para conservar los recursos existentes.

## Bloque 3: aviso previo Metro opcional

El payload de `metro_preferences` pasa a v2 y añade `vibration` booleano obligatorio. El lector conserva v1 con `vibration=false`; rechaza tipos/campos inválidos. No cambian Room, contenedor ni versión exterior del backup: se transporta el payload completo con la transacción y validaciones existentes. Las aplicaciones anteriores pueden rechazar este nuevo payload; se conserva lectura de sus backups en la aplicación actual.

El aviso usa el tipo histórico `PASS_<índice>` dentro del historial de sesión como marca durable de aviso emitido para el tramo previo a la combinación/destino. Su propósito es impedir vibraciones repetidas tras reabrir; no confirma ubicación ni llegada. Se conserva con viajes y backups, sin tablas ni archivos nuevos. El marcador se confirma antes de pedir vibración; si el dispositivo rechaza o interrumpe la vibración, no se repite automáticamente. Los Guardados siguen copiando solo planes sin sesiones ni marcas de avisos.

Prueba dirigida: preferencia y marcador tras reapertura/restauración, default histórico v1, tipo inválido, supresión por pausa/incertidumbre y confirmación sin cambios por un aviso.

## Bloque 4: Favoritos, recientes y destino de captura

La fila opcional `element_access` de `private_preferences` utiliza el serializador tipado existente de `EncryptedPreferences`. Su payload v1 contiene exactamente: `version` entero 1, `favorites` conjunto de strings, `recent` string de claves separadas por salto de línea, `counts` string de pares clave=entero separados por salto de línea y `destination` string (vacío significa sin destino). Las claves son `project:<ID>` o `node:<ID>` de entidades originales, nunca rutas absolutas.

El codec valida tipos, campos, versión, claves, entradas recientes únicas (hasta 20), contadores únicos (hasta 200, valores 1–1000000) y destino. El validador de backup ejecuta este codec antes de reemplazar datos. La restauración transporta todos estos valores con las transacciones existentes; no necesita medios ni staging de archivos adicionales. Al leer se depuran referencias a entidades ausentes. Backups históricos sin esta fila producen accesos vacíos; no cambia la versión lógica ni exterior del contenedor ni el esquema Room. Las aplicaciones históricas conservan el transporte genérico de preferencias privadas, aunque no ofrecen esta interfaz.

Pruebas dirigidas: favoritos después de cerrar/reabrir, ruta tras movimiento, limpieza al eliminar, restauración de favoritos y destino, rechazo de payload inválido conservando datos anteriores y aislamiento entre almacenes principal/señuelo.

## Avatares de Personas de mayor resolución

Las nuevas importaciones conservan una fuente PNG orientada de hasta 2048×2048 y 20 MiB, sin recortar el encuadre. Zoom y desplazamiento siguen independientes; ajustar o reimportar conserva la ficha y permite revisar el encuadre existente. No se reescriben imágenes antiguas. Tecnologías conservan 512 px / 1 MiB. No hay cambios de Room, estructura o versión del respaldo: se amplía únicamente la validación admitida de avatares. Los respaldos antiguos válidos siguen admitidos; aplicaciones anteriores pueden rechazar avatares nuevos que superen sus límites de 512 px / 1 MiB.

El respaldo por streaming conserva los bytes completos, con límite agregado existente de 1 GiB. El snapshot en memoria mantiene 8 MiB y debe usarse streaming para colecciones mayores. Miniaturas escaladas según densidad y zoom (96/192/384/512 px) comparten la caché LRU de 4 MiB, regenerable y excluida del respaldo. El editor carga solo la imagen seleccionada, acotada a 2048 px. Las reservas, bloqueo común, diarios y limpieza tras la última referencia se reutilizan; no se añaden archivos privados auxiliares ni se caducan reservas.

### Horarios informativos de Metro

El namespace privado opcional `metro_hours` contiene únicamente `hours` de tipo string: seis minutos separados por comas, en orden apertura/cierre de lunes a viernes, sábado y domingo/festivo. Cada minuto debe estar entre 0 y 1439 y cada apertura debe ser anterior al cierre del mismo día. Se valida antes de restaurar. Su ausencia equivale a los valores oficiales de referencia. Usa `privatePreferences` existente, sin cambios de versión, esquema o envoltura. Las preferencias pertenecen exclusivamente al almacén exportado y las cachés se recargan mediante el mecanismo actual después del commit.

## Bloque 8: vínculos externos Sprint–Tarea

Room 27→28 añade únicamente `sprint_links`, con `taskId` único y referencias a nodos existentes. No mueve tareas, reescribe filas históricas ni crea un segundo estado: `Node.workState` sirve para tareas directas o vinculadas.

JSON **20** añade `sprintLinks` (`taskId`, `sprintId`) cuando hay vínculos. Sin ellos se conserva la selección histórica 17/18/19. El lector acepta 1–20; los respaldos anteriores inicializan los vínculos vacíos. Aplicaciones anteriores no pueden restaurar JSON 20: lo rechazan por versión, sin degradar ni omitir relaciones. El contenedor, cifrado e identidad/aislamiento de sesiones se conservan.

La validación previa exige identificadores existentes, tarea ACTION y capa Sprint activa en el mismo Proyecto, una sola pertenencia Sprint y ausencia de duplicados. La elegibilidad de `workState` coincide con las pertenencias directas o externas. Snapshot y restauración incluyen los vínculos en la misma transacción que los nodos; un fallo conserva el estado anterior. No hay archivos nuevos ni cachés persistentes.

## Bloque 9: identidad y modalidad de gestión

Room 28→29 añade `management_contexts`: `id` referencia la raíz estable de `conversion_roots`, `enabled` indica gestión activa y `methodology` admite AGILE/TRADITIONAL/HYBRID. No crea contextos durante la migración: los Proyectos existentes siguen en modalidad Normal. No se duplican títulos, descripciones ni árboles. Desactivar conserva identidad y configuración. La eliminación definitiva del propietario elimina su raíz y contexto por FK; las conversiones los conservan transaccionalmente.

JSON **21** añade `managementContexts` (`id`, `enabled`, `methodology`) si hay contextos, incluidos los desactivados. Sin contextos se conserva la selección anterior 17–20. Se mantienen lectores 1–21, contenedores y cifrado. Los respaldos históricos generan una lista vacía y modalidad Normal. Lectores anteriores rechazan JSON 21; no se omiten los contextos para simular compatibilidad inversa.

Snapshot y restauración incluyen raíces y contextos en la misma transacción. Se validan identidades únicas, metodologías, tipos y referencias a raíces con propietario válido antes de sustituir datos. Las raíces se insertan antes que los contextos; un fallo revierte ambos. No se añaden archivos ni preferencias globales.


## Bloque 10: acta, objetivos y aceptación

Room **29→30** añade `project_charters` (una acta por contexto), `management_objectives` (ID estable, contexto, título, descripción y posición) y `acceptance_criteria` (ID estable, objetivo, descripción, posición, validación y fecha). Las claves foráneas en cascada impiden datos huérfanos. La migración es aditiva: no modifica datos anteriores ni crea actas u objetivos automáticamente. Las conversiones capturan y restituyen estos registros junto con el contexto dentro de la misma transacción; desactivar gestión los conserva.

JSON **22** añade `projectCharters`, `managementObjectives` y `acceptanceCriteria` cuando hay cualquiera de estos registros, incluso de gestión desactivada. Sin planificación se mantiene la selección previa 17–21. El lector admite 1–22; los formatos 1–21 generan listas vacías. Versiones anteriores **no pueden importar JSON 22**; no se eliminan datos ni se rebaja el formato para simular compatibilidad inversa. Room 30 tampoco tiene migración inversa: regresar a una APK antigua no es una actualización compatible.

Antes de restaurar se validan IDs únicos, propietarios existentes, texto obligatorio no vacío, posiciones no negativas y únicas dentro de su propietario y correspondencia entre validación y fecha (entero Long no negativo, nulo al revocar). Los órdenes pueden tener huecos, sin cambiar la identidad. Acta parcial y contexto desactivado son válidos. Se incluyen los tres conjuntos en los límites existentes de registros y metadatos. Las inserciones siguen contexto → acta/objetivo → criterio dentro de la transacción de restauración; un fallo conserva el estado anterior. No se añaden archivos, rutas, preferencias globales ni cambios al contenedor, cifrado o aislamiento de almacenes.

El título y la descripción del Proyecto/Capa siguen en su propietario actual: el acta no los duplica. La aceptación se calcula desde los criterios, nunca se exporta como porcentaje o estado adicional. Un objetivo sin criterios no está aceptado; completar tareas o fases Sprint no valida criterios.


## Bloque 11: roles, relaciones y Backlog

Room **30→31** añade cinco tablas: `management_roles` (PK nodo, contexto y EPIC/STORY), `objective_epics` (PK objetivo/épica), `epic_stories` (PK épica/historia), `story_tasks` (PK historia/tarea) y `backlog_entries` (PK nodo, contexto y posición). Las FK en cascada, índices y triggers validan propietarios, tipos de roles y nodo y pertenencia al contexto. No se copian títulos, descripciones, estados, medios ni árboles.

JSON **23** añade `managementRoles`, `objectiveEpics`, `epicStories`, `storyTasks` y `backlogEntries` cuando existe cualquiera de sus registros; incluye también los conjuntos de JSON 20–22. Sin estos datos conserva la selección anterior de versión. El lector mantiene JSON 1–23; históricos 1–22 producen los cinco conjuntos vacíos. **Lectores anteriores no pueden importar JSON 23; Room 31 no tiene downgrade.** Contenedor, cifrado, identidad y aislamiento de almacenes siguen iguales.

La validación previa exige IDs y referencias válidos, relaciones únicas, roles EPIC/STORY sobre LAYER, tareas ACTION y el mismo contexto persistente más cercano. Un contexto anidado, aunque desactivado, delimita su propio contenido. Solo hay aristas objetivo → épica → historia → tarea; los tipos excluyen ciclos sin imponer una forma al árbol. Posiciones del Backlog no negativas y únicas por contexto, con una sola entrada por nodo. Las nuevas filas y cadenas se incluyen en los límites anteriores de registros y metadatos.

Snapshot y restauración incluyen todo dentro de la transacción existente, insertando contextos/objetivos/nodos antes de roles, vínculos y entradas. Las conversiones capturan estos cinco conjuntos y validan su pertenencia sobre el árbol resultante antes de restituirlos y confirmar; los fallos conservan el estado anterior. No se añade almacenamiento de archivos ni caché persistente. Desactivar conserva roles, vínculos y orden.

Retirar/cambiar un rol exige reconocer la pérdida de sus vínculos y de su entrada de historia; no cambia nodos, descendientes o Sprint y las entradas de tareas siguen disponibles. Desvincular relaciones o retirar entradas se confirma en la UI; retirar una entrada no elimina vínculos. Eliminar un objetivo ya confirmado elimina sus vínculos con épicas, además de sus criterios. Movimientos, conversiones y eliminaciones de nodos que perderían relaciones se rechazan hasta retirarlas explícitamente. Véase `BACKLOG.md`.

## Bloque 12: participantes gestionados — JSON 24, Room 32

Room 31→32 es aditiva: añade `management_participants`, `participant_roles`,
`objective_responsibilities` y `node_responsibilities`. Sus claves compuestas
identifican contexto y Persona, y elemento/rol cuando corresponde. FK CASCADE
limpia relaciones al eliminar una Persona; no elimina elementos de trabajo.
No copia nombres, avatares ni asignaciones tradicionales. Los roles admitidos
son `PROJECT_LEAD`, `EXECUTOR`, `SUPERVISOR` y `VALIDATOR`.

JSON 24 añade las cuatro colecciones con esos mismos nombres. Cada participación
incluye `contextId`, `personId`; un rol añade `role`; una responsabilidad añade
`objectiveId` o `nodeId` además del rol. Se incluyen en el snapshot transaccional,
en el límite común de 100.000 registros y en la validación previa a restaurar.
Se rechazan duplicados, Personas/contextos ausentes, roles desconocidos y elementos
fuera del contexto más cercano (incluidos contextos desactivados). Los nodos
admitidos son tareas ACTION y capas con rol EPIC/STORY.

El lector conserva JSON 1–23 con las cuatro colecciones vacías. La exportación
emite 24 cuando contiene cualquiera de estas relaciones; de lo contrario conserva
la selección histórica de versión. **Los lectores anteriores a JSON 24 no pueden
restaurar los nuevos respaldos que contienen participantes gestionados.** Room 32
no incorpora downgrade: no regresar a una APK antigua sobre la base actual.

El contenedor v2, ANBACK01, SQLCipher y la separación principal/señuelo permanecen
sin cambios. Se respaldan las Personas originales y sus bytes multimedia mediante
el mecanismo existente; no se crean archivos privados nuevos, cachés ni derivados.
Desactivar gestión conserva todas las relaciones. Restaurar prepara/valida todo
antes de la transacción y mantiene el protocolo durable de archivos existente.

## Bloque 13: iteraciones — Room 33 y JSON 25

La migración aditiva 32→33 añade cuatro tablas. `iteration_plans` referencia el
contexto estable y una Capa con modo Sprint; su `sprintId` es único. No copia el
título del nodo ni crea tareas. Contiene meta, inicio/término previstos opcionales,
notas y estado PREPARED/ACTIVE/CLOSED. `iteration_cycles` guarda UUID, planId,
número secuencial, inicio real y cierre opcional. Reabrir crea otro ciclo.

`iteration_commitments` guarda `(cycleId,taskId)`, `initial` y `addedAt`. Las
referencias iniciales se fijan al activar; las posteriores se añaden sin cambiar
las iniciales y se conservan al retirar tareas. No es otra fuente de pertenencia:
el alcance actual sigue siendo los hijos ACTION directos y `sprint_links`.
`iteration_closures` conserva `(cycleId,taskId)`, `inScope`, `phaseAtClose` y
`completedAtClose` por cada referencia del ciclo. Las fases al cierre son una
instantánea histórica, no un segundo estado operativo. Para tareas retiradas la
fase es null: no se atribuye al Sprint una fase posterior obtenida fuera de él.

JSON 25 serializa estas cuatro colecciones con esos mismos campos. Se incluyen
snapshot transaccional, restauración, límite de 100.000 registros y límite de
metadatos. Antes de escribir se validan unicidad de Sprint, ciclos consecutivos,
estados y fechas coherentes, referencia a tareas ACTION del mismo contexto,
compromiso inicial inmutable por ciclo y cobertura completa de cierres.
Las referencias históricas no pueden quedar fuera del contexto al mover nodos.
La restauración elimina las tablas de iteración dentro de la transacción antes
que los nodos, y las reconstruye después de nodos, contextos y vínculos.

JSON 1–24 produce colecciones vacías, sin fabricar planificación ni compromisos.
Se emite 25 cuando hay datos de iteraciones; en su ausencia permanece la selección
histórica. **Lectores anteriores a JSON 25 rechazan los respaldos que lo usan;
Room 33 no tiene downgrade.** No cambian contenedor v2, ANBACK01, SQLCipher,
identidad de sesión, separación principal/señuelo ni bytes multimedia.

Las conversiones con vínculos Sprint solo se permiten si todos sus extremos
se trasladan juntos y conservan su contexto gestionado estable, también anidado
o desactivado. Se valida el árbol propuesto antes de las escrituras; se retiran
solo referencias internas durante la reconstrucción transaccional y se restituyen
antes del commit, preservando Node.workState exactamente. Si hay colisiones de
identidad, referencias separadas o datos inválidos se rechaza y revierte.
Las conversiones sin vínculos ni planificación mantienen sus reglas anteriores.
No se almacena ni transporta un segundo porcentaje de progreso. Véase ITERATIONS.md.


## Evolución de Personas — Room 34 / JSON 26

Migración aditiva 33→34: `person_profiles` (PK/FK `personId`) y
`person_relations` (UUID estable `id`, FK `sourceId`/`targetId`). La tabla `persons`,
sus IDs, encuadres, imágenes y asociaciones anteriores permanecen intactos.
Eliminar una Persona elimina por CASCADE su ficha adicional y los vínculos que la
referencian; no elimina Personas relacionadas, nodos ni contextos.

JSON 26 añade `personProfiles` y `personRelations`. Cada perfil contiene `personId`,
`alias`, `description`, `notes`, `phones`, `emails`, `addresses`, `links`, `qualities`,
`skills`, `interests`, `availability`; todos los textos pueden estar vacíos. Contactos
múltiples se conservan como texto multilínea, sin inventar validaciones de teléfonos,
proveedores ni direcciones. Cada campo admite hasta 16.000 caracteres; el JSON y el
recuento global mantienen los límites del contrato.

Cada vínculo contiene `id`, `sourceId`, `targetId`, `category` (FAMILY, SOCIAL, WORK,
ACADEMIC, CUSTOM), `label`, `inverseLabel`, `meaning`, `mode` (DIRECTED, RECIPROCAL,
ONE_WAY), `relationKey`. El origen es `label` respecto al destino. DIRECTED requiere
una etiqueta inversa; RECIPROCAL usa la misma etiqueta; ONE_WAY tiene inversa vacía,
y en la ficha de destino se muestra como vínculo entrante sin atribuir reciprocidad.
La inversa es una presentación del mismo registro; no se guarda un segundo vínculo.

`relationKey` es una clave derivada portable para evitar duplicados: un array JSON
compacto de categoría, modalidad, IDs ordenados (excepto ONE_WAY), y etiquetas
orientadas según ese orden, normalizadas con trim, minúsculas Locale.ROOT y espacios
consecutivos únicos. En FAMILY madre/padre/progenitor/a comparten rol parent y
hijo/a corresponde a child: crear la inversa genérica no duplica un vínculo parental.
Los IDs del vínculo y de Personas son las identidades de intercambio; la clave no
reemplaza esas identidades. Desktop/LAN deben implementar este contrato lógico,
no compartir directamente una base Android.

La exportación incluye ambas colecciones en el snapshot transaccional. La inspección
valida modalidades, campos, referencias, autorreferencias, claves y duplicados antes
de preparar archivos o sustituir datos. La restauración inserta los perfiles y
vínculos después de las Personas, en la misma transacción existente. Los PNG se
exportan con sus bytes y se restauran por staging/diario existentes, con deduplicación;
esta fase no crea nuevos archivos privados ni cambia su ciclo de vida.

Lectores 1–25 inicializan ambas colecciones vacías. Se emite JSON 26 únicamente si
hay perfiles o vínculos nuevos; sin ellos se conserva la selección histórica del
formato. Los lectores antiguos rechazan JSON 26: no hay compatibilidad inversa ni
downgrade de Room. SQLCipher, ANBACK01, contenedor v2 y aislamiento de bóvedas no cambian.


## JSON 27: Tecnologías de Persona

Room 34→35 añade únicamente `person_technologies`. JSON 27 incluye la colección
`personTechnologies` con `personId`, `technologyId` y `proficiency`. El nivel es uno
de `UNEVALUATED`, `BASIC`, `INTERMEDIATE`, `ADVANCED`, `EXPERT`. Pares duplicados,
IDs inexistentes y niveles desconocidos se rechazan antes de reemplazar datos.
Las FK CASCADE retiran asociaciones al borrar una Persona o Tecnología; retirar
la asociación conserva el catálogo. No contiene rutas ni medios duplicados: los
iconos y sus bytes permanecen en `technologies`/`technologyIcons` y manifiestos
existentes, incluso si solo los utiliza una Persona.

La restauración inserta relaciones después de Personas y catálogo dentro de la
transacción y staging existentes. JSON 1–26 inicializa esta colección vacía y
conserva perfiles/relaciones de fase 1 cuando están presentes. Si no hay asociaciones
nuevas, se conserva la selección histórica del formato. Lectores anteriores a esta
fase rechazan JSON 27. No hay migración inversa de Room. Los listados de proyectos
y trabajo son derivados de responsabilidades actuales y no se serializan de nuevo.
No cambia el contenedor, ANBACK01, límites globales ni aislamiento de bóvedas.

## Personas: galería CV — JSON 28 / Room 36

Room 35→36 añade `person_photos` y `person_birthdays`, con FK CASCADE a `persons`.
No cambia tablas ni datos previos. JSON 28 se emite al existir fotos adicionales
o fecha de nacimiento; en caso contrario se conserva la selección histórica.

- `personPhotos`: `id`, `personId`, `file`, `position`, `photoZoom`, `photoX`, `photoY`.
  Máximo cuatro por Persona; posiciones únicas 0–3; encuadre validado 1–5×.
- `personBirthdays`: `personId`, `date`, fecha real ISO `AAAA-MM-DD`, año 1–9999.
- Las fuentes fotográficas se incluyen en `avatars` o en sus streams `imageFiles`,
  sin rutas absolutas ni duplicar bytes de nombres compartidos. Conservan límites
  de avatar de alta resolución y límites agregados existentes.

El lector admite JSON 1–28. Versiones 1–27 inicializan ambas colecciones vacías.
Los lectores anteriores rechazan JSON 28: no puede restaurarse en APK antiguas.
La edad y las miniaturas no se exportan porque son datos calculados/regenerables.
La restauración remapea los nombres de todas las fotos, conserva IDs, orden y
encuadres, incluye las fuentes previas en el diario y revierte cambios ante fallos.
El cifrado y la separación entre bóvedas no cambian.


## Gestión mediante Capas reales — JSON 29 / Room 37

La migración aditiva 36→37 crea `management_layers`, `sprint_stories`,
`objective_work` y `local_identity`, con claves foráneas CASCADE e índices de
unicidad. No elimina ni modifica las tablas históricas. Room ejecuta la migración
transaccionalmente; no existe downgrade ni conversión destructiva.

JSON 29 añade cuatro colecciones:
- `managementLayers`: `nodeId`, `contextId`, `kind`. La función de una Capa no
  depende de su título editable. Cada contexto inicializado contiene las doce
  funciones base; los objetivos añaden `OBJECTIVE:<id>` con la misma identidad del
  objetivo histórico.
- `sprintStories`: `sprintId`, `storyId`. Cada Historia tiene como máximo una
  asociación operativa; su ubicación original y sus descendientes se conservan.
- `objectiveWork`: `objectiveId`, `nodeId`. Vínculos a Historias o Tareas, sin copiar
  contenido ni persistir porcentajes derivados.
- `localIdentities`: `slot` (`SELF`), `personId`. Como máximo una Persona Yo por
  almacén. No es una cuenta ni un permiso.

El lector admite JSON 1–29. Los respaldos 1–28 inicializan estas colecciones vacías;
se conservan Actas, objetivos, participantes y vínculos históricos. Una gestión
activa se organiza en Capas al activarla/abrirla, de forma idempotente y atómica.
Se añade estructura después de las Capas personales existentes. Las historias y
épicas anteriores conservan sus IDs y asociaciones; cambia su contenedor canónico
cuando resulta compatible. Conflictos de identidad, ciclos o separación de
contextos rechazan la operación completa sin sobrescribir datos.

Los textos históricos del Acta y objetivos se conservan. Tras la inicialización,
la ficha lee los títulos/descripciones de los nodos reales. Una edición explícita
actualiza también los adaptadores históricos dentro de la misma transacción; el
respaldo conserva los registros y los nodos, sin perder criterios ni relaciones.

La validación comprueba tipos, unicidad, contenedores, existencia de Personas y
objetivos, y pertenencia al mismo contexto antes de restaurar. La restauración
utiliza la transacción y staging durable existentes. No se añaden medios nuevos:
los bytes de imágenes y adjuntos mantienen su contrato y sus manifiestos.
Contenedor v2, ANBACK01 y aislamiento principal/señuelo permanecen sin cambios.
Los lectores anteriores rechazan JSON 29; una APK con Room 36 no puede abrir una
base actualizada a 37. Conservar un respaldo externo previo para volver a una
versión antigua mediante un procedimiento de recuperación autorizado.

## JSON 30 — anotaciones de Gantt / Room 38

Añade `ganttAnnotations`: registros `id`, `contextId`, `text`, `epochDay`, `lane`.
Contexto existente, IDs únicos no vacíos hasta 200 caracteres, texto no vacío
hasta 4000, día 0–2932896 y lane finita 0–1. No incluye píxeles ni rutas.
La colección se valida antes de restaurar y se escribe en la transacción existente.
La exportación utiliza 30 cuando hay anotaciones; sin ellas conserva la versión
mínima necesaria. Importar 1–29 produce colección vacía. No cambia contenedor,
cifrado, separación de bóvedas ni tratamiento de bytes multimedia.
Los lectores que no soportan 30 rechazarán esos respaldos. No se ofrece downgrade
de Room 38 a 37. PNG generados mediante SAF son derivados externos, no archivos
privados requeridos por la recuperación; sus anotaciones sí están respaldadas.


## Correcciones UI de Metro y Personas — sin cambio de formato

Se reutilizan Room 38 y las colecciones existentes personPhotos/personBirthdays,
cuya representación continúa en JSON 28 o versiones posteriores según el snapshot.
No se añaden campos, preferencias, rutas de archivos ni migraciones. Los favoritos
Metro continúan globales por instrucción expresa del usuario; cambiar de sección no
reescribe viajes, preferencias, rutas ni seguimiento.

Establecer una foto de referencia como avatar reutiliza su archivo y encuadre en una
transacción bajo el bloqueo común de archivos. Si la principal anterior no estaba en
otra referencia de esa Persona, ocupa el lugar de la foto elegida en la galería, con
su encuadre anterior. Así se conservan ambas fuentes incluso con cuatro referencias.
Si ya estaba representada, se conserva la galería; si no había principal, se conserva
la referencia elegida compartiendo su archivo con el avatar. No se duplican ni
recomprimen bytes. La exportación/restauración existente incluye las dos fuentes y
remapea sus nombres y referencias. La liberación usa el diario y las referencias
confirmadas existentes; quitar una referencia no borra el archivo usado como avatar.
Los fallos revierten el intercambio y conservan imágenes y metadatos previos.

La creación/edición de Persona puede guardar el cumpleaños ISO AAAA-MM-DD en la misma
transacción que su identidad, usando person_birthdays existente; una fecha inválida
revierte también la identidad. El parámetro ausente conserva el cumpleaños y la cadena
vacía lo retira. El calendario es estado de edición, no un nuevo dato exportable.

El visor compartido consulta las fuentes privadas mediante AvatarStore.read, sin
miniaturas ni escritura de archivos; conserva la calidad disponible de esas fuentes
(hasta 2048 px según el límite actual). No puede recuperar resolución ya descartada
por importaciones históricas. Zoom, posición y selección son estado temporal del visor,
no nuevos archivos ni datos confirmados; se liberan al cerrarlo. No se añade caché de
disco ni se reciclan bitmaps aún usados por el renderer.

Pruebas dirigidas: promoción explícita, conservación de principal anterior, referencias
compartidas, eliminación, restauración con bytes íntegros, fallo inyectado de promoción,
fecha inválida y rollback de creación/edición, y compatibilidad histórica en los ensayos
de galería existentes. No se cambia el protocolo de firma ni se genera APK oficial.

### Caché regenerable de tarjetas compartidas

Guardar imagen (T4) crea un PNG derivado externo mediante SAF, regenerable desde los mismos datos y originales. No integra un archivo privado nuevo ni modifica el formato de respaldo; el PNG externo elegido por el usuario queda fuera del backup de la aplicación.

La caché privada `card-shares` (PNG derivados, staging `.part` y diarios `.delete`) no forma parte del respaldo. Los nodos, fotografías, adjuntos, avatares e iconos originales conservan su formato y exportación existentes. Compartir no crea relaciones ni datos confirmados, ni cambia versiones de Room/JSON. El permiso temporal de captura tampoco se persiste o restaura. La caché puede reconstruirse a partir del nodo y sus originales; su limpieza usa nombres controlados, revocación de permisos y el bloqueo común de archivos.

### Rediseño compacto de Gantt — sin migración ni formato nuevo

Se conserva Room 38, JSON 30 para anotaciones y su compatibilidad anterior. Los números HU y de comentarios son proyecciones deterministas; no se exportan contadores nuevos. Los valores existentes de títulos, IDs, relaciones, fechas, progreso, texto, epochDay y lane permanecen en las colecciones originales. Abrir/filtrar no transforma registros; un movimiento confirmado actualiza únicamente epochDay/lane del mismo UUID mediante la transacción existente. Cancelar conserva el registro anterior. Las nuevas imágenes de gráfico/comentarios son derivados externos SAF, no archivos privados necesarios para restaurar. No se cambia el ciclo de vida del almacenamiento privado.

## JSON 31 / Room 39 — eventos originales del aprendizaje Metro (bloque 1)

Añade `metroLearningEvents` cuando existen eventos; sin ellos se mantiene la
selección histórica de la versión mínima. Los lectores admiten JSON 1–31 y los
respaldos 1–30 producen historial vacío. Se conservan contenedor, cifrado y los
payloads de viaje Metro v1–v5; este bloque no cambia las sesiones históricas.

Cada fila contiene exactamente `id`, `sessionId`, `sequence` (entero >=0),
`payload` y `invalidReason` (nulo o UNDONE). ID y pareja sesión/secuencia son
únicos; las secuencias de cada sesión son completas desde cero. El array mantiene
el orden original de registro (también tras restaurar), independiente de saltos
del reloj civil; ese orden define la ventana de observaciones recientes. El payload v1 exige `route` (plan Metro sin sesiones), `station`,
`stepIndex`, `line`, `direction`, `service`, `kind`, `wall`, `elapsed`, `boot`,
`clockDomain`, `paused`, `pauseMillis`, `valid`, `exclusion`, `estimatedOffset`
y `etaMillis`, además de `version`. Tipos, claves duplicadas/desconocidas,
versión, reloj no negativo, UUID del dominio, geometría, estación/índice,
línea/sentido/servicio, rangos temporales y coherencia de validez se comprueban
antes de sustituir datos. El catálogo Metro es necesario aunque se hayan eliminado
los viajes que originaron el aprendizaje. No se requiere una sesión vigente:
los eventos contienen su recorrido y sobreviven a eliminar el viaje.

Los eventos originales, incluida la captura invalidada al deshacer, se incluyen
en el snapshot transaccional y se restauran íntegramente. Observaciones, ventanas
y estimaciones se derivan de ellos; no se serializan como mediciones duplicadas.
Se mantienen los límites globales de 100.000 registros y 16 MiB de JSON; superar
los límites falla explícitamente sin truncar el historial. No contiene rutas de
dispositivo ni archivos adicionales.

`metro_learning_clock` identifica el reloj del dispositivo abierto. Es metadata
regenerable excluida del respaldo: se borra dentro de la transacción de restore y
se genera al registrar la primera nueva captura. Los eventos preservan sus dominios
originales; los intervalos históricos siguen siendo utilizables, pero nunca se
une un evento restaurado con una captura del dispositivo receptor. Un fallo o
cancelación de restauración conserva eventos y dominio anteriores por rollback.
Las aplicaciones que no soportan JSON 31 rechazan estos backups; no se promete
downgrade de Room 39. Los eventos no se borran por antigüedad: el límite de 100
rige la ventana de cálculo, no la fuente durable para futuros algoritmos.

Validación dirigida del bloque 1: `MetroLearningTest`,
`MetroLearningPersistenceTest`, `MetroLearningMigrationTest`, `MetroMigrationTest`,
`MetroStagePersistenceTest`, `BackupFormatTest` y `BackupRepositoryTest`: 70 casos
aprobados. Verifican exportación/inspección/restauración real del contenedor,
compatibilidad histórica, orden de registro, datos corruptos, fallos durante el
reemplazo, cancelación de captura, invalidez reversible y separación de bases.
Compilación debug aprobada; comprobación física del dispositivo pendiente.

## Metro bloque 2 — payload de viaje v6: predicción fija y evidencia

Se admite viaje Metro v1–v6. El escritor conserva v4/v5 para rutas sin modelo y
emite v6 cuando un plan, ruta original/vigente, archivo de recorrido o undo contiene
una predicción adaptativa. No cambian Room 39, contenedor ni colecciones exteriores
(JSON hasta 31). Los backups antiguos mantienen exactamente su estimación estándar,
sin reescribir filas al leer ni aplicarles aprendizaje posterior.

La ruta añade `prediction` opcional: objeto v1 con `version`, `window` y `parts`.
Cada parte exige `first`, `end`, `millis`, `mean` (nullable), `accepted`,
`discarded` (motivo→cantidad), `lastMillis` y `lastAt` (ambos nullable). Las partes
cubren todos los pasos en orden y sin huecos/solapamientos. Se validan tipos,
versión, índices, cobertura, continuidad de contexto para acumulados, límites,
coherencia media/cantidad, motivos admitidos, tiempos y fórmula del estimador v1
(mínimo 5 muestras, peso min(n/30,1)). El resultado admite una diferencia de 1 ms
por el redondeo de la media. WAYPOINT conserva duración cero; combinaciones
medidas son partes individuales. La distribución interna de un acumulado se
deriva de su duración total y estándar, nunca se exporta como nuevas mediciones.
El contexto de combinaciones distingue también la línea/sentido/servicio siguiente.

La evidencia de la predicción es una referencia histórica fija, separada de los
eventos originales y de la ventana actual de entrenamiento. Invalidar/deshacer
una confirmación modifica sus contribuciones futuras, pero no la predicción
original de un viaje ya iniciado. Todos los modelos se exportan y restauran
íntegros, incluidos `originalRoute`, `routeHistory`, `routeArchives` y el viaje
anterior del undo. La portabilidad sigue marcando relojes activos como inciertos;
no cambia los tiempos estimados. Un modelo corrupto se rechaza antes de reemplazar
el estado confirmado, con la restauración transaccional existente.

El payload de captura v1 conserva campos y lectores; se admiten además los motivos
NO_RAIL (sin ferrocarril) y NO_STOP (sin detención del servicio seleccionado).
La corrección entre estaciones se registra como CORRECTION y nunca como puertas.
La posición/ETA que mostró la UI puede capturarse directamente, sin reconstruirlo
más tarde a partir del reloj del gesto. No hay archivos, preferencias ni cachés
privadas nuevas que exportar o liberar. Los lectores anteriores al bloque 2 pueden
rechazar los payloads v6 y estos motivos nuevos; no se ofrece downgrade ni una
restauración parcial omitiendo datos.

Validación de bloque 2: 150 pruebas dirigidas aprobadas, con nuevos casos en
`MetroPredictionTest`, `MetroPredictionPersistenceTest`, `MetroAdaptiveUiTest`
y `MetroAdaptiveServiceTest`. Incluye round-trip real de un viaje v6 con undo,
archivo del modelo anterior tras cambiar destino, predicción original íntegra,
reapertura y rollback ante modelo inconsistente. Pasan regresiones de backup
histórico, rechazo de campos/tipos/versiones no compatibles y compilación debug.


## Metro — bloque 3: resultado de búsqueda temporal

Separar formulario y recorrido no añade tablas, preferencias, archivos ni campos
al backup. Se mantienen Room 39, JSON exterior 31 y payload de viaje Metro v6.
`Buscar viaje` solo cambia estado de UI restaurable; el formulario y el resultado
sin guardar no forman parte del almacén confirmado ni se exportan. Guardar viaje,
crear tarea e iniciar seguimiento siguen usando los repositorios y validaciones
existentes, con predicciones adaptadas y referencias originales del bloque 2.

Validación del bloque 3: pasan 110 pruebas dirigidas, incluidas regresiones de
formato y repositorio de backup, guardado explícito y predicciones persistidas.
La búsqueda y navegación de regreso se verifican sin modificar el snapshot del
almacén. `assembleDebug` y `git diff --check` pasan.


## Metro — sesiones canceladas: payload de viaje v7

Se admite v1–v7. Un viaje que contiene una sesión cancelada emite v7; sin ese
estado se mantiene la selección de formato v4/v5/v6 anterior. No cambia Room 39,
JSON exterior 31, el contenedor ni la captura de aprendizaje v1. CANCELLED es un
evento terminal de sesión, no una apertura de puertas: conserva offset actual,
última estación confirmada, fin, duración del seguimiento, pausas, predicciones,
etapas confirmadas e instantáneas históricas de ruta dentro del payload existente.
No incluye estación de llegada ni añade una etapa final. El estado cancelado se
deriva del último evento, sin un flag redundante. Los eventos de aprendizaje
previos se exportan y restauran sin invalidarlos.

El lector exige fin de sesión para ARRIVED/CANCELLED y lo rechaza sin evento
terminal; CANCELLED solo se admite desde v7, al final, sin estación, con offset
igual al de la sesión y sin undo de llegada. Conserva controles READY cancelados
tras un cambio de destino. La validación común de backup ocurre antes de sustituir
datos; un payload incoherente mantiene el almacén anterior. Aplicaciones anteriores
rechazan v7; no se ofrece downgrade que lo convierta falsamente en llegada.

Retirar la selección de Persona no borra IDs ni asociaciones históricas. Estos
siguen exportándose/restaurándose con las validaciones anteriores. La animación
del tren es temporal y no introduce datos, archivos ni preferencias de backup.

Validación: 149 pruebas dirigidas aprobadas. La cancelación conserva las capturas
originales y observaciones al reabrir y después de exportar/inspeccionar/restaurar
un backup real. Se rechaza un evento CANCELLED sin cierre, conservando el almacén,
y un payload cancelado falsamente rebajado a v6. Se verifica codec de controles
cancelados en etapas, combinación y READY, y se mantienen pruebas históricas de
backup. `assembleDebug` y `git diff --check` pasan.


## Metro — originales de pausa y posición durante pausa (captura v2)

La tabla y JSON exterior no cambian (Room 39 / JSON 31). `metroLearningEvents`
transporta íntegramente los payloads de captura v1 y v2. V1 conserva DOORS,
CORRECTION y ARRIVAL. V2 usa exactamente las mismas claves y tipos, con PAUSE,
RESUME y PAUSED_LOCATION. Los dos primeros conservan estación/índice estimados;
PAUSED_LOCATION representa una estación confirmada durante la pausa, sin afirmar
apertura de puertas. En todos los casos `valid=false`; PAUSE/RESUME usan motivo
PAUSE, PAUSED_LOCATION usa PAUSED. PAUSE y PAUSED_LOCATION exigen `paused=true`,
RESUME exige `paused=false`. Una versión o combinación incoherente se rechaza.
Se mantienen validaciones de reloj, dominio, ruta, geometría, límites, unicidad y
secuencia sin huecos. Los lectores antiguos rechazan v2; no hay downgrade que
convierta estos eventos en mediciones limpias.

Cada pausa/reanudación y actualización de sesión se escriben en la misma
transacción. Los originales sobreviven al borrado de un viaje y se exportan
sin depender de su existencia. El estado de la sesión mantiene el codec v1–v7.
El motor separa eventos de interrupción de referencias de puertas y conserva una
observación atravesada por una pausa con PAUSE, incluso de duración cero. No
resta tiempo para limpiar muestras ni genera mediciones de estaciones ausentes.
Las capturas v1 históricas mantienen la exclusión por acumulado de pausa.

No se añaden archivos, cachés, preferencias ni datos fuera del snapshot de backup.
Restaurar preserva los originales y regenera únicamente el dominio local del
reloj, con rollback transaccional si falla la validación. Las posibles cotas y
mediciones de salida no se implementan en esta fase.

Validación: 153 pruebas dirigidas pasan, más 18 verificaciones finales tras
precisar el texto de diagnóstico. Se prueba pausa/reanudación de duración cero,
lectura v1/v2, rechazo de versiones/flags incoherentes con rollback, reapertura,
borrado de viaje seguido de backup real y restauración de todos los originales
sin reconstruirlos desde el historial eliminado. Se verifica que las mediciones
limpias a ambos lados se conservan y que un acumulado con pausa no se distribuye
como tiempos individuales. `assembleDebug` y `git diff --check` pasan.


## Jerarquía — lote 1: cambios de presentación sin migración

No se modifica el esquema ni el formato de backup. LOW/MEDIUM históricos se
exportan/restauran sin normalizar; la UI los presenta como No prioritario según
la decisión del usuario, y HIGH como Prioritario. Fechas de vencimiento mantienen
los campos y validaciones existentes. Retirar accesos Metro de la jerarquía no
borra viajes, vínculos históricos ni configuraciones de plantillas. Los datos
siguen incluidos en el snapshot confirmado. La nueva variante del icono de nota
es un vector empaquetado de la APK; no hay archivos privados nuevos que exportar.

## Metro — modo viaje permanente sin cambiar datos históricos

Room 39 y JSON exterior 31 se mantienen. `metroJourneys.enabled` conserva sus
valores históricos y sigue siendo obligatorio en los formatos que lo incluyen.
Ya no condiciona el inicio de seguimiento: un viaje restaurado con false puede
iniciarse directamente sin convertir ese campo a true. No se modifica el
snapshot, el lector ni la restauración, ni se omiten relaciones, sesiones o
capturas originales. La retirada del selector de modo no añade datos o archivos.

## Capturas persistentes — preferencia opcional sin migración

`security_preferences` admite `captures_until_reenabled` de tipo booleano.
True conserva Hasta volver a activar; false o ausencia histórica mantienen la
protección inicial, salvo autorización temporal explícita de la sesión.
El permiso temporal sigue sin exportarse; la elección persistente sí se incluye
íntegramente en `privatePreferences` (JSON >=18), con su namespace y tipo.
Se rechazan otros tipos antes de sustituir el almacén. No cambia el formato
exterior, Room 39 ni los lectores históricos de backup. Restaurar preserva los
demás ajustes del namespace y usa rollback/recarga existentes. El ajuste solo
se aplica al almacén principal abierto, sin cambiar el bloqueo de la aplicación
ni permitir capturas de diálogos de contraseña o del almacén señuelo.

## Etiquetas únicas y propiedades de Núcleos

La selección manual única es una regla del formulario, no una restricción del
formato histórico. node_tag, recurrence_tag y conversion_tags siguen admitiendo
las asociaciones múltiples ya guardadas; exportar/restaurar no normaliza ni
elige etiquetas. El usuario debe seleccionar expresamente una o ninguna para
reemplazar una asignación histórica múltiple.

Las etiquetas, responsables y prioridad editables del Núcleo reutilizan sus
conversion_roots/conversion_tags/conversion_people existentes. El snapshot, la
validación de referencias y la restauración transaccional ya incluyen esas
filas. Un backup anterior sin raíz de conversión representa propiedades vacías;
la ausencia no genera filas ni asociaciones. No cambian Room 39, las versiones
JSON compatibles ni los lectores históricos; no se necesita migración y no se
añaden archivos o rutas del dispositivo.

ProjectTagsTest comprueba contenedor real, restauración íntegra, reapertura,
datos históricos sin propiedades, rechazo de referencias corruptas conservando
el estado anterior y conservación de asociaciones al convertir Núcleo/Capa.

## Personas — entradas fotográficas privadas (Room 40 / JSON 32)

JSON 32 añade `personMemories` (id, personId, date nullable, description,
position) y `personMemoryImages` (id, memoryId, attachmentId nullable,
legacyPhotoId nullable, position, date nullable, description). Cada imagen
referencia exactamente un original READY de `attachmentFiles` o una fotografía
histórica de `personPhotos` perteneciente a la misma Persona. Las entradas no
pueden estar vacías; dentro de cada entrada no se repiten fuentes ni posiciones.
Fechas ISO locales opcionales, años 1–9999; descripciones hasta 16.000 caracteres.
Se validan propietarios, IDs, orden y referencias antes de reemplazar datos.

Los lectores aceptan JSON 1–32. Los respaldos anteriores a 32 que contienen
`personPhotos` se adaptan a una entrada por fotografía, con el mismo ID, sin
copiar archivos, fechas null y descripciones vacías. La migración Room 39→40
aplica la misma regla y conserva tablas antiguas, encuadres y avatar seleccionado.
Los lectores antiguos rechazan la versión nueva; no se degrada el formato
eliminando metadatos. La biografía y el enlace reutilizan description y links de
`personProfiles`; la edad se calcula y no se exporta como campo nuevo.

Los originales nuevos PNG/JPEG reutilizan el contenedor binario de adjuntos y
su manifiesto: bytes completos, nombre portable, MIME, tamaño y SHA-256; nunca
rutas absolutas. La exportación y restauración son por streaming. No existe un
límite de cuatro fotos en la galería. Se mantienen el límite de 20 MiB por
original, dimensiones máximas de 32.000 píxeles y las cotas generales de metadatos
(16 MiB / 100.000 registros). Los originales usados exclusivamente por galerías
no consumen la cota agregada de 2 GiB de adjuntos de tareas/Núcleos; un original
compartido con esos elementos sí cuenta en ella. El espacio disponible y esas
cotas de integridad condicionan la capacidad real. Las fotografías antiguas
conservan exactamente sus PNG existentes, sin simular resolución perdida.

Las miniaturas de galería son regenerables y quedan solo en la caché LRU de
memoria existente de 4 MiB; no se incluyen en backup. Reservas de editores y
limpieza pendiente son estado operacional local, no contenido confirmado del
respaldo. Cada reserva durable conserva propietario y nombre portable del
archivo; no caduca por antigüedad. La restauración conserva archivos que aún
están reservados aunque remapee nombres de los originales confirmados.
Al liberar la última reserva, se reintenta la limpieza con el bloqueo común.
Eliminar entradas, imágenes o Personas confirma primero las relaciones en Room;
solo después libera originales sin referencias ni reservas. Los PNG históricos
siguen el diario de imágenes existente y se conservan si son avatar o están
compartidos. Fallos de eliminación quedan pendientes para reintentar. Un backup
inválido conserva el estado anterior y la restauración utiliza el staging,
transacción y diarios existentes. Se conserva el cifrado y aislamiento de vaults.

## Ajustes finales — bloqueo y apariencia (sin nuevo esquema)

Room 40 y JSON 32 se conservan. El namespace privado security_preferences
reutiliza background_timeout, de tipo long. Los valores históricos 0, 30.000,
60.000 y 300.000 ms mantienen su significado; ausencia equivale a 30.000 ms.
La configuración añade -1 para «Hasta bloquear manualmente» durante
la sesión autenticada vigente, sin exportar claves ni una sesión abierta.
Se validan tipo y valores admitidos antes de restaurar; los demás campos del
namespace permanecen intactos. Un lector antiguo que desconoce -1 aplica su
fallback seguro de bloqueo inmediato. La validación dirigida del modo manual
pasa: respaldo cifrado real, restauración de -1 y campos ajenos, rechazo de tipos
y valores inválidos sin reemplazar datos, ausencia histórica con 30.000 ms
predeterminados y reapertura que conserva la preferencia, pero exige autenticar
una nueva sesión. No se respaldan claves ni sesiones abiertas.

appearance_preferences conserva theme y text_size. NightBlue y DayGrey se
exportan y restauran como strings mediante privatePreferences. Leer Minimalist
lo reemplaza durablemente por Arachn0de, sin cambiar tamaño de texto ni campos
ajenos; la adaptación también se aplica al recargar preferencias restauradas.
Se rechazan tipos incompatibles antes de sustituir datos. No se renumeran temas
ni se modifica el contenido de los respaldos históricos para normalizarlos.

appearance_unlock_cache es una copia regenerable del nombre del tema principal,
fuera del almacén cifrado, para dibujar el desbloqueo antes de autenticar. Solo
contiene un nombre admitido: no contraseñas, claves, IDs de almacenes, tamaños de
texto ni datos de Personas. No se exporta; la fuente confirmada sigue siendo la
preferencia cifrada, incluida en el respaldo. Se sobrescribe al aplicar/restaurar
la apariencia del principal y no incorpora ajustes del señuelo. Si falta o está
mal formada, se usa Black & White 90. Es un único valor gestionado por Android,
sin crecimiento de archivos; borrar los datos de la aplicación lo elimina.
No conserva ni reconstruye sesiones. Los colores del logotipo se adaptan solo
al renderizar: no hay variantes privadas, bytes nuevos de imágenes ni archivos
que añadir al manifiesto. El vector Lucide de Capas es un recurso de la APK.

## Fijar Núcleos y Capas — JSON 33 / Room 41

Room 41 agrega `isPinned INTEGER NOT NULL DEFAULT 0` a `projects` y `nodes`.
La migración 40 → 41 conserva filas, relaciones y posiciones; los registros
anteriores quedan sin fijar. Solo `nodes.purpose = LAYER` admite la marca.

Cuando existe algún elemento fijado, el exportador usa JSON 33 e incluye
`isPinned` booleano en cada proyecto y nodo. Sin marcas continúa seleccionando
el formato histórico mínimo compatible con los demás datos. JSON 1–32 restaura
las marcas como `false`. JSON 33 exige booleanos válidos y rechaza nodos fijados
que no sean Capas antes de sustituir datos. El contenedor de respaldo, las imágenes
y las preferencias no cambian. La restauración usa el staging y la transacción
existentes; no hay nuevos archivos privados ni reglas de liberación.

## Audio — JSON 34 / Room 42

Los adjuntos admiten además AAC-LC ADTS mono de 16 kHz (audio/aac, extensión aac,
width/height 0). Se conservan los límites de 20 MiB por archivo y 2 GiB agregados.
JSON 34 se selecciona cuando existe algún audio e incorpora durationMs,
playbackMs y bookmarkMs a cada entrada de attachmentFiles. Las imágenes usan
0, 0 y null; audios exigen duración positiva, posición/marcador dentro de ella.
Sin audios se conserva la selección del formato histórico mínimo. El lector
admite JSON 1–34; versiones anteriores aplican los valores de imagen históricos.
Los manifiestos y el contenedor cifrado/streaming existentes incluyen los bytes
AAC originales con tamaño, SHA-256 y validación ADTS/duración, sin rutas absolutas.
Los nombres físicos se renuevan al restaurar; IDs, enlaces, posición y marcador
se conservan. Audios no pueden presentarse como fotografías de Personas.

Room 41 → 42 agrega las tres columnas y audio_recording_operations. Este diario,
las reservas y fragmentos de audio-drafts son estado operativo de una grabación
sin confirmar: no hay un respaldo paralelo. El backup/restore se rechaza mientras
queda una reserva WAITING, grabación o diario de fragmentos pendiente de resolver;
nunca se comunica éxito omitiendo material. El backup confirmado incluye todas
las filas/bytes del audio y sus referencias. Restaurar exige cerrar el reproductor.
Los respaldos antiguos conservan las imágenes y restauran sin marcas de escucha.

COMMITTED/DISCARDED se confirman antes de liberar fragmentos, para no resucitar
adjuntos eliminados ni perder una decisión de descarte tras un fallo físico.
La recuperación se ejecuta al autenticar; las reservas no caducan. Los archivos
temporales están cifrados con la clave del almacén correspondiente. La exportación
individual por SAF/URI entrega el sonido o la imagen original y conserva la fuente;
no requiere archivos públicos por defecto ni una caché persistente de sonido.

Validación dirigida de Audio: 28 casos distintos aprobados (persistencia/lifecycle,
UI, exportación por URI, cifrado/señuelo y migración), assembleDebug correcto una
vez y comprobación del esquema 42/diff. Se verificó restauración de un archivo
cifrado real con bytes de audio, posición y marcador, y de imágenes históricas;
rechazo de corrupción sin reemplazar datos; limpieza interrumpida que no resucita
material eliminado; y pérdida de proceso antes de abrir micrófono. No se ejecutó
la suite general ni se generó Release. Las pruebas nativas en dispositivo siguen
pendientes; los tonos de prueba son sintéticos y no contienen datos del usuario.

## Antisocial — fijado y orden de entradas (Room 43 / JSON 35)

Room 42→43 añade exclusivamente `person_memories.isPinned`, booleano NOT NULL
con valor inicial falso. Conserva entradas, posiciones, fechas, textos individuales,
referencias y bytes originales. `position` sigue representando el orden manual,
ahora independiente dentro de fijadas/no fijadas de cada Persona. Las fechas no
ordenan la galería. Crear inserta al principio de no fijadas; fijar/desfijar añade
al final del destino y normaliza las posiciones afectadas transaccionalmente.

JSON 35 incluye `isPinned` en cada registro de `personMemories` y se emite cuando
existe alguna entrada fijada. Sin fijadas se conserva el formato anterior adecuado
a los datos. El lector acepta 1–35; versiones anteriores a 35 asignan falso.
Se rechazan tipos de flag inválidos antes de restaurar. La captura y restauración
existentes transportan posiciones, fijado, descripciones y archivos completos;
no se añaden rutas ni archivos operativos al backup. El contenedor no cambia.

El editor y visor habituales solo muestran la descripción compartida. Los textos
individuales siguen en `personMemoryImages.description`, sin concatenación ni
truncamiento, y pueden consultarse/seleccionarse en Textos históricos únicamente
en entradas que los contienen. Sus fechas individuales también se conservan.

## Recurrencias finalizadas eliminadas — Room 44 / JSON 36

Room 43→44 agrega `recurrence_rules.isDeleted INTEGER NOT NULL DEFAULT 0`.
JSON 36 se emite cuando hay alguna regla eliminada e incluye `isDeleted` booleano
en todas las reglas. Sin marcas se mantiene la selección del formato mínimo
anterior. El lector acepta 1–36; versiones 1–35 restauran como no eliminadas.
Se rechazan tipos no booleanos y reglas eliminadas cuyo estado no sea FINISHED.
Las reglas ocultas, ocurrencias, asociaciones y Tareas siguen incluidas completas;
se conservan las validaciones referenciales y el reemplazo transaccional existente.
No cambia el contenedor ni se incorporan archivos o rutas nuevas.

## Capturas compartidas pendientes (diario operativo)

Las filas `share_pending:<UUID>` no forman parte del formato exportable: son
estado temporal de una captura todavía sin guardar dentro del almacén cifrado.
Crear/restaurar exige que la cola esté vacía; no se omiten filas o bytes de una
captura aceptada informando éxito. Guardar integra la Tarea y los adjuntos en el
snapshot normal y retira el diario transaccionalmente. Cancelar marca primero
CANCELLED y libera sus reservas mediante la limpieza existente, recuperable tras
fallos. No hay migración Room ni nueva versión de JSON/contenedor. Los respaldos
históricos se restauran normalmente cuando no hay una captura pendiente.

## Eventos independientes — Room 45 / JSON 37

La migración 44→45 añade `calendar_events` y `calendar_event_people`, sin cambiar
las tarjetas. JSON 37 se emite cuando existen Eventos e incorpora:

- `calendarEvents`: id UUID canónico, title obligatorio (hasta 100 puntos Unicode),
  date civil canónica YYYY-MM-DD (años 1–9999), minute entero 0–1439 o null para día
  completo, description (hasta 16000 unidades UTF-16).
- `calendarEventPeople`: pares únicos eventId/personId, con referencias existentes.

El lector admite 1–37; los formatos 1–36 producen listas de Eventos vacías. Sin
Eventos se conserva la selección previa del formato mínimo. No cambia el
contenedor ni la envoltura cifrada. Los eventos y vínculos cuentan en los límites
globales de registros/texto. Se rechazan duplicados, referencias rotas, fechas,
horas y títulos inválidos antes de preparar o sustituir datos.

El snapshot incluye ambas tablas en la transacción existente. Restaurar limpia
Eventos antes de reemplazar Personas y repone eventos/vínculos dentro de la misma
transacción; un fallo conserva el estado anterior. Restaurar un backup histórico
reemplaza también la colección de Eventos por la colección vacía de ese backup.
No hay bytes externos, rutas, recordatorios ni archivos operativos nuevos. Fecha
y hora son civiles y se conservan independientemente de la zona del receptor.
Cada bóveda exporta únicamente su colección; siguen las validaciones de identidad
y tipo de almacén de los respaldos cifrados existentes.

## Colores de indicadores (preferencia visual por bóveda)

Dentro del namespace existente `appearance_preferences` se admiten ocho claves
opcionales `indicator_color_<id>`, para people, technologies, notes, tasks, files,
due, today y high. Sus valores son strings tipados: WHITE, GREY, RED, ORANGE,
YELLOW, GREEN, BLUE o VIOLET. Se valida tanto la identidad como el valor antes de
restaurar; colores o identificadores desconocidos en este prefijo se rechazan.

Las claves viajan en privatePreferences del snapshot transaccional y el respaldo
cifrado existentes. No cambia Room 45, JSON hasta 37 ni el contenedor: son valores
opcionales del mecanismo de preferencias vigente desde JSON 18. Los respaldos
anteriores sin estas claves conservan el aspecto original del tema; restaurar un
backup histórico sin preferencias elimina las elecciones anteriores como parte
del reemplazo transaccional. Restablecer colores retira estas claves únicamente.
No hay archivos ni cachés nuevos, ni rutas del dispositivo ni metadatos externos.

## Color individual de Eventos — Room 46 / JSON 38

45→46 añade calendar_events.color TEXT NOT NULL DEFAULT 'LIGHT_GREY'; conserva los
Eventos y responsables existentes. Valores admitidos: LIGHT_GREY, RED, ORANGE,
YELLOW, GREEN, BLUE, VIOLET. No depende de los colores de miniindicadores.

JSON 38 se emite cuando algún Evento tiene color distinto de LIGHT_GREY y añade
color a todos los registros calendarEvents. Sin colores personalizados se conserva
JSON 37 (o el formato mínimo anterior sin Eventos). El lector acepta 1–38; en
1–37 el color de Evento se inicializa como LIGHT_GREY. Se rechazan valores/tipos
inválidos antes de reemplazar datos. La columna viaja en el snapshot y la
restauración transaccional existentes, dentro de la bóveda autenticada. No cambia
el contenedor, no hay archivos nuevos ni metadatos de color fuera de la bóveda.

### Antisocial: visor, Red y copia de fotografías (2026-10-15)

Esta integración no añade datos persistentes ni cambia el formato. La identidad
principal, Personas, relaciones, entradas, fotografías y encuadres reutilizan sus
tablas y respaldo existentes. El recuento de publicaciones y Red son proyecciones,
no copias ni relaciones nuevas. La mejora de miniaturas solo cambia decodificación
en memoria; no sustituye los bytes confirmados que transporta el backup.
Guardar copia exporta mediante SAF los bytes de la fotografía almacenada, sin
rutas privadas ni recodificación, bajo el bloqueo de archivos y una reserva del
ciclo de vida existente. Cancelar libera esa reserva y conserva la fuente. La
copia externa elegida por el usuario no se registra ni se incorpora al respaldo;
las reservas operativas siguen excluidas y no se crean archivos privados nuevos.

### Favoritos de Antisocial en preferencias existentes (2026-10-15)

Sin migración Room ni cambio de versión de JSON/contenedor. privatePreferences
transporta la fila antisocial_favorites mediante el codec tipado existente:
version es int 1 y people es un set de ids de Personas. No modifica element_access,
relaciones ni la identidad SELF, ya respaldada. Cada bóveda conserva su fila propia.

BackupData valida las dos claves, tipo/versión, ids no vacíos de hasta 200 caracteres
y que todos pertenezcan a las Personas del mismo backup. Se valida antes del
reemplazo. Un backup histórico sin la fila restaura ausencia de favoritos; los
lectores históricos de preferencias genéricas pueden conservar el namespace sin
interpretarlo. Eliminar una Persona quita su id de la preferencia en la misma
transacción. Perder una relación no borra la marca, pero no incluye a esa Persona
en una Red a la que no pertenece. No hay archivos, rutas ni bytes multimedia nuevos.

Pruebas dirigidas aprobadas de reapertura, round-trip, ausencia histórica, rechazo
de payload/versión/referencia inválidos sin pérdida de datos, conservación de otros
namespaces y aislamiento/restauración cifrada principal/señuelo en JVM. La verificación
nativa en teléfono sigue pendiente y no se sustituye por estas pruebas.

### Recepción de enlaces compartidos

Las nuevas capturas externas de enlaces conservan `linkNote` booleano en el diario
operativo `share_pending:` v2; se leen diarios v1 con `linkNote=false`, sin reinterpretar
borradores antiguos. La marca no admite imágenes. Los diarios siguen excluidos del
backup y bloquean exportación/restauración hasta guardar o cancelar, como antes.
Al guardar un enlace se crea un Node NOTE normal con título y URL HTTP/HTTPS;
se respalda y restaura mediante el formato existente, sin archivos ni metadatos nuevos.
No cambia Room ni la versión del JSON de backup. Captura habitual e imágenes
conservan su comportamiento anterior.

## Limpieza de tareas completadas

La preferencia privada `completed_cleanup` reutiliza `privatePreferences` dentro
del almacén SQLCipher y del backup existente. Payload con el codec de preferencias:
`version` int 1 y `days` int de 0, 1, 7, 30, 90, 180 o 365. Cero significa Nunca;
la ausencia histórica también significa Nunca. Se validan claves, versión, tipo y
valor antes de restaurar. No cambia Room ni la versión del JSON/contenedor.
Restaurar conserva el plazo elegido; un backup histórico sin la fila desactiva
la limpieza. El observador de la sesión actualiza o cancela la programación.
No se añaden archivos ni copias permanentes de tareas eliminadas. Las notas,
Capas, Núcleos, Personas, comprobantes de ocurrencias y archivos compartidos o
reservados conservan las protecciones existentes.
