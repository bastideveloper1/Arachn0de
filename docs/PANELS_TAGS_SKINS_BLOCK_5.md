# Bloque 5 — Paneles, etiquetas y skins

## Cambios y validación

- El selector guardaba la selección manual, pero permanecía abierto. Ahora cierra después de un guardado exitoso en Núcleos, Capas, Tareas y Notas. Ante fallo conserva la asignación anterior y el panel. Crear o editar etiquetas sigue dentro del panel: se distingue esa operación de elegir una etiqueta existente.
- El selector reutilizable cierra tras cada elección. En los lugares que habilitan selección múltiple se puede reabrir para añadir o quitar etiquetas sin reemplazar las otras. Se conservan las políticas de asignación existentes de cada editor; no se amplió ni restringió la persistencia.
- El número de Capa ya estaba separado del título como texto pequeño. Se reutiliza `Badge` de Material con los roles existentes de superficie de control y texto secundario. Es una insignia pequeña, sin borde ni texto adicional, alineada con la primera línea. Solo cambia el encabezado interior; no las tarjetas de vista previa. No se modifica el título, la profundidad ni la jerarquía.

Componentes: `TagUi`, `NodePropertyEditor`, `NodeBottomModule`, `CoreContentModule` y `DetailPresentation`.

Seis pruebas focalizadas pasaron mediante `:app:testDebugUnitTest --offline`: dos de `TagPanelAdjustmentsTest` (cierre/reapertura y selección múltiple), asignación inmediata en los cuatro tipos de propietario de `NodePropertyEditingTest`, `ImmediateTagAssignmentTest` (fallos/cancelación/duplicados), `TagPanelAdminTest` (creación/edición/eliminación) y el encabezado de `CalendarNavigationBlockTwoTest`. Esta última se repitió, también con resultado satisfactorio, ampliando la matriz a skin personalizada, ancho de 280 dp, fuentes 1×/1,5×, títulos largos y niveles 1/12. `git diff --check` sin errores. No se generó APK oficial.

## Auditoría estática de temas

Charcoal es `AppearanceTheme.Arachn0de`, el valor predeterminado de `AppearanceSettings` y el respaldo del lector de preferencias. No se ha modificado ningún tema, catálogo, preferencia, imagen ni motor de skins.

Valores reales: fondo / superficie / elevada / borde (hexadecimal RGB).

| Tema | Diferencia real | Ventaja | Redundancia | Recomendación | Riesgo |
| ---- | --------------- | ------- | ----------- | ------------- | ------ |
| Charcoal | `121212 / 242424 / 303030 / 505050`; texto `EEEEEE`; selección `484848` | Referencia oficial equilibrada; texto/superficie 13,38:1 | Referencia | **Conservar**, principal y predeterminado | Bordes discretos; no equivalen a un tema de alto contraste |
| Dark Grey | `1C1C1C / 282828 / 383838 / 555555`; texto igual a Charcoal | Superficies algo más claras | Alta: mismos componentes e identidad, diferencias pequeñas | **Retirar** del catálogo futuro, conservando compatibilidad | Icono inactivo `808080` sobre elevada: 2,97:1; una retirada directa alteraría selecciones y skins existentes |
| Pure Black | `000000 / 0C0C0C / 202020 / 484848` | Fondo negro real; texto/superficie 16,86:1; inactivo/elevada 4,13:1 | Media frente a Charcoal; aporta un extremo oscuro distinto | **Conservar** | Superficie y fondo se distinguen poco; no convierte toda la pantalla en negro puro |
| Black & White | `000000 / 101010 / 242424 / D0D0D0`; texto `FFFFFF`; selección `D0D0D0` | Bordes claramente visibles: 12,34:1 sobre superficie | Media respecto de Pure Black, pero bordes muy diferentes | **Revisar visualmente**, especialmente estados seleccionados | Texto blanco/selección: 1,54:1; texto secundario/selección: 1:1; no es alto contraste coherente en todos sus roles |
| Graphite | `262626 / 343434 / 424242 / 606060` | Alternativa visiblemente más clara; texto/superficie 10,73:1 | Alta con Dark Grey, menor con Charcoal | **Revisar visualmente**; conservar solo si aporta una preferencia útil frente a Charcoal | Inactivo/elevada: 2,54:1; secundario/selección: 4,08:1 |

Los ratios se calcularon a partir de los colores opacos del código con luminancia sRGB; son comparaciones de roles concretos, no una certificación de todas las pantallas. Los controles deshabilitados no están sujetos al mismo requisito que los activos; su ratio se incluye para evaluar visibilidad. Los bordes suaves no son por sí mismos un fallo si no son la única señal que identifica un control. El problema de selección de Black & White depende del componente que consuma ese rol: el selector de etiquetas usa `controls`, no `selection`, por lo que no corresponde atribuírselo a todas las selecciones.

Los cuatro temas comparados comparten `monochrome()`: texto secundario/acento `D0D0D0`, controles y botones con el color elevado, iconos activos derivados del texto e inactivos `808080`. Cambian también los paneles: todos usan su superficie; Charcoal conserva un panel de tareas `202224`. No hay componentes ni funciones exclusivos por estos nombres. Los estados funcionales se resuelven por el mismo sistema compartido.

Las skins usan `CustomTheme.base`: sus colores y pinturas sustituyen roles concretos y los demás se heredan de esa base. Las imágenes y protecciones de contraste son compartidas; `imageControlTint` puede sustituir por blanco un icono oscuro sobre imagen. Esa protección no corrige automáticamente todos los fondos de selección. El analizador `themeContrast` ya revisa texto, iconos y otros roles sobre superficies y gradientes de skins, sin sobrescribir los colores elegidos. Por ello, comparar solo los nombres de temas o una skin con todos los roles reemplazados ocultaría diferencias reales.

## Retirada futura, pendiente de aprobación

No borrar entradas del enum ni renombrar sus identificadores persistidos. El catálogo actual itera `AppearanceTheme.entries`; una retirada segura puede ocultar opciones para nuevas elecciones manteniendo sus paletas y lectores como compatibilidad histórica. Los usuarios que ya las tengan activas conservarían exactamente su aspecto, y podrían cambiar voluntariamente a Charcoal.

Esto también preservaría skins cuyo campo `base` usa esos identificadores, preferencias, importaciones y respaldos antiguos. `CustomThemeCodec` actualmente decodifica `base` con `AppearanceTheme.valueOf`: eliminar el enum rompería esa lectura. Si más adelante se quisiera eliminar también su implementación, haría falta una migración explícita que conserve todos los roles heredados, validación previa y pruebas dirigidas conforme al contrato de persistencia y backup. No basta con recurrir silenciosamente a Charcoal, porque cambiaría el aspecto existente.

Pendientes: aprobar cualquier retirada del catálogo y revisar visualmente Black & White y Graphite en dispositivo. Ninguna de esas acciones se implementó en este bloque.
