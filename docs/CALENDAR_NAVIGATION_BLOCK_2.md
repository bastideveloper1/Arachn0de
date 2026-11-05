# Bloque 2 — calendario y navegación

- El calendario ya tenía modos plegado/semana/mes y un almacén de estado. El control de plegado ahora permanece visible; minimizado se presenta una sola barra con fecha y expansión. Se reutiliza el almacén en la raíz, con claves separadas por Núcleo/Capa, para conservar cada ámbito al regresar. No se cambia Hoy, los filtros ni los cumpleaños.
- El nivel de Capa se separa del nombre, junto al icono oficial, con tipografía secundaria y alineación a la primera línea. No se escribe el nombre ni se cambia la jerarquía; no se añade una fila de cabecera.
- El menú existente pasó las verificaciones de contenido, límites, cierre inferior e insets sin cambios en su implementación durante este bloque. La prueba antigua del tema suponía una cabecera fija en pantalla; se ajustó para comprobar tamaño y contención, compatibles con la altura adaptable existente.

Archivos de implementación: `ContextCalendar.kt`, `DetailPresentation.kt`, `ProjectScreen.kt`, `ProjectsScreen.kt`, `AppRoot.kt`, bajo `app/src/main/java/com/r0ybt/arachn0de/ui/`.

Pruebas: nuevo `CalendarNavigationBlockTwoTest.kt`; se adaptan `ContextCalendarUiTest.kt` al plegado compacto y `DrawerOrganizationTest.kt` a la altura adaptable. Siete comprobaciones distintas aprobadas: barra compacta/estado por ámbito/restauración; título corto/largo y niveles con letra ampliada; todas las opciones del menú con insets simulados; navegación/filtros/restauración del calendario; cumpleaños reactivos; cierre inferior; tema personalizado y callback de respaldos.

Pendiente físico: alcance del pulgar, teclado real, áreas seguras de distintos teléfonos y preferencia visual del indicador de nivel. Sin APK oficial, cambios de persistencia, cifrado o backups.

El rediseño experimental solicitado después de este bloque se documenta por separado.
