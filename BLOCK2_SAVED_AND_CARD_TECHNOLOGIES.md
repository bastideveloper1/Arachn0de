# Bloque 2: Guardados y Tecnologías en tarjetas

## Cambios

Las tarjetas de Proyecto y Nodo usan una vista previa decorativa de Tecnologías: máximo tres iconos de 20 dp, separación de 3 dp, orden persistente y énfasis discreto en el primero. No incluye botones ni gestos propios: los toques sobre los logotipos llegan a la tarjeta. Sin Tecnologías no se compone ningún contenedor. No se eliminan asociaciones ni cambia el catálogo. El detalle conserva su consulta individual y la edición mantiene sus selectores.

Guardados ya tenía catálogo local, creación desde tareas/pagos/viajes, búsqueda, edición/renombrado, eliminación confirmada, fechas relativas, referencias y backup. Se conserva ese sistema. «Usar Guardado» queda fijo arriba del formulario, tras el tipo y antes del área desplazable, tanto en creación como en edición; se evita el acceso recursivo desde el editor de plantillas. Se añade «Consultar» sin aplicar ni modificar el borrador. Aplicar sobre un borrador con contenido sigue solicitando confirmación. No guarda automáticamente.

Al aplicar durante edición se conserva identidad de nodo, padre y vínculo/revisión del viaje existente. Una plantilla sin viaje no elimina su configuración; una con viaje reemplaza configuración conservando identidad y controles de concurrencia. Las sesiones siguen bajo la lógica existente, sin copiar seguimiento activo. No se modifican archivos Metro ni su persistencia.

## Archivos del bloque

- ui/TechnologyComponents.kt: tamaño configurable del icono y grupo decorativo para tarjetas.
- ui/NodeComponents.kt y ui/ProjectComponents.kt: activación de la vista previa decorativa.
- ui/EditDialogs.kt: acceso alto también en edición.
- ui/SavedTemplatesUi.kt: consulta, fechas legibles y aplicación conservando vínculo de instancia.
- ui/TechnologyUiTest.kt y templates/SavedTemplatesUiTest.kt: comprobaciones dirigidas.

Sin migraciones, nuevos archivos multimedia, cambios del formato de backup ni incremento de versión. Se respeta el contrato de persistencia: las plantillas y posiciones ya se exportan/restauran; referencias e imágenes pertenecen a sus catálogos. Se preservan los cambios previos. Sin firma, commit, push ni APK de distribución.

## Validación

Pruebas dirigidas: TechnologyUiTest (13), TechnologyRepositoryTest (10), SavedTemplatesMigrationTest (2), SavedTemplatesTest (9), SavedTemplatesUiTest (6): 40 casos distintos. La primera ejecución detectó un fixture con plan de viaje ficticio malformado; la prueba de conservación del vínculo se aisló en la biblioteca, y la visibilidad en edición se verificó por separado. Las comprobaciones incluyen límite/orden/tamaño de logotipos, toque sobre logotipo llegando al contenedor, conservación de más de tres asociaciones y orden tras reapertura, CRUD de Guardados, aplicación editable y confirmación, backup/restauración con posiciones, lectura histórica y rechazo de corrupción.

## Teléfono y límites

Comprobar toques sobre los tres logotipos en tarjetas reales de Proyectos/Capas/Tareas, contraste en varios temas y fuente grande; detalle individual, edición de orden, acceso fijo con teclado, cancelación y aplicación de Guardados para pagos/viajes. Los adjuntos de la descripción original se excluyen explícitamente de las plantillas, conservando el texto según el contrato existente; no se prometen bytes ausentes.

Las pruebas JVM no certifican nuevamente SQLCipher nativo. Los cambios invalidan el hash de evidencia anterior de release; este bloque no prepara distribución ni actualiza esa certificación.
