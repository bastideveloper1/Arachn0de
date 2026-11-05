# Ajustes visuales posteriores al bloque 4

## Calendario

`ContextCalendar` reemplaza la exclamación por la misma bandera de prioridad que usa Arachn0de. Bandera, Favoritos y plegado permanecen visibles en una única fila de 48 dp. Se elimina el desplazamiento horizontal de los controles.

El periodo abre un menú con los mismos controles Anterior/Siguiente y su mes/año; también contiene el recuento y Ver todas cuando hay filtros. Hoy y Semana/Mes conservan sus acciones y estado. En anchos inferiores a los necesarios para seis objetivos de 48 dp, Hoy pasa a ese menú; en anchos aún menores pasa también Semana/Mes. No se reducen los objetivos táctiles para forzar la distribución. Las etiquetas que no caben con fuentes grandes se sustituyen visualmente por el icono del mismo control, manteniendo el nombre accesible. El calendario plegado sigue mostrando fecha y desplegado.

La estrella y la bandera distinguen activo/inactivo mediante forma o intensidad. No se agregan círculos, fondos de selección ni otra fila. Se conservan el filtrado, la navegación de fecha y su restauración.

## Dificultad y geometría

`TaskEffortMini` conserva la flama y superpone una insignia cuadrada de 9 dp dentro de su esquina inferior derecha. El número 1–5 tiene contraste independiente del color de la flama. El espacio conserva el ancho medido de la presentación anterior, para no mover los metadatos vecinos; su altura es 16 dp. La insignia usa una cifra pequeña de tamaño visual fijo, con la descripción accesible completa del nivel. Cero mantiene la flama inactiva, sin insignia.

La inspección encontró 14 dp de margen exterior en el listado de Núcleos y 12 dp en su interior, frente a barras inferiores de ancho completo. `ProjectsScreen` y `ProjectScreen` pasan a un margen horizontal común de 4 dp para tarjetas y barra habitual: se recuperan 20 dp y 16 dp respectivamente para el contenido. No se añade separación inferior, elevación ni presentación flotante; se mantienen insets y reservas de altura existentes. Fotografías, skins y contenido interno de las tarjetas conservan sus recursos y paddings propios.

## Apariencia

`IndicatorColorSettings` utiliza dos columnas, con icono coloreado y nombre dentro de celdas accesibles. Se eliminan los círculos de muestra de esta sección; el selector de colores conserva su presentación original. Las celdas crecen verticalmente con las fuentes y envuelven los nombres, sin truncarlos. Ojo y cumpleaños se dibujan con los recursos reales, evitando el fallback de Lucide que dejaba su espacio vacío.

Una única celda Dificultad abre un panel inferior con los cinco niveles visibles. Cada nivel abre el selector existente y actualiza inmediatamente su color. Restablecer dificultad retira solo esas cinco elecciones; Restablecer colores conserva su función global. La celda principal muestra el color del último nivel elegido durante la sesión (nivel 1 al abrir por primera vez).

## Preferencias autorizadas

Se inspeccionó la validación estricta de `AppearancePreferences` antes de ampliar su lista. Se informó que los lectores anteriores rechazarían las nuevas claves y el usuario autorizó expresamente `effort_1`…`effort_5`.

Las elecciones reutilizan `indicator_color_<id>` en `appearance_preferences`. No se modifica Room, cifrado, estructura del respaldo, codec ni restauración. No se añaden archivos. Las preferencias anteriores y sus colores siguen intactos; sin claves nuevas, los cinco niveles usan el color monocromático por defecto del tema. La validación común acepta únicamente identificadores y colores conocidos. Los respaldos anteriores siguen siendo legibles; se mantiene la limitación informada para importarlos en versiones antiguas que desconozcan los cinco identificadores.

## Validación dirigida

Se reutilizan pruebas de calendario, dificultad, colores, respaldo y navegación; las comprobaciones se adaptan a la nueva ubicación de los controles:

- Bandera, Favoritos y plegado visibles a 280/320 dp, con fuentes 1/1,5/2, sin cambiar la altura de la fila ni reducir objetivos táctiles.
- Hoy, Semana/Mes, navegación de periodo, filtros, cumpleaños y restauración del ámbito.
- Insignias 1–5 dentro de la flama, con altura fija y posición estable de los metadatos vecinos; interacción y guardado originales conservados.
- Alineación medida entre tarjetas y barra de Núcleos y entre calendario y barra del interior.
- Recursos visibles de ojo, torta y flama, incluidos tintes de skins.
- Cuadrícula de dos columnas a 320 dp y fuente 1,8, cinco elecciones independientes, actualización tras recomposición, recarga y ambos restablecimientos.
- Recuperación de las cinco elecciones junto con preferencias anteriores, rollback transaccional y restauración histórica por el respaldo existente.

Catorce pruebas dirigidas distintas pasaron, reutilizando las suites existentes de colores, iconografía, dificultad, calendario, navegación y preferencias de backup. No se repitieron las comprobaciones de componentes ajenos al cambio.

Las pruebas son automatizadas con Robolectric. No sustituyen una nueva revisión física en Android. No se genera ni firma un APK oficial.
