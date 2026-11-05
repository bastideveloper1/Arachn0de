# Bloque 4 — Acciones de elemento

Se sustituyen exclusivamente cinco correspondencias por etiqueta exacta:
Desfijar, Eliminar, Fijar, Guardar como plantilla e Historial.

## Ubicaciones

- Menú exterior de `NodeCard` (Capas, Tareas y Notas según las acciones que cada
  tipo admite): plantilla, fijado/desfijado en Capas, historial si tiene callback,
  eliminar.
- Menú exterior de `ProjectCard`: plantilla, fijado/desfijado, eliminar. No se
  añade Historial porque esa entrada no existe en este menú.
- Opciones del elemento abierto (`ProjectScreen` / `CoreBottomModule`): las
  mismas correspondencias cuando la acción está disponible.
- `ActionMenuItem`: reutiliza el recurso solo para una de esas cinco etiquetas
  exactas, incluido Eliminar en confirmaciones de elementos.
- Otros consumidores del menú compartido reutilizan únicamente las mismas
  acciones exactas; por ejemplo, Fijar/Desfijar en entradas de recuerdos.

Los recursos se resuelven antes del tratamiento genérico de Description:
Guardar como plantilla deja de mostrar la identidad visual de Descripción.
Las propiedades del bloque 3 y los recursos de bloques 1 y 2 permanecen intactos.
No se cambian etiquetas, orden, callbacks, permisos, dimensiones o almacenamiento.

## Archivos

- `res/drawable/element_action_{desfijar,eliminar,fijar,guardar_plantilla,historial}.xml`.
- `ui/ElementActionIcons.kt`: correspondencias exactas.
- `ui/ActionComponents.kt`: presentación de iconos en menús compartidos.
- Pruebas: `ElementActionIconsTest.kt`, `ElementActionControlsTest.kt`.

## Verificación focalizada

- Los cinco trazados y viewports coinciden literalmente con los SVG originales.
- Estados Fijar/Desfijar distintos y correspondencias ajenas excluidas.
- Renderizado en todos los temas oficiales a 18, 24, 28 y 240 px, con tinte y
  límites del viewport comprobados.
- Controles compartidos de menú exterior/interior: cambio de fijado, callbacks,
  altura táctil y acceso a Seleccionar, Mover a y Limpiar completadas conservados.
  La prueba usa callbacks de fixture; no ejecuta eliminaciones de datos reales.

Resultado: tres pruebas focalizadas aprobadas, sin fallos.

## Pendientes

Mover a, Limpiar completadas y Seleccionar: SVG adjuntos, pero fuera de la tabla
expresamente autorizada de cinco acciones; no se incorporan. Ninguna de las cinco
correspondencias autorizadas queda pendiente. Revisión visual física en Android
pendiente. Sin cambio de versión ni generación de APK.
