# Calendario: filtros individuales de tarjetas

## Condiciones confirmadas de Atención

`AttentionPolicy.reasons` solo admite tareas hoja con Núcleo y no completadas.
Por verificar sigue siendo no completada y puede cumplir las condiciones.
Emite como máximo una condición temporal, más prioridad alta si corresponde:

1. `OVERDUE`: vencida según `TaskTemporal` (ahora es posterior al vencimiento).
2. `DUE_TODAY`: vence en el día local actual, no está programada para empezar
   más adelante y todavía no ha entrado en la condición anterior.
3. `UPCOMING`: vencimiento dentro de las próximas 24 horas, cuando no corresponde
   vencida ni vencimiento hoy. Una tarea aún programada no entra en esta condición.
4. `HIGH_PRIORITY`: prioridad efectiva alta, independientemente de las fechas.

`MEDIUM_PRIORITY` existe en el enum histórico, pero la política no lo emite.
Tener solo fecha de inicio tampoco activa Atención por sí mismo. No se añade un
filtro ficticio para condiciones que esta función no utilizaba.

## Qué existía y qué se añadió

Filtrar dentro del Núcleo ya tenía Tiempo, Persona, Estado, Prioridad y Etiqueta.
Tiempo/Hoy no equivale a vencidas, y Prioridad alta también puede consultar
tareas completadas: no equivalía exactamente a la condición pendiente de Atención.
Favoritos y el compuesto Atención solo tenían controles en el calendario; el
panel general de Núcleos incrustaba ese mismo componente.

Ahora Filtrar reutiliza `ScopeFiltersDialog` para ofrecer, tanto en el panel
general como en el interior:

- Favoritos: todas las tarjetas o solo favoritos.
- Urgentes: todas las tarjetas o solo urgentes pendientes, usando exactamente
  `HIGH_PRIORITY` de la política existente, sin modificar la propiedad prioridad.
- Vencimiento: cualquiera, vencidas, vencen hoy sin vencer o próximas 24 horas.

Urgentes y Vencimiento son independientes y se intersectan, igual que Favoritos
y los filtros anteriores. Por ejemplo, Urgentes + Vencidas devuelve únicamente
la intersección. Las tres condiciones temporales son alternativas del mismo
selector; cada una puede consultarse individualmente. La unión de las cuatro
consultas individuales cubre exactamente los resultados del antiguo compuesto.
No se añade un botón ni una opción para volver a activar Atención como conjunto.

`CardConditionFilter` solo combina condiciones de `AttentionPolicy`; no calcula
otras reglas de fechas, no modifica indicadores ni escribe datos. Los resultados
siguen pasando por `ContextCalendarIndex`, con el mismo alcance jerárquico,
selección de día, filtros de la vista, estados y búsqueda. Favoritos sigue
perteneciendo al propio elemento y no se hereda de sus antecesores. Los Núcleos
favoritos conservan su aparición en la lista general cuando no hay fecha ni
condición de tarea seleccionada. Urgentes conserva el alcance de la condición
original de Atención: tareas no completadas, no prioridad latente de Núcleos.

## Retirada de controles y conservación del estado

Se retiran exclusivamente Favoritos y Atención de la barra del calendario, junto
con el dibujo privado de la bandera retirada. Se conservan navegación, selección,
Hoy, Semana/Mes, plegado, recuento, cumpleaños y el comportamiento existente de
Ver todas. No se modifican `AttentionPolicy`, `TaskTemporal`, los indicadores
inferiores ni los estados de tarjetas.

Los nuevos controles comparten el mismo contexto de vista que ya contenía el
estado de Favoritos. No se crea otro almacén persistente ni otro motor de búsqueda.
Limpiar en Filtrar elimina también los nuevos filtros para evitar restricciones
ocultas. Dentro del Núcleo conserva la fecha seleccionada, como la limpieza de
ese panel hacía antes; la limpieza general conserva su comportamiento anterior
de retirar además la fecha. Cambiar de mes o plegar no altera los filtros.

El Saver temporal de navegación pasa a v3 para conservar Urgentes y Vencimiento,
además de Favoritos, fechas e intención de seguir Hoy. Lee los estados v1/v2,
conservando su fecha, navegación y Favoritos. El flag compuesto retirado se
descarta expresamente: no se convierte arbitrariamente en Urgentes ni se conserva
un filtro invisible. Esto solo afecta a una sesión antigua restaurada, no a datos
de tarjetas ni preferencias confirmadas de respaldo.

No hay modificaciones de Room, migraciones, formato de backup, versión de app ni
archivos almacenados. Los filtros de sesión siguen siendo estado temporal de
navegación, igual que sus equivalentes anteriores.

## Archivos de producción

- `domain/model/CardConditionFilter.kt`: intersección de razones ya existentes.
- `domain/model/ContextCalendarIndex.kt`: integra ese predicado con fecha/Favoritos.
- `ui/ScopeFiltersDialog.kt`: controles individuales con los selectores actuales.
- `ui/NodeSortMenu.kt`: conecta los controles y su limpieza al panel Filtrar.
- `ui/ProjectScreen.kt` y `ui/ProjectsScreen.kt`: conectan selección y resultados.
- `ui/ContextCalendar.kt`: retira los dos controles y conserva el estado temporal
  de los filtros individuales mediante el Saver.

## Pruebas focalizadas y límites

Nueve métodos distintos aprobados:

- `CardConditionFilterTest` (dos): cada razón emitida, equivalencia de cobertura
  con el compuesto original, intersección con Favoritos, día, Persona, Etiqueta,
  Prioridad y Estado; exclusión de completadas, Notas, Capas, prioridad media y
  tareas aún programadas; sin mutación del árbol.
- `ContextCalendarIndexTest.contextualFiltersIntersectWithoutDatesOrWritesAndReuseAttention`:
  conserva la política original usada como referencia y el alcance contextual.
- `DashboardSortPanelTest.globalFilterOpensCalendarDirectlyAndPreservesFiltersAcrossReopening`:
  Favoritos y Urgentes disponibles desde Filtrar, reapertura y limpieza.
- `ContextCalendarUiTest.calendarHasNoCardFilterControlsAcrossThemes`:
  ausencia de los controles retirados y conservación del estado activo.
- `ContextCalendarUiTest.weekMonthFoldFilterAndRestorationWorkInLightAndDark`:
  fechas, navegación, plegado y restauración de Favoritos/Vencimiento.
- `ContextCalendarUiTest.dateControlsRemainVisibleWithoutCardFiltersAtNarrowWidthsAndLargeFonts`:
  controles de fecha visibles a 280/320 dp y fuentes 1/1,5/2.
- `CalendarDateOffsetTest.manualNavigationAndSavedIntentSurviveRollover`:
  Saver v3, lectores anteriores y retirada del flag compuesto sin reinterpretarlo.
- `ContextCalendarIntegrationTest.individualCardFiltersAreAvailableInSortPanelAndPreserveCardsAndIndicators`:
  flujo real en Núcleo, combinación de los tres nuevos controles, reapertura,
  limpieza, indicadores intactos y registros de tarjetas exactamente iguales.

Se adaptan las pruebas que usaban el flag/control retirado. Solo se repitió la
integración que inicialmente no había esperado la apertura del panel; se utiliza
su acción accesible y se espera su composición. No requirió cambios de producción.
Pruebas JVM/Robolectric: pendiente comprobar físicamente el acceso táctil al panel
y a los selectores en Android. No se ejecutan suites completas ni se genera APK.
