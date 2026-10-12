# Bloque 14 — Adaptación del Kanban existente

## Arquitectura y comportamiento

El Kanban original es la vista vertical de una Capa con `sprintMode=true` en
`ProjectScreen.kt`. Sus cinco secciones son plegables y sus tarjetas admiten detalle,
edición, filtros, confirmación y avance. Esa entrada y esos gestos se conservan.

La gestión activa incorpora «Kanban» como proyección del mismo tablero. Reutiliza
`NodeCard`, `SprintSectionHeader`, `WorkStateDialog`, las cinco fases y
`sprintProgressPercentage`. `sprintSectionNodes` comparte la agrupación por ID con
la vista tradicional. No existe un tablero persistido ni un segundo motor de estados.

Los filtros de Proyecto y Backlog seleccionan tareas ACTION del contexto más
cercano; Iteración / Sprint une hijos directos y vínculos externos del Sprint elegido.
Todos deduplican por ID. Las fronteras de contextos anidados, incluso desactivados,
se conservan tras conversiones. En Sprints tradicionales con vínculos válidos de
otros contextos, la vista gestionada limita las tarjetas a su contexto y avisa que
el progreso oficial considera el Sprint completo; el detalle de la Capa conserva
la consulta completa. El filtro Proyecto representa el contexto lógico,
por lo que funciona también cuando su propietario es una Capa convertida.

Las tareas sin pertenencia Sprint aparecen en «Sin fase Sprint»; no se les inventa
una fase. Búsqueda por título/origen y filtros son estado de presentación, sin nuevas
preferencias. Las tarjetas muestran títulos completos y el estado de completado
separado de la fase. Abren el detalle original para editar y consultar asociaciones.
Las cabeceras, filtros y acciones tienen un mínimo táctil de 48 dp y la lista es lazy.
Se usan colores/tema actuales. El selector de fase compartido desplaza las opciones
y acota solo su título para que textos largos no oculten controles.

`KanbanRepository` observa los datos del Bloque 13. Para escribir valida gestión
activa y pertenencia al mismo contexto dentro de una transacción y delega en
`NodeRepository.setWorkState`: cambia únicamente la fase. El gesto de avance
invoca esa misma operación. El check conserva la confirmación oficial
`confirmWorkState`, incluyendo sus reglas anteriores de avance y completitud;
para tareas normales delega en `setCompleted`. No se simulan pulsaciones.

No hay controles de selección de alcance dentro del Kanban. «Planificación e
historial» abre el componente existente de iteraciones: allí se incorporan/retiran
referencias, se cierra y se reabre con sus confirmaciones actuales. Compromiso inicial
e instantáneas permanecen intactos al cambiar fases.

Una iteración cerrada conserva su restricción de selección, pero el motor Sprint
tradicional permite continuar cambiando fases. La vista muestra explícitamente
«Trabajo actual» y deriva al historial congelado; no modifica silenciosamente el
contrato del Bloque 13 ni presenta el trabajo posterior como resultado del cierre.

## Persistencia, seguridad y compatibilidad

Room **33** y JSON **25** permanecen iguales. No hay migraciones, tablas, columnas,
archivos privados, medios ni relaciones nuevos. Las fases escritas siguen siendo
`Node.workState`, respaldado y restaurado por el mecanismo actual. Lectura histórica,
SQLCipher y contenedores cifrados no cambian. El repositorio usa exclusivamente la
base de su sesión; no mantiene una caché global ni comparte estados entre bóvedas.
Se conservan los cambios anteriores del checkout.

## Archivos

Bajo `app/src/main/java/com/r0ybt/arachn0de/`:

- Nuevo `data/repository/KanbanRepository.kt`: proyección y escritura delegada.
- Nuevo `ui/ManagedKanbanUi.kt`: filtros y composición de los componentes existentes.
- `ui/SprintComponents.kt`: agrupación común y selector accesible con textos largos.
- `ui/ProjectScreen.kt`: utiliza la agrupación común en el Kanban tradicional.
- `ui/NodeComponents.kt`: opciones para tarjeta de consulta y título completo;
  los valores predeterminados conservan el comportamiento tradicional.
- `ui/ManagementSection.kt`, `data/repository/ManagementRepository.kt`: entrada local.

Pruebas nuevas bajo `app/src/test/java/com/r0ybt/arachn0de/`:
`data/KanbanTest.kt`, `data/KanbanVaultTest.kt`, `ui/KanbanUiTest.kt`.
Se adapta el fixture de `ui/SprintUiTest.kt` a composición explícita y gráficos
nativos para verificar la pantalla tradicional sin depender del ciclo de vida
de autenticación de MainActivity; se conservan sus cuatro pruebas de comportamiento.

## Validación

58 pruebas dirigidas aprobadas, sin fallos ni omisiones; 11 son nuevas del Bloque 14.
Se ejecutaron `KanbanTest`, `KanbanVaultTest`, `KanbanUiTest`, `SprintUiTest`,
`SprintLinksUiTest`, `SprintRepositoryTest`, `IterationTest`,
`SprintManagedConversionTest` y `SprintProgressTest`. Cubren la entrada tradicional,
proyecciones gestionadas, fases/checks, vínculos, compromiso y cierre, conversión,
reapertura, respaldo/restauración, bóvedas, vacíos, navegación y fuentes grandes.

`git diff --check` aprobado; también se comprobaron los seis archivos nuevos.
Compilación `:app:assembleDebug` aprobada. No se ejecutó la suite completa.

La prueba JVM de bóvedas utiliza sesiones reales y sustituye únicamente la factoría
SQLCipher nativa por SQLite/Room. Pendientes en teléfono: revisión visual de ambos
temas y colores personalizados, fuentes grandes, gestos y persistencia SQLCipher
nativa. No se accedió al teléfono ni se firmó oficialmente, instaló, hizo commit o push.
