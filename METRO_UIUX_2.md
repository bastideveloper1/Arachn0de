# Metro 2 — interfaz y seguimiento

## Revisión y cambios

Se revisaron Inicio, Líneas/exploración, Planificar, Viajes, Recorrido, Seguimiento, mapa, combinaciones y notificaciones. Había información de posición repetida, recuperación y correcciones como botones secundarios dispersos, nombres de tarea dentro de las tarjetas de viaje y acciones de administración intercaladas en el detalle.

- Viajes: origen → destino, minutos aproximados, líneas distintas, combinaciones y estado (Guardado, Activo, Pausado o Finalizado). Tocar abre el viaje original; menú de tres puntos para editar, duplicar y eliminar. El menú omite Eliminar en viajes activos; el repositorio también lo rechaza. Borrado confirmado, con tarea conservada.
- Recorrido: administración reunida en el mismo menú; historial y datos adicionales permanecen en Opciones del viaje. Planificar regreso aparece discretamente después de finalizar.
- Seguimiento: una posición estimada y tiempo aproximado hasta el objetivo de la etapa. Menú contextual para detalle, destino, corrección, historial, tiempos, viajero, recuperación de notificación y finalización confirmada. Pausar y Llegué conservan su función propia; Iniciar etapa permite embarcar en la línea siguiente.
- Mapa: tren estático de 20 dp en un marcador de 24 dp, sin imagen de Persona. Centrado mediante icono accesible a la izquierda de la barra del mapa; pantalla completa reversible a la derecha. No se añade una animación ni una fuente GPS.
- Inicio: se retira el botón que duplicaba Líneas; los recientes abren directamente su viaje. Planificar utiliza Simular horario. El aviso de otro viaje activo remite a Viajes sin otro botón redundante.
- Contraste explícito de las tarjetas sobre superficie/onSurface; se conserva la paleta existente de estaciones.

## Etapas, precisión y notificaciones

Se reutiliza el motor segmentado existente: la estimación se detiene al alcanzar una combinación; Llegué confirma e inicia su cronómetro; Iniciar etapa reancla el avance en la siguiente línea. El tiempo de combinación permanece separado de la estimación de estaciones. Reapertura y restauración conservan fases, IDs, tiempos y vínculos.

Se corrigió una finalización verificablemente defectuosa fuera de RIDING: durante una combinación no cerraba la sesión. La finalización explícita conserva los tiempos ferroviarios y de combinación, el historial y su recuperación reversible. Se mantiene el contrato ARRIVED existente y sus restricciones de validación, sin cambiar el codec ni relajar su validación.

No se reprodujo un error matemático específico en Gruta de Lourdes: las pruebas comprueban la estación exacta en el límite de tiempo, ausencia de deriva, pausas, correcciones, reanudación, reloj monotónico y combinaciones prolongadas. El modelo continúa utilizando tiempos genéricos de 2 minutos por tramo; una desviación física de una estación requiere mediciones de dogfooding. No se alteran estaciones ni duraciones arbitrariamente, ni se presenta la estimación como ubicación confirmada.

La vibración previa ya existe: Inicio → Vibración previa. Se conserva su preferencia respaldable y el marcador persistido PASS_n, una vez por etapa, con supresión durante pausa o incertidumbre. El servicio registra antes de vibrar, utiliza el permiso VIBRATE existente y AudioAttributes.USAGE_NOTIFICATION; no sustituye las políticas Android/DND ni requiere permisos nuevos. La efectividad física de vibración depende del dispositivo y del sistema.

## Datos y compatibilidad

No se introducen tablas, migraciones, archivos privados ni preferencias nuevas. Room 36 y máximo JSON 28 permanecen iguales. Se preservan independencia, IDs y vínculos de Metro 1. Los viajes finalizados siguen guardándose automáticamente como sesiones del viaje; finalizar no elimina, duplicar no copia historial y borrar sigue siendo explícito. El regreso recalcula desde destino a origen mediante el planificador actual, sin invertir pasos ni iniciar automáticamente.

## Archivos de implementación

MetroScreen.kt, MetroTimeline.kt, MetroTracking.kt y el nuevo MetroTripPreview.kt. Pruebas dirigidas nuevas/adaptadas de comportamiento, conservación, servicio, etapas, menús, mapa, contraste, simulación y edición. No se modifican Personas, Proyecto, Sprint, seguridad, estaciones ni los siete PNG oficiales.

## Dogfooding pendiente

Comprobar legibilidad con el tema y fuente habituales, manejo con una mano, vibración con permisos/DND reales, pausa y reanudación tras apagar pantalla, combinación larga y regreso. Para investigar Gruta de Lourdes, anotar estaciones confirmadas y tiempos reales (incluidas pausas) frente a la estimación; no se ha accedido al teléfono. No se valida GPS porque la aplicación no lo utiliza.

## Validación final

79 casos dirigidos únicos aprobados entre ejecuciones: comportamiento Metro 2 (5), etapas (9), precisión (5), confirmación/alertas/Gruta de Lourdes (3), persistencia de etapas (7), independencia e interfaz Metro 1 (3), persistencia y respaldo Metro (15), servicio/notificaciones (3), interfaz de etapas (4), navegación y recuperación Metro (3), regreso (7), conflictos entre viajes (3), mapa/tren/texto grande (2), edición en pantalla pequeña (1), contraste (1), controles y destino (6) y simulación horaria (2).

`assembleDebug`: correcto. `git diff --check`: sin errores. Las huellas SHA-256 de los siete PNG oficiales coinciden con las anteriores. Las dos últimas pruebas de controles reubicados aprobaron y la compilación Debug final fue correcta. No se generó APK oficial, no hubo commit/push ni acceso al teléfono. Estas pruebas son JVM/Robolectric; no certifican la vibración física ni una validación nativa SQLCipher.
