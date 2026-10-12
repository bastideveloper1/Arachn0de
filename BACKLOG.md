# Bloque 11 — Épicas, historias y Backlog

## Modelo y uso

En «Gestión del proyecto» activa, «Backlog» permite incorporar trabajo existente con búsqueda por título y origen. No se recrean ni trasladan nodos. «Relaciones y roles» asigna a Capas normales los roles Épica o Historia y permite cambiarlos o retirarlos con confirmación. Los nombres, descripciones, medios, Personas, Tecnologías y demás propiedades siguen siendo los del nodo original.

Los vínculos explícitos son muchos a muchos: Objetivo ↔ Épica, Épica ↔ Historia e Historia ↔ Tarea. Se consultan en ambos sentidos; las historias y tareas también muestran sus objetivos indirectos. Las relaciones tipadas impiden ciclos: una épica no puede ocupar el extremo Historia y una Capa no puede ocupar el extremo Tarea. No se impone ninguna jerarquía física.

El contexto es el límite gestionado persistente más cercano en el árbol, aunque esté desactivado. Un contexto anidado permanece independiente del exterior. No se vincula trabajo de otros contextos ni de otras sesiones. Los IDs de contextos provienen de las raíces estables del Bloque 9; las referencias a nodos usan sus IDs existentes, nunca títulos, rutas ni posiciones.

Asignar Historia añade su entrada; vincular una tarea añade la suya si falta. Se pueden agregar tareas independientes y reincorporar historias ya registradas. Un nodo tiene una sola entrada aunque tenga varios vínculos. El orden manual del Backlog es independiente del árbol y de la prioridad general; no cambia fechas, completitud, porcentajes ni fases. «Solo pendientes» usa completitud actual: tarea no completada; historia con tareas vinculadas pendientes o todavía sin tareas. Las tareas relacionadas se cuentan por ID, sin repetirlas. Mostrar solo pendientes deshabilita el reordenamiento para evitar mover respecto de entradas ocultas.

«Abrir original» navega a la ficha actual del nodo. Las tarjetas y los selectores son listas virtualizadas, sin decodificar medios. La búsqueda se filtra fuera del hilo principal; la pertenencia se resuelve por recorrido iterativo con memoización, sin recursión sobre árboles profundos.

## Integridad y operaciones

- Desactivar gestión conserva roles, relaciones y orden; reactivar los recupera.
- Cambiar o retirar un rol con referencias exige confirmación explícita. Elimina solo sus vínculos de gestión y su entrada de historia; conserva nodo, descendientes y entradas de tareas.
- Desvincular historia/tarea conserva su ubicación, entrada de Backlog y vínculo Sprint. Retirar una entrada conserva roles y relaciones. Ambas operaciones se confirman en la UI y son reversibles mediante vinculación/incorporación.
- Eliminar un objetivo confirmado elimina sus vínculos con épicas además de sus criterios; la confirmación lo indica. No elimina Capas ni Tareas.
- Mover un nodo dentro del mismo contexto conserva referencias. Mover una raíz de contexto conserva su independencia y la de sus descendientes.
- Cambiar de tipo, eliminar o sacar del contexto un nodo referenciado se rechaza con explicación. Hay que retirar explícitamente vínculos, roles y entradas afectados; si la gestión está desactivada, reactivarla para hacerlo.
- Crear/activar un contexto que dividiría relaciones existentes se rechaza transaccionalmente.
- Conversiones Proyecto → Capa → Proyecto capturan y restituyen los cinco conjuntos junto con contextos y planificación, validando sobre el árbol final antes del commit. Anidamiento, orden, IDs y gestión desactivada se conservan cuando siguen siendo válidos. Colisiones de IDs físicos no fusionan contextos.
- Una Capa con rol no puede promoverse a Proyecto hasta retirar su rol: el destino deja de ser LAYER. También se rechaza promover un subárbol que separaría trabajo de su contexto. Un fallo o cancelación antes del commit revierte toda la operación.

Sprint conserva sus cinco fases y el único `Node.workState`. La fase VALIDADA y la completitud no validan criterios de objetivos. No hay otro estado operativo ni porcentajes guardados por Backlog.

## Persistencia y respaldo

Room **30→31**, migración aditiva transaccional, cinco tablas:

| Tabla | Identidad y contenido |
|---|---|
| `management_roles` | PK nodo; contexto y rol EPIC/STORY |
| `objective_epics` | PK objetivo/épica |
| `epic_stories` | PK épica/historia |
| `story_tasks` | PK historia/tarea |
| `backlog_entries` | PK nodo; contexto y posición manual |

FK en cascada e índices de referencia. Triggers y validación del repositorio comprueban tipos, pertenencia y propietarios. Las PK impiden entradas o vínculos duplicados. El validador de respaldo también exige posiciones no negativas y únicas por contexto, referencias válidas y el mismo límite de gestión para ambos extremos. No se copia el árbol ni se añaden archivos privados o preferencias globales.

JSON **23** incluye roles, los tres conjuntos de vínculos y entradas cuando existen, incluso desactivados. Se leen JSON **1–23**; históricos 1–22 producen listas vacías. Sin datos del bloque se conserva la elección anterior de formato. **Lectores antiguos rechazan JSON 23; no existe downgrade de Room 31.** Contenedor, cifrado y aislamiento no cambian.

Todos los registros entran en el snapshot y la restauración transaccionales. Se valida antes de reemplazar; roles preceden vínculos y entradas. Los límites existentes de registros y metadatos incluyen las filas nuevas. No hay bytes de medios nuevos ni exclusiones regenerables que añadir. Las selecciones aún sin confirmar son temporales y no forman parte del backup. Ver [BACKUP_FORMAT.md](BACKUP_FORMAT.md).

## Archivos del bloque

- Nuevos: `data/local/BacklogEntities.kt`, `data/repository/BacklogRepository.kt`, `ui/BacklogUi.kt`.
- Integración y guardas: `Arachn0deDatabase.kt`, `ManagementRepository.kt`, `ProjectNestingRepository.kt`, `NodeRepository.kt`, `ProjectRepository.kt`, `ManagementSection.kt`, `ProjectScreen.kt` y texto de confirmación en `PlanningUi.kt`.
- Respaldo: `BackupData.kt`, `BackupJson.kt`, `BackupRepository.kt`.
- Esquema generado: `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/31.json`.
- Pruebas nuevas: `BacklogTest`, `BacklogMigrationTest`, `BacklogUiTest`, `BacklogVaultTest`. Expectativas de versión actual/futura adaptadas en las pruebas de gestión, planificación y Sprint pertinentes.
- Documentación: `ARCHITECTURE.md`, `BACKUP_FORMAT.md` y este informe. Cambios anteriores conservados; sin modificación de versiones comerciales ni protocolo de release.

## Validación y revisión física

Las pruebas dirigidas nuevas verifican roles reversibles, confirmación de pérdidas, relaciones múltiples, duplicados/tipos/ciclos, Sprint y aceptación independientes, orden propio, movimiento/contextos desactivados, conversiones anidadas, rollback/cancelación, colisiones físicas, reapertura, eliminación protegida, JSON 22/23 y fallos de restauración. La resolución de contexto se prueba con un árbol de **30.000 nodos**. Las pruebas Compose verifican búsqueda, vinculación, apertura original, confirmaciones, orden y fuente al **180 %** con título histórico largo.

La prueba de sesiones comprueba recuperación desde respaldo protegido e independencia principal/señuelo con IDs físicos coincidentes. Sustituye únicamente SQLCipher nativo por SQLite en JVM, como las pruebas existentes; la envoltura cifrada, contextos de sesión y archivos de prueba son reales. La validación nativa en teléfono sigue pendiente.

Validación final: **113 casos dirigidos aprobados** (18 nuevos y 95 regresiones pertinentes), sin ejecutar la suite global. Incluye las migraciones API 24/28, planificación y respaldo históricos, gestión reversible, conversiones y motor Sprint.

`git diff --check` pasó, además de revisar espacios en los archivos nuevos. `:app:assembleDebug` terminó con **BUILD SUCCESSFUL**. La compilación usa únicamente la configuración Debug existente; no se preparó una APK oficial.

Revisar físicamente teclado/IME, fuentes y lector de pantalla del dispositivo, navegación Atrás y rendimiento con datos reales respaldados. No se accedió al teléfono, no se generó ni firmó APK oficial y no se ejecutó commit ni push. No se implementaron herramientas fuera del Bloque 11.
