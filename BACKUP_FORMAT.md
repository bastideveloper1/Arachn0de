# Preferencias del editor de imágenes

El namespace opcional `image_annotation_style` reutiliza `privatePreferences`
(tipos de JSON >=18): `color` es un entero de la paleta existente y `width` un
float de uno de los grosores existentes (.003, .006 o .014). Se rechazan claves,
tipos y valores desconocidos antes de restaurar. Sin la fila o sin una clave se
usa el valor histórico correspondiente: rojo `0xffe53935` y grosor .006.

La fila pertenece al almacén privado, se incluye íntegramente en el snapshot y
se restaura mediante las transacciones actuales. No hay archivos nuevos ni rutas
del dispositivo; no cambian Room, JSON, contenedor ni cifrado. Los lectores
históricos transportan esta fila como preferencias privadas genéricas y los
respaldos antiguos siguen siendo legibles. La lupa y el gesto activo son estado
temporal excluido del respaldo y de los píxeles exportados. Contrato y pruebas
en `docs/IMAGE_EDITOR_GROUP_4.md`.

# Datos v59: preferencia de posición de Favoritos

Sin cambiar Room ni el contenedor, `node_sort_preferences.mode` conserva `SORT1:`
y admite un campo opcional `favorites`, con valores estrictos `NONE`, `FIRST` o
`LAST`. Su ausencia significa `NONE`; el escritor lo omite en ese caso. Los
criterios, sus direcciones, herencia y los campos de Capas primero se conservan.
Los payloads anteriores de siete u ocho criterios se leen sin reescribirlos.

La lista general de Núcleos usa una fila en la misma tabla con contexto reservado
`@projects`, sin propietario jerárquico y sin herencia. Esta clave no contiene
el separador de los contextos `proyecto:elemento` y no colisiona con ellos.
No se añaden preferencias paralelas, columnas, archivos ni migraciones.

El escritor emite JSON 59 cuando existe ese contexto o el campo `favorites`.
El lector admite v1–59 y rechaza ambos elementos en formatos anteriores a v59.
Se validan enum, campos, contexto e independencia de la herencia antes de
reemplazar datos. Exportación y restauración transaccionales incluyen las filas
mediante el snapshot existente; un fallo conserva los datos confirmados.
Los respaldos nuevos requieren un lector v59; las versiones anteriores de la
aplicación los rechazan en lugar de perder silenciosamente la preferencia.
No cambia la versión de la app ni el cifrado. Pruebas dirigidas y alcance en
`docs/SORT_FILTER_GROUP_2.md`.

# Retirada de Skins personalizadas — compatibilidad sin cambio de formato

El Grupo 4 retira el sistema visual activo, editor, importación e intercambio de
Skins. Los apartados históricos siguientes describen los formatos que el lector
sigue admitiendo; no indican que esas funciones permanezcan disponibles.

Las claves `custom_themes`, `custom_theme` y `theme_favorites`, sus previews inline
y los originales cifrados `theme-originals` se conservan como datos históricos.
El lector de `backup/legacy` valida los schemas 1–4 y remapea referencias al
restaurar con el protocolo transaccional existente. Los originales y adjuntos
normales siguen incluyendo sus bytes y validaciones de integridad. No se cambia
Room, la selección de versión JSON ni el contenedor de backup.

La apariencia activa no interpreta ni aplica esos payloads. Si existe una Skin
activa histórica, usa el tema oficial Charcoal; elegir un tema oficial elimina
solo la selección histórica, conservando biblioteca, originales y preferencias
normales. Las reservas y los archivos antiguos no se borran ni caducan por esta
retirada. No se reactivan editores o decodificadores visuales al restaurar.

## Imagen independiente de Núcleos Banco

La clave opcional `bank` en `CustomTheme.images` usa el mismo `ThemeImage`,
validación de hash, bytes de vista previa y originales cifrados que las demás
superficies. El editor incluye una vista previa Banco. Sin `bank` se utiliza
`core`, manteniendo el aspecto de temas anteriores. No cambia Room ni el
contenedor de backup: biblioteca, intercambio y restauración recorren el mapa
completo, incluidos bytes y originales; eliminación, reemplazo, reservas y
limpieza siguen el mecanismo común existente. Se conserva el formato de temas.

# Skins con originales — JSON 41 / Room 48

El contenedor v2 y ANBACK01 se conservan. JSON 41 se emite cuando el manifiesto
incluye `theme-originals`. Los lectores admiten 1–41; los respaldos anteriores
sin originales continúan inicializando ese conjunto vacío.

Las preferencias de Apariencia contienen ThemeImage schema 3 con metadatos
opcionales: name (UUID.img relativo), sha256, byteSize, width, height, mime y
orientation EXIF (1–8). Los schemas 1/2 continúan admitidos. El PNG normalizado
histórico permanece dentro del tema como preview; el original completo se incluye
una sola vez por nombre en imageFiles/imageContents, junto a sus bytes por streaming.
No se guarda una URI ni una ruta absoluta. El MIME real se verifica con el decoder.

Cada fuente conserva sus bytes exactos, con límite 20 MiB y 100 MP; el límite
acumulado de imágenes por streaming sigue en 1 GiB y el JSON en 16 MiB. Se valida
igualdad exacta de referencias/manifiesto, tamaños, SHA-256, dimensiones, MIME y
orientación antes de sustituir datos. No se acepta un respaldo sin sus originales.
La restauración prepara fuentes cifradas con nombres nuevos y reescribe las
referencias en la misma transacción de preferencias. El journal protege archivos
viejos/nuevos; un fallo revierte Room y elimina solo staging sin referencias/reservas.
La limpieza de inspecciones reconoce únicamente UUID.img del directorio propio.

Las reservas de editores y diarios de liberación/restauración son estado operativo
local y quedan fuera del backup. No caducan por tiempo. Finalizar un editor libera
sus reservas mediante registro durable; el archivo solo se retira después del
commit y de comprobar todas las referencias y reservas compartidas. Un editor
interrumpido antes de finalizar puede conservar su reserva para evitar pérdida.

Intercambio individual: se mantiene JSON. Sin originales se exporta el tema
histórico directamente; con ellos se usa kind `arachn0de-theme-with-originals`,
schema 1, `theme` (JSON del tema como cadena) y `originals` (name/data base64).
El importador comprueba el conjunto exacto y publica fuentes cifradas con nombres
nuevos antes del commit. Los errores o cancelación liberan únicamente el staging
propio; la biblioteca anterior se conserva. Los límites son 20 MiB por fuente,
150 MiB para el documento de intercambio y el presupuesto previo para metadatos.

# Respaldo offline de Arachn0de

## Banco/Pago — JSON 58 / Room 55

JSON 58 se emite cuando una operación no tiene `contextAccountId` o una asignación
no tiene responsable/pagador identificado. Los campos siguen presentes con null
explícito: `bank_operations.contextAccountId` solo para DIRECT;
`bank_allocations.personId/personName` y `sourcePersonId/sourcePersonName` como
parejas opcionales. PAYMENT conserva cuenta válida y pagador derivado del titular.
Los lectores 1–57 mantienen sus campos obligatorios anteriores, sin reinterpretar
referencias históricas. Los DIRECT antiguos conservan sus cuentas de contexto.
Se mantiene el resto del JSON, el contenedor cifrado y la versión de aplicación.
Snapshot y restauración usan las mismas cuatro colecciones financieras y la misma
transacción. Se validan relaciones, nombres emparejados, sumas, saldos y pendientes
antes de reemplazar. No hay archivos nuevos ni cambios en su liberación.

Room 54→55 copia exactamente las operaciones y asignaciones a staging transaccional,
reconstruye sus tablas/FKs/índices y restaura filas y triggers. Desvincula temporalmente
solo las autorreferencias de la tabla que se sustituye, después de copiar sus valores;
los repone sin cambios y compara ambas direcciones con EXCEPT y `foreign_key_check`.
Un fallo revierte también esquema, versión e historial. Las monedas y estados
históricos no se convierten ni se corrigen al migrar o restaurar.
Pruebas y comprobación SQLCipher pendiente: `docs/BANK_PAYMENT_IMPLEMENTATION.md`.

La restricción de nuevas operaciones a CLP no cambia el formato ni las versiones
del backup. Los lectores, snapshots y restauraciones siguen conservando importes,
unidades menores y códigos de moneda históricos, incluidas tarjetas, reglas,
valores iniciales y plantillas. La restauración no pasa por las restricciones de
creación de nuevos pagos y no convierte ni elimina monedas anteriores.

## Plantillas estructurales — payload saved_templates v2

La tabla y el campo de backup `savedTemplates` no cambian. Sus payloads v1 siguen
leyéndose; v2 conserva un árbol ordenado por padres anteriores, propiedades
reutilizables, etiquetas por nombre, valores iniciales locales, fotografías con
encuadre y bytes completos de adjuntos en base64. No contiene rutas ni referencias
persistentes al elemento original. El validador comprueba límites, relaciones,
marcadores, integridad de recursos y tipos antes de restaurar. Límites: 8 MiB por
payload y 4096 elementos/recursos, además de los límites globales existentes.

Room, SQLCipher, JSON exterior y contenedor no cambian. Esta versión lee v1 y v2;
versiones antiguas que solo conocen v1 rechazan respaldos con plantillas v2.
Tecnologías y gestión/Sprint/Gantt/Kanban se retiran de la interfaz conservando
sus registros y lectores históricos. No se eliminan ni convierten sus datos.

Las plantillas cifradas no mantienen archivos externos: sus bytes son parte del
registro. El diario privado `template-insertions/<uuid>.pending` es estado
operativo regenerable excluido del backup; recupera reservas de inserciones
interrumpidas bajo el mecanismo común, sin caducarlas por antigüedad. Las
instancias usan los almacenes, cifrado, reservas y diarios existentes. Véase
`docs/SIMPLIFICATION_STRUCTURAL_TEMPLATES_BLOCK_6.md` para las reglas de copia,
exclusión de datos personales/operativos y validación.

## Retirada de temas del catálogo — sin cambio de formato

DarkGrey, BlackAndWhite y Graphite conservan sus identificadores y paletas internos
para leer preferencias y skins históricas. Ya no son opciones del catálogo.
Al leer o recargar `appearance_preferences`, un `theme` con esos nombres se
reemplaza mediante commit por `Arachn0de` (Charcoal), conservando las demás claves.
El mismo criterio se aplica a la caché regenerable de desbloqueo al resolverla.
No se modifica `custom_themes`, su campo `base`, `custom_theme` activo ni imágenes:
una skin personalizada sigue heredando exactamente su paleta histórica.
Los lectores y validadores de backup mantienen esos nombres; no cambia Room,
JSON ni el contenedor. La normalización se realiza después de restaurar, al leer
las preferencias, sin reescribir archivos históricos.

Validación dirigida: seis pruebas satisfactorias de catálogo/UI, predeterminado,
persistencia de temas disponibles, migración tras restauración/recarga, skins
históricas con bytes de imagen y respaldo cifrado real. Este último conserva una
skin basada en Graphite con sus cinco imágenes, rechaza imágenes corruptas antes
de reemplazar datos y restaura preferencias históricas de los tres temas retirados.
Sin migración Room, nueva versión de formato ni APK oficial.

## Personas en publicaciones — JSON 40 / Room 48

Room 47→48 añade únicamente `person_memories.peopleVisible` (booleano, default
true) y la tabla `person_memory_people`. Cada asociación guarda ID propio,
`memoryId`, `personId` opcional, `label` textual original, `profileName` de respaldo
y posición. La publicación elimina sus asociaciones por CASCADE; eliminar el
perfil pone su FK a NULL. El borrado oficial captura antes el nombre actual en la
misma transacción, sin borrar asociaciones de publicaciones de otros perfiles.

JSON 40 incorpora `personMemoryPeople` y `peopleVisible` en `personMemories`.
Se emite si existen asociaciones o alguna publicación oculta Personas; los demás
casos conservan la selección histórica de versión. El lector admite 1–40; fuentes
1–39 inicializan asociaciones vacías y visibilidad true (sin personas que mostrar).
No cambian el contenedor v2, ANBACK01, contraseñas ni versiones de la aplicación.

Snapshot, estimación de bytes, límites globales, exportación y restauración incluyen
las asociaciones y la visibilidad, incluso cuando están ocultas. Se validan IDs
únicos, propietarios, perfiles, nombres no vacíos, posiciones y ausencia de perfiles
duplicados antes de reemplazar datos. Máximo 200 asociaciones por publicación;
los nombres históricos de perfiles se conservan completos bajo los límites globales
de metadatos. Las referencias libres nuevas/editadas evitan duplicados normalizando
espacios, Unicode NFC y mayúsculas. Dos perfiles distintos con igual nombre siguen
siendo personas distintas; al eliminarlos no se fusionan ni pierden sus asociaciones.

Las asociaciones se restauran después de publicaciones y perfiles, en la misma
transacción. Los lectores antiguos rechazan JSON 40. Los nuevos datos residen solo
en la base cifrada; no añaden archivos ni preferencias externas. El respaldo conserva
la información oculta porque es una copia privada completa. Descargar la fotografía
no incorpora listas de personas, estén visibles u ocultas. No hay exportador de
publicación con personas ni marcadores visuales en este bloque.

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

La extensión sigue siendo `.arachnode`. El esquema actual es Room **48**, con la cadena histórica completa de migraciones. Las secciones siguientes conservan la documentación de los formatos anteriores. No cambia el protocolo de firma.

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

appearance_preferences conserva theme y text_size. Dark se guarda como "Dark";
Charcoal conserva el identificador histórico "Arachn0de" y es el predeterminado.
No hay claves nuevas ni cambios de formato. Dark, NightBlue y DayGrey se
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

Dentro del namespace existente `appearance_preferences` se admiten quince claves
opcionales `indicator_color_<id>`, para people, technologies, notes, tasks, files,
due, today, high, verify, birthday y effort_1 a effort_5. Sus valores son strings tipados: WHITE, GREY, RED, ORANGE,
YELLOW, GREEN, BLUE, VIOLET o LILAC. Se valida tanto la identidad como el valor antes de
restaurar; colores o identificadores desconocidos en este prefijo se rechazan.

Las claves viajan en privatePreferences del snapshot transaccional y el respaldo
cifrado existentes. No cambia el esquema Room, la versión JSON ni el contenedor: son valores
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

### Recepción externa exclusivamente textual

Los envíos nuevos conservan texto, títulos y enlaces como notas (`linkNote=true`),
sin importar streams multimedia. Se conservan el texto mixto y todas sus URLs;
solo se omiten marcadores de medios explícitos en líneas independientes.
El diario operativo mantiene su versión y estructura; los pendientes anteriores
y su recuperación multimedia no se modifican. Las notas usan Node NOTE y el
respaldo existente, sin migraciones, nuevos tipos ni cambios de versión.

## Pomodoro — preferencias privadas, sin nuevo esquema

El namespace `pomodoro_preferences` usa únicamente `checkpoint`, string JSON
acotado a 4096 caracteres, versión interna 2 (lector compatible con 1). Se incluye automáticamente en
`privatePreferences`, con el cifrado, snapshot y restauración transaccional ya
existentes. No se añaden tablas, columnas ni campos al formato de backup.

El checkpoint contiene `version`, `revision`, `config` (focus/short/long en minutos
1–180, sessions 1–12, ticking/sound booleanos y appearance) y `session` nullable.
`appearance` acepta exclusivamente SOLAR, NIGHT_BLUE, SOFT_GREEN o MINIMAL;
se respalda y restaura en el mismo checkpoint cifrado. La versión interna 1 carece
del campo y recibe SOLAR, conservando su configuración y sesión. La versión 2
requiere el campo y rechaza tipos o valores desconocidos antes de reemplazar datos.
No cambia la versión exterior del backup; lectores antiguos del checkpoint no
pueden leer la versión interna 2. La sesión usa
phase FOCUS/SHORT_BREAK/LONG_BREAK, status READY/RUNNING/PAUSED/FINISHED, number y
cycle coherentes (1–12), total y remaining en ms, wall/elapsed no negativos y boot
no negativo. Los tipos y campos son estrictos, sin rutas ni identificadores externos.
Las pausas conservan el restante; una fase en marcha conserva sus anclas. Restaurar
reconstruye el tiempo por fecha y lo ancla al dispositivo actual; si ya venció deja
la siguiente fase lista, sin encadenar fases ni iniciar trabajo automáticamente.
Una ausencia histórica equivale a 25/5/15 minutos, cuatro sesiones, tic-tac apagado,
aviso sonoro activado y ninguna sesión. Un payload inválido falla antes de sustituir
los datos anteriores, incluyendo la configuración viva del módulo.

`pomodoro_clock_cache` es una copia operativa regenerable, sin texto ni información
personal, para el aviso con el almacén bloqueado o tras reiniciar. No forma parte
del backup y nunca prevalece sobre el checkpoint cifrado al autenticar/restaurar.
No se exportan permisos, canales de notificación ni alarmas de Android; se vuelven
a programar desde el estado recuperado. La caché tiene un solo registro limitado,
se sobrescribe al cambiar de estado y elimina la sesión activa al finalizar.
No hay archivos de usuario que liberar, bytes adicionales ni migraciones.

## Fechas informativas del Calendario — sin nuevo formato

El namespace privado `calendar_special_preferences` contiene exclusivamente
`holidays`, `saints`, `commemorations` y `birthdays`, todos booleanos. Su ausencia,
incluso en respaldos históricos, equivale a true/false/false/true. El nuevo filtro
birthdays solo controla visibilidad; las fechas siguen en person_birthdays y se
respaldan con el mecanismo preexistente, sin duplicarlas ni cambiar su formato. Se exporta y restaura mediante
la colección `privatePreferences` existente, sin nuevas tablas ni cambios en el
formato JSON. Se rechazan tipos incompatibles y claves ajenas antes del reemplazo
transaccional; una restauración rechazada conserva datos y preferencias anteriores.
Las instancias de pantalla escuchan la recarga de preferencias tras el commit.

El catálogo `assets/calendar_special_dates.json` es un recurso fijo de la APK:
16 feriados nacionales de 2026, 15 fechas nominales del santoral propio CECh
(Ordo 2022, p. 13) y diez conmemoraciones ONU. Sus referencias y cobertura están
en el recurso y en la interfaz. No se edita, descarga ni copia al almacenamiento
privado; no se serializan rutas ni se requieren bytes de archivos privados en el
backup. El recurso se recupera con la instalación de la aplicación. Desactivar
una categoría no elimina entradas. No hay adjuntos, cachés, reservas, crecimiento
de archivos ni nuevos permisos. No se crean ni modifican eventos personales,
registros de tareas, recurrencias o notificaciones.

## Green Bash — tema de presentación de General

Se selecciona mediante `theme=GreenBash` en `appearance_preferences`, igual que
los demás temas. Se exporta/restaura en `privatePreferences`, mediante el namespace
cifrado existente, sin nuevas tablas, archivos ni cambios de formato.
La clave histórica opcional `bash_theme` sigue validándose como booleana:
true se interpreta como Green Bash, su ausencia conserva el tema almacenado.
Elegir un tema elimina la clave histórica en la misma escritura, evitando que
reactive el modo al reiniciar. Cada perfil mantiene sus propias preferencias.
Las rutas, extensiones y permisos Unix son representaciones visuales sin archivos
ni datos adicionales de respaldo.

## Pomodoro — progreso manual y sonidos locales (2026-10-18)

El checkpoint conserva el formato interno 2 salvo cuando la fase alcanza cero
mediante un ajuste manual: entonces usa versión 3 y exige `session.manualEnd`
booleano. Esta marca solo admite remaining=0 y evita transiciones automáticas;
conserva RUNNING/PAUSED y se retira al retroceder o finalizar. Los lectores aceptan
1–3; 1–2 inicializan false. No cambia Room ni la versión exterior del backup.

`pomodoro_sounds` es un namespace opcional de privatePreferences con dos claves
opcionales string: `ticking` y `completion`. Cada una contiene JSON estricto con
version int 1, sha256 hexadecimal del contenido y data base64 canónica. Se limita
cada original a 512 KiB; se admiten firmas WAV/OGG/MP3 y Android debe poder preparar
el sonido (1 ms–30 s) antes de guardarlo. El backup incluye los bytes originales,
valida tamaño, estructura, firma y SHA-256 antes del reemplazo transaccional.
No contiene URI, rutas absolutas ni permisos de acceso al documento original.
Sin namespace/claves se usan los sonidos integrados, incluso tras restaurar un
backup histórico. La recarga de preferencias reemplaza también la reproducción
actual. Los originales personalizados no se copian a pomodoro_clock_cache:
con el almacén bloqueado se utiliza el aviso integrado.

Importar prepara/valida en memoria antes de un commit del almacén cifrado. Cancelar
el selector, fallar la lectura/decodificación o rechazar el commit conserva la
selección anterior. Reemplazar/restablecer elimina el valor anterior en la misma
escritura; las dos claves son copias independientes aunque compartan bytes. No hay
archivos privados nuevos, staging físico ni cachés multimedia que liberar. Al
restaurar se aplican el snapshot/transacción y las reservas de backup existentes.
La aplicación conserva como máximo dos clips; el reproductor lee de memoria.
Los WAV integrados en res/raw son recursos regenerables de la APK y no se exportan.

## Metro: Ruta Expresa provisional — viaje interno v8

Sin migración Room ni cambio de contenedor/JSON exterior: el viaje sigue siendo un
payload opaco validado por MetroCodec. Los viajes con modelos expresos nuevos, incluso
en originales, archivos o undo, usan versión interna 8. Los v1–v7 siguen leyéndose y
no reciben un modelo nuevo al importar. Sin modelos nuevos el escritor conserva la
versión histórica mínima. Un lector antiguo rechaza v8 antes de restaurar.

La ruta puede incorporar expressTiming: objeto estricto con version int 1 y baseMillis,
array de enteros largos, uno por paso físico. Exige algún servicio expreso; cada valor
debe coincidir con el catálogo propio de la ruta: RIDE expreso sin detención en su
extremo=105000 ms, RIDE con detención/NORMAL=120000, cambios=240000 y WAYPOINT=0.
Se rechazan valores, tipos, longitudes y versiones incompatibles antes de sustituir
registros. Las predicciones se validan contra esa base, conservando sus medias,
conteos, exclusiones y peso originales. Sin expressTiming se conserva la base previa.

El snapshot y el backup existentes transportan la ruta, planes, sesiones, originales,
archivos, undo y capturas con todos sus campos. Las capturas mantienen versiones 1/2,
con viaje v8 interno cuando corresponde; los intervalos nuevos expresos usan un sufijo
de contexto /express-timing-v1. El contexto histórico se conserva, sin reclasificar
muestras ni cambiar sus milisegundos reales. NORMAL, servicios, sentidos y líneas no
comparten evidencia. No hay nuevos archivos, reservas ni reglas de liberación.

## Wireframe: namespace opcional `wireframe_library`

Sin migraciones Room ni cambio del contenedor/versiones de la aplicación. Se usa
el transporte existente de `privatePreferences`: claves UUID de dibujo, valores
string con JSON documental versión 1. Cada documento contiene exactamente identidad,
nombre, formato, fondo, creación/modificación, lista ordenada de elementos y assets.
Los elementos incluyen UUID, tipo estático, coordenadas/tamaño relativos, texto,
colores ARGB, borde/radio/opacidad, tipografía/alineación, icono integrado, estado
visual y referencia de imagen. Los assets usan UUID → {data: base64 PNG, sha256}.
Nunca se guardan rutas absolutas, URI ni dependencias del archivo externo.

Validación previa al reemplazo: versión/campos/tipos estrictos, UUID y unicidad,
fechas, valores finitos dentro del lienzo, enumeraciones conocidas, referencias
exactas sin imágenes huérfanas, SHA-256 y decodificación PNG. Límites: 32 dibujos,
128 elementos y 16 imágenes por dibujo; PNG normalizado de 1 MiB / 1024×1024;
JSON de documento hasta 3 Mi caracteres, limitado además por el namespace
serializado completo de 1,5 MiB UTF-8 (para no exceder CursorWindow en Android).
Este límite es compartido por los dibujos e imágenes de la biblioteca. También rigen los límites globales
existentes del backup; superarlos produce error explícito, nunca omisión.

Las imágenes viven en el mismo valor confirmado que sus elementos: reemplazo y
eliminación se realizan mediante el commit atómico existente. Una imagen compartida
por elementos se conserva hasta retirar su última referencia. Las copias de dibujos
son independientes. Un editor cancelado no escribe imágenes. No hay archivos
privados temporales/confirmados que barrer ni rutas que reconstruir. La recuperación
tras fallo usa la atomicidad de SQLCipher y el protocolo de restauración existente.
La exportación PNG elegida por el usuario en SAF es externa; no es fuente del dibujo.
Miniaturas e historial son regenerables/de sesión y no se respaldan.

El snapshot/restauración transaccional existente incluye el namespace completo.
El validador WireCodec se ejecuta antes de sustituir datos. Corrupción o referencias
inválidas preservan el estado anterior. Un backup histórico sin este namespace
restaura una biblioteca vacía. Lectores históricos transportan genéricamente las
preferencias aunque no ofrecen el módulo; una versión documental desconocida se
rechaza, sin intentar convertirla silenciosamente.

### Wireframe 2: identidad de plantillas

Los documentos que contienen plantillas usan versión documental 2 y añaden
`template` nullable a cada elemento. El identificador debe existir en el catálogo
y corresponder al tipo del elemento; valores desconocidos se rechazan antes del
reemplazo. Los documentos versión 1 siguen leyéndose con template=null y conservan
su renderizado y propiedades anteriores. Sin plantillas se sigue escribiendo v1.
La plantilla define la disposición estática; texto, relleno y tinta permanecen en
el elemento. Las ocho categorías y sus valores iniciales son recursos de código,
no archivos privados. No cambia Room ni el formato exterior del backup.

El namespace cifrado, validación, snapshot y restauración atómica existentes
transportan íntegramente v1/v2, incluidas imágenes originales embebidas. No se
crean archivos privados ni nuevas reglas de limpieza: las imágenes continúan
liberándose al retirar la última referencia del documento confirmado. Los
borradores de arrastre e historial son estado de sesión; un gesto cancelado no
confirma cambios. Un lector documental anterior rechaza v2 explícitamente.

### Wireframe: documentos creados desde moldes de pantalla

Los 25 moldes son recursos de código. Insertarlos produce elementos ordinarios
v2 con identificadores de los componentes existentes y UUID propios. No se guarda
el ID del molde ni se introduce un nuevo tipo, relación, preferencia o archivo.
Los marcadores de imágenes se dibujan localmente y no requieren adjuntos.
Guardar y respaldar transporta el documento íntegro mediante `wireframe_library`,
con las validaciones, límites y restauración transaccional existentes. Se conserva
la lectura de dibujos v1/v2 y de backups históricos sin biblioteca. No cambia el
formato del backup ni las reglas de liberación de archivos.

### Wireframe 3: contenido de tamaño fijo, iconos y anotaciones

El documento usa versión 3 cuando existen anotaciones, sustituciones de iconos o
redimensionamiento por bordes. Conserva los campos v2 y añade a cada elemento:
`contentUnit`, `contentW`, `contentH` (los tres null o números positivos finitos
hasta 64; dimensiones de referencia normalizadas) e `icons` (mapa de índices de
posición canónicos a claves de recursos Lucide locales). Los índices deben existir
en el componente actual. Sin estas prestaciones se sigue emitiendo v1/v2.

La raíz añade `annotations`: hasta 128 registros ordenados con UUID único, `x`,
`y` finitos (entre 0 y 1 sin vínculo o entre -1 y 1 con vínculo), `title` de hasta 200 caracteres, `text` de hasta 16000 y `elementId`
nullable. Sin elemento la posición es relativa al lienzo; con elemento, son desplazamientos respecto a su origen, normalizados al lienzo. El propietario debe existir en el mismo documento. No son elementos,
imágenes ni entidades externas. Su número se deriva del orden; no se persiste otra
numeración. Al retirar un elemento desde el editor se preservan sus anotaciones
como posiciones independientes, con la operación completa disponible para deshacer.

Todo se transporta dentro del namespace cifrado `wireframe_library` mediante el
snapshot y restauración atómicos existentes. No cambia Room ni el contenedor del
backup. Siguen vigentes los límites globales documentales/de biblioteca; el rechazo
por tamaño o integridad preserva los datos anteriores. Se leen documentos v1/v2 y
backups históricos sin el namespace. Referencias de anotación, índices de iconos,
claves, tipos o versiones desconocidos se rechazan antes de reemplazar datos.
Las aplicaciones anteriores que no conocen v3 lo rechazan explícitamente.

Las exportaciones PNG son documentos externos elegidos por el usuario y no forman
parte del backup: diseño, marcadores y hojas se regeneran íntegramente desde los
datos cifrados. La visibilidad de marcadores y las URI transitorias para compartir
son estado de sesión. No hay archivos privados nuevos ni nuevos ciclos de limpieza
interna. La salida normal permanece libre de marcadores incluso si están visibles
en el editor.

### PNG editables de anotaciones (`arNd`, versión 1)

Sin cambio de versión Room ni JSON: son adjuntos `image/png` convencionales en
`attachmentFiles` y sus asociaciones existentes. El contenedor incluye todos sus
bytes cifrados, incluido el chunk privado ancillary `arNd` anterior a IEND. No se
serializan rutas del dispositivo ni IDs de otros archivos.

Payload del chunk (enteros de 32 bits big endian): versión 1, longitud de la fuente,
bytes fuente PNG/JPEG originales, y JSON UTF-8 de marcas hasta el final del payload.
Cada marca conserva UUID, herramienta, lista de puntos normalizados, color ARGB,
grosor relativo al lado menor y texto. La numeración de marcadores se deriva de su
orden, sin una segunda lista susceptible de desincronizarse. La fuente no puede
contener otro chunk editable; reeditar conserva siempre la primera fuente.

Importación y validación previa de backup/restauración verifican tamaño y SHA-256
existentes, límites de chunks, CRC del chunk privado, versión, unicidad de UUID,
coordenadas finitas en 0..1, grosor permitido, textos y límites de marcas/puntos/JSON,
y validez de la fuente mediante dimensiones y decodificación muestreada. Un chunk
privado inválido aborta la operación antes de reemplazar datos. Las imágenes sin
este chunk siguen el formato histórico; no requieren migración. Restaurar conserva
IDs lógicos y escribe nuevos storageName mediante el staging y diario actuales.
Las reservas de editores sin confirmar continúan excluidas del backup oficial.

La fuente está dentro de los bytes de la copia: se restaura incluso si el adjunto
original ya se eliminó. La última referencia de una copia libera un solo archivo,
incluidos sus datos editables y su fuente, con la misma limpieza durable que los
adjuntos anteriores. Las exportaciones y el compartidor público eliminan `arNd`;
no sustituyen un backup y esos PNG públicos no permiten recuperar marcas editables.

## Biblioteca Diagramas — namespace v1

`diagram_library` reutiliza `privatePreferences`: sin nueva tabla, migración Room,
versión de aplicación ni cambio de contenedor. Los lectores históricos siguen
aceptados. En bóvedas protegidas se cifra por SQLCipher y el backup portable usa
ANBACK01 con la contraseña del respaldo, como los otros namespaces.

El mapa contiene `doc_UUID` con JSON documental versión 1 y `asset_SHA256` con PNG
base64 canónico. Los documentos incluyen nombre, fondo ARGB, nodos con geometría
normalizada, texto, colores/borde y recurso opcional; conexiones con UUID, IDs de
extremos, ruta, puntos intermedios, flechas, color/grosor/etiqueta y configuración
completa de flujo (objeto, imagen opcional, dirección, velocidad, repetición, cantidad
y tamaño). No hay rutas absolutas. Recursos compartidos se incluyen una sola vez,
con todos sus bytes y SHA-256; se validan PNG, límites y decodificación antes de usar.

Snapshot/exportación incluyen el mapa completo, incluso iconos de biblioteca todavía
no usados por ningún diagrama. Son recursos confirmados de la colección personal,
no archivos huérfanos. Borrar un documento no implica borrar iconos. Eliminar un icono
requiere ausencia de referencias confirmadas; cancelar una edición no elimina recursos
compartidos. Todo reemplazo/eliminación es una transacción de preferencias cifradas,
sin operaciones de archivos que necesiten recuperación ni limpieza por antigüedad.

Restauración valida previamente namespace, hashes, identidades, dimensiones finitas,
límites, extremos de conexiones y referencias a recursos, incluidas imágenes animadas.
Un recurso ausente o dañado aborta sin sustituir datos. Las versiones/documentos no
admitidos fallan explícitamente. Se conservan UUID y hashes al restaurar; las fuentes
anteriores sin `diagram_library` siguen siendo válidas y dejan una biblioteca vacía.
Los borradores de edición no confirmados son estado de instancia, no datos del backup.

Límites del namespace y del documento: ver ARCHITECTURE.md, sección Diagramas. Se
mantienen además los límites globales del respaldo: nunca se truncan datos para que
quepan. Las miniaturas y las geometrías son regenerables en memoria y no se exportan.
El PNG público no contiene recursos editables ni configuración de animación; no
sustituye al respaldo completo y no constituye una exportación animada.

### Diagramas v1: cámara opcional y exportaciones públicas

Los documentos v1 admiten `camera: null` o `{cx,cy,scale}`: números finitos, cx/cy
entre −1 y 2, scale entre 0,01 y 8 (dp por píxel lógico). Ausencia del campo en
fuentes históricas equivale a null/encuadre inicial. Cámara confirmada, nodos,
conexiones, flujos y PNG se exportan/restauran juntos en `diagram_library`, sin rutas
ni cambios de Room48/JSON40. La restauración valida cámara antes de sustituir datos;
la prueba de backup cifrado incluye una cámara no predeterminada y conserva recursos
compartidos, IDs y animaciones. No se aumenta ningún límite del namespace.

Los GIF/MP4/PNG compartidos son salidas públicas elegidas por el usuario, no fuentes
editables ni sustitutos del backup. Su diario `files/diagram-export-journal` contiene
solo metadatos operativos cifrados de exportación/limpieza, nunca bytes necesarios
para reconstruir un documento; se excluye del backup, igual que permisos SAF y las
opciones de exportación no confirmadas. No hay cachés de fotogramas ni videos privados.
Cancelación/fallo retira solo la salida de su operación; el diario durable permite
reintentar tras rechazo del proveedor o interrupción al volver a abrir Diagramas.
Las salidas completadas no se eliminan en la recuperación. No caducan pendientes.
El PNG conserva su exportación estática anterior; GIF/MP4 son derivados animados con
las restricciones de paleta, transparencia y codec documentadas en DIAGRAMAS.md.

### Diagramas v1: fondo de imagen y estilos de trazo compatibles

Campos opcionales añadidos al JSON documental incluido como texto de `doc_UUID`:

- Raíz `backgroundAsset`: SHA-256 de PNG de biblioteca o null; ausencia equivale null.
- Raíz `backgroundFit`: FIT/FILL; ausencia equivale FIT.
- Cada conexión `strokeStyle`: SOLID/DASHED/DOTTED; ausencia equivale SOLID.

Enums presentes inválidos se rechazan, sin interpretarlos como defaults. El recurso
de fondo entra en el conjunto de referencias del documento: su PNG canónico, SHA-256
y todos sus bytes deben existir en `asset_SHA256`. Se comparte sin duplicación con
nodos/partículas; cuenta dentro de los límites actuales. No hay rutas de dispositivo.
La prueba de backup/restauración cifrados incluye fondo FILL, trazo discontinuo,
cámara, imágenes en nodos/flujos, múltiples referencias y rollback por corrupción.
Leer un documento histórico conserva posiciones y aplica únicamente los defaults.

Cambiar/quitar la referencia de fondo es una modificación documental; el PNG sigue
siendo recurso confirmado de la colección personal hasta retirarlo explícitamente,
si no tiene referencias confirmadas. Se mantienen transacción/mutex y recuperación
previos. No se añaden archivos privados, tablas ni migraciones. Exportaciones
PNG/GIF/MP4 usan el mismo renderer para fondo y estilo. Números, cuadrícula, guías,
selección múltiple, modos de preview y rutas de panel son estado de interfaz de
instancia, sin datos confirmados nuevos; no se serializan en el backup. No cambia
el contenedor ni la compatibilidad global hasta JSON40/Room48.

### Diagramas v1: página, diseño, recorridos y colores favoritos

Extensiones opcionales compatibles, sin cambiar Room48/JSON40:

- Raíz `page`: format FREE/SQUARE/STORY/PORTRAIT/WIDE, width/height lógicos. Ausencia:
  FREE 1600×1000. Lados 320..4096, producto ≤8.000.000 y proporción fija validada.
- Raíz `playback`: mode OFF/SEGMENT/SEQUENTIAL/SIMULTANEOUS, route (UUID de conexiones
  contiguas sin repeticiones, ≤192), loop. Ausencia: SIMULTANEOUS, [], true.
- Raíz `backgroundGradient` y nodo `gradient`: null o colors (2..8 ARGB), angle 0..360.
  Ausencia: null; no sustituye los colores históricos guardados.
- Nodo `borderColor`, `radius`, `font`, `lockAspect`, `centeredResize`, `resizeContent`.
  Ausentes: borde=ink histórico, radio20, fuente48, proporción=true para IMAGE,
  centrado=false, CONTAINER. Nuevos documentos serializan sus defaults nuevos.
- Flujo `particleStyle` NORMAL/LUMINOUS/PULSE, `particleColor`, `haloColor`, `glow`,
  `imageHalo`. Ausentes: NORMAL, null, null, 0.6, false. Color null conserva la
  convención histórica de esfera ámbar/punto con color de conexión. Glow 0..1.
- Clave de biblioteca `favorite_colors`: JSONArray textual de hasta 32 enteros ARGB
  únicos. Ausencia: lista vacía. Comparte snapshot, cifrado y restauración íntegra
  de `diagram_library`; admite como máximo 81 claves (32 docs+48 PNG+favoritos).

Enums/tipos presentes inválidos, recorrido roto, referencias faltantes y degradados
inválidos abortan antes de sustituir datos. No se serializan rutas absolutas ni se
omiten bytes de recursos. La prueba cifrada incluye estos campos, colores favoritos,
PNG compartidos y rollback; backups anteriores sin namespace siguen siendo válidos.
Opciones de exportación, selección temporal de color/degradado, indicadores de borde
y dirección de duplicación de interfaz no son datos documentales confirmados.

## Temas personalizados — claves opcionales de Apariencia (Room 48 / JSON 40)

Dentro del namespace existente `appearance_preferences`, el payload de
privatePreferences puede incluir tres Strings opcionales:

- `custom_themes`: array JSON de temas portables esquema 1, máximo 16/4 MiB.
- `custom_theme`: UUID de un tema de esa biblioteca; nunca una referencia huérfana.
- `theme_favorites`: array de hasta 32 enteros ARGB únicos.

Cada tema contiene únicamente kind/schema/id/name/description/base/colors/paints/
header. Header incorpora bytes PNG base64 completos y SHA-256, más modo y encuadre;
no serializa rutas. Los paints contienen un sólido o un degradado lineal/radial/
angular con 2–8 puntos ordenados, ángulo e intensidad. El codec es estricto y
comparte validación entre importación, persistencia y restauración. Recursos
corruptos, versiones desconocidas, activos inexistentes o bibliotecas excesivas
fallan antes de reemplazar datos; se conserva el estado previo. Siguen aplicándose
los límites globales del backup y no se omiten recursos para hacer caber un archivo.

No cambia el esquema Room ni la versión máxima JSON 40: son extensiones opcionales
del payload ya soportado de preferencias privadas. Respaldos históricos sin esas
claves restauran biblioteca/favoritos vacíos y tema oficial conforme al lector
anterior. La exportación cifrada oficial incluye todos los bytes inline; el JSON
portable compartido por SAF es independiente, legible y no contiene otras
preferencias ni claves. Documentación completa: TEMAS.md.

La imagen pertenece al valor confirmado de su tema: reemplazo/eliminación se
confirma atómicamente, duplicación conserva una copia y cancelación solo descarta
memoria. No hay archivos privados compartidos ni tareas de borrado de imágenes.
El borrador temporal retenido en ViewModel, miniaturas y el diario cifrado
`theme-export-journal` son estado operativo excluido del respaldo. El diario solo
recupera exportaciones SAF parciales propias; nunca elimina archivos ya confirmados.

Validación dirigida: CustomThemeTest comprueba el archivo cifrado oficial y su
restauración completa de PNG, hash, modo/encuadre, colores, degradado multipunto,
base activa y favoritos; inyecta fallo de escritura y corrupción antes de reemplazo,
y restaura preferencias históricas sin las claves nuevas. La batería final completa
incluye estos casos y termina **178/178** sin fallos. Detalle en TEMAS.md.


## Informes PDF / Word derivados

Los informes no introducen datos confirmados ni cambian Room 48, el contenedor
v2 o la compatibilidad JSON 1–40. La fuente sigue siendo el snapshot y adjuntos
originales del backup oficial; generar un informe no modifica ni elimina ninguno.
Opciones, contenido preparado e imágenes normalizadas viven en memoria.

Las salidas SAF confirmadas son documentos públicos del usuario, independientes
del respaldo privado. El diario cifrado `document-export-journal` es estado
operativo excluido: registra exclusivamente salidas parciales propias, conserva
fallos de borrado y recupera al reabrir la exportación, sin borrar documentos
confirmados ni aplicar caducidad. Se reutilizan el bloqueo común y las reservas
del diario existente. Pruebas de lectura sin mutación, cancelación, fallo de
escritura y recuperación se detallan en EXPORTACION_DOCUMENTOS.md.


## Temas simplificados — extensión portable 2

Dentro de las mismas preferencias cifradas de Apariencia, temas con imágenes de
superficies o muestra seleccionada usan esquema portable 2. Añade `images`, mapa
opcional por destino drawer/core/layer/task/note, y `swatch` (una de diez muestras
o null). Cada imagen contiene PNG base64 completo, sha256 y zoom/x/y. Los temas
antiguos esquema 1 conservan valores y siguen siendo legibles sin conversión.
No cambian Room 48, JSON máximo 40 ni el contenedor v2. Los lectores antiguos de
temas rechazan esquema 2; no ignoran imágenes silenciosamente.

Se reutilizan snapshot, exportación, validación y restauración de preferencias:
los cinco recursos completos y encuadres se recuperan, sin rutas absolutas. Hash,
PNG, destino o encuadre inválidos abortan antes del reemplazo. Límites existentes:
2 MiB por tema, 4 MiB por biblioteca y límites globales de backup. La importación
de fondos reduce copias a 768 px/96 KiB; no altera los originales locales.

Reemplazo/eliminación del valor confirmado es atómico; las copias de temas
mantienen sus bytes independientes. Cancelación solo libera borrador en memoria.
No hay archivos privados nuevos ni barrido de originales. Borradores y bitmaps
regenerables se excluyen; los bytes PNG confirmados siempre se incluyen. Detalle
vigente y validación dirigida en TEMAS.md.


## Diagramas: caras opcionales de conexiones

El documento de `diagram_library` admite versiones 1 y 2. Las conexiones añaden
únicamente fromAnchor/toAnchor opcionales con TOP/BOTTOM/LEFT/RIGHT; ausencia o
null conserva anclaje automático. El escritor omite campos ausentes y mantiene
versión 1 cuando no hay anclajes; usa 2 si existen, para que lectores anteriores
no descarten las caras silenciosamente. El codec exige enums válidos y relaciones
existentes antes de reemplazar datos. El snapshot y respaldo cifrado ya existentes
conservan ambos extremos y sus estilos. No cambia Room 48, JSON máximo 40 ni el
contenedor. No se añaden archivos o recursos; los bytes de imágenes existentes
siguen incluidos y no cambia su ciclo de liberación. Guías, imán y cuadrícula son
estado temporal del editor, no nuevas preferencias confirmadas. Ver DIAGRAMAS.md.


## Diagramas: usabilidad y tabla ERD

Mover, Redimensionar, Conectar, Elementos, Opciones y Eliminar usan seis iconos
fijos de 48 dp en la barra inferior. Conectar activa el modo sin abrir panel;
Opciones ofrece etiquetas de texto y mantiene la lista existente de conexiones
del documento. Historial contenía solo Rehacer, ahora accesible directamente allí.
Vista previa en vivo queda en las acciones superiores. El candado reutiliza
GlobalChromeActions a la derecha de la cabecera de biblioteca y editor.
El primer Guardar solicita nombre; Guardar actualiza el documento, Guardar copia
crea un UUID distinto y Exportar documento produce PNG/GIF/MP4 mediante SAF.
El nombre sigue siendo editable posteriormente. Puntos de recorrido conserva
los mismos puntos intermedios, trayectorias y extremos reasignables.

El nodo TABLE añade una lista fields: id UUID, name (hasta 100 caracteres), type
opcional (hasta 64), pk y fk booleanos estrictos. Máximo 32 campos por tabla;
encabezado en text (hasta 100). Las marcas son visuales y no introducen relaciones
SQL: las conexiones siguen referenciando el UUID del nodo. Los campos pertenecen
al nodo; borrar/reemplazar la tabla los elimina en la misma transacción. Duplicar
conserva una copia independiente de sus valores. No se crean archivos ni recursos.

El codec escribe versión documental 3 solo cuando hay tablas, y mantiene 1/2 para
los documentos anteriores. Lee 1/2/3, valida IDs, tipos, límites y relaciones antes
de confirmar o restaurar. Los nodos históricos conservan fields vacío y su
representación. No cambian Room 48, JSON global 40 ni el contenedor de backup.
Los campos confirmados viajan íntegros en diagram_library, dentro del snapshot
cifrado y la restauración transaccional existentes. Una carga inválida se rechaza
sin sustituir el estado anterior. Sin rutas absolutas ni nuevos ciclos de archivos.

El renderer compartido representa encabezado, campos, tipos y marcas PK/FK en
edición y en exportación PNG/GIF/fotogramas MP4. Ajusta el texto a las filas de la
tabla; los gestos y anclajes existentes mueven/redimensionan/conectan el nodo.
Las ediciones no guardadas siguen siendo estado temporal de instancia; cancelar
el nombre del primer guardado no confirma un documento.

## Borradores — JSON 42 / Room 49

Se emite JSON 42 cuando al menos un registro de `nodes` tiene `projectId: null`.
Sin borradores se conserva la selección histórica de versión. Los lectores admiten
JSON 1–42; 1–41 siguen exigiendo projectId String. En 42 es String o null explícito,
sin usar una cadena vacía, ID reservado ni ruta como sustituto de la ausencia.
Los lectores anteriores rechazan 42 en lugar de descartar contenido.

Un nodo sin proyecto debe ser ACTION o NOTE, sin parentId, creationGroupId,
sprintMode ni workState. Conserva título, descripción, identidad, timestamps,
fechas, prioridad y datos financieros compatibles, así como relaciones por nodeId.
Se validan antes de reemplazar datos las referencias, los tipos, la ausencia de
jerarquía y las invariantes anteriores. No cambian el contenedor v2, ANBACK01,
la extensión, los límites, el cifrado ni las versiones de distribución.

Snapshot y restauración incluyen las mismas tablas nodes, node_person, node_tag,
node_events, node_technologies y node_attachments. Los bytes completos de imágenes
y audio se incluyen por los manifiestos y streaming existentes, con hashes,
tamaños y metadatos de audio. No se serializan rutas absolutas. La preparación
durable, rollback, diario de restauración y liberación posterior al commit siguen
siendo los existentes. Un backup histórico sin estos nodos restaura Borradores vacío.

Mover no cambia relaciones de adjuntos ni genera archivos nuevos. Eliminar un
borrador usa la liberación normal: no borra bytes todavía compartidos ni reservados,
y las limpiezas fallidas se reintentan con el diario previo. Formularios sin confirmar,
reservas y diarios operativos continúan excluidos del contenido confirmado del backup.

Las preferencias de orden históricas de una hoja se conservan al trasladarla:
`projectId:nodeId` pasa a `draft:nodeId` y vuelve al contexto del proyecto al organizarla.
El namespace no representa un contenedor ni se muestra como jerarquía. El validador
solo admite esa clave para el borrador correspondiente y su eliminación retira la
preferencia en la misma transacción. Las ocurrencias recurrentes son recibos sin FK
al nodo; se comprueban explícitamente y no pueden quedar sin proyecto.

Las reservas compartidas del editor protegen también los nombres originales AAC
cuando una restauración prepara archivos nuevos. Son registros operativos del
mismo staging existente, sin caducidad; no añaden entradas al backup y no sustituyen
los bytes obligatorios de adjuntos confirmados. Se liberan después del commit de
su propio editor o al descartar explícitamente su entrada.

## Selector de estados — JSON 43 / Room 50

Room 49→50 añade `projects.verificationEnabled` y `nodes.awaitingVerification`,
booleanos NOT NULL DEFAULT 0. Es una migración aditiva: preserva registros,
fases Sprint, adjuntos y relaciones. JSON 43 añade esos campos; lectores 1–42
inicializan ambos a false. Se emite 43 si hay opción habilitada, tareas por
verificar o la preferencia global de orientación; los demás casos mantienen la
selección histórica de versión. El lector admite 1–43; el contenedor sigue en v2.

«Por verificar» exige ACTION hoja, proyecto real con la opción habilitada,
`isCompleted=false` y ausencia de fase Sprint. Se valida antes de reemplazar
Room y mediante triggers. La desactivación con tareas en ese estado se rechaza.
La orientación vive en las preferencias cifradas existentes `appearance_preferences`,
clave `status_selector_direction`, valores VERTICAL/HORIZONTAL; ausencia=VERTICAL.
Forma parte del snapshot/restauración transaccional y se valida su enumeración.
No se añaden archivos ni exclusiones al backup. Detalles y pruebas en
SELECTOR_ESTADOS.md.

## Skins: ajuste de previews al guardar (sin cambio de formato)

Se conservan schemas de tema 1–3 y el manifiesto de originales existente. Si
un guardado necesita ajustar su presupuesto, un PNG inline histórico puede
conservarse como original cifrado de bytes idénticos antes de reducir su preview.
La nueva referencia utiliza ThemeImage schema 3 y se respalda/restaura con el
protocolo ya establecido: bytes completos, SHA-256, dimensiones, MIME, EXIF,
staging durable, validación previa y transacción de preferencias. No se alteran
otros temas ni sus archivos. Las reservas del editor se liberan después del
commit o cancelación y no se eliminan archivos con referencias o reservas.

Las preferencias grandes se leen por bloques de bytes en la misma transacción;
se reconstruye exactamente el payload antes de decodificar UTF-8. No cambia el
JSON del respaldo, Room ni la representación persistida de private_preferences.
Se verificó restauración cifrada de las previews ajustadas y sus originales,
además de compatibilidad histórica, cancelación y referencias compartidas.

## Estados heredados — JSON 44 / Room 51

Room 50→51 añade `nodes.taskStateCount`, INTEGER nullable: NULL hereda, 2 y 3
son excepciones explícitas. No reescribe tareas ni estados, ni modifica adjuntos.
El Núcleo reutiliza `verificationEnabled` (false=2, true=3); las opciones previas
se conservan, y sin configuración previa hay 2 estados. La excepción más cercana
entre tarea y ancestros prevalece. Borradores mantienen 2 estados; Sprint conserva
sus cinco fases y restricciones independientes.

Los estados siguen siendo `isCompleted` y `awaitingVerification`. La migración y
la restauración no los convierten. Los triggers exigen hoja ACTION, proyecto real,
sin fase Sprint y sin completado simultáneo para Por verificar; se permite
restaurar ese estado histórico en modo 2. El repositorio solo permite entrar
nuevamente en Por verificar cuando el modo efectivo es 3. No puede recibir hijos.

JSON 44 añade taskStateCount a cada nodo y se emite con excepciones o estados
Por verificar conservados en núcleos configurados a 2. El lector admite 1–44;
los formatos anteriores inicializan la excepción a NULL y conservan su validación
histórica. Valores distintos de NULL/2/3 se rechazan antes de sustituir datos.
El snapshot, restauración transaccional, backup cifrado y validación incluyen
la columna; no hay archivos nuevos de usuario ni exclusiones del backup.

### Cambio confirmado de 3 a 2 estados

Las acciones de configuración previsualizan el modo efectivo antes/después de cada
hoja normal. Solo convierten Por verificar → Pendiente cuando el modo pasa de 3 a 2,
respetando las excepciones. Si hay conversiones, se confirma el número antes de
escribir. Configuración, estados e historial REOPENED se guardan en una transacción;
cancelación, alcance cambiado o error conservan los datos anteriores. Volver a 3 no
recupera estados previos. Las APIs sin confirmación rechazan conversiones pendientes.
Room 51, JSON 44 y el cifrado no cambian: se reutilizan los campos y eventos existentes.
La carga y restauración históricas no convierten datos; solo una acción confirmada
puede hacerlo. Se verifican rollback, reapertura y backup/restauración cifrados.

## Exportación parcial de elementos — contenedor JSON 1

Compartir y exportar produce JSON con kind arachn0de-element-export,
schemaVersion 1, partial true, root (projectId, nodeId, descendants),
referencePolicy, records, attachmentPaths e imagePaths. records usa el
serializador y dataVersion existentes del backup, conservando IDs y referencias.
Las referencias fuera de los registros exportados son externas: no se renumeran
ni se sustituyen por datos inventados. Las definiciones compartidas de recurrencia,
responsables, etiquetas, tecnologías y planificación referenciadas son dependencias;
no se incluyen tareas hermanas ni preferencias privadas/globales.

No es un respaldo completo y no se admite en Restaurar backup. No introduce
importación parcial, sincronización, migraciones ni una nueva versión del backup.
La validación del backup completo permanece obligatoria y sin cambios; el emisor
parcial toma filas confirmadas de Room y comprueba alcance y archivos por separado.

Sin archivos asociados se guarda .json o .md. Con archivos se genera ZIP con
element.json o contenido.md y files/attachments/<id>.<ext> y/o
files/<directorio>/<nombre-relativo>. Se conserva el manifiesto original de
adjuntos/imágenes y se comprueban tamaño y SHA-256 durante la escritura. Markdown
sustituye los marcadores de adjuntos por enlaces relativos, conserva listas/bloques
de código, utiliza encabezados por profundidad e indica niveles superiores a seis.

Solo se exportan archivos mediante selección explícita de carpeta Android; los
archivos externos son texto/medios sin cifrar y el panel lo advierte. Generación
offline, en IO, bajo el bloqueo compartido de archivos. No hay caché nueva ni
copia privada del contenido: se escribe directamente al destino SAF. El diario
cifrado existente de documentos admite md/json/zip, revierte parciales ante fallos
y permite reintentar la limpieza al reabrir el panel. Los archivos completados
pertenecen al usuario. Límites: 5000 elementos, presupuesto de metadatos del backup
y 256 MiB de archivos por exportación, con rechazo explícito sin truncar.

## Banners personales — JSON 45 / Room 52

`personBanners` contiene `personId`, `attachmentId`, `zoom`, `x`, `y`. Se emite
JSON 45 cuando existen banners o encuadres de galería; lectores 1–44 inicializan
los banners vacíos y encuadres de galería 1/0/0.
Cada propietario debe existir; la referencia debe apuntar a un adjunto READY PNG/JPEG.
Los bytes originales y su SHA-256 usan el mismo contenedor de adjuntos; no se
serializan rutas ni se recortan originales. La restauración valida primero e inserta
adjuntos antes de banners dentro de la transacción de reemplazo. El borrado de un
perfil elimina su banner; quitar/reemplazar un banner libera solo la referencia,
conservando imágenes usadas por galerías, elementos o reservas de editores.

Capturas de invitado: cola operativa cifrada AES-GCM en `noBackupFilesDir`, clave
independiente en Android Keystore, sin contraseñas ni claves de bóveda. No es dato
confirmado del perfil hasta importarse. Una captura aceptada pendiente impide
respaldar/restaurar ese almacén para evitar omisiones y destinos ambiguos. Tras
importarse, original y entrada de galería forman parte del respaldo habitual.
No hay caducidad automática de capturas aceptadas; fallos de autenticación,
importación o integridad conservan los archivos cifrados. La cola no es portable
entre instalaciones: depende del Keystore local. Captura de invitado permanece
**deshabilitada** hasta verificar aislamiento y ciclo de vida en Android.

JSON 45 añade zoom/x/y a personMemoryImages; se validan con AvatarFraming. Room
51→52 añade esas columnas con valores por defecto 1/0/0 sin reescribir archivos.

## Sonido de tareas — CustomTheme 4 / JSON de backup 46

Apariencia añade `task_completion_sound` booleano, default true en datos anteriores.
La skin con audio emite schema 4, campo `completionSound` con el contenedor de audio
Pomodoro (bytes completos base64 y SHA-256). Máximo 512 KiB, duración real 1–3000 ms,
MP3/OGG/WAV; se valida antes de importar o restaurar. Sin audio se conserva el schema
histórico 1/2/3. El intercambio individual y la biblioteca conservan el audio junto con
las imágenes y originales existentes, sin guardar URI ni rutas absolutas.

El backup emite JSON 46 solo cuando existe la nueva preferencia o alguna skin con
sonido. Se mantienen Room 52, ANBACK01 y contenedor v2; lectores anteriores rechazan
46. Los lectores actuales admiten versiones anteriores e inicializan sonido activado
con tin universal. Las preferencias ya participan del snapshot y de la restauración
atómica; corrupción o fallo de restauración conserva la biblioteca y configuración
anteriores. No se añaden archivos sujetos a limpieza ni se modifica el cifrado.

### Cumpleaños con año opcional (JSON 47)

`personBirthdays[].date` conserva `AAAA-MM-DD` para fechas completas y permite
`--MM-DD` (ISO MonthDay) cuando no se conoce el año. No almacena años ficticios;
la ausencia de cumpleaños sigue representada por ausencia del registro.
Día y mes se validan estrictamente, incluido `--02-29`; una fecha completa exige
un año 1–9999 y un día válido para ese año. Los borradores incompletos nunca se
confirman. Se emite JSON 47 únicamente cuando existe un cumpleaños sin año;
las fechas completas mantienen el versionado anterior. El lector acepta 1–47.
No cambian Room, el contenedor cifrado ni las relaciones. Snapshot/restauración
conservan literalmente ambos formatos; un respaldo inválido se rechaza antes
de reemplazar registros.

### Originales de avatar Antisocial — JSON 48

El namespace avatars admite UUID.jpg además de UUID.png. Los nuevos JPEG están
limpios y solo pueden conservar orientación mínima reconstruida; PNG admite
resolución completa hasta 32000 por lado/100 MP. Se mantienen 20 MiB por avatar,
8 MiB en snapshots inline y 1 GiB acumulado de imágenes por streaming. La UI
normal de exportación usa streaming; se conserva el contenedor v2, sus hashes y
la validación antes del reemplazo. El nombre interno restaurado mantiene su
extensión; contenido, referencias de Persona y fotos legadas se restauran juntos.
Los originales de publicaciones ya utilizan los adjuntos existentes (bytes y hash
incluidos), sin nuevo namespace. El lector acepta JSON 1–48 y conserva los PNG
anteriores sin modificarlos. JSON 48 solo se emite cuando un avatar necesita las
nuevas capacidades; los demás respaldos conservan su versionado anterior.
Los temporales de cámara y sus leases nunca forman parte del respaldo.

Validado: backup/restauración inline y por streaming de un avatar JPEG limpio
cifrado de 3072 px, preservando extensión/bytes; lectura de avatar PNG histórico;
backup v2 de publicaciones JPEG con restauración de originales y referencias
compartidas. Los casos de corrupción/CRC, cancelación y limpieza tras fallo de
escritura están cubiertos por la prueba focalizada de fotografías.

### Principal automático de Antisocial (sin nuevo formato)

Se reutiliza localIdentities con slot SELF. El snapshot transaccional asegura su
existencia cuando hay Personas; la restauración respeta la identidad respaldada.
Si un respaldo antiguo no incluye SELF, se utiliza el primer perfil insertado
al restaurarlo. No se conoce su fecha original de creación: esos respaldos
ordenaban por ID. La decisión se persiste y los respaldos siguientes la conservan.
No se eliminan columnas ni se omiten valores históricos de personProfiles por
haberse retirado sus controles. El formato vigente y sus lectores no cambian.

## Banca — Room 53 / JSON 49

Migración aditiva 52→53: `bank_roots`, `bank_accounts`, `bank_operations`, `bank_allocations`. No transforma importes, responsables, estados ni recurrencias existentes. Se instalan protecciones contra pérdida de historial financiero y reducción de obligaciones por debajo de pagos vigentes.

Cuando hay Núcleos Banca se emite JSON 49 con arrays `bankRoots`, `bankAccounts`, `bankOperations`, `bankAllocations`. El lector mantiene las versiones anteriores (1–48) con listas financieras vacías. Se conservan IDs, vínculos con cuentas/tareas/ocurrencias, importes enteros, fecha de operación, anulación, corrección y nombres históricos de las asignaciones. No se serializan rutas ni se crean archivos financieros: todo vive en Room bajo el cifrado de la bóveda existente. El contenedor cifrado externo no cambia.

Se validan tipos estrictos, identidades únicas, cuentas y sus propietarios, Núcleos/Capas, referencias a tareas y ocurrencias, suma de asignaciones, importes positivos, tipos de operación, saldo no negativo y pendiente no negativo antes de sustituir datos. Las cuatro listas cuentan en el límite de registros y se aplica el límite global de 16 MiB de metadatos. La restauración limpia primero el registro financiero dentro de la transacción, restaura las entidades originales y finalmente reinserta el registro financiero. Ante fallo revierte todo; un backup antiguo reemplaza Banca con sus listas vacías como el resto de datos ausentes de versiones históricas.

El límite del objeto raíz del lector pasa de 76 a 80 campos, exactamente los cuatro arrays nuevos. No cambia el límite de 32 campos por registro, profundidad, tamaño, tipos estrictos ni la comprobación exacta de campos conocidos. Las referencias financieras utilizan claves foráneas; la relación cuenta→Capa se comprueba al commit para poder crear ambas atómicamente. Se rechazan ciclos y dobles sustituciones en las correcciones.

Validación final: migración 52→53 preservando filas, exportación/lectura/restauración de JSON 49 con pagos recurrentes, lectura histórica sin Banca y rechazo de importes incoherentes antes de reemplazar datos, aprobadas en `BankIntegrationTest`. La prueba de backup respeta los IDs deterministas originales de las ocurrencias. El flujo de ingreso/cancelación/anulación mediante los paneles existentes aprobó en `BankUiTest`. No se modificaron el contenedor cifrado, la versión de aplicación ni las claves.

Jerarquía plana de Banca: una cuenta solo es válida con su nodo `LAYER` en `rootId` y `parentId=null`; todos los nodos del Núcleo Banca deben corresponder a cuentas. Esta validación existente se conserva antes del reemplazo transaccional. La integración visual y los nuevos guardas no cambian JSON 49 ni Room 53 y no reparan automáticamente cuentas históricas incompatibles.

Simplificación de pagos: sin cambios de formato ni migración. Ingreso, Retiro y Pago son denominaciones automáticas del campo `concept` existente cuando el editor recibe un nombre vacío. Las cuatro tablas, IDs, asignaciones, referencias y transferencias históricas continúan exportándose/restaurándose con la validación de JSON 49. La selección y la confirmación en pantalla son borradores temporales; no se añaden preferencias ni archivos persistentes.

## Detector opcional de compras CMR (en preparación; emisor no autorizado todavía)

Room continúa en 53. JSON 50 se emite cuando existe la preferencia `purchase_detection`; el resto de los respaldos conserva la selección de formato histórica. Los lectores aceptan 1–50. La preferencia contiene una cadena `state` tipada mediante `EncryptedPreferences`: versión interna 1, activación, identidad de bóveda, sugerencias pendientes (UUID, hash de entrega, comercio, CLP entero positivo y recepción), hashes de deduplicación y descartes operativos. Su estructura y límites se validan antes de sustituir datos. Los formatos anteriores carecen de preferencia y dejan el detector desactivado.

Las sugerencias incorporadas a la bóveda están en `private_preferences`, bajo SQLCipher, y participan del snapshot y restauración transaccionales existentes. No se añaden tablas financieras ni cambios de esquema. El editor no crea pagos hasta confirmar; creación de tarea y retirada de sugerencia comparten transacción. Los importes detectados son pendientes, nunca movimientos bancarios.

La entrada recibida con la bóveda bloqueada reside temporalmente en `noBackupFilesDir/purchase-input/inbox`, cifrada AES-GCM mediante AndroidKeyStore y reemplazada con `DurableVaultFile`. No contiene credenciales ni claves de bóveda ni texto completo de notificaciones. Al abrir la bóveda principal se incorpora idempotentemente a Room antes de retirar las entradas del archivo cifrado. No se elimina físicamente un archivo por cada sugerencia: el estado se reemplaza de forma durable; sus diarios se recuperan con el mecanismo común. No se barren directorios. Antes de respaldar o restaurar se comprueba que la entrada temporal de esa bóveda esté incorporada, incluidos descartes: si no lo está, la operación falla explícitamente y pide abrir Arachn0de. La cola temporal y su clave de dispositivo no se exportan; las sugerencias ya incorporadas y sus datos completos sí. No se expiran sugerencias pendientes; se limitan a 100 y los hashes no pendientes a una ventana de 48 horas y 2.000 registros. Al alcanzar límites se rechazan nuevas entradas sin borrar pendientes.

La lista de emisores y certificados autorizados permanece vacía hasta verificar una notificación real y su firma. `cl.android` es candidato identificado en Google Play, no un emisor autorizado por esta implementación. Nunca se acepta contenido de otros paquetes por coincidencia textual. La autorización Android y el consentimiento local no se restauran como permisos del sistema: se muestra su estado real y no se procesa sin ambos.


## Retirada de detección CMR — compatibilidad histórica

La integración activa se ha retirado: no existe listener Android, controles, permisos de acceso a notificaciones ajenas ni creación desde sugerencias. Room permanece en 53 y los lectores JSON 1–50 se conservan. `purchase_detection` se mantiene únicamente como dato histórico cifrado: se valida, exporta y restaura sin activarlo ni generar tareas u operaciones. La identidad de bóveda se adapta al restaurar como antes.

`backup/legacy` contiene exclusivamente el modelo/codec histórico y la lectura del antiguo archivo cifrado. Si existe `noBackupFilesDir/purchase-input/inbox`, el snapshot incorpora sus registros de la misma bóveda a la preferencia del backup, sin escribir Room ni alterar el archivo de origen. Omite entradas cuyo ID ya tenga tarea confirmada y conserva el control de duplicados. Un archivo corrupto o ilegible hace fallar el respaldo explícitamente; nunca se descarta silenciosamente. No se generan entradas ni claves nuevas.

No hay limpieza automática de estos archivos históricos ni de las preferencias: se conservan para evitar pérdida de información. Su lectura recupera únicamente el diario durable asociado, bajo el bloqueo común de archivos. No se recorren ni borran otros directorios. El payload incorporado al backup contiene los datos completos necesarios, no rutas locales ni claves de dispositivo. Las tareas y movimientos previamente creados son datos normales y permanecen intactos.


## Traslados mediante explorador jerárquico

Sin cambio de versión de respaldo ni nuevas entidades. El traslado entre Núcleos conserva IDs de nodos, adjuntos, personas, etiquetas, eventos y relaciones históricas. Se actualizan la ubicación y las claves de configuración/orden dependientes del Núcleo en una transacción; las reglas recurrentes internas conservan su identidad y cambian de proyecto junto con su contenedor. La validación de backup permanece vigente. No se copian ni borran bytes de adjuntos. Las relaciones de contexto incompatibles bloquean el traslado antes del commit; un fallo revierte la operación completa.


## Tragaperras Ahorro — JSON 51 / Room 53

La preferencia cifrada `slot_savings` activa JSON 51; los lectores aceptan 1–51 y los formatos anteriores sin esta preferencia mantienen Ahorro sin configurar. No cambia ANBACK01 ni el contenedor. Se exportan íntegramente todas las versiones de configuración usadas, asociaciones Banco/cuenta, libro de eventos, resultados y giro pendiente de presentación. No hay rutas locales ni archivos del módulo. Todos los movimientos físicos permanecen en bank_operations y se verifican por ID, cuenta, tipo, cantidad y concepto; una anulación histórica no se descarta y provoca bloqueo de conciliación al usar la máquina.

Se validan UUID, tipos enteros, límites, versiones, tabla, RTP exacto, porcentajes, asignaciones de créditos por compra, resultado/premio, cobertura antes de cada giro, decisiones únicas de premios, fondos no negativos e invariante de fondos contra entradas/salidas. Configuración nueva no revaloriza compras anteriores. El reparto arrastra el resto del acumulador ponderado entre compras y versiones. Eventos y versiones cuentan en el presupuesto de registros. El snapshot y la restauración transaccional existentes conservan los datos previos ante cualquier fallo; no se requieren tablas o migraciones nuevas.


## Eliminación confirmada de cuentas Banco

Sin cambio de Room ni del formato. La cuenta, sus operaciones/correcciones y asignaciones se eliminan en una transacción solo tras confirmación explícita. Se preservan tareas/personas y Banco; pagos pendientes se recalculan. Ahorro asociado exclusivamente a esa cuenta se elimina con advertencia expresa; no se mutilan libros compartidos entre cuentas. La eliminación conserva las protecciones contra saldo negativo y confirma que los vínculos restantes son restaurables. Backups anteriores siguen admitidos; los posteriores no incluyen referencias eliminadas. Round-trip posterior y rollback ante fallo comprobados. No se crean archivos; las referencias de la Capa se liberan mediante el flujo existente y la limpieza posterior al commit.

## Saldo de la cuenta vinculada a Ahorro — JSON 52

La cuenta seleccionada representa la caja de la máquina. El saldo bancario ya existente, descontados todos los fondos previamente atribuidos (protegido + libre + premios reservados), se incorpora en unidades enteras de 1.000 CLP mediante eventos `ASSIGN`. No son ingresos/egresos bancarios, compras de créditos, recargas físicas ni ahorro generado. Los pesos que no completen 1.000 quedan fuera de la cobertura de premios. El capital asignado se reconstruye del libro y se incluye en el invariante: fondos = compras + recargas + capital asignado − retiros.

SavingsCodec schema 2 añade el tipo ASSIGN y conserva la lectura del schema 1 sin él. Backup JSON 52 contiene íntegros estos eventos y conserva lectores 1–51. No cambian Room 53, ANBACK01, tablas, archivos ni cifrado. Los vínculos de movimientos físicos continúan validándose como antes; ASSIGN no inventa un movimiento bancario adicional. La asignación se realiza transaccionalmente al vincular/leer el estado y solo por el exceso de saldo aún no atribuido; repetir, reabrir o restaurar no duplica capital. No se añade durante un giro pendiente ni si hay vínculos rotos/solvencia insuficiente. Los retiros ajenos siguen bloqueando ante falta de respaldo: nunca consumen automáticamente ahorro protegido ni reservas de premios.

## Tragaperras selvática — JSON 53 / codec Ahorro 3

Ampliación mínima para los ocho símbolos e importes autorizados del cartel. SavingsCodec 3 admite tablas/resultados de siete u ocho símbolos y conserva lectores de schemas 1/2; estos últimos siguen limitados a siete. Se mantiene el orden original y los valores de todas las configuraciones históricas y sus créditos. Los conceptos de premios antiguos se validan con los nombres originales; los nuevos, con la tabla selvática. JSON 53 conserva lectura de 1–52. Room sigue en 53 sin cambios de esquema ni migraciones. Los 31 PNG son recursos inmutables incluidos en la aplicación, no archivos privados referenciados en respaldos. No se añaden rutas de dispositivo al formato. Detalles y validación en docs/JUNGLE_SLOTS.md.

Validación del formato selvático: round-trip con créditos históricos y nuevos, retirada de máscara con concepto correcto y recuperación de datos previo a restauración corrupta, dentro de 40 métodos focalizados aprobados. Sin nueva migración Room ni archivos privados.

## Paquetes de cinco tiradas — JSON 54 / codec Ahorro 4

Aprobación explícita: 1.000 CLP por cinco tiradas y RTP exactamente 30 %, con premios intactos. Cada configuración guarda `pack` y `denominator`: las nuevas usan 5 / 50.000; los codecs 1–3 se decodifican explícitamente como 1 / 10.000, preservando precio, créditos y probabilidades de cada compra histórica. El precio guardado sigue siendo entero en unidades de 1.000 CLP; `pack` determina cuántos créditos compra ese paquete. El coste estadístico de cada giro se deriva en pesos enteros (200 para el paquete nuevo); no crea un movimiento bancario por giro.

JSON 54 conserva lectores 1–53 y schemas anteriores de la preferencia. Sin cambios de tablas, Room 53, SQLCipher, ANBACK01 ni archivos. Depósito/creación de configuración futura/créditos son una única transacción; retirada consume una reserva y registra un egreso una sola vez. Cerrar un panel no resuelve premios. KEEP histórico continúa siendo válido/restaurable, aunque la interfaz actual ofrece únicamente RETIRAR. No se reescriben automáticamente compras o cantidades históricas. Detalles y resultados en docs/JUNGLE_UX_CORRECTIONS.md.

## Contenido enriquecido y GIF — JSON 55 / Room sin cambios

Cuando existe un GIF o un nuevo formato en una descripción de Núcleo, Capa,
Tarea, Nota o recurrencia, el escritor selecciona JSON 55. El lector admite
1–55; no cambia el contenedor ni hay columnas nuevas. Los formatos anteriores
siguen guardándose y leyéndose como antes. Las extensiones portátiles usan
pares del marcador reservado `[[arachnode:highlight]]`,
`[[arachnode:title]]` o `[[arachnode:code]]` dentro de la descripción. No se
serializan posiciones externas que puedan desfasarse al insertar texto.
Código conserva espacios, tabulaciones y saltos; los demás marcadores son
literales dentro de código, salvo el destacado por palabra. Los marcadores
sin pareja son literales. No se reescriben descripciones históricas al abrirlas.

Los adjuntos añaden `image/gif` / extensión interna UUID.gif a los PNG/JPEG/AAC
existentes. Se conservan los bytes originales, MIME, dimensiones, tamaño y
SHA-256, cifrados por SecureFiles. Límite general de 20 MiB y límites de GIF
2048×2048, 1000 fotogramas, estructura completa y trailer final válidos. El
respaldo transporta todos los bytes, no un fotograma ni una ruta absoluta;
verifica estructura, dimensiones/hash/tamaño antes de sustituir datos. La
restauración usa el staging, diario durable, mutex y transacción existentes.

Reservas, referencias compartidas, cancelación y DELETE_PENDING mantienen el
ciclo de vida de AttachmentRepository. El último vínculo confirmado se libera
antes de eliminar físicamente; un fallo se reintenta con la limpieza habitual.
La reproducción solo conserva datos decodificados en memoria durante el visor;
no crea caché persistente ni archivos descifrados. No hay archivos nuevos que
excluir del backup. Antisocial y fotografías de otros módulos conservan sus
reglas actuales; esta ampliación corresponde al botón Imagen de Contenido.

Exportación individual y paquetes Markdown/JSON incluyen el GIF original.
PDF/DOCX incorporan una representación PNG estática; no sustituyen ni eliminan
el archivo original, que permanece exportable y recuperable mediante backup.
Los tres formatos de texto se representan en el visor, editor y documentos;
Markdown usa HTML semántico para las extensiones de formato.

## Seguridad Fase A — sin cambio de formato

Backups privados, ANBACK01, versiones JSON y Room no cambian. Los originales,
metadatos y bloques editables `arNd` siguen incluidos íntegramente en el backup.
La política de saneamiento se aplica exclusivamente a copias públicas; no interviene
en snapshot, contenedor, restauración de adjuntos ni edición privada.

`security_preferences` permanece legible, validado y exportado en formatos históricos.
Al restaurar se conserva el namespace local del receptor dentro de la transacción;
se ignora el del archivo, incluso si el backup es cifrado. Si no existe configuración
local no se crea una autorización importada: se usan los defaults seguros existentes.
Cancelar, fallar validación o revertir la transacción conserva la política anterior.
Esta exclusión local deliberada impide que un backup transporte permisos de captura
o sustituya la decisión de bloqueo del dispositivo receptor.

Markdown/JSON y temas son documentos públicos independientes del backup: contienen
copias saneadas y sus tamaños/hashes recalculados. No modifican hashes privados ni
referencias. La Fase A.1 añade únicamente el staging regenerable descrito abajo.


## Seguridad Fase A.1 — derivados públicos excluidos

Se conservan ANBACK01, versiones JSON/Room, campos y todos los bytes originales,
incluidos WebP animado, APNG, EXIF, ICC y arNd privados. No se serializan rutas
absolutas. El intercambio público de temas conserva su esquema y registra MIME,
tamaño, hash y orientación de la copia realmente emitida. Los temas históricos
con orientación 1 en PNG/WebP siguen recuperándose con su apariencia almacenada.

`cache/public-exports-v1` contiene solo derivados cifrados regenerables y reservas
UUID `.lease`, excluidos deliberadamente del backup. No contiene originales ni
únicos datos del usuario; recuperar una exportación consiste en volver a generarla
desde las fuentes confirmadas. Su clave efímera no se persiste ni exporta. Límite
512 MiB, eliminación al cerrar/cancelar/fallar, reintento durable tras fallos de
limpieza y reinicio, reservas activas sin caducidad, nombres/directorios controlados
y bloqueo común. No se barre el resto del almacenamiento privado. Las pruebas
focalizadas cubren fuentes intactas, referencias concurrentes, reemplazo,
cancelación, borrado fallido y recuperación; las de backup cubren round-trip de
las fuentes originales independientemente de sus copias públicas saneadas.

## SEG-05 — tarjetas compartidas

No cambia el formato ni se añaden datos persistentes. Las entregas usan una
instantánea autenticada y acotada en RAM; se borra al completar, cancelar, fallar
o quedar inactiva. No genera archivos descifrados ni un nuevo staging. La caché
regenerable `card-shares` conserva su cifrado, exclusión del backup, marcas durables
de borrado y recuperación. El archivo solo se necesita durante la preparación,
bajo el bloqueo común; su entrega posterior no conserva referencias al archivo.

## Fase C — captura de cámara, sin cambio de formato

El JPEG de cámara y su reserva durable en cache/antisocial-camera son estado
operativo no confirmado. Se excluyen del snapshot lógico, de Android backup y
también del inventario de caché de la migración inicial. Excluir no elimina archivos.
Al devolver una captura se importa a un adjunto cifrado reservado antes de mostrar
su confirmación; solo una verificación completa permite retirar el JPEG. Las
reservas sin confirmar no se exportan. Confirmar y guardar una fotografía reutiliza
las relaciones y los bytes completos de adjuntos/avatares ya incluidos en backups.
La restauración y la liberación de referencias compartidas siguen el contrato actual.

Una solicitud pendiente de cámara no caduca por proceso ni por edad; errores y
cancelaciones usan estados durables y recuperación del almacén correspondiente.
No se añaden datos confirmados, tablas, algoritmos ni versiones de contenedor/JSON.
Tampoco se eliminan copias históricas de migración: no hay evidencia suficiente
para considerar prescindible cada original archivado. El checkpoint Pomodoro 1–3
y la política de reenvoltura de claves permanecen sin cambios. Véase
`docs/SECURITY_PHASE_C.md` para inventario, riesgo residual y alcance de pruebas.

## Guardar con descendientes — sin cambio de formato

La exportación PNG múltiple es de lectura: no introduce tablas, preferencias,
relaciones ni datos confirmados nuevos en la aplicación. Se mantienen completos
los bytes y referencias de las fuentes en los backups existentes. Los PNG finales
son documentos individuales en la carpeta SAF elegida por el usuario y no se
incorporan al snapshot privado. El staging cifrado public-exports-v1 y el diario
operativo cifrado card-image-export-journal se excluyen del backup: son recursos
regenerables de exportación, con limpieza/reintento después de fallos y sin
caducidad de reservas activas. No se serializan rutas de dispositivo en el backup.
No cambian el contenedor, JSON, cifrado ni compatibilidad histórica. Véase
`docs/GUARDAR_CON_DESCENDIENTES.md`.

## Datos v56: configuración jerárquica de ordenación

Se mantiene Room 53 y el contenedor existente. Cuando hay preferencias nuevas,
el JSON usa versión 56; los lectores conservan v1–v55. No se añaden archivos.
`nodeSortPreferences.mode` admite `SORT1:` seguido de un objeto validado con
`manual`, `inherit`, `layers`, `appliedLayers`, `legacyLayers` y `rules`. `rules` contiene exactamente
una entrada por criterio (STATE, PRIORITY, DUE, CREATED, PERSON, NAME, TAG), en su
jerarquía, con booleanos `on` y `reverse`. Se rechazan campos desconocidos, claves
repetidas, criterios ausentes y payloads superiores a 4096 caracteres antes de
restaurar. `layersFirst` de la fila representa la presentación ya aplicada;
`layers` conserva la elección guardada hasta pulsar Aplicar. Las posiciones siguen
siendo `nodes.position`, capturadas y restauradas transaccionalmente como antes.

Los modos antiguos se interpretan sin reescribir datos al leer: DUE_ASC/DUE_DESC
se convierten a DUE con su dirección; CREATED_NEWEST/CREATED_OLDEST a CREATED;
DUE_PRIORITY a DUE → PRIORITY; PRIORITY conserva su desempate histórico por DUE.
MANUAL e INHERIT permanecen disponibles. Los cambios nuevos se codifican al
editar o aplicar. Una Capa heredada conserva la referencia al Núcleo, no una copia
congelada de sus criterios. Los respaldos v55 o anteriores no aceptan SORT1.

`legacyLayers` conserva únicamente excepciones históricas de Capas primero de
filas INHERIT antiguas. Volver a heredar explícitamente elimina esa excepción y
utiliza todos los ajustes vigentes del Núcleo. No añade controles nuevos.

## Datos v57: esfuerzo manual y ordenación por esfuerzo

Room 54 añade `nodes.effort` y `conversion_roots.effort`, INTEGER NOT NULL DEFAULT 0,
con rango 0–5 validado. Los registros históricos reciben 0. El valor de conversión
es latente: evita perder esfuerzo al convertir una antigua Tarea a Capa/Núcleo y
regresar a Capa; no cambia las restricciones de conversión ni añade esfuerzo visible a estos tipos. No hay archivos nuevos.

Se emite JSON 57 cuando hay esfuerzo distinto de cero o preferencias SORT1 con
los ocho criterios, incluido EFFORT. En v57 ambos tipos de registro incluyen
`effort` obligatorio, entero estricto 0–5. Los JSON 1–56 conservan su forma anterior
y se restauran con 0. Un SORT1 de siete criterios sigue admitiéndose sin reescritura,
con EFFORT añadido desactivado en memoria; ocho criterios requieren JSON 57.
Se rechazan criterios duplicados/ausentes, rangos inválidos y números fraccionarios
antes de sustituir datos. Se mantienen el contenedor y cifrado existentes, la
restauración transaccional, posiciones, herencia y demás campos intactos.

### Catálogo institucional monocromático

DarkGrey, PureBlack, BlackAndWhite y Graphite amplían los valores del tema ya
serializado en las preferencias y temas personalizados. No cambian claves,
columnas ni formato JSON. Se conserva la lectura de valores históricos; las
nuevas selecciones recorren el mismo payload de preferencias de respaldo y su
validación. Los logotipos y el GIF institucional son recursos del APK, no datos
locales del usuario; se recuperan desde el paquete, sin rutas del dispositivo.

La paleta de indicadores conserva sus identificadores anteriores y añade `LILAC` (lila). Se guarda por nombre en las mismas preferencias de apariencia incluidas en el backup; no cambia el formato ni el esquema. Los colores cromáticos se representan ahora con tonos intensos.

## Preferencia visual del calendario contextual

El namespace opcional `calendar_presentation` en `privatePreferences` conserva
`collapsed` como booleano. Se exporta y restaura por el mecanismo cifrado de
preferencias existente; se valida su tipo antes de reemplazar datos. Sin la clave
(respaldos históricos), el calendario comienza expandido. No cambian Room, JSON,
archivos ni el contenedor. No se guardan fechas, eventos ni filtros nuevos.

## Presentación de galerías de herramientas visuales

El namespace opcional `visual_gallery_preferences` en `privatePreferences` guarda
los booleanos `wireframe` y `diagrams`: true = cuadrícula, false = lista. Las
claves ausentes en respaldos anteriores equivalen a cuadrícula. Se valida su tipo
antes de reemplazar datos. Se reutilizan el snapshot y la restauración cifrada de
preferencias; no cambian Room, JSON ni los documentos. Las miniaturas son caché
regenerable en RAM (6 MiB, dos generadores concurrentes), sin archivos nuevos ni
bytes que incorporar a un backup. Los documentos y sus recursos existentes siguen
siendo la fuente íntegra de esas vistas previas.

## Bloque 2: guardado de anotaciones planas

Sin cambio de Room, JSON ni contenedor. Guardar desde el anotador básico produce
un adjunto PNG convencional con las marcas incorporadas en los píxeles; se
respaldan sus bytes completos, metadatos y referencias mediante el flujo existente.
Se mantienen los lectores/validadores históricos de `arNd` v1 y su recuperación.
Las reservas de reemplazo/borrador y la limpieza pendiente siguen siendo estado
operativo excluido del backup; no se añaden rutas absolutas ni archivos auxiliares.
Se verifica rollback, reservas compartidas, cancelación exterior, limpieza fallida
y recuperación desde un backup después de eliminar los archivos confirmados.


## Grupo 5A — Juego retirado, datos históricos conservados

Se retiran los accesos, pantallas, motores y recursos estáticos exclusivos de
Juego/Carrera táctica. Tragaperras sigue siendo una herramienta independiente y
no cambia. Los apartados históricos del Juego describen formatos compatibles,
no una funcionalidad activa.

`game_state`, la preferencia histórica `experimental_game/session` y el campo
`gameSession` siguen el protocolo existente. `backup/legacy/game` conserva solo
modelos, codecs, topología canónica y validación/normalización requeridos por
partidas v1–v4 y por la migración 22→23. No carga imágenes ni reanuda partidas.
No se elimina ningún dato privado ni se cambia Room, el formato JSON, el
contenedor, las transacciones o la política de rechazo de datos corruptos.
Los recursos empaquetados retirados no son archivos personales del backup.
Inventario, medidas y validación: `docs/PERFORMANCE_GROUP_5A.md`.


## Grupo 6 — reservas del lector de miniaturas

`ProjectThumbnailCache` usa la reserva durable existente de ImageFileLifecycle
para proteger el original mientras lee/decodifica/genera fuera del mutex común.
El mutex común solo cubre adquisición, metadatos y liberación; la liberación se
completa en contexto no cancelable. El mutex propio serializa los lectores de
miniaturas y protege derivados/partes frente a su poda. La poda de lecturas se
amortiza a un minuto; tras generar se fuerza para conservar el límite de 64 MiB.

El propietario operativo `project-thumbnail-reader` identifica exclusivamente
estas lecturas. Al adquirir el mutex propio, sin otro lector activo, la siguiente
lectura recupera las reservas que dejó su predecesora interrumpida. No caducan
por antigüedad ni se liberan reservas de editores, backup u otros propietarios.
Si no hay otra lectura, una reserva pendiente conserva el original. No se borra
ningún original en recuperación: la limpieza normal sigue verificando referencias
y reservas bajo el bloqueo común, después del commit.

No se añaden datos confirmados ni se cambia Room o el formato de backups. Los
marcadores conservan el formato y la exclusión operacional existentes; derivados
cifrados y partes de `cache/project-thumbnails` siguen siendo regenerables y
excluidos del backup. No se cambian bytes originales, cifrado, firmas o versiones.
Medidas y pruebas específicas: `PERFORMANCE_GROUP_6.md`.
