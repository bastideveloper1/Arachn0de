# Arachn0de — Informe Fase A.1

## A. Resumen ejecutivo

Se recuperó la exportación pública de temas WebP estáticos y animados y la
compartición/exportación de APNG mediante la política común. Se preservan los
píxeles comprimidos, controles de animación y originales privados; se eliminan
metadatos opacos y arNd de las copias públicas. GIF conserva Plain Text válido.
La regresión de memoria se reprodujo antes de corregirla: ahora AAC fluye por
streams y las imágenes se preparan una a una con staging cifrado regenerable.
PDF/DOCX reciben directamente un bitmap público y lo codifican a PNG.

No se avanzó a Fase B ni a una auditoría general de rendimiento.

## B. Regresiones

| Problema | Estado | Evidencia |
|---|---|---|
| WebP estático | Corregido | Exportación/importación real de temas, MIME y hashes; píxeles idénticos con Pillow. |
| WebP animado | Corregido | ANIM/ANMF/payloads exactos; tres frames RGBA, alpha, 80/160/240 ms y loop 3 conservados. |
| APNG | Corregido | Proveedor real, ZIP JSON/Markdown y backup/restauración; CRC/zlib/controles y arNd probados. |
| GIF con extensiones | Corregido para Plain Text válido | GCE, frames, tiempos, loop y texto conservados; comentarios/extensiones opacas eliminados. |
| Memoria | Corregida la acumulación agregada | Reproducción previa y dos pruebas acotadas de retención; cancelación/fallos/limpieza. |
| ICC y dimensiones | Corregido dentro de límites explícitos | PNG de 20 MP sin conversión completa; orientación; ICC lineal 128→188 en Android y referencia LittleCMS. |
| Conversiones redundantes | Eliminadas en documentos | Bitmap directo desde política común, un PNG final; JPEG ordinario conserva datos comprimidos. |

## C. Arquitectura

`PublicImages.file` produce bytes y sus metadatos reales, distinguiendo estático y
animado. `prepare` mantiene la interfaz histórica. `render` solicita expresamente
una representación estática para documentos. `PublicPng` y `PublicWebp` son
validadores internos de esa misma política; ningún consumidor tiene un saneador.
Se reutilizan el limpiador JPEG, el parser TIFF y `ContentGif`.

Las fuentes privadas/editables y el backup conservan sus bytes. PNG/APNG se
publica por lista permitida con CRC y validación incremental de cada flujo zlib;
WebP conserva bitstreams/control y valida frames con decodificación muestreada.
Se reconstruye solo la orientación EXIF necesaria. ICC sRGB se elimina sin
recomprimir; otro ICC estático se convierte a sRGB con presupuesto de memoria.

El staging usa `cache/public-exports-v1`, archivos UUID `.enc`, streams cifrados
existentes y clave efímera en RAM. Las reservas `.lease` se sincronizan antes de
los derivados. No caducan por antigüedad. Completar/cancelar/fallar libera archivos;
los fallos de borrado mantienen el registro durable para reintentar al reiniciar.
Solo se eliminan derivados propios inactivos en directorios controlados. Producción
usa el bloqueo común. La caché tiene un techo de 512 MiB y queda fuera del backup.
No añade datos confirmados ni modifica su formato.

Los registros históricos de temas PNG/WebP con orientación 1 siguen validándose;
la exportación respeta la orientación que usaba su visor. Los nuevos registros
leen la orientación TIFF de forma uniforme, también antes de API 26.

## D. Seguridad

Las pruebas verifican ausencia de arNd y marcadores EXIF/XMP/texto/miniaturas
privados en salidas, conservación de los payloads públicos y fallos controlados
ante corrupción. El PDF real contiene la representación visible negra; no permite
recuperar el original rojo oculto de la fixture. No hay fallback al original.
Se comparan también los ciphertexts privados antes/después de exportar.

El round-trip de backup cifrado conserva todos los bytes de fuentes WebP, APNG,
PNG orientado y originales de temas; las validaciones y transacciones previas se
mantienen. El staging derivado no reemplaza ni libera fuentes. Las pruebas de
ciclo de vida cubren reservas simultáneas, reemplazo, cancelación, error de salida,
borrado fallido, recuperación y conservación de archivos desconocidos.

## E. Pruebas

Última tanda: **64/64 pruebas JVM/Robolectric aprobadas**, sin errores, fallos ni
omitidas, y **4/4 instrumentadas aprobadas** en Android 17/API 37. Compilación
de aplicación y pruebas correcta; se generaron únicamente los APK debug usados
en ese AVD. Las pruebas JVM cubren API 24 y 28.

El AVD usa la imagen Android 17/API 37 instalada, KVM y datos temporales nuevos;
no abre el userdata del Pixel_7a ni una bóveda personal, ni descarga componentes.
La prueba de proveedor usa ContentResolver/AttachmentExportProvider reales. La de
backup usa el cifrado y restauración existentes. El PDF usa PdfDocument/PdfRenderer.

La comparación externa abre 11 salidas sintéticas extraídas del emulador con
Pillow: todos los frames RGBA, dimensiones, alpha, duración, repetición y controles
APNG coinciden con las fuentes. El APNG con default excluido tiene una imagen
predeterminada y dos frames animados. La conversión ICC tiene además referencia
independiente LittleCMS: gris lineal 128→sRGB 188. Android pasa la comprobación
numérica en archivo y render. En esta fixture, Robolectric devuelve 128 en el
archivo y 188 en el render; Android devuelve 188 en ambos. La aserción numérica
del archivo se ejecuta en Android. JVM comprueba la conversión del render y la
estructura/dimensiones/alpha del archivo; no se atribuye esta diferencia a una
causa interna del simulador sin verificarla.

Evidencia local (solo fixtures sintéticas):

- `/tmp/arachnode-a1-tests/final-jvm-build.log` y XML en `jvm/`.
- `/tmp/arachnode-a1-tests/final-instrumentation.txt`.
- `/tmp/arachnode-a1-tests/frame-comparison.json`, `verify_frames.py`, `public/`.
- `/tmp/arachnode-a1-memory-before.json`, `/tmp/arachnode-a1-memory.json`.
- `/tmp/arachnode-a1-image-memory.json`, `/tmp/arachnode-a1-large-image.txt`.
- `/tmp/arachnode-a1-complete-build.log`, `/tmp/arachnode-a1-icc-build.log`.

Durante desarrollo falló deliberadamente la prueba que reproducía la acumulación
previa. Una ejecución adicional se detuvo por un error de la propia sonda: usaba
un delta negativo de heap como indicador de lectura pendiente y repetía GC. Se
corrigió con una bandera independiente y timeout de 120 s. La aserción ICC numérica
que fallaba en Robolectric se contrastó y aprobó en Android nativo. No se ocultan
esos fallos como resultados finales aprobados.

## F. Rendimiento

| Caso | Payload | Heap retenido al iniciar salida | Tiempo de esa ejecución |
|---|---:|---:|---:|
| AAC antes de corregir | 50,342,440 B | 68,422,592 B | 1082 ms |
| AAC después | 50,342,440 B | 12,584 B | 1168 ms |
| 24 PNG después | 37,774,056 B | 3,182,720 B | 3213 ms |

Estas medidas son heap **retenido al primer byte de salida tras GC**, no el pico
instantáneo, RSS ni un benchmark de velocidad. Un delta ligeramente negativo
representa ruido de GC. Las duraciones incluyen verificación y están afectadas
por compilación/emulación concurrentes; no demuestran una mejora de velocidad.

Los límites se justifican por el trabajo simultáneo: una fuente/una copia pública
por imagen, staging cifrado en disco y manifiesto pequeño; AAC usa buffers por
flujo. Los mapas inline históricos se acotan explícitamente a 8 MiB. Se validan
hash/tamaño de los bytes finales y se evita la delegación byte por byte del stream
ZIP. El caso de 20 MP valida scanlines con buffer de 32 KiB y renderiza muestreado,
sin bitmap completo para la salida de archivo sin conversión ICC.

## G. Archivos modificados

La lista corresponde al delta desde el inicio de **A.1**, no al diff global del
repositorio, que ya contenía trabajo previo conservado.

| Archivo | Motivo |
|---|---|
| `ARCHITECTURE.md` | Política común, límites, staging cifrado, reservas y recuperación. |
| `BACKUP_FORMAT.md` | Exclusión regenerable del staging y compatibilidad histórica; formato intacto. |
| `app/src/androidTest/assets/public-images/animated-default.png` | APNG con imagen predeterminada excluida y dos frames animados. |
| `app/src/androidTest/assets/public-images/animated.png` | APNG de tres frames con metadatos privados sintéticos. |
| `app/src/androidTest/assets/public-images/animated.webp` | WebP de tres frames, alpha, tiempos 80/160/240 ms y loop 3. |
| `app/src/androidTest/assets/public-images/icc.jpg` | JPEG sRGB con ICC y EXIF privado sintético. |
| `app/src/androidTest/assets/public-images/icc.png` | PNG sRGB con ICC y metadatos privados sintéticos. |
| `app/src/androidTest/assets/public-images/linear.png` | PNG gris 128 con ICC RGB lineal para referencia numérica 188 sRGB. |
| `app/src/androidTest/assets/public-images/ordinary.gif` | GIF convencional animado con comentario privado sintético. |
| `app/src/androidTest/assets/public-images/oriented.png` | PNG sintético con orientación EXIF 6. |
| `app/src/androidTest/assets/public-images/oriented.webp` | WebP sintético con orientación EXIF 6. |
| `app/src/androidTest/assets/public-images/static.webp` | WebP estático RGBA con EXIF/XMP privados sintéticos. |
| `app/src/androidTest/assets/public-images/text.gif` | GIF anterior con extensión gráfica Plain Text válida. |
| `app/src/androidTest/java/com/r0ybt/arachn0de/photos/PublicAnimationInstrumentedTest.kt` | Rutas reales de temas, proveedor APNG, ZIP, backup/restauración y color ICC numérico. |
| `app/src/main/java/com/r0ybt/arachn0de/Arachn0deApplication.kt` | Recuperación del staging al activar/mantener la bóveda, bajo el bloqueo común. |
| `app/src/main/java/com/r0ybt/arachn0de/document/DocumentReport.kt` | Bitmap público directo y codificación PNG sin JPEG intermedio. |
| `app/src/main/java/com/r0ybt/arachn0de/export/ElementExport.kt` | Procesamiento incremental, staging de imágenes, manifiestos públicos y escritura ZIP por bloques. |
| `app/src/main/java/com/r0ybt/arachn0de/export/PublicExportStaging.kt` | Staging regenerable cifrado, clave efímera, reservas durables, límite y limpieza recuperable. |
| `app/src/main/java/com/r0ybt/arachn0de/media/ContentGif.kt` | Conservación de Plain Text validado; eliminación de extensiones opacas. |
| `app/src/main/java/com/r0ybt/arachn0de/photos/PrivatePhoto.kt` | Acceso interno al saneamiento estructural JPEG y lectura TIFF de orientación WebP. |
| `app/src/main/java/com/r0ybt/arachn0de/photos/PublicImages.kt` | Fachada común file/render, metadatos públicos, perfiles ICC y presupuestos. |
| `app/src/main/java/com/r0ybt/arachn0de/photos/PublicPng.kt` | Validador y plan PNG/APNG central: CRC/zlib, controles, píxeles y eliminación de arNd. |
| `app/src/main/java/com/r0ybt/arachn0de/photos/PublicWebp.kt` | Validador y plan WebP central: RIFF, fotogramas, controles y metadatos mínimos. |
| `app/src/main/java/com/r0ybt/arachn0de/ui/ElementExportActions.kt` | Entrega del contexto privado al escritor incremental, sin cambio visual. |
| `app/src/main/java/com/r0ybt/arachn0de/ui/theme/ThemeDocuments.kt` | Fuentes saneadas una a una en staging cifrado; metadatos públicos coherentes. |
| `app/src/main/java/com/r0ybt/arachn0de/ui/theme/ThemeOriginals.kt` | Orientación PNG/WebP uniforme y lectura compatible de registros históricos con orientación 1. |
| `app/src/test/java/com/r0ybt/arachn0de/export/ElementExportTest.kt` | Adaptación de pruebas a contexto y sincronización de staging. |
| `app/src/test/java/com/r0ybt/arachn0de/export/PublicExportMemoryTest.kt` | Reproducción antes/después y paquete de 24 PNG; hashes, límites y sonda con timeout. |
| `app/src/test/java/com/r0ybt/arachn0de/export/PublicExportStagingTest.kt` | Cifrado, reservas compartidas, reemplazo, cancelación, fallos de borrado y recuperación. |
| `app/src/test/java/com/r0ybt/arachn0de/photos/PublicAnimationTest.kt` | Estructura/payloads exactos, APNG editable, orientación, ICC, API 24 y PNG de 20 MP. |
| `app/src/test/java/com/r0ybt/arachn0de/photos/PublicImageExportTest.kt` | Integración con staging y conservación de pruebas de seguridad previas. |
| `app/src/test/java/com/r0ybt/arachn0de/ui/theme/ThemeOriginalsTest.kt` | Compatibilidad de originales, backup e intercambio; caso histórico de orientación. |
| `docs/SECURITY_PHASE_A1.md` | Este informe, evidencia y lista completa de archivos de A.1. |

`PublicExportPdfTest.kt` ya existía y se ejecutó sin modificarlo.

## H. Riesgos residuales

- ICC animado no sRGB se rechaza controladamente: no se aplana ni se publica con color alterado.
- API 24/25 conserva saneamiento estructural ordinario, pero rechaza ICC. Hay pruebas Robolectric API 24/28 y Android nativo API 37; no se verificó un dispositivo físico API 24.
- Fuentes de hasta 20 MiB y lados de hasta 32000. APNG/WebP: hasta 1000 frames; PNG/APNG: hasta 512 MiB agregados de scanlines descomprimidas. Animaciones válidas que excedan estos límites pueden rechazarse.
- La conversión ICC estática completa requiere `8 × píxeles + 40 MiB ≤ mitad del heap máximo`; puede rechazar imágenes grandes. El render de documentos usa muestreo, hasta 1600 px por defecto y 2048 solicitados.
- Caché cifrada de 512 MiB y paquete público de 256 MiB. Si una limpieza no puede completarse y se alcanza el techo, la siguiente operación falla sin borrar fuentes.
- La fidelidad probada corresponde a las fixtures sintéticas y perfiles interpretados por Android; no es una garantía universal para todos los bitstreams/perfiles posibles.

## I. Confirmaciones

No se cambiaron versiones, dependencias, manifiesto, SQLCipher, Argon2id, AES-GCM,
contraseñas, Room ni el formato de backup. No se modificaron datos existentes,
interfaz visual ni módulos ajenos al alcance. Se preservaron los cambios previos.
No se hicieron commits, push, tags ni publicaciones. Se generaron únicamente APK
debug de aplicación y pruebas para el emulador; ningún APK de distribución.

La instancia temporal A1Synthetic, su servidor ADB del puerto 5047 y los procesos
Gradle de la tarea se cerraron. Se eliminaron únicamente el AVD temporal y la
caché de compilación aislada de esta tarea; se conserva la evidencia sintética
en `/tmp/arachnode-a1-tests`. No se modificó el userdata del AVD original.

Referencias de formato usadas para los validadores:
[WebP RIFF de Google](https://developers.google.com/speed/webp/docs/riff_container)
y [PNG/APNG, W3C PNG Third Edition](https://www.w3.org/TR/png-3/).
