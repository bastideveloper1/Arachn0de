# Corrección posterior a v0.22.0

El destacador se retira del editor y de sus gestos. Sus marcadores históricos se
reconocen únicamente para no mostrarlos; el texto almacenado permanece intacto
y ya no se emite formato amarillo. Negrita, cursiva, subrayado, títulos, listas,
enlaces y adjuntos conservan sus funciones. Código: fuente monoespaciada de
0.85 em, fondo temático redondeado y sangría visual compacta, sin modificar
espacios, tabulaciones ni saltos originales.

Los apartados siguientes describen la implementación histórica.

# Contenido: destacador, título, código y GIF

## Cambios implementados

- Barra compartida: conserva Negrita, Cursiva, Subrayado, Imagen y los controles
  de Audio en los contextos donde ya estaban disponibles. Añade solo
  Destacador, T⁺ y </>, con objetivos táctiles de al menos 48 dp.
- Destacador: modo explícito con estado visible; un toque cambia el destacado
  de una palabra Unicode completa. Otro toque lo elimina. Las referencias de
  adjuntos son atómicas y no se formatean accidentalmente por ese gesto.
  Amarillo único y texto negro mantienen contraste en todos los temas.
- El mismo destacador funciona en el visor interior del Núcleo/Capa/Tarea/Nota.
  Usa las escrituras existentes con comprobación de la descripción original;
  una edición concurrente produce un error y no sobrescribe texto nuevo.
- Título: selección existente, un único tamaño de 1.2 em y negrita, reversible.
- Código: selección en línea o de varias líneas, monoespaciado y fondo discreto
  con contraste fijo; conserva espacios, tabulaciones y saltos. El visor permite
  seleccionar y copiar cuando el destacador está desactivado.
- La correspondencia entre texto visible y fuente oculta el marcado sin perder
  selección, cursor ni referencias. El borrado elimina delimitadores huérfanos
  solo de pares que existían antes del cambio; no barre puntuación literal.
- Imagen acepta GIF con el selector/importador existente. Se conserva el archivo
  original y se anima al abrir su visor, con proporciones originales, pausa al
  salir de RESUMED y sin nuevos archivos temporales descifrados. La vista deja
  de solicitar fotogramas cuando no es visible y libera su referencia al salir.
- No se cambian fotografías de Antisocial ni las reglas de otros módulos.

## Persistencia, recuperación y exportación

No hay columnas ni migración Room. Descripciones y adjuntos conservan sus
mecanismos de cifrado. JSON 55 identifica GIF o formatos ampliados y admite
lectura histórica 1–54. Se usa el namespace reservado de marcadores dentro de
la descripción; no existen offsets persistidos separados que puedan desfasarse.
Ver BACKUP_FORMAT.md para la gramática y los límites.

El backup incluye todos los bytes GIF y verifica estructura, tamaño, SHA-256 y
metadatos antes del reemplazo. Se conserva el staging durable y la transacción
existentes. Importación cancelada, reemplazo, referencias compartidas y limpieza
DELETE_PENDING siguen el ciclo de vida de AttachmentRepository.

La exportación individual y los paquetes Markdown/JSON conservan el GIF.
PDF/DOCX reciben una representación estática PNG, manteniendo el original
cifrado y recuperable. Los estilos nuevos se representan en Markdown mediante
HTML, en DOCX mediante propiedades de ejecución y en el dibujo de PDF.

## Archivos de producción modificados o añadidos

Rutas relativas a app/src/main/java/com/r0ybt/arachn0de/:

- domain/model/DescriptionParser.kt
- domain/model/DescriptionHighlight.kt (nuevo)
- ui/state/EditorDraft.kt
- ui/state/AttachmentDraftEditing.kt
- ui/AttachmentDescriptionEditor.kt
- ui/AttachmentDescriptionField.kt
- ui/AttachmentText.kt
- ui/DescriptionHighlightTool.kt (nuevo)
- ui/ProjectScreen.kt
- ui/AnimatedAttachment.kt (nuevo)
- ui/AttachmentViewer.kt
- media/ContentGif.kt (nuevo)
- data/local/AttachmentStore.kt
- backup/BackupData.kt
- backup/BackupJson.kt
- backup/BackupAttachmentFiles.kt
- export/ElementExport.kt
- document/ReportLayout.kt
- document/ReportDocx.kt

Documentación: ARCHITECTURE.md, BACKUP_FORMAT.md y este documento.

## Validación

Compilación principal y de pruebas correcta, sin generar APK. 37 pruebas
distintas aprobadas entre las tandas dirigidas, sin suite general:

- ContentFormattingTest: 5 (palabras Unicode, otros formatos, toggles,
  indentación, inserción/borrado y limpieza de delimitadores).
- DescriptionParserTest: 8 (gramática anterior, escapes, anidación y referencias).
- AttachmentEditingStateTest: 13 (todos los formatos, selección, borradores,
  etiquetas y correspondencia de offsets).
- ContentFormattingUiTest: 2 (toques reales y activación en visor/editor).
- ContentGifPlaybackTest: 2 (estructura y dibujo/temporización de dos fotogramas,
  pausa y liberación).
- ContentGifBackupTest: 3 (original cifrado y backup cifrado/restauración para
  Núcleo/Capa/Tarea/Nota; cancelación/reemplazo/referencias compartidas/limpieza
  fallida; corrupción rechazada sin reemplazar el contenido existente).
- AttachmentBackupTest: 2 métodos (PNG/JPEG originales y restauración v9).
- ContentFormatsExportTest: 1 (propiedades DOCX, indentación y dibujo PDF).
- AttachmentEditorUiTest: 1 método ampliado (barra real B/I/U/Título/Código y
  activación/desactivación del Destacador).

Registros: /tmp/arachnode-content-tests-v2.log,
/tmp/arachnode-content-tests-v3.log y
/tmp/arachnode-content-final-controls.log. Se corrigieron errores de compilación
puntuales y pruebas incompatibles con el renderizado simulado antes de cerrar
la validación. No se repitieron suites ajenas.

Robolectric no implementa el decodificador Movie. La prueba de reproducción usa
un shadow explícito que decodifica el GIF real con ImageIO solo en tests, para
verificar fotogramas, dibujo, tiempo y pausa; no sustituye el decodificador de
producción ni incorpora una dependencia a la app. La decodificación BitmapFactory
para importación, backup y representación estática sí usa gráficos nativos.
La prueba de PDF verifica el dibujo sobre Canvas; no prueba el empaquetador PDF,
que no cambió.

Pendiente en teléfono real: decodificación/animación Movie de Android, pausa al
ocultar el visor, selector del sistema y ergonomía de selección/copia con teclado,
fuentes ampliadas y temas. No se consultaron ni modificaron datos del teléfono.
No hay APK, cambio de versión, commit ni push en esta intervención.

## Corrección posterior del resaltador (selección)

Por instrucción posterior, el resaltador deja de ser un modo por palabras en
el visor. El cuadrado de las capturas era HighlightTool incrustado en la vista;
el círculo era su fondo activo, no caracteres almacenados. Se retira ese
control del visor. La barra usa un icono Material compacto de 20 dp, objetivo
48 dp, habilitado únicamente con selección. Aplica/quita el formato Highlight
existente sin modificar letras ni otros formatos; el visor solo lo representa.
Imagen usa el icono Material Image y «Insertar imagen», con el mismo selector.
No se borran datos históricos ni se cambian gramática, DB, migraciones o backup.
Compilación correcta y una única prueba focalizada aprobada: selección, rango
amarillo exacto, texto visible intacto, recuperación del borrador, eliminación
del formato y acción del selector. Log: /tmp/arachnode-highlight-fix.log.
Pendiente comprobación visual en teléfono. Sin APK, versión, commit ni push.

## Continuación de listas y composición del teclado

Si Enter llega durante una composición IME, se conserva una candidatura temporal
en EditorDraft y se aplica al confirmar ese mismo texto/cursor. Otro cambio la
descarta; no se guarda en Room, backups ni estado restaurable. Guiones, números
y letras reutilizan DescriptionLists: incrementos, indentación, finalización de
línea vacía, exclusión de código y pegado multilínea intactos.

Selectores de etiquetas de edición: selección directa sin pie redundante;
la selección única existente se conserva. Desasignar una etiqueta histórica no
borra otras asociaciones ni el catálogo. Filtros y edición del catálogo conservan
sus controles. Las fichas existentes siguen sus escrituras inmediatas; los
borradores nuevos se guardan con el elemento que todavía se está creando.

Compilación correcta y cuatro pruebas focalizadas aprobadas para listas, IME,
cursor, reapertura y selección/desasignación visual de etiquetas.
