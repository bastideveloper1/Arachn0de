# Integridad del prototipo: rutas corregidas y límite del IME

## Conclusión

**PARCIAL. No integrar.** Se corrigieron las rutas explícitas de clipboard y su
sincronización con el borrador, pero una nueva prueba demuestra pérdida mediante
lectura/reemplazo genérico del IME. No se puede afirmar que todos los canales sean
seguros con el motor actual que transforma el documento presentado al teclado.
No se modificó el editor de producción, esquema, versión ni se generó APK.

## Correcciones exclusivamente en debug

`DescriptionSelectionPrototype.kt` reutiliza ahora un único
`DescriptionPrototypeClipboard`, implementado junto con
`DescriptionPrototypeSession` en el archivo debug nuevo `DescriptionPrototypeSession.kt`.

Rutas que llaman al mismo enrutador sobre el texto original:

- Copy/Cut/Paste dedicadas.
- Ctrl+C/X/V, Ctrl+Insert y Shift+Insert/Delete.
- Acciones semánticas de copiar/cortar/pegar y botones del harness.
- Menú contextual: se filtran las acciones nativas basadas en visualText y se
  añaden las acciones del enrutador.
- InputConnection.performContextMenuAction para copy, cut, paste y pasteAsPlainText,
  mediante InterceptPlatformTextInput y un InputConnectionWrapper público. El
  interceptor se mantiene estable al recomponer para no reiniciar la composición.

Se rechazan referencias ajenas a la sesión, tokens incompletos, contenido no textual
y recortes de formato que obligarían a normalizar bytes fuera del rango. No se
convierten etiquetas visuales en referencias ni se crean adjuntos ficticios.

El corte valida primero un candidato. Escribe el original en el clipboard antes
de modificar el documento; si esa escritura falla, no elimina texto. También
comprueba que el documento no haya cambiado durante la operación, evitando
sobrescribir una edición posterior. Las operaciones rechazadas no entran al
historial ni alteran el clipboard por un fallo de validación.

La sesión usa TextFieldState como texto canónico. `snapshotDraft()` obtiene texto,
selección e IDs retirados coherentes y actualiza el EditorDraft de la fixture en
una instantánea mutable. Los IDs retirados se derivan de los originales ausentes,
menos los presentes, preservando retiradas previas de la fixture. Restaurar una
referencia por pegar, undo o redo quita su ID de la lista de retiradas.

Los comandos explícitos y las entradas nativas registran un historial debug de
hasta 50 cambios, en memoria. Ctrl+Z/Shift+Z/Y lo usan; este historial no persiste
tras recrear la Activity. Texto, selección, scroll y procedencia inicial sí se
restauran. Esta es una limitación del harness, no una política nueva del editor real.
La sesión no importa, reserva ni libera archivos y no ofrece guardado real.

## Validación focalizada ejecutada

Diez métodos distintos completados: **nueve comprobaciones positivas y una prueba
diagnóstica que confirma el bloqueo pendiente**. Las repeticiones se limitaron a
cambios/fallos concretos, sin suites completas ni repetir gráficos o tiradores.

- `DescriptionSelectionPrototypeTest.dedicatedClipboardKeysPreserveRawReferences`:
  sustituye la antigua prueba diagnóstica de pérdida. Copy/Cut/Paste dedicados
  restituyen exactamente formato, texto y UUID; ya no reproduce el defecto anterior.
- `clipboardKeepsRawTokensAndRejectsPartialEdits`: rutas semánticas/botones y
  referencias parciales, reutilizada por el cambio de enrutador.
- `composingInputSurvivesRecomposition`: composición IME intacta con el nuevo wrapper.
- `DescriptionPrototypeIntegrityTest.shortcutsAndImeUseOriginalAndSyncUndo`:
  Ctrl, Insert/Delete, IME, semántica y botones; round-trip exacto, dos undo y dos
  redo por ruta, selección e IDs retirados coherentes, sin borrar retiradas previas.
- `contextMenuInvokesRawRouter`: un proveedor de menú de pruebas captura los
  componentes que Compose construye. Confirma ausencia de Copy/Cut/Paste nativos y
  ejecuta las acciones reales añadidas; conserva el original. No acredita el aspecto
  físico del ActionMode de Android.
- `unsafeFragmentsAndUnknownReferencesAreRejected`: rechazo de marcadores y tokens
  parciales y de UUID ajeno; documento/clipboard intactos; fallo de escritura antes
  del corte y edición concurrente simulada sin sobrescribir contenido nuevo.
- `nativeInputAndUndoKeepDraftSynchronized`: entrada/borrado nativos y undo/redo
  mantienen texto y metadatos del borrador.
- `DescriptionSelectionPrototypeTest.projectionScrollAndRestore`: se amplía el
  recorrido existente con cortar, restaurar el documento vacío y pegar el original,
  conservando la procedencia del UUID además de texto/selección/scroll.
- `AttachmentRepositoryTest.prototypeHistoryPreservesAttachmentOnSaveAndRollback`:
  fixture real de AttachmentRepository/Room y PNG. Cortar sin guardar no elimina
  relación ni bytes; pegar, undo y redo restauran un borrador sin IDs retirados
  incorrectos. El guardado confirmado conserva descripción, relación y bytes exactos;
  el fallo inyectado revierte. No modifica archivos ni datos del usuario.
- `DescriptionPrototypeIntegrityTest.imeReadAndCommitStillBypassClipboardRouter`:
  diagnóstico descrito abajo. Que el test apruebe confirma la pérdida, no su arreglo.

## Bloqueo restante demostrado

Con la descripción original `**Título**`, un token canónico cuya etiqueta es
«Fotografía» y texto final:

1. Un InputConnection real del ComposeView recibe la selección completa.
2. `getSelectedText(0)` devuelve la proyección: Título, Fotografía y el texto final,
   sin marcadores ni UUID.
3. Se simula un IME que gestiona su propio clipboard: reemplaza la selección con
   `commitText("", 1)` y pega lo que leyó con `commitText(copied, 1)`.
4. El documento resultante es la proyección visual, no el original. Su referencia
   desaparece y el borrador la registra correctamente como retirada. Eso no recupera
   el contenido que el teclado nunca recibió.

La prueba demuestra que esta secuencia de API permite pérdida. No demuestra que
un teclado específico del usuario la ejecute. La fuente local instalada de
Foundation 1.10.4 lo explica: AndroidTextInputSession entrega `state.visualText`
como documento del IME, y StatelessInputConnection.getSelectedText lee ese documento.
El wrapper de acciones de contexto no interviene en consultas ni commitText.

## Por qué no se añade un parche heurístico

No es seguro tratar toda lectura seguida de un borrado como un corte: el teclado
usa esas API también para edición, composición, predicción y sustitución normales.
Bloquearlas indiscriminadamente rompería el comportamiento de IME requerido.

Tampoco basta devolver el original solo en getSelectedText: longitudes, selección,
surrounding/extracted text y rangos de composición seguirían en coordenadas visuales.
Eso introduciría dos sistemas de offsets incoherentes ante el teclado. Implementar
un traductor completo de InputConnection y sincronización IME sería otro motor de
edición, sin garantía derivada de los tests actuales; no se incorporó.

**Recomendación:** no integrar la combinación actual de TextFieldState y una
OutputTransformation que elimina marcadores/sustituye tokens. Evaluar aisladamente
un motor cuyo documento de IME y clipboard sea siempre el original y cuyo formato
se represente mediante atributos, por ejemplo EditText/Spannable con spans protegidos.
Ese candidato requiere validar offsets, composición, tiradores y clipboard; no se
afirma que esté implementado o validado. Un modo de edición con marcadores visibles
sería otra posibilidad, pero cambiaría la presentación y requiere decisión aparte.

## Riesgos y alcance pendiente

- La pérdida por el canal genérico del IME permanece reproducible: impide integrar.
- La selección táctil entre pantallas sigue sin validación física; no se repitió
  la prueba omitida de Robolectric ni se presenta como aprobada.
- Historial limitado a memoria, herramientas reales de formato/visor y cambios
  externos del borrador no están integrados. No se debe guardar un EditorDraft
  externo omitiendo la instantánea canónica de la sesión.
- La prueba de archivo real confirma la compatibilidad del snapshot con el contrato
  existente; no convierte el harness en un editor conectado a archivos del usuario.

Archivos modificados: los dos archivos debug de componente/sesión,
`DescriptionSelectionPrototypeTest.kt`, el nuevo `DescriptionPrototypeIntegrityTest.kt`,
la prueba añadida a `AttachmentRepositoryTest.kt` y documentación de estado.
Todo continúa aislado de producción.
