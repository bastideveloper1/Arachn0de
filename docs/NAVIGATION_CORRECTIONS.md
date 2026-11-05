# Correcciones focalizadas de navegación

- Menú general: altura según contenido con límite adaptable; cabecera compacta,
  lista desplazable, cierre inferior de 48 dp y categorías inferiores intactas.
- Etiquetas: se conserva la asignación directa anterior. Cabecera con cierre
  discreto, chips adaptables con marca de selección y herramientas existentes.
  No se elimina Cancelar de la creación/edición del catálogo ni de los filtros.
- Tecnologías: mismo repositorio y acciones de creación/edición/eliminación en
  panel inferior sobre la pantalla actual. Cierre normal vuelve a ella; se
  impide ocultar el panel durante una operación o un editor activo.
- Cumpleaños: la fila compartida se coloca junto al calendario antes de las
  ramas de resultados. Antes quedaba fuera de la rama de filtrado por fecha
  en el interior de Núcleo/Capa. Consulta y diálogo de Persona se reutilizan.
- Buscador: opciones y filtros en filas adaptables; el espacio del indicador
  seleccionado se reserva siempre, evitando cambios de ancho al activarlo.
  Se conservan el contenido desplazable y Restablecer/Aplicar independientes.

Sin cambios en listas del editor, almacenamiento, backups, seguridad, skins,
reglas de búsqueda ni datos de personas/etiquetas/tecnologías. Sin APK o versión.
Revisión en teléfono de alcance con el pulgar y teclado real pendiente.

Resultado: código y pruebas compilan. Seis comprobaciones focalizadas aprobadas
(cinco en la ejecución conjunta y Tecnologías en su ejecución aislada).
La prueba JVM de Tecnologías usa el adaptador de sincronización existente de
BackupFixture; la app conserva su sincronización real. El nombre JVM del archivo
del buscador se acorta a SearchUi para evitar nombres .class demasiado largos
en el sistema de archivos, sin cambiar sus funciones o lógica.
