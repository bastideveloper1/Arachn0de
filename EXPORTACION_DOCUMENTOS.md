# Informes PDF y Word desde Núcleos y Capas

La acción **Exportar PDF / Word** está en las opciones del Núcleo, de cada Capa
y del contexto abierto. Captura únicamente el elemento elegido y sus
descendientes, incluidos tareas completadas y pendientes y notas. No modifica
registros, adjuntos ni relaciones. Funciona sin red ni dependencias nuevas.

La captura transaccional conserva el orden del árbol, numeración jerárquica,
descripciones y formato, estado, prioridad, fechas, obligaciones, responsables,
etiquetas y tecnologías disponibles en los nodos. El elemento seleccionado ocupa
el título principal; no se repite como otro encabezado. Los archivos de audio y
sus marcadores automáticos se excluyen, incluso si falta el archivo físico del
audio. Las imágenes adjuntas se incluyen en su posición dentro de la descripción;
las que no tienen marcador se añaden al final de su sección.

## Uso

La vista superior muestra las páginas PDF reales y permite recorrerlas. El panel
inferior modular ofrece Documento (título, subtítulo, fecha ISO editable, índice y
portada), Imágenes (automática, izquierda, derecha y ancho completo) y Salida
(PDF o Word). La fecha inicial es la actual y la portada separada está desactivada.
Exportar abre el selector de carpeta SAF y crea un archivo con identidad única.
No sobrescribe documentos anteriores. El documento final es legible fuera del
almacén cifrado; esto se indica en la interfaz.

## Maquetación

PDF usa texto vectorial y páginas A4. El índice se calcula iterativamente con las
páginas definitivas de cada sección. La vista previa y el archivo usan el mismo
plan y dibujante. Las imágenes flotantes ocupan inicialmente un 30 % del ancho
útil, conservan proporción y reducen el ancho de las líneas solamente durante su
altura. El texto recupera todo el ancho inmediatamente debajo. Las imágenes
consecutivas y los cambios de sección despejan la anterior; no hay columnas ni
tablas simulando el ajuste. Ancho completo conserva proporciones y se acota a la
altura imprimible de una página.

DOCX contiene estilos reales Heading1–Heading9, marcadores, TOC/PAGEREF y campos
PAGE/NUMPAGES. Las imágenes flotan mediante `wp:anchor` y `wp:wrapSquare`, con
anclaje al párrafo; ancho completo usa `wp:inline`. Word vuelve a distribuir el
texto según su motor. La vista previa corresponde al PDF y no promete idéntica
paginación de Word. Se solicita actualizar los campos al abrir: hasta hacerlo,
las páginas del índice muestran «—», no números inventados. Más de nueve niveles
conservan la numeración completa y usan Heading9; la interfaz informa ese límite.
No se generan vínculos externos ni se interpretan textos del usuario como campos.

## Integridad, límites y liberación

La captura y lectura de adjuntos usan el bloqueo común de operaciones de archivos
y una transacción Room. Se comprueba tamaño y SHA-256 antes de normalizar cada
imagen, aplicando orientación y retirando el original editable incrustado en PNG.
Los límites explícitos son 5000 elementos, un millón de caracteres de títulos y
descripciones, 64 imágenes, 20 MiB por origen, 100 megapíxeles al decodificar,
1600 píxeles por lado normalizado, 32 MiB de imágenes normalizadas y 500 páginas.
Excederlos falla explícitamente; no se publica un informe truncado.

El contenido preparado vive en memoria y se libera al cerrar. Las opciones son
estado temporal de pantalla, no preferencias confirmadas. No hay archivos
privados de informes o imágenes normalizadas ni cambios de esquema o backup:
Room sigue en 48 y el lector JSON admite 1–40.

La salida SAF reutiliza `DiagramExportJournal` en el namespace cifrado independiente
`document-export-journal`, con bloqueo y reservas existentes. Registra la operación
antes de crear el archivo, confirma después de cerrar el stream y elimina solamente
su salida parcial en error o cancelación. Un fallo de eliminación conserva el
diario para reintentar al abrir Exportar PDF / Word, sin caducidad por antigüedad.
Los documentos confirmados pertenecen al usuario y nunca se eliminan por limpieza.
No se reemplazan adjuntos, no se liberan referencias compartidas originales y no
se barre el almacenamiento. El diario operativo se excluye del respaldo; los
datos originales y sus bytes siguen en el backup oficial sin cambios.

## Archivos y validación

Producción: paquete `document/` (captura, modelo, maquetación, PDF, DOCX, SAF y
diálogo), integraciones en `ProjectComponents.kt`, `NodeComponents.kt` y
`ProjectScreen.kt`, y namespace del diario en `DiagramExportJournal.kt`.
Pruebas: `document/ReportSourceTest.kt`, `ReportLayoutTest.kt`, `ReportUiTest.kt`,
caso de documentos en `DiagramExportTest.kt` y espera del cierre asíncrono en
`CoreBottomModuleTest.kt`. Esta última conserva la expectativa original y espera
al callback de guardado, en lugar de confundir el commit Room con el cierre visual.

La selección dirigida incluye documentos, exportadores de origen, panel de Núcleo,
diario SAF y temas personalizados. Comprueba jerarquía, alcance, numeración,
índice, disposición real de líneas, proporciones, portada, exclusión de audios,
inmutabilidad de originales, XML/ZIP DOCX, navegación, validación de fecha,
cancelación, escritura fallida y recuperación de borrados pendientes preservando
archivos ajenos. No se ejecuta una regresión global.

Una selección inicial demasiado amplia de `export.*` también ejecutó cinco casos
antiguos de `NodeCopyUiTest`/`ProjectCopyUiTest` con expectativas de menús/esquema
anteriores. Fallaron y no se modifican en esta tarea; la selección final se limita
a los exportadores pertinentes. Robolectric no implementa el PdfDocument nativo;
no se sustituye el escritor por un simulacro para declarar validado un PDF.

`app/src/test/native/report/NativeReportSmoke.java` permite verificar el escritor
real con datos sintéticos en un proceso Android independiente, sin APK, base de
datos ni acceso a contenido del usuario. La apertura de DOCX se comprueba también
con LibreOffice. Queda pendiente inspección en el teléfono con su proveedor SAF y
en Microsoft Word, especialmente actualización del índice, fuentes y saltos.

Se mantiene **0.6.0/code20**. El usuario decidió dejar el empaquetado pendiente:
no se genera APK, no se modifica el helper, no se sustituye la entrega existente
y no se realiza commit ni push.

### Resultado final de validación

`./gradlew --offline :app:compileDebugKotlin :app:testDebugUnitTest` con los
selectores dirigidos anteriores: **41 pruebas, 0 fallos, 0 errores**; compilación
correcta. Incluye un título de índice que cruza páginas para conservar el número
al lado de su primera línea y tecnologías del Núcleo raíz. `git diff --check` limpio.

La fixture sintética del escritor nativo abrió y renderizó todas sus páginas con
Android `PdfRenderer` en el emulador API 37: automática 9, izquierda 9, derecha 9
y ancho completo 16 (**43 páginas**). Poppler abrió los cuatro PDF, verificó las
12 referencias del índice de cada uno, pie de todas las páginas y acentos. Se
inspeccionó visualmente la recuperación inmediata del ancho bajo la imagen.
Los cuatro DOCX abrieron en LibreOffice 24.2 y se convirtieron a PDF (11, 11, 11
y 19 páginas respectivamente), conservando imágenes, texto y encabezados. Esta
diferencia confirma por qué la interfaz distingue la paginación de Word.

Las evidencias sintéticas están en `/tmp/arachnode-report-native-output/`; logs
en `/tmp/document-final-verification.log`, `/tmp/document-native-final.log` y
`/tmp/document-word-open-final.log`. Son evidencia temporal de desarrollo, no
contenido del usuario ni archivos requeridos para backup o funcionamiento.


## Revisión de imágenes, vista previa e índice

La normalización ya respetaba orientación EXIF y relación de aspecto. La
maquetación limita ambas dimensiones al área útil y dibuja la imagen completa,
sin recorte de origen. Se verificaron imágenes horizontales, verticales y
cuadradas en las cuatro disposiciones, alineación y los cuatro cuadrantes de
color en el renderer nativo. No se cambió la maquetación ni la salida PDF/DOCX.

El índice ya utilizaba exclusivamente numeración y título de ReportSection, con
su referencia de página. Una prueba adicional comprueba que descripciones y
metadatos aparecen en el cuerpo pero no en el índice. No se altera su formato.

La vista previa carecía de zoom y desplazamiento táctil. Ahora reutiliza
rememberTransformableState/transformable del visor y VerticalPager de Antisocial:
pinza de 1× a 5×, desplazamiento limitado al área ampliada y paso vertical de
páginas a 1×. Se conservan Anterior/Siguiente. Cambiar de página o de plan vuelve
al encuadre inicial. El texto se dibuja con el plan PDF a la escala actual, sin
ampliar una captura rasterizada. Los gestos no alteran opciones ni datos fuente.

Validación: compileDebugKotlin y 12/12 pruebas dirigidas aprobadas: ReportLayoutTest
(7), ReportSourceTest (2) y ReportUiTest (3), incluida pinza real, desplazamiento y
cambio de página. Log: /tmp/report-pdf-verified.log. La primera prueba sintética
usaba posiciones de dedos en orden incorrecto; se corrigió para ejecutar una
pinza y la ejecución final pasa completa. Validación táctil en teléfono pendiente.
