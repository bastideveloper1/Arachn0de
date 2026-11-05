# Investigación focalizada del desfase del calendario

## Corrección posterior: seguimiento de Hoy

Tras precisar el caso (miniatura del día anterior al desplegar después de medianoche),
se confirmó una desincronización del estado: `anchor` se inicializaba una vez,
mientras la marca de hoy recibía la fecha local actualizada por el reloj existente.

`ui/ContextCalendar.kt` distingue ahora seguimiento automático y navegación manual.
Un contexto nuevo y el botón Hoy activan el seguimiento; asignar una selección o
navegar modifica la intención a manual, aunque el día pulsado coincida con hoy.
Al recibir la fecha local actual, el seguimiento actualiza el ancla y, si estaba
activo, el filtro de día. No cambia favoritos, atención, presentación ni jerarquía.
La actualización se realiza también al recuperar un ámbito antes de calcular sus
resultados. Miniatura y cuadrícula consumen ese mismo estado. Se reutiliza
`rememberTaskScreenNow`, incluido su refresco al reanudar, sin añadir temporizadores.

La intención se conserva en el Saver privado de navegación (v2); se siguen leyendo
las filas anteriores, que se conservan como navegación manual por no poder inferir
la intención histórica. Es estado efímero de interfaz, no un nuevo dato confirmado:
no se añaden preferencias, tablas ni datos a los respaldos, ni cambia su formato.

Archivos modificados en la corrección: `ui/ContextCalendar.kt`,
`ui/ContextCalendarUiTest.kt`, `ui/CalendarDateOffsetTest.kt` y este informe.

Validación de la corrección: cinco métodos distintos aprobados mediante
`:app:testDebugUnitTest --offline`, sin repetir las matrices de fechas anteriores:

- `todayRollsOverInBothPresentations`: 31 de diciembre → 1 de enero, miniatura y
  cuadrícula sincronizadas; atención/favoritos intactos; selección manual del día
  actual protegida; Hoy reactiva el seguimiento incluido el filtro de día.
- `midnightResumeUsesExistingClock`: ciclo RESUMED → CREATED → RESUMED con reloj
  inyectado cruzando medianoche; la miniatura se actualiza al volver.
- `manualNavigationAndSavedIntentSurviveRollover`: selección manual, navegación
  mensual sin selección, ámbitos inactivos, intención automática restaurada y
  compatibilidad con el Saver anterior de 11 campos.
- `todayIsIdempotentAndPreservesPresentationAndFilters`: regresión del botón Hoy.
- `weekMonthFoldFilterAndRestorationWorkInLightAndDark`: navegación, filtros y
  restauración Compose existentes. Su primer intento falló al comprobar el plegado
  antes de completarse el commit en IO; se añadió espera explícita a la prueba y
  se repitió únicamente ese método, que aprobó. No cambió la lógica de plegado.

No hay comprobación física en dispositivo; la reanudación y el cambio de fecha se
ejercitaron con Robolectric. Sin cambios de versión, esquema o backup, sin APK.

El resultado siguiente corresponde a la investigación inicial, antes de concretar
el caso de actualización de Hoy.

## Resultado

No se reprodujo ni se identificó una causa concreta del desfase comunicado. No se modificó código de producción, fechas almacenadas, preferencias, esquema ni versión. Se añadieron pruebas de diagnóstico; no se aplicó una corrección especulativa.

## Flujo revisado

- `domain/model/CalendarSnapshot.kt`: fechas civiles con meses 1–12, cálculo de celdas y rellenos, agrupación de vencimientos en la zona local. Los rótulos usan UTC deliberadamente, junto con su formateador UTC, sin convertir la fecha civil al día anterior.
- `domain/model/NodeFilters.kt`: desplazamientos civiles e intervalos entre medianoches locales, sin asumir días de 24 horas.
- `domain/model/ContextCalendarIndex.kt`: resultados del ámbito por vencimiento o, sin vencimiento, inicio programado.
- `ui/ContextCalendar.kt`: cuadrícula de lunes a domingo, selección, ancla de navegación y restauración por ámbito. Navegar cambia el período visible (`anchor`), conservando el filtro (`selected`) hasta seleccionar otro día o limpiar. Esto también está exigido por la prueba previa de navegación/restauración. Puede explicar una percepción de diferencia entre período y resultados; no prueba que sea el fallo reportado.
- `ui/CalendarScreen.kt` y `ui/state/CalendarFilterState.kt`: selección, intervalos, agrupación de tareas y eventos, etiquetas y navegación de meses.
- `calendar/CalendarBirthdays.kt`: proyección anual civil; un cumpleaños del 29 de febrero no se traslada al 28 ni al 1 de marzo.
- `data/local/CalendarEvents.kt`: eventos con fecha civil ISO, sin desplazarlos por zona; validación explícita de horas locales inexistentes.
- `ui/TaskDates.kt`: componentes UTC del selector Material combinados con hora local para guardar el instante; rechazo de horas inexistentes por horario de verano.
- `ui/state/TaskScreenClock.kt` y consumidores: actualización al reanudar y ante cambios de hora/zona, comprobación periódica en primer plano.

## Verificación ejecutada

Una ejecución focalizada de `:app:testDebugUnitTest --offline`, cuatro métodos aprobados, cero fallos:

- `CalendarDateOffsetTest.gridsAndNavigationMatchCivilDates`: los 12 meses de 2024, 2025, 2026, 2028 y 2100 frente a `LocalDate`; lunes/domingo como inicio; navegación desde días 28–31, límites de mes y año, bisiestos y siglo no bisiesto.
- `CalendarDateOffsetTest.localSelectionMatchesTasksAndEventDates`: UTC, Santiago, Kiritimati y Nueva York; extremos de día, cambios de mes/año, 29 de febrero y fechas de transición horaria chilena. Correspondencia entre instantes, índices de tareas, filtro por intervalo, fecha ISO de eventos y conversión del selector al mediodía local.
- `ContextCalendarUiTest.monthEndSelectionKeepsDateAndResultsAligned`: enero 31 → febrero bisiesto, selección real del 29 con tarea local a las 23:30, indicador, filtro, plegado/recomposición y navegación marzo → febrero.
- `CalendarBirthdaysTest.annualProjectionIncludesEveryoneComputesOnlyKnownAgesAndDoesNotMoveLeapBirthdays`: prueba existente reutilizada para la correspondencia anual y el 29 de febrero.

Archivos de pruebas añadidos/modificados: `app/src/test/java/com/r0ybt/arachn0de/ui/CalendarDateOffsetTest.kt` y `app/src/test/java/com/r0ybt/arachn0de/ui/ContextCalendarUiTest.kt`.

## Límites y próximo paso

Sin dispositivo conectado ni captura del caso original. No se acreditó la reproducción física al regresar de otra pantalla, cambiar la zona del dispositivo o cruzar medianoche. La comprobación de eventos aquí cubre la representación civil, no un nuevo recorrido de edición y persistencia; estas rutas se revisaron en el código. No se ejecutaron suites completas ni se generó APK.

Para localizar el caso pendiente hace falta una fecha concreta, fecha esperada/mostrada, zona del dispositivo y secuencia de selección/navegación; si hay una excepción, Logcat. Mantener la implementación actual hasta obtener evidencia reproducible.
