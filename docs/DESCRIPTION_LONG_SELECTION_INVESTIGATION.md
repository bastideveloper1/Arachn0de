# Selección extensa de descripciones

## Evaluación de la primera alternativa autorizada

No se consiguió establecer una coordinación fiable con los tiradores nativos
del campo actual. **La corrección continúa pendiente**. No se cambió a la segunda
alternativa ni se modificó código de producción.

La revisión de la dependencia instalada Foundation 1.10.4 concretó el bloqueo:

- `AndroidSelectionHandles.android.kt:85` crea los tiradores en `HandlePopup`;
  las líneas 225–228 utilizan `Popup` sin recorte, con una raíz de eventos propia.
  Un observador de punteros en la Column exterior no cubre ese gesto.
- `TextFieldSelectionManager.kt:594–647` mantiene internamente el tirador activo,
  su posición y los eventos down/up/drag/stop. `TextFieldSelectionHandle`, líneas
  1353–1367, instala ese detector en el Popup. Estas funciones no constituyen una
  API pública que el editor pueda usar para coordinar su ScrollState exterior.
- `onValueChange(TextFieldValue)` comunica texto y rango, no qué tirador está
  activo, dónde está el dedo o cuándo se suelta/cancela. Un rango que no cambia
  puede significar tanto mantener el dedo quieto en el borde como haberlo soltado.
  Iniciar un bucle por cambios de selección no garantiza el requisito de detenerlo
  al terminar la interacción ni evita interferir con selección por teclado.

Se reutilizó la prueba diagnóstica para intentar añadir una comprobación del
tirador real y contar eventos recibidos por la Column. Se localizaron los nodos
semánticos de los tiradores, pero los tres intentos de inyección de arrastre
(hacia arriba, hacia abajo y mediante swipe multievento) no produjeron un cambio
de selección. Falló la aserción que exigía validar primero un arrastre real;
**no se considera demostrado por esos tests el aislamiento de eventos**. Ese
aislamiento está sustentado en la implementación de Popup revisada, y la falta
de un gesto válido impidió validar la solución en Robolectric. No hay dispositivo
conectado para contrastarlo físicamente.

Se retiró la extensión experimental de la prueba, conservando exactamente la
prueba diagnóstica anterior. No se dejaron pruebas deshabilitadas ni una
implementación parcial que solo cubra pulsación larga y falle con los tiradores.
No se ejecutaron suites completas ni se repitieron pruebas ajenas.

Opciones pendientes de decisión:

1. Mantener este campo exigiría un puente explícito al ciclo de vida y posición
   de sus tiradores, por ejemplo mediante un cambio mantenido de Compose que
   exponga esos callbacks. Es un alcance mayor de dependencia y mantenimiento,
   no un simple modificador en el contenedor. No se propone acceder por reflexión
   a internals ni inferir el gesto de la visibilidad del menú flotante.
2. Autorizar por separado la evaluación del campo con desplazamiento integrado
   de la segunda alternativa, manteniendo formato, referencias y estética, con
   pruebas dirigidas de tiradores, teclado, coordenadas del código y clipboard.

Se cumple la instrucción de detenerse e informar si la primera alternativa no
puede resolverse fiablemente. En esta continuación solo cambia este informe;
sin modificaciones de contenido, esquema, versión ni generación de APK.

## Causa reproducida en edición

`ui/AttachmentDescriptionField.kt` utiliza la sobrecarga de `BasicTextField`
basada en `TextFieldValue`, con `maxLines = Int.MAX_VALUE`, independientemente
del parámetro recibido. El campo crece con todo el documento. Los editores
delegan el scroll vertical a su Column exterior; texto y fondo de código
comparten las coordenadas del campo completo.

Al arrastrar la selección cerca del borde de la ventana, ese borde no es el
borde del campo completo. La selección cambia, pero el contenedor exterior no
avanza automáticamente. No se trata de un máximo de caracteres seleccionables.

La fuente local de Compose Foundation 1.10.4,
`TextFieldSelectionManager.kt` (`onDrag` del texto y del tirador), actualiza
coordenadas/rango; no coordina un autodesplazamiento del contenedor externo.
`SelectionManager.kt`, usado por SelectionContainer en lectura, tampoco contiene
esa coordinación en el arrastre del tirador. Esto último es evidencia de código,
no una reproducción táctil del modo lectura.

`AttachmentDescriptionEditor.kt` conserva `TextFieldValue` por borrador y
`AttachmentDraftEditing.kt` conserva los extremos del rango al cambiar solamente
la selección. No se encontró un límite de longitud ni una reducción automática
del rango por los controles de formato. No se modificaron estas funciones.

## Prueba focalizada

Se añadió `DescriptionLongCodeLayoutTest.longSelectionStopsAtOuterViewport`:

- 100 párrafos y viewport exterior de 320 dp.
- Foco, pulsación larga real y arrastre mantenido cerca del borde inferior con
  eventos de movimiento durante dos segundos.
- Se exige que el gesto haya iniciado una selección no vacía.
- El contenedor tiene contenido desplazable, pero su scroll permanece en cero y
  el extremo seleccionado no llega a la mitad del documento.
- Desplazar posteriormente el contenedor hasta el final conserva rango y texto.

Resultado: **aprobado como prueba diagnóstica de la limitación actual**, no como
prueba de una corrección. Durante su construcción se corrigió un parámetro de la
API de tests y se ajustaron foco, procesamiento del tiempo de pulsación larga y
acceso al árbol semántico sin combinar; los intentos iniciales no acreditaban
un gesto de selección. Solo se ejecutó este método, sin suites completas.

No se acredita todavía el arrastre de ambos tiradores de Android ni teclado/IME
físico, copiar/cortar/pegar del sistema, lectura con SelectionContainer o aplicación
de formato sobre una selección de varias pantallas. La prueba previa de bloques
largos comprueba medidas y scroll, no resolvía este gesto; no se repitió.

## Alternativas antes de cambiar el editor

1. Conservar el campo expandido y coordinar explícitamente el autodesplazamiento
   exterior con la selección. Mantiene la distribución, pero exige manejar también
   los tiradores en ventanas emergentes, recorte del viewport, teclado y movimiento
   de coordenadas mientras se desplaza. No basta con añadir un detector de arrastre
   que compita con la selección nativa.
2. Dar al campo un viewport con desplazamiento propio e integrarlo con la selección
   de texto. Reutilizar decoración, estilos, referencias y herramientas actuales.
   Es la opción a evaluar preferentemente con el campo moderno de Compose basado
   en estado, pero requiere adaptar transformación de referencias y dibujo de código
   a sus coordenadas de scroll, así como comprobar IME y clipboard.

No se ha implementado ninguna alternativa. Activar simplemente `maxLines` finito
puede volver a desalinear los fondos de código y crear scroll anidado. Por la
instrucción de informar alternativas antes de modificar significativamente el
editor, se detiene la implementación en este diagnóstico. Tampoco se sustituye
la lectura por un editor ni se añade una interfaz paralela.

Archivos modificados: la prueba añadida en
`app/src/test/java/com/r0ybt/arachn0de/ui/DescriptionLongCodeLayoutTest.kt` y este
informe. Sin cambios de producción, contenido guardado, esquema, versión o APK.
