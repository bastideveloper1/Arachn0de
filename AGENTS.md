# Arachn0de: persistencia y backup

Antes de modificar persistencia, leer el **Contrato de persistencia y backup** de `ARCHITECTURE.md` y el formato en `BACKUP_FORMAT.md`.

Toda funcionalidad que añada tablas/columnas, preferencias/configuraciones, archivos/imágenes/adjuntos, relaciones o tipos de datos locales debe implementar en la misma tarea su exportación, restauración, validación de integridad, compatibilidad histórica y pruebas dirigidas. Los archivos necesarios deben incluir sus bytes; nunca serializar rutas absolutas del dispositivo.

Una función cuyos datos no puedan recuperarse íntegramente desde un backup no está terminada. Validar antes de reemplazar datos; conservar el estado anterior ante fallos y usar staging durable/transacciones/diarios para archivos. Revisar la lista de verificación del contrato durante desarrollo y la lista de `RELEASING.md` antes de preparar un release.

Conservar cambios previos y el funcionamiento offline. No ejecutar commits, push, tags o publicaciones sin autorización explícita.

## Contrato de liberación de archivos

Toda función que almacene archivos debe definir cuándo y cómo se liberan, además de cumplir el contrato de persistencia y backup. No está terminada sin pruebas de eliminación, reemplazo, referencias compartidas, cancelación y recuperación tras fallos.

Eliminar físicamente solo después del commit y de verificar que no quedan referencias confirmadas ni reservas de editores, backup, restauración o recuperación. Usar el bloqueo común de operaciones de archivos, directorios controlados y registros durables de limpieza pendiente; nunca barrer indiscriminadamente el almacenamiento privado. Las reservas pendientes no caducan por antigüedad. Los fallos deben permitir reintentar al reiniciar sin borrar datos necesarios. Acotar la caché y documentar las exclusiones regenerables del backup.
