# Respaldo offline de Arachn0de

La extensión sigue siendo `.arachnode`. El esquema actual es Room **27**, con la cadena histórica completa de migraciones. La versión comercial preparada es **0.3.0 / 10** y el protocolo de firma no cambia.

## Seguridad local: formato actual

Toda exportación desde un almacén protegido usa la envoltura autenticada **ANBACK01**, con su propia contraseña, UUID, salt y clave aleatoria. El payload conserva el contenedor v2 y sus manifiestos/SHA-256. Los backups antiguos sin cifrar se importan mediante la validación y confirmación existentes; nunca se exporta un backup nuevo sin contraseña desde un almacén protegido.

JSON **18** añade `privatePreferences` (namespaces y valores tipados), `storeId` (UUID portable o null para fuentes históricas) y `storeKind` (`primary`/`secondary`). No transporta contraseñas ni claves del almacén. Los lectores admiten JSON 1–19 y contenedores v1/v2. Una fuente histórica sin preferencias ni identidad puede conservar JSON 17. Todas las exportaciones de sesiones protegidas incluyen su identidad y emiten 18 o 19. Versiones antiguas rechazan explícitamente el nuevo formato.

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
