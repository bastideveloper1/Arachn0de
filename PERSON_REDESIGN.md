# Personas: galería y ficha CV

La implementación adapta las referencias a una columna de teléfono y conserva
las funciones de las fases 1 y 2. No incorpora los datos ficticios, favoritos ni
controles globales de las imágenes.

## Funciones y rutas

Desde el menú existente → **Personas**:

- Cuadrícula de tres columnas cuando hay al menos 360 dp de ancho útil ajustado
  por la escala de fuente; dos columnas en anchos menores o fuentes grandes.
  Foto cuadrada redondeada y nombre; toda la tarjeta abre la ficha. El menú `⋮`
  ofrece editar y eliminar, con la confirmación de eliminación existente.
- Buscar Personas admite nombre y alias. Los filtros usan categorías de relaciones
  existentes y consideran ambos extremos de cada vínculo: una Persona puede
  aparecer en varias categorías. Nueva Persona conserva su editor de avatar.
- Ficha → **General**: foto destacada, alias, descripción resumible y notas;
  nombre único en cabecera, Volver y Editar nombre y avatar. Secciones distribuidas
  en filas que se adaptan al ancho, sin desplazamiento horizontal obligatorio.
- General → **Fotos de referencia**: principal y hasta cuatro adicionales.
  Seleccionar una miniatura cambia la foto consultada y resalta la selección.
  La principal sigue siendo el avatar; las adicionales no la sustituyen al
  seleccionarlas. Añadir foto usa el selector Android y el editor de encuadre.
  Menú de cada foto: reemplazar, ajustar encuadre, mover antes/después o eliminar.
  El orden se guarda; eliminar compacta las posiciones. Las fuentes se conservan
  al ajustar el encuadre; no se recomprimen.
- General → **Añadir/Editar fecha de nacimiento**: formato `AAAA-MM-DD`, opcional,
  fecha real y sin fechas futuras al editar. Vaciar el campo retira el dato.
  Edad calculada al consultar, nunca almacenada.
- **Contacto / Perfil**: conservan los campos y editores contextuales anteriores.
- **Relaciones**: miniatura, nombre y tipo de vínculo; tocar abre la Persona
  relacionada. `⋮` ofrece editar/eliminar el vínculo. Se conserva su significado,
  presentación inversa y navegación de regreso por la cadena de fichas.
- **Tecnologías**: catálogo compartido, iconos, nivel visible en la cuadrícula y
  consulta contextual para modificarlo o retirar la asociación.
- **Proyectos y trabajo**: reutiliza íntegramente las consultas, filtros,
  responsabilidades y navegación hacia los elementos originales de la fase 2.

## Persistencia y medios

Room **35→36**, migración aditiva, añade `person_photos` y `person_birthdays`.
Todas las tablas previas se conservan. IDs estables, FK CASCADE y transacciones;
validación de propietarios, cantidad, posiciones y encuadres. Las asociaciones
personales, Tecnologías, trabajos y responsabilidades no se reemplazan.

Las fotos adicionales reutilizan el almacén privado `avatars`, importación
hasta 20 MiB y normalización de nuevas fuentes hasta 2048 px. Se cargan miniaturas
escaladas según tamaño y zoom; la caché decodificada compartida conserva su límite
4 MiB. La cuadrícula carga solo los elementos visibles mediante LazyVerticalGrid.
No se transforman imágenes existentes ni se crean copias por asociación.

El contador de referencias de avatar incluye fotos de referencia. Las operaciones
usan el bloqueo común, reservas de editor y diario durable. Reemplazar/eliminar
libera únicamente después del commit y sin referencias ni reservas; se incluyen
las fotos adicionales al eliminar una Persona o restaurar. La recuperación al
reiniciar reintenta la limpieza pendiente. Las miniaturas son regenerables.

JSON **28** agrega fotos y fechas de nacimiento; los bytes reutilizan los
manifiestos y streams de avatares, deduplicados por nombre. Restauración con
validación previa, staging durable, remapeo de nombres y transacción integral;
lectura histórica JSON 1–27 con colecciones vacías. APK antiguas que solo admiten
hasta JSON 27 no restauran JSON 28. No cambian ANBACK01, SQLCipher ni las reglas de
separación principal/señuelo. No cambia versión comercial ni protocolo de release.

## Archivos de esta intervención

- UI: PeopleScreen, PersonDetailUi, PersonPhotoHeader (nuevo), PersonComponents,
  PersonIntegrationUi.
- Datos: PersonGallery (nuevo), PersonDao, PersonRepository, Arachn0deDatabase;
  esquema Room 36 nuevo.
- Respaldo: BackupData, BackupJson, BackupRepository.
- Limpieza: Arachn0deApplication observa también person_photos.
- Pruebas: PersonGalleryTest, PersonGalleryMigrationTest, PersonGalleryVaultTest,
  PersonGalleryUiTest (nuevos); ajustes de navegación en pruebas de fases 1/2 y de
  expectativas de versión actual en pruebas históricas. El fixture histórico no
  se reemplaza por una base moderna con user_version alterado.
- Contratos: ARCHITECTURE.md y BACKUP_FORMAT.md.

## Limitaciones de validación

La elección de archivos en el selector del sistema y la percepción visual de
fotografías/textos en teléfono requieren revisión manual. Las pruebas locales
verifican datos, archivos, migraciones, transacciones, cifrado de archivos y
restauración entre bóvedas; en JVM sustituyen solo el motor nativo SQLCipher por
SQLite. No renuevan evidencia nativa de release. No se accede al teléfono ni se
prepara una APK oficial en esta intervención.

## Resultado verificado

- **77 casos dirigidos distintos aprobados**, incluidos 12 nuevos (6 de datos y
  archivos, 2 variantes SDK 24/28 de migración histórica 35→36, 1 de bóvedas y
  respaldo cifrado, 3 de UI). Los reintentos de un mismo caso no se cuentan de nuevo.
- Migración preserva todas las tablas históricas, perfiles, relaciones personales,
  responsabilidades y asociaciones de Tecnologías; comprobación de FK sin errores.
- Fotos: límite de cuatro, orden y encuadre persistentes, avatar principal intacto,
  referencias compartidas, reemplazo, cancelación, eliminación, recuperación del
  diario y reversión ante fallos. UI verifica selección, ampliación/cancelación,
  confirmación de encuadre, reordenación y eliminación contextual.
- Respaldo: JSON histórico y 28, bytes compartidos, remapeo, restauración cifrada
  principal/señuelo, validación previa y conservación del estado ante fallos.
- UI: tres columnas con ancho suficiente, búsqueda por alias, múltiples categorías,
  cien Personas, catálogo de quinientas Tecnologías, fuentes grandes, temas y
  navegación Persona → Proyecto → elemento → Persona.
- `git diff --check` aprobado; archivos nuevos sin espacios finales.
- **Debug final: BUILD SUCCESSFUL in 34s**, sin firma oficial.
  Artefacto: `app/build/outputs/apk/debug/app-debug.apk`.
- Versión comercial conservada: **0.3.0 / versionCode 10**. La APK oficial anterior
  no se regeneró. Los cambios previos del repositorio se conservaron; sin commit,
  push, instalaciones ni acceso al teléfono.

Registros locales de verificación: `/tmp/person-redesign-final.log` (ejecución de
las 77 pruebas y Debug), `/tmp/person-redesign-ui-final.log` (regresión de UI tras
los últimos ajustes visuales) y `/tmp/person-redesign-gallery-final.log` (las tres
pruebas de galería y Debug final, con el ajuste de espera de guardado y cancelación
segura al abandonar el editor).
