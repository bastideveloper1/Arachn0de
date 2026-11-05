# Bloque 1 — correcciones funcionales

## Cambios

- Código en descripciones: se conserva la transformación existente. Se recorta el dibujo del recuadro al campo y a su área interior para impedir que invada herramientas y controles al desplazarse un texto largo.
- Guardado/exportación: `OperationProgress` es una representación reutilizable del estado `busy` existente, con colores del tema y animación indeterminada (la generación completa no tiene porcentaje fiable). Se integra en ambos menús de imagen, Markdown y JSON. El estado termina después del guardado y su limpieza existente. No se añaden mensajes de éxito; los errores de imagen se presentan en línea. El menú contextual impide nuevas acciones y cierre mientras está ocupado.
- Cumpleaños en Núcleo/Capa: se conservan las consultas y filtros existentes; las tarjetas complementarias se sitúan después del contenido habitual para no desplazarlo al inicio de la lista.
- Hoy: el botón contextual regresa al período de la fecha actual, conservando modo y filtros. Si ya había una fecha seleccionada la lleva a hoy; si solo se estaba navegando, no activa una selección ni una vista de tareas filtradas. Las pulsaciones repetidas no alternan filtros. No se añaden controles ni se modifica el calendario general.

No se modifican almacenamiento, cifrado, formatos de backup ni generadores de archivos. No se genera ni firma una APK oficial.

## Archivos de implementación

Todos bajo `app/src/main/java/com/r0ybt/arachn0de/ui/`:

- `AttachmentDescriptionField.kt`
- `OperationProgress.kt` (nuevo)
- `CardImageCommands.kt`
- `CardTreeImageCommands.kt`
- `ElementExportActions.kt`
- `ElementOptionsExports.kt`
- `ActionComponents.kt`
- `ShareExportPanel.kt`
- `ContextCalendar.kt`
- `ProjectScreen.kt`

## Pruebas focalizadas

Se amplían `ContentFormattingTest`, `ContextBirthdaysUiTest`, `ContextCalendarUiTest` y `ContextCalendarIntegrationTest`; se añade `FunctionalBlockOneUiTest`.

La validación automatizada del editor cubre texto corto/largo, veinte ciclos de formato, selección, campo acotado y dibujo fuera del campo al mover el cursor. La comprobación física con teclado real requiere un dispositivo o emulador interactivo.

## Resultados

- 11 pruebas distintas aprobadas entre las ejecuciones focalizadas: 4 de transformación/selección, 2 de representación existente, 1 de cumpleaños reactivos, 1 de navegación Hoy, 1 de convivencia de tarea/cumpleaños y 2 de progreso/recorte visual.
- La prueba visual comprueba también píxeles fuera del campo en tres posiciones del cursor. Se adaptó la captura a `View.draw(Canvas)`, ya utilizado en el proyecto, por incompatibilidad de `captureToImage` con este entorno Robolectric.
- Se revisaron los guardas contra pulsaciones repetidas, los bloques de error y `finally`, y la permanencia del estado ocupado hasta terminar la escritura/limpieza. No se cambian los generadores, transacciones ni diarios de archivos.
- Pendiente de comprobación interactiva: teclado real y guardado mediante proveedores SAF del dispositivo (incluidos fallos del proveedor). No se afirma validación física de esos escenarios.
