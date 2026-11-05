# Control de estados actual

Las tarjetas normales usan un único control: vacío (Pendiente), scan-eye (Por verificar)
y marcado con título tachado (Completada). «Estados de tareas» permite 2/3 en el
Núcleo y Heredar/2/3 en capas y tareas; prevalece la excepción más cercana.
Al pasar efectivamente de 3 a 2, se confirma el número de tareas Por verificar que
pasarán a Pendiente. Se respetan excepciones; cancelar no escribe nada y volver a 3
no restaura estados anteriores. Sin configuración previa se usan 2.
La configuración se abre en un submenú junto a «Estados de tareas…»; las capas y
tareas ofrecen Heredar del contenedor/2/3, con una marca en la opción activa.
El filtro normal está en la acción «Ver» de la barra inferior de Núcleos y Capas;
no se duplica en Filtros ni utiliza cápsula flotante. Véase TASK_VIEW.md.
Sprint conserva su selector y sus cinco fases. Los contenedores muestran un indicador
discreto solo cuando queda trabajo Pendiente o Por verificar, sin rótulos ni barras.
Room 51 y JSON 44 incluyen las excepciones y conservan backups históricos; véanse
ARCHITECTURE.md y BACKUP_FORMAT.md para las reglas de integridad y recuperación.

El resto de este documento describe la implementación histórica anterior.

# Selector de estados

Se conservan los cambios previos del árbol. No se crean APK, commits ni publicaciones.

## Comportamiento

- Los Núcleos mantienen Pendiente y Completada por defecto. Desde sus Opciones se puede habilitar «Por verificar». La opción pertenece al Núcleo; todas sus capas la heredan y los demás Núcleos permanecen independientes.
- «Por verificar» es una tarea ejecutada pendiente de validación: `isCompleted=false`, `awaitingVerification=true`. No suma al progreso completado ni entra en la limpieza de completadas. Se puede pasar directamente de Pendiente a Completada.
- La cápsula filtra; nunca escribe el estado de las tareas. Tocar de nuevo el filtro seleccionado muestra todos. El filtro conserva los ancestros necesarios para entender resultados anidados. Los cambios efectivos se ofrecen en «Cambiar estado» de las acciones de la tarea y en su detalle.
- No se puede desactivar la opción mientras quede alguna tarea por verificar en cualquier capa del Núcleo. Tampoco convertir esas tareas en notas, introducirlas en Sprint ni trasladarlas a Borradores o a un Núcleo que no admita ese estado. Se conserva el estado ante el rechazo.
- Promover una capa a Núcleo hereda la opción del origen y conserva los estados. Convertir un Núcleo con la opción habilitada en capa requiere resolver las tareas y deshabilitarla primero, evitando cambiar implícitamente la configuración del destino.
- La gestión existente usa exactamente cinco fases, en su orden histórico: No planificada, Planificada, Haciendo, Terminada y Validada. `WorkState`, sus transiciones y sus reglas de completado se conservan. El Kanban reutiliza la cápsula y mantiene también su selector de fase, incluida «Sin fase Sprint» cuando corresponda.
- Apariencia ofrece una única orientación global: Vertical (predeterminada) u Horizontal. En vertical las cinco fases forman dos columnas compactas; en horizontal se ajustan a filas si el ancho disponible no permite los cinco objetivos de 48 dp.
- La cápsula flota a la derecha encima del módulo inferior medido, dentro del área segura existente. Las tarjetas mantienen su ancho; solamente los botones de la cápsula capturan toques. Los indicadores miden 24 dp, el objetivo táctil 48 dp; no hay rótulos interiores, y cada botón tiene descripción y selección accesibles. Los colores de tareas son semánticos; gestión reutiliza sus tokens existentes y el icono adapta el contraste al fondo.

## Persistencia y backup

Room 49→50 añade únicamente `projects.verificationEnabled` y `nodes.awaitingVerification`, ambos booleanos NOT NULL con default 0. No reescribe registros anteriores, adjuntos, fases, identidades ni relaciones. Los triggers impiden combinaciones incompatibles y la desactivación con tareas por verificar, incluso fuera de la UI.

JSON 43 incluye ambos campos y se emite cuando se utiliza la opción, el estado o la preferencia de orientación. Se conservan lectores 1–42 con defaults false. La orientación se guarda en el namespace cifrado existente `appearance_preferences`, clave `status_selector_direction`, valores VERTICAL/HORIZONTAL. Su ausencia significa Vertical. Se valida antes de restaurar y se incluye en el snapshot/restauración transaccional existente de preferencias.

Los candidatos con estados incompatibles, propietarios ausentes, tareas completadas a la vez que por verificar, fases Sprint o contenedores con ese estado se rechazan antes de reemplazar datos. No se añaden archivos privados, reservas ni cachés. Los bytes y el ciclo de vida de adjuntos continúan por las rutas anteriores.

## Archivos principales

- `ui/StatusSelector.kt`: cápsula compartida y representaciones de ambos dominios.
- `ui/ProjectScreen.kt`, `ui/NodeComponents.kt`, `ui/ManagedKanbanUi.kt`: filtros, acciones y presentación.
- `ui/AppearanceScreen.kt`, `ui/theme/Appearance.kt`: preferencia global.
- `domain/model/TaskStatus.kt`, `Node.kt`, `Project.kt`, `NodeFilters.kt`: estado de tareas normales y filtro sin escrituras.
- `data/local/VerificationMigration.kt`, entidades, DAOs y esquema Room 50: almacenamiento e invariantes.
- `data/repository/NodeRepository.kt`, `ProjectRepository.kt`, `ProjectNestingRepository.kt`, `SprintLinkRepository.kt`: transiciones y compatibilidad de movimientos/conversiones.
- `backup/BackupJson.kt`, `BackupData.kt`: versionado, defaults y validación.
- Exportadores de texto/Markdown, informes y tarjetas compartidas: etiqueta real «Por verificar».
- `status/TaskStatusTest.kt`, `VerificationMigrationTest.kt`, `StatusSelectorUiTest.kt`, `CoreStatusUiTest.kt`: pruebas nuevas. Aserciones de versión de pruebas históricas actualizadas a Room 50; comparación de migración de Borradores limitada a sus columnas históricas.

## Validación

Validación final: **39 pruebas, 9 clases, cero fallos**, ejecutadas con:

```text
./gradlew testDebugUnitTest --offline --tests '*status.*' --tests '*drafts.*' --tests '*data.NodePurposeTest' --tests '*ui.SprintUiTest' --tests '*ui.KanbanUiTest'
```

Incluye 12 pruebas nuevas: herencia por varias capas y aislamiento de Núcleos; transiciones directas; bloqueo de desactivación/conversión/Sprint/destinos incompatibles; promoción y traslado compatibles; filtros sin escrituras; exclusión de limpieza; reinicio y backup cifrado/restauración; rechazo previo de candidatos incompatibles; migración 49→50 con comparación de todas las tablas y foreign_key_check; cápsulas de dos/tres/cinco indicadores, selección accesible, orientación global, anchura reducida, texto grande, tema personalizado y toques fuera del componente. Las pantallas reales de Núcleos y Kanban confirman que filtrar no modifica los registros.

Regresiones: Borradores (incluido audio y migración 48→50), propósitos, las cinco fases Sprint y Kanban. Se actualizó el helper antiguo de SprintUiTest para utilizar el módulo de opciones ya existente o el botón actual de cabecera, y reconocer el menú que sigue abierto al cancelar. Sus cuatro pruebas pasan; no se alteraron las reglas de Sprint. Compilación Kotlin y `git diff --check` correctos. Log final local: `/tmp/traffic-final2.log`. No se ejecutó la suite global.

Pendiente inspección en teléfono de TalkBack, contraste visual con skins arbitrarios y navegación por gestos/botones, pantalla dividida y teclado. No se ha generado APK, firmado, preparado release, hecho commit ni push.
