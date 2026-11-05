# Wireframe y Diagramas: unificación visual, fase 1

## Resultado

Ambas bibliotecas utilizan el mismo encabezado, galería compacta, selector cuadrícula/lista, miniaturas reales, menú contextual y botones con borde, icono y nombre para «Lienzo vacío» y «Crear desde plantilla». «Mis diseños» tiene prioridad. La cuadrícula muestra dos columnas en teléfonos habituales, tres desde 600 dp y una en anchuras menores de 300 dp; la lista utiliza miniaturas laterales.

Las plantillas originales se presentan en una galería secundaria. Wireframe conserva sus categorías. No se añadieron plantillas ni se cambiaron sus contenidos. La inserción de una plantilla en un documento nuevo de Wireframe conserva una única operación de historial. El borrador mantiene su contenido y formato al restaurar el estado de la pantalla.

Wireframe conserva renombrar, duplicar, exportar PNG y eliminar con confirmación. Diagramas conserva abrir, guardar copia y eliminar con confirmación; la biblioteca de imágenes permanece accesible mediante su botón propio. No se añadieron operaciones inexistentes para igualar las herramientas.

Diagramas utiliza una barra lateral con mostrar cuadrícula, imán, ajuste a cuadrícula y encuadre. Guardar, exportar, deshacer, rehacer y reproducción están abajo. Las herramientas principales de ambos editores comparten botones con icono y etiqueta, de 88 dp de ancho y al menos 56 dp de alto, con desplazamiento horizontal. Los encabezados de panel mantienen controles compactos de 48 dp para evitar saturación en 320 dp. Se conservan selección, categorías, minimización y expansión.

## Reutilización y compatibilidad

Las vistas previas utilizan `WireframeRenderer` y `DiagramRenderer`, los mismos motores del editor/exportación. Las miniaturas de las bibliotecas tienen un lado mayor de 240 px, respetan la proporción y utilizan `ContentScale.Fit`. Se generan fuera del hilo principal, con dos trabajos simultáneos como máximo y caché RAM de 6 MiB. Ante un fallo aparece un marcador y el documento continúa disponible. No se crean archivos de caché ni nuevas referencias a adjuntos; las miniaturas no requieren bytes adicionales en el respaldo.

La preferencia independiente de cada herramienta usa el almacenamiento cifrado genérico existente (`visual_gallery_preferences`, booleanos `wireframe` y `diagrams`). Se exporta y restaura mediante `privatePreferences`, con validación previa al reemplazo. Si falta en un respaldo histórico se usa cuadrícula. No cambia Room ni el formato del respaldo.

Se inspeccionó la condición compartida que oculta la navegación global en Diagramas, Wireframe, Metro, Tragaperras y Antisocial; ya estaba corregida y se conserva. No se repitió la prueba de navegación de cinco herramientas previamente superada.

## Archivos del cambio

- `visual/ToolLibraryUi.kt`: galería, encabezado, botones, miniaturas, caché y lectura/escritura de la preferencia.
- `wireframe/WireframeScreen.kt`, `wireframe/WireframeVisualTools.kt`: biblioteca, plantillas y controles compartidos.
- `diagrams/DiagramScreen.kt`, `diagrams/DiagramTools.kt`: biblioteca, miniaturas nativas, barra lateral y panel inferior.
- `domain/model/ToolGalleryPreference.kt`, `backup/BackupData.kt`: identificación y validación de la preferencia opcional.
- `ARCHITECTURE.md`, `BACKUP_FORMAT.md`: compatibilidad y ciclo de vida de las miniaturas.
- Pruebas dirigidas en `WireframeUiTest`, `DiagramUiTest`, `ToolPreviewTest` y `ToolGalleryBackupTest`.

## Revisión visual pendiente

Revisar en Pixel 7a y con fuentes ampliadas el equilibrio entre lienzo y paneles, el desplazamiento de las categorías y las miniaturas con documentos reales extensos. Las pruebas de escritorio no sustituyen esa revisión física. No se generó APK oficial ni se modificaron versión, firma, motores geométricos, plantillas, colores de documentos o skins.

## Validación realizada

Compilación de Kotlin de depuración y de las pruebas correcta. Trece comprobaciones focalizadas superadas (sin ejecutar suites completas):

1. Preferencia exportada/restaurada, rechazo de tipos corruptos antes de reemplazar datos y respaldo histórico sin preferencia (`ToolGalleryBackupTest`).
2. Miniaturas verticales/horizontales de ambos renderizadores, dimensiones acotadas, caché reutilizada y documento intacto (`ToolPreviewTest`).
3. Galería vacía y con cinco diseños, dos columnas, alternancia a lista y apertura aunque falle la miniatura.
4. Wireframe: renombrar, exportar/cancelar, duplicar, cancelar/confirmar eliminación, abrir, plegar/desplegar y conservar vista de lista.
5. Wireframe: plantilla de escritorio, restauración de borrador y guardado en formato nativo.
6. Wireframe: inserción completa con deshacer/rehacer y edición individual sin perder elementos.
7. Wireframe: firma compartida entre exportación desde biblioteca/editor y cancelación del selector de archivo.
8. Wireframe: arrastrar, redimensionar y cancelar sin commits adicionales.
9. Diagramas: plantilla, barra lateral, plegado, copia, confirmación/cancelación de borrado, guardado y reapertura sin perder nodos/conexiones.
10. Diagramas: botones accesibles, selección, estados deshabilitados, desplazamiento horizontal y panel dentro de los límites en 320 dp.
11. Diagramas: selección/arrastre sin desplazar la cámara ni cambiar tamaños involuntariamente.
12. Diagramas: selección múltiple, alineación y estados independientes de cuadrícula, ajuste e imán.
13. Diagramas: prueba existente de movimiento, redimensionamiento, guías e historial conservando conexiones estables.

Durante la adaptación se corrigió la saturación del encabezado del panel en pantallas estrechas. Las pruebas antiguas se adaptaron a los nuevos nombres, menús y desplazamiento horizontal; los fallos de selectores detectados quedaron resueltos. Las comprobaciones finales afectadas terminaron con `BUILD SUCCESSFUL`.
