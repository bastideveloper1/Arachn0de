# Menú global inferior — experimento

Estado posterior: la fila independiente descrita en este registro histórico fue sustituida por la [integración en barras habituales](GLOBAL_MENU_INTEGRATION_PAYMENTS.md).

## Inspección y organización

El menú ya centralizaba sus acciones en `AppRoot` mediante `GlobalNavigation` y reutilizaba `AppIdentityDrawer`. Se conservan los callbacks y estas opciones condicionales:

| Categoría | Opciones |
| --- | --- |
| Inicio | Núcleos, Borradores, Metro, Buscar, Antisocial, Calendario, Tragaperras |
| Herramientas | Diagramas, Wireframe, Pomodoro, Captura rápida, Juego |
| Organizar | Tecnologías, Etiquetas, Pagos, Recurrencias, Estadísticas de Metro |
| Ajustes | Apariencia, Seguridad y respaldos, Acerca de |

Solo aparecen las acciones disponibles, igual que antes. Seguridad y respaldos conserva la selección de su controlador existente; Captura rápida mantiene su disponibilidad contextual anterior. Los callbacks de Favoritos y Atención que recibe el componente ya no tenían entradas propias en la presentación inspeccionada; no se añaden ni se eliminan destinos en esta intervención.

La presentación experimental muestra las opciones en dos columnas con desplazamiento interno cuando hace falta. Las cuatro categorías permanecen abajo dentro del mismo panel. Son pestañas directas, sin submenús ni paneles en cascada. El acceso seleccionado de la esquina inferior derecha sirve para cerrar; se distingue también por fondo y borde. Se mantienen los colores del tema y las imágenes personalizadas del menú.

## Integración

`BottomModulesFrame` y el sistema de propiedad de `BottomModulesHost` reservan el espacio del acceso global, debajo de las barras contextuales, audio y temporizador. No se reemplazan las herramientas de Núcleos, Capas o Tareas. En las pantallas habituales hay un solo acceso, abajo a la derecha; se oculta el acceso de la cabecera. Los bloqueos `enabled` que ya declaraban las cabeceras se transmiten al acceso inferior durante operaciones.

Está disponible en las pantallas principales montadas por `AppRoot`: Núcleos, interiores de Núcleo/Capa/Tarea, Borradores, Buscar, Antisocial, Calendario, Pagos, Apariencia, Acerca de, Seguridad/Respaldos y las herramientas Diagramas, Wireframe, Pomodoro, Metro, Juego y Tragaperras, mientras se muestran como pantallas principales.

El panel usa `BottomModulesSheet` y la hoja modal Material existente: animación desde abajo, gesto descendente, pulsación exterior, Atrás y áreas seguras. La pantalla de origen sigue compuesta bajo la cobertura, con sus manejadores Atrás suspendidos por `NavigationContent`.

Los diálogos y editores modales existentes toman temporalmente la propiedad de los módulos inferiores. No se inserta otro acceso inferior en ellos. Cuando ya usan una cabecera global, conservan temporalmente ese acceso anterior; por ejemplo, el detalle modal de Persona conserva su callback que primero cierra el detalle. Los demás conservan su cierre habitual antes de volver al acceso global. Esto evita imponer barras nuevas sobre herramientas y operaciones modales. No se cambia su autenticación ni su comportamiento interno.

## Reversión

`EXPERIMENTAL_BOTTOM_GLOBAL_MENU` en `GlobalNavigation.kt` cambia únicamente la presentación. Con `false`, vuelven el acceso de cabecera y el contenedor anterior, con las mismas acciones. No hay ajustes nuevos almacenados, migraciones, dependencias, cambios de cifrado ni modificaciones de backup.

## Archivos

Implementación, bajo `app/src/main/java/com/r0ybt/arachn0de/ui/`:

- `GlobalNavigation.kt`: acceso inferior, interruptor y conservación de bloqueos durante operaciones.
- `NavigationChrome.kt`: presentación compartida, cuadrícula experimental y hoja inferior.
- `AppRoot.kt`: conexión con el controlador global y módulos inferiores.

Pruebas nuevas: `ExperimentalMenuPresentationTest.kt` y `ExperimentalGlobalMenuIntegrationTest.kt`.

El trabajo anterior de calendario y niveles de Capa se documenta en `CALENDAR_NAVIGATION_BLOCK_2.md`.

## Revisión física pendiente

Evaluar alcance con el pulgar, coste vertical de la fila inferior de 48 dp, cuadrícula con nombres largos y fuentes ampliadas, teclado real, navegación por gestos/tres botones, pantallas horizontales y convivencia con audio/Pomodoro. No se genera ni firma APK oficial.

## Validación realizada

Compilación aprobada y cinco pruebas focalizadas finales aprobadas:

1. Veinte acciones originales con sus callbacks, categorías inferiores estables, letra 1,5×, desplazamiento y áreas táctiles de al menos 48 dp.
2. Cierre mediante Atrás, pulsación exterior y gesto descendente.
3. Acceso único y conservación de bloqueos durante operaciones.
4. Integración real con `AppRoot`: dashboard, interior de Núcleo, Buscar, separación de barras, insets de teclado simulados y texto conservado al cerrar el menú.
5. Compatibilidad de la presentación anterior con todas sus opciones, espacio limitado e insets simulados.

No se repiten auditorías ni pruebas de persistencia ajenas al cambio. Las comprobaciones de calendario del bloque 2 se ejecutaron antes de esta intervención, con siete pruebas distintas aprobadas.
