# Integración de selección extensa: bloqueo de integridad

**Actualización:** las teclas dedicadas, menú y rutas explícitas de IME ya están
corregidas en debug, pero otra prueba confirma pérdida por getSelectedText +
commitText del IME. El bloqueo global permanece. Estado y evidencia vigentes en
[DESCRIPTION_PROTOTYPE_INTEGRITY.md](DESCRIPTION_PROTOTYPE_INTEGRITY.md).
El informe siguiente documenta el hallazgo inicial.

## Resultado

**Integración detenida antes de sustituir el editor de producción.** La autorización
para avanzar no se interpreta como permiso para exponer contenido real a la pérdida
reproducida abajo. No se modificó `src/main`, el almacenamiento ni el renderizador
de lectura. El prototipo permanece exclusivamente en debug.

## Pérdida reproducida

La nueva prueba `DescriptionSelectionPrototypeTest.dedicatedClipboardKeysLoseRawReferences`
utiliza texto sintético con `**Título**`, una referencia canónica de imagen con etiqueta
«Fotografía» y texto final.

1. Abre el campo moderno y selecciona todo el original.
2. Envía `Key.Copy` (tecla dedicada, sin Ctrl).
3. Comprueba que el clipboard contiene la proyección visual, sin marcadores ni UUID.
4. Envía `Key.Cut`; el campo queda vacío.
5. Ejecuta el adaptador de pegar del propio prototipo.
6. El resultado coincide con la proyección visual; desaparecieron el formato y la
   referencia de imagen. «Fotografía» quedó como texto común.

Resultado: **prueba diagnóstica aprobada**, que demuestra el defecto, no una
integración corregida. Se ejecutó solo este nuevo método; no se repitieron las
pruebas anteriores de formato, scroll, composición, fondos o restauración.
No hay pérdida de datos del usuario: todo ocurrió con la fixture en memoria.

El origen está en `DescriptionSelectionPrototype.kt`: el `onPreviewKeyEvent` solo
intercepta Ctrl+C/X/V, mientras las teclas dedicadas alcanzan la lógica nativa del
campo. Las fuentes locales de Foundation 1.10.4 confirman que el motor moderno
copia/corta desde `visualText`; sus adaptadores semánticos y del menú contextual
no protegen automáticamente todas las entradas.

Se exploró también `InputConnection.performContextMenuAction(cut)` en Robolectric:
el método devolvió false, por lo que ese intento no prueba ni descarta un recorrido
peligroso en otros IME/dispositivos. El test experimental de ese canal se sustituyó
por la reproducción válida de teclas dedicadas.

## Por qué no se activó una integración parcial

Interceptar Copy/Cut/Paste dedicados es un arreglo local identificable, pero no
cierra por sí solo todos los canales ni la sincronización con el borrador real.
No se afirma que el problema sea técnicamente imposible: **todavía no hay una
solución integral de integridad validada para habilitarla con datos reales**.

El borrador actual tiene controles que el prototipo sintético no implementa:

- `AttachmentDescriptionEditor.kt` aplica `draft.editDescription` a los cambios
  aceptados y conserva composición/selección para las herramientas.
- `AttachmentDraftEditing.kt` registra los IDs retirados en `removedAttachmentIds`
  y rechaza introducir referencias nuevas por texto sin procedencia autorizada.
- El `prototypeReplace` de debug admite reinsertar IDs sintéticos conocidos,
  escribiendo directamente en TextFieldState; no actualiza el borrador real ni
  sus reservas de archivos.
- `AttachmentRepository.saveDraft` utiliza los IDs retirados al desvincular
  relaciones. Una integración que simplemente copie texto de TextFieldState al
  borrador podría dejar ese registro desactualizado al pegar o deshacer, con riesgo
  de desvincular una referencia reintroducida. Es un riesgo concreto de la
  integración ingenua, **no un fallo demostrado del editor actual**.

No se eliminó ninguna protección ni se añadieron callbacks asíncronos de copia
entre dos fuentes de texto para aparentar una integración completa. Tampoco se
acepta usar únicamente el aspecto visual como prueba de que un token sigue ahí.

## Trabajo necesario para levantar el bloqueo

1. Unificar las operaciones de clipboard sobre rangos originales para menú,
   accesibilidad, atajos, teclas dedicadas y rutas IME admitidas, con pruebas de
   round-trip exacto y rechazo seguro para rutas no compatibles.
2. Diseñar y validar una única fuente de texto/selección para el borrador y el
   campo moderno: sin actualizaciones tardías al guardar, sin reiniciar composición
   al recomponer y sin perder las modificaciones externas de formato/visor.
3. Mantener de forma coherente la procedencia y los IDs retirados al cortar,
   pegar, deshacer y restaurar, reutilizando las reservas/validación de archivos
   existentes. Comprobar adjuntos reales y rollback/cancelación antes de activar.
4. Solo después, mover la lógica común del prototipo a producción y hacer que el
   harness debug la reutilice; no crear dos motores paralelos.
5. Conservar como pendiente explícito el arrastre táctil de varias pantallas en
   Android físico. Las pruebas por estado no lo sustituyen.

Archivos de esta intervención: la prueba diagnóstica nueva en
`app/src/test/java/com/r0ybt/arachn0de/ui/DescriptionSelectionPrototypeTest.kt`,
este informe y el aviso de bloqueo en `DESCRIPTION_SELECTION_PROTOTYPE.md`.
Sin cambios de versión, esquema, migraciones, datos reales ni APK.
