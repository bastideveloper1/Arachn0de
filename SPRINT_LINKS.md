# Bloque 8 — Tareas vinculadas a Sprints

Implementado sin mover ni duplicar tareas. El padre físico, ID, contenido y asociaciones se conservan. El selector permite búsqueda por título/origen y selección múltiple; la tarjeta vinculada abre el detalle original.

## Modelo y seguridad de operaciones

- Room 28 añade `sprint_links(taskId, sprintId)` mediante migración aditiva 27→28, con claves foráneas, tarea única e invariantes SQLite.
- Una tarea ACTION pertenece como máximo a un Sprint, directo o externo, del mismo Proyecto. Todos los nodos `sprintMode=true` se consideran activos hasta disponer de un ciclo formal de apertura/cierre.
- Las dos ubicaciones usan únicamente `Node.workState`, con las cinco fases y ponderaciones anteriores. Completitud independiente. Progreso calculado por IDs únicos de tareas directas y vinculadas; 25/50/100 produce 58 % según la regla existente.
- Desvincular exige confirmar la fase concreta que se descartará. Se verifica nuevamente dentro de la transacción; una fase cambiada requiere otra confirmación. Cancelar conserva todo. La tarea, ubicación y completitud nunca se borran al desvincular. Volver a vincular inicializa No planificada o Terminada según completitud.
- Mover dentro del Proyecto conserva la fase; mover al propio Sprint sustituye la relación externa por pertenencia directa. Se rechazan otros Sprints incompatibles, conversiones con vínculos, desactivación y eliminación de Sprints con tareas externas supervivientes. Borrar intencionadamente la tarea o todo el Proyecto limpia las relaciones transaccionalmente.
- Los repositorios siguen vinculados a su sesión y base. No se modifica SQLCipher ni el aislamiento de almacenes. No hay archivos privados nuevos ni cambios de versión comercial.

## Respaldo

JSON 20 incluye `sprintLinks` cuando hay vínculos. Sin vínculos se conserva la selección 17/18/19. El lector mantiene compatibilidad 1–20, validación previa y restauración transaccional; fallos conservan los datos anteriores. Versiones antiguas no pueden restaurar JSON 20. El contenedor y cifrado se conservan. Detalles en [BACKUP_FORMAT.md](BACKUP_FORMAT.md).

## Archivos de este bloque

Bajo `app/src/main/java/com/r0ybt/arachn0de/`:

- `data/local/SprintLinkEntity.kt`, `Arachn0deDatabase.kt`.
- `data/repository/SprintLinkRepository.kt`, `NodeRepository.kt`, `ProjectRepository.kt`, `ProjectNestingRepository.kt`.
- `domain/model/WorkState.kt` (documentación de elegibilidad).
- `backup/BackupData.kt`, `BackupJson.kt`, `BackupRepository.kt`.
- `ui/SprintLinksDialog.kt`, `ProjectScreen.kt`, `state/SprintProgress.kt`, `state/OperationState.kt`.

También: esquema Room `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/28.json`; pruebas `data/SprintLinksTest.kt`, `data/SprintLinksMigrationTest.kt`, `data/SprintMigrationTest.kt`, `ui/SprintLinksUiTest.kt`; documentación `ARCHITECTURE.md`, `BACKUP_FORMAT.md` y este informe. Se conservaron los cambios anteriores del workspace.

## Validación

59 casos aprobados, sin fallos ni omisiones: `SprintLinksTest`, `SprintLinksMigrationTest` (SDK 24/28), `SprintLinksUiTest`, `SprintRepositoryTest`, `SprintBackupTest`, `SprintProgressTest`, `SprintMigrationTest`, `ProjectNestingTest`.

Cobertura: identidad/padre y asociaciones, exclusividad y selección atómica, deduplicación/progreso, fase y completitud, reapertura de base, sesiones separadas, migración histórica sin reescritura, JSON antiguo/nuevo, snapshot, restauración inválida y rollback con fallo inyectado, movimientos, conversiones, desactivación y borrados. Interfaz: búsqueda/origen, vinculación, detalle real, actualización de progreso y cancelación/confirmación de desvinculación. La prueba de sesiones emplea fixtures Room; no constituye una nueva acreditación de criptografía nativa.

`git diff --check` limpio. `:app:assembleDebug` exitoso; APK en `app/build/outputs/apk/debug/app-debug.apk`. Sin firma oficial, commit ni push.

Pendiente: revisión en teléfono de teclado, fuentes grandes, títulos largos, listas extensas y comodidad táctil. No se afirma validación física ni instrumentada.
