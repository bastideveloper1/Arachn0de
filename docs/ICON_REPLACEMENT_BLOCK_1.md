# Sustitución de iconos — Bloque 1: barras inferiores

Alcance corregido: exclusivamente controles de las barras inferiores global y
contextual. Se retiran las asignaciones del bloque en LucideIcon y en acciones
contextuales generales; cabeceras, tarjetas y menús de otras pantallas conservan
sus iconos anteriores.

## Ubicaciones

- Barra global: Bloquear, Buscar, Filtrar u Ordenar según contexto, Menú.
- Barra contextual compartida: Contenido, Crear, Opciones.
- Barra de inicio: Crear núcleo, Crear borrador.
- Barra de Borradores: Crear borrador.
- Acciones inferiores de creación jerárquica: Desde plantilla y Nuevo elemento
  (este último conserva su etiqueta visible «Nuevo»).
- Alternancia fija: abrir con `ic_menu_a_hamburguesa.svg`, cerrar con
  `ic_cerrar_menu.svg`. Conserva las descripciones accesibles «Mostrar barra
  global» / «Mostrar barra contextual» y sus acciones originales.

Las trece funciones tienen correspondencia en estos controles; Filtrar y Ordenar
son alternativas contextuales, no se añaden botones. No se usan `ic_menu.svg` ni
la variante B. El desbordamiento conserva las mismas correspondencias de las
acciones que contiene. No se cambia el icono genérico «Más acciones».

## Recursos y comprobaciones

13 vectores Android `custom_*.xml`: trazados idénticos al SVG, viewport 24 × 24,
grosor 1.75, extremos/uniones redondeados, círculos rellenos en Opciones. Tinte,
tamaños, márgenes y áreas táctiles originales conservados.

La prueba de renderizado del bloque anterior aprobó los 13 recursos a 18, 24,
28 y 240 px con tintes de todos los temas oficiales; no se repite porque no
cambian los vectores ni sus tintes. Se ejecutan de nuevo únicamente la prueba de
correspondencias (incluye ausencia de sustituciones en acciones generales) y las
tres pruebas de alternancia, desbordamiento y conservación del estado al volver.

Pendiente: apreciación visual y uso táctil físicos en Android. No se genera APK
ni se cambia versión, lógica de navegación o persistencia.
