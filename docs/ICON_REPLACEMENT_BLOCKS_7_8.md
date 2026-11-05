# Bloques 7 y 8 — Exportar/compartir y controles comunes

## Bloque 7

Los 12 SVG se resuelven por función en `ExportControlIcons.kt`:

- Exportar/compartir: «Compartir y exportar» y «Exportar o compartir».
- Copiar elemento; Solo elemento; Con descendientes.
- Guardar imagen; Compartir imagen/Compartir como imagen; Guardar con descendientes.
- PDF / Word; PDF/Exportar PDF; Word / DOCX/Exportar Word/DOCX.
- Exportar Markdown; Exportar JSON.

Se aplican en los menús de exportación de `ContextActionMenu` y en las opciones
`share-export-*` de `ModularFieldMenu`, usadas por `ShareExportPanel` y
`CardImageMenu`. Los demás menús de campos conservan su presentación anterior.
El alcance «Con descendientes» bajo Guardar imagen utiliza el SVG de guardar con
descendientes; en copiar/Markdown/JSON utiliza el SVG genérico de descendientes.
No se cambian etiquetas, árbol de menús, exportadores, portapapeles ni archivos.

## Bloque 8

`CommonControlIcons.kt` resuelve funciones por descripción, no por el nombre del
icono: Confirmar/Guardar elemento, Cancelar/Cerrar, Volver/Atrás, Expandir/Desplegar
y Plegar. Los indicadores de completado, marcas de selección, quitar fecha o
quitar etiqueta quedan fuera. Los cierres del menú principal y de la alternancia
inferior conservan los recursos de los bloques 1 y 2.

- `LucideIcon` reutiliza los SVG solo para descripciones de esas acciones.
- Regreso en submenús y accesos existentes de cabecera, filtros y creación rápida.
- Expandir/plegar en `ModularPanelDialog` y calendario superior.
- Controles de volver/cerrar ya existentes en navegación de Apariencia, vista
  jerárquica, Relaciones, recuerdos, eventos, audio y títulos informativos.
- Cierre/cancelación del pie de edición existente en `EditDialogs`.

Los controles que usaban Material Icon conservan explícitamente LocalContentColor;
los demás mantienen su tinte anterior. No se añaden iconos a botones de texto ni
se modifica el campo de descripción, la selección o su prototipo.

## Recursos y comprobaciones

17 vectores Android: `export_control_*.xml` y `common_control_*.xml`, con trazados y
viewport 24 × 24 idénticos a los SVG originales, conservando grosor y proporciones.
No cambian tamaños, áreas táctiles, datos, versión ni se genera APK.

Pruebas focalizadas:

- ExportCommonIconsTest: 17 correspondencias, alcance de guardar imagen,
  exclusión de indicadores y recursos de bloques anteriores; renderizado en todos
  los temas oficiales a 18/24/28 y 240 px.
- ContextMenuUiTest.exportScopeUsesSamePanelAndSingleActionRunsDirectly: navegar
  alcance, callbacks de solo elemento/descendientes/compartir y regreso.
- ShareExportPanelUiTest.contextualActionsAndBothCopyScopesStayAvailable: controles
  existentes del panel y sus alcances.
- ModularPanelTest.immediateFieldsCommitOnlyConfirmedValueAndKeepMenuOnCancelAndBack:
  confirmación, cancelación y regreso en panel compartido.

Resultado final: cinco pruebas focalizadas aprobadas, sin fallos.

Pendiente: revisión visual física de los iconos en Android. Ningún SVG de estos
dos bloques queda sin correspondencia; no se anticipan otros bloques.
