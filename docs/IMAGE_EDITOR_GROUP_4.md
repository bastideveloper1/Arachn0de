# Grupo 4 — Editor de imágenes

## 1. Último color y grosor

### Causa y alcance previo

`ImageAnnotationWorkspace` usaba `remember` para color, grosor y herramienta.
Cambiar de herramienta dentro de la misma composición ya conservaba los dos
ajustes; recrear o cerrar/reabrir el editor los devolvía a rojo y .006.
Además, las filas de Color y grosor se componían condicionalmente: su
`rememberScrollState` se destruía al cerrar ese panel, perdiendo su posición.

### Implementación

- Color, grosor y herramienta usan estado guardable durante la recreación.
- Los dos ajustes se leen/escriben mediante las preferencias privadas existentes
  del almacén: `image_annotation_style`. Se conservan al cerrar, cancelar la
  edición o reabrir otra imagen; no dependen del guardado del dibujo.
- Lectura y escritura ocurren fuera del hilo principal. Una exclusión mutua
  ordena los cambios y la lectura de una reapertura inmediata; las escrituras
  pequeñas ya solicitadas terminan al cerrar el editor. Los errores usan la
  presentación de error existente y no escriben imágenes.
- Los estados de desplazamiento de las dos filas viven fuera del bloque
  condicional. Cerrar/reabrir Color y grosor dentro del editor conserva su
  posición; no se promete persistir ese desplazamiento entre ediciones distintas.
- Los colores y grosores siguen siendo los seis y tres valores existentes. Cada
  marca mantiene su estilo inmutable; cambiar ajustes afecta a trazos nuevos.

Estado: implementado. Pendiente verificación física de reapertura y recreación
con la sesión privada real, así como accesibilidad táctil con distintas fuentes.

### Persistencia, respaldo e integridad

Se leyó el contrato de `ARCHITECTURE.md` y el transporte de preferencias en
`BACKUP_FORMAT.md` antes de intervenir. La fila es dato confirmado del almacén,
incluida automáticamente en el snapshot/exportación/restauración actuales de
`private_preferences`; no existe almacenamiento paralelo.

`AnnotationStylePreferences` valida claves, tipos, paleta y grosores. La validación
de backup aplica ese mismo contrato antes de reemplazar datos. Los valores
históricos por defecto se usan cuando faltan fila o claves. Los lectores antiguos
ya admitían preferencias privadas genéricas; no cambia la versión de app, JSON,
Room, cifrado ni contenedor. No se añaden archivos, imágenes, rutas, adjuntos ni
relaciones; no hay nuevos recursos físicos que requieran liberación. Se conserva
el ciclo de vida existente de originales, derivados y reservas del editor.

## 2. Lupa de precisión

### Causa y solución

El escenario dibujaba directamente bajo el contacto, sin referencia desplazada.
Se añade una lupa temporal de 96 dp, ampliación 2× y retícula monocromática, sobre
el propio lienzo. Utiliza la misma imagen, viewport y función de dibujo de marcas,
incluido el trazo en curso. No crea otro bitmap de la imagen ni añade dependencias.

La referencia se sitúa por encima del contacto cuando hay espacio, y por debajo
en la zona superior; se mantiene dentro del escenario. No aparece si el área es
demasiado pequeña. Se muestra al comenzar un arrastre de dibujo válido y sigue
su punto normalizado. Desaparece al finalizar, cancelar o cambiar herramienta,
estilo, modo, disponibilidad o imagen. No captura gestos adicionales: selección,
pan y transformaciones conservan sus manejadores existentes y no muestran lupa.
Las cuatro herramientas de la interfaz mantienen sus acciones.

La imagen base y las marcas se siguen renderizando por la misma ruta. La lupa es
un segundo dibujo interactivo recortado; no modifica la geometría ni pertenece
al historial, al codec de marcas, a `markedPng` o a la exportación. Guardar conserva
el renderizado de tamaño completo y el reemplazo existentes. Los originales y
PNG históricos no se convierten ni se reescriben por abrir el editor.

Estado: implementado. Pendiente comprobación física de precisión bajo el dedo,
posicionamiento cerca de bordes, cancelación al interrumpir la app y fluidez con
imágenes grandes/muchas marcas. Las pruebas automatizadas no validan la ergonomía
del dedo ni el rendimiento de GPU de un dispositivo real.

## Archivos modificados

Producción, bajo `app/src/main/java/com/r0ybt/arachn0de/`:

- `annotations/AnnotationStylePreferences.kt` (nuevo): ajustes tipados y validados.
- `ui/ImageAnnotationEditor.kt`: estado guardable, preferencias y desplazamiento
  retenido de los controles de estilo.
- `ui/ImageAnnotationStage.kt`: lupa y limpieza del estado temporal de interacción.
- `backup/BackupData.kt`: validación de la fila nueva en el transporte existente.

Pruebas: `ui/ImageAnnotationUiTest.kt` y
`security/PrivatePreferencesBackupTest.kt` bajo `app/src/test/java/com/r0ybt/arachn0de/`.
Documentación: este informe, `ARCHITECTURE.md` y `BACKUP_FORMAT.md`.

## Validación focalizada

Seis métodos distintos aprobados:

1. `fourToolsCommitOneMarkPerGestureWithoutSelectionOutline`: las cuatro
   herramientas conservan un commit por gesto y no recuperan el contorno auxiliar.
2. `compactToolsStyleUndoAndCancelKeepOriginalBytes`: herramientas, estilo,
   Deshacer y Cancelar; bytes originales e importación pendiente conservados.
3. `styleSurvivesToolsPanelAndImmediateCloseReopen`: color/grosor entre herramientas,
   desplazamiento del panel retenido y cierre/reapertura inmediato con últimos
   valores y original sin cambios.
4. `precisionIsTemporaryAndNewStylesDoNotChangeExistingMarksOrExport`: lupa visible
   durante arrastre y oculta al terminar/cancelar, comprobación de píxeles de la
   referencia en captura del lienzo, marcas previas inmutables, exportación sin
   retícula/fondo y pan sin modificar trazos ni mostrar lupa.
5. `saveReturnsOneFlattenedReplacementWithoutOpeningExporter`: guardado real,
   dimensiones completas, un reemplazo plano, contenido anotado y original del
   borrador todavía protegido.
6. `annotationStylesRestoreInEmptyStoreAndRejectInvalidOrFailedReplacement`:
   snapshot y round trip, recuperación en base vacía, lectura de los valores
   restaurados, rechazo de tipos/paleta/grosor/claves inválidos, rollback real
   mediante fallo SQL durante restauración y defaults históricos sin preferencias.

JVM/Robolectric, sin dispositivo conectado. Se repitieron solo las dos pruebas
nuevas que necesitaban ajustar su fixture: ejecutar la limpieza de Room en IO y
esperar el frame de dibujo para capturar la lupa. No fue necesario cambiar
producción para esas expectativas. `git diff --check` sin errores; sin suites
completas ni APK. No se modifica el editor de descripciones ni su prototipo.
