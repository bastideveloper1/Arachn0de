# Arachn0de — Cierre Metro: integración nativa

## Cambios

La integración anterior ocultaba la opción Metro cuando los defaults elegían Nota o Capa y enviaba la edición al módulo Metro. El formulario habitual muestra ahora **Contenido → Viaje Metro**, independientemente del tipo predeterminado al crear. Configura origen, destino, paradas ordenables, tren normal/expreso y Persona dentro del Proyecto/Capa/Subcapa actual. Conserva descripción, fechas, responsables, prioridad, tecnologías y el resto del formulario.

**Elegir viaje existente** vincula un viaje independiente sin duplicarlo. Los viajes ya vinculados a otra tarjeta conservan su relación y no se ofrecen para traslado implícito. Puede crearse explícitamente otro viaje con el recorrido seleccionado; sus sesiones/historial no se copian. Seleccionar, cancelar o cerrar el editor no escribe vínculos. La revisión/identidad se valida junto con la tarjeta, dentro de una transacción; los conflictos y planes incompletos no dejan tarjetas parciales.

Editar una tarjeta Viaje carga el itinerario en ese mismo formulario, sin salir a Metro. Los cambios de configuración mantienen ID/vínculo, modo activo/desactivado e historial. El plan de una sesión activa sigue siendo el de esa sesión; modificar el plan guardado no lo reemplaza. Guardar únicamente metadatos no reinterpreta planes históricos ni aplica restricciones nuevas silenciosamente. Recargar tras un cambio concurrente requiere confirmación y solo reemplaza los campos Metro del borrador. Guardados limpia referencias al viaje original antes de aplicar un plan.

Las estaciones conservan su composición, círculos, recorrido continuo e indicadores. Solo cambia el fondo a los tokens carbón **Surface / SurfaceRaised** de Arachn0de. Las filas de vías en Planificar dan al nombre el ancho completo, con controles compactos debajo: corrige nombres invisibles en pantallas estrechas y permite cambiar la estación mediante el mismo selector.

## Compatibilidad

Room **25**, backup **17**, payload Metro **v2**, applicationId y versión **0.3.0 / 9** sin cambios. No hay migraciones ni archivos privados nuevos. Se reutiliza la relación existente `metro_journeys.nodeId` y se prueba su restauración con sesión activa. EditorDraft conserva formatos anteriores y añade metadatos opcionales de vínculo/revisión; no son datos confirmados de backup.

## Validación

**82 casos dirigidos distintos aprobados** entre las tandas: 73 de Metro/Guardados/borradores y 9 del flujo habitual/creación. La última tanda repitió únicamente las áreas afectadas por la protección final de planes incompletos: **43 pruebas, 0 fallos**. Sin suite global.

- Flujo real AppRoot → Proyecto → Nuevo elemento, y dentro de Capa/Subcapa: seleccionar Viaje Metro, configurar y guardar en el contenedor correcto, incluso con defaults de Nota/Capa.
- Elegir un viaje independiente, restaurar estado del editor, vincular sin duplicar y editar después desde el mismo formulario.
- Rechazo transaccional de viaje eliminado/obsoleto/ya vinculado y configuración incompleta; sin tarjeta parcial.
- Sesión activa e historial preservados al vincular/editar; Persona viajera y modo desactivado conservados/actualizados según la edición. Exportación/restauración íntegra del vínculo en Subcapa con sesión activa. Guardado de metadatos bajo restricciones nuevas conserva el plan.
- Guardados, lectura de borradores anteriores, revisión/baseline recuperados y regresiones de creación normal/recurrente. La primera tanda incluyó todas las regresiones Metro, catálogo auditado, timeline, seguimiento y notificaciones.
- Parada de nombre largo visible a 320 dp, edición mediante selector y eliminación; capturas inspeccionadas de creación, edición, Planificar y explorador carbón.
- `assembleDebug`: **BUILD SUCCESSFUL**. APK debug local `app/build/outputs/apk/debug/app-debug.apk`, **102.645.092 bytes**. Catálogo maestro y cinco imágenes del juego comparados byte por byte dentro de la APK.
- `git diff --check`: correcto. Versión 0.3.0 / 9 y protocolo de firma sin cambios.

Logs: `/tmp/metro-native-{tests,flow,final}.log`.

Capturas de Compose en Robolectric API 28, 320 dp: [artifacts/metro-native](artifacts/metro-native). Incluyen el flujo habitual en Proyecto/Capa/Subcapa, selección de viaje independiente, edición inline, parada de nombre largo y explorador carbón.

No se dispone de prueba física en teléfono. No se realizó commit, push, firma, release ni instalación; se conservan íntegramente los cambios anteriores.
