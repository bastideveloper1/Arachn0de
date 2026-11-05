# Bloque 6 — Simplificación y plantillas estructurales

## Funciones retiradas

Se retiran del uso activo los accesos y controles de Tecnologías (menú global,
asignación en formularios, tarjetas, indicadores y sección de Personas), la
activación de planificación/Modo Proyecto, sus vistas especializadas y los
controles de Kanban, Gantt y Sprint. Las Capas y Notas que esas funciones crearon
siguen siendo contenido real: no se borran ni se convierten automáticamente.
Ya no se llama a la creación automática de la cascada al abrir un Núcleo ni se
actualizan actas/instantáneas de iteración al editar contenido ordinario.

Se conservan tablas, identificadores, campos, archivos, validadores y lectores
históricos. Las relaciones históricas aún pueden impedir traslados o borrados que
dejarían datos inválidos. No se desconectan ni se borran esas relaciones de forma
implícita. El mantenimiento de archivos históricos sigue verificando referencias.
SQLCipher, versiones Room y formatos históricos no cambian.

El semáforo normal conserva sus reglas, filtros y herencia de dos/tres estados.
Las tareas que conservan `workState` histórico no se convierten automáticamente:
su control operativo queda inactivo y se conserva su contenido. Tampoco se crean
hijos nuevos dentro de un Sprint histórico. Se puede guardar esa estructura como
plantilla e insertar una copia normal en un destino compatible. Una eventual
conversión de los originales requeriría una decisión explícita separada.

## Plantillas

`saved_templates` conserva su tabla. `SavedTemplateCodec.validate` admite ahora
el payload estructural v2 y sigue leyendo la configuración histórica v1.
`SavedTemplateRepository` se amplía mediante las operaciones del mismo paquete;
no hay otro catálogo, base de datos ni sistema de cifrado.

- Opciones de Núcleo/Capa/Tarea/Nota → **Guardar como plantilla**. Se incluye todo
  el subárbol, con una opción compacta para excluir imágenes y adjuntos.
- Crear Núcleo / Crear elemento → **Nuevo** o **Desde plantilla**. Solo se ofrecen
  tipos compatibles; antes de insertar se muestran nombre, tipo y número total
  de elementos. Las plantillas también pueden eliminarse desde ese panel.
- Borradores también ofrece **Desde plantilla**, limitado a Tareas y Notas sin
  jerarquía. Sus elementos existentes pueden guardarse desde Opciones del editor;
  siempre se captura su versión confirmada, sin guardar cambios del editor.
- Los Núcleos se insertan en General; las Capas, en Núcleos o Capas; Tareas y
  Notas, en sus destinos ordinarios. Si el destino es una Tarea pendiente se
  reutiliza la conversión a Capa que ya realiza la creación ordinaria. Banca se
  excluye explícitamente: no se capturan cuentas, movimientos, saldos ni claves.

Se preservan títulos, descripciones/notas, orden y jerarquía, prioridad, esfuerzo,
definición reutilizable de pago, fijación, configuración del semáforo, valores
iniciales locales, etiquetas por nombre, fotografías con encuadre, imágenes y
audio adjuntos. Las skins son una configuración global, no una propiedad de cada
elemento: se siguen aplicando las skins actuales sin duplicarlas ni cambiarlas.

Cada inserción genera IDs nuevos para elementos, relaciones, fotografías y
adjuntos, y remapea los marcadores del contenido. Puede compartir un recurso
entre propietarios de la misma instancia; nunca lo vincula al original ni a otra
instancia. Las etiquetas se resuelven por nombre normalizado, reutilizando el
catálogo existente o creando la etiqueta que falte.

Se reinician ejecución, verificación, fases, fechas de inicio/vencimiento,
identificadores e historial. No se copian Personas ni relaciones de gestión,
recurrencias, sesiones Metro o enlaces externos. Los valores iniciales conservan
sus reglas relativas; sus asignaciones personales se omiten explícitamente.
Las plantillas v1 ordinarias siguen disponibles y conservan sus campos de texto,
prioridad, definición de pago y etiquetas existentes; se aplican las mismas reglas
de reinicio. Las plantillas históricas de Metro siguen almacenadas, sin convertirse
en plantillas estructurales ni alterar la herramienta Metro.

## Persistencia, archivos y backup

El payload v2 contiene sus recursos completos en base64, no rutas. Se almacena en
Room/SQLCipher y viaja en el campo `savedTemplates` del respaldo cifrado existente.
No se requieren tablas ni una nueva versión del contenedor. Un lector antiguo
que solo entiende payload v1 rechazará un respaldo que contenga plantillas v2;
esta versión sigue leyendo los respaldos y plantillas anteriores.

Límites explícitos: 8 MiB por payload, 4096 elementos/recursos y profundidad de
captura inferior a 128. Siguen rigiendo los límites globales de backup existentes;
superarlos falla explícitamente, sin omitir recursos. El guardado valida estructura,
tipos de elemento, padres, campos, referencias y recursos antes de confirmar.

La plantilla no mantiene archivos externos propios: eliminarla libera su payload
de la tabla sin tocar los originales ni las instancias. Al insertar, los adjuntos
se importan con reservas y el bloqueo común; la transacción añade todos los
propietarios y relaciones. Las fotografías utilizan el diario durable existente.
El diario operacional privado `template-insertions/<uuid>.pending` permite liberar
reservas interrumpidas al reiniciar; no caduca por antigüedad ni se incluye en el
backup. No se recorre indiscriminadamente el almacenamiento. Las operaciones
activas del proceso quedan excluidas de esa recuperación. Las copias privadas
siguen cifradas por `SecureFiles` y no hay copias públicas persistentes.

## Validación focalizada

Resultado: 13 pruebas distintas satisfactorias (5 de plantillas, 1 de interfaz,
5 de `TaskStatusTest` y 2 de `TaskStateInheritanceTest`). Las ampliaciones de
cobertura de archivos compartidos/reemplazo y Borradores también pasaron, así
como la comprobación posterior de límites y profundidad. No quedan pruebas
focalizadas fallidas.

- Pruebas existentes de estado e herencia: semáforo, filtros y backup cifrado.
- Cinco pruebas de repositorio estructural: los cuatro tipos, Borradores, árbol anidado,
  etiquetas y valores iniciales; IDs y archivos independientes; original intacto;
  eliminación de plantilla/original sin perder las copias; recursos cifrados;
  referencias compartidas dentro de la instancia y reemplazo de plantilla;
  rollback por error SQL; cancelación tras preparar fotografías; recuperación
  de importaciones interrumpidas; respaldo cifrado real con payloads v1 y v2;
  rechazo de relaciones inválidas y destinos incompatibles.
- Prueba de interfaz: acceso retirado ausente, filtrado por destino, vista previa
  y cancelación sin insertar.
- Compilación debug y `git diff --check`; sin suites completas ni APK oficial.

Registros de ejecución: `/tmp/arachnode-block6-a-tests.log`,
`/tmp/arachnode-block6-templates-final.log`, `/tmp/arachnode-block6-ui.log` y
`/tmp/arachnode-block6-final.log`; cobertura adicional en
`/tmp/arachnode-block6-files-check.log`, `/tmp/arachnode-block6-drafts-tests.log`
y `/tmp/arachnode-block6-limits-check.log`.
