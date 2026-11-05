# Grupo 3 — Calendario

## 1. Cumpleaños antes de las tarjetas

Causa: `ProjectScreen` añadía el elemento lazy `context-birthdays` después de
las tarjetas; la lista general hacía lo mismo. No era un problema del índice
de cumpleaños ni de su ordenación.

Se mueve ese mismo elemento inmediatamente después del calendario y antes del
resto de los resultados. Solo se inserta si el día seleccionado tiene cumpleaños,
evitando introducir una fila vacía al principio de fechas sin cumpleaños. Se
conservan su clave, `ContextBirthdayCards`, el orden por nombre/ID de
`CalendarBirthdays`, los callbacks y todo el ordenamiento de las otras tarjetas.
No se cambia almacenamiento ni información de las personas.

Estado: implementado en el interior de Núcleos/Capas y en los resultados de la
lista general. Se mantienen las exclusiones previas de pantallas especializadas.

Prueba focalizada aprobada:
`ContextCalendarIntegrationTest.birthdaysLeadLayerAndDashboardResultsAndKeepExistingOrder`.
Comprueba dos cumpleaños antes de dos tarjetas, orden interno de ambos grupos,
ambos contextos, otra fecha sin cumpleaños, nodos persistidos idénticos y apertura
del detalle de la misma persona. Pendiente comprobar físicamente la visibilidad
inmediata con listas y cumpleaños reales en Android.

## 2. Navegación horizontal

### Calendario: implementado

No existía navegación por arrastre. La cuadrícula sí tenía desplazamiento
horizontal para conservar el tamaño de los días cuando no caben en pantalla.

Se añade un gesto con bloqueo de eje horizontal y umbral de 48 dp:

- Izquierda: mes siguiente; derecha: mes anterior.
- Disponible en la cabecera del calendario desplegado y en la cuadrícula cuando
  cabe completa. También cambia un mes si la presentación actual es semanal;
  los botones conservan su navegación previa por periodo.
- Si la cuadrícula desborda, conserva exclusivamente su desplazamiento lateral;
  cambiar de mes sigue disponible mediante el gesto de la cabecera y los botones.
  No se intenta interpretar el mismo arrastre como desplazamiento y navegación.
- El gesto consume el arrastre para cancelar la pulsación de día/botón. Un toque
  normal mantiene su acción. El calendario plegado no recibe el nuevo gesto.
- Solo cambia `anchor`, utilizando `CalendarMonth.shifted` y su ajuste al último
  día válido. Conserva selección, filtros individuales, modo y demás contexto.
  La navegación manual deja de seguir Hoy, como ya hacen los botones.
- Se respetan los límites enero del año 1 y diciembre del 9999.

Dos pruebas focalizadas aprobadas en `ContextCalendarUiTest`:

- `headerSwipesChangeMonthAndKeepSelectionFiltersAndBounds`: ambos sentidos,
  enero/febrero bisiesto, cambio de año, límites, selección y filtros conservados,
  sin apertura accidental del menú de periodo.
- `gridSwipeCancelsDayTapAndOverflowKeepsHorizontalScroll`: cambio de mes desde
  cuadrícula amplia sin seleccionar un día, desplazamiento de cuadrícula estrecha
  sin cambiar de mes y gesto de cabecera conservado en esa anchura.

Pendiente comprobación física de umbral/ergonomía, interacción con desplazamiento
vertical de la lista y gestos del sistema. La validación automatizada no sustituye
los gestos reales en Android.

### Menú inferior: pendiente de decisión, sin modificaciones

La revisión se acotó a los componentes compartidos:

- `CoreBottomModule` expone Contenido/Crear/Opciones mediante `visibleContexts`.
  Es la secuencia candidata; no todas las pantallas muestran los mismos contextos.
- `AlternatingBottomBar` alterna barra contextual/global. La barra global es un
  conjunto de acciones y destinos, no una secuencia equivalente de pestañas.
- Los paneles contienen campos, selectores y controles desplazables. Un gesto
  horizontal sobre todo su contenido competiría con esas interacciones.
- La cabecera de plegado ya usa arrastre vertical y mide 24 dp. Añadir otro eje
  allí requiere delimitar y comprobar el área táctil, sin asumir que será cómoda.
- La fila de acciones puede utilizar overflow según anchura/fuente. Interceptar
  sus arrastres sin definir el área podría cancelar interacciones con esos botones.
- `choose` también cierra controles de vista, limpia foco y oculta teclado. Por
  ello no corresponde dispararlo por cualquier movimiento horizontal del cuerpo.

No existe una única interpretación segura de «siguiente» para todo el menú.
Siguiendo la instrucción de evitar comportamientos especulativos, no se instala
un gesto global ni se alteran barras, paneles, formularios o botones.

Propuesta para una autorización posterior: limitar inicialmente el gesto a una
zona acordada del panel contextual abierto, recorrer únicamente `visibleContexts`
y detenerse en los extremos, excluyendo la barra global y los editores activos.
Hace falta decidir la zona (cabecera de plegado o fila contextual) antes de
implementarlo y validar su ergonomía y la coexistencia del arrastre vertical.
No se añade ninguna pantalla ni control nuevo en esta intervención.

## Archivos y validación

Producción: `ui/ProjectScreen.kt`, `ui/ProjectsScreen.kt`, `ui/ContextCalendar.kt`.
Pruebas: `ui/ContextCalendarUiTest.kt` y
`status/ContextCalendarIntegrationTest.kt`. Rutas bajo
`app/src/main/java/com/r0ybt/arachn0de/` y
`app/src/test/java/com/r0ybt/arachn0de/`, respectivamente.

Tres métodos focalizados aprobados, sin suites completas ni repeticiones.
`git diff --check` sin errores. No se modifica la investigación de alineación,
los filtros del Grupo 2, persistencia, versión ni APK. No se modifica código del
menú inferior: el análisis anterior no constituye validación táctil de ese gesto.
