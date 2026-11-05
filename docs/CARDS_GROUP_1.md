# Grupo 1 — Tarjetas

## Ajuste puntual posterior del menú exterior

La decisión posterior del usuario sustituye la propuesta de reorganización del apartado 4: no se reorganiza el menú. Se retira exclusivamente la entrada Contenido; el acceso sigue siendo tocar la tarjeta y abrir su panel inferior. Las variantes que ya ofrecían Editar conservan esa acción.

Favoritos utiliza las etiquetas exteriores «Marcar como favorito» / «Quitar de favoritos». Las etiquetas de los paneles interiores permanecen iguales. Se añade «Marcar como urgente» / «Quitar urgencia» a tareas hoja y Núcleos ordinarios, que ya admitían prioridad propia; Capas, Notas y Núcleos Banco especializados no reciben esa acción. Se reutilizan `NodeRepository.togglePriority` y `saveCoreMetadata` con sus validaciones y transacciones existentes. La bandera del Núcleo refleja también su prioridad propia, conservando el resumen de urgencia de sus descendientes. No se cambian el modelo, esquema, backups ni repositorios.

Todas las demás declaraciones de acciones y sus callbacks permanecen en el mismo orden, incluida Seleccionar. No se aplica la anterior propuesta de enfatizar o desplazar acciones.

`CardFavoritePlacementTest` verifica ambos sentidos de Favoritos y Urgencia, sus datos e indicadores, los tipos excluidos, la ausencia de Contenido, las otras acciones en el mismo orden tras las operaciones y el callback de Seleccionar. Su primer método conserva la comprobación de Favoritos desde el interior, entrando ahora al tocar la tarjeta. Se adaptan los dos métodos afectados de `ContentEquivalenceUiTest` para utilizar este acceso existente.

Archivos de producción de este ajuste: `NodeComponents.kt`, `ProjectComponents.kt`, `ElementAccessUi.kt` y `CompactCardIndicators.kt`. Sin cambios de versión ni APK; revisión física pendiente.

Resultado del ajuste puntual: cuatro métodos focalizados aprobados (los dos de `CardFavoritePlacementTest` y los dos afectados de `ContentEquivalenceUiTest`). Se corrigieron dos expectativas del test: distinguir la tarjeta clicable del título del menú y conservar Guardar como plantilla, que ya estaba implementado. No se modificó producción para satisfacer esas expectativas antiguas. `git diff --check` sin errores.

## 1. Efecto al iniciar el arrastre

`DragReorderState.cardModifier` añadía una sombra de 8 dp a la capa gráfica de la tarjeta arrastrada. Se elimina únicamente esa elevación decorativa. Se conservan la traslación que sigue al dedo, el orden Z, las animaciones de colocación de las tarjetas vecinas y todo el motor de reordenamiento.

Estado: simplificación implementada. Sin grabación o dispositivo no puede asegurarse que esa sombra sea todo el efecto señalado por el usuario. Si persiste, hace falta observarlo antes de cambiar gestos, animaciones o el ripple.

Pruebas aprobadas: `DragViewportTest.movingToZeroKeepsViewportAndPersistsSameOrder` y `grabbedCardKeepsSizeAndFollowsFingerWithoutChangingOrder`. Comprueban posición del viewport, orden remitido al callback, tamaño constante, traslación respecto al dedo y ausencia de commit cuando no cambia la posición. No equivalen a una revisión visual táctil.

Archivo: `app/src/main/java/com/r0ybt/arachn0de/ui/DragReorder.kt`.

## 2. Espacio inferior de las tarjetas

La altura mínima compartida era 88 dp incluso sin etiqueta en la esquina. Se ajusta a 64 dp: objetivo táctil de 48 dp más los márgenes verticales existentes de 8 dp. Es un mínimo, no una altura fija: el contenido sigue determinando el crecimiento. La etiqueta conserva su reserva medida para no invadir los controles. No cambian el texto, sus límites previos, metadatos, orden ni paddings.

Estado: implementado en las tarjetas que comparten `CompactCardHeader` y `CompactCardMinHeight` (Núcleos, Capas, Tareas y Notas). Las variantes especiales conservan sus reglas existentes.

Pruebas aprobadas: `CompactCardUniformityTest.commonBaseHeightRealCornerAndMeasuredClearanceAcrossCardTypes` y `CardCompactionBlockThreeTest.actualCardsKeepOneProgressRowAndActionsClearAcrossWidthsTitlesAndFontSizes`. Cubren títulos cortos/largos, etiquetas únicas/múltiples, iconos jerárquicos, progreso, anchos 260/360 dp y fuentes 1/1,5. Pendiente apreciación visual en Android con contenido real.

Archivo: `app/src/main/java/com/r0ybt/arachn0de/ui/CompactCardHeader.kt`.

## 3. Favoritos y Opciones

La compactación visual de los botones ya existía (icono 18 dp, fondo 28 dp, objetivo táctil 48 dp). Tras la decisión del usuario se retira el botón de Favoritos de las tarjetas de la jerarquía habitual, recuperando sus 48 dp para el contenido. Se conserva Opciones con su objetivo táctil completo.

Marcar/Quitar de Favoritos se ofrece en Opciones de la vista previa y en Contenido del interior de Núcleos, Capas, Tareas y Notas. Opciones del interior también ofrece la misma acción. La fila de indicadores muestra una estrella informativa de 16 dp solo para favoritos confirmados; no es otro botón ni ocupa espacio cuando está ausente. La variante de Capa vacía de las vistas por estado utiliza también ese indicador.

La nueva presentación reutiliza `FavoriteFeedback`, `OperationState`, el repositorio y la preferencia de Favoritos existentes. El control anterior delega en la misma lógica, para conservar su funcionamiento en las pantallas especializadas no modificadas. No se cambia persistencia, formato de backup ni esquema. Banco, Kanban, buscadores y Borradores conservan sus controles especializados; no se amplía el rediseño a esos módulos.

Estado: implementado para las tarjetas compartidas y sus paneles de Contenido/Opciones.

Pruebas aprobadas:

- `CompactCardUniformityTest.favoriteIsInformationalAndOptionsKeepTouchTarget`: cuatro tipos de tarjeta, anchos 260/360 dp, fuentes 1/1,5 y ambos estados. Sin botón exterior, estrella solo si corresponde, objetivo de Opciones de al menos 48×48 dp y título sin invadirlo.
- `CardFavoritePlacementTest.previewOptionsAndInteriorContentShareExistingFavoriteRecord`: marcar desde Opciones y quitar desde Contenido en los cuatro tipos, comprobando el registro real de Favoritos y que las tarjetas y el Núcleo permanecen idénticos.

Pendiente revisión física de alcance táctil y legibilidad de los indicadores con skins reales.

Archivos de producción bajo `app/src/main/java/com/r0ybt/arachn0de/ui/`:

- `ElementAccessUi.kt`: lógica de interacción compartida entre botón existente y acción contextual; estrella informativa.
- `NodeComponents.kt` y `ProjectComponents.kt`: retirar botón exterior e incluir Favoritos en Opciones.
- `CompactCardIndicators.kt` y `TaskViewSelector.kt`: indicador de favorito.
- `ProjectScreen.kt`: acción del contexto interior.
- `NodeBottomModule.kt`, `CoreContentModule.kt` y `CoreBottomModule.kt`: acceso desde Contenido reutilizando las listas de acciones existentes.

## 4. Organización de Opciones

Estado: incorporación de Favoritos implementada por decisión expresa; reorganización general pendiente de decisión.

Los menús de las tarjetas declaran acciones de Contenido/Editar, Responsables, Mover a, Historial, Seleccionar, Cambiar estado, Fijar, Estados, Fotografía, plantillas, limpieza, conversiones, exportación y eliminación según el tipo, las capacidades y el contexto. Contenido abre el panel inferior del propietario; Mover abre el selector existente; Responsables su edición; Historial el registro; Estados configura y Cambiar estado opera sobre el estado. Los demás callbacks conservan sus destinos actuales.

`ContextActionMenu` ya agrupa conversiones y exportaciones, separa acciones destructivas y enfatiza Mover a. Por ello cambiar solo el orden de las declaraciones no bastaría para reorganizar todas las posiciones. La configuración y las plantillas pueden preceder a operaciones de contenido o responsabilidades. No hay datos de frecuencia de uso que justifiquen retirar funciones.

Accesos ya existentes: abrir tarjeta tocando su contenido, checkbox para completar cuando corresponde, recurrencia desde su indicador y Contenido desde el panel inferior del interior. Cambiar estado conserva opciones que el checkbox no sustituye. Favoritos tiene ahora los dos accesos solicitados; no se mantiene además un botón exterior.

Propuesta para autorizar antes de implementarla:

1. En Capas/Tareas/Notas, enfatizar Contenido junto a Mover a, conservando las capacidades actuales de cada tarjeta.
2. Situar Favoritos, Responsables e Historial al comienzo del bloque restante cuando estén disponibles. Mantener Seleccionar y Cambiar estado accesibles.
3. En Núcleos, enfatizar Contenido y colocar Favoritos/Fijar/Fotografía antes de las acciones de configuración.
4. Conservar Estados, plantillas, limpieza, conversiones y exportaciones, sus submenús existentes y Eliminar separado. No añadir acciones ni modificar el menú compartido de otras pantallas.

No se ejecutan pruebas de una reorganización no implementada.

## Verificación y límites

Se ejecutaron solo métodos focalizados mediante `:app:testDebugUnitTest --offline --tests …`, con Robolectric. Seis comprobaciones distintas vigentes aprobadas; las de geometría se repitieron solo tras retirar el botón de Favoritos. La comprobación inicial de ambos botones se sustituyó por la correspondiente a la decisión posterior del usuario.

`git diff --check` sin errores. No se modifica versión, no se genera APK, no se cambia jerarquía ni persistencia. Se conservan los cambios previos del repositorio. No se toca el editor de descripciones ni el prototipo aplazado.

Archivos de pruebas: `DragViewportTest.kt`, `CompactCardUniformityTest.kt` y el nuevo `CardFavoritePlacementTest.kt`. Se reutiliza además el método existente de `CardCompactionBlockThreeTest.kt` sin modificarlo.
