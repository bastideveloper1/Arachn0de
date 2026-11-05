# Alternancia de barras inferiores

Se muestra una sola fila de navegación: contextual cuando existe, o global cuando
se selecciona o no hay contextual. Un botón de icono de 48 × 48 dp, reservado a la
derecha, alterna entre ambas. Tiene las descripciones «Mostrar barra global» y
«Mostrar barra contextual», sin texto visible. No ejecuta acciones de navegación,
no limpia el foco ni cierra paneles. El menú principal sigue siendo su acción real,
distinta del botón que muestra la barra global.

La selección vive en el estado de presentación compartido de los módulos inferiores
y se conserva al volver de otra pantalla compatible. Si solo hay una barra
disponible, no aparece un alternador inútil. No es una preferencia de disco:
solo se amplía el mismo estado UI guardable en Bundle; no cambian persistencia,
backup, versión de aplicación ni datos.

Las filas contextuales permanecen compuestas, medidas y sin colocar mientras
están ocultas; se excluyen de accesibilidad y pulsaciones. Se conservan campos,
paneles, búsquedas y selección. Los paneles de edición y los controles temporales
siguen usando sus componentes y ubicación existentes. El marco no reserva espacio
ni dibuja una segunda bandeja cuando su contenido queda vacío.

La anchura del alternador nunca se comparte con otras acciones. El cálculo de
capacidad usa la tipografía real, escala de fuente, iconos, padding y separadores.
Si no caben objetivos de al menos 48 dp, las acciones sobrantes pasan al mismo
`DropdownMenu` de Material ya utilizado en la aplicación. Conservan texto,
habilitación y callback. No hay desplazamiento horizontal ni otra fila permanente.
No se añaden acciones ni se reducen los tamaños de iconos/textos.

## Archivos

En `app/src/main/java/com/r0ybt/arachn0de/ui/`:

- `AlternatingBottomBar.kt`: alternancia, composición retenida y desbordamiento.
- `BottomModules.kt`: selección UI compartida y registro de filas disponibles.
- `GlobalNavigation.kt`: barra global y regreso a contextual.
- `NavigationBarStyle.kt`: elimina el marco vacío de una fila oculta.
- `CoreBottomModule.kt`: barra compartida por Núcleos, Capas, Tareas, Notas y Banco.
- `ProjectsScreen.kt`: acciones contextuales del inicio.
- `BankUi.kt`, `DraftsScreen.kt`, `PeopleScreen.kt`: integran sus filas en el pie
  contextual existente, conservando callbacks y formularios.
- `PersonProfileModule.kt`: acciones existentes de Foto/Banner/Información.

Pruebas en `app/src/test/java/com/r0ybt/arachn0de/ui/`:

- `AlternatingBottomBarTest.kt` (nuevo).
- `NavigationWorkTest.kt`: pruebas de navegación/perfil y acceso explícito a la
  barra global antes de usar sus acciones en las pruebas existentes.

Este informe es el único archivo de documentación añadido en esta intervención.

## Verificación

10 métodos focalizados aprobados mediante `:app:testDebugUnitTest --offline --tests ...`:

1. `AlternatingBottomBarTest.switchRetainsFocusAndState`: alternancia en ambos
   sentidos, una sola barra, descripción accesible sin texto, foco y borrador intactos.
2. `AlternatingBottomBarTest.overflowKeepsAllActions`: 240 dp, fuente al 200 %,
   todas las acciones contextuales/globales ejecutables y alternador separado.
3. `AlternatingBottomBarTest.selectionSurvivesContextReturn`: regreso compatible
   y pantallas con una sola barra, sin alternador sobrante.
4. `NavigationWorkTest.bottomBarSwitchKeepsSearchPanelAndSelectionAcrossNavigation`:
   AppRoot real, búsqueda conservada, entrar/volver de un Núcleo, panel seleccionado
   intacto y datos sin cambios.
5. `NavigationWorkTest.profileBarRetainsItsPhotoPanelWithLargeFont`: perfil actual
   con letra grande, panel Foto intacto y acceso a Información.
6. `NavigationWorkTest.contextualNameSearchFiltersOriginalCardsAndClosingKeepsTheCurrentLayer`:
   búsqueda jerárquica original y cierre sin modificar ubicación ni datos.
7. `GlobalBottomBarTest.narrowBarKeepsFourTargetsInOrderAndLeavesTemporaryModulesAboveIt`:
   geometría y objetivos táctiles de la barra global cuando es la única disponible.
8. `BankPaymentUiTest.mainDirectPaymentWithoutAccountsAndFinalCancellationWritesNothing`:
   acceso y cancelación del formulario existente tras integrar la barra en el pie.
9. `AudioUiTest.compactAudioUsesNavigationFrameAndKeepsControlsAndSession`.
10. `PomodoroUiTest.compactBarToggleDoesNotChangeTimerSession`.

`git diff --check` aprobado. No se ejecutaron suites generales ni se generó APK.

Se ejecutó además `PersonEvolutionUiTest.emptyPersonAndHundredPeopleAreSearchableWithLargeFont`,
que falla esperando «Buscar Personas» al entrar. Esa expectativa es anterior al
comportamiento actual: `PeopleScreen` resuelve `ensurePrincipal()` y abre su perfil
antes del listado. Esa resolución de entrada no se modificó en esta intervención;
el fallo ocurre antes de alcanzar la fila de búsqueda modificada. No se cambió
la navegación de Personas para satisfacer la prueba antigua. La prueba específica
del perfil actual indicada arriba está aprobada.

Pendiente: comprobación física de IME, gestos y tamaños de pantalla. El foco y la
conservación de formularios se verificaron localmente con Robolectric; los controles
reales de audio y Pomodoro conservan sus sesiones en sus pruebas focalizadas.
