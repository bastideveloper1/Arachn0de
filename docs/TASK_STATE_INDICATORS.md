# Indicadores de tareas por estado

## Cambios

- `InteriorTaskCounts`: conserva el recorrido del ámbito real y separa los IDs con estado `PENDING` y `VERIFY`. Solo incluye tareas completables de ese Núcleo y, dentro de una Capa, sus descendientes. Los conjuntos de IDs y el conjunto de visitados evitan duplicaciones. Se excluyen completadas, Notas y contenedores. El cálculo de prioridad alta sigue incluyendo todos los estados sin completar, igual que antes.
- `InteriorTaskStateIndicators` y `CardIndicatorDetail`: el interior muestra dos pares compactos de icono oficial y cantidad, agrupados en una misma fila. No tienen acciones, filtros ni navegación. Se muestra cero explícitamente; el ojo solo aparece cuando el contenedor utiliza tres estados.
- `ProjectScreen`: entrega el ámbito real y resuelve el modo con `TaskStatePolicy`, respetando la configuración del Núcleo y las anulaciones heredadas de Capas. Los conteos son independientes del filtro de la lista y del calendario.
- `CompactCardIndicators`, `NodeComponents` y la tarjeta de Kanban: Tareas y Notas omiten los espacios de tareas contenidas y de verificación de tareas contenidas, tanto activos como inactivos. Se conserva el control propio de estado de la Tarea, incluido su ojo Por verificar. Los demás metadatos mantienen orden, separación, colores y activación.

Las vistas previas de Núcleos y Capas conservan sus indicadores y resumen compacto anteriores. No se cambian cálculos de progreso, jerarquía, transiciones, datos, skins, menú global ni Pagos.

## Comprobaciones dirigidas

Se reutilizan `InteriorTaskCountsTest`, `InteriorIndicatorCountUiTest`, `CardCompactionBlockThreeTest` y `ContextCalendarIntegrationTest`:

- Ámbitos de Núcleo, Capa y Capa anidada con pendientes, por verificar, completadas y Notas, sin mezclar otro Núcleo.
- Ámbitos únicamente pendientes, únicamente por verificar, vacíos y con solo completadas; conjuntos de estados disjuntos.
- Dos y tres estados en la presentación; ceros explícitos y ausencia de acciones en los contadores.
- Pantallas reales de Núcleo y Capas anidadas, contrastando las cantidades con las tareas creadas mediante los repositorios existentes.
- Tarjetas reales de Tarea y Nota con metadatos activos/inactivos, sin espacios de tareas contenidas; estado propio Por verificar conservado.
- Regresión de tarjetas compactas con distintos anchos, fuentes, títulos y skins, y de orden/separación de sus iconos.

Diez pruebas dirigidas distintas pasaron. Las pruebas visuales automatizadas usan Robolectric; no se realizó una revisión en dispositivo físico. No se genera ni firma un APK oficial.
