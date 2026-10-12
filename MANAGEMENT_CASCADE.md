# Gestión mediante Capas reales

## Diagnóstico inicial — 11 de octubre de 2026
Room 36 / JSON 28. ManagementContextEntity mantiene identidad mediante ConversionRoot.
Historias y épicas ya son Node LAYER con roles; admiten notas, tareas y capas profundas.
Sprint conserva cinco WorkState y un check independiente; SprintLink e Iteration mantienen
referencias e historial. ManagementSection presenta seis herramientas mediante diálogos,
sin nodos persistentes Acta/Backlog/Sprints/Kanban. ProjectCharter y objetivos son datos
separados; no existe Persona Yo ni vista Gantt. ProjectScreen ya conserva rutas y scroll.
No es necesario crear otro árbol ni otra copia de Historias.

## Estrategia
Añadir metadatos de función sobre nodos reales, independientes del título. Inicializar
transaccionalmente, conservar registros anteriores y no sobrescribir contenido editado.
Usar jerarquía Node como ubicación canónica y los vínculos actuales como referencias.
Mantener las fases operativas y los historiales; rechazar reorganizaciones que creen
ciclos o separen contextos. Las capas personales mantienen sus posiciones; la estructura
nueva se añade en orden sin renumerar las anteriores.
Room 36→37 añade management_layers, sprint_stories, objective_work y local_identity.
JSON 29 incluye las cuatro colecciones y valida contexto,
tipo, unicidad y contenedor. Lectura histórica 1–28 conservada; sin downgrade.
No se introducen archivos privados ni cambios de cifrado. Las conversiones capturan y
restituyen los metadatos en la transacción existente.


## Implementación funcional

- Proyecto → Acta / Backlog / Sprints / Kanban usa la lista y el navegador de
  Node habituales, no tarjetas que simulan otro árbol. Acta incluye Objetivos,
  Equipo y seis Notas editables. Su lectura completa incluye notas personales y
  el objetivo general de la descripción de Objetivos.
- Backlog → + Épica → + Historia crea LAYER originales. Una Historia admite
  los descendientes habituales, sin límite nuevo de profundidad. `Vínculos`
  conserva las consultas y asociaciones históricas, incluso con varias épicas.
- Objetivos → + Objetivo → Vincular trabajo asocia Historias o Tareas.
  El progreso usa ACTION hoja únicos; las Notas no cuentan y cero trabajo no
  completa un objetivo. `Criterios` conserva validación independiente.
- Sprints → + Sprint → Planificar permite fechas, objetivo, Historias, Tareas,
  responsables tradicionales, activación, cierre y reapertura. Referencias
  históricas, cinco fases y check mantienen su contrato. Retirar una Historia
  requiere confirmar las fases actuales; no elimina su ficha ni descendientes.
- Kanban abre automáticamente el Sprint activo, muestra referencias a Historias
  y Tareas y abre sus ubicaciones canónicas. El regreso recupera la vista desde
  la que se consultó el original. Fases vacías quedan contraídas.
- Personas → ficha → General → Identificar como Yo mantiene una identidad local
  por bóveda. Kanban y planificación Sprint ofrecen Todas/Mis tareas/De otros/
  Sin responsable. No se infiere autoría ni desempeño.
- Gestión configura modalidad/metodología; Cronograma representa fechas y avance
  actuales y abre originales. Ágil prioriza Sprints al entrar al cronograma;
  Tradicional e Híbrida muestran todo. Los filtros siguen disponibles y cambiar
  metodología no elimina herramientas, capas ni trabajo.
- Equipo, dentro de Acta, incorpora Personas existentes mediante + Persona,
  permite varios roles, muestra Lead con corona dorada y abre la ficha existente.

## Compatibilidad y conservación

La migración aditiva no transforma datos históricos en SQL. La inicialización de
la estructura se realiza posteriormente dentro de una transacción, una sola vez
por contexto. Preserva contenido, IDs, fases y vínculos, y rechaza ciclos/colisiones.
Los textos históricos se conservan; las ediciones explícitas de notas/objetivos
sincronizan sus adaptadores en la misma transacción. Las conversiones capturan y
restituyen metadatos y asociaciones con el contexto estable, sin fusionarlos.

Las nuevas capas se crean mediante el repositorio Node existente, incluidos sus
eventos. Los descendientes conservan edición, creación, archivos y mecanismos
habituales de recuperación. No se introduce almacenamiento multimedia nuevo ni
limpieza de archivos. El JSON 29 usa el staging/diario durable y cifrado existentes.

## Alcance y limitaciones que requieren revisión

- Cronograma es una primera proyección móvil de fechas previstas y progreso.
  No implementa hitos, dependencias ni comparación plan/real: no existen datos
  fiables para inferirlos. Se abre el original para corregir fechas.
- La planificación admite Planificado/Activo/Terminado; no inventa Pausado.
  Si hay varios Sprints activos, Kanban selecciona uno de forma estable y ofrece
  selección explícita. No se impone un único Sprint activo nuevo.
- Una Historia puede tener vínculos a varias épicas, pero solo un padre canónico.
  Se conserva el padre vinculado actual o se elige una épica de forma estable.
- Si un nodo histórico combina rol Épica/Historia y modo Sprint, se conserva su
  identidad en Backlog; no puede tener simultáneamente dos padres físicos. Sus
  referencias y planificación se conservan.
- Los nodos de estructura deben conservar contenedor/tipo. No se pueden eliminar
  por separado; eliminar su contexto completo sigue usando la operación existente.
  Roles y referencias que impiden eliminar trabajo requieren retirada explícita.
- Responsables tradicionales de Sprint/Tareas y responsabilidades gestionadas
  existentes siguen siendo conceptos distintos. No se crean nuevos permisos ni
  un segundo estado operativo.
- La revisión visual física queda pendiente. La validación nativa Room 37 se
  completó en el emulador aislado Pixel_7a; véase el cierre técnico siguiente.
  No se accedió al teléfono ni a bóvedas reales.
- Algunas pruebas UI históricas describen los menús anteriores y necesitan
  adaptación a las Capas reales antes de una futura ejecución de toda la suite.
  La validación dirigida utiliza el recorrido nuevo real, incluido texto grande.

## Archivos principales

Persistencia: Arachn0deDatabase, ManagementLayerEntity, CascadeRelations, esquema
37.json. Backup: BackupData, BackupJson y BackupRepository. Repositorios:
ManagementCascadeRepository, CascadeRelationsRepository, ManagementRepository,
BacklogRepository, PlanningRepository, IterationRepository, SprintLinkRepository,
NodeRepository, ProjectNestingRepository, PersonRepository y WorkOwnership.
UI: ProjectScreen, ManagementSection, ManagementCascadeUi, BacklogUi, IterationUi,
ManagedKanbanUi, ProjectGanttUi, ParticipationUi y PersonDetailUi.
Pruebas nuevas: ManagementCascadeTest, CascadeRelationsTest, CascadeMigrationTest,
CascadeProjectionTest y ManagementCascadeUiTest; adaptaciones dirigidas de las
pruebas anteriores mantienen sus fixtures históricos y verificaciones de integridad.

## Validación final

La pasada final ejecutó 107 pruebas, todas aprobadas, y `:app:assembleDebug`:
BUILD SUCCESSFUL (1 min 46 s). `git diff --check` aprobó después de los cambios.

Clases ejecutadas: ManagementCascadeTest (5), CascadeRelationsTest (6),
CascadeMigrationTest (2, API 24 y 28), CascadeProjectionTest (3),
ManagementCascadeUiTest (2, fuentes 1× y 1,6×), ManagementTest (6),
PlanningTest (11), ParticipationTest (15), KanbanTest (7), BacklogTest (13),
IterationTest (15), SprintManagedConversionTest (5), KanbanVaultTest (1),
ProjectNestingTest (16). Las pruebas JVM de bóvedas sustituyen explícitamente la
factoría SQLCipher; no acreditan validación nativa ni la instalación del teléfono.

Los fallos intermedios se corrigieron: límite del objeto raíz JSON ampliado a 68
campos para las cuatro nuevas colecciones, expectativas actuales e históricos
separados, sincronización de texto y ciclo de vida/cierre del fixture UI. No se
eliminaron verificaciones para conseguir un resultado verde.

APK de revisión **Debug**, 0.3.0 / 11:
`/home/r0ybt/AndroidStudioProjects/Arachn0de/release-assets/Arachn0de-gestion-cascada-room37-DEBUG.apk`
Tamaño: 174356247 bytes (166.28 MiB).
SHA-256: `dd35021956916ed3275d65bd9e8036a4cd2b9d963a467128f851f4d9eb68bc0d`.

`apksigner verify --verbose --print-certs` aprobó (v2); certificado Android Debug,
SHA-256 `2b77e47d7a03bccf1cc2edee3543d0b8b2a591e94b9875cbe30625a492b14883`.

Esta APK usa firma Debug y el package habitual. **No sirve para actualizar la
instalación oficial firmada**; no se debe desinstalar ni borrar datos para instalarla.
La APK oficial anterior / 11 permanece intacta. No se firma oficialmente, instala,
hace commit ni push. La revisión oficial futura debe verificar que la evidencia
nativa sigue correspondiendo al checkout y seguir íntegramente RELEASING.md.


## Cierre técnico — 11 de octubre de 2026

Room sigue en 37, con migración aditiva 36→37; respaldo JSON 29 y lectura histórica
1–29. No se modificaron producción, migraciones, esquema, formato, firma ni
versiones durante este cierre. Acta, Backlog, Sprints y Kanban son nodos Capa
reales; Objetivos y Equipo dependen del Acta. Las proyecciones comparten IDs y
entidades originales, sin copias de historias, notas o tareas.

| Verificación ejecutada | Resultado |
| --- | --- |
| 14 clases dirigidas JVM/UI, ejecución forzada | 107 aprobadas; 0 fallos, errores u omitidas |
| UI de navegación compartida, fuentes 1× y 1,6×, 411×891 dp | 2 pruebas aprobadas, incluidas en las 107 |
| Tres pruebas oficiales SQLCipher nativo | 3 aprobadas (37,345 s) |
| Esquema cifrado auténtico Room 26→37, principal y señuelo | 1 aprobada (20,932 s) |
| Esquema cifrado auténtico Room 36→37 y respaldos | 1 aprobada (29,627 s) |
| Compilación Debug final del checkout | BUILD SUCCESSFUL; 39 tareas actualizadas |
| Compilación APK instrumental aislado | BUILD SUCCESSFUL |
| Preflight oficial `preparar-apk-0.3.0.sh --check` | Aprobado; sin firma ni empaquetado oficial |
| `git diff --check` | Aprobado |

El emulador Pixel_7a (`emulator-5554`, API 37, x86_64, páginas de 16384 bytes)
recibió exclusivamente los paquetes `com.r0ybt.arachn0de.securityvalidation` y
su APK instrumental `.test`. Las bases usadas son fixtures sintéticos aislados;
no se instaló ni reemplazó el paquete oficial ni se borraron datos del usuario.

Los fixtures construyen SQL desde los esquemas históricos exactos y verifican
sus hashes e identidad Room. No parten de una base moderna con versión antigua.
Se comparan todas las columnas y filas de cada tabla histórica antes y después
de migrar, además de `foreign_key_check` e `integrity_check`. La cobertura incluye
proyecto, capas, tareas, cinco fases y completitud independiente, asociaciones,
personas, encuadre de avatar e iconos, y preservación de identidades de bóveda.

La prueba 36 comprueba la estructura real, historias compartidas, vínculo con
Sprint, fase DOING, objetivo, Persona Yo, un adjunto compartido entre tarea y
nota, avatares e iconos con sus bytes. Exporta e inspecciona un respaldo cifrado
JSON 29, restaura metadatos históricos JSON 28 con sus medios verificados y
restaura JSON 29. Repite el recorrido en ambas bóvedas y rechaza el respaldo de
la principal en el señuelo. La lectura de otros formatos históricos se cubre
con las pruebas dirigidas; no se afirma una prueba nativa individual para cada
versión JSON.

Se ampliaron únicamente `HistoricalRoom26Fixture.kt` y `NativeVaultTest.kt` para
esta cobertura. Dos fallos iniciales del fixture se resolvieron sin cambios de
producción: comparación de ByteArray por contenido y uso del manifiesto y medios
del mismo contenedor, en vez de mezclar snapshot y exportación por streaming.
La validación rechazó el manifiesto incoherente antes de reemplazar datos.

Evidencia renovada mediante el mecanismo existente:
`release-assets/security-native-validation.json`,
`security-native-instrumentation.log`, `security-native-room26-upgrade.log` y
`security-native-room36-upgrade.log`. Se conservaron copias `.before-room37`
de la evidencia previa. Fingerprint del checkout y de la copia aislada coincidente:
`450df7177b94c96884f117ee4777ac75fda18ed71c0a2d4952d71a384c66f22c`.
El preflight original verificó la huella y el hash del registro principal;
los registros adicionales conservan sus hashes en el mismo JSON. No se relajaron
sus condiciones.

### Pendientes reales de revisión física

- Teléfono: legibilidad y contraste en sus temas, pantalla pequeña, teclado,
  escalas de fuente mayores y TalkBack. Las pruebas UI automatizadas no sustituyen
  esta comprobación ni acreditan todos los temas/dispositivos.
- Recorrido manual completo de filtros, cronograma, regreso y reanudación tras
  segundo plano, con datos representativos del uso real. Las pruebas dirigidas
  verifican relaciones y persistencia, no toda combinación visual posible.
- Algunas pruebas UI históricas aún describen menús anteriores; no se ejecutó
  la suite completa ni se declara adaptada por este cierre.
- Actualización física pendiente mediante protocolo oficial, firma habitual,
  versionCode compatible y respaldos externos verificados de ambas bóvedas.
  Este cierre no genera una APK oficial ni acredita compatibilidad con una
  instalación del teléfono cuya versión no fue consultada.

## Extensión posterior: Gantt integral

La validación histórica Room 37/JSON 29 arriba permanece como registro de aquel
cierre. La extensión Gantt utiliza Room 38 y JSON 30 para anotaciones, sin duplicar
entidades de Cascada. Su alcance, artefactos, pruebas y pendientes están en
[GANTT.md](GANTT.md). La revisión física sigue pendiente; no se genera release
oficial ni se reutiliza evidencia antigua como si validara el checkout nuevo.

## Sprint UX 2 — Acta, Objetivos y Equipo

El Acta de un contexto nuevo contiene Objetivos y Equipo. Las secciones documentales
son Node NOTE hijos del Acta: se añaden desde sugerencias o con título y contenido
propios, y reutilizan edición, orden, confirmación de borrado y liberación de archivos
existentes. No hay tablas, preferencias, tipos de archivo ni formato de backup nuevos.
La validación compartida exige las seis capas esenciales y admite las funciones
históricas de secciones opcionales. Los doce nodos de Actas anteriores mantienen sus
identidades, textos, posiciones y asociaciones; `ensure` no elimina ni repone secciones
opcionales. Al inicializar backups anteriores sin capas, se materializan los textos
documentales históricos que tengan contenido. Los adaptadores históricos de texto se
conservan, sin convertir destructivamente registros anteriores.

Los nodos documentales, sus títulos, contenidos, orden y adjuntos utilizan la captura,
validación y restauración existentes. Las pruebas dirigidas comprueban cierre/reapertura
de Room, restauración íntegra, rechazo de estructura esencial incompleta sin reemplazo,
conservación de Actas antiguas y ausencia de regeneración tras un borrado explícito.
Room y JSON conservan sus versiones; no se modifican módulos de exportación ni release.

La barra contextual ofrece dos botones de texto: creación semántica y Nuevo elemento,
sin abrir automáticamente el elemento guardado. Equipo presenta participantes de forma
directa y el selector conserva las personas incorporadas. Quitar del equipo siempre
requiere confirmación y conserva la Persona global. Roles generales y responsabilidades
sobre el trabajo tienen agrupaciones independientes; la ayuda de Objetivos y Roles ocupa
la pantalla. El acceso superior a Personas presenta avatares y permite abrir directamente
las responsabilidades de participantes del contexto o la ficha de personas asociadas.

### Validación de Sprint UX 2

`assembleDebug` y 42 pruebas dirigidas aprobadas: ManagementCascadeTest (7),
ManagementTest (6), ParticipationTest (15), ManagementCascadeUiTest (7),
ParticipationUiTest (5) y CharterUxTest (2). Incluyen compatibilidad de creación
UX 1, botones en una misma fila, creación tradicional sin rol implícito, persistencia
tras reabrir Room, orden de secciones, restauración histórica y rechazo íntegro de
backup incompleto, selección de varias personas sin ocultarlas, retirada confirmada
sin borrar la Persona global y acceso superior directo a responsabilidades.
Queda pendiente la revisión visual en dispositivo/emulador. Sin release, commit ni push.

Archivos de este bloque:

- UI: ProjectScreen.kt, ManagementCascadeUi.kt, ManagementNavigation.kt,
  ParticipationUi.kt, CompactCardIndicators.kt, ManagementHelp.kt y CharterSectionEditor.kt.
- Repositorios: ManagementCascadeRepository.kt y BacklogRepository.kt.
- Pruebas: ManagementCascadeTest.kt, ManagementTest.kt, ManagementCascadeUiTest.kt,
  ParticipationUiTest.kt y CharterUxTest.kt.
- Documentación: ARCHITECTURE.md y MANAGEMENT_CASCADE.md.

## Sprint UX 3 — Backlog, Épicas, Historias y Personas

Las pantallas de Equipo, incorporación, participantes, acceso superior a Personas y
selector de responsables reutilizan `ParticipantCard`: cuadrícula vertical de tres
columnas, fotografía cuadrada, franja inferior independiente con nombre truncado y
respaldo de avatar. Las incorporadas aparecen primero, con borde de acento, y siguen
visibles; tocar la celda completa abre sus responsabilidades. La retirada conserva
la confirmación y la Persona global. Los resúmenes de responsables dentro de una
Historia mantienen chips compactos con avatar, nombre y responsabilidades específicas.

Backlog y Épicas conservan sus ordenamientos y la barra contextual de dos botones.
Las tarjetas completas abren su contenido sin flechas redundantes; permiten crear
Épicas e Historias consecutivamente sin entrar automáticamente. Épica utiliza el
vector Workspaces e Historia Article; la Capa tradicional conserva su icono.
El recuento de Historias combina pertenencia y vínculos sin duplicar identidades.
Las Historias vinculadas desde otras Épicas aparecen identificadas como adicionales.

`StoryContext` distingue Épica principal, responsabilidades específicas, vínculos
adicionales y elementos de trabajo. La pertenencia se obtiene del padre existente;
los vínculos adicionales no trasladan nodos ni modifican fases. El inspector ofrece
«Vincular otra épica» y conserva las relaciones existentes. Las tarjetas de relaciones
y roles son clicables; sus acciones secundarias se agrupan en menús.

«Abrir original» utiliza la identidad del nodo almacenado y cierra primero el inspector.
Esto corrige el caso en que el nodo ya estaba abierto y la navegación retornaba antes
de cerrar el panel. Un destino ausente muestra una explicación sin acción vacía.
No se añaden datos persistentes, migraciones ni cambios de formato de backup.

Archivos de este bloque: ParticipationUi.kt, CompactCardIndicators.kt,
PersonComponents.kt, ProjectScreen.kt, BacklogUi.kt, ManagementCascadeUi.kt y
StoryContext.kt; pruebas ManagementCascadeUiTest.kt, ParticipationUiTest.kt y
BacklogUiTest.kt y TechnologyUiTest.kt; documentación MANAGEMENT_CASCADE.md.

### Validación de Sprint UX 3

Compilación Debug y 46 pruebas dirigidas aprobadas: BacklogTest (13),
ParticipationTest (15), ManagementCascadeUiTest (7), ParticipationUiTest (6),
BacklogUiTest (3) y CharterUxTest (2). Comprueban geometría de tres columnas,
proporción cuadrada, nombre inferior, desplazamiento vertical, incorporación y
conservación de roles, creación consecutiva, superficies clicables, jerarquía frente
a vínculos adicionales, cierre y navegación de «Abrir original», destino ausente y
compatibilidad UX 1/UX 2. La revisión visual manual en dispositivo sigue pendiente.
Sin release, commit ni push; no se inicia UX 4.

Comprobación final adicional: BacklogUiTest (3) volvió a pasar después de agrupar
la desvinculación de Objetivos en su menú; `assembleDebug` pasó con ese código.
También pasó `TechnologyUiTest.peoplePickerIsLazySearchableAndCancelPreservesAssignments`,
que comprueba carga perezosa, búsqueda y conservación de selección en el selector
común. Total: 47 casos dirigidos distintos aprobados. `git diff --check` sin errores.

## Sprint UX 4 — Equipo integrado y planificación de Sprints

Equipo conserva su nodo del Acta y su descripción editable. Debajo muestra directamente
Participantes mediante el retrato común de UX 3, con tres columnas, fotografías cuadradas,
nombre inferior y borde de acento. Incorpora varias Personas desde el selector de pantalla
completa; el equipo se actualiza por los flujos existentes. Tocar un retrato abre sus
responsabilidades. Atrás desde responsabilidades regresa al listado contextual y después
a Equipo; salir de Equipo utiliza la navegación jerárquica/de referencias que lo abrió.
Se elimina el cierre que abandonaba la capa y el acceso duplicado de Personas, conservando
los indicadores de otras funciones. La ausencia de tareas no muestra un Equipo vacío.
Las asignaciones tradicionales del contenido Equipo mantienen su identificación propia.

Sprints conserva ordenamientos, identidad y la barra Nuevo Sprint / Nuevo elemento.
Sus tarjetas muestran estado, fechas, número de Historias y progreso del motor existente.
Crear varios Sprints no los abre automáticamente. Dentro de un Sprint gestionado se muestra
un resumen, hasta tres títulos de Historias y sus responsables, sin controles de fase ni
checks. Planificar abre una pantalla completa con secciones de Fechas, Objetivo, Notas,
Historias, Equipo y Ciclo. El editor utiliza un calendario, permite limpiar fechas opcionales
y mantiene la validación de rango del repositorio. Objetivo y notas tienen campos separados.

Gestionar historias muestra tarjetas con icono, título, Épica de origen y estado, búsqueda,
incorporadas primero y disponibles después. Incorporar permanece en el selector; retirar
requiere confirmación y preserva padres, checks y referencias históricas según las reglas
existentes. Las tareas de una Historia se incorporan mediante `CascadeRelationsRepository`;
no se añade otro motor ni se duplican compromisos. Los Sprints cerrados permiten consulta
sin modificar su selección.

Consultar trabajo y tareas técnicas es una vista independiente de consulta: fases, checks,
compromiso inicial, añadidas y retiradas. Mantiene tareas directas y vínculos antiguos,
además de acceso a capas y notas del Sprint. El menú de cada tarea agrupa origen,
responsables y retirada técnica cuando corresponde. El trabajo derivado de Historias se
administra desde Gestionar historias. Las tareas directas conservan su ubicación; no se
ofrece una retirada que requiera moverlas. Historial de ciclos permite consultar resultados
conservados y volver al estado actual. Inicio, cierre y reapertura requieren confirmación
sin modificar sus reglas. Kanban, Gantt, fases, porcentajes y formatos no cambian.

Responsabilidades específicas del Sprint y responsables tradicionales conservan sus
asignaciones independientes. El selector contextual identifica que muestra participantes
del proyecto y que las asignaciones afectan al elemento seleccionado; se reutiliza la
cuadrícula existente sin añadir preferencias ni datos locales nuevos.

Archivos de este bloque:

- UI: TeamContext.kt, SprintContext.kt, IterationUi.kt, ParticipationUi.kt,
  ManagementCascadeUi.kt, ProjectScreen.kt y CompactCardIndicators.kt.
- Pruebas: ManagementCascadeUiTest.kt, ParticipationUiTest.kt e IterationUiTest.kt.
- Documentación: MANAGEMENT_CASCADE.md.

La revisión visual manual en dispositivo/emulador sigue pendiente. No se genera release,
ni se ejecutan commit o push. No se inicia UX 5.

### Validación de Sprint UX 4

`assembleDebug` y 58 pruebas dirigidas aprobadas: SprintRepositoryTest (18),
ManagementCascadeTest (7), IterationTest (15), ManagementCascadeUiTest (8),
ParticipationUiTest (6) e IterationUiTest (4). La cobertura verifica Equipo sin diálogo
inicial, tres retratos cuadrados en una fila, incorporación y retorno contextual con
Atrás, responsabilidades sin pérdida de Personas, creación consecutiva de Sprints,
incorporación/retirada confirmada de Historias, padres originales, conservación de
checks y tareas directas antiguas, calendario y campos separados, inicio/cierre y
reapertura, referencias históricas, reinicio/restauración y motor compartido de progreso.
`git diff --check` sin errores. Los cambios del bloque se limitan a presentación y
navegación sobre los repositorios existentes, sin cambiar esquema, formato ni motores.

## Sprint UX 5 — Kanban directo y fotografías completas

`PersonPhoto` presenta la imagen almacenada entera dentro de un cuadro 1:1 con
`ContentScale.Fit`, fondo del tema y sin máscaras ni encuadre aplicado. Conserva la
proporción y muestra los espacios sobrantes. El catálogo tiene tres columnas y nombres
truncados debajo; Equipo, participantes, responsables y selectores reutilizan el mismo
retrato. La ficha y las miniaturas de fotos de referencia también muestran la imagen
completa. Los usos históricos de `PersonAvatar(rounded=true)` delegan en este componente,
evitan reintroducir recortes y conservan su tamaño. Los indicadores compactos de avatar
conservan su encuadre existente. No se alteran las fotografías ni los metadatos guardados.

### Investigación de carga

La importación existente copia la imagen elegida, corrige su orientación, limita su
resolución y guarda un PNG privado con referencia relativa UUID. El encuadre se guarda
como metadatos, no como recorte del archivo. `PersonRepository` conserva `avatarFile` al
convertir las Personas; los componentes de gestión también transmiten esa referencia.
La lectura utiliza `privateStorageContext`, correspondiente a la sesión activa, y
`AvatarStore.readThumbnail` / `SecureFiles`, no el directorio sin sesión de la Activity.
La caché existente usa ruta privada, tamaño y modificación del archivo, mantiene su
límite y no almacena fallos de lectura. No se añaden permisos ni almacenamiento nuevo.

Se encontró un recorte de presentación: los retratos anteriores aplicaban zoom y
encuadre mediante `FramedAvatar`. Los cuadros ahora leen la fotografía completa y no
usan esos metadatos. El cargador diferencia ausencia de referencia, lectura en curso y
archivo no disponible/error, con respaldo visible. Cambiar la referencia reinicia la
carga y sustituye la imagen; los errores no se confunden con ausencia legítima de foto.
En el perfil inspeccionado del emulador conectado no existe un directorio de avatares.
Esto impide reproducir allí un fallo de carga de una fotografía existente; no se deduce
que el problema histórico del dogfooding tuviera una única causa. La inspección fue de
solo lectura y no abrió bases de datos ni claves ni modificó el perfil.

### Tablero y navegación

Abrir el nodo Kanban gestionado presenta directamente el tablero dentro de esa misma
entidad, con título y descripción editable. No muestra una capa vacía ni exige pulsar
Tablero. La pila de referencias y el proveedor de estado de la capa conservan Sprint,
consulta y vista al regresar del detalle; cerrar regresa al origen que abrió Kanban.

El selector de Sprint utiliza tarjetas completas con nombre, estado, progreso y borde
que identifica la selección. En ausencia de Sprints explica cómo crear uno y abre la
capa Sprints existente, sin generar datos automáticamente. Las vistas de Proyecto,
Backlog y Sprint, búsqueda y filtros de responsabilidades se conservan. Las fases
pueden consultarse por separado o juntas mediante controles que se distribuyen en
filas y evitan columnas estrechas o desplazamiento en dos direcciones.

Las Historias son contenedores consultables con resumen de su trabajo, no tareas
convertidas. Las tareas ejecutables conservan sus identidades únicas, incluidos hijos
directos y vínculos antiguos. Sus tarjetas reutilizan `NodeCard`, favoritos, controles
de avance/check y metadatos oficiales. El menú dentro de la tarjeta contiene Cambiar
fase y Consultar origen; los responsables aparecen mediante indicadores pequeños.
El selector de fases destaca la actual mediante selección visual. Se mantienen nombres,
validación, persistencia, checks y progreso ponderado del motor: el check avanza por sus
fases oficiales y no se añade ningún contador, tabla, trigger ni seguimiento paralelo.
Sprint y Gantt reciben el mismo estado existente; Gantt no se modifica.

Archivos de este bloque:

- UI: PersonPhoto.kt, PersonComponents.kt, ParticipationUi.kt, PeopleScreen.kt,
  PersonPhotoHeader.kt, ManagedKanbanUi.kt, ProjectScreen.kt, NodeComponents.kt y
  SprintComponents.kt.
- Pruebas: PersonPhotoUiTest.kt, KanbanUiTest.kt, ParticipationUiTest.kt y
  ManagementCascadeUiTest.kt.
- Documentación: MANAGEMENT_CASCADE.md.

No se genera release, commit ni push. No se inicia UX 6 ni se crea un proyecto de
demostración. Sigue pendiente la revisión visual del nuevo código en un dispositivo
con fotografías reales de usuario.

### Validación de Sprint UX 5

`assembleDebug` y 63 casos dirigidos distintos aprobados: SprintRepositoryTest (18),
KanbanTest (7), AvatarFramingTest (10), ManagementCascadeUiTest (8),
ParticipationUiTest (6), PersonGalleryUiTest (3), KanbanUiTest (4),
PersonPhotoUiTest (4) y PersonEvolutionUiTest (3). Las ejecuciones se acotaron a estas
clases y se repitieron solo los casos afectados por ajustes de pruebas y presentación.

La prueba de píxeles dibuja la vista real Android en un bitmap: comprueba ambos extremos,
esquinas y espacios sobrantes de fotos horizontales y verticales, incluso con zoom y
posición guardados. También verifica importación/lectura privada, ausencia frente a
referencia rota, sustitución al recomponer, catálogo de tres columnas y nombre inferior.
La cobertura de tablero verifica entrada directa sin diálogo/capa intermedia, detalle y
regreso al mismo Sprint, selector, fase actual destacada, cambio de fase y avance por
check en todas las fases de una tarea directa antigua. Se comprueba el progreso canónico,
identidad, padres, referencias históricas, conservación tras reapertura/restauración y
compatibilidad de Personas, galería y responsabilidades. Sin cambios en motores ni datos.
`git diff --check` sin errores. El aviso sonoro se emite desde Linux, fuera de Arachn0de.

## Sprint UX 6 — Gantt y demostración integral

El acceso Gantt ya abría la herramienta directamente desde la tarjeta de gestión;
se conserva esa ruta y el contexto real, sin añadir una capa ni implementación
independiente. La vista de teléfono utiliza el `GanttDocument` existente: nombres
fijos, calendario con desplazamiento horizontal compartido y una única lista vertical.
Las filas muestran tipo, estado, fechas reales, duración inclusiva cuando hay ambos
extremos, progreso y completitud. Las barras recortan únicamente el intervalo visible;
la línea del día actual no cambia las fechas. Los elementos sin fechas tienen su propio
apartado. Semana/mes ahora incluyen actividades que atraviesan el período, en lugar de
excluirlas por vencer después; consultar nunca modifica datos antiguos.

Días/semanas/meses están accesibles arriba junto a Anterior/Hoy/Siguiente. Opciones
conserva Semana/Mes/Sprint/Todos, selección de Sprint, personas, detalle, fechas de
Historias y anotaciones. Se selecciona inicialmente el Sprint activo disponible.
El lienzo anterior y su arrastre de anotaciones permanecen disponibles; la exportación
PNG mantiene el renderizador, paginación y SAF existentes. La vista de teléfono no es
un segundo almacén ni motor: recoge las invalidaciones de fechas/fases/checks desde
los mismos repositorios. Pulsar una fila abre el original y la navegación contextual
permite volver al cronograma.

### Demostración explícita

**Proyectos → Opciones → Crear proyecto de demostración** solicita confirmación.
Reutiliza el generador Gantt existente, actualizado para las secciones opcionales del
Acta de UX 2 y convertido en una operación transaccional. Crea un nombre identificable
«Demo — Gestión de proyectos · <identificador>», seis Personas ficticias con iniciales,
tres objetivos y criterios, propósito, alcance, restricciones, entregables, éxito y
riesgos como Notas del Acta, Equipo y responsabilidades, tres épicas, nueve Historias,
27 acciones, subtareas, Notas, prioridades, favorito y dos anotaciones. Las Historias
conservan sus padres y asociaciones reales con Sprints; metas y fechas relativas
cubren un Sprint cerrado, otro activo y otro preparado. Hay fases no planificadas,
planificadas, haciendo, terminadas y validadas, actividades consecutivas/superpuestas,
fechas propias de Historias y tareas sin fecha o responsable.

No incorpora Personas reales, fotografías ni archivos; tampoco cambia proyectos
existentes. Cada nueva instancia tiene nuevas identidades. Error o cancelación antes
del commit revierte toda la estructura y Personas. Se usan las tablas y backup actuales,
sin migración, preferencia, versión de formato, descarga ni motor nuevo.

Se corrigió un bloqueo existente del borrado completo de proyectos gestionados:
la ruta valida que el contexto y sus relaciones no incluyan extremos externos, retira
los contextos propios (y su historial por CASCADE) antes de los nodos con FK RESTRICT,
y termina todo en la transacción normal del proyecto. El borrado parcial de nodos
mantiene sus protecciones. No se borran Personas: las ficticias permanecen en el
catálogo global y pueden retirarse allí; la confirmación informa este comportamiento.

Archivos de UX 6: `GanttTimeline.kt`, `ProjectGanttUi.kt`, `ProjectsScreen.kt`,
`GanttDemo.kt`, `GanttModel.kt`, `ProjectRepository.kt`, `BacklogRepository.kt`,
`ManagementContextEntity.kt`; pruebas `GanttTest.kt`, `GanttUiTest.kt`, `BacklogTest.kt`;
documentación `GANTT.md` y este archivo. El nuevo método DAO de borrado no altera esquema.

### Validación de Sprint UX 6

`assembleDebug` aprobado y 45 casos dirigidos distintos aprobados: GanttTest (9),
GanttUiTest (5), BacklogTest (13), DeepDeletionTest (6), KanbanUiTest (4) y
ManagementCascadeUiTest (8). La ejecución conjunta inicial final aprobó 44 casos;
el ajuste de límites de ancho del cronograma se recompiló y verificó con los 13 casos
Gantt afectados. El quinto caso UI se ejecutó por separado y aprobó el recorrido con
la demostración real: Acta → Objetivos/Equipo → Backlog/Épica/Historia → Sprint →
Kanban → Gantt. El arnés espera las proyecciones asíncronas antes de buscar tarjetas.

Se verifican fechas superpuestas y ausencia de fechas sin escrituras, escalas y límites
de paginación, anotaciones y compatibilidad JSON 29/30, roles y vínculos reales, cambios
desde el check oficial de Kanban reflejados en Gantt, títulos únicos por identidad,
confirmación de creación y segunda instancia, rollback por error/cancelación al final
de la creación, snapshot completo tras restaurar/reabrir y recuperación en una base
vacía. El borrado elimina el contexto, nodos e historial propios y conserva otro demo,
el proyecto real de control y Personas globales. La entrada directa desde el proyecto,
el detalle original y el regreso a Gantt, controles y anotación se prueban también con
fuente ampliada. No se alteran motores de fases, checks ni progreso.

`git diff --check` sin errores. La revisión visual en dispositivo y la exportación
mediante un proveedor SAF real siguen pendientes; estos ensayos son JVM/Compose con
Robolectric, no una nueva validación nativa de release. Se conservaron los cambios y
artefactos históricos, sin actualizar su evidencia como si fuera de UX 6. No se instaló
la aplicación ni se precargaron datos en el perfil del usuario. La demostración se
crea únicamente mediante su acción explícita. No hay commit, push, release, APK oficial
ni nuevo sprint iniciado. Se espera revisión visual y autorización del usuario.

El sonido Linux se verificó al inicio con `paplay` y el archivo del sistema
`message.oga`. El aviso final se emite después de completar modificaciones,
compilaciones, pruebas y documentación, sin instalar paquetes ni modificar Arachn0de.


## Ajustes visuales finales — preparación 0.4.0 / 12

Se conserva la paleta actual. Personas mantiene tres columnas: tarjetas rectangulares,
foto Fit completa sin margen superior y franja independiente para nombre y menú. Las
cuadrículas de participación ya comparten esa estructura; los avatares compactos y
los originales no se modifican. Las pruebas de fotografía verifican límites, ajuste,
nombres y apertura del menú.

NodeCard, ProjectCard y previsualizaciones del Backlog omiten descripciones; el detalle,
editor y datos almacenados las conservan. La base común es 72 dp, con padding horizontal
12 dp y vertical 8 dp; los títulos pueden envolver. Se elimina la fila vacía que aparecía
por vencimientos cuando sus indicadores ya estaban en la fila compacta. Se conservan
estados, etiquetas, progreso y controles operativos de Kanban/Sprint.

Pagos usa una sola tarjeta con Este mes, Próximo mes y Total pendiente por moneda.
Reutiliza thisMonthPendingMinor, nextMonthPendingMinor y pendingMinor del overview
filtrado existente; excluye completados sin eliminar cálculos ni historial. Usa tres
columnas cuando caben las cifras y disposición vertical cuando el ancho o fuente lo
requieren. No recorta importes. No hay cambios de tablas, backup ni almacenamiento.

Validación: 39 casos dirigidos aprobados (PersonPhotoUi 4, FinancialSnapshot 6,
FinancialRepository 6, FinancialUi 5, ObligationRepository 8, ObligationMigration 2,
CompactPreviewUi 4 y KanbanUi 4). Las expectativas históricas de Room 27 y 17 triggers
se actualizan al esquema vigente Room 38 y sus 19 triggers; se siguen comprobando filas,
relaciones, filtros y conservación. Debug compila y git diff --check pasa. Los logs están
en /tmp/final-visual-final.log (37 casos aprobados y un hallazgo de fila vacía) y
/tmp/final-visual-spacing.log (7 casos aprobados tras corregir esa fila) y
/tmp/final-visual-accessibility.log (4 casos aprobados, incluido título completo con
fuente al 180 % y ancho reducido). No se acredita
una nueva ejecución de lint: las dos ejecuciones anteriores quedaron canceladas después
de detectar pruebas fallidas.

Se prepara versión 0.4.0 / 12 con applicationId, clave y alias habituales. Las APK
oficiales históricas, hasta código 11, conservan el certificado oficial y no se borran.
El helper manual conserva lectura silenciosa desde /dev/tty y exige evidencia nativa
actual y APK instalada con firma/identidad oficiales y código inferior a 12. La firma,
comprobación del teléfono y evidencia nativa siguen pendientes. RELEASING.md contiene
los comandos de lectura y firma local. No se presenta una Debug como actualización
oficial ni se inventa evidencia. El preflight bloquea la distribución del checkout
sin validar. No hay instalaciones automáticas, desinstalaciones, borrado de datos,
commits, push ni publicaciones.


Preparación nativa: los dos APK de la copia temporal se compilaron con éxito y se
verificaron sus firmas de pruebas, identidad y SHA-256. El manifiesto de preparación
está en /tmp/arachnode-native-preparation-manifest.json y el build final en
/tmp/arachnode-native-preparation-final-build.log. La huella normalizada coincide con
el checkout; esto acredita preparación, no ejecución de pruebas nativas. El paquete
es com.r0ybt.arachn0de.securityvalidation y su runner .test. Se solicita autorización
expresa para instalar exclusivamente esos paquetes en emulator-5554, conforme a la
prohibición de instalaciones automáticas del documento del usuario. La app real y sus
datos quedan fuera. El aviso sonoro de intervención se emitió mediante paplay.


### Validación nativa autorizada — 11 de octubre de 2026

El usuario autorizó únicamente emulator-5554. Se instalaron con -r -t el paquete
com.r0ybt.arachn0de.securityvalidation y su runner; no se instaló ni se modificó la
app de producción. Aprobaron los tres casos oficiales NativeVaultTest y, por separado,
las migraciones cifradas genuinas 26→38, 36→38 y 37→38, con conservación de ambos
almacenes, fotografías, backups históricos y anotaciones Gantt/JSON 30. Son seis casos
nativos adicionales a los 39 dirigidos JVM/Compose. Se renovó el registro únicamente
tras comprobar OK (3 tests)/OK (1 test), SHA-256 de logs y huella del checkout. La evidencia
anterior quedó archivada en release-assets/native-evidence-before-code12-20261011T202736Z.

El verificador de evidencia nativa aprueba. El helper --check supera ese paso y se
detiene porque falta ARACHNODE_INSTALLED_APK del teléfono real. Aún no se firmó ni
se generó la APK oficial: primero se debe comprobar identidad, certificado y versionCode
de esa instalación, y después ejecutar el helper con la contraseña silenciosa en la
terminal. Las observaciones anteriores de evidencia pendiente quedan resueltas por
esta validación; la comprobación del teléfono y la firma permanecen pendientes.


### Alcance confirmado: validación aislada, sin firma oficial

El usuario ratifica la autorización exclusiva de securityvalidation y sus pruebas en
emulator-5554 y ordena no firmar ni publicar todavía. Se mantiene esa restricción; no
se solicita ahora intervención con el teléfono ni contraseña de firma. La evidencia
ya ejecutada vuelve a verificar su huella y los cuatro logs: seis casos aprobados, sin
fallos de ensayo. Todos los fixtures son sintéticos y usan contextos/directorios únicos
bajo el paquete aislado; no se usa información real del usuario.

Resultados concretos: SQLCipher conserva texto UTF-8, números, BLOB y NULL; rechaza
una clave incorrecta y la lectura SQLite sin clave. La copia cifrada conserva los bytes
del origen. La interrupción inyectada antes de activar la migración deja la sesión sin
activar, conserva el proyecto y archivo originales y permite reintentar con la misma
contraseña, recuperando ambos. Las migraciones genuinas 26/36/37→38 conservan las filas
históricas comprobadas, identidades y separación de almacenes; restauran imágenes y
backups, y el caso Gantt verifica anotaciones y foreign_key_check. Esta conclusión
corresponde a esos casos y a ese punto de fallo inyectado, no a todos los fallos posibles.
No se ejecutan otra vez las mismas pruebas sin cambios que lo justifiquen. No se
instala, actualiza, desinstala ni borra el paquete principal, ni se modifica otro
dispositivo. La APK oficial permanece sin firmar ni publicar.


Preferencia confirmada del usuario: se envía la APK por WhatsApp y la abre en el
teléfono para instalar/actualizar; no utiliza ADB como flujo habitual. No asumir
conexión ADB para futuras instalaciones. Firma y publicación continúan en pausa.


### Firma oficial 0.4.0 / 12 autorizada

El usuario autoriza expresamente la firma oficial y permite continuar sin comprobar
la instalación exacta del teléfono. Se retira esa exigencia del helper: verifica las
seis APK oficiales locales, identidad y certificado habituales, códigos 5/6/7/9/10/11
inferiores a 12; si se aporta voluntariamente una APK instalada también la verifica.
La limitación del teléfono desconocido se informa. El preflight aprueba la evidencia
nativa existente sin repetir los 39 casos dirigidos ni los seis nativos. No se modifican
fuentes de aplicación, clave, identidad ni políticas de backup.

Se inicia el mismo helper de firma en una terminal gráfica local con lectura silenciosa
/dev/tty. Las contraseñas no se solicitan en el chat ni se registran. La compilación usa
la caché offline y no guarda configuración con credenciales. No se instala, desinstala,
borra ni modifica ningún dispositivo; no hay commit, push ni publicación. La APK solo
se considerará lista cuando termine la firma y aprueben las verificaciones posteriores.


### APK oficial completada — 0.4.0 / 12

La terminal local terminó con código 0. Se generó y verificó la APK Release oficial
release-assets/Arachn0de-v0.4.0-code12.apk (160815769 bytes, 153.37 MiB), applicationId
com.r0ybt.arachn0de, versionName 0.4.0, versionCode 12. apksigner verifica firma v2,
un firmante y certificado SHA-256 oficial
 d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437.
Se comprobaron checksum, integridad ZIP y coincidencia byte a byte con la salida Release.
SHA-256 de APK: 89910b3acd81f2ddcb99d0b6665073d916d1f896acff0747629dae6b63a1611d.
El helper verificó ausencia de debuggable/instrumentación, políticas existentes,
recursos y bibliotecas SQLCipher ARM antes de copiar. No se repitieron las pruebas.

Lista para enviar manualmente como Documento por WhatsApp. La versión/firma exactas
instaladas en el teléfono no se verificaron, conforme a la excepción autorizada; la
compatibilidad comprobada corresponde a las seis referencias oficiales anteriores.
No se instaló, desinstaló ni borró nada en dispositivos. No hubo commit, push ni
publicación. Las APK anteriores se conservan. Chicharra final mediante paplay.


## Rediseño acotado de navegación general — UI/UX

Menú lateral: Buscar, Proyectos y Metro permanecen fuera de grupos, en ese orden.
Seguimiento contiene Favoritos, Avisos (mismo destino de Atención), Obligaciones y
Calendario, que se conserva. Recursos contiene Personas, Tecnologías y Etiquetas.
Ajustes contiene Seguridad, Respaldo (mismo destino de Respaldos) y Apariencia.
Captura rápida, Acerca de y Juego siguen accesibles. Los grupos empiezan contraídos,
se abren/cerran independientemente, tienen indicador discreto y estado accesible, y
restauran expansión ante recreación. No se guardan preferencias nuevas. Los accesos
principales quedan fijos cuando hay altura suficiente; en ventanas inferiores a
400 dp el conjunto permite desplazamiento para no impedir acceso a sus destinos.
Las secciones inferiores disponen de scroll propio en el resto de ventanas.

Menú inferior: contenedor transparente, botones individuales cuadrados con esquinas
12 dp y separación 8 dp. Candado y captura usan fondo individual únicamente en este
menú; su uso en cabeceras conserva el estilo anterior. Creación y capas mantienen sus
acciones, iconos y habilitación; creación mantiene su etiqueta. Los controles comparten
tamaño adaptativo, sin elevación, con mínimo previsto 56 dp ajustado por fuente y límite
80 dp ajustado por fuente para no crecer innecesariamente en tabletas. Si no caben se
reparten en filas. Se comprueban geometría, separación, orden, interacción y fuente al
180 %. AppSafeArea y navegación del sistema se conservan.

Archivos de producción modificados en este bloque:
- app/src/main/java/com/r0ybt/arachn0de/ui/NavigationChrome.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/OfficialIcons.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/ProjectScreen.kt

Pruebas dirigidas y comprobaciones nuevas/adaptadas:
- app/src/test/java/com/r0ybt/arachn0de/ui/GlobalNavigationUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/OfficialIconsUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/VisualActionsNavigationTest.kt

Se adaptan únicamente los pasos de entrada al drawer en las pruebas existentes
siguientes, para abrir su grupo y usar Avisos donde correspondía Atención; no se
cambian sus escenarios de dominio ni se ejecutan estas suites adicionales:
- app/src/test/java/com/r0ybt/arachn0de/ui/NotesUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/CalendarUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/AttentionUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/NodeSortUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/TechnologyUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/FinancialUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/PersonIntegrationUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/ObligationUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/AppearanceRestartUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/AppearanceUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/MvpReadinessTest.kt

Validación final: assembleDebug aprobó; 23 casos únicos de navegación aprobados
(GlobalNavigation 10, OfficialIcons 5 y VisualActionsNavigation 8). La ejecución
/tmp/navigation-redesign-verified.log aprobó 22 casos y detectó el uso incorrecto del
simulador de restauración sobre MainActivity con contenido ya instalado. Ese caso se
movió a la actividad vacía de GlobalNavigationUiTest y aprobó por separado en
/tmp/navigation-redesign-restoration.log. Se corrigió también el cierre del entorno
VisualActionsNavigation para disponer composiciones y detener observadores antes de
cerrar la base sintética. Estos son arreglos del harness, no cambios de base ni de
persistencia de la aplicación. git diff --check sin errores.

No hay cambios de Gantt, Kanban, lógica de proyectos, BD, migraciones, dependencias,
paleta, identidad, firma ni protocolo de release. No se genera APK oficial, no se
instala en dispositivos, no se ejecutan suites completas ni instrumentación nativa,
y no hay commit/push. La APK oficial anterior se conserva y no contiene este nuevo
bloque de UI. La evidencia histórica de release no se presenta como validación nativa
de estas fuentes nuevas. Pendiente revisión visual en teléfono real; las comprobaciones
actuales son JVM/Compose/Robolectric. Aviso sonoro final mediante paplay.


## Correcciones puntuales UI/UX de Metro y Personas

Metro usa HeaderBar común, con hamburger de 48 dp y padding 12/10 dp, también en mapa
ampliado. Se elimina su antigua cabecera y la acción global duplicada del mapa. Los
accesos principales a Proyectos/Metro y el drawer general no cambian. Opciones de Metro
agrupa Viajes (Viajar, guardados, recientes), Preferencias (restricciones, estaciones
favoritas, vibración) y Configuración (general, Casa y horarios). Inicio y Líneas se
conservan; las opciones antes dispersas se trasladan a sus páginas, con las mismas
acciones. Recientes consulta sesiones existentes. El usuario confirmó conservar las
estaciones favoritas globales: no se añade asociación por Persona. El marcador conserva
20 dp, el testTag y la ubicación calculada; reutiliza Icons.Train frontal y colores del
tema sobre fondo contrastante. No se cambia MetroTimeline, tracking, planificación,
catálogo, coordenadas ni persistencia de Metro.

Las miniaturas y las fotos del gestor de Persona abren PersonPhotoViewer único, incluida
la principal en su secuencia. Muestra la fuente completa mediante Fit, sin máscaras,
con anterior/siguiente, pellizco, arrastre, Ampliar/Alejar/Restablecer y cierre sin guardar.
Reutiliza ZoomableImage extraído del visor de adjuntos; se comprueba también ese visor.
Solo Establecer como avatar principal cambia los datos. El intercambio conserva la
principal anterior en una referencia cuando no estaba ya representada, sin nuevos
archivos ni recompresión; mantiene acciones de añadir/reemplazar/ordenar/eliminar.
La transacción, bloqueo, diario y cleanup compartidos protegen las imágenes ante fallos.

BirthdayField compartido añade calendario Material con selección de año, mes y día y
modo de entrada rápida, conserva entrada ISO, vacío y fechas antiguas. Se usa en creación,
edición de identidad y detalle. Persona y cumpleaños se guardan juntos en las tablas
existentes; el editor espera cargar la fecha antes de permitir guardar, para no borrarla
por un estado inicial vacío. El ensayo de creación detectó un bucle de medición de los
campos de texto en el contenedor AlertDialog; se corrigió usando el patrón Dialog/Surface
acotado y desplazable del detalle de Persona, con botones persistentes. La prueba final
NATIVE de creación/calendario/edición aprobó; se retiró el diagnóstico temporal.

Archivos de producción de este bloque:
- app/src/main/java/com/r0ybt/arachn0de/metro/MetroScreen.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/AttachmentViewer.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/PersonPhotoViewer.kt (nuevo)
- app/src/main/java/com/r0ybt/arachn0de/ui/PersonPhotoHeader.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/BirthdayField.kt (nuevo)
- app/src/main/java/com/r0ybt/arachn0de/ui/PersonDetailUi.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/PeopleScreen.kt
- app/src/main/java/com/r0ybt/arachn0de/ui/state/PersonActions.kt
- app/src/main/java/com/r0ybt/arachn0de/data/repository/PersonRepository.kt

Pruebas nuevas/adaptadas:
- app/src/test/java/com/r0ybt/arachn0de/data/PersonGalleryTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/PersonGalleryUiTest.kt
- app/src/test/java/com/r0ybt/arachn0de/ui/BirthdayFieldUiTest.kt (nuevo)
- app/src/test/java/com/r0ybt/arachn0de/metro/MetroUiTest.kt

Validación: Debug compila; 20 casos únicos dirigidos aprobados: PersonGalleryTest 8,
PersonGalleryUiTest 5, BirthdayFieldUiTest 1, MetroUiTest 5 y un caso existente de
AttachmentEditorUiTest (sin modificar ese archivo). Evidencia final distribuida en
/tmp/metro-personas-gallery-final.log (8 datos y 4 UI aprobados),
/tmp/metro-personas-last.log (5 Metro, calendario y adjuntos aprobados) y
/tmp/metro-personas-form-fixed.log (creación/edición corregida aprobada). Los logs
anteriores incluyen los hallazgos corregidos y no sustituyen esos resultados.
git diff --check pasa. No se ejecuta la suite completa ni instrumentación nativa.

Documentación: este registro y BACKUP_FORMAT.md describen reutilización del formato,
referencias compartidas y conservación. Room, esquema y versiones de backup no cambian.
No se modifica Gantt, Kanban, firma ni protocolo de release; no se genera APK oficial,
no se instala nada ni se hace commit/push. La APK oficial anterior permanece intacta y
no incluye este bloque. Pendiente revisión visual, gesto táctil y calendario en teléfono
real; los ensayos son JVM/Compose/Robolectric. Chicharra final mediante paplay.

## Bloque 3 — Seguridad y tarjetas (UI/UX)

Capturas bloqueadas por defecto, autorización manual volátil sin temporizador propio y conservación durante navegación/recreación. El cierre definitivo o de sesión revoca el permiso; los diálogos de contraseña siguen protegidos. Seguridad usa una pantalla completa con encabezado común, vuelta/cierre y contenido desplazable; conserva bloqueo en segundo plano y opciones de contraseña.

Las tareas muestran un único botón genérico de Tecnologías cuando tienen asignaciones; abre el selector existente. Favoritos, opciones e iconos de detalle involucrados usan contenedores visuales de 28 dp cuadrados con esquinas redondeadas y áreas táctiles de 48 dp. «Copiar con descendientes» utiliza CopyAll y «Convertir en proyecto» conserva AccountTree.

No había una exportación de tarjetas como imagen: existían únicamente otros informes PNG. Se añadió «Compartir como imagen» a las acciones del nodo, con título, estado, fechas, descripción y fotografías/adjuntos/avatar/iconos directamente asociados. Android Sharesheet recibe una URI de contenido con permiso de lectura; no depende de WhatsApp. Los originales se leen sin modificación; caché, cifrado, límites y recuperación se documentan en ARCHITECTURE.md y BACKUP_FORMAT.md. Tarjetas superiores a 1080 × 8192 px se rechazan explícitamente para evitar imágenes truncadas.

Archivos de este bloque (se conservan los cambios anteriores del repositorio):

- Navegación/seguridad: `MainActivity.kt`, `security/ScreenCaptureController.kt`, `security/VaultManager.kt`, `security/SecurityUi.kt`, `ui/GlobalNavigation.kt`, `ui/AppRoot.kt`.
- Tarjetas: `ui/CompactCardIconButton.kt`, `ui/ElementAccessUi.kt`, `ui/CompactCardIndicators.kt`, `ui/TechnologyComponents.kt`, `ui/NodeComponents.kt`.
- Compartir y mantenimiento: `report/CardShareSource.kt`, `report/CardShareRenderer.kt`, `report/CardShareFiles.kt`, `report/ReportFileProvider.kt`, `Arachn0deApplication.kt`.
- Pruebas: `security/ScreenCaptureControllerTest.kt`, `security/ScreenCaptureUiTest.kt`, `ui/GlobalNavigationUiTest.kt`, `ui/TechnologyUiTest.kt`, `report/CardShareFilesTest.kt`.
- Documentación: `ARCHITECTURE.md`, `BACKUP_FORMAT.md`, `MANAGEMENT_CASCADE.md`.

No se cambian esquema, migraciones, firma ni protocolo de release. No se instala en dispositivos ni se genera una APK oficial. Pendiente de comprobación manual en teléfono: aspecto con tamaño de fuente/configuración reales, captura física y recepción del PNG en WhatsApp. La cancelación del selector conserva únicamente un PNG temporal sujeto a limpieza, nunca cambia la tarjeta u originales.

Validación del bloque: `:app:assembleDebug` satisfactorio; 13 casos dirigidos satisfactorios (2 controlador de capturas, 3 UI de capturas, 1 navegación de Seguridad, 1 acceso compacto a Tecnologías y 6 PNG/URI/cifrado/limpieza). No se ejecutaron suites completas ni instrumentación. En Robolectric se utiliza `BackupFixture.syncDirectory` para la sincronización de directorios, como en las pruebas existentes; la producción conserva `syncBackupDirectory`. Verificados permiso exclusivo de lectura, denegación de escritura, sesión propietaria, originales idénticos byte a byte, cancelación/fallo sin PNG parcial, reintento de limpieza y conservación de envíos recientes al alcanzar el límite. `git diff --check` satisfactorio.

## Bloque 4 — Planificación y Recurrencias

El acceso visible de Gestión se llama Planificación. Se retira el botón suelto de configuración y se añade Planificación a las opciones de la tarjeta del proyecto y del encabezado contextual. El icono oficial existente se conserva. Gantt conserva su acceso, y Acta, Backlog, Sprints y Kanban siguen siendo las mismas capas y herramientas.

Inspección funcional: AGILE, TRADITIONAL e HYBRID solo se usaban para los textos explicativos del selector y para guardar/validar/exportar el valor histórico. `ManagementRepository.configure` activa la misma cascada sin condiciones por metodología. Se retira el selector; la configuración conserva la metodología histórica y utiliza AGILE únicamente para proyectos nuevos, como antes. No hay cambios de esquema, backup o lógica de herramientas. Planificación y Modo Sprint continúan independientes. Limitación preexistente: Modo Sprint se activa en capas mediante sus menús; el proyecto raíz no tiene un campo propio de Modo Sprint. No se añade uno en este bloque.

Recurrencias desaparece de encima de la lista de proyectos y se abre desde Seguimiento en el menú lateral, reutilizando el mismo gestor global y sus funciones. Proyectos y Metro permanecen como accesos principales.

Archivos: `ui/ManagementSection.kt`, `ui/ProjectComponents.kt`, `ui/ProjectScreen.kt`, `ui/ProjectsScreen.kt`, `ui/PlanningUi.kt` (solo título), `ui/NavigationChrome.kt`, `ui/AppRoot.kt`, `ui/GlobalNavigationUiTest.kt` (pruebas dirigidas) y este documento. Sin cambios de firma, release, datos ni instalaciones.

Validación: una única ejecución de `:app:assembleDebug` y dos pruebas dirigidas satisfactorias: Recurrencias accesible exclusivamente desde Seguimiento y apertura/cierre de su gestor existente; Planificación desde la tarjeta, ausencia del selector y conservación de la metodología histórica HYBRID y de los valores Sprint. `git diff --check` satisfactorio. No se ejecutaron suites completas ni pruebas instrumentadas.

## Kanban — simplificación visual compacta

Cambios localizados en `ui/ManagedKanbanUi.kt`: título y descripción conservados; buscador inmediatamente después de la descripción; una fila de tres desplegables (Ámbito, Responsable y Fase), con anchos iguales y texto que puede ocupar varias líneas. Iteración/Sprint sigue siendo el valor predeterminado y conserva su selección. Proyecto, Backlog, búsqueda, responsables, fases y operaciones utilizan exactamente los repositorios y filtros existentes. «Sin fase Sprint» continúa disponible cuando existen tareas sin fase, para conservar el acceso previo en Proyecto/Backlog.

Las tarjetas específicas de Kanban tienen dos filas: check/título/favorito y metadatos compactos/opciones. No hay recorte por número de líneas del título. Se retiran del resumen los textos de estado, origen, compromiso y responsables extensos; el detalle, la planificación y el menú de fase conservan sus accesos y datos. El check mantiene sus condiciones, colores y operaciones anteriores. Se conserva «Consultar origen» en las opciones, con el mismo destino anterior.

Se retiran únicamente las tarjetas visuales de historias HU anteriores a las etapas. Sus relaciones siguen intactas y disponibles en las otras herramientas. Selección de Sprint, progreso y Planificación e historial tienen presentación compacta. No hay cambios de datos, relaciones, repositorios, migraciones, Gantt, firma o release.

Archivos: `ui/ManagedKanbanUi.kt`, `ui/KanbanUiTest.kt` y este documento. Validación dirigida: compilación Debug y cinco casos UI de Kanban (ámbitos, búsqueda/vacíos, fase manual, checks sucesivos, detalle, historial, fuente grande, responsables, favorito independiente). La comprobación final de dos casos amplía la verificación del favorito y de historias HU sin repetir compilación Debug ni los otros casos. Pendiente únicamente revisión visual en teléfono real con su tamaño de fuente y ancho particulares; en anchos pequeños los valores de los selectores se ajustan en varias líneas, aumentando la altura de esa única fila.

Resultado: cinco pruebas dirigidas aprobadas y los dos casos ampliados también aprobados. Color de título, negrita y tachado se conservan explícitamente; tras ese ajuste exclusivamente visual se actualizó Debug sin repetir pruebas. `git diff --check` satisfactorio. No se ejecutaron suites completas, instrumentación, instalaciones ni release oficial.

## Gantt definitivo compacto — etapas A–D

Terminado: numeración visual HU consecutiva por proyecto, estable antes de filtros; orden `createdAt` + ID y compactación tras eliminación. Se conserva cada título/ID original y el mismo formateador se usa en Backlog, Sprint, tarjetas y encabezados de historias. El modelo de fechas/progreso sigue siendo la proyección existente; no se inventa finalización real ni se añaden indicadores de puntualidad.

El gráfico utiliza una única geometría compartida por Android/PNG: gutter 62 unidades, columnas/filas 18, círculos 16 y barras 8. El mes se muestra a la izquierda de los días en la misma fila y cambia al desplazar el calendario. Las filas compactas muestran solo HU; tocar la etiqueta abre el nodo original. La paleta específica #202124/#F0F0F0/#AAAAAA/#66686A/#3D3F42/#66686C/#D8D8D8/#B86A2C queda aislada de los temas globales. Encabezado con Sprint, progreso y fechas; Opciones agrupa escala, período, Sprint, responsable y acceso a notas guardadas. Anterior/Hoy/Siguiente desplazan ventanas coherentes con la escala seleccionada.

Las notas tienen marcadores numerados con área semántica/táctil de 48 dp. Se crean tocando el calendario; se leen/editan/eliminan al tocar el marcador y se mueven mediante arrastre. Se conserva la estructura de anotaciones y se guarda el mismo UUID mediante el repositorio original. El editor contextual tiene calendario, texto y acciones explícitas, sin fecha manual ni deslizador. Las colisiones se apilan de forma determinista solo en presentación; la posición normalizada original no se reescribe al abrir/filtrar. Las anotaciones fuera del período permanecen accesibles desde Opciones.

PNG: mismo renderer del gráfico Android, encabezados repetidos por bloque, identidad discreta y sin páginas auxiliares de metadatos. Solo si hay notas visibles se añaden imágenes «ARACHN0DE — ANOTACIONES», con numeración coincidente, fecha, HU derivada cuando corresponde y texto completo. Se conserva el máximo de 4 millones de píxeles por PNG y 512 imágenes; se pagina calendario/HU y comentarios sin truncar textos. Los archivos continúan como derivados externos SAF; no se añade caché privada, esquema o versión de backup.

Archivos de implementación:

- `data/repository/StoryNumbering.kt`.
- `gantt/GanttModel.kt`, `gantt/GanttCompactModel.kt`, `gantt/GanttRenderer.kt`.
- `ui/GanttTimeline.kt`, `ui/ProjectGanttUi.kt`.
- Mínima numeración compartida: `ui/BacklogUi.kt`, `ui/IterationUi.kt`, `ui/GlobalNavigation.kt`, `ui/AppRoot.kt`, `ui/NodeComponents.kt`, `ui/ProjectScreen.kt`.
- Pruebas: `gantt/GanttCompactTest.kt`, `data/GanttTest.kt`, `ui/GanttUiTest.kt`.
- Documentación: `ARCHITECTURE.md`, `BACKUP_FORMAT.md` y este documento.

Validación: Debug satisfactorio; 14 casos dirigidos aprobados (6 numeración/geometría/PNG/Unicode/paginación, 4 fechas/progreso/persistencia/backup y 4 UI/detalle/fuentes/gestos). Se corrigió un bucle de medición del campo multilineal mediante editor acotado y altura estable; se repitieron solo los casos afectados y las comprobaciones nuevas. Muestra PNG inspeccionada: `/tmp/arachn0de-gantt-review.png`. `git diff --check` satisfactorio. No se ejecutaron suites completas, instrumentación, instalaciones, APK oficial, release, commit ni push.

Limitaciones reales: pendiente revisión táctil/SAF en teléfono real. La asociación HU de una nota se infiere de la posición original, pues el formato existente no guarda una FK a historia; cambiar qué filas se consultan puede cambiar esa asociación visual, como con las posiciones relativas anteriores, sin cambiar los registros. Para consultas extremas Android solicita reducir el período si un eje supera 100.000 píxeles. Las exportaciones que excedan 512 imágenes fallan explícitamente antes de generar imágenes gigantes. Los textos/fechas reales siguen disponibles en el nodo original; no se transforma información histórica para inventar fechas de cumplimiento.

## Black & White 90 — primera etapa visual

Alcance completado: paleta central del tema predeterminado Arachn0de (etiqueta visible «Black & White 90»), cinco recursos oficiales, vectores Lucide locales, barra inferior principal del proyecto, menú lateral, componentes compartidos de proyectos/capas y presentación del catálogo Personas. No se considera terminado el rediseño de todas las pantallas.

Los tokens del tema predeterminado son fondo #202124, tarjeta #2B2D30, elevada #34363A, borde #414347, texto #D6D6D6, secundario #A6A8AB, inactivo #74777B y selección #3D4044. Se eliminan los degradados de los fondos de proyectos e interiores. Los progresos Sprint utilizan grises, manteniendo fases, etiquetas, checks y semántica. Se conservan los colores funcionales existentes de error, prioridad y vínculos multimedia. No se modifican preferencias guardadas: el identificador del tema continúa siendo `Arachn0de`, y las apariencias alternativas siguen disponibles con sus paletas anteriores.

La barra de proyecto contiene exactamente Bloquear | Crear | Capas. Bloquear es texto, Crear conserva su acción contextual o tradicional anterior y Capas usa el PNG oficial con ajuste proporcional. La creación tradicional alternativa en contextos de planificación conserva acceso desde el menú «Nuevo elemento». Captura rápida desaparece únicamente de la barra inferior: sus rutas y entrada del menú lateral se conservan. Se mantienen insets y funciones de bloqueo, navegación, orden/filtros y creación. No se implementa el futuro formulario compacto.

Los PNG llegaron con nombres distintos de la descripción del pedido. Se utiliza su contenido real, sin alterar bytes, transparencia, proporción o colores:

- `/home/r0ybt/Descargas/logoprincipal.png` → `drawable-nodpi/bw90_brand_mark.png` (símbolo sin texto).
- `/home/r0ybt/Descargas/logocontexto.png` → `drawable-nodpi/bw90_brand_logo.png` (logotipo con ARACHN0DE).
- `/home/r0ybt/Descargas/capa.png` → `drawable-nodpi/bw90_layer.png`.
- `/home/r0ybt/Descargas/proyecto.png` → `drawable-nodpi/bw90_project.png` (archivo proporcionado, equivalente al recurso solicitado como proyecto(2).png).
- `/home/r0ybt/Descargas/ver_capas.png` → `drawable-nodpi/bw90_view_layers.png`.

Los efectos propios de las imágenes oficiales permanecen intactos; no se les añaden filtros, tintes, sombras o brillos. No se borran recursos anteriores, que pueden estar referenciados por pantallas pendientes. El logotipo completo se usa en el menú y el símbolo en identidad compacta; proyectos sin foto y capas utilizan sus PNG estructurales. Fotografías personales/de proyectos y archivos multimedia permanecen sin modificación.

Lucide: 28 iconos oficiales y una variante de estrella seleccionada con la misma geometría y relleno, en `res/drawable/lucide_*.xml`. Importados desde la revisión `70562c1ee1c4fdcf736fe97bc893fb8511927934`, con SVG convertidos a VectorDrawable y sin nueva dependencia. Se conserva licencia y procedencia en `third-party/lucide/LICENSE` y `SOURCE.md`. Las acciones sin icono apropiado mantienen texto. Los indicadores comunes muestran estados vacíos en gris visible y contenido en gris claro; favorito seleccionado conserva diferencia de forma (estrella rellena) y semántica. El botón de Tecnologías permanece visible también sin asignaciones y utiliza el mismo selector existente.

Catálogo Personas: se reutiliza su galería ya rectangular y `ContentScale.Fit`, adaptando columnas hasta tres cuando el ancho lo permite, separaciones compactas y opciones Lucide. Búsqueda, alias/categorías, edición, eliminación, selección y fotos se conservan. No se modifica el editor ni los almacenes de fotos.

Archivos Kotlin de este bloque:

- `ui/theme/BlackWhite90.kt`, `Appearance.kt`, `Color.kt`, `Theme.kt`.
- `ui/LucideIcons.kt`, `OfficialIcons.kt`, `NavigationChrome.kt`, `ActionComponents.kt`.
- `ui/ProjectsScreen.kt`, `ProjectComponents.kt`, `ProjectPhotoComponents.kt`, `ProjectScreen.kt`, `NodeComponents.kt`.
- `ui/CompactCardIconButton.kt`, `CompactCardIndicators.kt`, `ElementAccessUi.kt`, `TechnologyComponents.kt`, `PeopleScreen.kt`.
- Pruebas: `ui/theme/AppearancePreferencesTest.kt`, `ui/GlobalNavigationUiTest.kt`.
- Recursos PNG/vectores y `third-party/lucide`; este documento.

Pendiente: revisión visual y sustitución de iconografía secundaria dentro de otras herramientas y pantallas; revisión de apariencias alternativas, que conservan su identidad previa; prueba de lectura/tamaño táctil en teléfono real. Las pantallas restantes heredan los tokens comunes del tema predeterminado, pero no se afirma que se hayan rediseñado individualmente. Se detiene aquí el bloque autorizado de componentes y presentación, sin iniciar grandes reescrituras.

Comprobaciones de integridad: los cinco PNG copiados son idénticos byte a byte; hashes de todos los archivos Kotlin Gantt/renderer/UI/repositorio/anotaciones iguales antes y después. No hay cambios de esquema, migraciones, datos, IDs, relaciones, versión, firma o configuración de release. No se instala ni se utiliza ADB. No se genera APK oficial ni se ejecutan suites completas.

Resultado de validación: `:app:assembleDebug` satisfactorio y 11 casos dirigidos aprobados (7 paleta/preferencias/contraste/tamaños, 2 navegación/barra, 1 catálogo Personas y 1 acceso de Tecnologías). Se ajustó una aserción antigua de título mediano a 19, valor ya existente en Type.kt; no se cambió tipografía. Se comprobó Crear con el formulario anterior y su acción Cerrar, que conserva el borrador. La captura PixelCopy no está disponible en este entorno Robolectric y no se usa para declarar una revisión visual real; esa revisión queda pendiente en teléfono. Se repitieron solo los casos afectados. `git diff --check` satisfactorio. Chicharra emitida al finalizar.

## Bloque 1A — depuración de barra inferior

Intervención limitada a Bloquear / Crear / Capas de `ProjectScreen.kt`, siguiendo los fundamentos permanentes de `ARCHITECTURE.md`. En el estado inspeccionado la fila no tenía una superficie compartida propia: la zona inferior correspondía al fondo natural de la pantalla y a tres superficies próximas. Se explicita la transparencia del contenedor técnico y se añade una separación superior de 6 dp. No se cambia el fondo global ni la barra gestual de Android.

Los tres botones mantienen anchos iguales, altura mínima de 64 dp, separación de 8 dp y márgenes exteriores existentes (12 dp horizontales y 10 dp verticales). Se homogeneizan rellenos de 4 dp, esquinas de 8 dp y composición de icono sobre etiqueta. Bloquear y Crear incorporan Lucide lock/plus de 28 dp centrados en espacios de 40 dp; Capas conserva el PNG original de 40 dp con ajuste proporcional. Superficies y texto reutilizan los tokens actuales; no se añaden acentos, bordes ni elevaciones. El cambio de Bloquear se limita a su variante inferior `boxed`; su presentación en cabeceras se conserva.

La lista continúa ocupando el espacio restante antes de la fila inferior, sin superposición; los insets siguen siendo responsabilidad de `AppSafeArea`. Se conservan callbacks, destinos, habilitación y repositorios. No se modifican menús, tarjetas, modelos, datos, recursos originales ni configuración de release.

Archivos: `ui/ProjectScreen.kt`, `ui/OfficialIcons.kt`, los nuevos vectores `res/drawable/lucide_lock.xml` y `lucide_plus.xml`, la prueba dirigida `GlobalNavigationUiTest.projectHasOneHeaderAndKeepsThreeBottomActions` y este documento. Los vectores utilizan la misma revisión y licencia Lucide ya documentadas en `third-party/lucide/SOURCE.md`.

Validación: `:app:assembleDebug` satisfactorio y una prueba dirigida aprobada. La primera ejecución pasó la geometría, pero falló al cerrar Capas sin esperar a la aparición del diálogo; se corrigió la sincronización del fixture y se repitió únicamente el caso afectado, sin cambios de producción ni nueva compilación Debug. `git diff --check` satisfactorio. La prueba comprueba igualdad de dimensiones, separaciones, espacio reservado de lista, estabilidad tras gesto de desplazamiento y accesos existentes a Crear/Capas; produce una imagen de revisión mediante dibujo software en `app/build/reports/block1a-bottom-actions.png`. Imagen inspeccionada: botones separados, iconos y etiquetas alineados y fondo natural visible entre las superficies, sin franja adicional; Bloquear muestra su estado deshabilitado. Esta imagen no acredita comportamiento de barras gestuales en dispositivo real. No se ejecuta release, instalación, commit ni push ni se inicia el siguiente bloque. Referencia `1000070135.png` no localizada en el entorno; comparación con ella y comprobación táctil/insets en teléfono real pendientes.

## Bloque 1B — menú lateral, Buscar y Recurrencias

Alcance exclusivamente UI/UX, conforme a Thumb-Friendly Design y UX-ERG-02 de `ARCHITECTURE.md`. Las referencias adjuntas se identificaron por contenido: Buscar con campo y modos superiores, y Recurrencias en un diálogo centrado. Se conservan funciones, datos, navegación y Black & White 90.

- **Menú lateral:** Obligaciones incorpora Lucide receipt, de la misma revisión/licencia documentada en `third-party/lucide/SOURCE.md`. El recurso se utiliza directamente en este menú para no cambiar otros consumidores de Payments. Los iconos de destinos presentes se homogeneizan a 24 dp, incluido el PNG de Proyectos con ajuste proporcional. Se conservan filas Material, áreas táctiles, agrupaciones, orden, Proyectos principal y Metro independiente; no se añaden categorías ni destinos.
- **Buscar:** cabecera existente arriba; resultados desplazables ocupan el espacio restante; campo de consulta y cuatro modos quedan en una zona inferior fija, sin desplazar la pantalla entera. Opciones con anchos iguales, separaciones de 6 dp y área mínima de 48 dp. En teléfonos se distribuyen en dos columnas; solo un ancho útil de al menos 440 dp multiplicado por la escala de fuente permite cuatro columnas. Esto evita partir palabras a 360 dp y conserva el orden Buscar / Favoritos / Recientes / Frecuentes. Carga y vacío mantienen el espacio de resultados. `AppSafeArea` continúa consumiendo los insets del sistema y teclado una sola vez: el panel inferior sube con el teclado y el viewport de resultados se reduce. No se cambia búsqueda, filtrado, IDs, favoritos, historial ni callbacks de resultados.
- **Recurrencias:** únicamente el listado pasa de AlertDialog a ModalBottomSheet, con altura ajustada al contenido y límite del 75 % del alto disponible para su contenido, título, filas de al menos 48 dp, lista perezosa desplazable y Cerrar fuera de la lista. Se utilizan superficies/textos del tema y los insets propios del componente Material. Se conservan el orden por ID del flujo existente, estados Activa/Pausada/Finalizada, estado vacío, cierre, editor y sus operaciones; no se modifican los diálogos de edición ni reglas del repositorio.

Archivos: `ui/NavigationChrome.kt`, `ui/ElementAccessUi.kt`, `ui/RecurrenceUi.kt`, `res/drawable/lucide_receipt.xml`, `ui/NavigationErgonomicsUiTest.kt` y este documento. Se preservan cambios previos; hashes de `ProjectScreen.kt` y `OfficialIcons.kt` del Bloque 1A idénticos antes/después. Sin cambios de modelos, migraciones, almacenamiento, Metro, Gantt, tarjetas, seguridad ni menús contextuales.

Validación: siete casos dirigidos distintos aprobados entre dos ejecuciones: cuatro nuevos de ergonomía; navegación de Recurrencias desde Seguimiento; restauración de consulta/modo/desplazamiento y retorno al contexto original desde Buscar; pausa/reanudación/finalización de recurrencias con confirmación y conservación de nodos. Los casos nuevos verifican callbacks del menú y geometría del icono de Obligaciones, cuatro modos de búsqueda, apertura por ID, 60 resultados y controles estables al desplazar, insets inferiores de 24 dp y teclado simulado de 280 dp, ancho de 320 dp con fuente 1,8×, vacío y 40 reglas desplazables con sus estados, apertura/regreso del editor y registros sin modificaciones por la consulta.

Revisión de imágenes renderizadas mediante dibujo software: `app/build/reports/block1b-drawer.png`, `block1b-search.png`, `block1b-search-keyboard-insets.png`, `block1b-search-large-text.png`, `block1b-recurrences-empty.png` y `block1b-recurrences.png`. La revisión inicial detectó palabras partidas con cuatro columnas a 360 dp; se ajustó el umbral y se repitieron solo los dos casos de Buscar afectados. El caso de lista larga de recurrencias asumía orden por título: se corrigió el fixture para buscar mediante desplazamiento, conservando el orden por ID de producción, y se repitió ese caso. Debug satisfactorio; se actualizó tras el ajuste visual de Buscar. `git diff --check` satisfactorio.

Limitación: Robolectric verifica geometría, foco/entrada de texto y consumo de insets simulados; no reproduce un teclado físico ni acredita la ergonomía táctil o navegación gestual de un teléfono real. El fondo transparente del diálogo no compone la pantalla subyacente en la imagen software del sheet. No se declara una revisión física. No se ejecutan suites generales, instalación, APK oficial, release, commit ni push. El Bloque 1C queda sin iniciar y requiere autorización posterior.

## Bloque 1C — depuración de menús contextuales

Alcance localizado a los menús de tres puntos de tarjetas de Proyecto/Capa/Tarea/Nota y del contexto abierto en `ProjectScreen`. Se mantiene Black & White 90 mediante los tokens actuales y componentes Material; no se cambia la presentación de los menús de otras herramientas.

### Auditoría de equivalencia

| Acceso | Decisión y alternativa comprobada |
| --- | --- |
| Editar en el detalle ordinario de un nodo | Retirado: tocar el título abre `openNodeEditor` y el mismo `NodeDialog` completo, con título, descripción, propiedades y Guardar. El acceso se conserva en el contexto Kanban integrado, que no presenta ese título editable. |
| Editar en tarjetas y Proyecto | Conservado: tocar la tarjeta abre el elemento; no abre directamente su editor. El título del Proyecto en el detalle tampoco es editable. |
| Editar tecnologías en tarjetas de Tarea/Nota | Retirado: `TechnologyCardButton` permanece visible incluso sin asignaciones y abre el mismo `EditOwnerTechnologies`. |
| Editar tecnologías en Capas/Proyecto y encabezado contextual | Conservado: los indicadores ofrecen consulta y navegación de catálogo; no son un selector equivalente de asociaciones. El editor completo exige otras condiciones de carga y no sustituye ese acceso independiente. |
| Responsables | Conservado: indicadores y avatares consultan personas; no sustituyen `PersonAssignmentsDialog` y su administración de asignaciones. |
| Historial | Conservado entre acciones secundarias. |
| Subir/Bajar de Capas | Retirados únicamente cuando el arrastre manual está disponible como alternativa. Permanecen con filtros, orden automático sin arrastre, operaciones en curso, Sprint, teclado, exploración táctil y «Capas primero». Tareas y Notas conservan sus accesos y condiciones existentes. |

La comprobación de arrastre con «Capas primero» expuso un fallo preexistente de presentación: `drag.orderFor("layers", ...)` devuelve el mismo orden transitorio para las secciones pending/completed y provoca claves `node:<id>` duplicadas en LazyColumn durante el gesto. Se conservan Subir/Bajar en este contexto en vez de quitar el acceso seguro. No se modifica el algoritmo ni la persistencia del arrastre; su reparación queda pendiente para otro alcance.

### Organización final

`ContextActionMenu`, añadido junto a los componentes de acciones existentes, presenta estos menús mediante Modal Bottom Sheet. Solo sus tres puntos de uso adoptan el componente; `ActionMenu` y `ActionMenuItem` de otras pantallas permanecen intactos.

- Secundarias arriba: edición que siga siendo necesaria, tecnologías, Historial, fotografía, valores predeterminados y configuraciones.
- Organización a continuación: selección, responsables, conversiones, plantilla, compartir y operaciones Sprint existentes. Un divisor discreto diferencia los grupos cuando ambos tienen contenido, sin añadir submenús ni grupos vacíos.
- Prioritarias fijas abajo: Copiar / Mover y la variante Copiar con descendientes cuando corresponda. Se conservan etiquetas, parámetros false/true y condiciones de copia de los llamadores; Proyecto mantiene Convertir en capa como su operación jerárquica existente, sin inventar un Mover nuevo.
- Eliminar sigue visible en el pie, separado por un divisor de las operaciones prioritarias; Cancelar permanece accesible. Las confirmaciones existentes se conservan.

Filas compactas, iconos de 20 dp, áreas mínimas de 48 dp, esquinas de 8 dp y separaciones de 6 dp. Dos columnas cuando el ancho y la fuente lo permiten; una columna con texto grande o ancho reducido. No se rellenan cuadrículas con controles vacíos. El contenido se ajusta a su altura, con límite del 85 % del alto disponible para el contenido del sheet; solo las acciones secundarias/organizativas necesitan desplazamiento en listas extensas, conservando las prioritarias y el pie. Los insets y el cierre gestual utilizan el componente Material. Las nuevas geometrías Lucide utilizan la revisión/licencia existente y una asignación de iconos local a estos menús para no cambiar otras pantallas.

### Eliminar y Deshacer

La revisión localizada de `NodeActions` y `ProjectActions` encontró Deshacer para creación (`CreationUndo`), no un token de recuperación íntegra de eliminaciones. El borrado de nodo/proyecto sigue pasando por los mismos repositorios y confirmaciones; no se añade recuperación, Snackbar de eliminación ni una confirmación extra. Queda pendiente cualquier futuro Deshacer de borrado que garantice descendientes, relaciones y archivos antes de poder sustituir la confirmación segura.

Archivos de producción: `ui/ActionComponents.kt`, `ui/NodeComponents.kt`, `ui/ProjectComponents.kt` y `ui/ProjectScreen.kt` (solo condiciones y menú contextual, sin cambios en la barra inferior). Recursos nuevos: `lucide_copy`, `lucide_folder_input`, `lucide_trash`, `lucide_clock`, `lucide_pencil`, `lucide_image`, `lucide_share_2`, `lucide_bookmark`, `lucide_network`, `lucide_arrow_left_right` y `lucide_navigation`, en `res/drawable/*.xml`. Prueba nueva `ui/ContextMenuUiTest.kt`; la prueba existente de ordenación se conserva con sus accesos originales. Este documento registra auditoría y alcance.

Validación: seis casos dirigidos distintos aprobados entre las ejecuciones afectadas: cuatro de `ContextMenuUiTest` (Proyecto; Capa/Tarea/Nota con callbacks y variantes; texto grande con acciones prioritarias fijas; título editable y confirmación de borrado) y dos existentes de `NodeSortUiTest` (Subir seguro con Capas primero y arrastre manual). Se verifican copia false/true, estados deshabilitados, Mover/Eliminar, conservación de Responsables/Historial y accesos equivalentes; los modos táctil, teclado y exploración táctil se fijan explícitamente en el fixture. `git diff --check` satisfactorio. Las pruebas iniciales permitieron detectar la limitación de Capas primero; se conservó el acceso seguro y se verificaron de nuevo solo los casos afectados. La compilación del código final también comprobó las importaciones necesarias para unificar los iconos. Se realizó una sola ejecución de `:app:assembleDebug`; los ajustes posteriores se verifican mediante la compilación Kotlin requerida por las pruebas dirigidas, sin volver a generar Debug. No se realizan capturas automáticas, suites completas, instrumentación, instalaciones, APK oficial, release, commit ni push. Los hashes de los archivos del Bloque 1B y `OfficialIcons.kt` son idénticos; la sección completa de la barra inferior del Bloque 1A y los componentes anteriores de otros menús también son idénticos. Pendiente revisión táctil/visual en teléfono real; no se declara una revisión física. No se comienza otro bloque.

## Metro — limpieza visual mínima del seguimiento

Cambios exclusivamente de presentación en `metro/MetroScreen.kt`. En Recorrido, la duración acumulada, pausas, última posición repetida y resumen de tramos físicos/estaciones con detención/líneas pasan a las Opciones del viaje existentes. El resumen de ruta conserva origen, destino, estimación, estaciones, combinaciones y advertencias de servicio; las otras pantallas mantienen su resumen técnico anterior. En Seguimiento, los segundos acumulados de combinación pasan a Tiempos, junto a duración y pausas que ya estaban allí. La posición estimada conserva su texto y se explicitan próxima estación y destino; el cálculo y la etiqueta de tiempo hasta el siguiente hito se conservan, sin inventar otro estimador.

Los controles existentes de comenzar/reiniciar seguimiento, opciones y regreso mantienen una fila adaptable compacta. Modo Viaje, Deshacer llegada y Opciones del viaje se agrupan en otra fila adaptable. Los botones de inicio de etapa/combinación dejan de ocupar todo el ancho innecesariamente; se reducen separaciones y se reutilizan superficies grises, formas de 8 dp y controles Material. Pausar/Reanudar y Llegué conservan sus controles y superficies táctiles; Inicio sigue siendo el acceso superior existente, sin duplicarlo. No se cambian estados, condiciones de disponibilidad, callbacks, seguimiento, rutas, tiempos, estaciones, mapa, almacenamiento ni otras pantallas.

Archivos: `metro/MetroScreen.kt` y este documento. Validación: revisión del diff localizado y `git diff --check` satisfactorio; comparación de todos los callbacks onClick y de los bloques RouteTimeline, TrackingMap y ArrivalRecovery, idénticos antes/después. No hay cambios de interacción que requieran pruebas dirigidas. No se ejecutan pruebas, compilaciones, capturas automáticas, emulador, release, commit ni push. Pendiente revisión visual en teléfono; no se declara una comprobación física. La estimación existente sigue siendo hasta el siguiente hito de etapa y no se cambia a una estimación nueva al destino. Se detiene este alcance aquí.

## Capas de cebolla — actualización mínima de iconos

En `ui/LayerNavigator.kt`, compartido por el navegador jerárquico y los selectores de movimiento, se sustituye `cebolla_icon` por el PNG oficial `bw90_layer` y `iconohome` por `bw90_brand_mark`. Se conservan sus tamaños de 18/20 dp, contenedores, proporciones y ausencia de tintes sobre los originales. Las flechas de expansión/contracción pasan del icono Material al adaptador Lucide existente, conservando dimensiones, gris del tema, etiquetas y callbacks. No se modifican filas, destinos, selección, navegación ni comportamiento; no se crean ni alteran recursos gráficos.

Archivos: `ui/LayerNavigator.kt` y este documento. Validación: diff localizado revisado y `git diff --check` satisfactorio. Sin pruebas, compilaciones, capturas, APK, release, commit ni push. Se detiene el alcance aquí.

## Seguridad — presentación aislada de desbloqueo

`security/SecurityUi.kt`, únicamente en `UnlockScreen`: fondo negro carbón Black & White 90, logotipo oficial existente `bw90_brand_logo` sin tintes ni recursos nuevos, formulario centrado con ancho máximo de 420 dp, márgenes de 20 dp y separaciones de 12 dp. Botón de al menos 48 dp, esquinas de 8 dp y superficie gris sin elevación. La paleta Material se aplica localmente al contenido de desbloqueo: no se modifica el tema compartido ni `PasswordField`. Se retira el título textual duplicado del acceso configurado; configuración inicial mantiene instrucciones, confirmación y su acción original. No existe menú propio en `UnlockScreen` que deba trasladarse o retirarse.

Se conserva `InitialRecoveryAction`, junto con errores, progreso y estados de disponibilidad. El contenedor se centra cuando cabe y permite desplazamiento en alturas reducidas; los insets del teclado/sistema siguen consumiéndose en `AppSafeArea`, sin añadir padding duplicado. Se mantienen autenticación, borrado de secretos de memoria, contraseñas, cifrado, almacenamiento y navegación. No se intervienen componentes compartidos con efectos sobre otras pantallas.

Archivos: `security/SecurityUi.kt` y este documento. Validación: diff revisado y `git diff --check` satisfactorio; comparación de PasswordField y del contenido posterior a UnlockScreen, idénticos, y de la acción de autenticación, igual salvo indentación. No se ejecutan compilaciones, pruebas, capturas, APK, release, commit ni push. Pendiente revisión visual/táctil con teclado en dispositivo real. Se detiene el alcance aquí.

## Bloque T1 — alineación de tarjetas y tecnologías del Proyecto raíz

Las referencias se identificaron por contenido: interior de Capa con funciones y asignaciones separadas, Proyecto raíz con miniaturas de Android/Git/GitHub y tarjeta de Capa con flecha junto a Favorito. No se modificó el menú lateral global: «Menú lateral» es el título de la Capa mostrada en la captura.

- **Tarjetas:** `NodeCard` comparte una línea de alineación entre el centro de sus indicadores y el centro de los controles de Tecnologías/Favorito/Opciones. Esto evita centrar las acciones respecto del título y progreso completos, sin añadir otra fila ni cambiar estados o callbacks. Se mantiene el repertorio existente de indicadores vacíos/activos, grises y +N cuando falta ancho.
- **Proyecto raíz:** se ocultan exclusivamente sus miniaturas expandidas de tecnologías en `CompactAssignments`; se conserva el indicador cuadrado de Tecnologías ya existente en `CardIndicatorDetail`, su lista de asociaciones, consulta y acceso al catálogo. Si está vacío permanece visible como indicador inactivo, sin acción nueva. Las asociaciones y datos no cambian; las miniaturas de Capas se conservan.
- **Flecha:** indica entrada/navegación a la Capa. No tiene callback propio: el toque pertenece a `NodeCard.onOpen` (o a selección cuando está activa). Se conserva esa delegación y sus condiciones de aparición; se centra Lucide chevron-right de 18 dp en la misma superficie de 28 dp, esquinas de 6 dp y área de 48 dp que los controles vecinos. No se añade un botón ni una acción nueva.
- **Interior de Capa:** funciones y asignaciones usan una fila adaptable común, alineada al centro y con separaciones de 4 dp, sin forzar a ambos grupos a ocupar toda la anchura. Se mantienen los componentes de tecnologías y personas diferenciados, sus fotos, proporciones, accesos y +N. Si no caben, la composición se distribuye en varias líneas sin recortar imágenes.

Archivos: `ui/NodeComponents.kt`, `ui/CompactCardIndicators.kt`, `ui/TechnologyComponents.kt`, `ui/ProjectScreen.kt` y este documento. No se crean recursos ni se cambia navegación, almacenamiento, modelos o lógica de negocio. Los nuevos parámetros son exclusivamente de presentación y conservan sus valores anteriores por defecto para los otros usos.

Validación: diff localizado revisado; `git diff --check` satisfactorio. Una única `:app:compileDebugKotlin` satisfactoria (22 s), justificada por la nueva línea de alineación y parámetros de Compose; no se ejecutó assembleDebug ni se generó APK. Sin pruebas, suites, emulador, capturas automáticas, release, commit ni push. Pendiente revisión en teléfono del eje de iconos con títulos/progreso distintos, el ancho para +N, la distribución de asignaciones y texto grande. No se declara una revisión visual física. Se detiene este bloque aquí.

### Corrección T1 — retirada de la flecha redundante

Por instrucción posterior, se elimina la flecha junto a Favorito de `NodeCard`, incluido su contenedor de 48 dp: no queda espacio reservado. Se retiran también su parámetro visual `showNavigationArrow`, la referencia en `ProjectScreen.kt` y la importación específica del icono. Esta decisión sustituye la conservación/homogeneización registrada en T1. La apertura al tocar la tarjeta, la selección, Favorito y el menú de tres puntos mantienen exactamente sus callbacks y condiciones; no se modifica navegación.

Archivos: `ui/NodeComponents.kt`, `ui/ProjectScreen.kt` y este documento. Diff revisado y `git diff --check` satisfactorio. Sin pruebas, compilaciones, capturas, APK ni release. Se detiene la corrección aquí.

## Bloques T2 + T3 — correcciones localizadas de Tarjetas

Se utilizan las referencias por contenido: selector de Etiquetas en el editor, detalle de tarea con Vincular viaje debajo de Completar y Capa con una fila independiente de participantes. Las observaciones sobre iconos de Capas y barra inferior quedan fuera del alcance.

1. **Etiquetas — completada:** `TagSelector` utiliza ModalBottomSheet de Material, con superficie actual, altura ajustada al contenido (límite del 70 % disponible), búsqueda arriba, lista perezosa compacta y pie fijo para Quitar todas/Todas las etiquetas y Listo. Filas de 48 dp, selección destacada en gris y espaciados de 6 dp. Se conservan selección única/múltiple, búsqueda, creación y selección, retirada de asociaciones y sus callbacks. No se cambia `TagManager`, repositorio ni filtros.
2. **Títulos — completada:** los campos Título de nodo y Nombre de Proyecto de `EditDialogs.kt` son multilínea, de una a cinco líneas visibles; crecen para contenido largo y conservan foco, selección, estado de edición y validación original de caracteres. No se modifica el modelo ni guardado. Por encima del límite visual sigue disponible el desplazamiento vertical del campo, evitando la dependencia del desplazamiento horizontal.
3. **Vincular viaje — completada:** se retira el acceso de vinculación nuevo del detalle de una tarea sin viaje asociado. `TaskMetroAccess` solo se presenta cuando el snapshot existente contiene un vínculo histórico para ese nodo, conservando Vínculo Metro y sus acciones actuales. El módulo Metro, los viajes, relaciones y estados históricos permanecen intactos; no se borra ni desvincula información.
4. **Imágenes/portadas de Proyecto — pospuesta por el usuario:** la aclaración indica que se refería a eliminar la asignación de portadas a Proyectos, y pide no modificar ninguna función relacionada. No se intervienen selectores, portadas ni funciones de Personas.
5. **Participantes de Capa — completada:** se retira la presentación independiente de avatares de `CompactAssignments` para nodos estructurales. El acceso se concentra en el indicador de Personas de la fila superior, también en Equipo; su diálogo existente muestra la lista completa y conserva consulta, participantes y responsabilidades. `ParticipantCard` sigue utilizando `PersonPhoto` (imagen completa, ContentScale.Fit, sin máscara circular); no se cambia ninguna fotografía ni asociación. Las vistas especializadas de Sprint conservan su presentación existente. Tareas y otros usos de CompactAssignments mantienen sus valores anteriores por defecto.

Archivos de producción: `ui/TagUi.kt`, `ui/EditDialogs.kt`, `ui/ProjectScreen.kt` y `ui/TechnologyComponents.kt`. Pruebas dirigidas: `ui/TagsUiTest.kt` y `ui/ContextMenuUiTest.kt`. Este documento registra el resultado. Sin recursos nuevos, cambios de base de datos, modelos, reglas, navegación o edición de Metro, fotos o portadas.

Validación: diff localizado revisado y `git diff --check` satisfactorio. Cuatro casos dirigidos aprobados entre las ejecuciones: búsqueda/selección única entre 200 etiquetas; selección múltiple, retirada y Listo; indicador único de personas sin fila de avatares y apertura de los tres participantes; formulario multilínea con rechazo de 101 caracteres y guardado íntegro de un título de 100 caracteres con salto de línea. La lógica de creación de etiquetas y los callbacks de asociaciones se conservaron por revisión del diff. Un caso integrado de edición no alcanzó la apertura del editor en este entorno; se dejó la prueba original intacta y se verificó el formulario directamente, sin investigar navegación fuera del alcance. Se corrigieron solo las esperas/consultas de semántica de los fixtures afectados. Las pruebas compilaron el código necesario; no se ejecutó assembleDebug ni se generó APK. No se ejecutan suites completas, emulador, capturas automáticas, release, commit ni push. Pendiente revisión visual/táctil en teléfono con teclado y fuentes grandes. Se detiene el alcance aquí.

## Bloque T4 — PNG compacto y Guardar imagen

1. **PNG — completada:** el exportador ampliaba todas las imágenes a hasta 1016 px de ancho / 720 px de alto. Personas y Tecnologías ahora usan bloques de 160 × 160 px, ajustados proporcionalmente y sin máscaras ni recortes, con su nombre alineado al lado y 16 px entre bloques. Se mantienen ancho de exportación 1080 px, texto íntegro, metadatos, fotografías de Proyecto y adjuntos. Los archivos originales y tarjetas normales no se modifican. El generador existente exporta un único nodo; no se añade ni altera una modalidad de descendientes.
2. **Guardar imagen — completada:** opción junto a Compartir como imagen, con icono Lucide existente. ACTION_CREATE_DOCUMENT solicita un destino PNG con nombre neutral; cancelar no genera un archivo. Después se reutilizan exactamente CardShareSource/CardShareRenderer/CardShareFiles.generate y se copian los bytes mediante SecureFiles bajo el bloqueo común. No hay permisos generales de almacenamiento, preferencias, modelos persistidos, migraciones ni formatos nuevos. Se informan errores de selector, generación o escritura. El temporal de guardado se descarta mediante el diario de limpieza existente; compartir conserva su entrega anterior.

Archivos de este bloque: `report/CardShareRenderer.kt`, `report/CardShareSource.kt`, `report/CardShareFiles.kt`, `ui/NodeComponents.kt`, `ui/ActionComponents.kt`, `report/CardShareFilesTest.kt`, `ARCHITECTURE.md`, `BACKUP_FORMAT.md` y este documento. Se conservan los cambios previos del árbol de trabajo. Los documentos de arquitectura/backup registran que el PNG guardado es un derivado externo regenerable excluido del respaldo, sin nuevos datos privados.

Validación: revisión del código y diff localizado; `git diff --check` satisfactorio. Ocho pruebas dirigidas de CardShareFilesTest aprobadas (0 fallos): dimensiones reales de fotografías/logotipos de gran resolución y conservación de proporciones, copia exacta al destino SAF y fallo de escritura, compartir de solo lectura, fallos/cancelación/límite del render, conservación de originales, limpieza/reintento, caché cifrada y revocación por sesión, y capacidad de caché. Solo se compilaron las dependencias necesarias para estas pruebas; sin APK, assemble, suites completas, emulador, capturas ni release. El primer intento de Gradle no arrancó por la caché fuera del sandbox; la ejecución autorizada fue satisfactoria.

Pendiente comprobar en teléfono la composición del PNG y el selector de destino. Ante fallo se intenta eliminar el documento externo creado; los proveedores SAF pueden impedir esa limpieza o dejar un archivo incompleto si termina el proceso. No se promete atomicidad externa. Se detiene el bloque aquí.

## Bloque T5 — herramientas accesibles en notas largas

Implementado únicamente para notas en NodeDialog, excluyendo el formulario especializado de plantillas. Antes, el campo Descripción crecía sin límite y su fila de herramientas pertenecía al contenido desplazable del formulario. Con el teclado abierto, las herramientas existentes Negrita, Cursiva, Subrayado e Imagen se presentan en una fila fija sobre Cerrar/Cancelar y Crear/Guardar. Los botones mantienen los estados habilitados y condiciones originales (incluida selección de texto para formato). La fila usa superficies táctiles de al menos 48 dp y desplazamiento horizontal para anchos reducidos, sin duplicar herramientas ni añadir formatos.

El campo conserva BasicTextField, TextFieldValue, selección, composición del teclado, transformación de adjuntos y acciones existentes. Mientras el teclado está abierto, muestra hasta cinco líneas y desplaza internamente el texto; el formulario conserva su propio desplazamiento. Las herramientas están en el pie, fuera de ambos desplazamientos, y los insets IME existentes sitúan el pie sobre el teclado sin superponerlo al campo. Al cerrar el teclado, la misma instancia de herramientas regresa debajo del campo y se recupera la altura anterior de la descripción. movableContentOf conserva el estado del selector de imágenes y su scope al cambiar de ubicación. No se reinicia ni mueve el campo de texto.

Archivos: `ui/EditDialogs.kt`, `ui/AttachmentDescriptionEditor.kt`, `ui/AttachmentDescriptionField.kt` y este documento. Los componentes compartidos reciben parámetros opcionales con los valores anteriores por defecto; solo NodeDialog para Nota activa la distribución con teclado. Tareas, Capas, Proyectos y plantillas conservan la disposición anterior. No se modifica persistencia, modelos, formato de notas ni importación de archivos; se reutilizan los callbacks existentes.

Validación: revisión localizada del diff/código y de los puntos de llamada, y `git diff --check` satisfactorio. No se ejecutan pruebas que requieran compilación, compilaciones, APK, suites completas, emulador, capturas automáticas ni release, respetando la prohibición de compilar de este bloque. Pendiente validación táctil en teléfono: cursor/selección, alternancia del teclado, retorno del selector de imágenes y espacio visible en orientación horizontal o con fuentes grandes. No se declara validación de ejecución. Se detiene el bloque aquí.

## Bloque T6 — creación rápida sobre el teclado

Implementada la barra en el pie existente de ProjectScreen, sustituyendo temporalmente Bloquear/Crear/Capas mientras se crea una Tarea o Nota. La lista conserva su espacio y la pantalla actual; AppSafeArea ya consume los insets del teclado. Primera fila: título multilínea compacto y confirmación de 48 dp. Segunda: Nota, Vence, Repetir, acceso secundario Formulario completo y Cancelar. En anchos reducidos la segunda fila admite desplazamiento horizontal. Foco inicial y retorno al título al cerrar selectores; se oculta el teclado al guardar/cancelar o pasar al formulario, cuyo enfoque habitual lo recupera.

Se reutilizan EditorDraftStore, CreationDefaultsDraftFactory, EditorDraft y NodeActions.saveDraft. El destino sigue siendo parentId del mismo borrador aunque cambie la vista; el guardado usa la identidad estable y OperationState para impedir pulsaciones repetidas. Guardar correctamente limpia el borrador y cierra la barra; cancelar usa discardDraft, sin crear un nodo. Formulario completo recibe la misma instancia y todos sus campos. Los valores predeterminados siguen resolviéndose antes de abrir. Si Pago requiere datos, el campo indica completarlos en Formulario completo y no permite un guardado inválido. La creación de Capa, los flujos especializados de Gestión y el acceso tradicional Nuevo elemento no se modifican. Borradores complejos existentes vuelven a su formulario completo.

Vence reutiliza TaskDatesEditor/TaskDatePickerDraft, incluida la hora predeterminada, selección en dos etapas y Quitar Vencimiento. El estado de activación refleja el resultado del selector. Nota conserva título y opciones latentes al alternar tipo; Vence/Repetir quedan deshabilitados y la escritura normal de Nota aplica sus reglas actuales.

Repetir reutiliza RecurrenceFields y la creación de reglas existentes, sin tocar el motor. El selector secundario conserva frecuencias, intervalos, Comienza y fecha final inclusiva o sin fecha final. Si hay Vence y Comienza está vacío, se toma su día; si no, se solicita elegir Comienza y se bloquea confirmar/guardar una regla incompleta. El parámetro opcional initializeStart=false evita inicialización silenciosa en este flujo; los otros usos mantienen el valor anterior por defecto. No se añaden días laborales, selección de días, festivos ni límites por cantidad. No se modifican modelos, tablas, dependencias, formatos de backup ni datos históricos; los datos confirmados usan exactamente la persistencia/exportación existentes.

Archivos del bloque: `ui/QuickCreateBar.kt` (nuevo), `ui/ProjectScreen.kt`, `ui/RecurrenceUi.kt`, `ui/QuickCreateBarTest.kt` y `ui/QuickCreationFlowTest.kt` (pruebas nuevas), y este documento. Se conservan los cambios previos del árbol.

Validación final: diff localizado revisado y `git diff --check` satisfactorio. Cuatro casos distintos aprobados entre las ejecuciones dirigidas: título vacío/bloqueo de pulsaciones durante guardado; cambio Tarea/Nota conservando título y deshabilitando opciones; transferencia de la misma instancia con vencimiento/hora predeterminada al acceso de formulario y cancelación sin guardar; guardado real mediante NodeActions desde Proyecto y Capa, tanto Tarea como Nota, sin duplicados. La última ejecución dirigida (acceso al formulario y guardado real) terminó BUILD SUCCESSFUL. Se corrigió un icono sin mapeo que mostraba texto sustituyéndolo por Settings/Lucide existente.

La prueba visual del selector mensual no logró estabilizar Compose y produjo AppNotIdleException, incluso con el contenedor de insets real; no se considera aprobada ni se atribuye concluyentemente al entorno. Se interrumpieron las ejecuciones que seguían bloqueadas para evitar repetir los selectores. Permanecen las pruebas de selector como casos pendientes: quitar vencimiento, escoger anclaje explícito y configuración mensual con/sin fecha final. La revisión del código confirma reutilización de los selectores y motor, pero no sustituye esa validación de interacción. La ejecución inicialmente solicitada de RecurrenceRepositoryTest se interrumpió junto al caso visual; no se declara como aprobada.

Solo se compilaron dependencias de las pruebas dirigidas y los cambios que exigieron corregir/verificar esos casos; sin assemble, APK, suites completas, emulador, capturas ni release. Pendiente teléfono: posición sobre IME y navegación gestual, foco/selección al regresar de fecha/hora o repetición, selectores mensual/anclaje, restauración de borrador, orientación horizontal/fuentes grandes y acceso a toda la segunda fila en anchos estrechos. Las reglas futuras conservan el comportamiento actual: no aparece una tarea futura inmediatamente, sino al comprobar su vencimiento; el guardado de la regla sí cierra la barra. Se detiene T6 aquí, sin comenzar otro bloque.

## Corrección puntual T2 — comprobación de títulos largos al editar

Revisados los campos de edición reales: Nombre de ProjectDialog y Título de NodeDialog (compartido por Capa, Tarea y Nota). Ambos ya contienen singleLine=false, minLines=1, maxLines=5 y fillMaxWidth; no se encontró otro editor de títulos de estos tipos configurado en una sola línea. Los campos de Captura rápida, Creación de Gestión, Personas y otros formularios quedan fuera del encargo y no se modifican. No se añade un cambio de producción redundante ni se presenta la observación del teléfono como resuelta.

Se refuerza ContextMenuUiTest para comprobar ajuste automático con 100 caracteres sin espacios ni saltos de línea, ausencia de desbordamiento horizontal, conservación del límite y guardado íntegro. Se añade el caso equivalente de Proyecto con 60 caracteres. El renderizado LEGACY no sirvió para medir anchuras reales; con GraphicsMode.NATIVE, la prueba del editor compartido de nodos pasó. La prueba de Proyecto quedó bloqueada con AppNotIdleException en el diálogo y no se declara validada. No se investiga ni altera la arquitectura del diálogo, fuera del alcance puntual.

Archivos modificados en esta revisión: `ui/ContextMenuUiTest.kt` (pruebas) y este documento. Ningún archivo de producción, tarjeta, modelo o base de datos modificado. Diff/código revisados y `git diff --check` satisfactorio. Sin suites completas, APK ni emulador. Pendiente identificar el editor y la compilación que se ejecutan en teléfono, y contrastar el ajuste de Proyecto allí: el comportamiento de una sola línea informado no se reprodujo en el editor común del código actual. Se detiene esta revisión aquí.

## Personas — fotografías completas y comparación de bordes

En las pantallas de Personas, la vista del perfil en el editor sustituye el avatar circular por una fotografía completa en contenedor cuadrado redondeado, reutilizando PersonPhoto/ContentScale.Fit. La cabecera de detalle usa esquinas redondeadas y permite abrir el visor existente al tocar la foto principal, incluso sin fotos de referencia. El editor de encuadre de perfil recibe la forma cuadrada redondeada existente; conserva sus ajustes, confirmación y cancelación. No se cambia ni borra ningún encuadre almacenado ni se transforma el archivo original. La cuadrícula ya usa tarjetas rectas, foto superior completa y franja de nombre inferior y se conserva.

PersonPhotoComparison presenta opciones locales A (gris: Border/Inactive/Text de BlackWhite90) y B (Copper y dorado suave de la paleta HeavyGold existente, sin cambiar el tema). Bordes de 2 dp, con separación exterior de 4 dp, sin sombras ni brillo. Se aplica exclusivamente a la vista grande del perfil en el editor y al visor grande de fotos (principal o referencia). El visor conserva zoom, restablecer, navegación y establecer avatar. Empieza sin borde; tocar A/B alterna las variantes y volver a tocar la seleccionada la quita. Estado de comparación solo en rememberSaveable de esa vista, sin preferencias, Room ni backup nuevo. Ninguna variante es definitiva. No se añaden bordes decorativos a la cuadrícula ni a las miniaturas, ni se cambia el componente global de avatares de otras pantallas.

Archivos: `ui/PeopleScreen.kt`, `ui/PersonPhotoHeader.kt`, `ui/PersonPhotoViewer.kt`, `ui/PersonPhotoComparison.kt` (nuevo), `ui/PersonPhotoComparisonTest.kt` (nuevo) y este documento. Validación: revisión localizada del código/diff, `git diff --check` satisfactorio y una prueba dirigida aprobada (sin borde inicial, alternancia A/B, exclusión mutua y retirada reversible). Se compilaron únicamente las dependencias para la prueba, sin assemble/APK, release, suites completas, emulador ni capturas. La conservación de proporciones se revisa en el uso existente de ContentScale.Fit y el visor; sin escritura a originales. Pendiente comparación del usuario en teléfono, incluyendo fotos horizontales/verticales, pantalla pequeña y zoom. Se detiene aquí sin elegir variante final.

## Cierre — filtro del texto jerárquico y preparación oficial 0.4.1 / 14

Completado el filtro previo a Copiar texto con descendientes en menús de nodos y Proyectos: Todas por defecto o Solo pendientes, con Copiar/Cancelar. NodeCopyActions retiene la instantánea elegida sin publicar hasta confirmar, conserva bloqueo de operaciones y ejecuta el render fuera del hilo principal. Solo pendientes utiliza completeHierarchy con un filtro adicional que excluye exclusivamente tareas completables cuyo isCompleted real sea true; DONE/VALIDATED siguen la escritura de completado existente de Sprint. Se conservan contenedores, notas, orden jerárquico y títulos/descripciones multilínea, incluido el marcador de niveles profundos. No se usa el antiguo modo de resumen que quitaba notas. Mismo límite de 200.000 caracteres y rechazo completo; sin escrituras en Room ni originales.

Nueve pruebas dirigidas de FullHierarchyCopyTest aprobadas: contenido completo por defecto, completadas/notas/capas vacías, multilínea, metadatos, profundidad, límite, Proyectos, filtro con DONE/VALIDATED y exceso en modo filtrado. Se ajustan las referencias de etiqueta de ContextMenuUiTest y NodeSortUiTest y este último confirma Todas antes de leer el portapapeles; no se ejecutan suites adicionales. Revisión localizada y git diff --check satisfactorio.

Preparación oficial: ejecutado únicamente bash release-assets/preparar-apk-0.3.0.sh --check. Bloqueo explícito: «El código cambió después de las pruebas nativas; distribución bloqueada». No se actualiza ni fabrica la huella/evidencia. No se usa ADB, dispositivo ni instalación. No se alcanza la lectura de contraseñas y no se genera una APK nueva.

Existe la APK anterior Arachn0de-v0.4.1-code13-BlackWhite90.apk. apksigner verify y aapt confirman paquete com.r0ybt.arachn0de, 0.4.1 / 13 y certificado SHA-256 d0e8d7aa348a3b50ac380dd0586b608cc7425693be0433a8dad8df4125791437, coincidente con el oficial. No contiene este filtro nuevo. Se conserva y se prepara versionCode 14 (versionName 0.4.1), necesario para actualizarla. Configuración, helper y verificador oficiales se alinean al 14, añadiendo la APK 13 como referencia sin cambiar clave, applicationId ni controles. Salida prevista, no generada: /home/r0ybt/AndroidStudioProjects/Arachn0de/release-assets/Arachn0de-v0.4.1-code14-BlackWhite90.apk.

Archivos del cierre: export/PendingChatRenderer.kt; ui/state/NodeCopyActions.kt; ui/CopyTextChoiceDialog.kt (nuevo); ui/NodeComponents.kt, ProjectComponents.kt, ProjectScreen.kt, ProjectsScreen.kt y ActionComponents.kt; export/FullHierarchyCopyTest.kt; ui/ContextMenuUiTest.kt y NodeSortUiTest.kt; app/build.gradle.kts; release-assets/preparar-apk-0.3.0.sh y verificar-seguridad-release.py; RELEASING.md y este documento. bash -n del helper satisfactorio. El preflight 14 mantiene el mismo bloqueo por evidencia antigua.

Pendiente: validación nativa vigente mediante el protocolo oficial, cuya ejecución no se autoriza aquí por la prohibición de ADB; después el usuario ejecutará --check y el mismo helper sin argumentos desde su terminal, introduciendo personalmente las contraseñas mediante /dev/tty. La firma/verificación del artefacto nuevo y la compatibilidad con la versión exacta del teléfono no están acreditadas. Sin procedimiento alternativo, firma Debug, APK nueva, release publicado, commits ni push. Se detiene aquí por el bloqueo del protocolo.

### Continuación autorizada — validación nativa y espera de firma manual

El usuario autoriza ADB exclusivamente en emulador, con paquete aislado y datos sintéticos. Se creó /tmp/arachn0de-native-code14-4tnkh6ic, con el mismo código y recursos y únicamente applicationId securityvalidation; el fingerprint normalizado coincide con el checkout oficial antes y después de compilar. Se compilaron/instalaron solo securityvalidation y su APK de pruebas en emulator-5554. API 37, páginas de 16384 bytes; ningún teléfono conectado, ninguna instalación del paquete oficial.

Aprobados seis casos oficiales: nativePagesExportMainMigrationAndIndependentSecondary, nativeSqlcipherPreservesTypedRowsAndRejectsWrongKey, interruptedNativeMigrationRetainsSourceAndRetriesWithSamePassword; encryptedVersion26UpgradePreservesBothStoreIdentitiesAndNewPhotosBackups, genuineRoom36CascadeBackup29PreservesBothVaults y genuineRoom37Gantt30PreservesAndSeparatesVaults. Los cuatro logs registran OK (3 tests) y tres OK (1 test). Se renueva security-native-validation.json y los logs/hashes solo después de comprobar esos resultados y la identidad del checkout. Evidencia anterior conservada en release-assets/native-evidence-before-code14-20261012T234318Z/.

Preflight oficial --check aprobado: referencias oficiales locales códigos 5, 6, 7, 9, 10, 11, 12 y 13, mismo applicationId y certificado. Preparada versión 0.4.1 / 14; sin cambios adicionales de producción. Actualizados RELEASING.md y este documento. git diff --check satisfactorio. No se realiza firma, no se pide contraseña al usuario en chat ni se genera una APK oficial nueva. Se detiene exactamente antes del paso interactivo del helper habitual; el usuario debe ejecutarlo desde su propia terminal para introducir personalmente las contraseñas. La verificación de firma/ZIP/versión de la APK nueva será realizada por ese helper después de construirla. Sigue pendiente comprobar la instalación exacta del teléfono, sin solicitar ADB físico.
