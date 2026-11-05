# Prototipo aislado de selección extensa

**Estado vigente:** se corrigieron las rutas explícitas de portapapeles y la
sincronización de adjuntos/undo en debug. Persiste una pérdida reproducida por
lectura y reemplazo genérico del IME: **no integrar**. Detalles y pruebas en
[DESCRIPTION_PROTOTYPE_INTEGRITY.md](DESCRIPTION_PROTOTYPE_INTEGRITY.md).
Los apartados siguientes conservan la evolución histórica del prototipo.

**Bloqueo posterior confirmado:** las teclas dedicadas Copy/Cut evitan los
adaptadores y un ciclo cortar/pegar pierde marcadores y referencias. La integración
autorizada se detuvo antes de cambiar producción. Evidencia y trabajo pendiente en
[DESCRIPTION_SELECTION_INTEGRATION_BLOCKER.md](DESCRIPTION_SELECTION_INTEGRATION_BLOCKER.md).
Los resultados siguientes corresponden a los canales concretos probados durante
la construcción del prototipo; no acreditan integridad de todos los canales.

## Estado y aislamiento

Prototipo de la segunda alternativa sobre Foundation 1.10.4. **No integrado ni
recomendado todavía para sustituir el editor de producción.** Se recomienda
conservarlo como candidato para comprobar los tiradores en Android físico.

Archivos nuevos:

- `app/src/debug/java/com/r0ybt/arachn0de/ui/DescriptionSelectionPrototype.kt`
- `app/src/debug/AndroidManifest.xml`
- `app/src/test/java/com/r0ybt/arachn0de/ui/DescriptionSelectionPrototypeTest.kt`
- Este informe.

No se modifica `src/main`, el renderizador de lectura, el formato del contenido,
Room, respaldos ni versión. No hay entrada desde menús habituales. La Activity
solo existe en debug, usa texto sintético con un UUID de referencia de ejemplo,
no carga archivos ni repositorios y no guarda tarjetas. No se generó APK.
El estado restaurable de la Activity es exclusivamente el documento sintético.

## Implementación

- BasicTextField basado en TextFieldState, con ScrollState explícito, máximo de
  doce líneas y 320 dp. El contenedor exterior sigue pudiendo desplazarse.
- OutputTransformation deriva siempre del buffer vigente, no de una captura de
  composición antigua. Aplica cambios individuales de proyección para mantener
  la correspondencia de offsets y añade los estilos de fragmento y párrafo del
  parser existente. Los marcadores y tokens permanecen en el estado original.
- El fondo de código utiliza las líneas del layout visual y se traslada una vez
  con el scroll interno, recortado al viewport. No modifica caracteres.
- La decoración, los controles y los tokens originales están separados del texto
  visual. Se reutiliza el parser y la validación de ediciones de EditorDraft.
- Copiar/cortar/pegar del menú contextual personalizado, acciones semánticas,
  botones de comprobación y Ctrl+C/X/V trabajan sobre el original. Copiar amplía
  una selección parcial de referencia al token completo; cortar/editar una
  referencia parcialmente seleccionada se rechaza. Cortar no escribe en el
  clipboard si la edición falla.
- Pegar referencias se limita a IDs conocidos en la sesión sintética. No inventa
  adjuntos ni importa tokens de otro documento. Esta restricción no sustituye una
  política de importación de archivos para producción.
- rememberTextFieldState y rememberScrollState conservan texto, selección y scroll
  en la restauración de la Activity. La composición IME se deja al motor del campo;
  no se reescribe el estado después de cada recomposición.

## Evidencia focalizada

Se ejecutaron seis métodos distintos, **cinco aprobados y uno omitido con motivo
explícito**. No hubo suite global. Las repeticiones se limitaron a código cambiado,
fallos o nuevas comprobaciones:

1. `projectionScrollAndRestore`: proyección exacta del parser para más de cien
   líneas, estilos enriquecidos y párrafos de código presentes, selección original
   completa entre varias pantallas, scroll interno no nulo, restauración de texto,
   selección y desplazamiento mediante StateRestorationTester.
2. `clipboardKeepsRawTokensAndRejectsPartialEdits`: acción semántica Copiar conserva
   marcadores y token canónico; cortar y pegar restituyen exactamente el original;
   una selección parcial no emite token incompleto al copiar ni permite cortarlo.
3. `imeAndRecompositionKeepOriginal`: entrada de texto y rango conservados al
   recomponer.
4. `composingInputSurvivesRecomposition`: InputConnection real del ComposeView bajo
   Robolectric; setComposingText conserva texto/composición al recomponer y
   finishComposingText termina la composición sin pérdida.
5. `codeBackgroundScrollsAndClips`: dibujo nativo en bitmap de la View; el fondo
   aparece en la posición visible tras desplazar y no invade el padding exterior.
6. `handleGestureProbe`: **omitido**, porque Robolectric localiza el Popup pero
   su swipe no cambia la selección. El test exige validar primero un arrastre
   auténtico antes de afirmar que el scroll funciona o se detiene al soltar.

El caso de corte detectó inicialmente un fallo del prototipo: la transformación
capturaba texto anterior y fallaba al vaciar el campo. Se corrigió derivando la
proyección del buffer que Compose entrega a OutputTransformation. La captura por
PixelCopy agotó el tiempo de espera de Robolectric; la prueba gráfica usa el dibujo
de View en bitmap, como otras pruebas del proyecto. Estos fallos no pertenecen al
editor de producción.

La selección de varias pantallas quedó comprobada **por estado**, no mediante
arrastre táctil. No se presenta la omisión del gesto como una validación favorable.

## Comprobación táctil posterior

En una compilación debug autorizada que incluya este código, iniciar explícitamente:

```
adb shell am start -n com.r0ybt.arachn0de/com.r0ybt.arachn0de.ui.DescriptionSelectionPrototypeActivity
```

No se creó ni instaló esa compilación durante esta tarea. El componente está
exportado únicamente en debug para permitir este lanzamiento de datos sintéticos.

Recorrido pendiente:

1. Pulsación larga en el texto y arrastre de ambos tiradores hacia los bordes;
   ampliar y reducir entre varias pantallas, incluido rango invertido.
2. Mantener el dedo quieto en cada borde y comprobar avance sostenido; soltar y
   cancelar, verificando que se detenga. Alternar con scroll manual.
3. Repetir con teclado visible/oculto, composición, pantalla girada y fuente grande.
4. Copiar/cortar/pegar fragmentos con formato y una referencia completa usando menú
   Android, botones, accesibilidad y teclado. Probar también acciones de clipboard
   iniciadas directamente por el IME: estas rutas no están acreditadas todavía.
5. Comprobar fondo/contorno de código y visibilidad de controles durante todo el
   recorrido; rotar y comprobar restauración.

## Lo que falta y riesgos de integración

- La capacidad decisiva —tiradores que amplían continuamente fuera de pantalla y
  paran al soltar— sigue sin validación física. No integrar antes de comprobarla.
- No se ha acreditado el menú nativo Android completo ni comandos de clipboard
  invocados directamente por IME o teclas dedicadas; podrían evitar los adaptadores
  de portapapeles. Tampoco se acredita paridad de offsets al copiar fragmentos que
  corten marcadores ocultos o selección invertida sobre formato/referencias.
- Las referencias son etiquetas sintéticas. No hay visor, importación de imágenes,
  resolución de archivos reales ni reservas; falta adaptar hit testing con scroll
  y conectar el visor existente. No se deben pegar referencias a archivos ajenos.
- El fondo y los estilos de código están probados, pero falta trasladar el icono
  decorativo completo del editor actual y revisar píxeles durante teclado/rotación.
- No hay botones de formato ni puente al borrador de producción: se preservan los
  marcadores de entrada y sus estilos, pero aplicar/quitar formato desde las
  herramientas reales exige probar la sincronización sin reiniciar IME ni undo.
- La restauración probada es del documento sintético y selección/scroll. No supone
  persistencia de una sesión de composición del teclado tras muerte del proceso.

Integración eventual, con autorización aparte: adaptar AttachmentDescriptionField
al motor con estado; adaptar la transformación en AttachmentText sin tocar lectura;
reutilizar el dibujo de DescriptionCodeBlocks con offsets de scroll; conectar
AttachmentDescriptionEditor y AttachmentDraftEditing con el borrador real y sus
herramientas; acotar el viewport/IME en EditDialogs. Completar clipboard por todos
los canales y visor/archivos usando sus contratos actuales. No exige por sí mismo
cambiar el formato persistido ni migrar datos.
