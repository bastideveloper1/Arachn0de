# Personas Fase 2: integración y navegación

## Funciones y rutas

Menú → Personas → tocar la tarjeta / Ver ficha. La ficha ofrece General, Contacto,
Perfil, Relaciones, Tecnologías y Proyectos y trabajo; muestra solo la sección elegida.
Los accesos desde responsables y participantes conducen a la misma ficha.

Tecnologías → Asociar Tecnología → buscar un registro del catálogo → elegir dominio
→ Guardar dominio. Los niveles son Sin evaluar, Básico, Intermedio, Avanzado y Experto.
Tocar una celda de la cuadrícula permite consultar/cambiar el dominio o Retirar
asociación. Cancelar no escribe. Retirar conserva la Tecnología, su icono y otras
asociaciones. Renombrar/reemplazar iconos se hace en el catálogo existente y se
refleja automáticamente en la ficha. El dominio es declarado, no una evaluación.

Proyectos y trabajo agrupa proyectos físicos una sola vez; muestra participación,
roles de gestión y responsabilidades tradicionales. Proyecto filtra un ámbito;
Tipo filtra Tareas, Notas, Capas, Sprints, Épicas, Historias y Objetivos; Estado filtra
completitud explícita de tareas, conservándola independiente de la fase Sprint.
Los elementos sin ese campo no se etiquetan como terminados ni pendientes.
Las responsabilidades múltiples aparecen juntas sin duplicar el elemento.

Abrir proyecto abre la pantalla original; tocar un elemento abre su ubicación
original. Objetivos abre el detalle existente de planificación, con el objetivo
seleccionado por ID. Volver desde el proyecto retorna a la Persona de origen y a
Proyectos y trabajo; Volver dentro del árbol conserva primero la navegación normal.
El regreso conserva también el camino entre fichas de Personas relacionadas.
Las asignaciones no acreditan trabajo realizado. No se generan estadísticas,
evaluaciones de desempeño, informes ni copias de elementos.

## Datos, derivación y archivos

Room 34→35 añade `person_technologies`, clave compuesta (`personId`, `technologyId`),
FK CASCADE a Personas y catálogo, con dominio portable enum en texto. La migración
es aditiva y se ejecuta en la transacción de Room; no reescribe perfiles, vínculos,
encuadres, árboles, Sprints ni responsabilidades. IDs y dominio se validan al guardar
y antes de restaurar; se rechazan referencias huérfanas, pares duplicados y niveles
no admitidos. No hay límite nuevo de cantidad de entidades.

Proyectos y trabajo es una consulta reactiva, no nuevas tablas: `node_person`,
`management_participants`, `participant_roles`, `node_responsibilities` y
`objective_responsibilities`, unidas a sus nodos, objetivos y contextos. Los
`conversion_roots` resuelven el proyecto físico después de conversiones; se muestra
el nombre del ámbito independiente para no confundir contextos. La desactivación de
gestión conserva y señala sus responsabilidades. Se agrupa por ID de proyecto y
por tipo/ID de elemento; no se infieren contribuciones ni porcentajes.

No se crean archivos ni copias de iconos. El catálogo sigue siendo propietario de
sus medios; la cuadrícula y selector virtualizan filas/celdas y reutilizan
`TechnologyIcon`, su carga escalada y caché existente. Retirar una asociación o
eliminar una Persona no libera un icono del catálogo. Eliminar la Tecnología retira
sus asociaciones por FK y mantiene la liberación segura existente, después del
commit, con bloqueo, reservas y diario durable. No se cambian esas reglas.

JSON 27 añade `personTechnologies`; JSON 1–26 sigue admitido con esa colección vacía.
Se conserva la selección histórica del JSON si no existen datos nuevos. Los bytes
de iconos siguen incluidos una sola vez mediante el catálogo y manifiesto existentes.
Snapshot transaccional, validación previa, staging durable, rollback y aislamiento
principal/señuelo permanecen vigentes. Lectores anteriores no admiten JSON 27; no
hay downgrade Room. No se cambia envoltura cifrada, contraseñas, claves ni paquete.

## Archivos de la fase

Producción, bajo `app/src/main/java/com/r0ybt/arachn0de/`:
- Nuevos: `data/local/PersonIntegration.kt`, `ui/PersonIntegrationUi.kt`.
- Integrados: `data/local/Arachn0deDatabase.kt`, `data/repository/PersonRepository.kt`,
  `backup/BackupData.kt`, `backup/BackupJson.kt`, `backup/BackupRepository.kt`,
  `ui/PeopleScreen.kt`, `ui/PersonDetailUi.kt`, `ui/PlanningUi.kt`, `ui/AppRoot.kt`.
- Esquema: `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/35.json`.

Pruebas nuevas: `PersonIntegrationTest`, `PersonIntegrationMigrationTest`,
`PersonIntegrationVaultTest` y `PersonIntegrationUiTest`. Expectativas existentes de
versión Room y JSON futuro se adaptan al nuevo destino, sin cambiar sus fixtures
históricos ni las migraciones de producción. `NativeVaultTest` solo actualiza el
destino/esquema esperado; no se certifica validación SQLCipher nativa en esta fase.
Documentación: este archivo, ARCHITECTURE.md y BACKUP_FORMAT.md.

## Verificación y entrega

80 casos dirigidos distintos aprobados, 14 nuevos en esta fase: cinco de datos, dos
variantes SDK 24/28 de migración histórica auténtica 34→35, uno de bóvedas/archivo
cifrado y seis de interfaz. Regresiones: fase 1, PersonRepository, catálogo de
Tecnologías, formatos/rollback de respaldo, participantes y planificación.

Cobertura: todos los dominios, alta/baja sin borrar catálogo, renombre/reemplazo de
icono compartido, eliminación independiente de Persona/Tecnología/proyecto,
perfiles/relaciones personales intactos, respaldo JSON 26/27 y restauración,
referencias y dominios inválidos, rollback después del borrado, archivos liberados
tras último referente y conservación del archivo mientras pertenece al catálogo,
bóvedas separadas incluso con IDs iguales, responsabilidades múltiples y contextos
convertidos/desactivados, completitud independiente, navegación real y retorno
(incluso desde participantes del mismo proyecto), filtros y borrado del proyecto
filtrado, catálogo y cuadrícula de 500 asociaciones, 100 Personas en el listado,
fuente 1.6×, todos los temas existentes y conservación del camino Persona→Persona
al regresar de un proyecto. La comparación de esquemas confirmó que
la única tabla añadida es person_technologies y ninguna anterior cambió.

`git diff --check` aprobado; archivos nuevos revisados también por espacios finales.
`:app:assembleDebug` final: BUILD SUCCESSFUL (28 s), junto con las seis pruebas
de interfaz de esta fase sobre los archivos definitivos. Artefacto de desarrollo:
`app/build/outputs/apk/debug/app-debug.apk`; sin instalación.
Quedan pendientes revisión visual en teléfono y validación SQLCipher nativa del
checkout antes de un futuro release. Las pruebas Robolectric de bóvedas usan
sesiones, archivos y respaldos cifrados reales con SQLite sustituyendo únicamente
el motor SQLCipher. No equivalen a una prueba nativa.

Se preservan los cambios previos del árbol de trabajo. No se genera APK oficial,
no se accede al teléfono ni se hace commit/push. La versión comercial sigue
0.3.0 / 10; la APK oficial entregada antes de Personas no contiene estas fases.
