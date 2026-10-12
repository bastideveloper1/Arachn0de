# Bloque 12 — Participantes y responsabilidades por contexto

Implementación localizada sobre los Bloques 9–11: una Persona existente participa
en varios contextos y conserva su identidad, nombre, avatar y encuadre originales.
La modalidad Normal y las asignaciones tradicionales `node_person` no cambian.

## Modelo y reglas

- `management_participants`: clave `(contextId, personId)`, referencias al contexto
  estable de conversión y a la Persona original. No crea descendientes ni asignaciones.
- `participant_roles`: clave `(contextId, personId, role)`. Roles opcionales y múltiples:
  Responsable del proyecto, Ejecutor, Supervisor y Validador. Sin permisos de acceso.
- `objective_responsibilities`: añade `objectiveId` a esa clave.
- `node_responsibilities`: añade `nodeId`; admite tareas ACTION y capas EPIC/STORY.

Los cuatro roles se pueden asignar al contexto o a un elemento explícito. Una
responsabilidad sobre trabajo no requiere que se haya asignado previamente el mismo
rol general. No hay herencia automática ni reparto a todas las tareas de una historia.
Un validador asignado no valida criterios ni identifica al autor real de una acción.
No se escriben estados, porcentajes, orden del Backlog ni datos Sprint.

Todas las operaciones relacionadas usan transacciones. PK, FK y triggers protegen
identidades, duplicados, roles y pertenencia. El alcance utiliza el contexto más
cercano del árbol, incluso cuando esté desactivado: no admite responsabilidades del
contexto exterior sobre una tarea de un contexto anidado.

Retirar una participación con roles o responsabilidades exige confirmación explícita,
con cantidad y explicación. Conserva Persona, objetivos, criterios y nodos. Borrar
la Persona permanentemente limpia sus relaciones mediante FK CASCADE dentro de la
transacción y conserva el ciclo de vida existente para avatares compartidos.
Desactivar gestión conserva todo; reactivar recupera la misma identidad.

## Conversiones y movimientos

La conversión captura participaciones, roles y responsabilidades antes de cambiar
el árbol, y los valida/restaura después de reconstruir contextos, objetivos y Backlog,
antes del commit. Conserva contextos independientes sin fusionarlos. Un movimiento,
cambio de tipo, retirada del rol EPIC/STORY o nueva frontera de contexto que invalide
responsabilidades se rechaza y revierte con explicación. Deben retirarse primero las
responsabilidades afectadas. La eliminación de objetivos ya confirmada avisa también
de la retirada de sus responsabilidades. El borrado/undo de nodos respeta los guardas.

## Persistencia y respaldo

Migración **Room 31→32**, aditiva, cuatro tablas nuevas; esquema exportado `32.json`.
**JSON 24** añade las cuatro colecciones al snapshot, restauración transaccional,
validación estricta y límites comunes. Lectura de JSON 1–23 aplica listas vacías.
Lectores antiguos rechazan JSON 24; no hay downgrade de Room. Se conserva la selección
de formatos anteriores cuando no hay datos nuevos de este bloque.

No se crean imágenes ni archivos privados nuevos: los bytes de las Personas originales
se recuperan por el mecanismo actual, con deduplicación, encuadre y staging durable.
No hay cambios de SQLCipher, ANBACK01, contenedor v2, claves o aislamiento de almacenes.

## Interfaz

Sección «Participantes y responsabilidades» dentro de Gestión del proyecto. Lista
buscable de Personas existentes, avatares compartidos, incorporación, roles múltiples,
trabajo por Persona, aviso «Sin responsabilidad asignada», retirada confirmada y
consulta de la ficha original (nombre y avatar, los datos personales existentes).
Los chips se distribuyen en filas adaptables y conservan altura táctil mínima 48 dp.
Las listas y diálogos son desplazables. Accesos desde objetivos, relaciones de
épicas/historias del Backlog y detalle original de tareas, solo en contexto activo.

## Archivos de este bloque

Rutas de producción relativas a `app/src/main/java/com/r0ybt/arachn0de/`:

- Nuevos: `data/local/ParticipationEntities.kt`,
  `data/repository/ParticipationRepository.kt`, `ui/ParticipationUi.kt`.
- Integración: `data/local/Arachn0deDatabase.kt`, `data/local/BacklogEntities.kt`,
  `data/repository/ManagementRepository.kt`, `BacklogRepository.kt`,
  `PlanningRepository.kt`, `ProjectNestingRepository.kt`.
- Interfaz: `ui/ManagementSection.kt`, `PlanningUi.kt`, `BacklogUi.kt`, `ProjectScreen.kt`.
- Respaldo: `backup/BackupData.kt`, `BackupJson.kt`, `BackupRepository.kt`.
- Esquema: `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/32.json`.

Pruebas nuevas: `data/ParticipationTest.kt`, `ParticipationMigrationTest.kt`,
`ParticipationVaultTest.kt`, `ui/ParticipationUiTest.kt`, bajo `app/src/test/java/com/r0ybt/arachn0de/`.
Se adaptan únicamente las expectativas de versión actual en las migraciones de
Management, Planning, Backlog, SprintLinks y Sprint, y la versión futura rechazada
en PlanningTest. Documentación: este informe, ARCHITECTURE.md y BACKUP_FORMAT.md.

## Validación

**134 casos distintos dirigidos aprobados**, de los cuales **21 son nuevos**:
15 de repositorio/respaldo, 2 ejecuciones de migración (SDK 24 y 28), 1 de
almacenes/backup cifrado y 3 de interfaz. La ejecución conjunta aprobó 133 casos;
la final específica aprobó los 21 del bloque, incluido el caso adicional de rollback.
Los 113 casos de regresión cubren Backlog, acta/objetivos, modalidad gestionada,
conversiones, vínculos Sprint, migraciones y formatos históricos de respaldo.

Se verifican referencias duplicadas/ausentes, roles incorrectos, pertenencia,
confirmación y cancelación de retirada, borrado de Persona, reinicio, desactivación,
reversibilidad, movimientos simples/selección, contextos anidados, rollback,
medios compartidos y encuadre, JSON 23/24 y restauración antes de alterar datos.
La interfaz prueba búsqueda/incorporación, roles, confirmación/cancelación,
accesos originales y objetivo con texto largo y fuente 1.6×.

`git diff --check` correcto, también revisados los nueve archivos nuevos.
`./gradlew :app:assembleDebug` final **correcto** (BUILD SUCCESSFUL).

La prueba JVM de almacenes usa VaultManager, sesiones y backup cifrado reales;
sustituye exclusivamente SQLCipher nativo por Room/SQLite para ejecutar en Robolectric.
Queda pendiente validar en teléfono la migración cifrada nativa, la actualización
con la firma habitual y la presentación visual con avatares reales/fuentes grandes.
No se accede al teléfono ni se genera o firma APK oficial; sin commit ni push.
