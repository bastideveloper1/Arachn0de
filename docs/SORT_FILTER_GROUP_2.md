# Grupo 2 — Ordenar y filtrar

Actualización posterior: la tarea 3 fue autorizada con filtros individuales,
retirando el compuesto Atención y ambos controles del calendario. La implementación
y sus comprobaciones se documentan en `CALENDAR_INDIVIDUAL_FILTERS.md`; el apartado
3 de este informe conserva el diagnóstico y la recomendación de la etapa anterior.

## 1. Favoritos arriba / abajo

No estaba implementado. `SortConfiguration` solo contenía criterios jerárquicos,
modo manual, herencia y Capas primero. La lista general de Núcleos ofrecía un
panel de filtros, sin preferencia de ordenación.

Se añade una elección independiente: Sin preferencia, Favoritos arriba y
Favoritos abajo. Reutiliza el panel y el selector existentes. En las listas de
Capas/Tareas/Notas se guarda al elegir y se aplica con Aplicar, igual que los
otros ajustes de ese ordenamiento. No desactiva Manual ni los demás criterios.
Se conserva la separación Disponible/Completada, los fijados y Capas primero;
Favoritos agrupa dentro de esas restricciones. Los criterios anteriores siguen
ordenando dentro de cada grupo y los empates conservan posición/creación/ID.
Capas primero mantiene su excepción previa: las Capas conservan su orden manual
entre sí, salvo la nueva agrupación explícita de Favoritos.

En Núcleos se reutiliza el panel inferior, que ahora reúne ordenar y filtrar.
La preferencia agrupa la lista normal y los resultados de búsqueda de Núcleos,
conservando la separación de fijados y el orden estable entre iguales. Es una
proyección, no reescribe posiciones de Núcleos. Los filtros del calendario y
sus resultados conservan sus reglas anteriores.

### Persistencia y backup

Misma tabla `node_sort_preferences` y mismo codec `SORT1:`. El campo opcional
`favorites` vale NONE/FIRST/LAST; por defecto y en datos históricos es NONE.
El contexto global `@projects` usa la misma infraestructura de guardado,
observación y respaldo, sin propietario artificial ni relaciones nuevas.
Las configuraciones de cada Capa siguen heredando las del Núcleo cuando procede.
No se alteran al leer las filas antiguas. No hay archivos nuevos, ni cambios de
Room, ni migraciones ni cambios de versión de aplicación.

JSON 59 es necesario para que un lector antiguo rechace explícitamente los
nuevos ajustes en vez de ignorarlos o fallar interpretando un contexto desconocido.
Se conserva la lectura de respaldos anteriores y el contenedor/cifrado. El
contrato de persistencia y backup se revisó antes de intervenir.

Estado: implementada. Pendiente revisión física del selector y arrastre en
listas reales con la agrupación activa.

## 2. Presentación de Capas primero

Los demás criterios también utilizan casillas, pero dentro de una tarjeta con
borde, título, estado/dirección y fila táctil. Capas primero era una casilla
aislada junto a Manual.

Ahora utiliza la tarjeta compartida visualmente: borde según activación, título
y estado Activado/Desactivado, sin casilla dibujada. La fila es un interruptor
accesible. Continúa modificando exclusivamente `layersFirst`, sin activar o
desactivar reglas ni modificar Manual. Se mantienen los cambios de propiedad
local/herencia que ya efectuaba ese control, el guardado y la aplicación previa.

Estado: implementada. Pendiente apreciación visual/táctil en Android.

## 3. Evaluación de Favoritos y Atención del calendario

Evaluación actualizada con el principio de producto: el calendario debe contener
funciones de calendario; los filtros de tarjetas deben estar en Filtrar.
No se han modificado sus controles ni su comportamiento.

### Diagnóstico concreto

- `ContextCalendar.kt`: la estrella modifica `ContextCalendarState.favorites`;
  la bandera modifica `attention`. La descripción accesible real es Atención.
  `filtering` combina esos flags con la fecha seleccionada. Plegar o navegar por
  meses no los limpia. El Saver del contexto los conserva al restaurar la vista.
- `ContextCalendarIndex.results`: aplica fecha, Atención y Favoritos mediante
  intersección. Favoritos requiere marcar el propio nodo; no hereda el favorito
  del padre. Con fecha seleccionada, los candidatos son las tareas de ese día:
  las Capas y Notas favoritas dejan de aparecer en ese resultado.
- `AttentionPolicy.reasons`: Atención incluye tareas pendientes vencidas, para
  hoy, próximas o de prioridad alta. No equivale a `priority == HIGH`. No incluye
  Notas, Capas ni tareas completadas. La prioridad propia de un Núcleo tampoco
  lo convierte en un resultado de Atención, que trabaja sobre tareas.
- `ScopeFiltersDialog`: Filtrar dentro del Núcleo ofrece Tiempo, Persona,
  Estado, Prioridad y Etiqueta. No tiene Favoritos ni un equivalente exacto de
  Atención. `CalendarFiltersDialog` tampoco aporta ese equivalente.
- `DashboardSortAccess`: el panel de la lista general incrusta el mismo
  `ContextCalendar`, con el mismo estado. Es una segunda ubicación visual del
  mismo control, no un filtro independiente que sobreviva si se retiran los
  botones del componente compartido.
- `ProjectScreen`: los resultados del calendario se intersectan también con
  filtros de la vista y estado de las tareas. Limpiar los filtros de
  `ScopeFilters` no limpia por sí mismo `attention` ni `favorites`. En modo
  plegado esos controles desaparecen visualmente pero sus filtros siguen activos.
  Esto dificulta reconocer qué está restringiendo la lista.
- Ordenar favoritos no duplica Filtrar favoritos: uno conserva las tarjetas y
  cambia su posición; el otro excluye las no favoritas.

### ¿Pueden retirarse sin perder capacidad?

Sí, trasladando primero los controles al panel Filtrar y reutilizando exactamente
los flags, alcance contextual, intersecciones y callbacks actuales. No hay una
dependencia con el cálculo de fechas o la navegación mensual que obligue a
mantenerlos arriba. Que su estado viva hoy en `ContextCalendarState` es una
dependencia de implementación, no una necesidad visual.

Ocultarlos únicamente dejaría filtros activos sin acceso equivalente en varias
vistas. Borrar sus flags al retirarlos alteraría los resultados y perdería la
selección restaurada. Sustituir Atención por Prioridad alta también cambiaría
los resultados. Ninguna de esas opciones conserva la funcionalidad exacta.

### Recomendación para autorizar por separado

Retirar ambos del calendario **después de trasladarlos a Filtrar**:

1. Ofrecer Favoritos y Atención en el panel de filtros de Núcleos y del interior,
   con el patrón de selección ya existente. Mantener Atención como filtro de
   vencimientos/prioridad; llamarlo solo Urgente sería ambiguo con la propiedad
   de prioridad de las tarjetas. Si se quiere únicamente prioridad alta, debe
   acordarse como un cambio de semántica separado.
2. Reutilizar inicialmente el estado actual, incluidos sus Savers. Cada nueva
   entrada debe reflejar filtros ya activos y permitir desactivarlos sin borrar
   fecha, mes, persona, etiqueta, estado, búsqueda ni navegación.
3. Mantener sin cambios `ContextCalendarIndex`, `AttentionPolicy` y la selección
   contextual de resultados. No crear otro motor ni otra lista de resultados.
4. Definir Limpiar para que no queden flags activos ocultos. Comprobar tanto el
   borrado individual como la limpieza del panel y la vuelta a la vista normal.

Pruebas propuestas para esa intervención futura: igualdad de IDs antes/después
del traslado; entrada con flags activos/restaurados; combinaciones con fecha,
persona, estado y etiqueta; plegado, cambio de mes y regreso; limpieza sin tocar
orden ni navegación. No se implementa ni se prueban controles que aún no existen.

Estado: diagnóstico entregado y cambio de ubicación pendiente de autorización.

## Archivos y comprobaciones de esta intervención

Producción:

- `domain/model/HierarchicalSort.kt`: preferencia, codec y comparación.
- `data/repository/NodeRepository.kt`: usa Favoritos confirmados al aplicar orden.
- `data/repository/NodeSortPreferenceRepository.kt`: contexto global en la misma tabla.
- `backup/BackupData.kt` y `backup/BackupJson.kt`: validación y compatibilidad JSON 59.
- `ui/NodeSortMenu.kt`: selector Favoritos y presentación de Capas primero.
- `ui/ProjectsScreen.kt` y `ui/AppRoot.kt`: integración de orden de Núcleos.
- `ARCHITECTURE.md` y `BACKUP_FORMAT.md`: contrato del ajuste persistente.

Pruebas focalizadas (Robolectric/JVM; sin dispositivo):

- `HierarchicalSortTest.favoritesGroupStablyAroundOtherCriteriaWithoutChangingStructuralRules`:
  arriba/abajo/sin preferencia, Manual/Nombre, empates, Capas/Notas/Tareas,
  completadas, fijados y Capas primero.
- `HierarchicalSortTest.legacyModesMapToEquivalentCriteriaAndAllDirectionsRoundTrip`:
  compatibilidad del codec anterior y sus criterios/direcciones.
- `HierarchicalSortRepositoryTest.favoritePreferenceAppliesWithinSiblingsAndBackupRestoresOrRollsBack`:
  aplicación por hermanos, configuración global, round trip JSON, restauración
  en base vacía, preservación de nodos y Favoritos, rechazo de corrupción sin
  reemplazo y lectura de payloads históricos de siete/ocho criterios.
- `HierarchicalSortPanelTest.favoriteDirectionsAndLayerPresentationSurviveReopeningIndependently`:
  los tres estados de Favoritos, Capas primero y criterios independientes,
  reapertura y Aplicar accesible.
- `DashboardSortPanelTest.dashboardFavoriteOrderDoesNotChangeCalendarFilters`:
  el orden sobrevive a reapertura y limpiar filtros no lo borra; elegir orden no
  cambia fecha ni flags del calendario.
- `FavoriteSortDashboardTest.rootCardsGroupFavoritesStablyAndPreservePinnedAndStoredPositions`:
  tarjetas reales de Núcleos, ambos sentidos, sin preferencia, cambios de
  favoritos, fijados y posiciones originales conservadas.
- `ContextCalendarIndexTest.contextualFiltersIntersectWithoutDatesOrWritesAndReuseAttention`:
  evidencia de las intersecciones actuales, contexto jerárquico y ausencia de escrituras.

Las siete comprobaciones distintas pasaron en ejecuciones focalizadas. Se
repitieron únicamente las afectadas por los ajustes finales de contexto global
y restauración vacía. No se ejecutaron suites completas ni se generó APK.
