# Bloque 13 — Planificación de iteraciones y Sprints

La planificación reutiliza una Capa Sprint y el motor existente. No crea nodos,
tareas, estados de trabajo o porcentajes paralelos. Gestión del proyecto ofrece
«Iteraciones», con selección de Sprints existentes y acceso a su planificación.

## Modelo y uso

Room **32→33**, migración aditiva, cuatro tablas:

- `iteration_plans`: UUID, contexto estable, Sprint único, meta, fechas previstas
  opcionales, notas y PREPARED/ACTIVE/CLOSED. El nombre es el título del Node original.
- `iteration_cycles`: UUID, planificación, número de ciclo, inicio real y cierre.
- `iteration_commitments`: referencia a tarea/ciclo, inicial o añadida después,
  fecha de incorporación. Se conserva aunque la tarea salga del alcance.
- `iteration_closures`: alcance, fase y check al cierre por referencia del ciclo.

Inicio fija el compromiso con el conjunto actual. Las incorporaciones posteriores
no alteran esa referencia. El alcance actual procede de hijos ACTION directos más
vínculos externos del Bloque 8, sin duplicados. La planificación selecciona tareas
existentes del Backlog y muestra origen e historias/épicas, sin moverlas ni reordenar
árbol o Backlog. Otra pertenencia Sprint incompatible se explica y bloquea.

Las cinco fases y su ponderación 0/25/50/75/100 usan `sprintProgressPercentage`.
La completitud sigue siendo independiente: el resumen distingue fases terminadas,
fases previas y checks pendientes. La validación de objetivos/criterios no cambia.
Los responsables explícitos del Bloque 12 aparecen como asignaciones, sin atribuir
quién ejecutó acciones ni asignar nuevas Personas o roles.

El cierre requiere confirmación y verifica que el trabajo mostrado no haya cambiado.
Congela referencias, alcance, fases y checks sin completar trabajo, cambiar fases,
eliminar vínculos ni aceptar objetivos. La fase de una tarea retirada se guarda null:
no se atribuye una fase que pudiera haber obtenido posteriormente en otro Sprint.
El historial puede consultarse por ciclo; sus instantáneas no son estados operativos.
Reabrir exige confirmación, crea un nuevo ciclo y fija otro compromiso sobre el
alcance actual. Las fechas/meta/notas configuradas pertenecen a la planificación
actual; el historial de ciclos conserva compromiso y resultado, no copias de tareas.

## Integridad y compatibilidad

Los contextos anidados conservan su frontera, incluso desactivados. Desactivar no
elimina planes, ciclos ni referencias. Se rechazan movimientos/cambios de tipo
que saquen tareas históricas o Sprints de su contexto. No se puede desactivar el
modo Sprint de una capa planificada ni borrar nodos que perderían historial.
No se incorpora una función de borrado de historial en este bloque.

La ampliación de conversión autorizada prevalida el árbol y todas las relaciones.
Solo tras esa comprobación reconstruye en una transacción. Captura y restituye
vínculos, planificación e historial, conserva fases exactas y verifica antes del
commit. Sprint y tareas vinculadas deben moverse juntos y conservar el mismo
contexto gestionado. Referencias separadas, colisiones de identidad o fallos revierten
sin pérdida. Las conversiones tradicionales con vínculos siguen rechazadas; las
que no involucran vínculos ni planificación mantienen sus reglas anteriores.
También se evita crear una fase latente para un Sprint raíz inexistente al promover
una capa normal con tareas vinculadas.

JSON **25** incluye las cuatro tablas, validación estricta y restauración transaccional.
Lee JSON **1–24** con colecciones vacías, sin compromisos históricos inventados.
Los lectores antiguos rechazan JSON 25; Room 33 no incorpora downgrade.
Se conserva contenedor v2, ANBACK01, SQLCipher y aislamiento principal/señuelo.
No se añaden imágenes ni archivos privados; medios y staging usan el contrato existente.

## Archivos

Nuevos, relativos a `app/src/main/java/com/r0ybt/arachn0de/`:
`data/local/IterationEntities.kt`, `data/repository/IterationRepository.kt`,
`ui/IterationUi.kt`. Esquema: `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/33.json`.

Integración: `data/local/Arachn0deDatabase.kt`,
`data/repository/ManagementRepository.kt`, `BacklogRepository.kt`,
`NodeRepository.kt`, `SprintLinkRepository.kt`, `ProjectNestingRepository.kt`,
`ui/ManagementSection.kt`, `backup/BackupData.kt`, `BackupJson.kt`, `BackupRepository.kt`.
Documentación: ARCHITECTURE.md, BACKUP_FORMAT.md y este informe.

Pruebas nuevas bajo `app/src/test/java/com/r0ybt/arachn0de/`:
`data/SprintManagedConversionTest.kt`, `IterationTest.kt`, `IterationMigrationTest.kt`,
`IterationVaultTest.kt`, `ui/IterationUiTest.kt`.
Se adaptan expectativas de versión actual en migraciones Management, Planning,
Backlog, SprintLinks, Sprint y Participation; la versión futura en PlanningTest;
y la expectativa de conversión gestionada en ManagementTest, ahora comprobando
ida/vuelta con árbol, vínculos y fases intactos según el contrato autorizado.

## Validación y límites

159 pruebas dirigidas aprobadas, sin fallos ni omisiones; 25 son nuevas del
Bloque 13. Incluyen conversiones, colisiones y reversión, compromiso y ciclos,
persistencia, migración, respaldo histórico/nuevo, restauración, aislamiento y UI.
Tras el último ajuste de prevalidación se repitieron las 56 pruebas pertinentes
de conversión y planificación: todas aprobadas.

`git diff --check` aprobado, también comprobados los diez archivos nuevos.
Compilación `:app:assembleDebug` aprobada. No se ejecutó la suite completa.

Las tareas directas pertenecen por su padre físico. Aquí no se ofrece retirarlas
sin moverlas: se muestra la explicación y se conservan. Para las externas se aplica
la confirmación exacta de descarte de fase del Bloque 8, conservando referencias
históricas. El cierre es de planificación; no desactiva el motor Sprint tradicional.
Cambios posteriores del trabajo no reescriben el resultado cerrado.

La prueba JVM sustituye exclusivamente el SQLCipher nativo por Room/SQLite; usa
VaultManager, sesiones y backup cifrado reales. Pendientes en teléfono: migración
SQLCipher nativa, restauración real y revisión visual con fuentes grandes/temas.
No hubo acceso al teléfono, firma oficial, commit, push ni cambios de versión de app.
