# Dogfooding 1: creación y consulta compactas

Versión conservada: **0.3.0 / 10**. Cambios previos conservados; sin commit, push, firma, desinstalación ni limpieza de datos.

## Implementación

- Tarea/Nota/Capa permanece sobre el área desplazable del formulario. Se mantienen creación, edición, conversiones, plantillas, lotes, recurrencia y Metro.
- Un único acceso compacto a Tecnologías abre una cuadrícula adaptable lazy con iconos/miniaturas, nombres, búsqueda y selección múltiple visible. Cancelar conserva la selección anterior; confirmar actualiza el borrador.
- Ordenar seleccionadas conserva arrastre y añade flechas accesibles. Las posiciones se guardan por dueño, sin modificar la biblioteca global. Los proyectos guardan texto y asignaciones en una transacción; una referencia inexistente conserva el estado anterior.
- Consulta oculta secciones vacías, prioriza tres tecnologías y permite abrir la cuadrícula completa. Pulsar abre detalles, nunca desasigna. Tecnologías y participantes comparten el ancho cuando existen ambas, y la sección única utiliza el disponible. Los participantes adicionales tienen acceso a un diálogo con nombres y avatares.
- Timer, HourglassBottom y LocalFireDepartment sustituyen textos redundantes en la vista previa y usan el acento del tema. Sus descripciones identifican tarea propia, tareas descendientes o tareas del proyecto. Hoy tiene precedencia sobre futuro para una misma tarea. Completadas, notas y fechas propias de capas quedan excluidas; se conservan avisos de atraso y los detalles temporales. Los filtros y cálculos originales de atención siguen separados de estos indicadores.

## Persistencia y seguridad

Sin cambios de esquema Room 26, formato JSON 18 / ANBACK01 ni almacenamiento de iconos. El orden utiliza las posiciones existentes; las pruebas verifican restauración del orden de proyecto/tarea, asociaciones, bytes de iconos, compatibilidad histórica, reapertura y rollback ante referencias ausentes. No se crean archivos de usuario nuevos; su liberación mantiene los protocolos existentes.

No se modifican SQLCipher, contraseñas principal/señuelo, envolturas de claves, sesiones independientes, bloqueo ni cifrado de backups. Los borradores continúan bajo el registro privado existente y las miniaturas se leen desde el contexto del almacén autenticado.

## Validación

La tanda principal aprobó **171 pruebas, cero fallos**, incluyendo Tecnologías, backups, creación, formularios, indicadores/atención y 30 pruebas dirigidas de seguridad. Compilaron Debug y AndroidTest. Log local: `/tmp/dogfood-final-tests.log`.

En conjunto quedaron aprobados **183 casos distintos** (171 de la tanda principal, 11 adicionales de Metro/historial y la nueva prueba de adaptación). `git diff --check` aprobado y los cinco PNG originales conservan sus bytes en la APK final Debug.

La tanda de ajustes cubre cálculo agregado y ausencia de márgenes vacíos, consulta/selección, Metro nativo desde el formulario (5 pruebas) e historial de tarjetas (6 pruebas). La prueba de anchura de 280 dp y fuente 1,8 confirma controles dentro del ancho disponible y acceso al detalle del participante. Se conserva la prueba de tipo fijo al desplazar el formulario a 320 dp y la búsqueda/cancelación con 500 tecnologías. Ver logs `/tmp/dogfood-final-adjustments2.log` y `/tmp/dogfood-narrow-final.log`.

## Límites

La validación visual y de navegación usa Compose/Robolectric; queda pendiente dogfooding táctil en teléfono físico. No se genera APK release. La evidencia instrumentada SQLCipher de la preparación anterior no se reescribe: el preflight bloquea la firma hasta ejecutar de nuevo las pruebas nativas sobre el checkout modificado, tal como exige su comprobación de integridad.
