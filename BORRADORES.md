# Borradores

Notas y tareas sin proyecto real, con sus adjuntos y audio. La sección vive junto
a Favoritos, muestra modificación reciente y permite crear, editar, mover y eliminar
con confirmación. Captura rápida permite Nota/Tarea y Guardar en Borradores sin destino.
El editor y el almacenamiento de archivos se comparten con los elementos organizados.

Mover conserva ID, metadatos, vínculos, historial y Favoritos. Solo admite como destino
un proyecto o una capa existente. Capas, grupos de creación, pertenencia a Sprint,
recurrencias y otras relaciones de contexto requieren organización previa: el rechazo
conserva el elemento y explica el motivo. Las grabaciones existentes se mueven con
su tarea; no se extrae un archivo de su propietario ni se convierte contenido.
No hay caducidad, limpieza automática, carpetas internas ni nuevos editores.

Los borradores quedan fuera de listados y resúmenes de proyectos, Atención, Calendario,
finanzas y limpieza de completados; Buscar/Favoritos permiten abrirlos por identidad.
Room 49 y JSON 42 admiten projectId nulo. Ver ARCHITECTURE.md y BACKUP_FORMAT.md.

## Validación

`compileDebugKotlin` correcto. La batería final de regresión dirigida ejecutó
**177 pruebas en 20 clases, sin fallos ni errores**: Borradores, Favoritos/Captura,
formato y repositorio de backup, bytes de adjuntos, audio, migración, invariantes,
reapertura, liberación de archivos, proyecciones de Atención/finanzas y exportación
textual. Tras el último ajuste de validación del botón de audio, se repitieron
las **22 pruebas de Borradores y acceso**, también sin fallos.
`git diff --check` correcto.

Las pruebas específicas nuevas cubren creación de nota/tarea sin proyecto,
idempotencia, ausencia de proyectos fantasma, edición/reapertura, orden por
modificación (incluido audio), movimiento de ida/vuelta y fallo/cancelación,
conservación de IDs/fechas/contenido/responsables/etiquetas/historial/Favoritos,
eliminación del antiguo proyecto, rechazo de ocurrencias, preferencias de orden,
backup cifrado con audio, PNG completo, corrupción/archivo ausente y compatibilidad
histórica. La migración compara todas las tablas y prueba rollback, CASCADE,
SET NULL y RESTRICT con compromisos/cierres. Las reservas compartidas se prueban
ante escritura rechazada, guardado, eliminación y reapertura. La prueba de UI
crea una nota desde Seguimiento→Borradores con el editor real, recrea su estado
y cancela movimiento y eliminación sin modificar el registro.

En exploraciones más amplias también aparecieron fallos en pruebas históricas:
BackupUiTest, CreationDefaultsBackupTest, ProjectConversionBackupTest,
RecurrenceDeletionTest, TechnologyBackupTest, CompletedCleanupUiTest,
CalendarSnapshotTest y los menús de NodeCopyUiTest/ProjectCopyUiTest. Incluyen
selectores anteriores a los grupos plegables del menú, fixtures/expectativas de
formatos previos y una aserción de cuadrícula de calendario. No se presenta la
suite completa como verde ni se han cambiado funcionalidades ajenas para satisfacer
esas expectativas. Los fallos de la prueba nueva y las aserciones de versión de
Room encontradas durante desarrollo sí se corrigieron y se volvieron a comprobar.
No se ejecuta assembleDebug/assembleRelease ni se genera una APK para este cambio.

Pendiente en teléfono: permisos y captura de micrófono real, pausas/interrupciones
del servicio, reinicio del sistema y cierre forzado durante grabación/movimiento,
recreación con teclado, ergonomía de menús y restauración cifrada en otra instalación.
Las pruebas JVM pueden comprobar Room, UI con Robolectric, transacciones, archivos
y respaldo, pero no sustituyen las condiciones reales del micrófono y del sistema.

## Archivos de esta intervención

- Modelo/consultas/migración: Node, NodeTreeSnapshot, NodeEntity, NodeDao,
  NodeInvariants, DraftsMigration (nuevo), Arachn0deDatabase y esquema Room 49 (nuevo).
- Escrituras e integridad: NodeRepository, ProjectNestingRepository,
  AccessRepository, ElementSearch, SharedCaptureQueue y CompletedCleanup.
- Proyecciones y exportación: AttentionSnapshot, FinancialSnapshot, CalendarSnapshot,
  ProjectCardAlerts, TaskPlainTextRenderer y CardShareSource.
- Backup: BackupData, BackupJson y BackupRepository. La prueba cifrada detectó un
  doble cierre del stream durable: se finaliza ahora la envoltura portable sin
  cerrar anticipadamente el stream propietario de fsync.
- Interfaz: DraftsScreen (nuevo, incluye adaptadores al editor/selector existentes),
  AppRoot, NavigationChrome, ElementAccessUi, EditDialogs, ProjectScreen,
  NodeComponents, AttentionScreen, CalendarScreen, NodeActions, NodeCopyActions y
  OperationState. AudioDraftStore actualiza la modificación al confirmar audio
  en un borrador, sin reescribir sus archivos.
- Pruebas nuevas: drafts/DraftsTest y drafts/DraftsMigrationTest.
  ElementAccessUiTest cubre captura sin destino y navegación/edición de borradores;
  KanbanUiTest explicita que su fixture sí tiene proyecto. Las aserciones de versión
  de base actual en pruebas históricas se actualizan a 49; no cambian sus datos
  históricos ni se eliminan comprobaciones.
- Documentación: ARCHITECTURE.md, BACKUP_FORMAT.md y este informe (nuevo).

Se conservan los cambios que ya existían en el árbol de trabajo. No se cambia
versionCode/versionName, firma, distribución ni configuración de compilación.

Los editores de Borradores reservan los archivos confirmados mientras conservan
entrada sin guardar. AttachmentRepository libera ahora las reservas del editor
que confirma, preservando las de los demás; AttachmentStore incluye AAC en los
nombres protegidos de esas reservas. Se prueba guardar, rechazar una escritura,
compartir archivos entre editores, eliminar su propietario y reabrir antes de
liberar la última reserva.

## Acceso desde la barra principal de Núcleos

La barra principal ofrece exactamente dos acciones horizontales del mismo ancho:
Crear núcleo y Crear borrador. Reutiliza TextButton, altura mínima de 48 dp,
iconos de 20 dp, tipografía labelSmall, separación de 8 dp y divisor de las acciones
inferiores existentes. Los textos pueden envolver al aumentar la fuente. Los
colores proceden de la paleta activa y se conserva la medición del pie para que
el listado no quede cubierto. AppSafeArea sigue consumiendo los insets de Android.

Crear núcleo conserva su editor y callback. Crear borrador llama al mismo
DraftElementEditor y al mismo EditorDraftStore de Borradores: selector Nota/Tarea,
ubicación nula y guardado existente, sin datos, preferencias ni migraciones nuevos.
Las barras de otros módulos no se modifican.

Validación de la barra: se comprueban el guardado de una nota sin proyecto aun
existiendo un Núcleo, el selector reutilizado, los anchos iguales, el área de toque,
la fuente a 1,8× en 280 dp, los textos dentro de sus botones, los colores de un tema
personalizado y una reserva inferior simulada de 32 px. El callback de Crear núcleo
es el mismo que antes de esta intervención. La interacción completa con su diálogo
anterior produjo AppNotIdleException en Robolectric, incluso aislándola sin insets
simulados; se conserva ese editor y queda esa interacción para validación en teléfono.

Resultado de esta intervención: compilación correcta y **20 pruebas dirigidas sin
fallos**, incluidas las nuevas de barra/tema y las regresiones de Borradores.
`git diff --check` correcto. Sin APK, commit ni push.
