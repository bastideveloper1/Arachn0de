# Guardar con descendientes — PNG individuales

## Comportamiento

La opción aparece en **Compartir y exportar → Imagen** únicamente para Núcleos y
Capas con descendientes reales. La disponibilidad consulta los registros completos,
no las listas filtradas de la pantalla. Tareas, Notas y contenedores vacíos no
ofrecen la opción. No se alteran sus datos ni sus estados.

Se elige una carpeta Android SAF **una sola vez**. Se guarda primero el origen y
luego cada descendiente en el preorden de `TaskPlainTextRenderer.branch`, el mismo
que «Copiar con descendientes», incluyendo completadas, Notas y Capas intermedias.
No se aplica el filtro visual ni la selección de alcance de Markdown/JSON.

Cada elemento produce un PNG independiente. Los nombres llevan ordinal con ceros,
título saneado y UUID propio, por ejemplo:
`0001-Mi Capa-tarjeta-<UUID>.png`. No se reutilizan nombres ni se reemplazan archivos
previos. No hay ZIP, PDF ni imagen que reúna varios elementos. El selector de carpeta
es la adaptación necesaria del guardado Android de un documento a varios; no se
abren selectores de nombre por cada PNG.

La acción individual conserva `rememberCardImageCommands`, `CreateDocument`,
`CardShareFiles` y su renderizador. El lote llama al **mismo `CardShareRenderer` y
`ObligationReportFiles.writePng`**. Para los nodos se reutiliza exactamente la
proyección de `CardShareSource`, ahora también vinculable a una sesión concreta.
El origen Núcleo usa sus propios datos, imágenes referenciadas y fotografía con el
mismo modelo `CardShareData`; no hay un segundo renderizador. Los adjuntos se
pintan realmente con la misma escala, proporciones y legibilidad del guardado
individual, sin sustituirlos por identificadores o miniaturas nuevas.

## Seguridad y recursos

- La sesión que pidió el destino queda fijada por identidad. Se comprueba admisión
  antes y durante cada elemento, cada escritura y la confirmación del archivo.
  Cambiar o bloquear el almacén no permite continuar adquiriendo sus datos.
- Los metadatos y la carga de imágenes se leen bajo el mutex común. Los datos se
  obtienen de la sesión capturada, sin seguir el siguiente almacén de la aplicación.
- Se procesa **un solo elemento y un solo PNG por vez**, fuera del hilo principal.
  El renderer existente también decodifica las fotografías de una en una.
- El PNG se comprime directamente al `PublicExportStaging` cifrado de A.1, con
  clave efímera en RAM y el mismo formato. No se conserva otra copia PNG en RAM
  ni un temporal privado en texto claro. La extensión por streaming admite el
  límite existente de 64 MiB por tarjeta; se conserva el límite global de staging
  de 512 MiB y el límite del renderer de 1080×8192.
- El mutex de archivos se libera antes de crear, consultar, borrar o escribir
  documentos SAF. Las llamadas a otra aplicación no retienen ese mutex ni la
  clave principal para sostener la entrega. El staging se cierra y su clave se
  borra al finalizar o fallar cada elemento; una eliminación local fallida deja
  la reserva durable existente para reintento al autenticar.
- Los originales solo se leen. No se descargan originales ocultos ni se adjuntan
  sus bytes al PNG. Se conserva la política de representación pública actual.

## Fallos, cancelación y recuperación

El diario existente `DiagramExportJournal` incorpora un modo de tarjetas en
`files/card-image-export-journal`, cifrado con la infraestructura existente. Cada
nombre único se registra antes de pedir la creación SAF, cerrando la ventana entre
creación remota y registro de su URI. Si se interrumpe allí, se busca ese nombre
exacto en la carpeta autorizada. No se barre ni se elimina el resto del destino.

Un archivo se confirma solo después de terminar, cerrar y comprobar la sesión.
Los PNG ya confirmados pertenecen al usuario y se conservan si falla uno posterior.
Se retira únicamente el archivo parcial actual y se muestra el número de PNG
completos guardados, dejando explícito que **el lote no terminó**. Si el proveedor
rechaza eliminarlo, se informa y se conserva su diario para reintentar al abrir
Compartir y exportar. También se informa del fallo cuando el proveedor creó el
archivo pero lanzó un error antes de devolver su URI.

«Cerrar» cancela el lote; cancelar el selector no crea archivos. Cambiar de pantalla
o cerrar sesión también cancela el trabajo de la composición. La limpieza privada
se ejecuta en un bloque no cancelable. Las reservas no caducan por antigüedad.

Los proveedores externos pueden rechazar operaciones, perder permisos, bloquear
E/S o no respetar su contrato. No se promete una transacción atómica para todo el
lote ni eliminación forense. Tras terminar el proceso puede quedar un PNG parcial
hasta volver a autenticar y abrir el menú para recuperar su diario. Los archivos
completos anteriores siguen disponibles individualmente; no se presenta ese estado
como éxito del conjunto. Los campos de cada PNG se leen al generarlo: si un elemento
se elimina o queda incompleto durante la operación, se informa del fallo.

## Persistencia y compatibilidad

No se añaden tablas, columnas, preferencias ni versiones de backup. Las fuentes
confirmadas continúan respaldándose con sus bytes y relaciones existentes. El
staging y el diario son estado operativo regenerable, excluido del snapshot lógico;
los PNG finales pertenecen al destino SAF del usuario, no al backup de la aplicación.
No cambian SQLCipher, claves, contraseñas, bloqueo, modo Manual ni recientes.

## Archivos modificados

- `app/src/main/java/com/r0ybt/arachn0de/ui/ShareExportPanel.kt`: opción en el menú y
  estado/cancelación del lote con los componentes existentes.
- `app/src/main/java/com/r0ybt/arachn0de/ui/CardTreeImageCommands.kt` (nuevo):
  disponibilidad, selector único, sesión fijada y resultado del lote.
- `app/src/main/java/com/r0ybt/arachn0de/report/CardTreeImages.kt` (nuevo): recorrido,
  render secuencial, staging y entrega de cada PNG.
- `app/src/main/java/com/r0ybt/arachn0de/report/CardShareSource.kt`: reutilización de
  la proyección de nodos con sesión fijada y origen Núcleo.
- `app/src/main/java/com/r0ybt/arachn0de/export/PublicExportStaging.kt`: entrada por
  streaming acotado, conservando la API de bytes y el cifrado de A.1.
- `app/src/main/java/com/r0ybt/arachn0de/diagrams/DiagramExportJournal.kt`: modo PNG
  con nombres ordenados y llamadas SAF fuera del mutex; modos anteriores conservados.
- `app/src/test/java/com/r0ybt/arachn0de/report/CardTreeImagesTest.kt` (nuevo) y
  `app/src/test/java/com/r0ybt/arachn0de/ui/ShareExportPanelUiTest.kt`: pruebas.
- `ARCHITECTURE.md`, `BACKUP_FORMAT.md` y este informe: contrato y alcance.

## Verificación

**23/23 pruebas dirigidas aprobadas**, `testDebugUnitTest` offline, Robolectric API
28 y gráficos nativos para los PNG:

- Siete pruebas nuevas del lote: preorden completo de Núcleo/Capa, exclusión de
  otras ramas, rechazo de hojas, archivos independientes, 21 PNG sin agotar la
  caché de compartición, repetición de nombres, cancelación, disco lleno, borrado
  fallido, recuperación por nombre tras perder la URI y revocación durante la entrega.
- Comparación píxel a píxel del PNG de una tarea con su guardado individual. Tiene
  tres adjuntos reales rojo/verde/azul y se comprueba también que cada imagen ocupa
  una superficie legible, que sus bytes originales no cambian y que las filas
  de nodos permanecen intactas.
- Dos pruebas de menú: flujo individual existente y disponibilidad del nuevo
  componente para Núcleo/Capa frente a contenedores vacíos y hojas.
- Ocho pruebas existentes de `CardShareFilesTest`, tres de `PublicExportStagingTest`
  y tres del diario anterior: cifrado, reservas, recuperación y no eliminación de
  archivos ajenos.
- El proveedor SAF sintético comprueba que crear, abrir, consultar y eliminar
  documentos ocurre sin poseer el mutex común. La prueba de bloqueo confirma
  denegación de claves y retirada del staging.

La primera ejecución amplia incluyó adicionalmente la prueba de exportación de
Temas. Falló con `ShadowLinux.open`/EIO al sincronizar un directorio en Robolectric,
en el constructor de staging **idéntico al anterior a esta tarea**, antes de llamar
al nuevo método de streaming. `ThemeDocuments.kt` y esa prueba no se modificaron
ni deshabilitaron. Se conserva el fallo y no se declara aprobada toda esa clase ni
la suite completa. La limpieza del nuevo fixture se ajustó para no esperar jobs de
media sobre el looper principal pausado de Robolectric; no cambia el cierre real.

Evidencias locales: `/tmp/arachnode-card-tree/final-tests.log`, XML en `jvm/`,
`verification.json`, `tests.log` (ejecución inicial) y `threads.txt`.
No se realizó una prueba en dispositivo/proveedor externo real: no se generó APK,
según lo solicitado. Se verifican hashes y fechas de los APK anteriores para
confirmar que permanecen intactos. Sin cambios de versiones, commit o push.

## Ficha visual de referencia (27 de octubre de 2026)

Exportación individual y lote comparten la ficha de 1080 px: título, proyecto,
estado cuando corresponde, creación/asignación/vencimiento/etiquetas, descripción
y referencias a todo el ancho interior (976 px), con sus nombres/formato/tamaño.
Las imágenes no compactas mantienen la proporción sin el antiguo límite de 720 px
de alto. La decodificación de adjuntos se ajusta al ancho visual, incluida su
orientación, para no degradar capturas verticales por su altura. Personas y
tecnologías conservan sus miniaturas separadas. No se modifican originales.
Se mantiene el límite de 8192 px por PNG; si no cabe la ficha completa se rechaza
la generación en lugar de truncarla o reducir las referencias a miniaturas.
No cambia almacenamiento, formato de backup ni el mecanismo seguro de entrega.
Validación dirigida: 16 comprobaciones aprobadas de los flujos y decodificación;
una prueba nueva de referencia vertical aprobada después de ajustar la tolerancia
al trazo del borde (4 px). Compilación correcta; vista sintética inspeccionada.
Pendiente revisar capturas reales y legibilidad en el teléfono/destinatario.
