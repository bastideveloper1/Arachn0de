# Bloque 1: Personas, responsables y Tecnologías

Se conserva el modelo existente: Personas con nombre, avatar y encuadre; responsables asignados a nodos (Capas/Tareas), y Tecnologías asignadas a proyectos o nodos con posición propia. Los proyectos no tienen una relación directa de responsables en el modelo actual; no se introduce una relación nueva ni roles ficticios.

Cambios:
- El selector compartido de responsables utiliza búsqueda y cuadrícula adaptable con composición diferida. Los cambios permanecen en el borrador hasta Guardar; Cancelar no escribe asociaciones.
- Consultar un avatar muestra únicamente el nombre y avatar de la Persona seleccionada. No cambia relaciones.
- La carga de avatares se reinicia también cuando cambia el contexto privado del almacén, como ya ocurre con los iconos de Tecnologías.
- La primera Tecnología asignada recibe un fondo del tema. Su prioridad visual proviene del orden existente, sin campos nuevos.

Se conservan las secciones vacías ocultas, la fila compartida compacta de participantes/Tecnologías, las tres vistas previas y el acceso a todos los elementos, la cuadrícula de consulta, la asignación explícita y el reordenamiento persistente. Se reutilizan miniaturas de hasta 96 px y LRU de 4 MiB, referencias compartidas y limpieza durable existente.

Sin migraciones ni cambios en Room, formato de backup, versión de aplicación, seguridad, Metro o juego. Los formatos existentes incluyen asociaciones ordenadas y bytes de imágenes. No se generan APK de distribución ni se ejecuta firma.

Validación: 46 pruebas dirigidas aprobadas en TechnologyUiTest (12), TechnologyRepositoryTest (10), PersonRepositoryTest (4), TechnologyBackupTest (9) y StorageLifecycleTest (11); assembleDebug aprobado. Se incluyen pruebas nuevas de búsqueda/composición diferida de 100 Personas, guardado explícito y consulta de identidad individual, y énfasis de la primera Tecnología. Pruebas existentes cubren 500 Tecnologías, fuentes grandes y ancho estrecho, persistencia, cancelación de edición, imágenes compartidas, reemplazo, recuperación y caché acotada.

Comprobación manual pendiente: en teléfono, revisar selector en Capas/Tareas, búsqueda y cancelación, consulta de avatares/iconos, reordenamiento tras cerrar/reabrir, ausencia de huecos sin Tecnologías y legibilidad con fuente grande en distintos temas. La referencia adjunta se usa solo como inspiración conceptual, sin navegación de escritorio.

Las modificaciones de código invalidan la evidencia de release asociada al hash anterior; este bloque no renueva esa certificación ni prepara una distribución. Las pruebas JVM de almacenes no equivalen a una nueva certificación nativa SQLCipher.

Validación adicional aprobada: SecurityVolumeBackupTest (1: respaldo cifrado con 100 Personas y 500 Tecnologías, imágenes y relaciones) y VaultManagerTest.passwordChangeIndependentStoresRestartAndRevokedReferences (1: separación de almacenes y revocación). Total: 48 pruebas correctas. Esta última prueba usa credenciales sintéticas exclusivamente en su fixture aislado; no modifica las contraseñas ni datos de la aplicación real. git diff --check aprobado.
