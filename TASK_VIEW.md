# Selector Ver

- Cuarta acción de la barra estándar: Contenido, Crear | Capas, Ver, Opciones.
  Sprint/Kanban conservan su navegación y sus selectores independientes.
- Todas conserva los grupos y reglas de arrastre anteriores, según autorización.
  Las vistas filtradas conservan capas sin coincidencias como filas compactas,
  con icono, contador 0 y acceso a favoritos y opciones. La lista raíz de Núcleos
  no está afectada por este selector interior; no se añade otra implementación.
- Los contadores usan tareas únicas y los mismos filtros de fecha, Atención,
  Favoritos, persona, etiqueta, tiempo y prioridad, sin filtrar primero por estado.
- El diseño mide con TextMeasurer y la tipografía/densidad reales el ancho necesario
  de cada posible etiqueta activa completa, sus contadores, iconos, padding y gaps.
  Si algún caso no cabe, usa filas de dos celdas con etiquetas completas; con tres
  opciones la última ocupa todo el ancho. No hay umbral fijo de ancho/fuente.
- La vista es un único valor en ViewModel de Activity, sin SavedStateHandle ni
  preferencias: sobrevive a rotación/navegación y empieza en Todas en proceso nuevo.
  No forma parte de Room ni del backup. Abrir el selector no guarda datos.
- Deshacer reutiliza setTaskStatus con validación del último estado observado.
  Conserva el historial append-only y el orden persistido; una referencia visual
  temporal devuelve la tarea al lugar anterior, invalidada por escrituras posteriores.
- Se reutiliza el SnackbarHost existente, con sustitución del aviso anterior. La
  lista reserva la altura real del módulo y del aviso, sin tarjetas por detrás.
- Con el selector abierto se permite desplazar la lista, pero sus tarjetas y
  controles ignoran toques hasta cerrarlo, según la aclaración aprobada.

Validación: compilación Debug y 15 pruebas iniciales aprobadas; el caso de bloqueo
se validó después de forma aislada con geometría real de LazyColumn. La inyección
del gesto largo no activó el aviso en Robolectric: confirmar ese gesto en teléfono,
junto con contraste de skins y ergonomía con fuentes grandes.
