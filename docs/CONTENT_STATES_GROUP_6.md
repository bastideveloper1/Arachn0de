# Arachn0de — Grupo 6: Contenido y estados

Fecha: 2026-11-05. Se conservan los cambios previos del workspace.

## 1. «Cambiar estado desde menú no debe ir ahí» — implementada

El módulo compartido muestra Cambiar estado exclusivamente en Contenido y
Configurar estados exclusivamente en Opciones. La configuración antes se
etiquetaba Estados; conserva su icono y el mismo selector. Los menús exteriores
de las tarjetas conservan Configurar estados y dejan de duplicar Cambiar estado.
La elección explícita del estado se realiza desde Contenido de la tarea abierta.

No cambia TaskStateSettingsItems, TaskStatusItems, sus callbacks, permisos,
repositorios, contadores, filtros ni indicadores. Núcleos y Capas solo configuran
el modo; Notas no adquieren acciones de tareas. Las tareas normales mantienen
la herencia y la confirmación existente de Por verificar → Pendiente al reducir
el modo efectivo de tres a dos estados.

## 2. «Cosas como "1. 2. 3." o "a. b." debe saltar un espacio, para saber que se aplicó» — implementada

Inspección previa: el editor usa BasicTextField con transformación visual de
formatos y referencias atómicas. Las listas son texto plano; ya existía la
continuación de Enter, pero no había controles para aplicar listas.

Se añaden Lista numerada (1.) y Lista alfabética (a.) a la barra existente.
Aplican el marcador seguido de un espacio a la línea actual o las líneas
seleccionadas. Conservan sangría, líneas vacías intermedias, texto exterior y
tokens de adjuntos. Sustituyen marcadores reconocidos sin acumular prefijos;
no reinterpretan palabras como «Nota.» como marcadores. El cursor termina tras
el contenido seleccionado. Los bloques de código no se convierten.

Se reutiliza la continuación existente, incluidos z → aa, números de varias
cifras, salida de un elemento vacío y espera al commit del IME. Pegar, abrir o
guardar texto no dispara una normalización global. No se cambia el parser ni
el formato almacenado: no hay migración ni nueva interpretación histórica.
Se conserva también la política previa de trim de los guardados de tarjetas;
las pruebas de guardado/reapertura comprueban listas con contenido.

## 3. «Fotos en descripción podrían mostrar una miniatura» — parcialmente implementada

AttachmentText muestra una miniatura en la posición de cada referencia de
imagen. Repeticiones del mismo archivo mantienen sus etiquetas y posiciones
individuales. Tocar la miniatura abre AttachmentViewer con la identidad del
original. Los audios mantienen sus enlaces. Si no se puede leer la imagen,
queda disponible su etiqueta como alternativa.

Se usa PrivateImageCache: LRU privado de 4 MiB, muestreo hasta 256 px por lado,
orientación EXIF existente y lectura SecureFiles del almacén autenticado. La
lectura ocurre en IO bajo el mutex común de archivos para no competir con
limpieza/restauración. No se crean derivados en disco ni temporales en claro.
Se conserva el rechazo por sesión inactiva/revocada y la limpieza de la caché
al bloquear. Los bitmaps que Compose aún usa no se reciclan manualmente.
La presentación usa Fit, sin recortar. No se toca la lógica de cifrado.

No se modifican originales, asociaciones, reservas, diarios, eliminación,
exportadores ni renderizadores de tarjetas. La exportación sigue cargando las
referencias con su mecanismo de imagen grande, con proporciones completas.

**Parte detenida:** incrustar miniaturas dentro del campo editable. El campo
actual calcula cursor, selección y toques mediante offsets de etiquetas de
texto; sustituirlos por imágenes requiere validar otra geometría. No se modifica
AttachmentDescriptionField ni se retoma el prototipo de selección de texto.

**Propuesta pendiente de decisión:** si también se requieren miniaturas durante
la escritura, añadir una vista previa de lectura opcional que reutilice este
renderer y el mismo borrador, conservando intacto el campo editable. Una imagen
incrustada en el propio campo debe tratarse como una tarea separada, con pruebas
de cursor/IME, referencias repetidas y eliminación/cancelación.

## Persistencia y ciclo de vida

Revisados el contrato de ARCHITECTURE.md y BACKUP_FORMAT.md. No se añaden datos
confirmados, archivos, preferencias, relaciones ni formatos. Las descripciones
siguen guardando exactamente los tokens de adjuntos existentes; los backups
transportan sus originales completos mediante el protocolo actual. La caché
reutilizada solo existe en memoria y no pertenece al backup. No cambia la
política de liberación de archivos. No se cambia versión, no se genera APK y
no se ejecutan commits, push, tags ni publicaciones.

## Validación focalizada

**39 casos distintos aprobados**, con compilación Kotlin y git diff --check
correctos. Se ejecutó :app:testDebugUnitTest con filtros concretos, offline.

- CoreBottomModuleTest: cuatro casos nuevos de ubicación exclusiva, elección
  de estado heredado, conversión confirmada, Núcleo, Capa y restricciones de Nota.
- TaskStateConfigurationTest: tres casos existentes de herencia/excepciones,
  conversión efectiva, alcance obsoleto, fallo transaccional, backup y reapertura.
- TaskViewProjectionTest: tres casos de contadores, filtros y disponibilidad
  del tercer estado. TaskStateCardUiTest: indicador de verificación y progreso.
- AttachmentEditingStateTest: 18 casos, incluidos dos nuevos de aplicación,
  separación, continuidad, reapertura del borrador, 28 elementos alfabéticos,
  sangría, código, tokens intactos y preservación de texto ajeno a la selección.
- AttachmentEditorUiTest: dos casos nuevos de botones y Enter, guardado y
  lectura desde Room, miniaturas por aparición y apertura del visor original.
- DescriptionThumbnailTest: dos casos nuevos de imagen cifrada de 2048×1024
  reducida a 256×128, caché acotada, bytes y archivos intactos, otra sesión,
  revocación, archivo ausente/corrupto, audio y eliminación pendiente.
- ContentEditingIconsTest: un caso de mapeo del nombre Configurar estados.
- AttachmentRepositoryTest: dos casos existentes de backup y formato/etiquetas.
- AttachmentBackupTest: un round trip existente con original y dueños compartidos.
- CardShareFilesTest: dos regresiones existentes de imagen vertical grande,
  proporciones sin recortes y bytes originales intactos.

La primera ejecución tuvo 32 aprobaciones y un fallo del fixture nuevo: consultaba
la Activity desde una transacción en IO. Se capturó el repositorio antes de la
transacción. Después, la espera de imágenes consultaba el árbol semántico unido,
que oculta los hijos del botón; se cambió al árbol completo. Se repitieron esos
casos y los afectados por la preservación de sangría vacía, sin repetir la
batería completa. Un filtro de backup tenía el nombre de clase invertido; ese
caso se ejecutó posteriormente con su nombre correcto.

Evidencia local: /tmp/content-group6-compile.log, /tmp/content-group6-tests.log,
/tmp/content-group6-followup.log y /tmp/content-group6-final-checks.log; XML
conservados en /tmp/content-group6-results-initial y
/tmp/content-group6-results-followup, más los resultados de la última ejecución.
El mensaje de decoder «unimplemented» corresponde a la imagen corrupta sintética.

Pendiente en Android físico: separación con teclado/IME real, cursor tras aplicar
listas, fuentes ampliadas, legibilidad y orientación de fotos, toques del visor,
bloqueo/cambio de almacén durante una carga y comprobación visual de la imagen
exportada. Robolectric no sustituye estas verificaciones táctiles y visuales.
