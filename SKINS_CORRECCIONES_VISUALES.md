# Sistema de skins: editor interactivo y pulido visual

Referencia real del código: **0.9.0 / code23**, conservada.

## Implementación

- Las cinco superficies de previsualización (Núcleos, Capas, Tareas, Notas y encabezado) abren sus acciones de imagen al tocarlas. Una marca de selección identifica la última superficie elegida. Los componentes internos de ejemplo son inertes para evitar abrir registros o ejecutar acciones de tarjetas.
- Cada superficie ofrece el selector Android existente, cambiar imagen, encuadrar con el diálogo compartido y quitar la asignación. Los cambios se muestran en la misma previsualización y permanecen al seleccionar otra tarjeta. Cancelar el selector conserva la imagen anterior.
- Se eliminan el formulario «Imágenes opcionales», las tres etapas y la separación rígida entre vista previa y controles. El nombre, los diez colores y las tarjetas usan un desplazamiento continuo. Los colores se pueden plegar; los valores históricos de logo, encabezado y pinturas se conservan al abrir/guardar.
- «Crear tema» / «Editar tema» utiliza el color semántico del texto y una cabecera compacta. Los botones de guardado permanecen fuera del contenido desplazable, con los insets de sistema y teclado existentes.
- «Guardar» conserva el diseño y no solicita activación. «Guardar y aplicar» realiza una única escritura mediante el flujo existente con activación. La biblioteca incorpora una acción explícita «Aplicar» para temas guardados. Las operaciones confirmadas muestran mensajes breves.
- No cambia la semántica de un tema que ya estaba activo: guardar sus modificaciones mantiene su ID seleccionado y actualiza el diseño asociado a ese mismo ID. No se crea una segunda copia ni se cambia la selección a otro tema.
- El degradado horizontal protege el área de texto durante toda la altura de la tarjeta, incluidos títulos de varias líneas y progreso. La columna de acciones conserva su protección independiente y el extremo derecho conserva los píxeles de origen.
- Favoritos, Opciones y etiquetas reciben fondos oscuros semitransparentes solamente dentro de tarjetas ilustradas. Los acentos de iconos se conservan cuando tienen contraste suficiente; un tinte personalizado demasiado oscuro recibe una marca clara sobre la placa. Se conservan los estados seleccionados, dimensiones táctiles de 48 dp, distribución, separaciones y alturas de las tarjetas.
- El último 32 % de las imágenes del encabezado se desvanece mediante una máscara de transparencia. Se revela el fondo real del menú padre, también cuando usa pinturas/degradados; no se impone negro ni se tiñe la parte superior. El logo se dibuja fuera de la máscara. También se cubren encabezados históricos con imagen.
- El candado de cabecera utiliza los roles semánticos `icons` / `disabled`. La sesión abierta se identifica con candado abierto y descripción accesible; la acción continúa siendo «Bloquear», con el mismo callback. No se modifica la lógica de seguridad.

## Datos y compatibilidad

Esta intervención cambia presentación e interacción. No añade tablas, preferencias, tipos de archivos ni campos persistentes; no modifica cifrado, versiones de formato, almacenamiento ni migraciones. No modifica ni recomprime originales. Continúan el guardado preparado, las reservas de archivos, la reducción de previews cuando el presupuesto lo requiere y la lectura fragmentada de preferencias que corrigieron `Failed requirement.`.

Se conservaron los cambios previos del árbol de trabajo. No se generó APK, ni se firmó, ni se ejecutaron commit/push.

## Archivos de esta intervención

Producción, dentro de `app/src/main/java/com/r0ybt/arachn0de/`:

- `ui/CustomThemeEditor.kt`: selección directa, editor continuo, acciones de imagen y pie de guardado adaptable.
- `ui/AppearanceScreen.kt`: Aplicar explícito y confirmaciones.
- `ui/ThemeIdentity.kt`: transición de imágenes históricas al fondo del menú.
- `ui/theme/ThemeImages.kt`: máscara vertical, sombreado horizontal y colores de controles ilustrados.
- `ui/BashPresentation.kt`: alcance local de los controles sobre imagen en las tarjetas reales compartidas.
- `ui/CompactCardIconButton.kt`, `ui/ElementAccessUi.kt`: protección de Favoritos y controles compactos.
- `ui/ProjectComponents.kt`, `ui/NodeComponents.kt`: protección de Opciones.
- `ui/TagUi.kt`: protección de etiquetas.
- `ui/OfficialIcons.kt`: candado con colores semánticos.

Pruebas:

- `app/src/test/java/com/r0ybt/arachn0de/ui/CustomThemeUiTest.kt`: actualiza los flujos existentes a las tarjetas interactivas; cubre seleccionar, quitar, cambiar/cancelar, guardar sin activar, aplicar, reabrir, fuentes ampliadas, controles y separación de etiquetas.
- `app/src/test/java/com/r0ybt/arachn0de/ui/theme/CustomThemeTest.kt`: regresiones de píxeles del encabezado y del sombreado, proporciones y contraste.
- `app/src/test/java/com/r0ybt/arachn0de/ui/AppearanceUiTest.kt`, `AppearanceRestartUiTest.kt`: actualizan la navegación al grupo existente «Personalización» y finalizan los escritores de la aplicación de prueba antes de cerrar Room.

## Verificación automatizada

La batería general se detuvo por petición del usuario y no se repetirá. No se declara una ejecución conjunta completamente correcta.

Resultados ya obtenidos:

- Compilación Kotlin correcta.
- Las comprobaciones dirigidas de distribución con fuentes hasta 300 %, separación de títulos/etiquetas/progreso y degradados nativos pasaron (`/tmp/skin-layout4.log`).
- En la ejecución de 29 pruebas de interfaz, pasaron 28: incluidas la confirmación al aplicar desde biblioteca, guardar y aplicar con recomposición/cierre del editor, navegación y reinicio (`/tmp/skin-confirmations.log`). El único fallo de esa ejecución fue la protección de iconos oscuros; se corrigió después y su verificación definitiva queda pendiente.
- La ejecución amplia anterior completó 73 pruebas con 4 fallos: dos recorridos antiguos del menú, una excepción tardía de limpieza del fixture y la confirmación durante recomposición. Esos cuatro casos pasaron posteriormente en la ejecución dirigida de interfaz.
- La última batería amplia se interrumpió sin completar: había registrado fallos en la búsqueda de la tarjeta duplicada y en la observación del mensaje de guardado. No se investigaron ni corrigieron tras la petición de detener las comprobaciones. Quedan pendientes de evaluar; no se atribuyen sin evidencia a fallos de persistencia ni a una causa concreta.
- La corrección de confirmación al aplicar ya estaba validada mediante `combinedSaveAndApplyConfirmsOnceAndClosesAfterThemeRecomposition` y `administrationSelectsCopiesAndConfirmsDeletionOfActiveTheme` en la ejecución dirigida anterior. No se repitieron esas pruebas tras la petición de detenerse.
- `git diff --check` correcto durante la revisión.

No se realizaron correcciones adicionales después de la petición de cierre.

Problemas encontrados y corregidos durante las comprobaciones:

- Con fuente al 300 %, `FlowRow` podía colocar el segundo botón fuera de su altura disponible. El pie ahora calcula posiciones y alto a partir de las medidas reales de los botones, pasando a columna cuando es necesario.
- La activación puede reconstruir el árbol de composición. La fase final ya confirmada de guardado, liberación del editor y notificación termina aunque se sustituya esa composición; no se repite la escritura.
- Dos pruebas anteriores abrían «Ajustes» para buscar Apariencia, que ahora pertenece a «Personalización». Se actualizaron sus recorridos sin modificar el menú. También se detienen sus escritores antes de cerrar Room para impedir excepciones tardías en la siguiente prueba.
- La cabecera anterior podía ocupar demasiado alto con fuente ampliada: se muestra el título de creación/edición correspondiente, acotado a dos líneas.
- Una cota proporcional fija del sombreado podía dejar metadatos desprotegidos en tarjetas anchas: el límite del área protegida ahora sigue el ancho disponible antes de las acciones.
- El desplazamiento automático de pruebas podía oscilar intentando mostrar una tarjeta mayor que la ventana: la prueba usa desplazamiento finito al inicio de la superficie y comprueba que su acción está visible.

## Pendiente en teléfono

`adb devices -l` no detecta dispositivos conectados. No se declara realizada una revisión visual/táctil física.

- Revisar encuadre con arrastre y pellizco, selector del proveedor de fotos del teléfono y cambios de orientación/teclado mientras se edita.
- Evaluar visualmente fotos claras, oscuras, con detalle y transparencia en las cuatro tarjetas, con títulos/etiquetas/progreso reales y los temas personalizados habituales.
- Revisar la continuidad del menú completo y legibilidad del logo con imágenes horizontales, verticales y cuadradas; las pruebas nativas de píxeles cubren la máscara, pero no sustituyen esta revisión.
- Comprobar gestos Android, navegación de tres botones, tamaño de pantalla y fuente del dispositivo, y lectura mediante TalkBack.
- Repetir guardar, aplicar, editar y reabrir con las dos imágenes originales que provocaban el error, y restaurar un backup real en el teléfono. Las regresiones automatizadas utilizan fuentes válidas generadas y fixtures; esas dos imágenes concretas no se han proporcionado.
