# Arachn0de — Dogfooding Sprint 2

Versión conservada: **0.3.0 / versionCode 10**. Se mantienen los cambios anteriores, Sprint 1, seguridad, juego y Metro. No se genera Release ni se firma, publica, hace commit/push o instala en el teléfono.

## Implementación

- Proyectos usan el selector múltiple de Tecnologías del Sprint 1: búsqueda, cuadrícula lazy, confirmación/cancelación, orden propio y vista previa de tres. Las asociaciones de Proyecto son independientes de sus descendientes. Copia, edición, conversiones y backup mantienen asociaciones y orden mediante la lógica compartida existente.
- Dashboard sin el encabezado redundante «Proyectos». «Gestionar etiquetas» pasa al drawer existente, tanto desde proyectos como desde sus contenidos; mantiene administración, filtros y navegación.
- La identidad sin foto reutiliza `AccountTree`, símbolo de jerarquía ya presente en las conversiones. Se inspeccionaron recursos: `iconohome.png` representa Home y `iconovercapas.png` representa Capas; no hay un recurso oficial exclusivo de Proyecto. No se ha inventado un PNG ni sustituido fotografías personalizadas. Queda pendiente un recurso definitivo aprobado de jerarquía/proyecto si se desea reemplazar este fallback existente.
- «Capas primero» tiene preferencia independiente por Proyecto/Capa/subcapa y está desactivada por defecto. Activada, las Capas aparecen primero y conservan orden manual; las tareas conservan el criterio seleccionado. Arrastre y acciones accesibles Subir/Bajar permiten reordenar dentro del grupo sin escribir posiciones del otro. Manual sigue disponible; desactivar restaura el comportamiento tradicional. Los filtros respetan el criterio propio de cada padre. Los grupos y restricciones especializados de Sprint se conservan.
- Convertir Tarea→Capa habilita inmediatamente su orden estructural y conserva propiedades y Tecnologías. Proyecto↔Capa mantiene las preferencias, asociaciones y fotografía oculta con el mecanismo durable existente, incluyendo Undo.

## Fotografías: causa y estrategia

La importación de proyectos reutilizaba la normalización de avatares a 512 píxeles y el muestreo podía reducir aún más el detalle antes de ampliar. Se separa únicamente el límite de proyectos: normalización única a PNG de hasta **2048 píxeles de lado mayor**, sin ampliar fuentes menores, preservando proporciones, alfa y orientación EXIF. La fuente privada normalizada conserva toda la composición; no se almacena necesariamente la resolución completa de cámara. El límite de importación sigue en 20 MiB.

Cambiar zoom/encuadre no recomprime ni modifica bytes. Reemplazar conserva el encuadre vigente. Las fotografías antiguas no se reescriben: el detalle ya perdido no puede recuperarse; requiere reimportar el original.

Las vistas piden miniaturas según tamaño físico y zoom, sin decodificar cada fuente completa. Derivados regenerables usan disco cifrado hasta **64 MiB**, claves por fuente/tamaño y limpieza acotada a nombres controlados. El LRU compartido sigue limitado a **4 MiB**. Se usa el contexto capturado del almacén y bloqueo común de archivos; se comprueba la sesión activa. Cerrar sesión limpia memoria y revoca accesos. Los derivados no entran en el backup.

Fuentes, importaciones temporales, reservas y diarios mantienen cifrado y el ciclo de vida previo: commit antes de liberar, referencias compartidas, reservas de editor/backup/restauración y recuperación tras fallos. No se añaden límites de entidades; avatares e iconos conservan sus límites anteriores y carga lazy. No se serializan rutas absolutas.

## Persistencia y compatibilidad

- **Room 26→27:** añade `node_sort_preferences.layersFirst INTEGER NOT NULL DEFAULT 0`, sin recrear tablas ni cambiar claves/identidades. `INHERIT` almacena la opción sin congelar el criterio heredado. Las posiciones existentes siguen representando el orden manual.
- **JSON 19:** transporta `layersFirst`, admite `INHERIT` y fotos de proyectos de hasta 2048 píxeles/20 MiB. Se emite si existen fotos o preferencias nuevas; otros casos conservan 17/18. Lectores admiten 1–19; opciones históricas quedan desactivadas. Versiones antiguas rechazan 19.
- Fotos y encuadres se restauran íntegramente con manifiestos, SHA-256, validación previa y staging/transacciones existentes. Streaming mantiene el límite agregado histórico de 1 GiB; inline conserva 8 MiB, por lo que las colecciones grandes requieren contenedor con streaming. Avatares/iconos mantienen 512 píxeles/1 MiB.

## Archivos principales

- UI: `ProjectsScreen`, `ProjectScreen`, `NavigationChrome`, `AppRoot`, `TagUi`, `NodeComponents`, `ProjectPhotoComponents`, `DragReorder`, `NodeSortMenu`.
- Estado/repositorios: `NodeSortPreferences`, `NodePresentationSort`, `NodeActions`, `NodeRepository`, `NodeSortPreferenceRepository`, `ProjectNestingRepository`, `ProjectPhotoRepository`.
- Archivos/persistencia: `AvatarStore`, `ProjectPhotoStore`, nuevo `ProjectThumbnailCache`, `PrivateImageCache`, `ConversionEntities`, `Arachn0deDatabase`, nueva `LayerSortMigration26To27`, esquema `27.json`.
- Backup: `BackupData`, `BackupJson`, `BackupImageFiles`, `BackupAvatarFiles`, `BackupContainer`.
- Pruebas dirigidas de fotos, conversiones, orden, Tecnologías, seguridad y migraciones; nuevo `Dogfooding2PersistenceTest` y ampliación de `NativeVaultTest`. Se actualizaron expectativas históricas de versión Room a 27 y snapshots para comparar las columnas originales, conservando cobertura de migraciones.
- Documentación: `ARCHITECTURE.md`, `BACKUP_FORMAT.md`, este informe. El árbol ya contenía otros cambios de sprints anteriores; se conservaron.

## Validación

**166 pruebas JVM distintas aprobadas**, sin suite global. Cubren selección/orden/asociaciones de Tecnologías, fuentes/miniaturas, alfa, proporciones, EXIF, encuadre, reapertura, backup bytes, formatos históricos y rechazo de campos inválidos, referencias compartidas, cancelación, reemplazo, limpieza/recuperación, conversiones, orden automático/manual, arrastre y navegación de etiquetas. Los fallos iniciales por expectativas de 512 píxeles, encabezado eliminado y snapshots históricos se corrigieron y verificaron; la prueba de reordenación requirió desplazar el menú hasta la acción visible.

`assembleDebug`, `assembleDebugAndroidTest` y `git diff --check`: aprobados. No se ejecutó firma ni Release. Las cinco imágenes del juego permanecen intactas.

La primera ejecución SQLCipher detectó que la autoverificación de contenedores sin staging aún aplicaba el límite de avatar a fotos de proyectos. Se corrigió `BackupContainer` para validar por directorio, manteniendo comprobación de dimensiones, bytes, PNG y hash. **Validación nativa final: OK (4 tests), 60,049 segundos**, emulador Android API 37 x86_64 de 16 KiB. Se usó exclusivamente `com.r0ybt.arachn0de.securityvalidation`, distinto de la aplicación oficial, sin desinstalar ni limpiar sus datos. Incluye SQLCipher real y rechazo de clave errónea, migración inicial y recuperación tras interrupción, separación de almacenes y nueva migración cifrada 26→27 en ambos UUID; foto de 1800×900 y encuadre restaurados con bytes idénticos, miniatura cifrada y opción de orden conservada. Log final: `/tmp/dogfood2-native-final.log`. No se renueva automáticamente la evidencia del script de distribución.

## Riesgos y comprobaciones pendientes

Revisar en teléfono real pantallas estrechas/fuentes grandes, tacto y arrastre, encuadre/zoom de imágenes de cámara y consumo durante navegación con bibliotecas grandes. Emulador y Robolectric no sustituyen esa evaluación visual y de rendimiento. Antes de una futura actualización, conservar un respaldo exportado de los datos actuales; este sprint no prepara ni certifica una distribución firmada. El recurso gráfico definitivo exclusivo de Proyecto no existe en el repositorio; la UI usa el fallback de jerarquía existente.
