# Correcciones de Núcleos: etiquetas y títulos

## Resultado

Las etiquetas de tarjetas de Núcleos tienen una zona propia encima del contenido y las acciones. Conservan las insignias existentes y pasan a otra fila cuando no caben. No compiten por ancho con Favoritos u Opciones; los controles mantienen sus objetivos de 48 dp y al menos 8 dp de separación vertical. El mismo encabezado compartido aplica esa separación a la insignia de las tarjetas de Capas, tareas y notas, sin alterar sus acciones ni contenido.

Los límites funcionales existentes son **60 caracteres Unicode para Núcleos** y **100 para Capas, tareas y notas**. Se cuentan puntos de código sobre el título sin espacios exteriores: un emoji cuenta una vez. El número de líneas visibles no restringe el guardado; no se cambia TitleLimits ni la validación de los repositorios.

Los editores rechazan una entrada que exceda el límite completo, sin cortar pegados ni sobrescribir el texto anterior, y muestran «No se añadió el texto que excede el límite». El contador explica el máximo y cuándo se alcanza. Un título anterior ya demasiado largo se conserva íntegro y permite correcciones que reduzcan o mantengan su longitud; se explica que debe reducirse antes de guardar. No se alteran descripciones, adjuntos ni datos persistentes.

## Archivos de esta intervención

- `app/src/main/java/com/r0ybt/arachn0de/ui/CompactCardHeader.kt`: disposición separada para etiquetas de las tarjetas que lo solicitan.
- `app/src/main/java/com/r0ybt/arachn0de/ui/NodeComponents.kt`: aplica la misma separación a la insignia compartida de las tarjetas de elementos.
- `app/src/main/java/com/r0ybt/arachn0de/ui/ProjectComponents.kt`: activa esa disposición y ajusta la altura mínima del contenido etiquetado.
- `app/src/main/java/com/r0ybt/arachn0de/ui/TitleInput.kt`: validación y avisos transitorios compartidos de entrada.
- `app/src/main/java/com/r0ybt/arachn0de/ui/CorePropertyEditor.kt`: edición directa de títulos de Núcleos y nodos.
- `app/src/main/java/com/r0ybt/arachn0de/ui/ModularPanel.kt`: permite marcar el campo compartido como erróneo, sin cambiar sus demás usos.
- `app/src/main/java/com/r0ybt/arachn0de/ui/EditDialogs.kt`: creación y edición completas de Núcleos y elementos.
- `app/src/main/java/com/r0ybt/arachn0de/ui/QuickCreateBar.kt`: creación rápida con el mismo límite y aviso.
- `app/src/main/java/com/r0ybt/arachn0de/ui/ElementAccessUi.kt`: captura rápida con el mismo comportamiento de títulos.
- `app/src/test/java/com/r0ybt/arachn0de/ui/CoreCorrectionsTest.kt`: pruebas nuevas de geometría, entrada y formularios.
- `app/src/test/java/com/r0ybt/arachn0de/ui/CompactCardUniformityTest.kt`: comprueba el margen inferior de los controles sin exigir su alineación anterior exacta.

Se conservan los cambios anteriores del árbol. No hay migraciones, cambios de formato de backup ni preferencias persistentes nuevas. No se genera APK, firma, commit ni push.

## Validación

Compilación Kotlin y **13 pruebas dirigidas correctas**, sin fallos:

```text
./gradlew testDebugUnitTest --offline --tests '*ui.CoreCorrectionsTest' --tests '*ui.CompactCardUniformityTest' --tests '*data.TitleLimitsTest' --tests '*ui.NodePropertyEditingTest'
```

Se comprueban cuatro etiquetas largas, anchos de 260 y 320 dp, fuentes 1×/1,8×, objetivos de 48 dp y separación de acciones. También límites de 60/100, emojis, rechazo explícito de entradas excesivas, recuperación de títulos anteriores demasiado largos, títulos multilínea válidos, creación de Capas, edición de notas y creación/edición persistente de Núcleos conservando descripciones. Las siete pruebas existentes de propiedades de nodos y la validación del repositorio siguen pasando. `git diff --check` correcto. Log local final: `/tmp/core-corrections-complete.log`.

El intento de automatizar directamente ProjectDialog se bloqueó con `AppNotIdleException` en Robolectric, un problema observado previamente con ese diálogo. Su política de títulos queda comprobada en el componente compartido y en el repositorio; la interacción completa de creación/edición de Núcleos requiere revisión en teléfono. También quedan pendientes contraste y teclado en dispositivos específicos. La suite global no se ejecuta.

## Correcciones posteriores a la APK 0.13.0/code27

- Fotografía: ya existían importación acotada a 2048 px y miniaturas adaptadas al tamaño y zoom; se activó filtrado de alta calidad al dibujar la fotografía del proyecto, sin reescribir archivos ni cambiar cifrado/backups.
- Skins: títulos con una región horizontal estable cuando hay imagen; dos líneas no desplazan el límite del degradado. El renderizado de imagen ya ocupaba toda la tarjeta y conserva encuadre, dimensiones y controles.
- Capas sin resultados: reutilizan NodeCard completo, con fondo atenuado, borde, Skin y acciones; se evita la fila alternativa sin contenedor.
- Crear borrador desde Núcleos: modo compacto del editor existente, con título, fotografía y su visor, pago/importe, vencimiento y micrófono. Cancelar/Guardar usan ModularPanelActions. No se modificó el guardado ni las reservas de adjuntos; edición de borradores existentes sigue completa.
- Cabecera: retirado el tipo Núcleo redundante (se conserva el modo Bash); resumen de tareas sin completar y Atención agrupados. El título, descripción, navegación y acciones permanecen.
- Validación: compilación correcta y tres pruebas focalizadas aprobadas (espacio de Skin en 240/360/640 dp con fuentes 1/1,5; captura/guardado sin proyecto/cancelación; capas sin resultados y navegación/filtros sin escribir datos).
- Pendiente en teléfono: nitidez con distintas fotografías y zoom, importación/visor durante captura, permiso y grabación reales de audio, pago/vencimiento y revisión visual en ambos temas. No hay APK nueva, cambio de versión, firma, commit o push.

## Revisión de las cinco correcciones — fotografía encuadrada y panel de captura

Confirmado en el código actual: las miniaturas de proyecto se solicitaban por
lado largo y zoom, sin compensar la proporción al llenar el marco cuadrado.
Ahora ProjectIdentityIcon usa readFramedThumbnail, que considera el lado corto y
conserva el límite de 2048 px y la caché cifrada existente. No reescribe originales
ni modifica importación, almacenamiento o backup; otras clases de avatar mantienen
su cálculo anterior. Las fotos antiguas ya reducidas no se reconstruyen.

La captura rápida ya ocultaba los campos avanzados, pero seguía usando la ventana
completa de NodeDialog. Solo esa modalidad utiliza ahora ModularPanelDialog,
con desplazamiento del formulario y Cancelar/Guardar en el área de acciones.
Los formularios completos permanecen intactos.

Ya estaban implementados y se conservaron: región estable del título/Skin,
tarjetas de capas sin resultados con contenedor completo y encabezado compacto
sin la etiqueta Núcleo redundante. No se modificaron calendario ni exportaciones.

Validación: una ejecución Gradle con compilación y dos pruebas focalizadas,
ambas aprobadas: ProjectPhotoCropQualityTest y el caso quickDraftCapture de
ContentEquivalenceUiTest. Se comprobaron panorámicas, verticales, cuadrados,
zoom 1×/2×, lectura repetida de caché, proporciones, bytes originales intactos,
apertura del panel compacto, guardado sin proyecto y cancelación sin escritura.
Log: /tmp/arachnode-five-ui-fixes.log. No se repitieron las pruebas previas sin
cambios relevantes.

Pendiente en teléfono: nitidez con fotos reales y zoom extremo, panel con teclado
y fuentes ampliadas, selector fotográfico, pago/vencimiento, grabación real y
aspecto en Charcoal, Dark y Green Bash. Sin APK, firma o cambio de versión.
