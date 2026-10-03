# Arachn0de — Architecture & Product Specification

## 1. Propósito de este documento

Este documento es la fuente principal de contexto técnico y funcional de Arachn0de.

Antes de realizar cambios importantes en el proyecto, debe consultarse este documento.

Es una fuente de verdad viva: cada cambio arquitectónico o regla importante debe documentarse en la misma intervención que modifica el código. Distinguir siempre lo implementado, lo descartado y lo pendiente; no presentar objetivos de sprints futuros como capacidades actuales.

El objetivo es mantener una arquitectura coherente, evitar implementaciones innecesarias y permitir que el proyecto crezca progresivamente sin tener que reconstruir su núcleo.

La distribución oficial base es **v0.2.0 Beta**, con `versionName = "0.2.0"` y `versionCode = 2`, publicada en GitHub Releases; la base MVP histórica fue v0.1.0 / 1. #33 no cambia estos valores.

---

# 2. ¿Qué es Arachn0de?

Arachn0de es una aplicación local-first de organización, planificación y seguimiento.

Su objetivo no es ser solamente una aplicación de tareas.

Busca representar relaciones entre:

- proyectos;
- tareas;
- objetivos;
- versiones;
- hitos;
- personas;
- archivos;
- cambios;
- decisiones;
- riesgos;
- tiempo;
- colecciones personales;
- y otros elementos relacionados.

El principio central es permitir que la información pueda organizarse mediante estructuras jerárquicas flexibles.

Arachn0de debe comenzar siendo simple y crecer progresivamente.

---

# 3. Principios del producto

## Local-first

Los datos pertenecen al usuario y deben almacenarse principalmente en el dispositivo.

Arachn0de debe poder funcionar sin conexión a Internet.

## Offline-first

Las funciones principales no deben depender de servicios externos.

## Sin cuenta

El MVP no requiere registro ni inicio de sesión.

## Sin backend

El MVP no utiliza servidores propios ni servicios cloud.

## Privacidad

La arquitectura futura debe permitir incorporar cifrado local y otras protecciones sin abandonar el principio local-first.

Para el MVP v0.1.0, los datos de la aplicación no deben participar en copias de seguridad administradas por Android ni en cloud backup. También deben excluirse de la transferencia de datos entre dispositivos administrada por Android.

La configuración debe mantener `android:allowBackup="false"` y exclusiones explícitas de todos los dominios de datos en las reglas de backup para Android 11 y anteriores y en las reglas `cloud-backup` y `device-transfer` para Android 12 y posteriores.

La persistencia exigida al cerrar la aplicación o reiniciar el dispositivo es almacenamiento local, no recuperación mediante backup.

## Modularidad

Las nuevas funciones deben construirse encima del núcleo existente y no convertir la aplicación en un sistema monolítico difícil de mantener.

## Simplicidad

No implementar funciones futuras antes de que sean necesarias.

---

# 4. Plataforma inicial

Arachn0de será desarrollado inicialmente para Android.

El desarrollo de una aplicación de escritorio se considera una etapa futura.

Tecnologías iniciales:

- Kotlin
- Android SDK
- Jetpack Compose
- Material 3
- Room
- SQLite
- Gradle
- Git

IDE principal:

- Android Studio

Minimum SDK inicial:

- Android 7.0
- API 24

---

# 5. Arquitectura general

Mantener una separación clara entre:

```text
UI
↓
ViewModel / Application logic
↓
Repository
↓
Room
↓
SQLite
```

La interfaz no debe acceder directamente a la base de datos.

La lógica de negocio importante no debe quedar incrustada dentro de componentes visuales de Compose.

Favorecer código sencillo, legible y fácil de probar.

Evitar abstracciones innecesarias durante el MVP.

---

# 6. Concepto central: Capas de cebolla

La jerarquía es una primitiva general de Arachn0de.

No debe estar limitada al concepto tradicional:

```text
Tarea
└── Subtarea
```

Internamente utilizaremos nodos.

Un nodo puede contener otros nodos.

Ejemplo de proyecto:

```text
Arachn0de
└── v0.1.0
    └── MVP
        ├── Persistencia local
        ├── Navegación
        └── Capas de cebolla
```

La misma arquitectura puede representar información personal:

```text
CINE
└── Star Wars
    └── The Clone Wars
        └── Temporada 1
            ├── Episodio 1
            ├── Episodio 2
            └── Episodio 3
```

Para el sistema ambas estructuras deben poder representarse mediante el mismo modelo.

La interfaz denomina este concepto:

**Capas de cebolla**

La implementación interna utiliza relaciones padre-hijo entre nodos.

La representación visual de Capas de cebolla es una trayectoria de profundidad: el proyecto raíz actúa como origen y cada capa interna se sitúa a lo largo de una línea continua que comunica el recorrido de exploración dentro del proyecto. La cebolla visualiza la profundidad interna; la raíz conserva identidad propia y un tratamiento distinto.

---

# 7. Modelo de dominio inicial

## Project

Representa un proyecto o espacio principal de organización.

Propiedades iniciales aproximadas:

```text
id
name
description
position
createdAt
updatedAt
```

La lista global de proyectos se ordena por `position`, con `createdAt` e `id` como desempates deterministas. El esquema Room se actualizó a versión 4 para persistir ese orden sin perder datos antiguos ni introducir una segunda regla de orden distinta a la ya validada para nodos.

## Node

Representa un elemento dentro de un proyecto.

Propiedades iniciales aproximadas:

```text
id
projectId
parentId
title
description
isCompleted
position
createdAt
updatedAt
startAt (opcional)
dueAt (opcional)
purpose (ACTION / NOTE, persistido)
amountMinor / currencyCode (capacidad de Obligación opcional)
```

Desde el esquema Room 3, `isStructural` e `isCompletable` no son columnas persistidas ni parámetros de creación/edición. El dominio expone `hasChildren` derivado de las relaciones persistidas; `isStructural = hasChildren` e `isCompletable = !hasChildren && purpose == ACTION` son propiedades calculadas de solo lectura.

`isCompleted` conserva exclusivamente el completado manual de una hoja. Para una capa siempre es `false`; no equivale a su progreso derivado.

`parentId` puede ser nulo.

Un nodo sin `parentId` pertenece al nivel raíz del proyecto.

Un nodo puede contener cero o más nodos hijos.

“Profundidad arbitraria” significa profundidad práctica no limitada artificialmente por el modelo de dominio; no significa recursos infinitos.

La implementación debe evitar supuestos sobre una profundidad fija y manejar razonablemente árboles profundos en navegación, cálculo de progreso y operaciones de datos, incluida la eliminación. Los recorridos y operaciones deben considerar los límites reales de memoria, pila y almacenamiento.

---

# 8. Nodos universales

No crear tablas independientes para cada posible concepto futuro, como:

```text
Task
Subtask
Season
Episode
Folder
Phase
Version
```

cuando todos ellos puedan representarse inicialmente mediante `Node`.

El modelo debe permanecer suficientemente genérico para permitir futuras especializaciones.

No obstante, evitar implementar esas especializaciones durante el MVP.

---

# 9. Regla tarea/capa, completado y progreso

## Fuente de verdad del dominio

Las relaciones persistidas definen la estructura; el propósito persistido define el comportamiento de hoja:

- Node sin hijos = hoja: ACTION es tarea completable; NOTE es información no completable.
- Node con uno o más hijos = capa/contenedor, nunca completable manualmente.
- El usuario elige propósito Tarea/Nota; hoja/Capa sigue derivándose de los hijos.
- NOTE es una hoja informativa no completable; no existe una bandera estructural independiente.

Se descarta mantener `isStructural` e `isCompletable` almacenados como fuentes independientes de la estructura.

## Transiciones estabilizadas en Sprint 5.5, bloque 1

- Hoja pendiente + primer hijo: pasa a capa y conserva `isCompleted = false`.
- Hoja completada + primer hijo: pasa a capa y se borra su antiguo completado manual (`isCompleted = false`). Su progreso depende desde ese momento de las hojas descendientes.
- Capa pierde su último hijo, por eliminación o traslado: vuelve a ser una hoja pendiente y completable. Nunca recupera un completado manual histórico.
- Crear, trasladar o eliminar hijos aplica estas reglas en la misma operación atómica.
- Editar título/descripción no modifica estructura, posición ni completado.
- Completar/descompletar una capa se rechaza; nunca hay completado automático en cascada, ni hacia hijos ni hacia padres.
- Completar una hoja no la elimina ni la oculta. Tampoco elimina su relación de parentesco: completar un hijo no convierte a su padre en hoja; perder el último hijo por eliminación o traslado sí lo hace.

## Cálculo exacto de progreso

Cada hoja ACTION aporta una unidad de trabajo, con igual peso; NOTE aporta cero. El progreso de ACTION es 0/1 o 1/1. Una capa suma las hojas de todos sus descendientes; no se cuenta a sí misma ni se promedian los porcentajes de sus hijos.

```text
Temporada 1
├── Episodio 1 ✓
├── Episodio 2 ✓
├── Episodio 3 ○
└── Episodio 4 ○

Progreso: 2 / 4 = 50 %
```

El porcentaje entero se trunca: `completed * 100 / total`. Los estados son `NOT_STARTED`, `PARTIAL` y `COMPLETE`. Una hoja ACTION tiene total 1. Una hoja NOTE, una Capa con solo Notes o un proyecto sin hojas ACTION tiene total 0 y estado NO_WORK. Un proyecto sin nodos tiene una instantánea vacía.

Los porcentajes y contadores no se guardan en SQLite. `NodeTreeSnapshot` calcula todos los progresos de una instantánea con un recorrido iterativo de hojas a raíz, sin profundidad fija. Un ciclo o relación inválida produce un error de integridad controlado, no recursión infinita.

Además, el progreso del proyecto es derivado de todas las tareas hoja del árbol del proyecto según el mismo criterio: las capas no cuentan como unidades de trabajo, y el proyecto queda en `NO_WORK` si no hay hojas relevantes. La compensación del proyecto no se persiste ni se almacena en Room; se recalcula desde los nodos existentes y se usa solo para la representación visual.

La UI de capas consume `NodeRepository.observeProjectState`: una emisión contiene nodos, relaciones y progreso coherentes. Al cambiar un descendiente, el porcentaje de la capa abierta se recalcula sin salir, volver a entrar ni refrescar manualmente.

---

# 10. MVP v0.1.0

El primer MVP debe demostrar únicamente que el núcleo de Arachn0de funciona.

Debe permitir:

### Proyectos

- crear proyectos;
- visualizar proyectos;
- editar proyectos;
- eliminar proyectos.

### Capas de cebolla

- crear nodos;
- editar nodos;
- eliminar nodos;
- crear nodos hijos;
- navegar entre niveles;
- soportar jerarquías de profundidad práctica arbitraria;
- ordenar elementos de forma estable.

### Progreso

- reconocer automáticamente las hojas como completables;
- completar/descompletar nodos;
- mostrar progreso derivado de los descendientes.

### Persistencia

- almacenar toda la información localmente;
- conservar los datos después de cerrar la aplicación;
- conservar los datos después de reiniciar el dispositivo.

### Interfaz

Crear una interfaz limpia y sencilla utilizando:

- Jetpack Compose;
- Material 3;
- soporte para modo claro;
- soporte para modo oscuro.

La interfaz del MVP debe priorizar funcionalidad y claridad sobre efectos visuales complejos.

---

# 11. Navegación inicial

Flujo aproximado:

```text
Projects
   ↓
Project
   ↓
Root Nodes
   ↓
Node
   ↓
Child Nodes
   ↓
...
```

El usuario debe poder comprender en qué nivel de la jerarquía se encuentra y regresar fácilmente al nivel anterior.

---

# 12. Base de datos

Utilizar:

**Room + SQLite**

Room debe proporcionar la capa de persistencia.

El esquema actual es la **versión 14** (valores predeterminados de creación; véase la sección final). v4 incorporó el orden persistente de proyectos, v5 Personas/Responsables, v6 fechas opcionales, v7 ACTION/NOTE, v8 obligaciones, v9 recurrencia, v10 etiquetas, v11 eventos, v12 Priority, v13 creation groups y v14 CreationDefaults. Se conservan los esquemas históricos **1–14** y todas sus rutas de migración; no se usa `fallbackToDestructiveMigration`.

### Invariantes de nodos

- Todo nodo pertenece a un proyecto existente. Identidad y proyecto de un nodo son inmutables.
- El padre debe existir y pertenecer al mismo proyecto. SQLite lo exige mediante una clave foránea compuesta `(projectId, parentId)` → `(projectId, id)` y su índice único de referencia.
- No se permite autoparentesco ni mover un ancestro debajo de un descendiente. Repository valida la cadena de padres dentro de la transacción; triggers SQLite rechazan ciclos incluso al escribir directamente sin Repository.
- Los recorridos de ancestros usan IDs visitados. La consulta recursiva de defensa SQL usa `UNION` para no repetir IDs. El cálculo de descendientes/progreso es iterativo y verifica que todos los nodos hayan podido procesarse.
- Triggers SQLite impiden completar manualmente contenedores y normalizan el completado de los padres afectados al insertar, mover o eliminar hijos. Se instalan en bases nuevas y durante la migración 2→3; no aparecen en el JSON de esquema de Room, por lo que tienen pruebas explícitas.
- Crear, editar, trasladar, completar/descompletar, alternar completado y borrar nodos pasan por transacciones de Room. Las escrituras de contenido, estructura y completado actualizan únicamente sus campos respectivos.
- La eliminación de un nodo elimina su subárbol; la eliminación de un proyecto elimina sus nodos. El borrado por DAO/Repository desconecta los vínculos internos antes de eliminar, dentro de una única transacción, para evitar cascadas recursivas dependientes de la profundidad. Las claves foráneas CASCADE se conservan como defensa del esquema; SQL directo que omita este procedimiento sigue sujeto al límite de SQLite.
- `moveNode(id, parentId)` separa traslado de edición. `parentId = null` significa mover a raíz; no significa conservar el padre. La interfaz «Mover a…» se añadió posteriormente reutilizando esta operación; véase la sección 21.
- Al crear o trasladar a otro padre, la posición se asigna como máximo entre hermanos + 1 dentro de la transacción. El orden de lectura usa posición, fecha de creación e ID. Las posiciones históricas duplicadas no se renumeran en esta migración; existe desempate determinista. El reordenamiento manual y la normalización posterior se implementan en el bloque de orden de Sprint 5.5.
- Las fechas de los nodos reflejan sus escrituras directas. No se actualizan fechas de ancestros por progreso derivado; la normalización automática del completado de padres conserva sus fechas.

### Migración 2→3 y datos anteriores

Se reconstruye la tabla de nodos dentro de la transacción de migración, preservando IDs, proyectos, títulos, descripciones, posiciones y fechas. Se retiran las dos banderas de tipo y se conserva el completado de hojas válidas. El completado de antiguos contenedores se normaliza a pendiente; las antiguas hojas estructurales pasan a ser tareas.

Ante relaciones antiguas corruptas:

- padre ausente o de otro proyecto: el nodo se conserva como raíz de su propio proyecto;
- ciclo: se corta solo el vínculo al padre del nodo con menor ID lexicográfico dentro del ciclo, conservando todos sus nodos;
- proyecto ausente: se aborta la migración, con rollback a la base anterior; no se inventan proyectos ni se descartan nodos.

Las relaciones válidas se conservan. Antes de reemplazar la tabla antigua se desconectan sus vínculos, dentro de la misma transacción, para evitar cascadas recursivas durante la reconstrucción. Se comprueba que se reinserte el mismo número de nodos. Esta reparación es exclusiva de datos heredados: las escrituras nuevas inválidas se rechazan.

La base de datos es local.

No introducir:

- Firebase;
- Supabase;
- servidores;
- APIs remotas;
- cuentas;
- sincronización cloud.

---

# 13. Estado y flujo de datos

Favorecer un flujo de datos predecible.

Compose debe reaccionar al estado de la aplicación.

Las operaciones persistentes deben pasar por las capas correspondientes y no realizarse directamente desde componentes UI.

Evitar duplicar innecesariamente el estado entre interfaz y base de datos.

---

# 14. Reglas de código

El código debe:

- utilizar nombres descriptivos;
- mantenerse modular;
- evitar archivos gigantes;
- separar UI, datos y lógica;
- incluir comentarios cuando expliquen decisiones o comportamiento no evidente;
- evitar comentarios que simplemente repitan el código;
- eliminar código muerto;
- evitar dependencias innecesarias;
- mantener el proyecto compilable después de cambios significativos.

No realizar grandes refactors sin una razón concreta.

No añadir una dependencia cuando las herramientas estándar de Android/Kotlin resuelvan adecuadamente el problema.

---

# 15. Git

El proyecto utiliza Git para control de versiones.

El repositorio ya está inicializado, la rama principal es `main` y existe un commit inicial limpio anterior a la implementación del MVP. No debe tratarse la inicialización de Git como una tarea pendiente.

Los cambios deben realizarse en unidades comprensibles.

Evitar mezclar refactors grandes con nuevas funcionalidades en el mismo cambio cuando sea posible.

No modificar archivos no relacionados con la tarea actual sin una razón técnica clara.

---

# 16. Pruebas mínimas del MVP

Todas las comprobaciones de esta sección son criterios obligatorios de aceptación del MVP, incluidas la creación, edición y eliminación de proyectos y nodos.

Comprobar como mínimo:

- crear proyecto;
- editar proyecto;
- eliminar proyecto;
- crear nodo raíz;
- crear nodo hijo;
- crear varios niveles de profundidad;
- editar nodo;
- eliminar nodo;
- completar/descompletar nodo;
- cálculo de progreso;
- persistencia después de cerrar/reabrir;
- navegación hacia niveles profundos;
- volver correctamente a niveles anteriores.

Añadir pruebas automatizadas donde aporten valor, especialmente para lógica de progreso y operaciones de datos.

---

# 17. Definition of Done — v0.1.0

El MVP se considera funcional cuando un usuario puede:

1. abrir Arachn0de;
2. crear un proyecto;
3. entrar al proyecto;
4. crear una estructura de Capas de cebolla;
5. añadir varios niveles;
6. reconocer las hojas como tareas completables;
7. completar elementos;
8. observar cómo cambia el progreso;
9. cerrar la aplicación;
10. abrirla nuevamente;
11. encontrar intacta la estructura creada.

Además de este recorrido, deben cumplirse todas las comprobaciones de la sección 16. Este resumen no sustituye los criterios de creación, edición y eliminación allí descritos.

El proyecto debe compilar correctamente.

No deben existir crashes conocidos en los flujos principales.

## Baseline de compilación anterior al MVP

La compilación inicial fue verificada antes de comenzar la implementación mediante:

```bash
./gradlew assembleDebug
```

Resultado registrado:

```text
BUILD SUCCESSFUL in 2m 34s
36 actionable tasks: 36 executed
Configuration cache entry stored.
```

Este resultado corresponde a la base inicial, no a una validación de funcionalidades del MVP. Los cambios posteriores deben volver a compilarse y comprobarse según su alcance.

---

# 18. Sprint 5 — Capas de cebolla navegables

Este sprint introduce la primera experiencia visual y navegable de la jerarquía del proyecto.

La meta no es mostrar todo el árbol a la vez, sino que el usuario recorra una estructura tipo:

```text
Proyecto
  └── Capa 1
      └── Capa 2
          └── Capa 3
```

## Reglas del sprint

- El flujo principal es: Proyecto → Capa → Capa → Capa...
- La pantalla debe ser mobile-first y legible en un teléfono.
- La navegación debe permanecer por capas, una a la vez.
- Cada nivel debe mostrar únicamente los nodos hijos del nivel actual.
- El usuario debe poder volver al nivel anterior con un gesto claro o botón explícito.
- La ruta actual debe permitir comprender la posición dentro de la jerarquía sin mostrar el árbol completo.
- El diseño debe priorizar claridad sobre densidad visual.
- Al entrar en un nodo sin hijos se muestra un estado vacío con opción de añadir un hijo; en el dominio ese nodo sigue siendo una hoja hasta que tenga hijos.

## Reglas de implementación

- Se mantiene el flujo UI → lógica de aplicación/dominio → Repository → Room. Las invariantes viven en Repository/SQLite y el cálculo de progreso en el dominio. En Sprint 5.5, bloque 5, las escrituras pasan por acciones de presentación y MainActivity queda como punto de entrada; pantallas, navegación, diálogos y componentes están separados.
- No se introduce una base de datos separada ni una nueva capa de backend.
- La navegación visual debe reutilizar el modelo existente de `Node` y `NodeRepository`.
- Los cambios en `Node` deben reflejarse reactivamente en la UI a través de `Flow` de Room y del Repository; no debe existir una segunda fuente de verdad local para la capa visible.
- La capa visible debe actualizarse automáticamente tras crear, editar, eliminar y completar/descompletar un elemento sin salir de la pantalla.
- El proyecto raíz no cuenta como capa; la primera capa real es `Capa 1`.
- La capa actual debe poder retroceder de forma inmediata con botón de retroceso, `BackHandler`/atrás del sistema y también mediante un selector de Capas de cebolla.
- La navegación por capas debe ser accesible de forma compacta y permitir saltar directamente a cualquier capa previa sin volver paso a paso.
- Capas de cebolla es el único navegador jerárquico: reúne ubicación actual, saltos entre ramas y regreso a Proyectos/Home. Sustituye al mapa independiente y al antiguo selector de ancestros.
- El selector de Capas de cebolla debe limitar su altura máxima y permitir scroll interno para soportar profundidades arbitrarias sin romper la interfaz.
- El proyecto puede tener varias ramas, por lo que no existe una sola “capa siguiente”; la navegación debe permitir saltos a cualquier nodo del árbol completo desde Capas de cebolla.
- Las hojas representan las unidades de trabajo medibles en progreso.
- Los contenedores estructurales no deben tratarse como elementos completables manualmente.
- La información derivada de progreso debe calcularse a partir de datos persistidos y no duplicarse como estado de base de datos.
- La UI no debe pedir al usuario que elija un "tipo técnico" de elemento; la estructura define si el elemento funciona como tarea o como capa.
- Los elementos completados deben seguir visibles, accesibles y reabiertos para descompletar.
- Las tarjetas priorizan el estado del elemento, su nombre y la navegación sobre la exposición permanente de acciones secundarias.
- Las acciones de edición y eliminación se reservan para un menú contextual para no saturar la interfaz móvil ni competir con la acción principal de completar.
- El concepto visible de navegación profunda se llama `Capas de cebolla`, no `ruta de ancestros`.
- La UI puede presentar conceptos amigables como `Tarea` y `Capa / contenedor`, pero el modelo interno sigue siendo `Node`.

## Criterio de aceptación del sprint

El sprint se considera realizado cuando el usuario puede:

1. abrir un proyecto desde el dashboard;
2. navegar por la jerarquía de capas desde el nivel raíz;
3. entrar a una capa y ver solo sus hijos;
4. crear nuevos nodos dentro de la capa actual;
5. volver a la capa anterior;
6. completar o descompletar hojas;
7. ver progreso actualizado por capa y por nodo;
8. cerrar y reabrir la aplicación y conservar la estructura.

Este sprint sigue siendo una mejora incremental del MVP, no una reescritura del núcleo de datos.

---

# 19. Sprint 5.5 — Estabilización del núcleo

Fase formal entre Sprint 5 y Sprint 6. Su objetivo es corregir la deuda detectada por la auditoría, por bloques revisables, sin añadir funcionalidades futuras. Sprint 6 no está iniciado.

## Bloque 1: invariantes y progreso reactivo

Implementa las reglas de las secciones 7, 9 y 12: una sola fuente estructural de verdad, transiciones hoja → capa → hoja, ausencia de cascada de completado, protección contra ciclos, coherencia proyecto–padre, escrituras atómicas, migración explícita y progreso observable coherente.

La UI solo recibe la instantánea observable y deja de decidir o persistir tipos. Se eliminan sus consultas repetidas por hijo para calcular progreso. No se realiza el gran refactor de MainActivity ni un rediseño.

Las pruebas existentes de nodos se conservan adaptadas a la nueva regla: ya no se espera que una hoja estructural vacía carezca de trabajo; pasa a ser una tarea pendiente. Se añaden regresiones de transiciones, ciclos, claves foráneas y triggers, concurrencia, progreso reactivo, 32 niveles y migración desde esquemas 1 y 2, incluyendo datos corruptos y rollback. Las pruebas de migración se ejecutan en Robolectric API 24 y 28.

## Bloque 2: eliminación de árboles profundos

`NodeDao.delete` captura los IDs del subárbol mediante una CTE con `UNION` (sin repetir IDs), desconecta sus relaciones y elimina los nodos en lotes de 500 IDs para respetar el límite de parámetros de SQLite en API 24. Todos los lotes se desconectan antes de comenzar a borrar. El padre externo y las ramas hermanas se conservan; los triggers existentes dejan al padre como hoja pendiente si pierde su último hijo.

`ProjectDao.delete` desconecta todos los nodos del proyecto antes de borrar el proyecto; CASCADE elimina entonces nodos sin cadenas de descendencia. Ambas operaciones son transaccionales: los observadores no reciben estados intermedios y un fallo restaura relaciones, nodos y proyecto. Borrar un elemento ausente sigue devolviendo `false` en Repository.

No cambia el esquema Room ni requiere migración. La selección del subárbol consume memoria proporcional a sus nodos; no se promete capacidad ilimitada. Las regresiones cubren 1200 niveles, preservación de ramas/otros proyectos y rollback ante un fallo inyectado después de desconectar, en API 24 y 28.

Validación del bloque 2 (2026-10-01): `testDebugUnitTest --rerun-tasks` completó 47 pruebas sin fallos (6 ejecuciones nuevas); `assembleDebug`, `lintDebug` y `git diff --check` finalizaron correctamente.

## Bloque 3: sistema visual

La identidad de la aplicación es la araña: `arachn0de_logo.png` se conserva intacto y se usa en el inicio y el launcher. La cebolla identifica nodos con hijos (capas), nunca sustituye al logo de la aplicación. Los nodos hoja conservan el control de completado.

Por indicación del usuario se sustituye el antiguo recurso circular `cebolla_icon.png` por el archivo proporcionado `cebollaicon.png` (1254 × 1254, RGBA), copiado sin modificar sus bytes. Se mantiene el nombre interno `cebolla_icon`. Las vistas conservan sus colores, sin tintes ni recorte; la tarjeta usa un espacio de 36 dp y `ContentScale.Fit` para mostrar su silueta completa. Ambos PNG están en `drawable-nodpi`; Compose controla su tamaño en dp.

El launcher reutiliza exclusivamente el logo de araña: icono adaptativo desde API 26 con fondo oscuro y margen del 20 % por lado, y drawable compuesto para API 24–25. Se eliminan los WebP del robot Android, el fondo de plantilla y `cebolaicon.xml` sin uso. No se declara una variante monocromática temática. Referencia técnica: https://developer.android.com/reference/android/graphics/drawable/AdaptiveIconDrawable.

`Arachn0deColors` centraliza la paleta semántica de Compose; se unifican variantes casi idénticas y el diálogo de eliminación de proyecto adopta la superficie oscura común. El tema Material utiliza estos mismos colores. Se retiran las paletas moradas/rosadas y colores XML de plantilla. El único color XML es el fondo utilizado por el launcher, documentado como reflejo de `Arachn0deColors.Background`.

El tema actual es explícitamente oscuro, como ya lo era la pantalla principal. No se anuncia soporte claro ni colores dinámicos. Quedan pendientes modo claro, accesibilidad y comprobación visual en dispositivos/launchers reales. Este bloque no modifica persistencia, navegación ni distribución de pantallas.

Validación del bloque visual (2026-10-01): 47 pruebas sin fallos con `testDebugUnitTest --rerun-tasks --console=plain`; `assembleDebug`, `lintDebug` y `git diff --check` correctos. Lint conserva 15 advertencias, incluidas dos por ausencia deliberada de icono monocromático. Tras trasladar también el logo a nodpi se repitieron build y lint. No había dispositivo/emulador conectado para validación visual.

### Inicio e identidad en la navegación

La ventana nativa de arranque tiene fondo negro y muestra la araña y el nombre Arachn0de. API 24–30 usa `startup_background`; desde API 31 se configuran los atributos nativos SplashScreen y un wordmark vectorial de 200 × 80 dp. No se agrega otra Activity ni una demora artificial; Android controla la duración y puede omitir el splash en un arranque caliente. Referencia: https://developer.android.com/develop/ui/views/launch/splash-screen.

El logo de araña permanece junto al nombre en el menú lateral y se retira de la cabecera principal para liberar espacio. La cabecera conserva el nombre de la app y los controles existentes. La cebolla continúa reservada a las capas. Queda pendiente verificar visualmente el arranque en versiones de Android y tamaños reales.

## Bloque 4: navegación y restauración

`AppRoot` guarda únicamente el ID del proyecto mediante `rememberSaveable` y obtiene el proyecto vigente del flujo de Room. La ruta de capas guarda una lista de IDs con un `Saver` de Compose. Al recrear la Activity se recuperan proyecto y profundidad, sin serializar entidades ni instantáneas de base de datos.

Atrás dentro de una capa sube un nivel; desde la raíz del proyecto vuelve a proyectos. En el dashboard se mantiene el comportamiento de salida del sistema. Si el menú lateral está abierto, atrás primero lo cierra. Los diálogos siguen usando su manejo modal de atrás.

La ruta se valida después de recibir una instantánea persistida, nunca contra el estado vacío inicial. Si un nodo desapareció o cambió de padre, se conserva el prefijo válido hasta el ancestro existente; si desapareció el proyecto, se vuelve al dashboard. Mientras se recupera el proyecto no se muestra un proyecto obsoleto. La restauración utiliza el estado guardado de Android: no implica recordar una sesión después de una salida voluntaria ni force-stop.

Las pruebas Compose/Robolectric ejercitan atrás desde raíz, varios niveles y recreación real mediante `ActivityScenario.recreate()`, cierre del menú y recuperación tras eliminar capas/proyectos. No se afirma haber probado muerte real del proceso ni gestos predictivos en dispositivos. Formularios, desplazamiento y diálogos transitorios no se restauran en este bloque. El ciclo de vida de Room y la separación de MainActivity se resuelven posteriormente en el bloque 5.

Validación del bloque 4: `./gradlew testDebugUnitTest --rerun-tasks --console=plain` pasó 53 pruebas (6 nuevas de navegación); `./gradlew assembleDebug --console=plain`, `./gradlew lintDebug --console=plain` y `git diff --check` correctos.

## Bloque 5: errores de persistencia y separación de UI

`MainActivity` solo configura la ventana y el contenido. `Arachn0deApplication` conserva una instancia perezosa de Room y de los repositorios durante la vida del proceso. Recrear o destruir una Activity no cierra la base compartida. Las pruebas que crean bases propias siguen siendo responsables de cerrarlas.

La UI se organiza en `AppRoot` (selección y restauración), `ProjectsScreen`, `ProjectScreen`, `EditDialogs`, componentes de proyectos/nodos, `LayerMap` y `NavigationChrome`. La extracción conserva comportamiento y aspecto. La recursión del mapa se elimina en el bloque posterior de escalabilidad.

`ProjectActions` y `NodeActions` coordinan las escrituras. `OperationState` conserva el estado ocupado/error, impide envíos solapados y solo ejecuta el cierre del formulario o confirmación después de un resultado satisfactorio. Se comprueban los booleanos de editar, eliminar y completar; un `false` informa que el elemento desapareció o que la operación dejó de ser válida. Un fallo de SQLite conserva el editor y sus campos, presenta un mensaje comprensible y permite reintentar. Las confirmaciones de eliminación tampoco se cierran si falla la persistencia. Durante el envío se deshabilitan guardar, cancelar y descartar el diálogo.

`LoadState` captura fallos de observación, conserva la última instantánea válida y ofrece reintento explícito. Los proyectos se observan una sola vez desde `AppRoot`; la pantalla de proyectos recibe la lista. No se muestran excepciones técnicas como mensajes al usuario. Tanto carga como escritura propagan `CancellationException`; no se presenta una cancelación como guardado exitoso ni como fallo de almacenamiento.

Los estados de operaciones son objetos de presentación recordados por cada pantalla, con coroutines ligadas a su composición; no se introducen ViewModels ni una nueva librería de navegación. En aquel bloque, los diálogos permanecían en las pantallas y los campos en los editores, sin restaurar borradores. El bloque posterior de recuperación de estado añade esa restauración y reintentos de creación idempotentes. No se mantienen escrituras en segundo plano; los resultados confirmados se recuperan mediante los flujos de Room.

Las regresiones incluyen fallos reales inyectados con triggers SQLite en crear proyectos/nodos, editar y eliminar proyectos; preservación de campos, reintento, rechazo por elemento desaparecido, bloqueo de doble envío, cancelación, error de lectura y conservación de la misma base al recrear Activity. Los triggers de fallo solo existen en las bases de prueba.

Validación del bloque 5: `./gradlew testDebugUnitTest --rerun-tasks --console=plain` pasó 64 pruebas (11 nuevas); `./gradlew assembleDebug --console=plain`, `./gradlew lintDebug --console=plain` y `git diff --check` correctos. Lint conserva 15 advertencias y no registra errores. No se realizaron pruebas manuales en dispositivo.

## Escalabilidad de listas y mapa — Sprint 5.5

Las listas de proyectos, nodos de la capa abierta, mapa y ruta de ancestros utilizan `LazyColumn`. Las claves se basan en IDs persistentes; las filas de acciones y la raíz del proyecto tienen claves reservadas distintas. El índice de una fila solo sirve para su posición visual, nunca para identificar un nodo. Se conservan tarjetas, colores y acciones. Los contenedores de diálogo tienen altura acotada y no anidan listas lazy dentro de un scroll vertical sin límites. El drawer contiene una lista pequeña y fija de destinos y no forma parte de las listas de datos crecientes.

El mapa anterior construía `LayerTreeNode` recursivamente y componía `LayerMapBranch` recursivamente con todas las ramas abiertas; su estado de expansión no tenía interacción que lo modificara. Ambos recorridos se sustituyen por `LayerMapIndex`: agrupa hijos por padre, ordena hermanos por posición/fecha/ID y valida referencias/ciclos iterativamente. Una pila explícita realiza DFS preorden para producir solamente las filas visibles, con profundidad numérica. No hay límite artificial de profundidad ni composables anidados por nivel. Los conectores ya no dependen de un umbral de profundidad 4.

El índice se recuerda por instantánea de nodos; las filas visibles se recalculan al cambiar índice o expansión. Construir/validar el índice cuesta O(N + suma de k·log(k) por grupos de hermanos), con memoria O(N). Aplanar una expansión cuesta O(V), siendo V el número de filas visibles por expansión, y no visita descendientes de ramas cerradas. LazyColumn compone la ventana visible y el margen de trabajo que gestione Compose, no todas esas V filas. Cambiar la expansión también copia/construye el conjunto de E IDs abiertos, por lo que el coste de la interacción es O(E + V), aparte de la composición visible. Los IDs de la ruta activa se convierten a un conjunto para comprobar resaltado sin buscar linealmente por cada fila.

La expansión se guarda por ID de nodo y proyecto con `rememberSaveable`. Inicialmente las ramas están cerradas; abrir el mapa expande la ruta actual para revelar la ubicación. El botón de flecha expande/contrae sin navegar y el toque de tarjeta navega al ID. Contraer un padre conserva las elecciones de sus descendientes. El estado persiste al cerrar/reabrir el mapa y al recrear la Activity; los IDs eliminados se depuran tras una emisión de Room. El estado guardado crece con la cantidad de ramas abiertas y sigue sujeto a los límites reales del Bundle de Android; no se promete expansión/restauración ilimitada.

Pruebas sintéticas: 10.000 niveles; 10.000 tareas hermanas; 2.000 capas con cinco hijos cada una; orden determinista, ciclos, expansión y contracción. Las pruebas Compose cubren mapa de 5.000 nodos, lista de 3.000 proyectos, capa de 2.000 tareas persistidas en Room y ruta de 5.000 ancestros, ausencia de elementos lejanos antes de desplazarse y navegación por ID. Se verifica además expansión tras recreación real de Activity. No se usan umbrales de tiempo como benchmark.

Limitaciones: Room y `NodeTreeSnapshot` aún cargan/calculan el proyecto completo en cada emisión; el índice se reconstruye cuando cambia esa instantánea. Medir en dispositivos representativos el tiempo hasta primera fila, duración de frames/jank al desplazar/expandir, asignaciones/GC, memoria máxima y latencia al completar tareas con 1.000/10.000+ nodos. También medir guardado/restauración con muchas ramas abiertas, orientación y tamaños pequeños. Robolectric comprueba comportamiento, no acredita FPS, tiempos ni consumo real. No se añade paginación, reordenamiento ni un rediseño.

Validación del bloque de escalabilidad: `./gradlew testDebugUnitTest --rerun-tasks --console=plain` pasó 75 pruebas, sin fallos, errores ni omitidas (11 nuevas); `./gradlew assembleDebug --console=plain` correcto; `./gradlew lintDebug --console=plain` sin errores, con 15 advertencias y 1 sugerencia existentes; `git diff --check` limpio. Tras ajustar la expansión a una lista inmutable con Saver explícito, se repitieron todas las pruebas, build y lint mediante `./gradlew testDebugUnitTest assembleDebug lintDebug --console=plain`: BUILD SUCCESSFUL, con los mismos resultados. Se revisó el diff completo, incluidos los archivos nuevos. No se hicieron mediciones de rendimiento en dispositivo.

## Reordenamiento manual y posiciones históricas — Sprint 5.5

El menú de nodo permite mover arriba/abajo un lugar entre hermanos, tanto en raíz como dentro de una capa. Las opciones se deshabilitan en los extremos y durante una operación. La tarjeta conserva su ID, padre, descendientes, contenido y completado. El mapa y la lista reciben el orden nuevo a través del flujo de Room. Para los proyectos, el orden manual persistente utiliza `Project.position` y la base queda en Room v4; el drag-and-drop de proyectos no se implementó en aquella iteración y se añadió después, como se documenta en «Drag y reorder visual de proyectos y nodos».

`reorderNode` lee el nodo y sus hermanos dentro de una transacción, valida el padre esperado para rechazar acciones sobre una ubicación obsoleta, intercambia vecinos y asigna posiciones contiguas 0..n−1. No se utiliza el índice visual como identidad. Un nodo ausente o con padre distinto devuelve false; mover más allá de un extremo es una operación idempotente que también puede normalizar el grupo. Se actualizan únicamente posición y fecha de modificación, nunca una entidad completa. La fecha cambia para los dos nodos intercambiados; la reparación de numeración conserva fechas históricas.

La política para datos antiguos es conservar el orden visible por posición, fecha de creación e ID y compactar cada grupo de hermanos por separado. `observePreparedProjectState`, utilizado por la pantalla, normaliza todos los grupos del proyecto en una única transacción antes de emitir el contenido inicial. `observeProjectState` y los demás observadores generales siguen siendo de solo lectura. La pantalla espera la primera instantánea validada antes de mostrar una ruta restaurada. Las normalizaciones posteriores sin cambios no escriben filas. No se modifica otro proyecto ni se requiere una migración adicional para normalizar nodos. El esquema fue Room v4 por la incorporación de posiciones de proyectos; Personas + Responsables lo amplió a v5 y la base temporal a v6, sin cambiar ese orden.

Reordenar vuelve a normalizar el grupo dentro de la misma transacción, incluso si había posiciones duplicadas o huecos. Crear/trasladar continúa añadiendo al final mediante máximo+1; si se alcanzó `Int.MAX_VALUE`, primero compacta los hermanos dentro de esa transacción. Borrar puede dejar huecos válidos hasta la próxima preparación o reordenamiento. No se introduce un índice UNIQUE sobre posiciones: las garantías se aplican mediante Repository, y SQL externo podría volver a introducir duplicados.

El menú se cierra solo después de persistir correctamente. Un fallo conserva el menú y muestra el error mediante `OperationState`, permitiendo reintentar; se mantiene el bloqueo de operaciones simultáneas por pantalla. Las pruebas incluyen raíces, hijos, extremos, posiciones repetidas, fechas, preservación de subárboles/completado/progreso, padre obsoleto, aislamiento entre proyectos, concurrencia, rollback inyectado y saturación de posición. Los tests de datos corren en API 24 y 28; los de Compose comprueban menú, error/reintento y recreación.

Costes: normalizar un proyecto requiere leer N nodos y escribir solo posiciones que cambian; reordenar lee el grupo de hermanos y normalmente escribe dos filas si ya estaba compacto. Todo el trabajo de datos es transaccional, sin recorridos recursivos. La latencia de la preparación inicial con datos históricos masivos debe medirse en dispositivo junto con los pendientes de escalabilidad.

Validación del bloque de orden: `./gradlew testDebugUnitTest --rerun-tasks --console=plain` pasó 93 pruebas, sin fallos, errores ni omitidas (18 ejecuciones nuevas); `./gradlew assembleDebug --console=plain` correcto; `./gradlew lintDebug --console=plain` sin errores, con 15 advertencias y 1 sugerencia; `git diff --check` limpio. Se revisó el diff del bloque.

## Recuperación de estado — Sprint 5.5

`EditorDraft` conserva ID de edición, ID del padre original, título/nombre, descripción e identidad de creación mediante estado observable y un Saver explícito de cadenas. Las pantallas guardan el borrador con `rememberSaveable`; los editores reciben ese estado y no lo reconstruyen a partir de emisiones de Room. Un borrador nulo significa formulario cerrado. Cancelar o guardar correctamente lo descarta; un fallo conserva los campos. No se serializan entidades ni se escriben borradores en SQLite: Room sigue siendo la fuente de verdad del contenido confirmado.

La recreación recupera formularios nuevos y de edición de proyectos y nodos, confirmaciones de eliminación (ID y nombre mostrado), y apertura del mapa y del selector de capas. Restaurar una confirmación nunca ejecuta la acción. El drawer, los menús contextuales y los avisos efímeros de error no se restauran. Si desaparece el elemento editado o el padre de creación, se conserva el destino original: guardar falla y el texto sigue disponible para copiar o cancelar. Nunca se transforma una edición en creación ni se redirige un hijo a raíz al recuperar la navegación. Si desaparece el proyecto abierto, se vuelve al dashboard como antes.

Cada borrador de creación recibe un UUID antes de guardar, conservado en el estado guardado. `createProject` y `createNode` aceptan esa identidad; consultar/insertar se realiza transaccionalmente. Un reintento con el mismo ID y contenido reconoce la fila confirmada sin volver a insertar, cambiar fechas, posición, completado ni descendientes. Si la fila ya tiene contenido/destino diferente, se rechaza el reintento y se conserva el formulario; no se sobrescribe silenciosamente Room. Las creaciones normales sin ID explícito siguen generando uno nuevo. No se usa REPLACE, no cambia el esquema y no se introduce un registro persistente de borradores u operaciones.

`OperationState` sigue ligado a la composición. Una recreación descarta el estado efímero ocupado/error, cancela su coroutine y no reenvía operaciones automáticamente. El formulario vuelve a permitir una acción explícita. Si una creación se confirmó antes de perder su callback, el ID conservado evita duplicados al reintentar, incluso tras reabrir Room. Una edición siempre usa su ID original; una eliminación ya aplicada informa que el elemento no existe. No se promete mantener tareas en segundo plano ni un protocolo de operaciones exactamente una vez después de borrar la fila: la idempotencia de creación reconoce la fila mientras exista. No se añaden ViewModels ni dependencias.

### Desplazamiento

El estado de la lista de proyectos se conserva en `AppRoot`, también durante una visita a un proyecto. Cada capa visitada tiene su `LazyListState` en un `SaveableStateHolder`, con clave por ID de nodo y una clave distinta para la raíz. Volver al padre y recrear la Activity recuperan su posición. Mapa y recorrido conservan sus estados de lista en la pantalla, fuera de los diálogos, por lo que cerrar/reabrir no los reinicia. Se guardan índices de desplazamiento y offsets, no listas ni entidades; la identidad de las filas sigue siendo su ID persistente. Tras cambios en los datos, Compose ajusta la posición al contenido disponible; no se promete el mismo píxel si cambian filas o dimensiones.

### Proceso y atrás

Se utiliza el estado guardado de Android: puede recuperar la sesión cuando el sistema recrea el proceso y entrega su Bundle, pero no garantiza recuperación tras force-stop, eliminación de la tarea o salida voluntaria. El Bundle contiene solo entradas y posiciones, no Room, repositorios, Jobs ni el indicador «guardando». Textos muy largos, muchas capas visitadas y muchas ramas expandidas comparten el límite real del Bundle; no se afirma persistencia ilimitada. Referencia: [guardar estado en Compose](https://developer.android.com/develop/ui/compose/state-saving).

Se conserva `BackHandler` de AndroidX: confirmar atrás sube una capa o vuelve a proyectos, y cancelar el gesto no modifica la ruta. Los diálogos gestionan su ventana modal; atrás cierra el editor sin guardar ni salir del proyecto, y durante un envío no se descarta. No se intercepta `KEYCODE_BACK` ni se desactiva el callback moderno en el manifiesto. Se comprueban los eventos de inicio/progreso/cancelación/confirmación a través de `OnBackPressedDispatcher`; no se implementa una animación predictiva entre capas. Referencia: [compatibilidad con atrás predictivo](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture).

### Pruebas y límites

Las regresiones cubren recreación real de Activity para los cuatro formularios, padre original/eliminado, cancelación, borrado pendiente, mapa abierto y errores/reintentos. También cubren desplazamiento por capa, vuelta al dashboard y mapa cerrado/abierto, callbacks de atrás y cierre modal. Una prueba serializa el borrador a Bundle/Parcel y lo recupera como un objeto nuevo; otras reabren Room tras perder el callback de una creación y comprueban reintentos concurrentes. Estas pruebas no equivalen a matar un proceso Android real ni a ejecutar un gesto físico.

Pendiente en dispositivo: llevar la aplicación al fondo con cada tipo de borrador y posiciones desplazadas, matar únicamente el proceso en segundo plano (sin force-stop ni eliminar la tarea), restaurar desde recientes y verificar contenido, ruta y ausencia de escrituras automáticas; repetir con IME visible y orientaciones distintas. En Android 13–16 probar gesto cancelado/confirmado con editor, confirmación, drawer, capa interna y raíz, incluyendo atrás hacia Home. Comprobar tamaño del estado con descripciones largas y muchas capas visitadas. La prueba de formulario en capa vacía invoca la acción semántica del botón para aislar el estado del problema de espacio vertical pendiente de UI/UX.

Validación final del bloque: `./gradlew testDebugUnitTest assembleDebug lintDebug --console=plain` terminó con `BUILD SUCCESSFUL in 57s` (56 tareas: 6 ejecutadas, 50 actualizadas). Se completaron 110 pruebas sin fallos, errores ni omitidas; assembleDebug correcto y lint sin errores, con 15 advertencias y 1 sugerencia existentes. Diff completo revisado y `git diff --check` limpio. No había dispositivos conectados para las comprobaciones manuales. No se hicieron commit ni push.

## MVP UI READINESS — Sprint 5.5

Preparación para dogfooding en teléfono. Se mantiene el tema oscuro, la paleta negro/gris/violeta/naranja y los assets oficiales. No se implementan funciones futuras ni modo claro en este bloque.

`AppSafeArea` envuelve el contenido de la Activity con `windowInsetsPadding(WindowInsets.safeDrawing)`: consume una vez las barras del sistema, el recorte de cámara y el teclado, incluidos los insets laterales en horizontal. El fondo puede dibujarse bajo el sistema; cabecera, controles y panel lateral permanecen dentro del área segura. No se usan alturas de status bar estimadas. Las barras usan iconos claros sobre el fondo oscuro de la app, independientemente del modo del sistema. Los diálogos Material conservan su ventana modal y sus acciones; sus campos admiten desplazamiento. Referencias: [insets de Compose](https://developer.android.com/develop/ui/compose/system/insets-ui) y [recortes de cámara](https://developer.android.com/develop/ui/compose/system/cutouts).

La cabecera contextual de una capa pasa al contenido lazy para no agotar la altura disponible antes de las tareas. Crear elemento y volver a proyectos quedan fuera de esa lista, con altura mínima adaptable al texto; crear sigue disponible en capas vacías. Se conservan claves persistentes y restauración del desplazamiento por capa. El contexto es una fila reservada distinta de los nodos. En proyectos se retiran las métricas simuladas; el estado vacío puede desplazarse. Se eliminan alturas máximas de tarjetas del recorrido que cortaban texto ampliado, sin rediseñar el mapa ni sus conectores.

La búsqueda sin acción, los contadores Activos/Hoy y todos los destinos no funcionales del menú se retiran de la interfaz. El panel lateral conserva únicamente la identidad oficial, versión y cierre. La planificación de Inbox, filtros temporales, búsqueda, métricas, etiquetas, personas, calendario y demás destinos se mantiene para etapas posteriores; su retirada visual no elimina el roadmap.

Las acciones de proyectos se agrupan en un menú secundario con Editar/Eliminar y confirmación de borrado existente, siguiendo el criterio de los nodos. Los botones importantes de icono tienen área de 48 dp. El control de completado anuncia acción, título y estado pendiente/completada; los iconos decorativos junto a texto no duplican etiquetas. Los formularios y menús largos admiten scroll. Se conserva el comportamiento de errores, reintentos y borradores.

Pruebas nuevas limitadas a correcciones relevantes: área útil con insets asimétricos y texto 1,6× en 320×480 dp, botón de creación de una capa vacía accesible mediante toque real, dashboard/panel sin funciones simuladas y semántica del completado. Se adaptan los tests existentes al menú secundario de proyectos y a la fila contextual de capas; los de borradores dejan de invocar directamente la acción semántica del botón vacío.

Dogfooding pendiente: recortes reales en vertical/horizontal, navegación por gestos y tres botones, teclado abierto, texto/tamaño de pantalla ampliados, TalkBack y títulos/descripciones extensos. Las pruebas con insets sintéticos verifican geometría, no sustituyen dispositivos ni validan animaciones del sistema. Modo claro, refinamiento visual completo, mediciones de rendimiento y funciones futuras permanecen postergados.

Validación del bloque: la suite completa ejecutó 113 pruebas (3 nuevas); 107 pasaron y 6 requerían adaptar sus interacciones al contexto ahora desplazable. Tras adaptar solo esos tests, `testDebugUnitTest --tests '*DraftRestorationTest' --tests '*NavigationTest' --console=plain` pasó las 17 pruebas de ambas clases (`BUILD SUCCESSFUL in 32s`). No se repitieron las pruebas de datos ya correctas. El ajuste final del contraste de barras se verificó con una prueba de Activity, `assembleDebug` y `lintDebug`: `BUILD SUCCESSFUL in 42s`, 56 tareas (17 ejecutadas, 39 actualizadas). Lint conserva 15 advertencias y 1 sugerencia, sin errores. Diff completo revisado y `git diff --check` limpio. APK debug generado; la validación física queda para dogfooding. Sin commit ni push.

## Pendiente para los siguientes bloques

- **Navegación y estado:** validar muerte real del proceso y gestos predictivos en dispositivos; comprobar límites del Bundle con entradas grandes. Borradores, diálogos, desplazamiento e idempotencia de creaciones están implementados en el bloque de recuperación. Atrás desde raíz y ubicación ante recreación están cubiertos por el bloque 4.
- **Orden y escala:** benchmark/profiling en dispositivo y evaluación de paginación/incrementalidad si las mediciones lo requieren. Las listas lazy, claves estables, mapa iterativo y expansión funcional están implementados. La observación aún carga todo el proyecto.
- **Arquitectura de UI:** evaluar ViewModels solo si más adelante se requiere mantener tareas fuera de la composición; la recuperación actual no los necesita. La extracción de MainActivity, los errores de persistencia y la propiedad de Room están resueltos en el bloque 5.
- **UI/UX y accesibilidad:** dogfooding de áreas seguras, tamaños pequeños, texto ampliado y TalkBack; refinamientos según uso real. Las correcciones básicas están implementadas en MVP UI READINESS. Modo claro permanece pendiente para una etapa posterior; no se anuncia como disponible.
- **Assets y tema:** verificar el launcher en dispositivos y máscaras de fabricantes; variante monocromática temática si se decide incorporarla. El PNG original debe conservarse sin reemplazos no solicitados.
- **Pruebas adicionales:** muerte del proceso y gestos en dispositivos, fallos de almacenamiento en dispositivos reales, listas/árboles grandes y profundidades extremas en navegación y UI, pantallas pequeñas, fuente ampliada y accesibilidad. La prueba de progreso reactivo de este bloque verifica la instantánea consumida por la UI; no sustituye una prueba visual de Compose.

Personas, responsables, tags, versiones/releases, Change Sets, historial especializado, sincronización LAN y cifrado pertenecen a sprints futuros y no se implementan en Sprint 5.5.

Al terminar cada bloque se ejecutan pruebas, compilación, lint y `git diff --check`, se informa el resultado y se espera revisión antes de avanzar. No hacer commit ni push sin autorización.

---

# 20. Fuera del alcance del MVP

NO implementar todavía:

- cuentas;
- login;
- backend;
- cloud;
- Firebase;
- sincronización LAN;
- aplicación de escritorio;
- cifrado avanzado;
- contraseña maestra;
- contraseña alternativa;
- Motor de Atención;
- inteligencia artificial;
- integración Git;
- commits automáticos;
- Change Sets;
- releases;
- changelog automático;
- Project Path;
- snippets de código;
- equipos;
- roles;
- métricas por persona;
- reuniones;
- agenda;
- riesgos;
- decisiones;
- Gantt;
- Kanban;
- Scrum Poker;
- compras en cuotas;
- gestión especializada de series;
- métricas avanzadas;
- notificaciones complejas.

Estas funciones pertenecen al roadmap futuro.

---

# 21. Evolución posterior al MVP: arquitectura y roadmap

Esta sección formaliza la evolución posterior al MVP y distingue los pasos implementados de los pendientes; documentar una capacidad futura no autoriza su implementación automática. Sustituye el orden aproximado del roadmap anterior; la secuencia es tentativa y puede revisarse antes de cada bloque. Las capacidades futuras se construirán sobre el mismo núcleo:

```text
Proyecto → Nodo → jerarquía arbitraria de Nodos → Capas de cebolla
         → tareas hoja → progreso derivado → persistencia local
```

## IMPLEMENTADO — base que se conserva

`Node` es la unidad estructural universal. Actualmente una hoja ACTION es una tarea completable y una hoja NOTE es información; un nodo con hijos es una Capa/contenedor. La profundidad práctica no tiene un límite artificial. Una tarea que recibe hijos pasa a Capa; una Capa que pierde todos sus hijos por eliminación o traslado vuelve a hoja pendiente. Completar un hijo mantiene la relación estructural y la condición de Capa de su padre, aunque todos los hijos estén completados.

Las capas no se completan manualmente. El progreso existente se deriva de todas las tareas hoja descendientes, incluidas subcapas, sin contar los contenedores ni promediar sus porcentajes. `NodeTreeSnapshot` y los flujos del repositorio mantienen esta información reactiva. Las hojas NOTE quedan excluidas del trabajo medible.

La base actual es local-first y offline-first, sin cuenta ni backend obligatorio: proyectos, nodos, navegación por Capas de cebolla, orden manual, drag/reorder y progreso derivado se apoyan en Room/SQLite v8, con Personas, responsables, fechas locales, propósito de hoja y capacidad financiera opcional. Las secciones anteriores y los ajustes finales describen su implementación y las verificaciones físicas pendientes. Esta documentación no convierte esos pendientes en comprobaciones realizadas.

El repositorio ofrece `moveNode(id, parentId)` transaccional, valida el proyecto del padre y rechaza ciclos. La acción visible «Mover a…» está implementada y reutiliza esta operación. Reordenar entre hermanos y trasladar a otro padre son operaciones distintas.

### Mover Node a otra Capa — IMPLEMENTADO

«Mover a…» está disponible en el menú contextual de tareas y Capas y en el contexto del nodo abierto. Abre un diálogo que reutiliza `LayerNavigator` y `LayerHierarchyIndex`, con expansión por rama, profundidad visible y lista lazy. Permite elegir la raíz del mismo proyecto, una Capa o una tarea hoja que pasará naturalmente a Capa. El origen y todos sus descendientes quedan excluidos; el destino se vuelve a validar transaccionalmente en `moveNode`. Seleccionar el padre actual es una operación sin cambios.

Se conservan ID, título, descripción, completado del nodo trasladado, fecha de creación y subárbol. Solo se actualizan padre, posición y fecha de modificación del nodo movido, con la semántica existente de añadir al final mediante máximo+1 y compactar si hay saturación. Los triggers conservan las transiciones del antiguo/nuevo padre y `NodeTreeSnapshot` recalcula progreso. Los grupos disponibles/completados y el reorder siguen sus reglas actuales. No se modificaron repositorio, fórmula, Room ni schema para añadir esta interfaz.

`NodeActions.move` usa `OperationState`; el selector solo se cierra después del éxito. Durante la escritura bloquea selección/cancelación; un fallo muestra el error existente y permite reintentar conservando el diálogo. El ID del origen y la expansión del selector se restauran como estado de UI, sin repetir automáticamente la escritura. Si el origen desaparece, el selector informa que ya no existe y permite cancelar.

Cada emisión confirmada reconstruye iterativamente los ancestros del nodo abierto si sigue existiendo, preservando la ubicación dentro de él incluso si se movió un ancestro. Si desaparece, se conserva el retroceso seguro al prefijo válido existente. El traslado funciona solo dentro del mismo proyecto; no se implementa movimiento entre proyectos. La comprobación física del selector en pantallas pequeñas y jerarquías profundas sigue siendo parte del dogfooding.

### Personas + Responsables — IMPLEMENTADO

`Person` es una entidad independiente con UUID estable, nombre obligatorio (sin aceptar solo espacios) y avatar local opcional; no representa cuenta, login ni usuario autenticado. `PersonRepository` y `PersonActions` conservan la cadena UI → acciones → repositorio → Room. No se añaden dependencias ni servicios remotos.

Room pasa de **v4 a v5** mediante `PersonMigration4To5`: añade `persons` y `node_person`, con clave compuesta `(nodeId, personId)`, índice por Persona y foreign keys con borrado en cascada. Una Persona puede participar en varios Nodes y un Node tener 0..N Personas. La relación guarda IDs, no nombres. La migración conserva tablas, contenido, posiciones y triggers anteriores; no utiliza migración destructiva.

«Personas» es un destino funcional del panel existente, tanto desde proyectos como desde una capa. Permite listar, crear, editar y eliminar con confirmación, seleccionar/cambiar/quitar avatar. Al volver conserva la ruta del proyecto y el estado guardable mediante `SaveableStateHolder`. «Responsables» aparece en el menú de cada tarea/Capa y en el contexto de la capa abierta: un selector permite guardar ninguna, una o varias Personas. Su estado vacío indica dónde crearlas. Las tarjetas y la capa abierta muestran hasta tres avatares y `+N`, sin sección vacía. Los flujos de Room actualizan nombres, avatares y asignaciones sin reabrir pantallas.

El selector Android `PickVisualMedia` entrega la imagen que `AvatarStore` copia inmediatamente al almacenamiento privado de la app. Room guarda únicamente el nombre del archivo PNG; no guarda blobs ni depende de mantener acceso a la URI original. Se acepta una entrada de hasta 20 MiB y se genera una miniatura de hasta 512 px por lado, respetando orientación EXIF. Sin imagen disponible se muestra la inicial. Cambiar/quitar/eliminar y cancelar un borrador limpia archivos sin referencias, con limpieza de archivos como mejor esfuerzo. No existe crop avanzado.

Eliminar una Persona elimina sus asociaciones y conserva los Nodes. Eliminar un Node/subárbol elimina sus asociaciones y conserva las Personas. No existe herencia hacia descendientes: cada capa/tarea tiene responsables propios. El progreso, completado, orden y drag conservan su lógica; «Mover a…» conserva responsables porque mantiene el ID del Node.

Pruebas dirigidas: CRUD y persistencia, múltiples relaciones en ambos sentidos, renombrado, eliminación y rollback de selección inválida, traslado con responsables, copia persistente de avatar y retirada, migración del schema real v4 conservando proyectos/Nodes/triggers y cadenas anteriores. La prueba Compose verifica creación/edición/asignación/borrado, recreación de Activity y vuelta a la misma capa con el nombre actualizado. Se mantienen las regresiones de navegación y traslado.

Limitaciones reales: faltan comprobación física del selector Android y revisión visual en dispositivos pequeños. Un cierre abrupto del proceso durante un borrador de avatar puede dejar un archivo privado sin referencia; no hay recolector periódico de huérfanos. La restauración de borradores usa el Bundle de Android y conserva sus límites existentes. No se implementan roles, disponibilidad, contacto, autenticación, herencia ni sincronización.

### Fechas de tareas y estado temporal — IMPLEMENTADO

`Node` y `NodeEntity` añaden `startAt` y `dueAt` opcionales como `Long?`: instantes en epoch millis, coherentes con `createdAt`/`updatedAt`, sin guardar strings formateados ni estados temporales en Room. `TaskDatesMigration5To6` añade únicamente dos columnas INTEGER nullable a `nodes`. Se conservan proyectos, Nodes, Personas, responsables, posiciones, índices, constraints y triggers; las filas anteriores reciben fechas nulas. No hay migración destructiva.

Crear/editar una tarea hoja permite seleccionar fecha mediante `DatePicker` Material, elegir hora con `TimeInput`, cambiar y quitar Inicio/Vencimiento. El día UTC del selector se combina con hora y zona local del dispositivo para obtener el instante persistido; la presentación vuelve a usar la zona local. No se guarda una zona fija por tarea: viajar/cambiar la zona modifica su representación, no el instante. Las horas inexistentes por un salto de horario se rechazan con un mensaje; ante horas repetidas Calendar elige su offset estándar, sin selector de ocurrencia en esta versión. Referencias: [selector de fecha Compose](https://developer.android.com/develop/ui/compose/components/datepickers) y [selectores de hora](https://developer.android.com/develop/ui/compose/components/time-pickers).

`dueAt < startAt` es inválido cuando ambos existen; la UI muestra un error, bloquea guardar y conserva lo introducido. El repositorio vuelve a validar dentro de la transacción. `EditorDraft.Saver` incluye las fechas; `TaskDatePickerDraft` conserva campo, etapa y selección encima de las ventanas de diálogo para recuperar incluso el selector abierto. Una creación idempotente compara también fechas. Editar solo contenido de una capa conserva sus fechas dormidas; la escritura de contenido+fechas valida nuevamente que siga siendo hoja.

`TaskTemporal.state(node, now)` recibe el instante explícito y aplica estas fronteras/prioridades:

- Capa con hijos: sin estado temporal operativo, aunque conserve fechas.
- Hoja completada: COMPLETADA, sin urgencia aunque haya vencido o tenga inicio futuro.
- Hoja incompleta sin fechas: sin indicador temporal especial.
- PROGRAMADA: `now < startAt`; sigue visible y conserva su posición. Al alcanzar `startAt` deja de estar programada.
- VENCIDA: `now > dueAt`, después de descartar programada/completada.
- PRÓXIMA: `now <= dueAt <= now + UPCOMING_WINDOW_MILLIS`; incluye el instante exacto de vencimiento y el límite de proximidad.
- ACTIVA: hoja incompleta con fechas, disponible y fuera de los estados anteriores.

`UPCOMING_WINDOW_MILLIS` centraliza **24 horas exactas** (duración, no días de calendario); cambiar el umbral no requiere migración. Si Inicio y Vencimiento coinciden, antes del instante es programada, en él es próxima y después vencida. `nextTransition` calcula el siguiente cambio, usando `dueAt + 1 ms` para pasar a vencida, con protección ante overflow.

`rememberTaskScreenNow` es el único mecanismo temporal de la pantalla de proyecto, compartido por tarjetas y contexto abierto. Mientras está RESUMED espera la próxima frontera de todas las tareas del proyecto, incluidas las descendientes ocultas que alimentan Atención, con una revisión de reloj como máximo cada minuto. Relee inmediatamente al reanudar y al recibir cambio de hora/zona del sistema. No existen timers por tarjeta ni trabajo temporal en background. El scheduler del dispositivo puede retrasar la actualización; el estado siempre se deriva del instante observado, sin prometer alarmas exactas.

Las tarjetas y el contexto de una hoja muestran una línea compacta de estado y fecha/hora local. Próxima usa naranja existente y Vencida el color de error; no se recolorea toda la tarjeta. Completada usa texto discreto; sin fechas no aparece una sección vacía. Los responsables coexisten con esa línea y se siguen resolviendo por sus IDs/flujos.

Hoja → Capa → hoja conserva ambas fechas: mientras hay hijos quedan sin indicador, edición operativa ni urgencia propia; al perder el último hijo vuelven a aplicarse a la hoja pendiente según los triggers existentes. El estado temporal propio no se propaga hacia ancestros; Atención agrega sus señales por separado, como se describe a continuación. Fechas no filtran tareas, no modifican orden manual, drag/reorder ni fórmula de progreso. Mover conserva fechas y responsables porque solo actualiza estructura/posición del mismo ID; editar responsables conserva fechas y editar fechas conserva responsables.

Cobertura dirigida: estados y fronteras con `now` fijo, umbral y extremos Long; persistencia/edición/retirada/validación, idempotencia, progreso/orden/responsables y traslado; hoja → Capa → hoja; migración del schema real v5 en API 24/28 conservando tablas y triggers. Compose verifica selección de fecha/hora, restauración con selector abierto, error/corrección, reloj al reanudar/cambiar hora, presentación sin urgencia en completadas/Capas y edición real con recreación de Activity. Se verifican regresiones de Personas, traslado, borradores y rutas de migración anteriores.

Limitaciones: selector Material con rango de años predeterminado 1900–2100, pendiente dogfooding en pantallas pequeñas, cambios reales de zona/horario y ciclos de foreground. No se afirma validación física ni muerte real de proceso. En el bloque de fechas no se añadieron notificaciones, alarmas, calendario ni recurrencias; el Calendario derivado se implementa en su bloque posterior. El Motor de Atención básico se describe a continuación.

### Motor de Atención básico + propagación + vista Atención — IMPLEMENTADO

`AttentionSnapshot` deriva del `NodeTreeSnapshot` validado y un único `now`; no persiste atención. `AttentionSummary` conserva conteos `upcoming`, `overdue` y `total`, con nivel `NONE`, `UPCOMING` u `OVERDUE`: cualquier vencida domina. Solo las hojas cuyo `TaskTemporal.state` existente devuelve UPCOMING u OVERDUE aportan una unidad. Se conserva su ventana de 24 horas y precedencia: completadas, programadas, activas, sin fechas y fechas latentes de contenedores no aportan señales. Una Capa nunca adquiere un vencimiento propio por esta agregación.

Una cola iterativa procesa hojas y luego padres cuando todos sus hijos están resueltos, sumando cada aporte una vez hasta todos los ancestros y la raíz virtual de cada Proyecto. No hay recursión ni recorridos completos por cada Capa: derivación y orden transversal cuestan O(N + M log M), con memoria O(N + M), donde M son las hojas con atención. Los caminos se resuelven por ID bajo demanda en O(profundidad), sin guardar todas las rutas. `rememberAttention` ejecuta el cálculo en Dispatchers.Default.

Proyectos, tarjetas de Capas y contexto abierto muestran un indicador compacto con conteos de vencidas/próximas. Usa el rojo de error existente si hay vencidas y el naranja existente para próximas; NONE no ocupa espacio. Convive con progreso y responsables, sin teñir tarjetas. Las hojas conservan su indicador temporal existente. Completado/reapertura, movimiento, eliminación y hoja → Capa → hoja regeneran las señales desde las relaciones actuales; fechas y responsables conservan la identidad del Node. La fórmula de progreso y el orden manual no cambian.

El destino funcional «Atención», junto a Personas en el panel existente, muestra referencias a hojas de todos los proyectos, sin copias, movimientos ni escrituras. Ordena vencidas primero, luego próximas, por `dueAt` ascendente y desempata por `createdAt` e ID. Cada fila incluye título, vencimiento, Proyecto + ancestros y sus responsables propios, sin herencia. Las rutas largas se truncan visualmente. Al pulsar se revalida el ID y se abre el Node real usando la reconstrucción de ancestros de `ProjectScreen`; un destino desaparecido cae de forma segura a la raíz del proyecto. Atrás asciende por las Capas y al salir del proyecto vuelve a Atención. Visitar Atención desde una Capa y volver conserva su ubicación; la ruta y el estado guardable de la vista se restauran mediante el mecanismo existente.

Observadores globales de solo lectura de Nodes y asignaciones reutilizan Room **v6**, entidades, tablas y relaciones existentes. Una instantánea global sustituye los observadores de progreso separados por proyecto, usando el mismo progreso derivado. Los flujos actualizan fechas, completado, estructura, nombres de ancestros/proyectos y responsables. Solo hay un reloj de pantalla activo: global para Proyectos/Atención/Calendario, o del proyecto completo cuando este está abierto. Usa el mecanismo temporal existente en foreground, fronteras temporales, reanudación y cambios de hora/zona; no hay timers por tarjeta, alarmas ni trabajo temporal en background.

Validación dirigida: seis pruebas del modelo (reglas, fronteras, orden, conteos, raíces, camino y 10.000 niveles), tres de repositorios (emisiones, traslado, completado/reapertura, cambios estructurales, fechas, nombres y asignaciones) y tres de Compose (proyección, navegación al ID real, ruta guardable, retorno, cambios reactivos y reloj de descendientes ocultos). Pasaron además 15 regresiones dirigidas de navegación, traslado, Personas y reloj temporal. No se afirma prueba física, benchmark de dispositivo ni muerte real del proceso: restauración usa el arnés de estado guardable. La proyección carga todos los Nodes locales, sin paginación; el coste de los caminos visibles depende de su profundidad. El movimiento conserva el alcance existente dentro del mismo proyecto. Prioridad, notificaciones, recurrencias y atención avanzada quedan fuera de esta etapa.

### Notas como propósito de Node + conversión Tarea ↔ Nota — IMPLEMENTADO

```text
Node
├── estructura: hoja / Capa (derivada de hijos)
└── propósito: ACTION / NOTE (persistido)
```

`NodePurpose` distingue ACTION (trabajo) y NOTE (información). Una Nota sigue siendo un Node, con título y descripción de texto plano, ubicación, posición e identidad normales; no hay entidad ni tabla Note. ACTION es el default para creaciones y datos históricos. Room pasa de **v6 a v7**: `NodePurposeMigration6To7` añade únicamente `nodes.purpose TEXT NOT NULL DEFAULT 'ACTION'`. Conserva proyectos, jerarquía, posiciones, fechas, Personas, asociaciones, foreign keys y los siete triggers anteriores. Cuatro triggers adicionales rechazan propósito inválido, Notas completadas/con hijos y destinos NOTE al insertar o trasladar. Repository aplica las mismas restricciones transaccionalmente; no hay migración destructiva.

Solo hojas pueden convertirse. «Convertir en nota» y «Convertir en tarea» usan el mismo ID; actualizan propósito, `isCompleted = false` y `updatedAt`, conservando padre, proyecto, título, descripción, posición, creación, responsables y `startAt`/`dueAt`. ACTION completada → NOTE pierde el completado operativo; NOTE → ACTION vuelve pendiente, sin recuperar un completado histórico. Repetir el propósito actual es un no-op. Fechas quedan latentes en NOTE: no se muestran ni editan operativamente, pero no se borran y vuelven a actuar al convertir a ACTION.

`NodeTreeSnapshot` mantiene su suma iterativa y porcentaje truncado; solo hojas ACTION aportan trabajo. Notes aportan 0/0 y no se cuentan como pendientes. Capas con solo Notes y proyectos sin hojas ACTION quedan NO_WORK. `TaskTemporal.state` y `nextTransition` ignoran hojas NOTE, de modo que el mismo `AttentionSnapshot` las excluye de la vista y señales ancestras sin una segunda política temporal. Los flujos actualizan progreso, Atención, acciones, fechas e iconografía sin reiniciar.

«Nuevo elemento» conserva Tarea por defecto y añade selector compacto Tarea/Nota; el propósito forma parte del borrador guardable y de la comprobación de reintentos idempotentes. Una tarjeta Nota usa un icono discreto de documento, sin checkbox, progreso ni urgencia; conserva vista previa y responsables. Al abrir sigue la navegación existente, permite editar texto, convertir, mover, gestionar responsables y eliminar. «Nuevo elemento» está deshabilitado con explicación: debe convertirse primero en tarea para recibir hijos. El selector de traslado excluye Notes como destinos; la operación también lo valida.

Las Notes permanecen en Disponibles (`isCompleted = false`) y se reordenan entre hermanos de ese grupo junto con tareas pendientes y Capas. No cambia `DragReorder`, sus gestos ni su política de posiciones. Mover conserva propósito, fechas y responsables; eliminar usa el borrado seguro de Nodes, limpia asociaciones y conserva Personas. ACTION que recibe hijos sigue pasando a Capa y al perderlos vuelve a tarea pendiente.

Pruebas dirigidas incluyen migración real v6 en API 24/28 con todos los datos existentes, creación/reintentos por propósito, conversión y normalización, fechas latentes, progreso mixto/NO_WORK, Atención, defensas de hijos/completado, traslado/reorder y borrado con responsables. Compose cubre creación con borrador restaurado, conversión/edición, iconografía, restricción de hijos y actualizaciones de Proyecto/vista Atención. Se mantienen regresiones afectadas de migraciones históricas, Personas, fechas, Atención, borradores, traslado y orden. No se afirma instalación física sobre una APK previa ni validación de drag en dispositivo para esta etapa. Texto plano únicamente: sin Markdown, formato enriquecido, imágenes ni adjuntos. Movimiento entre proyectos sigue fuera del alcance.

### Creación múltiple + numeración + generación temporal finita — IMPLEMENTADO

Un único `NodeBatchGenerator` puro genera una lista finita de `GeneratedNodeSpec` desde `NodeBatchParameters`; UI y persistencia no contienen generadores alternativos. Parámetros: nombre base, cantidad, numeración, número inicial, propósito, descripción, regla temporal y primer vencimiento. `MAX_BATCH_SIZE = 500` está centralizado: cantidad 1..500, nombre no vacío y títulos finales de hasta 100 puntos de código Unicode según `TitleLimits`. Se valida toda la lista antes de escribir, sin truncar títulos. Hermanos con títulos repetidos siguen permitidos. Numeración NONE/PREFIX/SUFFIX («Sin numeración», «Al inicio», «Al final»); número inicial default 1, admite 0..Int.MAX_VALUE y rechaza overflow del último número.

«Crear varios» es una acción secundaria junto a la creación existente del nivel abierto. «Nuevo elemento» conserva su formulario individual. El lote crea hermanos dentro del destino original, o raíces del Proyecto si no hay padre. Se permite ACTION por defecto y NOTE; Notes no ofrecen generación temporal y se crean sin fechas. Comparten descripción opcional y 0..N responsables seleccionados con el `ResponsibleDialog` existente; no se crean Personas desde el generador. Una Nota abierta mantiene bloqueadas ambas entradas de creación.

Reglas NONE/DAILY/WEEKLY/MONTHLY/YEARLY generan solo `dueAt`; `startAt` queda null. Son desplazamientos de calendario sobre la fecha ORIGINAL, no sumas de millis ni incrementos sucesivos sobre una fecha ya ajustada. Diaria conserva hora local y Semanal día de semana/hora. Mensual ajusta al último día válido cuando falta el día objetivo y recupera el día original en meses posteriores (31 ene → 28/29 feb → 31 mar). Anual conserva mes/día/hora y ajusta 29 feb a fin de febrero en años no bisiestos, recuperando 29 en el siguiente bisiesto.

La generación recibe explícitamente la zona local del dispositivo. Calcula componentes de fecha de calendario sin DST y reconstruye cada fecha/hora estrictamente en esa zona, convirtiendo el resultado a epoch millis. Una hora o día local inexistente bloquea TODO el lote con error; se pide elegir otra hora inicial. Ante hora repetida se conserva la política actual de Calendar: offset estándar, sin selector de ocurrencia. El primer instante elegido se conserva exactamente. Fechas calculadas limitadas a años 1–9999; el selector Material mantiene su rango actual 1900–2100. No se persiste una zona ni una regla recurrente.

La preview muestra hasta cuatro líneas: primeros tres, «…» y último si hay más, junto a cantidad total; no compone cientos de tarjetas. Consume la misma lista de especificaciones que recibe `NodeRepository.createBatch`. Generación inválida muestra el error y bloquea Crear lote. `NodeActions` reutiliza `OperationState` para impedir doble envío y mantener formulario/parámetros ante errores. `NodeBatchDraft.Saver` guarda destino, UUID de reintento, nombre, cantidad, numeración, número inicial, propósito, descripción, responsables, regla y primer vencimiento; el selector temporal existente también conserva su etapa y selección. Restaurar no ejecuta escrituras automáticas ni redirige un padre desaparecido a raíz.

`createBatch` valida especificaciones, Proyecto, padre ACTION y Personas dentro de una única transacción para TODOS los Nodes y asociaciones `node_person`. Cualquier fallo revierte también posiciones y normalización de completado del padre. Los nuevos Nodes nacen incompletos, se añaden mediante máximo+1 en orden generado, sin tocar otros hermanos normalmente; solo se compacta el destino si faltan posiciones antes de Int.MAX_VALUE, con la política de orden existente y sin alterar sus fechas históricas. Disponibles/Completadas y drag/reorder conservan sus reglas: el lote se añade al final de Disponibles, aunque Completadas se muestre después.

IDs deterministas derivados del UUID guardable del borrador y del índice permiten reconocer un lote confirmado y reintentar sin duplicarlo, incluso tras reabrir Room o ante llamadas concurrentes. Se exige coincidencia de cantidad, destino, contenido, propósito, fechas y responsables y se rechaza una identidad existente parcialmente. No hay tabla/registro de lotes: la protección reconoce las filas mientras existan; no garantiza exactamente una vez después de borrarlas. Tras crear, cada Node es independiente: no existen edición/borrado del lote como unidad ni creación futura automática.

Los Flows existentes incorporan la lista, responsables, progreso y Atención sin refresh manual: ACTION aporta trabajo y su `dueAt` alimenta `TaskTemporal`/`AttentionSnapshot`; NOTE queda excluida. No cambian sus fórmulas. Room permanece **v7**, con schema, entidades, migraciones y triggers intactos; `PersonDao` solo añade lectura de IDs de asociaciones para comprobar reintentos. Este bloque de lotes no añadió WorkManager, alarmas, notificaciones, Calendario ni recurrencia persistente; el Calendario se implementa después como proyección. No se implementa intervalo personalizado en esta versión.

Validación dirigida: cinco pruebas puras de cantidad/numeración/títulos, propósito, fechas mensuales/anuales sin deriva, calendario local/DST y días inexistentes; tres pruebas de repositorio en API 24/28 (seis ejecuciones) para especificaciones exactas, asociaciones, progreso/Atención, orden, rollback de Nodes/asociaciones, saturación de posiciones y reintentos concurrentes/reapertura; dos pruebas Compose para creación en la Capa actual, Notes, responsables, borrador/selector restaurados, preview y fallo/reintento. Pasaron también 17 regresiones dirigidas de Notes, fechas, Atención, traslado, orden y acceso a creación en 320×480 con texto ampliado. Una prueba de Notes se adapta para desplazarse a la fila lazy antes de comprobarla. Estos resultados no acreditan instalación/prueba física, muerte real de proceso ni rendimiento con 500 elementos y muchos responsables; el borrador mantiene los límites del Bundle existentes.

### Calendario — vista temporal derivada IMPLEMENTADA

El destino **Calendario** se añade al panel transversal existente, junto a Personas y Atención, tanto desde Proyectos como desde una Capa. Es una vista mensual global de solo lectura y local/offline: los Nodes y sus fechas siguen siendo la única fuente de verdad. Room permanece **v7**, sin entidades, tablas, columnas, DAO ni migraciones nuevas.

`CalendarSnapshot` recibe el `NodeTreeSnapshot` global observado y la zona actual del dispositivo. Incluye únicamente hojas ACTION reales con `dueAt != null`, comprobando las relaciones de padre en la instantánea. Excluye Notes (aunque conserven fechas latentes), Capas y ACTION sin vencimiento. Las completadas permanecen visibles; su etiqueta reutiliza `TaskDateIndicator`/`TaskTemporal`, donde COMPLETED conserva prioridad. No cambia ninguna fórmula de progreso, Atención ni clasificación temporal.

`CalendarDates` centraliza epoch millis → zona local → fecha civil. El índice agrupa por `CalendarDay`, nunca por día UTC. Cambiar zona solo reconstruye la representación: no escribe `dueAt`. Los instantes en cambios DST, incluso horas repetidas, se convierten usando el offset correspondiente al instante; dos ocurrencias se ordenan por sus epoch reales. Las etiquetas de fechas civiles se formatean desde mediodía UTC para evitar desplazarlas por huecos de medianoche.

`CalendarMonth` construye la cuadrícula gregoriana con días y huecos necesarios, en semanas completas (normalmente 4–6), incluyendo años bisiestos. Usa el primer día semanal del Locale del dispositivo; los nombres de mes/días y números se localizan y los encabezados siguen el mismo orden. Los controles anterior/siguiente conservan el número de día cuando existe y lo ajustan al último válido si hace falta. La navegación admite años 1–9999. El mes entero es un ítem de una lista lazy desplazable: no exige que toda la cuadrícula quepa en la pantalla.

Hoy se distingue con borde naranja `PathHighlight`; la selección usa `Primary` morado. Un punto naranja indica días con tareas y la semántica anuncia su cantidad. Tocar un día selecciona su lista; **Hoy** vuelve a la fecha local actual. Sin entradas muestra «Sin tareas para este día». Cada fila compacta contiene título, hora local, Proyecto, ancestros, responsables propios y estado temporal existente. No presenta progreso ni controles de creación/completado. El orden es `dueAt`, `createdAt`, ID, independiente de `position`, que nunca se modifica desde Calendario.

Al tocar una fila, `AppRoot` revalida el Node y su Proyecto y entrega el ID real a `ProjectScreen`, que reconstruye ancestros mediante la navegación existente. No hay detalle duplicado. Para una entrada desde Calendario, Atrás del sistema o la flecha superior vuelve directamente al contexto temporal; el patrón habitual de subir Capas y el retorno desde Atención se conservan para sus entradas normales. Visitar Calendario desde una Capa y cerrarlo conserva esa ubicación. Mes/día (el mes se deriva del día seleccionado) y scroll usan estado guardable dentro del proveedor `calendar`; también se guarda el origen de retorno cuando se abre una tarea. Si desaparece su Proyecto se vuelve al Calendario.

Los Flows globales existentes alimentan Nodes/Proyectos y asignaciones. Crear, editar/retirar fechas, completar/reabrir, convertir ACTION ↔ NOTE, pasar hoja ↔ Capa, mover, borrar, renombrar ancestros/Proyectos y cambiar responsables actualizan la vista sin refresh manual. Los lotes con fechas son Nodes normales y aparecen por el mismo flujo; las fechas latentes se conservan al convertir/cambiar estructura.

`rememberCalendar` construye el índice en `Dispatchers.Default`, únicamente al cambiar la instantánea o zona. Agrupar cuesta O(N); ordenar añade Σ O(k log k) por día y la memoria es O(N). Las celdas consultan el mapa por día, sin consultas Room ni observadores por celda. Solo las filas compuestas resuelven rutas iterativas O(profundidad), evitando copiar todos los caminos de árboles profundos. Cambios del reloj no reagrupan el índice.

Sigue activo un solo `rememberTaskScreenNow`: global para Proyectos/Atención/Calendario, o para el proyecto abierto. Reutiliza foreground RESUMED, fronteras `TaskTemporal`, revisión como máximo cada minuto y cambios de hora/zona. Una devolución de llamada actualiza el entorno incluso si el instante observado no cambia. Reanudación y ticks actualizan hoy/estados sin emitir ni escribir Room; no hay timers por celda ni background. El scheduler puede retrasar el cambio visible de día hasta la siguiente revisión.

Cobertura dirigida nueva: cinco pruebas puras (inclusión, orden global determinista, medianoche local, DST, meses/Locale y ruta de 10.000 niveles), dos de repositorios (conversiones, estructura, completado, fechas, traslado, borrados, lotes y Room v7), y tres Compose (ruta real/responsables, retorno/restauración, reactividad y cambio de día/zona con reloj controlado sin escrituras). Pasaron las 10 pruebas nuevas y 23 regresiones directamente afectadas de Atención, navegación, reloj temporal, Personas, traslado y creación múltiple (33 en total); la prueba Compose se repitió tras ampliar Atrás del sistema y selección distinta de hoy. La restauración usa el arnés de estado guardable; no acredita muerte real de proceso ni prueba física.

Límites: vista mensual y lista del día únicamente; carga todos los Nodes locales, sin paginación ni benchmark de dispositivo. Se usa calendario gregoriano con localización del dispositivo. No añade creación contextual, edición de fechas, drag temporal, vista semanal/diaria completa, filtros, búsqueda, eventos externos, recurrencia persistente, alarmas, notificaciones ni sincronización. Pendiente dogfooding en tamaños pequeños, texto ampliado, TalkBack y cambios reales de zona/ciclo de vida.

### Obligaciones — base financiera de ACTION Nodes IMPLEMENTADA

La solicitud de este bloque resuelve la decisión abierta del modelo: **Obligación es una capacidad financiera opcional de una hoja ACTION**. Mantiene el mismo Node y árbol; no existe FinancialNode, CalendarEvent, tabla de obligaciones ni entidad especial de cuota. Una Capa como «Notebook» sigue siendo un contenedor normal de ACTION independientes. El bloque base no calculaba dinero agregado; #19–22 añade la proyección derivada descrita más abajo.

El dominio expone `Node.obligation: Obligation?`, una pieza de valor inmutable con `amountMinor: Long` y `currencyCode: String`. La misma fila `nodes` guarda dos columnas nullable: ambas nulas significan tarea normal; ambas presentes representan la capacidad financiera. No hay booleano persistido redundante. Solo ACTION sin hijos puede operarla. `Obligation` valida importe positivo y moneda con unidades menores definidas.

Room pasa **v7 → v8** mediante `ObligationMigration7To8`: añade `amountMinor INTEGER` y `currencyCode TEXT`, ambos NULL para TODOS los Nodes existentes. Preserva IDs, jerarquía, posición, propósito, completado, fechas, Proyectos, Personas, asociaciones, índices y los once triggers anteriores; añade cuatro defensas financieras. Se exporta `8.json` y se conserva toda la cadena histórica sin migración destructiva ni reconstrucción de tablas.

`Money` centraliza validación ISO con `Currency.getInstance`, `defaultFractionDigits`, parseo y formateo. El importe se guarda en unidades menores Long: CLP 50000 → 50000; USD 10.50 → 1050. No se usa Float/Double para dinero. `BigDecimal` realiza la conversión exacta y `longValueExact` rechaza overflow. Se exige monto > 0 y entrada numérica con separadores del Locale; no se aceptan símbolos monetarios, signos, exponentes, NaN/Infinity, agrupación incorrecta ni más decimales que los de la moneda. El texto de entrada es solo un borrador, nunca la fuente persistida. Las entradas se limitan a 128 caracteres. El Locale de entrada se guarda junto al borrador para interpretar sus separadores consistentemente al restaurar; el formateo visible usa el Locale actual. La moneda siempre queda explícita en la línea visual.

La UI ofrece CLP (default), USD y EUR, más la moneda existente si se edita una futura obligación de otro código válido. La representación no está limitada a esas tres: respeta monedas de cero, dos o tres decimales según Currency. Códigos sin unidades menores definidas no son operativos. Cambiar moneda requiere que el monto introducido sea compatible; no convierte tasas ni mezcla monedas.

«Nuevo elemento» y «Editar elemento» conservan campos existentes y agregan una opción secundaria **Obligación** solo para tareas hoja; al activarla aparecen Monto y Moneda. La creación individual de una obligación permite seleccionar 0..N responsables con `ResponsibleDialog`; el borrador conserva selección y selector abierto. `createNode` valida Personas e inserta las asociaciones existentes en la misma transacción, incluyendo su comparación al reintentar. Errores bloquean Guardar y conservan el formulario. Tarea normal ↔ Obligación actualiza contenido/fechas/capacidad en una transacción, sin recrear el Node: conserva identidad, padre, Proyecto, posición, createdAt, completado y responsables; actualiza updatedAt. Quitar la opción de una obligación existente requiere confirmar **Eliminar datos financieros** al guardar, indicando que se borrarán monto y moneda. Cancelar la confirmación no escribe. No se mantienen finanzas latentes.

Obligación ACTION → NOTE requiere la misma confirmación antes de convertir. Repository exige intención explícita de borrado; el DAO borra ambos campos en la escritura de propósito que ya normaliza completado. Fechas siguen latentes según la política existente de Notes. NOTE → ACTION vuelve a tarea normal pendiente, sin restaurar dinero eliminado. NOTE no muestra campos financieros ni permite activarlos.

Una obligación no puede recibir hijos: creación individual/lote y traslado hacia ella se rechazan en Repository y SQLite, y las entradas de creación están deshabilitadas al abrirla con el mensaje «Convierte esta obligación en una tarea antes de usarla como capa». El selector de traslado excluye obligaciones como destinos. La validación de hojas es transaccional: activar finanzas en una Capa se rechaza y, ante creación concurrente de un hijo, solo puede confirmarse una de las operaciones válidas. Convertir primero en tarea normal habilita la transición hoja → Capa habitual.

Los triggers exigen par monto/moneda, tipo INTEGER y monto > 0, código de tres letras mayúsculas, propósito ACTION y ausencia de hijos; otros dos impiden insertar/mover hijos bajo obligación. La pertenencia real del código a ISO y su cantidad de decimales se validan en dominio/Repository mediante Currency; SQLite garantiza la forma del código sin replicar un catálogo ISO en otra tabla. Las defensas de ciclos, identidad, propósito, completado y relaciones siguen instaladas. SQL externo con un código de forma correcta pero ajeno a Currency no está soportado: la lectura valida el dato y informa error de integridad mediante el mecanismo existente.

Se reutilizan exactamente `isCompleted`, `node_person`, `startAt` y `dueAt`: completar equivale en esta base a obligación satisfecha/pagada; reabrir vuelve a pendiente, conservando dinero. No hay fecha real de pago, pagos parciales ni historial. Responsables son las mismas 0..N Personas, sin payer/debtor/creditor ni herencia. Mover y reorder escriben solo sus campos habituales, preservando monto/moneda; borrar conserva la limpieza de asociaciones.

Calendario y Atención siguen clasificando exclusivamente ACTION/estructura/fechas mediante sus proyecciones actuales y TaskTemporal. Añaden una línea compacta del importe, también en tarjeta y contexto del Node, usando el naranja existente. Completadas permanecen en Calendario y salen de urgencia por la prioridad temporal existente. Progreso sigue contando una unidad por ACTION hoja, con el mismo peso que cualquier tarea; no hay progreso financiero ni porcentaje de dinero.

«Crear varios» permite un lote ACTION de obligaciones con monto y moneda comunes, responsables/numeración y generación temporal existentes. `NodeBatchGenerator` entrega las propiedades financieras en las mismas `GeneratedNodeSpec` de la preview, sin fórmula temporal alternativa. La validación rechaza lotes parcialmente financieros o con monto/moneda distintos entre entradas. Cada entrada es un Node independiente creado ahora; no hay agrupación persistida de cuotas ni generación futura. `createBatch` inserta TODOS los Nodes financieros y asociaciones en la transacción existente o revierte todo, incluidas las normalizaciones. Los reintentos comparan también monto/moneda exactos, rechazando contenido financiero distinto.

Los Flows existentes propagan creación, edición de monto/moneda, fechas, completado/reapertura, responsables, movimiento, reorder, borrados, conversiones y lotes. No se añade refresh manual. `EditorDraft.Saver` y `NodeBatchDraft.Saver` incluyen opción, importe introducido, código y Locale de entrada; conservan los demás campos/IDs. Las confirmaciones se restauran sin ejecutar escrituras y la versión del borrador de lote acepta el formato anterior. No hay persistencia de borradores en Room.

La representación permite futuros cálculos por moneda sobre Nodes existentes y sus relaciones, sin implementarlos: **no sumar monedas distintas**, convertir monedas ni interpretar progreso estructural como proporción monetaria. No se añaden índices monetarios especulativos ni consultas de agregación.

Pruebas dirigidas nuevas: Money con Locale fijo para CLP/USD/EUR y tres decimales, precisión/overflow/errores; repositorio y SQLite en API 24/28 para identidad, conversiones, bloqueos, traslados, reorder, completado, responsables, proyecciones y lotes atómicos/reintentos; migración del schema exportado v7 en API 24/28 conservando filas y triggers; Compose para creación/restauración, retiro confirmado, NOTE, presentación en Calendario/Atención y lote mensual financiero restaurado.

Validación del bloque: 114 ejecuciones dirigidas (15 nuevas y 99 regresiones). Tras ajustar expectativas de versión/cantidad de triggers y el selector modal de Cancelar, se repitieron las clases afectadas con resultado correcto. La restricción final de lote financiero homogéneo también pasó las pruebas de generación, repositorio y UI de lotes.

Límites: sin totales por Capa/Proyecto/período/Persona, filtros ni dashboard financiero, informes PNG, historial/fecha de pagos, parcialidades, intereses, amortización, deudas, cuentas bancarias, presupuestos, tasas externas, recurrencia ni avisos. Estos límites describen el bloque base; los totales y filtros están implementados posteriormente en #19–22 y el informe PNG en #23. No se acredita validación física, muerte real de proceso ni benchmark; las restauraciones usan el arnés de estado guardable existente y sus límites de Bundle.

### 7. Agregaciones de Obligaciones — #19–22 IMPLEMENTADAS

FinancialSnapshot deriva una proyección de solo lectura sobre NodeTreeSnapshot y responsables existentes. Solo hojas ACTION con obligation aportan dinero, comprobando hijos reales. Acumula de abajo hacia arriba sin recursión y expone byNodeId, byProjectId y resumen global de la selección. Cada hoja aporta una vez a sus ancestros y Proyecto, incluidas Subcapas.

Cada moneda tiene total, pendiente y completado separados, junto con cantidades. CLP y USD nunca se mezclan ni convierten. Los importes persistidos siguen siendo Long; los agregados usan BigInteger en unidades menores para evitar overflow. Money.format comparte formato localizado para importes individuales y agregados, incluido cero. Se reutiliza isCompleted: completar satisface todo el importe y reabrir lo vuelve pendiente. Los estados Sin obligaciones, Pendiente, Parcialmente completado y Completado se derivan de cantidades; no representan pagos parciales ni porcentajes monetarios.

Períodos: Este mes (inicial), Mes anterior, Próximo mes y Todo el período. Cada mes usa dueAt en la zona local del dispositivo. Todo incluye Nodes sin vencimiento y completados. El filtro por Persona usa responsables propios: cada coincidencia aporta el importe completo una vez, sin herencia, reparto ni duplicación. Los filtros de distintas Personas pueden solaparse y no deben sumarse. Una Persona eliminada seleccionada conserva su ID y muestra una selección vacía hasta cambiar el filtro.

La vista transversal **Obligaciones** se abre desde el drawer. Presenta selección, resumen por moneda y Nodes reales con importe, completado, vencimiento, Proyecto, ruta y responsables propios. Ordena por vencimiento, creación e ID, dejando sin vencimiento al final; no modifica posiciones. Tocar abre el contexto real; volver regresa directamente a Obligaciones con filtros y desplazamiento guardables. Abrir la vista desde una Capa ordinaria y volver conserva esa ubicación.

Los contextos de Proyecto y Capa muestran totales recursivos de **Todo el período**, sin filtro por Persona, solo si tienen obligaciones. Las hojas conservan su importe individual. La vista transversal muestra los totales globales filtrados.

rememberFinancial calcula fuera del hilo principal por datos, responsables, selección, mes civil y zona. Las emisiones existentes actualizan completado/reapertura, importe/moneda, fecha, traslado, eliminación, propósito y responsables. Se reutiliza el reloj único de pantalla en primer plano para cambios de mes/zona y reanudación; no recalcula cada minuto si las claves no cambian. No hay consultas por fila ni temporizadores financieros adicionales.

Coste: N Nodes, M obligaciones seleccionadas, C monedas y A asociaciones. Recorrido iterativo con combinaciones que copian/ordenan mapas de monedas: cota O(N·C log C + M log M), memoria O(N·C + M + A). Las rutas visibles se resuelven en O(profundidad). Se prueban 10.000 niveles y sumas mayores que Long. Room permanece v8: sin cambios de esquema, DAO, entidades persistidas, triggers ni migraciones.

Pruebas nuevas: cinco de dominio, cuatro ejecuciones de repositorio API 24/28 y tres Compose API 28. Cubren alcance, monedas, estados, períodos/zona, responsables múltiples sin herencia, overflow, lotes, reactividad, navegación y restauración de filtros. El arnés guardable no acredita muerte real del proceso. Pendientes: dogfooding físico, accesibilidad y benchmark. Sin períodos arbitrarios, historial de pagos ni pagos parciales. Este bloque no incluía PNG ni entrega de archivos; #23 los implementa en su misión separada descrita a continuación.

### 8. Informe PNG de Obligaciones — #23 IMPLEMENTADO

La vista Obligaciones ofrece **Generar informe** como acción secundaria. Captura la misma FinancialSnapshot visible, la lista vigente de Proyectos, el nombre seleccionado, Locale y el instante REAL de pulsación (System.currentTimeMillis). ObligationReportData transforma esa instantánea en datos de presentación inmutables: período, Persona opcional, secciones monetarias, counts, obligaciones y fecha de generación. Sus listas se publican sin posibilidad de mutación; no retiene entidades ni consulta Room. No filtra, agrega ni ordena nuevamente. Money.format reutiliza el formato exacto de importes y totales de #19–22, sin mezclar monedas.

La cabecera refleja Este mes/Mes anterior/Próximo mes/Todo con el mes civil de la proyección. Persona mantiene la pertenencia por importe completo sin prorratear. Conserva el orden de FinancialSnapshot.tasks, incluye completadas y sin vencimiento cuando corresponden al filtro. Cada fila muestra título completo con wrap, monto, vencimiento local, estado textual, Proyecto y nombres propios de responsables; sin fotos, avatares ni recorrido completo de ancestros. Vencimientos y generación se formatean con la zona capturada de la proyección. El pie muestra Generado por Arachn0de y fecha/hora de generación; no datos técnicos.

ObligationPngRenderer es independiente de Compose: mide StaticLayout y luego dibuja texto, superficies, separadores y logo sobre Canvas/Bitmap. No captura Activity, ComposeView, LazyColumn ni píxeles del teléfono. Ancho fijo **1440 px**, altura medida hasta **8000 px**, márgenes de 80 px, padding de 32 px y tipografía sans-serif consistente. Los montos de filas se alinean a la derecha y títulos/nombres/importes largos envuelven líneas sin superposición ni truncado. Fondo BackgroundEnd carbón, superficies Surface, texto claro/gris suave, violeta Accent y naranja PathHighlight de la paleta existente; sin colores globales nuevos, gráficos, glow ni degradados añadidos.

Reutiliza exactamente drawable-nodpi/arachn0de_logo.png, idéntico por SHA-256 a design/arachn0de-logo.png. Original y copia permanecen intactos; no hay assets redundantes. Decodifica una versión reducida en memoria y escala conservando aspecto, sin tint; logo de hasta 64 px junto a ARACHN0DE en la esquina superior derecha.

Antes de reservar el Bitmap valida altura y bytes con aritmética Long. El presupuesto es el mínimo de **48 MiB**, un cuarto del heap máximo y la mitad del heap disponible; se vuelve a comprobar inmediatamente antes de crear ARGB_8888. El techo 1440×8000 ocupa aproximadamente 44 MiB de píxeles, dejando margen para layout, logo y compresión. También rechaza más de 1000 filas antes de construir datos y campos mayores que 20.000 caracteres antes de StaticLayout, como límites de trabajo para datos históricos extremos. La altura/memoria suele limitar antes la cantidad de filas. No corta obligaciones silenciosamente: informa que debe reducir período/Persona. Captura OutOfMemoryError y recicla Bitmaps al finalizar o fallar. Estos límites son defensas, no garantía de memoria infinita ni benchmark físico.

ObligationReportFiles genera localmente en cache/obligation-reports, publica el PNG completo mediante archivo .part y rename solo después de compresión/cierre correctos, y elimina staging ante fallos. Filename neutral: arachn0de-obligations-yyyy-MM-dd-HHmmss-SSS-<sufijo aleatorio>.png, sin nombres, títulos, montos ni IDs de entidades. Una nueva generación limpia informes de ese directorio con más de 24 horas; no elimina inmediatamente los recientes para permitir la lectura tras compartir. Android puede limpiar la cache antes y un cierre abrupto puede dejar un huérfano hasta una próxima limpieza. No hay historial de informes ni tabla de exportaciones.

ReportFileProvider, no exportado, expone únicamente el subdirectorio dedicado mediante report_paths.xml y autoridad applicationId.reports. Sharesheet usa ACTION_SEND, image/png, content URI, ClipData y permiso temporal exclusivamente de lectura. Nunca file URI ni permisos generales de almacenamiento. Guardar usa ActivityResultContracts.CreateDocument(image/png), equivalente a ACTION_CREATE_DOCUMENT, con filename sugerido y selección de ubicación por el usuario; copia el PNG completo con buffer de 32 KiB. Cancelar el selector no es un error. No conserva permisos permanentes. Referencias: [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider) y [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).

Construcción de ReportData en Dispatchers.Default; medición, render, compresión y archivos en Dispatchers.IO. La acción y su operación pertenecen a la pantalla, fuera de los ítems lazy: siguen disponibles aunque el listado esté desplazado. La UI muestra estado ocupado y bloquea duplicados durante generación/guardado. Al completar presenta Guardar PNG/Compartir; errores mantienen pantalla/filtros y permiten reintentar. Sin obligaciones, Generar informe queda deshabilitado. Estado guardable conserva únicamente nombres de cache para el diálogo listo y resultado pendiente de SAF, nunca Bitmap ni información financiera en Bundle. Recreación/navegación cancela operaciones ligadas a la composición; no hay worker, regeneración automática ni promesa de completar durante muerte del proceso. Un archivo completo huérfano puede quedar en cache si se pierde su resultado durante cancelación.

Fallos de dimensiones/memoria, compresión, escritura, cache ausente, URI, selector o Sharesheet muestran mensajes comprensibles sin logs financieros. La escritura SAF depende del proveedor externo y no puede garantizar atomicidad: ante fallo/cancelación se intenta borrar el documento incompleto y no se anuncia éxito; si el proveedor rechaza borrarlo puede quedar un parcial que el usuario debe retirar. La app no envía datos a servidores; solamente entrega un archivo cuando el usuario elige guardar/compartir. El destino externo elegido puede tener su propia política de almacenamiento.

Room permanece **v8**, sin esquema, entidades, DAO, triggers, tablas de informes ni migraciones nuevos. Sin PDF, multipágina, CSV, Excel, impresión, gráficos, historial, envío automático ni otras capacidades del roadmap.

Validación dirigida: 20 ejecuciones nuevas (5 ReportData, 4 renderer nativo API 28, 8 archivos/contratos API 24 y 28, 3 Compose API 28) y 14 regresiones de FinancialSnapshot, FinancialRepository, FinancialUi y Money. Comprueban selección/orden/totales/monedas, responsables, inmutabilidad, fechas, vacío, wrapping, límites, PNG decodificable con contenido, logo/aspecto, filenames, MIME, URI y lectura del provider, escritura/errores, generación con listado desplazado, diálogo restaurable, cancelación SAF simulada, filtros y Room v8. Tras corregir dos expectativas de pruebas (retorno Unit para JUnit y categoría no garantizada por CreateDocument), se repitieron las clases afectadas con resultado correcto. Se inspeccionó visualmente un PNG sintético; no se automatizó Sharesheet externo ni se acredita validación física. La suite dirigida final pasó las 34 ejecuciones; después de mantener la acción fuera de los ítems lazy se repitieron las 6 pruebas UI afectadas, también correctas. compileDebugKotlin y git diff --check finalizaron correctamente.

Dogfooding pendiente: CLP y múltiples monedas; los cuatro períodos; Persona y varios responsables; completadas; títulos/nombres largos; informe largo y rechazo por límite; logo y legibilidad; compartir, guardar/cancelar y abrir desde otra app. También recreación con selector SAF abierto, cache eliminada y proveedores de documentos reales.

## PRÓXIMA ETAPA — capacidades previstas, no implementadas

### 9. Tags / Etiquetas — IMPLEMENTADO

Un Node puede tener múltiples etiquetas (Room v10, sección final). **Capa = dónde está el Node; Tag = qué es o con qué se relaciona.** Las etiquetas complementan la jerarquía y no la reemplazan.

### 10. Favoritos

Una tarea o Capa podrá marcarse como Favorita para referencia y acceso rápido desde una futura vista **Favoritos**. Inicialmente no mueve ni duplica el Node, no cambia prioridad y no afecta progreso.

### 11. Menú lateral con destinos reales

El menú lateral recuperará progresivamente destinos cuando existan funcionalidades utilizables: **Proyectos, Atención, Favoritos, Personas, Etiquetas, Configuración y Acerca de**. El panel actual conserva identidad, versión y cierre, y añade los destinos funcionales Personas, Atención y Calendario; no se añadirán destinos vacíos para llenar el menú.

### 12. Acerca de e identidad

La pantalla **Acerca de Arachn0de** está implementada en #33–34 con versión instalada y actualización manual. Incluye Autor (r0ybt), enlaces externos a Instagram, GitHub, Mastodon y Web, y Código fuente por separado. Usa ACTION_VIEW y muestra un error si Android no puede abrir el enlace; no usa WebView. Descripción y licencia/información pertinente siguen pendientes.

### 13. Releases y actualizaciones DE Arachn0de

**#30–32 — IMPLEMENTADOS; v0.2.0 Beta publicada manualmente con APK firmado y SHA256SUMS.txt.** Repositorio oficial verificado mediante Git: `https://github.com/bastideveloper1/Arachn0de.git`. Canal oficial: GitHub Releases de **bastideveloper1/Arachn0de**.

Versionado MAJOR.MINOR.PATCH: PATCH para correcciones, MINOR para funcionalidades compatibles y MAJOR para cambios importantes/incompatibles. `versionName` es humano; `versionCode` es un entero Android estrictamente creciente que nunca se reutiliza ni disminuye. Se prepara `0.2.0 / 2`; el menú usa `BuildConfig.VERSION_NAME`.

Debug conserva su firma habitual y no requiere credenciales Release. `assembleRelease` exige las cuatro credenciales locales `ARACHNODE_KEYSTORE_PATH`, `ARACHNODE_KEYSTORE_PASSWORD`, `ARACHNODE_KEY_ALIAS`, `ARACHNODE_KEY_PASSWORD`, desde entorno o propiedades Gradle del usuario fuera del checkout. La ruta debe ser absoluta, externa y legible; nunca se sustituye por debug key. La clave definitiva no se crea automáticamente, debe conservarse con seguridad y mantener la misma identidad en futuras actualizaciones. No se versionan claves ni secretos. Room permanece v8.

El asset distribuible es el APK **Release firmado**, preparado como `Arachn0de-v0.2.0.apk`, con SHA-256 opcional en `SHA256SUMS.txt`. [RELEASING.md](RELEASING.md) documenta creación manual de clave, build, verificación oficial con apksigner, commit/push, tag del commit exacto y publicación manual. [CHANGELOG.md](CHANGELOG.md) registra capacidades reales. Esta preparación no crea commits, tags ni Releases.

**#33–34 — IMPLEMENTADOS: comprobación manual, descarga/verificación explícita y entrega al instalador Android desde Acerca de.** No hay consulta automática al arrancar, workers ni notificaciones. **ARACHN0DE NUNCA REALIZA UNA ACTUALIZACIÓN SILENCIOSA.** Versión/cambios se muestran antes de Descargar; Instalar es una decisión separada. Android conserva la autoridad final sobre autorización, firma e instalación. La actualización física entre Releases formales sigue pendiente de prueba.

Privacidad: solo metadatos públicos de Releases y, con autorización explícita, checksum/APK oficiales; nunca enviar Projects, Nodes, Notes, Personas, responsables, obligaciones, montos, estadísticas, contenido exportado o información personal. Sin analytics ni telemetría. El núcleo sigue offline-first, sin backend o cuenta obligatoria.

Validación #30–32: `assembleDebug` correcto; `assembleRelease` sin credenciales falla claramente en `validateReleaseSigning`. Build Release con clave temporal externa y credenciales efímeras correcto, incluida lintVital; apksigner verificó la firma y aapt confirmó package, versión 0.2.0/código 2 y ausencia de debuggable. Se calculó SHA-256; APK y clave temporales fueron eliminados. No se creó clave definitiva ni asset para publicación. Sin prueba de instalación física. `git diff --check` correcto y revisión tracked/staged sin secretos detectados.

Se distinguen dos conceptos: **Release DE Arachn0de** actualiza/distribuye la aplicación; **Release DENTRO de un Proyecto** será una futura función para gestionar versiones de proyectos administrados por Arachn0de. Una no implementa ni presupone la otra.

### Secuencia tentativa de implementación

Este orden es una propuesta revisable, no una obligación irreversible. Los primeros doce pasos y el Calendario derivado ya están implementados; releases tiene preparación parcial #30–32. Los demás pasos siguen pendientes:

1. Mover Node a otra Capa — IMPLEMENTADO.
2. Personas — IMPLEMENTADO.
3. Responsables — IMPLEMENTADO.
4. Fechas — IMPLEMENTADO.
5. Atención básica — IMPLEMENTADO.
6. Notas y conversión Tarea ↔ Nota — IMPLEMENTADO.
7. Creación múltiple y numeración — IMPLEMENTADO.
8. Reglas temporales finitas para creación múltiple — IMPLEMENTADO. Motor de recurrencia persistente V1 — IMPLEMENTADO en Room v9, descrito al final.
9. Definir Obligación como capacidad de ACTION hoja — RESUELTO en el bloque financiero.
10. Implementar base de Obligaciones — IMPLEMENTADO.
11. Agregaciones, totales y filtros financieros — IMPLEMENTADO en #19–22.
12. Informe PNG — IMPLEMENTADO en #23.
13. Tags.
14. Favoritos.
15. Menú lateral cuando sus destinos sean reales.
16. Acerca de y redes.
17. Sistema formal de releases: #30–34 IMPLEMENTADOS; actualización física entre Releases pendiente de validar.

El Calendario mensual derivado se implementó después de las reglas temporales finitas, antes del diseño financiero.

## ROADMAP FUTURO — evolución posterior

Se conservan como posibilidades posteriores, sin implementación ni calendario comprometido:

- Prioridad; historial de completado y pagos; fechas planificadas frente a reales.
- Sprint, backlog, objetivos, hitos, puntos/dificultad, Kanban, Gantt derivada y exportación Gantt PNG.
- Releases de proyectos, Change Sets, changelog e integración con Git.
- Plantillas, métricas, progreso histórico, tendencias, velocidad y estimado frente a real.
- Project Path, archivos, código, snippets e issues asociados a archivos.
- Equipos, roles, disponibilidad, Scrum Poker, riesgos, reuniones, agenda, decisiones e historial especializado.
- Usos personales como series, colecciones, cuotas y mediciones sobre el núcleo universal, sin universos de datos separados por cada caso.
- Privacidad avanzada: cifrado local, contraseña maestra, bloqueo y contraseña señuelo.
- Aplicación de escritorio, emparejamiento local Android ↔ Desktop y sincronización local sin nube, incluida LAN.

Desktop y sincronización local constituyen una etapa avanzada/final de esta línea de evolución, no una prioridad inmediata.

## DECISIONES ABIERTAS — resolver antes de implementar

- **Finanzas avanzadas:** alcance recursivo, períodos mensuales y filtros por responsables implementados en #19–22. Períodos arbitrarios, pagos parciales e historial requieren diseño separado; monedas distintas permanecen separadas.
- **Capas explícitamente vacías:** decidir si deben existir en el futuro. Actualmente perder el último hijo convierte el nodo en tarea; esta documentación no cambia esa regla ni elige una alternativa futura.
- **Notas avanzadas:** formato y adjuntos se diseñarán en otra etapa; el propósito NOTE y la conversión de hojas ya están implementados. No se definen Notas contenedoras.
- **Atención avanzada y generación temporal:** la propagación básica y generación temporal finita ya están implementadas; diseñar futuras reglas adicionales; la recurrencia persistente V1 se describe al final.
- **Distribución de la app:** versionado, firma local y procedimiento manual implementados en #30–32; comprobación manual implementada en #33. Descarga/verificación y entrega manual al instalador implementadas en #34, siempre bajo control del usuario y sin actualización silenciosa. Rotación de claves y prueba física requieren trabajo separado.

## Principios de evolución

Se preservan local-first, offline-first, privacidad y ausencia de cuenta/backend obligatorio. `Node` sigue siendo la primitiva estructural con jerarquía de profundidad práctica arbitraria. La información será derivada cuando sea posible, evitando persistir datos calculables innecesariamente.

La UI seguirá separada del dominio, repositorios y persistencia. Room/SQLite es el almacenamiento local actual; cualquier extensión que necesite cambios persistentes se implementará en su propio bloque mediante migraciones seguras, sin romper datos existentes. El roadmap de capacidades todavía pendientes no crea migraciones por sí mismo; los bloques implementados documentan sus propias migraciones.

Las nuevas vistas reutilizarán el mismo dominio. La UI no debe sobrecargarse con conceptos que puedan permanecer implícitos. El siguiente desarrollo ampliará el núcleo del MVP; no lo reemplazará ni implementará anticipadamente todo este roadmap.

---

# 22. Compatibilidad con el futuro

El MVP no debe implementar funciones futuras.

Sin embargo, evitar decisiones arquitectónicas que hagan innecesariamente difícil añadir posteriormente:

- tipos de nodo;
- etiquetas;
- fechas;
- prioridades;
- relaciones entre elementos;
- personas;
- versiones;
- archivos;
- historial;
- sincronización.

No realizar sobreingeniería para necesidades hipotéticas.

El principio es:

**Diseñar un núcleo limpio y extensible, pero implementar solamente lo necesario ahora.**

---

# 23. Instrucciones para agentes de código

Antes de implementar una funcionalidad:

1. leer este documento;
2. inspeccionar el código existente;
3. comprender la arquitectura actual;
4. determinar el cambio mínimo necesario;
5. implementar únicamente el alcance solicitado;
6. no implementar funciones del roadmap por iniciativa propia;
7. mantener compatibilidad con el modelo de datos existente siempre que sea razonable;
8. compilar el proyecto después de cambios relevantes;
9. ejecutar las pruebas relacionadas;
10. informar claramente qué archivos fueron modificados y por qué.

Si una solicitud entra en conflicto con este documento, no asumir silenciosamente una nueva arquitectura.

Se debe señalar el conflicto antes de realizar una modificación estructural importante.

---

# 24. Principio rector

Arachn0de debe comenzar pequeño.

El objetivo de v0.1.0 no es construir toda la visión del producto.

El objetivo es demostrar que su núcleo funciona:

```text
Proyecto
   ↓
Nodo
   ↓
Nodo
   ↓
Nodo
   ↓
Progreso
   ↓
Persistencia local
```

Si este núcleo es sólido, las capacidades posteriores podrán construirse progresivamente sobre él.

## Ajustes finales del MVP tras prueba manual

- El botón inferior **Capas de cebolla** sustituye a **Volver a proyectos** y usa el PNG oficial `iconovercapas.png` sin modificaciones, tintes ni filtros. Se elimina **Mapa de capas** como interfaz independiente.
- El navegador vertical muestra primero **Proyectos/Home**, después la raíz del proyecto y sus ramas expandibles. Permite saltar directamente a cada destino. Solo la ubicación actual lleva punto naranja, tarjeta destacada, semántica de selección y texto **ACTUAL**; la flecha superior sigue subiendo exactamente un nivel.
- Se reutiliza el índice iterativo de la jerarquía (ahora `LayerHierarchyIndex`) y una lista lazy con claves persistentes. Expansión, diálogo y desplazamiento siguen siendo estado restaurable; al abrir se expanden los ancestros de la ubicación actual. No se establece profundidad máxima.
- Los títulos se validan tanto en formularios como en repositorios: proyectos hasta **60** caracteres y elementos hasta **100**. Se cuentan puntos de código Unicode tras quitar espacios de los extremos. Los formularios muestran contador y bloquean guardado fuera del límite; las vistas acotan títulos a dos líneas con ellipsis. Los datos históricos largos no se recortan automáticamente; para editarlos deben ajustarse al límite.
- La descripción opcional del elemento ya existía en modelo, Room, borradores y formularios. Se conserva su creación/edición y persistencia; ahora también se muestra una vista previa en tarjetas y el texto completo al entrar al elemento. Puede quedar vacía.
- **Sin cambio de esquema ni migración en estos ajustes:** Room permanece en versión 4, sin modificar datos confirmados ni introducir guardado automático de borradores.

- La lista de hermanos muestra secciones **Disponibles** y **Completadas**, omitiendo grupos vacíos. El arrastre conserva posiciones dentro del mismo grupo y padre, con validación transaccional; los encabezados tienen claves propias y no son destinos. El detector conserva coordenadas estables mientras la tarjeta se desplaza visualmente y no se reinicia por cambios de callback. Home usa el PNG original suministrado, sin recoloreado.

## Drag y reorder visual de proyectos y nodos

El motor único `DragReorderState` y el detector de long press pertenecen a la lista. Las tarjetas reciben solo el modificador visual y el indicador de arrastre. El dedo se mide en píxeles del viewport; el punto agarrado se conserva y la capa gráfica deriva `translationY = fingerY - grabOffsetY - item.offset` del layout actual, también durante scroll. La identidad del gesto no depende del índice transitorio ni de callbacks de cada tarjeta.

El orden transitorio contiene únicamente proyectos o hermanos del mismo proyecto, padre y grupo de completado. Se intercambia el elemento con un vecino inmediato cuando su centro cruza el centro de ese vecino; se espera que el nuevo orden sea medido antes de otro cruce. Cada intercambio conserva explícitamente índice y offset del viewport con `requestScrollToItem`, evitando que el anclaje por clave de LazyColumn oculte el nuevo primer elemento. Los vecinos utilizan `animateItem` con animación de placement; el elemento agarrado sigue al dedo sin esa animación.

Un loop con `withFrameNanos` calcula tiempo real (limitado a 50 ms tras pausas), usa una zona de 96 dp y una curva cuadrática de hasta 700 dp/s, y ejecuta `scrollBy`. Reevalúa cruces en cada frame y al mover el dedo. En los extremos del grupo se detiene el scroll en esa dirección para no arrastrar la lista hacia otro grupo o controles auxiliares.

Al soltar, el ID ubicado en el índice final dentro del orden original es el target compatible con `reorderTo`. Solo se envía una operación si cambió el índice. El orden visual final permanece hasta la emisión correspondiente de Room; el error existente o un cambio en los miembros del grupo lo descarta. Cancelar, cambiar capa o destruir la composición no persiste y termina el motor. No se modifican repositorios, Room ni dependencias. La fluidez y el agarre durante auto-scroll deben validarse también en teléfono físico.

## Visualización del progreso en proyectos y capas

Las tarjetas de proyectos y capas con trabajo, y la cabecera de la capa abierta, muestran `percentage% · pending de total pendientes` y una barra fina de 4 dp con relleno naranja `PathHighlight` y track `ControlSurface`. Consumen el `NodeProgress` derivado existente; `pending = total - completed`. Las capas reciben `progressById[node.id]` de `NodeTreeSnapshot`, incluyendo todas las hojas de subcapas sin contar los contenedores. Las hojas ACTION y NOTE no muestran progreso; solo ACTION participa en el cálculo. El estado `NO_WORK` muestra solo «Contenedor vacío», sin métricas ni barra. La regla del dominio se conserva: un nodo sin hijos es una hoja ACTION o NOTE, no un tipo persistido de capa vacía.

## #55A — Copiar contexto estructurado de Nodes — IMPLEMENTADO

Capacidad añadida posteriormente, **fuera de las 54 funcionalidades originales**; no modifica su contador ni implementa otras etapas. Antes de iniciar se confirmó la presencia de #23 en este checkout. No modifica el informe PNG, su renderer, proyección ni lógica financiera.

El menú existente de cada tarjeta y las acciones del Node abierto ofrecen **Copiar este elemento** y, solo para contenedores con hijos, **Copiar con descendientes**. El primer alcance conserva únicamente título, descripción y semántica del seleccionado; el segundo recorre su subárbol completo. La extensión a Proyectos se documenta en VISUAL ACTION LANGUAGE; no necesita pantalla nueva.

NodeExportSnapshot.capture parte del NodeTreeSnapshot cargado y utiliza childrenOf para hijos y orden existente: disponibles/completadas, posición y desempates vigentes. DFS iterativo conserva ese orden sin recursión ni consultas Room por Node; la raíz seleccionada tiene profundidad relativa cero. La lista inmutable de ExportEntry contiene solo título, descripción, profundidad relativa, clasificación derivada y completado. La clasificación viene de hijos reales y propósito ACTION/NOTE; no hay campo persistido nuevo ni segunda implementación del árbol. Los cambios posteriores de Room no alteran el contenido capturado.

NodeMarkdownRenderer es puro: no depende de Compose, Android Clipboard ni Room. Capa raíz usa encabezado #; otras capas hasta nivel seis usan encabezados dentro de ítems de listas anidadas, preservando también el retorno entre hermanos de distinta semántica. ACTION hoja produce - [ ] o - [x]. NOTE produce encabezado informativo Nota en raíz o título informativo en lista, nunca checkbox. Una Capa sigue siendo sección incluso al copiar solo ese elemento. No existe tipo persistido de Capa vacía: un Node sin hijos vuelve a hoja ACTION/NOTE según las reglas vigentes.

Más allá de seis niveles, la indentación queda acotada y cada entrada lleva su nivel relativo explícito; el preorden y esos niveles permiten identificar inequívocamente el parentesco sin encabezados mayores que h6 ni salida cuadrática por profundidad. Descripciones no vacías conservan Unicode, emojis, párrafos y líneas, como cuerpo del ítem correspondiente; sin etiquetas, placeholders ni campos vacíos. Se normalizan CRLF/CR, espacios finales y una única nueva línea final. Se escapan delimitadores Markdown y comienzos estructurales de texto literal para que el contenido no se convierta accidentalmente en otra sección.

NodeCopyActions pertenece a la pantalla y ejecuta snapshot/render en Dispatchers.Default; desplazar una tarjeta no cancela la operación. La entrega final usa ContextClipboard con ClipboardManager y ClipData.newPlainText, únicamente tras terminar todo el texto. Bloquea duplicados; cancelación no reemplaza el clipboard. Feedback breve: Contexto copiado mediante Toast hasta API 32, confirmación del sistema desde API 33 para evitar duplicados. Errores usan Toast sin cerrar la pantalla ni alterar navegación. Referencia: [Clipboard Android](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste).

Límite de 200.000 unidades UTF-16: alrededor de 400 KiB de texto serializado, dejando margen frente al presupuesto compartido de transacciones Binder. Una comprobación conservadora limita el contenido durante captura y otra incluye escape/indentación/formato durante render. Si excede el límite, se rechaza completo con mensaje de copiar una Capa menor; nunca se entrega un árbol truncado. También se manejan memoria insuficiente y fallo de Clipboard. El límite es de texto, no de niveles fijos; tamaño, memoria y transacciones simultáneas siguen siendo límites prácticos. Coste proporcional a Nodes exportados y texto generado, más la ordenación por grupos que ya proporciona childrenOf; memoria proporcional a Nodes/texto y frontera DFS.

Excluye IDs/UUID, timestamps, posiciones numéricas persistidas, paths, metadatos de Room, responsables/avatares, fechas, Attention y datos financieros. Una obligación se copia como ACTION común sin monto/moneda. No usa red, logs de contenido, permisos nuevos, archivos, FileProvider, SAF ni Sharesheet de texto. El usuario decide dónde pega después. Clipboard conserva el comportamiento y políticas del sistema.

Snapshot independiente de Markdown y Clipboard, apto para una futura representación gráfica sin añadirla ahora. Sin PNG estructural, archivo .md, importación, formatos adicionales ni conexión a IA. Room permanece v8: no cambia esquema, entidades, DAO, triggers ni migraciones. Las operaciones pertenecen a la composición y no se reenvían al recrear la Activity.

Dogfooding pendiente: copiar tarea pendiente/completada, Nota y Capa desde tarjeta y contexto abierto; comparar ambos alcances; pegar en editor/ChatGPT/GitHub; comprobar hermanos, descripciones, Unicode y árboles profundos; rechazo por límite sin reemplazar un clipboard previo; feedback en Android anterior/posterior a 13. Los niveles explícitos profundos priorizan parentesco legible sobre apariencia de headings.

Validación dirigida de #55A: 68 casos únicos (9 ejecuciones nuevas y 59 regresiones de NodeInvariant, NodePurpose, NotesUi, NodeOrder, NodeOrderUi y Navigation). Las nuevas pruebas cubren ambos alcances, clasificación, raíz relativa, orden, descripciones, exclusión de metadatos/dinero, Unicode, inmutabilidad, determinismo, 3000 niveles, 4000 hermanos, límite y entrega exacta al Clipboard en API 24/33. Compose API 28 verifica menús/contexto abierto, ausencia de opción redundante en Note, feedback, ausencia de escrituras y Room v8. Las regresiones pasaron; las pruebas UI nuevas se ajustaron para desplazar tarjetas y procesar el Looper de Robolectric, después de hacer explícita la entrega/feedback en Dispatchers.Main. Todas las clases nuevas quedaron correctas; no se repitió una auditoría general. Compilación Kotlin final y git diff --check correctos. Sin validación física ni commit/push.

## #33 — Comprobación manual de actualizaciones — IMPLEMENTADO

El menú lateral de Proyectos y de un Proyecto/Capa ofrece **Acerca de**. Pantalla sencilla con versión instalada de BuildConfig.VERSION_NAME y **Buscar actualizaciones**. Volver/Atrás conserva el destino previo mediante los estados guardables existentes. Abrir/restaurar la pantalla no inicia una consulta. La comprobación pertenece a su composición: salir/recrear cancela el resultado y nunca reenvía la solicitud automáticamente; al volver se presenta Idle salvo que #34 recupere localmente un APK verificado.

Cadena: AboutScreen → UpdateActions (Idle, Checking, UpToDate, Available, Error) → UpdateRepository → ReleaseSource/GitHubReleaseSource. La UI no conoce HTTP. Checking bloquea duplicados; error ofrece reintento; Available muestra instalada/disponible, título, Beta y notas como texto legible desplazable, sin WebView ni enlaces/HTML ejecutables. No añade un botón de descarga deshabilitado.

Usa HttpsURLConnection nativo, sin dependencias nuevas. Solo permiso INTERNET. GET anónimo HTTPS a `https://api.github.com/repos/bastideveloper1/Arachn0de/releases?per_page=100&page=N`, no `/latest` que excluye prereleases. Incluye Releases públicas estables y prereleases/Beta, ignora drafts y entradas inválidas; consulta páginas construidas localmente, sin seguir redirecciones ni URLs externas de paginación. Timeouts de conexión/lectura de 10 segundos, máximo 2 MiB por página y 10 páginas; si excede el límite o falla cualquier página, muestra Error en lugar de afirmar actualización completa. Cancela entre páginas/lecturas; una lectura bloqueante puede tardar hasta el timeout antes de liberar la conexión. Siempre desconecta. No consulta ni descarga assets.

SemanticVersion compara MAJOR/MINOR/PATCH numéricamente con BigInteger y precedencia SemVer de sufijos prerelease: numéricos por valor, numéricos antes de texto, versión estable por encima de prerelease de la misma base; build metadata no altera precedencia. Rechaza formato incorrecto, ceros iniciales inválidos y tags mayores de 200 caracteres. Mayor versión semántica válida prevalece sobre fecha/orden de publicación. El indicador GitHub prerelease no transforma `v0.2.0` en un sufijo: la Beta actual usa ese tag sin sufijo y sigue siendo una candidata válida.

Una candidata exige tag `v` + SemVer, draft=false, indicador prerelease booleano y exactamente un asset subido, de tamaño positivo, llamado `Arachn0de-<tag>.apk` (por ejemplo Arachn0de-v0.2.0.apk). La URL debe ser HTTPS en github.com, ruta exacta de este repositorio/tag/nombre, sin credenciales, puerto explícito, query ni fragmento. No acepta un APK arbitrario. Releases sin ese APK se ignoran; lista vacía/sin candidatas superiores significa UpToDate. JSON inválido o HTTP no-200 significa Error. Campos opcionales de título/body se normalizan; entradas inesperadas no causan crash. Límites de 100 Releases/página, 100 assets/Release, título 500 y notas 200.000 caracteres.

GitHubRelease conserva tag, versión, título, body, prerelease, assets oficiales validados, APK elegido y SHA256SUMS.txt cuando existe. Su ausencia no impide comprobar versión; #34 exige ese archivo y verificación de hash e identidad antes de ofrecer Instalar. No se confía solo en nombre/URL.

Privacidad concreta: salen solicitudes GET al repositorio oficial con parámetros de paginación y cabeceras fijas Accept, versión de API y User-Agent genérico. El servidor observa IP y metadatos normales de conexión HTTPS. No se envía ni siquiera la versión instalada: comparación local. Nunca se accede a Room desde el comprobador ni se envían Projects, Nodes, Notes, Personas, responsables, obligaciones, montos, estadísticas, identificadores de contenido, exportaciones ni información personal. Sin tokens, credenciales, analytics, telemetría o logs de contenido. Núcleo offline-first intacto; Room v8 sin cambios. Referencias: [API Releases GitHub](https://docs.github.com/en/rest/releases/releases), [red Android](https://developer.android.com/develop/connectivity/network-ops/connecting).

Validación #33: 13 pruebas de selección/parseo/SemVer/estado y 3 Compose API 28, sin GitHub real; cubren misma/superior/inferior, 0.2.10 frente a 0.2.9, precedencia prerelease y metadata, drafts, Beta, tags/campos inválidos, vacío, orden, APK oficial/ausente/duplicado/URL ajena, checksum, JSON/red, reintento, Checking/duplicados/cancelación, conservación del modelo, consulta manual y retorno desde Acerca de. Las 16 pasaron en la repetición final junto con assembleDebug. El montaje inicial de dos tests UI se corrigió para usar el contenido de la Activity existente; el fallo interno inicial de lint con la representación de SemVer se evitó simplificando esa clase, sin desactivar detectores.

`test assembleDebug lintDebug --continue` ejecutó 270 pruebas: 265 correctas y 5 fallidas preexistentes. Dos ScrollRestorationTest (dashboard/mapa), dos LargeListsTest (drag/hoja) y narrowDashboardAndDrawerExposeNoNonfunctionalFeatures de MvpReadinessTest reproducen el mismo fallo en una copia aislada de HEAD 1836f89 sin #33 (15 casos dirigidos, 5 fallos). La prueba de Atención falló de forma intermitente en la primera suite; pasó en la copia original y en la suite final. No se modificaron esos tests ni su comportamiento de producto.

lintDebug ejecutado: 1 error WrongConstant en ObligationPngRenderer.kt:68, 19 warnings y 3 hints; idéntico resultado en HEAD original sin #33. El renderer y las reglas de lint permanecen intactos. assembleDebug y git diff --check correctos; versión 0.2.0/código 2, configuración Gradle de firma y Room v8 intactos. No se acredita prueba física ni consulta real desde un dispositivo; la validación nueva es determinista. Sin commit, push, tags o edición de Releases.

## #34 — Descarga segura y actualización manual — IMPLEMENTADO

AboutScreen conserva #33. Available añade **Descargar actualización**; solo su pulsación inicia las conexiones de assets. UpdateActions coordina Checking, Available, Downloading, Verifying, Ready y Error, bloqueo de duplicados y estado esperando Android. Buscar no descarga. No existe instalación automática al terminar descarga: Ready ofrece **Instalar actualización**. Errores permiten consultar de nuevo o reintentar la descarga, con mensajes comprensibles para checksum/firma incompatibles y para red/espacio. Atrás/salir/recrear cancela la operación de composición, sin servicio/worker ni reenvío automático. Cancelación se comprueba entre lecturas; una llamada de red bloqueante puede tardar hasta el timeout antes de liberar la conexión.

Cadena: UI → UpdateActions → UpdateRepository para selección #33 / UpdateDownloads–UpdateDownloadStore para descarga, integridad y cache → AndroidUpdateInstaller para Intents oficiales. La UI solo conecta ActivityResultLaunchers; no implementa HTTP, hashing, FileProvider ni comprobación de paquetes. Sin nuevas dependencias ni cambios Gradle, versión o firma Release.

GitHubAssetFetcher inicia exclusivamente las dos URLs validadas del mismo GitHubRelease seleccionado: APK esperado y SHA256SUMS.txt. Revalida ruta/repositorio/tag/nombre, pertenencia y unicidad en assets. HTTPS, GET anónimos y User-Agent fijo; sin tokens. GitHub redirige assets a CDN: se permiten como máximo cinco saltos controlados, solo github.com con URL original, release-assets.githubusercontent.com y objects.githubusercontent.com, sin HTTP, credenciales, puertos explícitos o fragmentos. Las queries temporales firmadas del CDN provienen del redirect de GitHub: no se registran ni persisten. Se comprobó con HEAD del asset oficial v0.2.0 que redirige a release-assets.githubusercontent.com, sin descargar/modificar el APK. Timeouts 10 s de conexión/lectura, stream de 32 KiB, tamaño exacto de metadatos y Content-Length cuando existe. Rechaza HTTP no-200, vacío, truncado, sobrelongitud y límites de 256 MiB para APK/64 KiB para checksum. Sin permisos generales de almacenamiento.

UpdateChecksum exige líneas sha256sum de 64 dígitos hex, separador texto/binario y filename simple exacto; admite mayúsculas y CRLF. Rechaza líneas malformadas, entrada ausente o duplicada para el APK. SHA-256 se calcula por streaming y se compara con MessageDigest.isEqual. Solo tras coincidir se inspecciona el APK mediante APIs públicas de PackageManager: GET_SIGNATURES en API 24–27 y GET_SIGNING_CERTIFICATES/apkContentsSigners desde 28. AndroidApkValidator exige packageName propio, versionName exacto del tag sin v, versionCode mayor que el instalado, SemVer superior, minSdk compatible y APK no debuggable. Los conjuntos no vacíos de SHA-256 de certificados actuales deben coincidir exactamente con la instalación propia. No se incluye keystore, contraseña, certificado privado ni pin inventado: la app Release instalada es el ancla de identidad. Debug no puede aceptar la Release oficial con otra firma. No se admite rotación de claves en este bloque; requiere diseño separado. Esta inspección anticipada no sustituye la validación criptográfica/final del instalador Android.

Almacenamiento: únicamente cache/updates. Descargas .part y recibo current.json están fuera de la ruta compartible; el APK recibe nombre UUID neutral en updates/verified solo después de hash e identidad, mediante rename. Fallo/cancelación elimina staging y no publica un parcial. Un mutex compartido serializa descarga/recuperación incluso durante recreación. El recibo privado guarda solo metadatos públicos de Release, filename y hash; permite recuperar localmente Ready tras volver de Settings/recrear, sin consulta ni descarga automática. Recuperar y cada pulsación de Instalar recalculan hash e identidad; archivo perdido, expirado o alterado se invalida. Android puede purgar la cache; en ese caso se vuelve a comprobar/descargar explícitamente.

Limpieza local al abrir Acerca de y al descargar: elimina parciales huérfanos y APK/recibo de más de 24 h o timestamps futuros. APK anteriores recientes se conservan hasta ese plazo para no cortar una lectura del instalador; no hay acumulación indefinida durante uso ni limpieza en background. Si la app no vuelve a abrir este flujo, la eliminación depende de la siguiente limpieza o de Android. El único directorio afectado es updates; nunca Room, datos del usuario, avatares ni informes PNG.

UpdateFileProvider separado, no exportado, autoridad applicationId.updates; update_paths.xml expone solo cache/updates/verified. El provider de #23 y sus rutas permanecen intactos. AndroidUpdateInstaller construye ACTION_INSTALL_PACKAGE con content URI, MIME APK, ClipData, permiso temporal solo de lectura y retorno de resultado; selecciona un handler del sistema mediante MATCH_SYSTEM_ONLY, con consulta de visibilidad limitada a ese Intent. Sin file://, comandos, root, instalación privilegiada ni permisos de escritura. Resultado de Activity no se presenta como prueba de actualización instalada; cancelación permite repetir y un handler ausente muestra error.

Único permiso adicional: REQUEST_INSTALL_PACKAGES. API 26+ comprueba canRequestPackageInstalls al pulsar Instalar; si falta autorización, abre ACTION_MANAGE_UNKNOWN_APP_SOURCES para el paquete propio. Al volver informa que debe pulsar Instalar otra vez: no instala automáticamente por conceder permiso. API 24–25 deja el control de fuentes desconocidas al instalador del sistema. El archivo cache recuperable se revalida en el siguiente intento. **Arachn0de NO instala actualizaciones silenciosamente.**

Privacidad #34: solo conexiones a GitHub/CDN oficial para metadatos públicos, SHA256SUMS.txt y APK. Salen GET, cabeceras fijas y metadatos normales de conexión (IP); no contenido de Arachn0de, versión instalada, Projects, Nodes, Notes, Personas, responsables, obligaciones, base de datos, estadísticas ni identificadores de contenido. Sin analytics, telemetría, cuentas, backend ni logs de URLs firmadas o contenido. Room v8 permanece intacto. Referencias: [assets GitHub](https://docs.github.com/en/rest/releases/assets), [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager), [autorización de fuentes](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES).

Validación #34: 14 casos nuevos de UpdateDownloadTest en API 24/28 (28 ejecuciones) y 1 Compose nuevo; junto a las 16 regresiones #33 suman **45 ejecuciones correctas**. Cubren assets APK/checksum, formato SHA (texto/binario, mayúsculas/CRLF, múltiples entradas, ausencia/duplicado/malformado/traversal), hash coincidente/no coincidente, descarga vacía/parcial/sobrelongitud/fallida/cancelada, publicación posterior a identidad, recuperación local, cache alterada/expirada y limpieza aislada, paquete/versionCode/versionName/minSdk/debug/signer equivocados, APIs públicas de certificados mediante PackageManager simulado, redirects inseguros, estados/reintentos/duplicados, instalación imposible antes de verificar, URI/read grant y handler de sistema, fuente desconocida y UI de descarga explícita. No se descargan assets reales en unit tests; las firmas/paquetes del contrato Android usan fixtures de metadatos públicos simulados, no una actualización física.

Se ejecutó `test assembleDebug lintDebug --continue` dos veces: primero **299 casos, 293 correctos y 6 fallidos**, después **299 casos, 292 correctos y 7 fallidos**. Ambas conservan los cinco fallos probados en baseline (#33: ScrollRestorationTest ×2, LargeListsTest ×2, MvpReadinessTest). La primera añadió FinancialUiTest al intentar desplazar una fila antes de que la proyección filtrada estuviera disponible; pasó al repetir dirigida y también en el baseline original. La segunda añadió la prueba intermitente de Atención ya observada en #33 y ReportMemoryException en longTextWrapsWithinPanelsAndHeightIncludesAllRows del renderer PNG, dependiente del presupuesto de heap disponible en la suite. La repetición dirigida final de updater, FinancialUiTest, AttentionUiTest y ObligationPngRendererTest pasó **55 casos** sin modificar estos bloques. Estos resultados no acreditan una suite general verde ni reproducen el fallo de memoria del PNG en baseline; queda registrado como fallo sensible a recursos observado en la suite completa.

assembleDebug correcto. lintDebug sigue fallando únicamente por WrongConstant en ObligationPngRenderer.kt:68, con 19 warnings y 3 hints del baseline. Dos advertencias nuevas de la frontera Android (guardia API 26 y uso KTX de Uri) se corrigieron sin suprimir lint ni modificar el renderer. git diff --check correcto. Gradle/signingConfig, versión 0.2.0/código 2, Room v8 e informes #23 intactos. No se tocó el keystore ni se creó/publicó una Release, tag, commit o push.

Pendiente antes de validar una distribución: prueba física completa en API 24–25 y 26+, denegar/conceder origen, volver de Settings (incluida recreación), cancelación/error del instalador, cache purgada, red interrumpida/espacio insuficiente y actualización real con APK de versión/código superiores firmado con la misma clave. #33/#34 son cambios locales posteriores al tag publicado v0.2.0; su APK publicado no incorpora estos bloques. Publicar una siguiente versión formal y probar la actualización son misiones separadas; esta intervención no incrementa versiones ni sustituye assets. Rotación de claves sigue fuera del alcance.

## Backup y restauración completa — formato v1

Recuperación manual del **estado funcional completo** desde Acerca de → Backup y restauración, con botones Crear backup / Restaurar backup. No es exportación de un proyecto. Todo el código de esta función es local; no usa red, cuentas, nube ni telemetría. La exclusión de backup/transferencia administrados por Android permanece intacta. SAF usa ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT con EXTRA_LOCAL_ONLY, sin permisos amplios de almacenamiento ni permisos URI persistentes; el usuario elige el destino. El comportamiento final del proveedor de documentos pertenece a Android/proveedor.

Inventario comprobado en Room v8, sin cambios de entidades, schema ni migraciones:

| Tabla / archivo | Campos incluidos |
| --- | --- |
| `projects` | `id`, `name`, `description`, `position`, `createdAt`, `updatedAt` |
| `nodes` | `id`, `projectId`, `parentId`, `title`, `description`, `isCompleted`, `position`, `createdAt`, `updatedAt`, `startAt`, `dueAt`, `purpose`, `amountMinor`, `currencyCode` |
| `persons` | `id`, `name`, `avatarFile` como referencia portátil |
| `node_person` | `nodeId`, `personId` (many-to-many) |
| `files/avatars/*.png` referenciados | Bytes PNG completos; referencias compartidas se conservan |

No hay otros almacenes funcionales persistentes en este checkout. Se excluyen índices/triggers/metadatos internos de Room (los crea la app), progreso y proyecciones derivados, estado de navegación/formularios del Bundle, cachés, APK/recibos de actualización, informes regenerables, temporales, avatares sin referencias y credenciales de firma. No se recortan títulos históricos ni se renumeran posiciones al exportar/restaurar; las normalizaciones de orden de las operaciones habituales siguen siendo las existentes.

Cadena: UI `BackupSection` → `BackupActions` → `BackupRepository` → `BackupDao`/DAOs actuales y `BackupAvatarFiles`. `BackupData` representa el conjunto lógico; `BackupJson` hace el mapeo explícito de campos; `BackupContainer` encapsula el payload; `BackupDocuments` solo entrega/lee streams del SAF. El repositorio pertenece al Application; un mutex serializa snapshots/restauraciones/recuperación de sus archivos. IO y serialización corren fuera del hilo UI. La UI no consulta/escribe Room directamente.

### Formato `.arachnode`

Nombre sugerido `Arachn0de-Backup-YYYY-MM-DD.arachnode`, usando fecha local del dispositivo. Contenedor binario de tamaño acotado, sin ZIP ni copia SQLite:

1. Magic: diez bytes ASCII `ARACHNODE\n` (el último byte es LF).
2. `formatVersion`: Int32 big-endian, valor **1**.
3. `encoding`: Int32 big-endian, valor **0** (JSON plano UTF-8).
4. Long64 big-endian con longitud exacta del payload.
5. 32 bytes SHA-256 calculados sobre **cabecera anterior + payload**.
6. Payload JSON UTF-8: `dataVersion = 1`, `appVersion` creadora, `createdAt` epoch millis, arrays `projects`, `nodes`, `persons`, `assignments`, `avatars`. Cada avatar contiene nombre relativo y PNG codificado en Base64 canónico.

Formato del contenedor y versión de datos son independientes de la versión de Room. v1 rechaza versiones/codificaciones desconocidas; una futura versión podrá añadir un adaptador de datos explícito antes de validar/restaurar. Importes y fechas son enteros Long exactos, nunca Double. El parser rechaza campos duplicados, ausentes/desconocidos, tipos incorrectos, overflow, UTF-8 inválido, profundidad excesiva y contenido sobrante. No se interpreta ninguna ruta absoluta ni se extraen archivos externos.

SHA-256 detecta daño accidental, truncado o contenido agregado, incluyendo modificación de cabecera. **No ofrece autenticidad ni confidencialidad**: alguien con acceso puede leer o modificar/recalcular un archivo. v1 no tiene cifrado, contraseña, YubiKey ni claves ligadas al dispositivo. Límites explícitos: payload de **16 MiB**, hasta **100.000 registros** sumados entre las cuatro tablas, avatar de **1 MiB** y hasta **8 MiB** de PNG en total; dimensiones 1..512 px. También se acota el texto previo a serializar a 8 MiB. Superar los límites rechaza la operación sin exportación/restauración parcial. v1 trabaja en memoria con límites; no promete volumen ilimitado.

### Consistencia e integridad

El snapshot lee todas las tablas y copia los PNG referenciados **dentro de una sola transacción Room**. No normaliza ni modifica datos. Un avatar referenciado ausente/dañado impide generar el backup, en lugar de perderlo silenciosamente. Tras validar el snapshot, genera un archivo privado en `cache/backups`, escribe/cierra/sincroniza y publica mediante rename únicamente cuando está completo. El selector de destino se abre después. Fallo/cancelación de creación elimina staging; los archivos privados completos pendientes caducan al volver a esta sección tras 24 h.

Antes de confirmar restauración se verifica magic/versiones/tamaño exacto/hash, JSON, identidades únicas, proyectos/padres existentes y del mismo proyecto, ausencia de ciclos mediante recorrido iterativo, propósito/completado válidos, fechas, dinero/monedas con `Obligation`, pares monto/moneda, prohibición de hijos en Notes/obligaciones, relaciones many-to-many únicas y referencias exactas de avatares. PNG exige firma, estructura de chunks, CRC, IEND final, dimensiones y decodificación correcta. Un archivo inválido no abre la confirmación y no escribe datos.

### Restauración atómica y archivos

La confirmación explica que **se reemplazan todos los datos actuales**, con recuentos y opción Cancelar. Recomienda cancelar y usar el mismo Crear backup para conservar primero el estado actual; no hay un segundo sistema de backup. No se restaura automáticamente al seleccionar/restaurar estado UI.

1. Revalidar todos los datos antes de cualquier reemplazo.
2. Preparar los PNG con **nombres UUID nuevos**. Nunca sobrescribir los avatares actuales. Un diario privado durable `files/backup-restore-journal.json` registra los nuevos nombres antes de escribirlos; todos los archivos y directorios se sincronizan antes de que Room pueda referenciarlos.
3. En una transacción Room, registrar también los avatares actuales en el diario; borrar asociaciones, desconectar padres de todos los nodos antes de borrarlos para evitar cascadas recursivas profundas, borrar las cuatro tablas y reinsertar Proyectos/Personas, Nodes en orden padre→hijo y asociaciones. Mantener todos los campos/IDs/UUID/fechas/posiciones y triggers. Solo remapear los nombres privados de avatar, conservando sus bytes y referencias compartidas.
4. Ante excepción/cancelación previa al commit, Room revierte todas las filas; los nombres anteriores nunca se sobrescribieron. Tras commit o rollback, limpiar **solo** archivos del diario sin referencias en la base confirmada y retirar el diario. Si la limpieza falla, no se comunica como restauración fallida después de un commit: se reintenta al abrir la sección o iniciar otra restauración. No se borran los avatares de borradores de Personas ajenos al diario.

Un cierre del proceso conserva el commit/rollback SQLite y deja archivos preparados antes de referenciarlos. Al volver a la sección, el diario permite decidir por referencias confirmadas qué archivos conservar/eliminar, tanto antes como después del commit. Base + archivos forman una operación lógica mediante preparación inmutable/commit/recolección; no existe una transacción filesystem+SQLite nativa. Pérdida física del almacenamiento/corrupción del dispositivo queda fuera de esta garantía.

Los observadores Room reciben los datos confirmados. Tras éxito, la raíz Compose cambia su generación, descarta navegación/borradores/scroll de la sesión anterior y vuelve a Proyectos; un aviso confirma la restauración. No se reinstala ni se cierra/reabre la base. La recreación cancela operaciones ligadas a la composición: no serializa datos del backup en Bundle ni repite el reemplazo; si se pierde la confirmación hay que seleccionar nuevamente. El nombre temporal de una exportación con selector abierto sí puede recuperarse y se valida antes de guardar.

### Límites y evolución

La escritura al proveedor SAF **no puede ser transaccional**: si falla/cancela se intenta borrar el documento y nunca se anuncia éxito. Si el proveedor impide el borrado puede quedar un parcial que el usuario debe eliminar; sus longitud/hash no lo convierten en un backup válido. EXTRA_LOCAL_ONLY solicita destinos locales; la app no controla proveedores que incumplan el contrato. No hay historial, backup automático, merge, recuperación selectiva, compatibilidad con formatos futuros, compresión ni cifrado en v1. Faltan pruebas físicas con proveedores SAF reales, poco espacio, interrupción del proceso/dispositivo y fsync real en Android; Robolectric prueba filesystem/SQLite del host, con un adaptador JVM para fsync de directorios porque ShadowLinux API 28 no permite abrirlos.

Para añadir protección futura se mantiene la frontera **datos lógicos → serialización → transformación del payload → contenedor → almacenamiento Android**. Un encoding nuevo podrá transportar cifrado autenticado estándar y sus parámetros/versiones. Habrá que diseñar y probar derivación de contraseña, envolturas de clave para varias YubiKeys autorizadas, autenticación del contenedor, UX de desbloqueo y migración/compatibilidad; no atarlo exclusivamente a Android Keystore ni a una clave del teléfono. Los repositorios/snapshot/restauración no necesitan una nueva semántica para ello. No se implementa criptografía propia ni esos componentes ahora.

### Validación de Backup/Restore

Pruebas nuevas: `BackupFormatTest` (7 casos × API 24/28 = 14), `BackupRepositoryTest` (10 × API 24/28 = 20), `BackupDocumentsTest` (3 × API 24/28 = 6) y `BackupUiTest` (5 Compose, cuatro API 28 y uno API 24): **45 ejecuciones correctas**. Cubren round-trip de todos los campos, Long.MAX_VALUE en dinero, UUID, jerarquía/orden/estados/Notes/fechas, Personas y many-to-many, avatares compartidos, Estado A → backup → Estado B → restauración exacta de A, vacío, corrupción/truncado/versión/codificación desconocida, JSON/tipos/referencias/ciclos inválidos, PNG/CRC, rollback SQLite inyectado después de borrar las tablas, fallo de sincronización de staging, recuperación de diario, snapshot consistente ante escrituras concurrentes en varias tablas, jerarquía de 1100 niveles, SAF/copia/error de proveedor y confirmación/cancelación/retorno al dashboard. Un fallo en el callback UI después del commit no se informa como rollback de datos.

Primero se ejecutó el módulo nuevo. Los ajustes iniciales corrigieron el adaptador de fsync en Robolectric API 28 y una expectativa del contrato SAF, sin debilitar la sincronización de Android. La prueba UI detectó un Toast fuera del Looper; su entrega se pasó explícitamente al Handler principal y las esperas sincronizan la cola Android. La validación dirigida final con NodePersistence, NodeInvariant, PersonRepository, ProjectPersistence, FinancialRepository, NodeBatchRepository y UpdateUi pasó **103 ejecuciones** (45 nuevas + 58 regresiones), junto a `assembleDebug`. Tras acotar la acumulación de PNG durante el snapshot se repitieron las 20 pruebas del repositorio y `assembleDebug`, correctos.

Después se ejecutó **una sola vez** toda la suite unitaria Debug: `:app:testDebugUnitTest`, **348 casos, 343 correctos, 5 fallidos**, sin errores ni omitidos. Los 45 casos nuevos pasaron también en esa suite. Los cinco fallos coinciden con los antecedentes documentados de #33/#34: `ScrollRestorationTest.dashboardRetainsScrollAcrossProjectVisitAndRecreation`, `ScrollRestorationTest.mapRetainsScrollWhileClosedAndAcrossRecreation`, `LargeListsTest.largeTaskLayerScrollsWithoutNavigatingIntoLeaf`, `LargeListsTest.projectListLongPressDragReordersByStableId`, `MvpReadinessTest.narrowDashboardAndDrawerExposeNoNonfunctionalFeatures`. No se arreglaron ni se ejecutó una segunda suite general. No aparecieron fallos nuevos observados fuera de esos cinco. Esta intervención no volvió a ejecutar una suite baseline sin cambios; la clasificación histórica usa la evidencia ya registrada en el documento.

`assembleDebug` correcto; `git diff --check` correcto. No se ejecutó lint completo. Versión `0.2.1 / 3`, Room v8, dependencias, manifiesto, firma y reglas Android de exclusión de backup intactos. Sin commit, push, tag, APK Release ni publicación.


## Motor de recurrencia V1 — Room v9

Cadena: `NodeDialog`/`RecurrenceManager` → `NodeActions`/`OperationState` → `RecurrenceRepository` → Room. `RecurrenceSchedule` calcula fechas sin UI, CalendarSnapshot ni Batch. **Crear varios** mantiene su comportamiento: genera inmediatamente un conjunto finito de Nodes. Una recurrencia persiste una regla y solo materializa vencimientos que ya corresponden; una regla con inicio futuro puede existir sin ningún Node.

### Persistencia y migración

| Tabla nueva | Contenido e invariantes |
| --- | --- |
| `recurrence_rules` | ID estable; plantilla de título/descripcion, proyecto/padre, amountMinor/currencyCode opcionales; startDay, frecuencia, intervalo, endDay opcional; nextIndex; estado; zoneId; dueMinute; startOffsetMillis opcional. Índice por estado. |
| `recurrence_occurrences` | Recibo de materialización `(ruleId, day)` como PK; nodeId único. FK RESTRICT a regla; deliberadamente sin FK a Node para conservar recibos al eliminar ocurrencias, subárboles o proyectos. |
| `recurrence_person` | Responsables de plantilla, PK `(ruleId, personId)`; FK a regla y Persona e índice personId. Eliminar Persona elimina su selección futura, igual que sus asignaciones actuales. |

`RecurrenceMigration8To9` crea únicamente estas tablas/índices; no modifica ninguna fila, columna ni trigger anterior. Se registra junto a todas las rutas históricas en `Arachn0deDatabase.create`. Se exporta `9.json`; no se usa migración destructiva. La prueba de migración abre un esquema v8 exportado con jerarquía, tarea completada financiera, Nota, Persona/avatar y asociación; compara todos los campos, claves foráneas y los 15 triggers en API 24 y 28.

Las referencias de destino de reglas no tienen cascada: una regla sobrevive al borrado de su destino y conserva recibos históricos. Al comprobar un destino ausente/no válido, el motor la pone en PAUSED sin avanzar el cursor. El gestor global de Proyectos permite reasignar proyecto/capa, reanudar o finalizar, incluso sin Nodes ni proyectos originales. Si se borra un proyecto, sus Nodes se eliminan por la operación existente; los recibos conservados impiden recrearlos.

### Calendario determinista

Frecuencias DAILY, WEEKLY, MONTHLY y YEARLY; intervalo entero 1..10000 como defensa de configuración. Inicio y fin son fechas civiles inclusivas, representadas por días epoch UTC; las ocurrencias se calculan siempre desde inicio + índice × intervalo. MONTHLY ajusta al último día del mes sin arrastrar el ajuste: 31 enero → 28/29 febrero → 31 marzo. YEARLY conserva el ancla 29 febrero: 28 febrero en años comunes, 29 en años bisiestos.

La zona local se captura al crear la regla y queda persistida; cambiar la zona del dispositivo no cambia las fechas futuras de la regla. Se conserva una hora civil de vencimiento por regla. Calendar normaliza una hora inexistente por DST hacia adelante; una hora repetida utiliza la resolución estándar de GregorianCalendar. No hay selección de offset. Se prueba el salto 02:30 → 03:30 y la recuperación de 02:30 al día siguiente. El formulario acepta AAAA-MM-DD; no impone horizonte final al cálculo del motor. Una regla sin endDay continúa activa sin un número máximo de repeticiones ni límite de años de negocio, sujeta a representación numérica/almacenamiento del dispositivo.

El inicio de la regla fija las fechas de cada vencimiento. El campo Vence del formulario fija la hora; si no se elige, utiliza 00:00. Si también se elige Inicio de tarea, guarda la diferencia en milisegundos hasta Vence y la aplica a cada snapshot. Inicio de tarea sin Vence se rechaza para evitar descartarlo silenciosamente. Una tarea recurrente sin obligación se materializa exactamente por el mismo motor.

### Materialización, atrasos y unicidad

MainActivity comprueba en onResume. El trabajo pertenece a una coroutine del ciclo de vida y se cancela en onPause/destrucción. Crear regla y cambiar su estado también comprueban vencimientos. No se añade red, backend, WorkManager, alarmas ni notificaciones; no hay garantía de generación mientras la aplicación está cerrada.

Cada transacción procesa hasta 256 fechas debidas, inserta Node/assignments/recibo y avanza nextIndex de forma atómica. Por comprobación se ejecutan hasta 16 bloques (4096 fechas); si quedan atrasos devuelve pendiente, conserva el cursor y continúa en otra pasada con yield mientras la app está en primer plano. Pausar/editar/finalizar drena primero los atrasos ACTIVE, también cediendo entre pasadas. Estos presupuestos limitan trabajo por pasada, **no** la vida de la regla ni el total recuperable. Aritmética exacta, enums, intervalos, fechas/zona y cursores se validan; datos inválidos producen un fallo controlado y no un bucle no progresivo. Los reintentos continúan desde los bloques ya confirmados; el bloque fallido revierte por completo.

Una app cerrada durante días/meses recupera todas las fechas vencidas mientras la regla siguió ACTIVE. No adelanta Nodes futuros. La identidad es regla + fecha civil; PK del recibo, nodeId UUID determinista y UNIQUE nodeId garantizan idempotencia, incluso entre ejecuciones concurrentes. Node y recibo pertenecen a la misma transacción; no hay estado confirmado de Node generado sin recibo. Borrar/completar/modificar/mover una ocurrencia no altera la regla. Borrar el Node deja el recibo durable; ejecutar nuevamente el motor no lo recrea.

### Estados e historia

- ACTIVE: genera fechas debidas y recupera atrasos. Cuando nextIndex supera endDay pasa automáticamente a FINISHED; incluye el vencimiento de la fecha final.
- PAUSED: conserva configuración, cursor, historial y recibos; no genera. Pausar primero materializa deuda ACTIVE anterior a la pulsación.
- PAUSED → ACTIVE: mantiene el ancla original y calcula sin recorrer todos los periodos pausados el primer índice con fecha >= fecha civil de reanudación, nunca menor que el cursor ya confirmado. Se omiten los periodos anteriores a ese día; si hoy es un día programado todavía no materializado, se genera cuando llegue su hora, o inmediatamente si ya pasó. Si excede fin, termina.
- FINISHED: conserva regla/historial y no genera; Repository impide volver a ACTIVE/PAUSED o editar plantilla. Para comenzar otra recurrencia se crea otra regla.

Editar plantilla drena primero los vencimientos ACTIVE pendientes con los datos anteriores; solo después actualiza título, descripción, importe/moneda, responsables y destino para fechas futuras aún no materializadas. No ejecuta UPDATE sobre Nodes históricos. Cada Node nuevo comienza ACTION pendiente con responsables propios, dueAt y startAt opcional; no copia completado, posición anterior, hijos ni historial. El formulario de edición confirma retirar datos financieros futuros. V1 mantiene inmutables frecuencia/ancla/intervalo/fin/zona/hora de una regla creada; crear otra regla permite una programación diferente.

### UI, Finance, Calendar y Backup

Nuevo elemento → Tarea ofrece Ninguna/Diaria/Semanal/Mensual/Anual, intervalo, inicio y fin opcional (vacío = sin fecha final). Conserva parámetros/ID/responsables al recrear el formulario. El gestor Recurrencias está disponible en Proyectos y dentro del proyecto. Cada tarjeta materializada muestra ↻ Recurrencia y abre su regla, aunque el Node se haya movido. El gestor permite editar plantilla/destino/responsables, pausar/reanudar y finalizar con confirmación irreversible; explica que la historia no cambia y que se liquidan atrasos activos antes de cambiar la regla.

Finance sigue leyendo las obligaciones de Nodes y `node_person`; importar CLP u otra moneda no introduce un segundo sistema financiero ni cambia el significado de isCompleted. Calendar y Atención consumen los mismos Nodes fechados mediante sus proyecciones existentes; el motor no depende de la vista mensual. No se implementan filtros nuevos de Calendar.

Backup escribe **dataVersion 2** dentro del contenedor `.arachnode` **formatVersion 1 / encoding 0** existente: conserva firma, SHA-256 y límites del contenedor. Añade arrays explícitos recurrenceRules, recurrenceOccurrences y recurrenceAssignments. El snapshot los lee en la misma transacción que el resto. La validación comprueba enums/configuración, unicidad, referencias de regla/Persona, fechas programadas, cursor posterior a los recibos y UUID determinista. Un recibo sin Node es válido por eliminación deliberada. Destinos ausentes se permiten por la política de preservación y pausa. Los registros nuevos cuentan dentro del presupuesto total de 100000; el texto de plantillas cuenta en el límite previo de serialización.

Restore borra recibos/asociaciones/reglas antes de reemplazar las tablas antiguas, y reinserta reglas/asociaciones/recibos después de proyectos/personas/Nodes, **en la misma transacción**. Conserva todos sus IDs, estados, cursores y snapshots. Rollback inyectado comprueba que se recuperen también las tablas de recurrencia. Restaurar y ejecutar el motor no duplica ocurrencias ni revive Nodes eliminados; una regla ACTIVE restaurada recupera únicamente deuda posterior a su cursor.

Backups de v0.2.2 **dataVersion 1** siguen siendo restaurables: el adaptador exige exactamente sus campos anteriores y añade colecciones de recurrencia vacías. Una restauración v1 reemplaza también las recurrencias actuales por ese estado vacío. Versiones futuras desconocidas se rechazan antes de tocar datos. Una app antigua no puede leer dataVersion 2; la compatibilidad garantizada es leer v1 en la app nueva, no downgrade.

Limitaciones pendientes: dogfooding físico de UI/DST/cancelación por background/almacenamiento bajo; listas de reglas/destinos cargadas sin paginación; programación de reglas ya creadas inmutable en V1; el motor comprueba al reanudar/crear/cambiar estado, no continuamente al permanecer abierto durante días. No se añade benchmark ni generación en segundo plano. Si una configuración persistida está corrupta, se informa un fallo general y se reintenta al siguiente resume; no se repara ni descarta silenciosamente. Se conservan versión de app 0.2.2/código 4 y configuración de firma; sin Release, tag, commit ni push.

### Archivos de esta intervención

Inventario de cambios locales de recurrencia (las pruebas existentes solo actualizan la expectativa de Room 8→9 o la versión futura rechazada del backup):

- `ARCHITECTURE.md`
- `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/9.json`
- `app/src/main/java/com/r0ybt/arachn0de/MainActivity.kt`
- `app/src/main/java/com/r0ybt/arachn0de/backup/BackupData.kt`
- `app/src/main/java/com/r0ybt/arachn0de/backup/BackupJson.kt`
- `app/src/main/java/com/r0ybt/arachn0de/backup/BackupRepository.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/Arachn0deDatabase.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/RecurrenceEntity.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/RecurrenceMigration8To9.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/repository/NodeRepository.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/repository/RecurrenceRepository.kt`
- `app/src/main/java/com/r0ybt/arachn0de/domain/model/RecurrenceSchedule.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/AppRoot.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/BackupSection.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/EditDialogs.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/NodeComponents.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ProjectScreen.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ProjectsScreen.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/RecurrenceUi.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/state/EditorDraft.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/state/NodeActions.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/BackupFormatTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/BackupRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/RecurrenceBackupTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/AttentionRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/CalendarRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/FinancialRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodeBatchRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodeMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodePurposeTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/ObligationMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/ObligationRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/PersonRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/RecurrenceMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/RecurrenceRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/TaskDatesRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/domain/RecurrenceScheduleTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/export/NodeCopyUiTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/report/ObligationReportUiTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/ui/RecurrenceUiTest.kt`

### Validación del motor de recurrencia

Se añadieron **39 métodos de prueba / 63 ejecuciones**: RecurrenceScheduleTest (12 JVM), RecurrenceRepositoryTest (18 × API 24/28 = 36), RecurrenceMigrationTest (1 × API 24/28 = 2), RecurrenceBackupTest (5 × API 24/28 = 10) y RecurrenceUiTest (3 Compose API 28). Todos pasaron en ejecución específica y en la suite completa. Cubren las cuatro frecuencias, intervalos, ancla 31/29 febrero, horizonte indefinido, fin inclusivo, estados/pausa/reanudación/irreversibilidad, recuperación activa, hora/DST/zona, snapshots anteriores frente a cambios futuros, importes/responsables/destino, tareas/obligaciones, proyecciones Finance/Calendar, idempotencia/concurrencia, borrado con recibo durable/reapertura, rollback de Node+recibo+cursor, destino eliminado, reintento de creación, migración v8, backup v2/v1, restore+catch-up sin duplicados, validación de corrupción y rollback de restore.

La ejecución dirigida conjunta de Nodes/dominio/Finance/Calendar/Backup y pantallas relacionadas pasó **285 pruebas**, junto a assembleDebug. Después de añadir manejo de errores/reintento de cargas se repitieron recurrencia, BackupUi y DraftRestoration, con resultado correcto y assembleDebug correcto. La expectativa de versión de las pruebas existentes se actualizó de Room 8 a 9; posiciones históricas con valor 8 permanecieron intactas. BackupFormatTest ahora utiliza dataVersion 3 como versión futura rechazada.

Se ejecutó **una sola vez** la suite completa `:app:testDebugUnitTest assembleDebug --continue --offline`: **411 casos, 404 correctos, 7 fallidos**, sin errores/omitidos. Los **63 nuevos pasaron**. Cinco fallos coinciden con los antecedentes documentados: ScrollRestorationTest.dashboardRetainsScrollAcrossProjectVisitAndRecreation, ScrollRestorationTest.mapRetainsScrollWhileClosedAndAcrossRecreation, LargeListsTest.largeTaskLayerScrollsWithoutNavigatingIntoLeaf, LargeListsTest.projectListLongPressDragReordersByStableId, MvpReadinessTest.narrowDashboardAndDrawerExposeNoNonfunctionalFeatures. La clasificación histórica usa la evidencia existente en este documento, sin nueva suite baseline.

Los otros dos fallaron con **ReportMemoryException** por el presupuesto de heap disponible del renderer PNG sin cambios: ObligationPngRendererTest.longTextWrapsWithinPanelsAndHeightIncludesAllRows (ya observado/documentado en #34) y ObligationPngRendererTest.measuresAndRendersRealNonemptyPngWithSeparateCurrenciesAndLogo (sin baseline propio para ese caso). Se repitió únicamente ObligationPngRendererTest de forma aislada: **4/4 correctos**, sin cambios al renderer ni a sus pruebas; evidencia de sensibilidad a recursos en la suite, no prueba de una suite general verde ni demostración baseline de ambos casos. No se repararon esos bloques ni se repitió la suite general. Informe completo preservado en `/tmp/arachnode-recurrence-full-suite/index.html`; XML en `/tmp/arachnode-recurrence-full-results`.

assembleDebug final correcto (también terminó en la ejecución general cuyo task de tests falló); git diff --check correcto. No se ejecutó lint completo ni Release. VersionName/versionCode permanecen 0.2.2/4. No se realizó commit, push, tag ni publicación. Pendiente validación física y benchmark de recuperación con volúmenes extremos.

## Filtros universales y Calendar Hoy / Semana / Mes

`NodeFilter` es una pieza de dominio reutilizable: recibe rango opcional, ID de Persona opcional y CompletionFilter (ALL/PENDING/COMPLETED), combinados mediante **AND**. Filtra sobre Nodes y un índice de IDs de responsables; no consulta Room ni escribe preferencias. Cada vista decide sus Nodes elegibles y su campo temporal: por defecto usa dueAt y admite una función de selección de instante para futuros consumidores. Solo Calendar utiliza esta abstracción en este sprint; Atención, Finance, reportes y exportaciones conservan su lógica actual.

`TemporalRange` representa `[startInclusive, endExclusive)` en epoch millis. `TemporalRanges` centraliza días civiles, semanas por primer día del Locale, meses reales, desplazamientos y los ocho presets: Hoy, Mañana, Esta semana, Próxima semana, Este mes, Mes anterior, Próximo mes y Todo (rango null). Los límites son medianoches locales calculadas individualmente, no sumas de 24 h/7×24 h: los días DST de 23/25 horas se conservan. Una búsqueda acotada alrededor de la medianoche calcula el primer instante que entra en la fecha civil, incluyendo ambos offsets si se repite la medianoche; una fecha civil omitida tiene un rango vacío; las etiquetas civiles mantienen el mediodía UTC del Calendar existente. Cambiar zona solo recalcula la proyección/rangos; nunca modifica fechas persistidas.

Se preserva exactamente la elegibilidad de `CalendarSnapshot`: **hojas ACTION reales con dueAt**. Las Notes, contenedores y tareas solo con startAt/sin dueAt siguen excluidas, incluso con Todo. Una tarea con startAt y dueAt aparece exclusivamente en el día de vencimiento; no se expande por el intervalo entre ambas fechas. Completadas permanecen visibles salvo filtro Pendientes. `FilteredCalendar` filtra el índice existente sin crear eventos ni otra entidad de Calendar.

La UI ofrece Hoy | Semana | Mes y un botón compacto Filtros. Hoy vuelve al día actual; Semana/Mes conservan la fecha civil seleccionada al cambiar de vista. Semana agrupa los siete días con nombres localizados y permite anterior/siguiente; Mes conserva cuadrícula, selección de día, punto/conteo y lista del día. Los puntos y conteos reflejan los filtros. Actual vuelve al día/semana/mes vigente manteniendo Persona/Estado. Los presets adicionales están dentro del diálogo temporal; Todo muestra una agenda global agrupada por fecha, siempre de ACTION con vencimiento. El mes continúa mostrando los Nodes del día seleccionado, con el resto accesible en su cuadrícula.

Persona Todas equivale a ausencia de filtro. Una Persona específica exige asignación propia por ID, sin herencia ni inclusión de Nodes no asignados; varias Personas no duplican el Node. Si la Persona seleccionada desaparece, se mantiene el ID, se indica Persona ausente y se muestra vacío hasta limpiar/cambiar el filtro. Estado reutiliza únicamente isCompleted. Los filtros activos se resumen en naranja y se pueden limpiar Persona/Estado juntos conservando el período. Vacío y carga tienen mensajes separados.

`CalendarFilterState.Saver` guarda vista, fecha, preset, Persona, Estado y apertura del diálogo; el scroll y la navegación real a Nodes conservan el proveedor calendar existente. Presets relativos se recalculan con la fecha local observada por el reloj existente; navegar/seleccionar manualmente fija una fecha civil. No se persisten preferencias en Room. La restauración automatizada usa el arnés Compose; no acredita muerte física del proceso.

AppRoot reutiliza los Flows globales de Nodes/proyectos/asignaciones y observa Personas también al abrir Calendar desde una capa. No hay consultas por fila ni consultas nuevas de repositorio/DAO. El filtrado/orden de la proyección corre en Dispatchers.Default, con cancelación al cambiar claves; los cambios visuales no vuelven a leer toda la base. El trabajo cuesta O(N + K log K) y memoria O(N + A + K) para N ACTION fechados, A asociaciones y K coincidencias; agrupación de días recordada, filas lazy y rutas resueltas solo para filas compuestas. Siguen pendientes paginación y benchmark físico.

Obligaciones reutilizan Node.obligation y muestran cada moneda por separado; no se agregan ni convierten monedas. Las ocurrencias recurrentes materializadas participan como Nodes normales. El filtro nunca recibe reglas abstractas: una regla y su ocurrencia no producen dos eventos. No se modifica el motor de recurrencia, Room v9, migraciones, backup ni versión 0.2.2/código 4.

Archivos de este sprint: nuevos `domain/model/NodeFilters.kt`, `ui/state/CalendarFilterState.kt`, `ui/CalendarFiltersDialog.kt`; modificados `ui/CalendarScreen.kt`, `ui/AppRoot.kt` y este documento. Pruebas nuevas `NodeFiltersTest`, `CalendarFilterStateTest`, `CalendarFiltersUiTest`, `CalendarFiltersRepositoryTest`; CalendarUiTest adapta únicamente el selector del retorno Actual, conservando las pruebas de navegación/restauración mensual existentes. El schema 9.json sin seguimiento ya estaba presente antes de este sprint y se conserva.

Validación de filtros: **32 métodos nuevos / 34 ejecuciones correctas** (NodeFiltersTest 20 JVM, CalendarFilterStateTest 4 JVM, CalendarFiltersUiTest 6 API 28, CalendarFiltersRepositoryTest 2 × API 24/28). Cubren los ocho rangos, fronteras exactas, mes/año/febrero bisiesto, zonas y DST de 23/25 h, medianoche repetida y fecha civil omitida, Persona/Estado/AND, Notes/startAt excluidos según Calendar, monedas preservadas, 10000 Nodes, navegación/restauración, filtros activos/limpieza/vacío, Persona eliminada y recurrencia materializada sin evento abstracto duplicado.

La ejecución dirigida final de Calendar/Temporal/Personas/Finance/Recurrence y las pruebas nuevas pasó **151/151**, sin errores ni omitidas, junto a `assembleDebug`. `git diff --check` correcto. No se ejecutó la suite completa: no se modificaron consultas/DAOs/repositorios compartidos ni persistencia; no se investigaron fallos históricos ajenos. Sin lint completo, cambios de versión, commit, push, tag o Release. Pendientes dogfooding físico en pantallas pequeñas/TalkBack y benchmark; las preferencias se conservan en estado guardable de sesión, sin persistencia permanente.


## Etiquetas y filtros por ámbito — Room v10

`Tag(id, name, normalizedName)` es una entidad reutilizable independiente. `TagNames`
quita espacios exteriores, comprime espacios repetidos y rechaza nombres vacíos;
la clave usa minúsculas con `Locale.ROOT`. Se conserva el nombre legible inicial.
Crear un nombre equivalente devuelve la etiqueta existente; renombrar hacia una
clave ocupada se rechaza. No se eliminan ni fusionan etiquetas automáticamente.

Room v10 añade `tags` con índice UNIQUE de `normalizedName`, `node_tag` y
`recurrence_tag` con claves compuestas, índices de ambos extremos y FKs CASCADE.
La migración explícita 9→10 solo crea estas tablas/índices: conserva todos los
campos anteriores, recibos de recurrencia y los quince triggers de invariantes.
Los esquemas exportados y todas las migraciones anteriores permanecen disponibles.

La relación Node↔Tag es many-to-many y directa, sin herencia. ACTION, NOTE,
obligaciones, capas y ocurrencias reales pueden etiquetarse. Quitar una asociación
no elimina la etiqueta. Eliminar un Node quita sus asociaciones; eliminar una
etiqueta global quita únicamente sus asociaciones (también de plantillas).
La gestión global confirma la eliminación e informa usos en nodos y plantillas;
las etiquetas sin uso siguen visibles y se incluyen en backups.

Los formularios individuales y de lote permiten búsqueda y creación inline,
selección múltiple y retirada de etiquetas. Los borradores guardan IDs en su
Saver, incluidos los lotes y las plantillas. Las tarjetas muestran dos chips y
`+N`; nombres largos se acotan. El selector usa LazyColumn y búsqueda, sin cargar
un control visible por cada etiqueta fuera del diálogo.

`NodeFilter.tagId` añade un único criterio opcional combinado por AND con persona,
estado y rango temporal. Calendar filtra su índice ACTION existente, sin notas,
capas ni reglas abstractas, y mantiene counts, orden y navegación del Node real.
Los filtros de proyecto y capa se guardan por ámbito durante la sesión y recreación.
`ScopedNodeFilter` recorre iterativamente los descendientes del ámbito, conserva
el orden de hermanos y retiene ancestros necesarios mediante una pasada inversa.
Las filas no coincidentes se rotulan **Contexto**; no cuentan como coincidencias.
La raíz de proyecto no mezcla otros proyectos y una capa no muestra ramas ajenas.
Limpiar vuelve inmediatamente a la vista ordinaria con sus controles de orden.
No se reconstruye un árbol filtrado para calcular progreso: se conserva el snapshot
original. Las proyecciones se preparan en Dispatchers.Default solo si hay filtros.

`TagDao.observe` usa relaciones Room en consultas agrupadas y una transacción,
observando etiquetas y ambos tipos de asociaciones; no hay consulta por tarjeta.
`TagState` contiene índices por Node/regla y por ID de etiqueta. Crear y actualizar
Node, crear Batch y guardar plantilla incluyen validación/asociaciones dentro de
la transacción existente. Los reintentos estables comprueban también las etiquetas.
Al materializar recurrencia se copian las etiquetas de la plantilla; editarla
primero procesa deuda activa con la plantilla anterior y solo afecta fechas futuras.
Las etiquetas de ocurrencias anteriores permanecen independientes y los recibos
siguen evitando recrear ocurrencias borradas.

Backup lógico v3 añade `tags`, `nodeTags`, `recurrenceTags`; el contenedor sigue v1.
Se validan claves normalizadas únicas, referencias, duplicados y límites antes de
restaurar. La restauración transaccional conserva IDs, nombres, relaciones y
etiquetas sin uso. Los datos lógicos v1/v2 se leen con etiquetas vacías y sustituyen
las etiquetas presentes al restaurarse. Los límites de texto/registros incluyen
las etiquetas y sus relaciones. No cambia versionName/versionCode ni la publicación.

Validación de etiquetas: 200 pruebas dirigidas pasaron, incluidas 23 nuevas.
`assembleDebug` y `git diff --check` pasaron. La suite completa se ejecutó una sola
vez: 468 pruebas, 459 pasaron y 9 fallaron. Cinco fallos corresponden a los
históricos de ScrollRestorationTest (2), LargeListsTest (2) y MvpReadinessTest (1).
Cuatro regresiones nuevas de visibilidad (NodeOrderUiTest, 3; NotesUiTest, 1)
se corrigieron moviendo el acceso a filtros al encabezado, sin una fila extra.
La verificación posterior dirigida pasó 39 pruebas sin fallos, incluyendo
las cuatro regresiones y las pruebas de etiquetas, Calendar, Batch y Recurrence UI.
No se repitió la suite completa ni se ejecutó lint completo. No hubo errores
de memoria/entorno en la ejecución completa. Queda dogfooding en dispositivo
físico de diálogos, teclado y navegación con grandes cantidades de etiquetas.


## Historial de eventos V1 — Room v11

`NodeEvent(id, nodeId, type, occurredAt)` conserva eventos inmutables de entidades
existentes. Los tipos V1 son `CREATED`, `COMPLETED` y `REOPENED`. `occurredAt` es
UTC epoch milliseconds del reloj de la operación; la UI lo convierte a fecha/hora
local. No se guarda texto temporal, actor, estadísticas ni otros tipos de evento.
El reloj civil puede retroceder si cambia el reloj del dispositivo: no se inventan
instantes ni se ajustan artificialmente a una secuencia monotónica.

`Node.isCompleted` sigue siendo la fuente del estado actual. El historial explica
sus transiciones y no sustituye el progreso derivado. `NodeRepository.setCompleted`
lee el estado persistido, comprueba ACTION/hoja y escribe estado y evento dentro
de la misma transacción Room. Solicitar el estado actual es un éxito sin escritura,
sin cambiar `updatedAt` y sin evento. Un fallo al insertar el evento revierte el
estado; un fallo al actualizar el Node no deja evento. Las transacciones serializan
solicitudes concurrentes del mismo estado. `toggleCompleted` reutiliza la misma
operación para callers existentes; dos toggles deliberados son dos transiciones.

La UI ahora envía el estado deseado (`setCompleted`) y conserva el bloqueo síncrono
de `OperationState.busy`; no invierte otra vez el estado real en un reintento o tap
con snapshot visual antiguo. Los puntos operativos son el checkbox de tarjeta y
Completar/Reabrir del detalle en Proyecto/Capa. Attention, Calendar y Obligaciones
navegan al Node real y usan esas mismas acciones; no escriben completado por otra
ruta. No se registran eventos desde collectors, recomposición o presentación.

La revisión de todas las escrituras encontró dos reinicios implícitos adicionales:
convertir una ACTION completada en NOTE y añadir/mover el primer hijo a una ACTION
completada. Antes de perder su capacidad de hoja se reabre mediante la misma
operación atómica; conserva su historia anterior. No se registra un evento de
conversión o traslado: únicamente la transición real true→false. Las fechas del
padre se conservan como antes al volverse capa; el evento sí usa el instante real.
Los triggers de defensa estructural continúan instalados. NOTE/capas existentes
rechazan completar/reabrir, incluso si se pide false→false. Perder el último hijo
no crea una reapertura porque la capa ya estaba pendiente. El historial no es
un trigger global de SQL: los writes funcionales usan Repository; los DAOs de
snapshot se reservan para migración/restore y los tests de defensa de SQLite.

La creación normal registra exactamente un CREATED con el mismo instante que
`Node.createdAt`, dentro de la transacción de Node, responsables y etiquetas.
Su ID estable se deriva del ID de Node; un reintento confirmado no vuelve a insertar.
Batch añade un CREATED por Node en la transacción completa del lote. Recurrence
materializa por `NodeRepository.createNode`: Node, CREATED, relaciones, recibo y
cursor se confirman o revierten juntos. Un vencimiento recuperado recibe la fecha
real de materialización como CREATED, no una fecha de vencimiento ficticia.
Cada ocurrencia tiene historia independiente; las reglas abstractas no tienen
NodeEvents. Editar/pausar/finalizar reglas no reescribe historias existentes; si
se procesa deuda activa antes de una operación, las nuevas ocurrencias reales
reciben su CREATED. Los recibos siguen evitando recrear Nodes borrados.

En una obligación, COMPLETED se presenta como **Pagada** y REOPENED como
**Reabierta**; no se duplica un evento PAID. Registrar eventos no modifica monto,
moneda, fechas ni responsables. La etiqueta visual usa la capacidad financiera
actual del Node: V1 no captura snapshots de monto, plazo, propósito o responsables
por evento. No se atribuye un actor a los responsables. Esa semántica histórica
necesitará un diseño explícito antes de métricas por responsabilidad/plazo.

Room v11 añade `node_events`, PK `id`, FK `nodeId`→`nodes.id` ON DELETE CASCADE,
e índice compuesto `(nodeId, occurredAt)`. La migración explícita 10→11 solo añade
la tabla y el índice, sin cambiar filas/triggers existentes ni usar fallback
destructivo. Se conserva el esquema exportado 11 junto a los anteriores.
Aunque `createdAt` se mantiene y suele proceder de la creación original, los
snapshots históricos/importados y las migraciones antiguas no garantizan una
proveniencia de eventos. Se elige historial inicialmente vacío para **todos los
Nodes preexistentes**, en lugar de reconstruir CREATED o inferir completados desde
`updatedAt`. La primera transición posterior se registra aunque no haya CREATED.

No hay API de editar, borrar o crear manualmente eventos. Los DAOs solo permiten
append/lectura e inserción de snapshots durante restore. Borrar un Node, su subárbol
o su proyecto elimina sus eventos por FK; se conserva el borrado iterativo seguro
para árboles profundos. No queda historial huérfano, tombstone ni auditoría forense
global. No hay poda automática de eventos de Nodes existentes.

Desde **Historial** en el menú de tarjeta, el detalle y los resultados filtrados
se abre un diálogo compacto de solo lectura. Consulta exclusivamente el Node
seleccionado con un Flow y muestra eventos recientes primero, con estados distintos
de carga, error/reintento y «Aún no hay eventos registrados.». Usa LazyColumn con
claves de evento. La consulta ordena `occurredAt DESC, rowid DESC`: con timestamps
iguales conserva el orden inverso de append sin añadir campos al modelo. El índice
satisface búsqueda y orden; no se cargan eventos de toda la base ni hay N+1 por
lista de tarjetas. El acceso en detalle comparte la fila de acciones existente.

Backup lógico **v4** incluye `nodeEvents` con IDs, Node, tipo e instante exactos;
el contenedor conserva su versión 1. Valida identidades únicas, referencias,
tipos conocidos, campos estrictos, enteros Long y presupuesto total de registros
antes de borrar datos. Snapshot conserva orden de append para preservar desempates
iguales al restaurar. Restore inserta Nodes mediante DAO y después sus eventos;
no usa semántica de creación normal ni agrega CREATED. La sustitución es atómica,
incluyendo eventos y archivos de avatar según el journal vigente. v1/v2/v3 se leen
con historia vacía, que también sustituye la historia presente. Restore seguido
de Recurrence conserva Nodes, recibos e historia; solo nuevas ocurrencias reciben
eventos. No se deduce una secuencia completa de backups con historial parcial.

La estructura permite futuros eventos/metadatos mediante una migración posterior.
V1 no implementa métricas, prioridad, sprints, pagos parciales, métodos de pago,
actor autenticado ni snapshots de relaciones históricas. Eventos eliminados junto
con su Node y cambios del reloj limitan cualquier futura interpretación forense.
No cambia versionName/versionCode ni publicación.

Archivos añadidos en el sprint de historial:

- `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/11.json`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/NodeEventEntity.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/NodeEventMigration10To11.kt`
- `app/src/main/java/com/r0ybt/arachn0de/domain/model/NodeEvent.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/NodeHistoryDialog.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/NodeEventBackupTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodeEventMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodeEventRecurrenceTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodeEventRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/ui/NodeHistoryUiTest.kt`

Archivos modificados en el sprint de historial:

- `ARCHITECTURE.md`
- `app/src/main/java/com/r0ybt/arachn0de/backup/BackupData.kt`
- `app/src/main/java/com/r0ybt/arachn0de/backup/BackupJson.kt`
- `app/src/main/java/com/r0ybt/arachn0de/backup/BackupRepository.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/Arachn0deDatabase.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/repository/NodeRepository.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/NodeComponents.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ProjectScreen.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/state/NodeActions.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/BackupFormatTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/BackupRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/RecurrenceBackupTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/TagsBackupTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/AttentionRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/CalendarFiltersRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/CalendarRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/FinancialRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodeBatchRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodeMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodePurposeTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/ObligationMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/ObligationRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/PersonRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/RecurrenceMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/TagMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/TaskDatesRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/export/NodeCopyUiTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/report/ObligationReportUiTest.kt`

Validación de historial: 279 pruebas dirigidas pasaron, incluidas 54 nuevas
(en cinco archivos de pruebas; persistencia/migración/backup también en API 24/28).
`assembleDebug` final y `git diff --check` pasaron. La suite completa se ejecutó
una sola vez: 522 pruebas, 517 pasaron y 5 fallaron. Los cinco fallos coinciden
con los históricos de ScrollRestorationTest (dashboard/map), LargeListsTest
(scroll de capa/drag de proyectos) y MvpReadinessTest (dashboard estrecho).
No se detectaron regresiones nuevas ni fallos por memoria. Hubo advertencias
de Robolectric de decoder y CloseGuard sin fallo de prueba asociado; no se afirma
una auditoría de recursos en dispositivo físico. No se repitió la suite completa
ni se ejecutó lint completo ni se arreglaron esos fallos históricos.
Queda validación manual de Historial desde tarjeta/detalle/Calendar/Attention/
Obligaciones, recreación, fechas locales y teclado en un dispositivo real.
Versión conservada: 0.2.2 / código 4; sin commit, push, tag o Release.


## Priority y Attention 2.0

Priority es una decisión explícita del usuario, independiente de la urgencia temporal:
`NONE`, `LOW`, `MEDIUM`, `HIGH`; por defecto `NONE`. No hay URGENT, puntuaciones,
métricas ni cambios de actor. Solo ACTION hoja tiene prioridad operativa, incluidas
obligaciones y ocurrencias reales. Convertir a NOTE o añadir hijos conserva el valor
latente, como las fechas; deja de mostrarse, filtrarse como prioridad o producir
señales. Al volver a ACTION hoja recupera su valor. No se hereda a hijos.

Room v12 añade `priority TEXT NOT NULL DEFAULT 'NONE'` a nodes y recurrence_rules
mediante migración explícita 11→12. No reconstruye tablas ni cambia relaciones,
triggers, índices o eventos. Cambiar prioridad actualiza updatedAt sin NodeEvent.
El editor y Batch usan un selector compacto; los indicadores usan la paleta existente.
Los borradores y filtros sobreviven a recreación de actividad.

NodeFilter combina prioridad opcional mediante AND con tiempo, responsables,
completado y etiquetas. null significa todas; NONE significa ACTION hoja sin
prioridad. Proyecto/Capa conserva ámbito y ancestros de contexto. Calendar conserva
su elegibilidad por fechas; una prioridad alta sin fecha no crea una entrada.
Limpiar filtros también restablece prioridad. Attention reutiliza el mismo filtro.
Recurrence guarda prioridad en la plantilla: editarla afecta solo ocurrencias futuras,
resolviendo antes las vencidas pendientes; las materializadas conservan su snapshot.
Batch asigna una misma prioridad a cada tarea dentro de la transacción existente.
Backup JSON v5 guarda prioridad de Nodes y reglas, conserva NodeEvents sin cambios;
lectura v1–v4 asigna NONE. Valores de enum inválidos se rechazan antes del restore.

AttentionReason define OVERDUE, DUE_TODAY, UPCOMING, HIGH_PRIORITY y MEDIUM_PRIORITY.
Solo entran ACTION hoja pendientes. OVERDUE mantiene la comparación estricta
now > dueAt; UPCOMING mantiene el horizonte inclusivo de 24 horas de TaskTemporal.
Una tarea todavía programada no obtiene razón temporal hasta su inicio. DUE_TODAY
usa día civil local, siempre que no esté atrasada ni programada, y sustituye UPCOMING
para evitar dos razones temporales del mismo vencimiento. HIGH incluye también
sin fecha, fecha lejana y programadas. MEDIUM se añade solo cuando ya existe razón
temporal; LOW/NONE no incluyen por sí solas. Cada tarea conserva varias razones
explicables, pero aparece una vez y cuenta una vez. No entran reglas abstractas.

Secciones en orden: atrasadas, vencen hoy, próximas, prioridad alta sin señal temporal.
Dentro: vencimiento ascendente con null al final, prioridad descendente, createdAt,
ID. No se usa puntuación compuesta. El resumen mantiene upcoming para hoy/próximas
y agrega priorityOnly; total suma categorías disjuntas. La propagación de conteos es
iterativa desde hojas, sin modificar prioridad de capas/proyectos. La ruta conserva
el proyecto y sus ancestros; obligaciones mantienen sus importes individuales sin
agregación monetaria ni mezcla de monedas. Se muestran responsables y etiquetas.

La proyección usa el snapshot compartido, sin consultas por tarjeta; cálculo/filtro
fuera del hilo principal, propagación O(n), orden O(k log k), caminos bajo demanda.
Una identidad de snapshot y filtro evita mezclar tarjetas antiguas con razones nuevas
al completar o editar. Se prueba una jerarquía de 10.000 niveles sin recursión.
Limitaciones V1: no desempate por score, prioridades heredadas, métricas, sprints,
planificación automática o snapshots históricos de prioridad. El reloj compartido
revalida en primer plano cada minuto como máximo, además de cambios temporales,
reanudación y broadcasts de zona/reloj; cambiar el día civil puede tardar hasta un
minuto en reflejarse. Queda revisión visual y de teclado en dispositivo físico.

Pruebas nuevas: PriorityAttentionTest, PriorityRepositoryTest, PriorityMigrationTest,
PriorityBackupTest y PriorityUiTest (35 ejecuciones; persistencia/backup/migración
incluyen API 24/28). Cubren política y orden, razones múltiples, exclusiones,
propagación profunda, filtros combinados, prioridad latente, obligaciones/historial,
atomicidad Batch/editor, recurrence futura, migración y backups v1–v5, recreación y
actualización reactiva de Attention.

Archivos de este bloque (inventario final):

- `app/src/main/java/com/r0ybt/arachn0de/backup/BackupData.kt`
- `app/src/main/java/com/r0ybt/arachn0de/backup/BackupJson.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/Arachn0deDatabase.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/NodeDao.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/NodeEntity.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/RecurrenceEntity.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/repository/NodeRepository.kt`
- `app/src/main/java/com/r0ybt/arachn0de/data/repository/RecurrenceRepository.kt`
- `app/src/main/java/com/r0ybt/arachn0de/domain/model/AttentionSnapshot.kt`
- `app/src/main/java/com/r0ybt/arachn0de/domain/model/Node.kt`
- `app/src/main/java/com/r0ybt/arachn0de/domain/model/NodeBatchGenerator.kt`
- `app/src/main/java/com/r0ybt/arachn0de/domain/model/NodeFilters.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/AppRoot.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/AttentionScreen.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/CalendarFiltersDialog.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/CalendarScreen.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/EditDialogs.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/NodeBatchDialog.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/NodeComponents.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ProjectScreen.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/RecurrenceUi.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ScopeFiltersDialog.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/state/AttentionState.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/state/CalendarFilterState.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/state/EditorDraft.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/state/NodeActions.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/state/NodeBatchDraft.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/BackupFormatTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/BackupRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/NodeEventBackupTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/RecurrenceBackupTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/TagsBackupTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/AttentionRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/CalendarFiltersRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/CalendarRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/FinancialRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodeBatchRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodeEventMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodeMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/NodePurposeTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/ObligationMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/ObligationRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/PersonRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/RecurrenceMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/TagMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/TaskDatesRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/export/NodeCopyUiTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/report/ObligationReportUiTest.kt`
- `app/schemas/com.r0ybt.arachn0de.data.local.Arachn0deDatabase/12.json`
- `app/src/main/java/com/r0ybt/arachn0de/data/local/PriorityMigration11To12.kt`
- `app/src/main/java/com/r0ybt/arachn0de/domain/model/Priority.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/PriorityUi.kt`
- `app/src/test/java/com/r0ybt/arachn0de/backup/PriorityBackupTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/PriorityMigrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/PriorityRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/domain/PriorityAttentionTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/ui/PriorityUiTest.kt`

Validación de Priority/Attention 2.0: 384 pruebas dirigidas pasaron; assembleDebug
pasó y git diff --check pasó. La suite completa se ejecutó una sola vez: 557 casos,
552 pasaron, 5 fallaron (0 omitidos). Coinciden con los cinco fallos históricos:
ScrollRestorationTest.dashboardRetainsScrollAcrossProjectVisitAndRecreation,
ScrollRestorationTest.mapRetainsScrollWhileClosedAndAcrossRecreation,
LargeListsTest.largeTaskLayerScrollsWithoutNavigatingIntoLeaf,
LargeListsTest.projectListLongPressDragReordersByStableId y
MvpReadinessTest.narrowDashboardAndDrawerExposeNoNonfunctionalFeatures.
No se observaron regresiones nuevas ni errores por memoria. Robolectric emitió
una advertencia de decoder sin fallo nuevo asociado. No se repitió la suite ni
se ejecutó lint completo o se modificaron los fallos históricos. Versión conservada
0.2.2 / código 4; sin commit, push, tag o Release.

## Creación / Edición 2.0 — revisión UX, bloque 1

La puerta principal es **Nuevo elemento** en el destino actual. No había un botón
«Crear recurrente» independiente en este checkout: el problema era que Recurrencia
se presentaba como otro selector entre campos dispersos. Ahora se activa mediante
un Switch dentro de Planificación. Se retira el botón aislado «Crear varios» del
Proyecto/Capa; Batch se configura dentro del mismo formulario. Recurrencias sigue
siendo el gestor de reglas existentes, incluidas reglas sin ocurrencias, no otra
puerta para crear Nodes. Node y RecurrenceRule mantienen dominios separados.

El formulario usa bloques verticales ligeros: **General** (título, descripción,
tipo), **Pago**, **Planificación**, **Organización** y **Creación**. No hay tabs ni
tarjetas grandes por sección. Título conserva autofocus al comenzar vacío.
Crear/Guardar es un botón primario fijo; Cerrar es secundario. El contenido admite
scroll con estado guardable y controles dinámicos; la acción de descarte está al
final del contenido. Los selectores finitos de tipo/numeración/fechas de lote
usan chips que se ajustan en varias filas, y las modalidades usan Switch con
semántica explícita, sin depender exclusivamente del color.

Obligación reutiliza el Checkbox y Money existentes: apagada oculta importe/moneda,
encendida los muestra. Inicio/Vencimiento tienen Switch; encender muestra el
selector fecha/hora existente y exige elegir una fecha antes de guardar. Apagar
conserva el instante latente; «Quitar» elimina explícitamente el valor y desactiva
la opción. Recurrencia muestra frecuencia DAILY/WEEKLY/MONTHLY/YEARLY, intervalo,
inicio civil y fin opcional solo al activarse. La primera frecuencia seleccionada
es Diaria; después se recupera la última frecuencia. La inicialización del inicio
civil al activar conserva el comportamiento previo del selector de recurrencia;
no hay nuevos defaults globales, por Capa ni horas predeterminadas.

La organización reutiliza Priority, TagSelector y ResponsibleDialog. Responsables
está disponible también para tareas ordinarias y Notes, y dentro de la edición.
La edición inicial espera las asignaciones cargadas para no sustituirlas por una
lista vacía de carga. updateEditor permite guardar responsables, contenido, fechas,
obligación, prioridad y tags en la misma transacción; el parámetro opcional preserva
la compatibilidad de callers anteriores. Un fallo en cualquier relación revierte
los demás campos. No cambia Finance, monedas, pagos ni historial.

Cambiar ACTION→NOTE→ACTION dentro de creación conserva opciones/valores latentes:
obligación, dinero, prioridad, fechas y recurrencia se ocultan para NOTE y vuelven
al regresar. Notes se guardan sin obligación ni prioridad operativa y Batch NOTE
no genera fechas. No se cambia el propósito de un Node existente desde el editor:
las conversiones conservan sus operaciones y confirmaciones actuales. Editar una
ocurrencia es editar únicamente ese Node; no cambia la regla. El editor específico
de plantilla se organiza en General/Pago/Organización/Destino, conserva entrada
al cerrar mediante SaveableStateHolder por regla y tiene descarte confirmado.
Guardar plantilla limpia ese estado y vuelve al listado de reglas.

Crear varios activa cantidad 1–500, numeración y número inicial, regla temporal
finita y preview acotada. El título del editor es el nombre base y todos los campos
comunes proceden del mismo EditorDraft: no se copian al cambiar modalidad.
EditorDraft.batchDraft() adapta esos campos al NodeBatchDraft/NodeBatchGenerator
existentes únicamente para validar/generar/persistir. No hay un segundo borrador
activo de lote en ProjectScreen. NodeBatchDialog permanece como componente legado
sin entrada de producción, ejercitado por sus regresiones anteriores. El lote
mantiene la semántica existente: genera solo dueAt, nunca startAt. Inicio queda
latente mientras Batch está activo y una explicación lo comunica. Sin fechas
significa BatchTemporalRule.NONE; el resto necesita primer vencimiento.

Batch y recurrencia persistente no se combinan: al activar una se bloquea activar
la otra y aparece una explicación. Si se vuelve de NOTE con ambas opciones
latentes, Guardar se bloquea y se permite apagar cualquiera; no se borran parámetros
para resolver el conflicto. No se inventan reglas para N recurrencias o cuotas.

EditorDraft sigue siendo el único modelo de entrada de Nodes. Su Saver versionado
incluye opciones activadas, valores latentes, recurrencia, campos Batch, contenido,
fechas, dinero/Locale, prioridad, tags, responsables y UUID de reintento. Lee el
formato anterior. EditorDraftStore organiza esos mismos borradores por Proyecto
(en el SaveableStateProvider existente) y claves distintas `new:<parentId>` / raíz
y `edit:<nodeId>`. Abrir otro Node o destino nunca reutiliza entrada ajena.

**Cerrar ≠ descartar**: tap fuera, Atrás del diálogo y Cerrar quitan únicamente la
apertura, sin borrar entrada. Volver a Nuevo elemento en el mismo destino, o a
Editar del mismo Node, recupera automáticamente el borrador. No hay confirmaciones
para cerrar. Un formulario vacío no ofrece descarte; con entrada significativa,
Descartar pide confirmación y elimina únicamente el borrador activo. Guardado
confirmado elimina ese borrador; ante error se conserva completo y se permite
reintentar. El destino e identidad originales no se redirigen por cambios de
navegación. La restauración nunca dispara escrituras.

NodeActions.saveDraft decide modalidad y reutiliza NodeRepository, Batch y
RecurrenceRepository. OperationState mantiene bloqueo síncrono de doble submit,
campos deshabilitados durante escritura, errores y cierre solo tras éxito.
Normal y Batch conservan sus transacciones e identidades estables; Recurrence
conserva la creación de regla y materialización/cursor/recibos por bloques existentes.
No promete convertir todo el catch-up de una regla en una sola transacción:
un fallo posterior puede dejar bloques anteriores confirmados, cuyo reintento
reconoce las identidades existentes. Cambiar opciones/cerrar/restaurar borradores
no genera NodeEvents; CREATED sigue perteneciendo solo a creación real.

Room sigue **v12**, backup lógico **v5**, contenedor v1, sin migraciones, columnas,
DAO o formato de backup nuevos. Borradores no pertenecen a Room ni al backup.
SavedState guarda solo inputs/IDs, no entidades, repositorios ni operaciones.
Sobrevive recomposición, cierre/reapertura y recreación de Activity (también con
borrador cerrado). Puede recuperarse tras muerte del proceso si Android devuelve
el Bundle; no garantiza force-stop, borrar tarea de recientes, salida voluntaria
o reinicio. No se añadió almacenamiento permanente de borradores. Texto y número
de borradores comparten los límites reales del Bundle. Desaparecer un proyecto
elimina su acceso de navegación; no hay navegador de borradores huérfanos.

Fuera de este bloque: Undo, selección múltiple, edición del lote como unidad,
batchId/creationGroupId persistidos, defaults globales/Capa, Templates, orden
automático, rediseño global de botones, drawer, redes sociales y Copy Project.
VersionName/versionCode permanecen 0.2.2/4; sin commit, push, tag o Release.
Pendiente dogfooding físico de teclado/IME, TalkBack, fuente ampliada, rotación y
muerte real de proceso. Robolectric verifica estado/geometría y no sustituye esas
pruebas. No se afirma revisión visual global ni persistencia ilimitada.

Archivos de Creación/Edición 2.0 (inventario):

- `ARCHITECTURE.md`
- `app/src/main/java/com/r0ybt/arachn0de/data/repository/NodeRepository.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/EditDialogs.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/ProjectScreen.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/RecurrenceUi.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/TagUi.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/TaskDates.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/state/EditorDraft.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/state/NodeActions.kt`
- `app/src/test/java/com/r0ybt/arachn0de/DraftRestorationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/PeopleUiTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/SaveFailureTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/TaskDatesIntegrationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/ui/MvpReadinessTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/ui/NodeBatchUiTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/ui/NotesUiTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/ui/ObligationUiTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/ui/RecurrenceUiTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/ui/TaskDatesUiTest.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/CreationFields.kt`
- `app/src/main/java/com/r0ybt/arachn0de/ui/state/EditorDraftStore.kt`
- `app/src/test/java/com/r0ybt/arachn0de/CreationDraftRestorationTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/data/CreationEditorRepositoryTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/ui/CreationDraftStateTest.kt`
- `app/src/test/java/com/r0ybt/arachn0de/ui/CreationUxTest.kt`

Pruebas nuevas: CreationUxTest (4 Compose), CreationDraftRestorationTest (2 con
ActivityScenario.recreate), CreationDraftStateTest (3 de estado/Saver) y
CreationEditorRepositoryTest (2 × API 24/28 = 4). RecurrenceUiTest añade un caso
de recuperación/descarte/guardado de plantilla. Total: 14 ejecuciones nuevas.
Las pruebas existentes de formularios se adaptan a Crear/Guardar y a Batch dentro
de Nuevo elemento; mantienen las verificaciones de persistencia/error/idempotencia.
No se corrigen fallos históricos de scroll/drag/dashboard. La prueba estrecha
verifica 320×480 dp y fuente 1,6× con campos dinámicos y acción primaria accesible.
Validación dirigida final: 359/359 pasaron; assembleDebug pasó.

La suite completa se ejecutó **una sola vez**: 571 casos, 565 correctos, 6 fallidos,
0 errores y 0 omitidos. Las 14 ejecuciones nuevas pasaron. Frente al baseline
552/557, conserva los cinco fallos conocidos de ScrollRestorationTest (2),
LargeListsTest (2) y MvpReadinessTest (1). El sexto es
ObligationPngRendererTest.longTextWrapsWithinPanelsAndHeightIncludesAllRows,
con ReportMemoryException por presupuesto de heap disponible. El renderer y su
prueba no se modificaron; ese mismo fallo por recursos ya está documentado en
#34 y recurrencia. Se repitió **solo** ObligationPngRendererTest de forma aislada:
4/4 pasaron. Es evidencia de sensibilidad a recursos, no una suite general verde
ni una nueva ejecución de baseline. No se volvió a ejecutar toda la suite ni
se arreglaron fallos históricos. Informe completo conservado en
`/tmp/arachnode-creation-ux-full-report/index.html` y XML en
`/tmp/arachnode-creation-ux-full-results`.
assembleDebug final y git diff --check correctos. Sin lint completo, Release,
cambios de versión, commit, push o tag.

## Recuperación, selección y grupos de creación (UX bloque 2)

El estado actual pasa a **Room v13 y backup lógico v6**. Los apartados anteriores
que describen Room v12/backup v5 corresponden al bloque previo. La versión de app
permanece 0.2.2 (code 4).

### Undo V1

`NodeActions` ofrece Snackbar Material Short con «Deshacer» tras creación normal
o Batch: aproximadamente cuatro segundos, ampliables por accesibilidad Material.
El token efímero `CreationUndo` contiene los IDs exactos, filas, relaciones de
etiquetas/responsables e historial capturados dentro de la transacción creadora.
No se infieren IDs por título, posición ni timestamp. Undo compara nuevamente
el snapshot entero y rechaza filas desaparecidas, modificadas o con nuevos hijos;
si es válido, borra únicamente esas filas en una transacción. Cascades eliminan
relaciones y CREATED, pero conservan etiquetas/personas del catálogo. Fallos no
se presentan como éxito. El borrador se limpia al crear con éxito y Undo no lo
resucita. La oportunidad termina por timeout, salida/recreación de pantalla o
nuevo feedback; no se persiste un historial de comandos.

Se evaluó Undo de eliminación y quedó fuera de V1: la eliminación detacha y borra
subárboles, cascades y relaciones; restaurarlos exactamente exigiría snapshots de
historial/relaciones/posición y resolver conflictos con padres, catálogos,
normalización y restauraciones concurrentes. No se introducen papelera ni
soft-delete. Tampoco se ofrece Undo de edición, movimiento, recurrencia o bulk.
La eliminación conserva confirmación explícita, incluida advertencia de ausencia
de Deshacer para selección múltiple.

### Creation group y persistencia

`Node.creationGroupId`/`NodeEntity.creationGroupId` es nullable e indexado. El UUID
estable de la ejecución Batch se usa como grupo de sus Nodes; otra ejecución
obtiene otro ID. La API conserva IDs opacos de Batch existentes en pruebas y
reintentos. Crear individualmente o materializar recurrencia no asigna grupo.
Mover/editar conserva identidad histórica y eliminar un miembro no afecta los
otros por pertenencia. No hace falta tabla de grupos ni cascades de grupo.

`CreationGroupMigration12To13` añade columna nullable e índice, sin reescribir
filas: Nodes anteriores quedan sin grupo. Se exporta schema 13 y se conservan
rutas históricas, triggers y foreign keys. Backup v6 exige y preserva el campo;
v1–v5 usan null. Se rechazan grupos vacíos/excesivos o compartidos entre proyectos
antes de restaurar. El contenedor de backup no cambia. Batch es generación finita;
creation group es origen persistente; RecurrenceRule/receipts siguen siendo el
motor recurrente independiente.

### Selección, eliminación y movimiento

La pulsación larga sobre «Más opciones» de una tarjeta entra en selección y
selecciona ese Node; «Seleccionar» del menú y acción semántica son alternativas.
Se reserva el cuerpo de la tarjeta para el drag individual existente. En selección,
tap agrega/quita, aparecen checkbox/estado semántico y barra con contador,
Mover/Eliminar/Salir. No se seleccionan visualmente descendientes por elegir una
Capa. No hay drag múltiple; reorder se desactiva mientras hay selección.

La selección vive solo en la composición del contexto Proyecto/Capa/filtro;
cambiar de contexto o recrear limpia y una emisión retiene únicamente IDs
visibles. Back/Salir limpian. Filtros incluyen filas de contexto visibles y
selección explícita accesible. No hay selección entre proyectos.

`SelectionRoots.normalize` recorre iterativamente el bosque ordenado, valida la
selección y elimina raíces cubiertas por ancestros seleccionados. Bulk delete
expande esas raíces iterativamente, detacha en bloques de 500 y elimina en una
sola transacción; una confirmación advierte descendientes/relaciones/historial.
Bulk move valida la cadena de destino contra el snapshot y raíces elegidas,
rechaza ciclos, NOTE/obligaciones y destinos ajenos; mueve raíces preservando
subárboles y orden relativo, append al destino. Reabre el destino completado
mediante la semántica existente. Sin cambios parciales; errores conservan selección
y diálogo para reintentar. Un éxito produce un único feedback con cantidad de
IDs seleccionados (puede incluir descendientes ya cubiertos por una raíz).

### Edición grupal y Draft

Al guardar un Node agrupado con cambios propagables y más de un miembro existente
se ofrece «Solo este elemento»/«Todos los elementos del grupo». La revisión
comunica cantidad total e incluye miembros movidos a otras capas. Solo este usa
el editor individual existente. Todos aplica `SharedNodePatch` explícito, nunca
clona Nodes. Allowlist: descripción, monto, moneda, Priority, etiquetas y
responsables. Se propagan exclusivamente valores diferentes de la baseline del
borrador; conjuntos se comparan sin depender del orden. `FieldChange` distingue
campo ausente de retirada nullable; descripción vacía y conjuntos vacíos son
cambios explícitos. No se propagan título/numeración, fechas, posición, jerarquía,
completion, recurrencia ni eventos. Título/fechas modificados del Node editado
se guardan únicamente en ese Node, en la misma transacción.

El editor captura baseline después de cargar relaciones. Saver conserva grupo,
baseline y valores al cerrar/reabrir/recrear, con compatibilidad del formato de
Draft anterior. Error retiene Draft/revisión; éxito o descarte explícito limpia.
Cambiar solo título/fecha o editar sin grupo no provoca pregunta grupal. Borrados
no se recrean: se aplican cambios a miembros existentes. Cambios de membresía
entre revisión y guardado requieren revisar de nuevo. Un patch financiero o de
prioridad exige que TODOS los miembros sean hojas ACTION compatibles; se rechaza
el grupo entero si alguno se convirtió en NOTE/Capa. Descripción/relaciones pueden
aplicarse a miembros con esas capacidades. Nodes heterogéneos conservan campos
sin cambios, incluida moneda cuando solo se cambió monto.

Filas y relaciones se actualizan en una única transacción Room y cualquier fallo
revierte todas. History sigue CREATED/COMPLETED/REOPENED: Undo y bulk/group edit
no añaden tipos de evento. Los receipts recurrentes conservan su política previa
cuando se elimina una ocurrencia. No se edita una regla por pertenecer a un grupo.

### Rendimiento y cobertura

No se consulta por tarjeta para selección/grupo. IDs usan sets y normalización,
expansión y validación de jerarquía son iterativas, con prueba de 10.000 niveles.
Snapshot Undo lee Nodes, hijos, relaciones y eventos en consultas IN de hasta
500 IDs; validación y bulk usan snapshots del proyecto, evitando lecturas por
Node para recorrer ancestros. Las escrituras por miembro son necesarias y quedan
dentro de una transacción; la consulta de grupo usa su índice.

`GroupBulkUndoTest` cubre identidad, Undo seguro/rechazo/rollback, padres/hijos,
movimiento y patches heterogéneos/atómicos. `CreationGroupMigrationTest` usa el
schema real v12, preserva todas las tablas y verifica v13/null/triggers/FKs.
`CreationGroupBackupTest` verifica v6, distintos grupos, individuos, recurrencia,
historial y v1–v5. `CreationDraftStateTest` verifica baseline restaurada, retirada
explícita y selección profunda. `RecoveryUxTest` cubre Snackbar normal/Batch,
selección por pulsación larga y menú, contador, confirmación única, errores,
reintento, Solo este y Todo el grupo con borrador conservado.

Undo elimina los Nodes capturados; no revierte normalizaciones de posiciones ni
la reapertura previa de una hoja padre que se convirtió en Capa al crear hijos.
Esos cambios del dominio existente y sus eventos se conservan. Al eliminar hijos
no se completa automáticamente el padre. La eliminación validada de Undo también
usa bloques de 500, sin consultar un subárbol por cada hoja del lote.

Validación del bloque: tanda dirigida de 333 casos, 332 correctos y un fallo de
sincronización al abrir el borrador de Proyecto (`DraftRestorationTest`, campo
Nombre aún ausente); ese caso pasó aisladamente sin modificarlo. Tras optimizar
el borrado Undo por bloques, las 22 pruebas de GroupBulkUndoTest/RecoveryUxTest
pasaron y assembleDebug pasó con el código final. Una tanda anterior incluyó por
coincidencia de nombre el fallo histórico de drag de proyectos; no se modificó.
La suite completa se ejecutó UNA vez: **595/601**, frente a **565/571** del baseline.
Los 30 casos añadidos pasan. Persisten exactamente los cinco fallos históricos:
ScrollRestorationTest (2), LargeListsTest (2), MvpReadinessTest (1), más
ObligationPngRendererTest.longTextWrapsWithinPanelsAndHeightIncludesAllRows por
ReportMemoryException, que pasó **4/4** al ejecutar su clase aisladamente. No hay
nuevos fallos en la suite completa. Reporte completo preservado en
`/tmp/arachnode-recovery-full-report` y XML en
`/tmp/arachnode-recovery-full-results`. `git diff --check` pasa. No se ejecutó lint
completo ni se realizaron commit/push/tag/Release ni cambios de versión de app.


## VISUAL ACTION LANGUAGE — UX bloque 3

La gramática distingue la función antes de elegir el control. No se sustituyen indiscriminadamente textos interactivos:

| Función | Representación y regla |
| --- | --- |
| Primaria | `Button`: Crear/Guardar y operación principal del formulario. |
| Secundaria | `SecondaryAction` (`OutlinedButton`): Editar, Mover, Seleccionar, Completar/Reabrir. |
| Terciaria | `TextButton` para Cancelar, Cerrar, Salir y acciones menores inequívocas. |
| Compacta | `IconButton`, icono comprensible, `contentDescription` y target de 48 dp. |
| Overflow | `ActionMenu` modal desplazable y `ActionMenuItem` con icono, label y rol Button. Conserva el patrón contextual existente y admite listas largas. |
| Destructiva | `DestructiveAction` / item de menú con icono Eliminar, color error y descripción de estado «Acción destructiva»; conserva confirmaciones existentes. |
| Estado / selector | Switch, Checkbox, Radio o chips según su semántica; un cambio de estado no es texto informativo. |
| Navegación | Cards/títulos navegables y `NavigationDrawerItem`, con selección semántica; no se convierten en acciones de formulario. |
| Información | Text sin callback; los iconos decorativos de una acción no duplican su anuncio. |

Los componentes comunes están en `ActionComponents.kt`; se reutilizan los controles Material 3 existentes para primaria, terciaria y estado. Convertir Nota/Tarea y la copia del Node abierto pasan al overflow del contexto; las tarjetas de Node y Proyecto comparten la presentación del menú. Convertir mantiene ACTION/NOTE, History y confirmación de quitar pago. La barra de selección conserva su contador, muestra Mover secundario, Eliminar destructivo y Salir terciario en una fila que puede envolver. En resultados filtrados Seleccionar es una acción secundaria explícita. Descartar borrador adquiere tratamiento destructivo, sin alterar las secciones de Creación/Edición 2.0, Crear varios, recurrencia, borradores, creación grupal ni Snackbar/Deshacer.

### Drawer adaptable

`DrawerWidthPolicy` usa las restricciones Compose del ancho disponible, dentro de `AppSafeArea`, sin dependencia nueva: menos de **600 dp** ocupa todo el ancho y alto disponibles; desde **600 dp** conserva un panel lateral de **360 dp**. La política depende de la ventana actual (incluidos rotación/multiventana), no del nombre del dispositivo. `AppSafeArea` sigue consumiendo una sola vez `safeDrawing` (barras, cutouts e IME); no hay medidas estimadas de barras.

El logo oficial `arachn0de_logo` se muestra a 112 dp con `ContentScale.Fit`, sin alterar su archivo o colores; «Arachn0de» conserva identidad y tipografía Material existente. Principal agrupa Proyectos, Atención, Calendario, Obligaciones y Personas; Aplicación contiene Acerca de, con un único separador entre grupos. No se inventa Configuración ni una ruta Finanzas distinta de Obligaciones. Proyectos representa tanto dashboard como el proyecto abierto. Elegir una opción cierra y navega; Proyectos regresa explícitamente al dashboard aunque el proyecto haya sido abierto desde una vista transversal. Back y «Cerrar menú» cierran el panel; en ventanas grandes también cierra el fondo externo.

El panel completo se desplaza cuando no cabe (incluida fuente grande); sus controles usan mínimos de 48 dp y texto sin truncado impuesto. Mientras está abierto, se ocultan de accesibilidad las acciones de la pantalla subyacente. El fondo externo es un hermano del panel, evitando mezclar sus semánticas/paneTitle.

### Redes sociales

`SocialLinkRow` usa un botón outlined adaptable: icono local, red + identificador y affordance de enlace externo. GitHub, Instagram y Mastodon usan vectores locales de Simple Icons 13.21.0; Web usa Material Language. Origen y licencia CC0 están en `THIRD_PARTY_NOTICES.md` y `design/licenses/simple-icons-CC0.md`. No existe descarga de iconos en runtime. `AuthorLinks` conserva exactamente autor, labels y URLs anteriores; Código fuente sigue separado del GitHub personal. Se mantienen ACTION_VIEW y el manejo de ActivityNotFoundException/SecurityException con error visible. Iconos decorativos no añaden anuncios; el botón anuncia red, identificador y apertura externa.

### Copy Project

`NodeExportSnapshot.captureProject` añade una raíz de presentación con nombre y descripción del Proyecto; «Copiar» exporta solo esa raíz y «Copiar con descendientes» recorre las raíces del proyecto y sus subárboles con el orden del `NodeTreeSnapshot`. Se reutilizan la captura de Node, `NodeMarkdownRenderer` y `NodeCopyActions`, sin formato paralelo ni consultas nuevas. El Proyecto se representa como contenedor, conserva NOTE/ACTION y añade un nivel a sus descendientes. El árbol vacío sigue exportando el Proyecto.

La copia vive en las opciones de la tarjeta del Proyecto y del contexto raíz, nunca como botones permanentes. En dashboard la copia completa espera la carga del árbol coherente existente; las acciones se deshabilitan durante la copia. El snapshot es inmutable, el trabajo corre en Default con cancelación y el portapapeles se escribe en Main. Se conservan el límite de 200 000 unidades UTF-16 tanto del contenido agregado como del resultado escapado, el indentado/encabezados acotados, las exclusiones de IDs, fechas, dinero y asociaciones privadas, los mensajes de error y el feedback Toast previo a Android 13 / confirmación nativa desde Android 13. Si se supera el límite no se sustituye el portapapeles anterior.

Fuera del bloque: Room v13, Backup v6, Repository, dominio, paleta/tipografía global, logo original, reglas de selección/Undo, orden automático, defaults, Templates, Sprints, Kanban, Gantt, Undo de eliminación y refactor general de navegación. Se mantiene versión 0.2.2 / versionCode 4.

## Orden de presentación — UX bloque 4

`NodeSortMode` y `NodePresentationSort` pertenecen a `ui/state`: son una proyección de presentación de los Nodes existentes, sin operaciones de Repository ni cambios de dominio. **Orden visual no equivale a `position` persistido.** El modo inicial es Manual. El selector compacto «Ordenar» está en la cabecera del Proyecto/Capa abierto, reutiliza `ActionMenu`/`ActionMenuItem` y muestra check y estado seleccionado. Un texto discreto indica el modo automático y «Arrastre deshabilitado»; el botón lo anuncia también semánticamente. No se añade selector al dashboard de Proyectos ni a vistas transversales.

| Modo / label | Criterio y desempates, dentro del grupo de completado existente |
| --- | --- |
| MANUAL / Manual | `position ASC → createdAt ASC → id ASC`, exactamente como `NodeTreeSnapshot.childrenOf`. |
| DUE_ASC / Vencimiento próximo | Con fecha primero; `dueAt ASC → position ASC → createdAt ASC → id ASC`. |
| DUE_DESC / Vencimiento lejano | Con fecha primero; `dueAt DESC → position ASC → createdAt ASC → id ASC`. |
| PRIORITY / Prioridad | Ranking explícito de `effectivePriority`: HIGH=3, MEDIUM=2, LOW=1, NONE=0, descendente; luego con fecha primero, `dueAt ASC → position ASC → createdAt ASC → id ASC`. |
| CREATED_NEWEST / Más recientes | `createdAt DESC → position ASC → id ASC`. |
| CREATED_OLDEST / Más antiguos | `createdAt ASC → position ASC → id ASC`. |

Todas las fechas se comparan como instantes UTC ms (Long), sin resta susceptible de overflow, conversión a texto ni reconstrucción desde History. Null queda al final tanto en vencimiento próximo como lejano. Todos los modos conservan los elementos sin atributo. Notes y Capas usan `effectivePriority` NONE según el dominio; no se inventa prioridad operacional. La ordenación por fecha utiliza únicamente el `dueAt` propio existente; no deriva vencimiento o prioridad desde hijos. Una obligación y una ocurrencia materializada son Nodes ACTION normales para esta proyección.

La vista ya separaba **Disponibles / Completadas**: se conserva esa regla en todos los modos, sin cambiar visibilidad. Dentro de cada grupo los elementos se ordenan normalmente por sus atributos. No se introduce una nueva política de completado.

### Scope, Filter y Sort

Para listas normales se seleccionan los hijos reales del Proyecto/contexto y se ordenan. Con filtros, `ScopedNodeFilter` conserva la responsabilidad de decidir pertenencia y ancestros mínimos; después `NodePresentationSort.filtered` ordena **solo hermanos dentro de cada nivel** y reconstruye preorder iterativo. Conserva identidad, profundidad, `isMatch`, padres y conjunto de filas; no aplana ramas ni mueve un descendiente fuera de su padre. Manual conserva la proyección filtrada anterior. Sort no forma parte de `NodeFilter` ni altera sus presets, AND, Tags, Personas, Estado o Tiempo.

La proyección automática y el árbol filtrado se calculan en `Dispatchers.Default` mediante estado Compose cancelable cuando cambian snapshot/contexto/filtro/modo. Manual reutiliza `NodeTreeSnapshot.childrenOf` con `remember(snapshot, contexto)`, conservando la proyección coherente existente sin añadirle una fase de carga asíncrona. Mientras se prepara se muestra estado de carga; la selección no se depura contra ese vacío transitorio. Elegir modo no elimina selección: las operaciones siguen usando IDs y normalización padre/hijo existentes. Las emisiones de creación, edición, batch, materialización, Move y Undo recalculan la vista sin escribir positions de otros elementos.

### Orden manual y operaciones

Drag y reordenamiento se habilitan exclusivamente en Manual sobre hijos reales, sin filtro ni selección activa y sin operación pendiente. Cambiar modo/contexto cancela el estado de drag anterior; en automático también se deshabilitan acciones de reorder y se protege el callback de escritura. Volver a Manual recupera exactamente las posiciones persistidas y habilita drag. Move To individual/múltiple sigue permitido: usa la transacción existente para parent, posición manual en destino, relaciones y creationGroup; el destino aplica su propio modo de presentación. No se modifica creación, batch, recurrencia, History ni el contrato Snackbar/Deshacer.

### Preferencias y restauración

`AppRoot` conserva `NodeSortPreferences` mediante `rememberSaveable`, separado de los proveedores de pantalla que la navegación elimina al volver al dashboard. Las claves combinan ID de Proyecto y ID de Capa (o raíz): cada contexto tiene un modo independiente, incluso después de salir/reabrir un Proyecto durante la sesión. No es una preferencia global que cambie todas las listas.

Se guardan **como máximo 64 contextos con modo no Manual en toda la sesión**, los modificados más recientemente. Volver a Manual elimina la entrada; al superar el límite se expulsa la entrada modificada hace más tiempo y ese contexto vuelve a Manual. El Bundle guarda únicamente identidad y nombre del modo, nunca Nodes/árboles. Valores desconocidos o incompletos se restauran como Manual. No hay preferencias permanentes en disco ni mapa nuevo ilimitado.

Sobrevive recomposición, navegación y recreación de Activity. Puede restaurarse tras muerte de proceso **si Android devuelve SavedState de la tarea**; no se garantiza force-stop, retirar la tarea, arranque nuevo o reinicio del dispositivo. Un lanzamiento nuevo sin Bundle empieza en Manual. Restaurar un backup reinicia AppRoot mediante su infraestructura existente y devuelve estos modos a Manual. Robolectric comprueba Activity recreation y el Saver; no acredita muerte física del proceso.

### Rendimiento, export y límites

Ordenar N filas cuesta O(N log N), con O(N) memoria adicional y sin consultas por tarjeta. En un árbol filtrado se indexan filas/padres una vez, se ordena cada conjunto de hermanos y se recorre iterativamente; no depende de la profundidad de la pila. El render con drag resuelve IDs mediante `nodesById` en O(1), sustituyendo búsquedas repetidas en cada grupo. Pruebas dirigidas cubren 20 000 elementos y 12 000 niveles filtrados, sin benchmark ni promesa de FPS.

Copy Node/Project sigue recibiendo el **snapshot estructural original** y su política manual de orden; elegir un sort temporal no modifica el Markdown exportado. Attention 2.0, Calendar, Obligaciones agregadas y dashboard de Proyectos conservan sus órdenes propios. Room permanece v13 y Backup v6; Sort no añade columnas, migraciones, tablas ni preferencias al backup. VersionName/versionCode permanecen 0.2.2/4.

Fuera de V1: agrupaciones mensuales, derivar fechas de Capas/Proyectos, ordenar reglas abstractas de recurrencia, otros criterios, presets, defaults, Templates, Sprints, Gantt y Kanban; tampoco se cambia la navegación general, el dominio de Move/Undo ni los fallos históricos de listas/PNG.

## Valores predeterminados de creación — UX bloque 5

El estado actual es **Room v14 / backup lógico v7**, conservando app **0.2.2 / code 4**.
`CreationDefaults` inicializa un formulario nuevo; después se guardan valores concretos
como cualquier Node. Cambiar la configuración nunca actualiza Nodes existentes.
La cadena es UI → configuración/resolución/fábrica de Draft → Repository → Room.
No se introduce configuración adicional en cada Node.

### Modelo, scopes y precedencia

`DefaultValue<T>` distingue `Inherit` de `Own(value)`. Las diez propiedades se
resuelven independientemente: tipo ACTION/NOTE, obligación ON/OFF, moneda
CLP/USD/EUR, Priority, conjunto de Tags, conjunto de Personas, regla de inicio,
hora de inicio, regla de vencimiento y hora de vencimiento. NONE, OFF, conjunto
vacío, fecha NONE y hora Unspecified son overrides reales, diferentes de heredar.
No hay default de título, descripción, monto, completion, recurrencia, creación
grupal, posición ni History. La moneda puede quedar preparada aunque Pago esté OFF.

Scopes explícitos: Global (`G`), Proyecto raíz (`P:<projectId>`) y Capa/destino
(`N:<nodeId>`, junto a su projectId). El Node con propósito ACTION y sin obligación
puede recibir hijos y configurar sus defaults incluso mientras todavía sea hoja.
Un contexto temporalmente convertido en NOTE/obligación conserva su configuración
latente, sin permitir creación; recupera su utilidad al volver a admitir hijos.

Precedencia exacta: comportamiento base → Global → Proyecto raíz → capas ancestro
desde la más distante → contexto actual. Solo se reemplazan propiedades Own.
Tags y Personas reemplazan **todo el conjunto** en el nivel que los configura;
no existe unión implícita. Renombrar una etiqueta/persona no cambia su identidad.
La resolución recibe colecciones indexadas, recorre ancestros iterativamente y
rechaza ciclos, padres ausentes y pertenencia a otro proyecto. Se prueban 12000
niveles; no usa recursión ni consultas por ancestro.

### Reglas temporales

Ambas fechas permiten NONE, TODAY, TOMORROW, IN_DAYS (1–3650), FIRST_DAY,
FIRST_DAY_NEXT, DAY_OF_MONTH (1–31), DAY_NEXT_MONTH, FIRST_MONDAY y
FIRST_MONDAY_NEXT. Se guardan regla y parámetro, nunca el instante calculado al
configurar. `DefaultDateResolver` recibe reloj y TimeZone explícitos. La creación
los obtiene al finalizar la lectura de configuración, usando la zona local actual.

- Hoy/mañana/en N días utilizan fechas civiles y aritmética de Calendar, sin +24h.
- Día N se ajusta al último día válido del mes; febrero admite 28/29.
- Inicio usa el ancla del mes actual, aunque ya haya pasado.
- Vencimiento DAY_OF_MONTH/FIRST_MONDAY busca el mes siguiente únicamente si el
  día civil elegido ya pasó. Si es hoy conserva hoy, aunque la hora ya haya pasado.
- Primer día del mes es un ancla fija; las reglas explícitas «mes siguiente»
  siempre calculan ese mes. Cruces de mes/año mantienen calendario gregoriano.
- Las horas se heredan por separado: Unspecified o minuto 0–1439. Sin fecha la hora
  permanece latente. Sin hora se utiliza 00:00 local, porque el dominio actual
  guarda instantes epoch millis y no distingue un evento de día completo.
- Se reutiliza `RecurrenceSchedule.localDay/timestamp`: un hueco DST se normaliza
  hacia adelante; una hora repetida usa la ocurrencia estándar posterior elegida
  por GregorianCalendar. Esto coincide con materialización recurrente; el selector
  manual existente continúa rechazando horas inexistentes. No hay selector de offset.
- Se acotan los años calculados a 1–9999. Inicio y vencimiento independientes pueden
  producir un orden incompatible: el editor ACTION lo muestra y exige corregirlo,
  sin cambiar silenciosamente ninguna regla ni guardar fechas inválidas.

### UI y borradores

Drawer → **Configuración** → **Valores predeterminados de creación** abre el editor
global. Overflow del Proyecto/Capa abierto → **Valores predeterminados** edita ese
contexto. No se añade una sección permanente a las listas. Cada selector anuncia
Heredar con su valor heredado, o Propio con el valor elegido. Cambiar una propiedad
conserva todas las otras. Tags/Personas propios permiten ninguno y selección buscable
con lista lazy. Número y HH:mm inválidos bloquean Guardar. Fallos de carga ofrecen
reintento; fallos de escritura conservan el editor y usan OperationErrorDialog.

El editor de configuración tiene estado guardable explícito de valores/IDs;
recrear Activity conserva cambios sin escribirlos automáticamente. Guardar persiste
solo tras acción explícita; salir con cambios requiere descarte confirmado. Reset
siempre confirma: Global borra únicamente G y vuelve al comportamiento base; un
contexto borra únicamente sus overrides y vuelve a heredar. No modifica otras
configuraciones, Nodes ni borradores abiertos. SavedState no es persistencia permanente.
El diálogo contextual consume sus propios insets y evita cierre externo accidental.

`CreationDefaultsDraftFactory` transforma valores efectivos en `EditorDraft`, fuera
de Compose. `EditorDraftStore.hasNew` permite recuperar el borrador del destino
antes de resolver defaults. Cerrar/reabrir, recrear o cambiar defaults conserva las
ediciones y los instantes del borrador previo. Descartar elimina solo ese borrador;
la siguiente apertura resuelve configuración/reloj actuales. La lectura asíncrona
usa OperationState, evita doble envío y conserva el padre capturado.

NOTE oculta Pago/Priority/fechas como valores latentes del Draft; cambiar a ACTION
los recupera. Una NOTE nueva se guarda sin fechas, obligación ni Priority operativa,
evitar incompatibilidad entre fechas ACTION latentes. Editar Notes existentes no
borra sus fechas históricas latentes. Editar Node existente y Move nunca usan la
fábrica de defaults ni rellenan campos vacíos. La configuración no toca History.

Batch adapta los campos comunes del mismo Draft (Pago/moneda/Priority/Tags/Personas).
Sus fechas siguen `BatchTemporalRule`, finitas y derivadas de su primer vencimiento;
Inicio permanece latente según la política existente. Defaults no activan Batch,
Recurrence ni creationGroup. Recurrence sigue requiriendo una acción explícita y
materializa por su motor/recibos propios. Sort, filtros, Copy, Attention y Calendar
no leen defaults: sus Nodes concretos mantienen las proyecciones anteriores.

### Persistencia, eliminación y backup

`creation_defaults` utiliza columnas explícitas nullable para INHERIT; enums conocidos
para reglas/propósito/prioridad y boolean nullable para obligación. Hora NULL hereda,
−1 significa explícitamente sin hora y 0–1439 hora propia. `tagsOverride` y
`peopleOverride` distinguen herencia de un conjunto propio vacío; las asociaciones
se guardan en `creation_defaults_tag` y `creation_defaults_person` con PK compuesta.
Codec/repositorio validan identidades, enums, parámetros, horas y monedas;
foreign keys validan IDs existentes y proyecto de la Capa.

Migración explícita 13→14 añade solo tres tablas e índices, vacías. Conserva todas
las tablas/filas/triggers anteriores, incluidas finanzas, recurrence, eventos,
jerarquía, posiciones y creationGroup. Se exporta 14.json; todas las migraciones
históricas permanecen y no existe fallback destructivo.

FK CASCADE elimina configuración del Proyecto/Node y asociaciones correspondientes.
Borrado de subárbol conserva el detach/borrado iterativo anterior. Global sobrevive
al borrado de proyectos. Borrar Tag/Persona elimina solo su asociación de defaults;
un conjunto propio que queda vacío sigue siendo propio, sin resucitar herencia.

Backup lógico v7 añade arrays explícitos `creationDefaults`, `defaultsTags` y
`defaultsPeople` a snapshot/JSON/restore. Comparte transacción, presupuestos,
contenedor v1 y journal de avatares existentes. Valida duplicados, identidades,
referencias, flags, monedas, reglas/horas/parámetros antes de reemplazar datos.
Restore limpia todos los defaults, incluido Global, y los inserta después de
Proyectos/Nodes/Tags/Personas, dentro de la misma transacción. Un fallo incluso al
insertar defaults revierte TODO el restore. Backups v1–v6 adaptan colecciones vacías,
restauran comportamiento base y sustituyen defaults anteriores. Apps antiguas no
pueden leer v7; no se promete downgrade. No cambia cifrado ni permisos.

Configuración efectiva usa consultas agrupadas acotadas al proyecto y Global,
y una lectura de Nodes del proyecto para ancestros. No consulta por tarjeta ni
observa defaults para resolverlos en cada recomposición. Coste O(N + D + A) en lectura
indexada/recorrido para N Nodes, D profundidad y A asociaciones relevantes, más
copias de los conjuntos efectivos de los overrides aplicados; memoria proporcional
al proyecto/configuración. No hay benchmark ni paginación nuevos.

`CreationDefaults` y la fábrica dejan una frontera reutilizable para futuros presets,
sin parser, interpolación, macros, perfiles nombrados ni Templates. V1 sigue local,
sin red/telemetría, automatización condicional o cambios retroactivos. Pendientes:
dogfooding físico de IME/TalkBack/fuente grande, DST real, muerte de proceso y límites
de Bundle; las pruebas Robolectric no acreditan esos escenarios físicos.

Archivos nuevos: `domain/defaults/CreationDefaults.kt`,
`data/local/CreationDefaultsEntity.kt`, `CreationDefaultsMigration13To14.kt`,
`data/repository/CreationDefaultsRepository.kt`, `ui/CreationDefaultsScreen.kt`,
`ui/state/CreationDefaultsDraftFactory.kt` y schema `14.json`.
Integraciones: `Arachn0deDatabase`, `NodeRepository`, `BackupData`, `BackupJson`,
`BackupRepository`, `AppRoot`, `NavigationChrome`, `ProjectsScreen`, `ProjectScreen`,
`EditorDraftStore` y `NodeActions`. Se conservan los cambios locales de Sort del bloque 4.
Pruebas nuevas: `CreationDefaultsTest`, `CreationDefaultsDraftTest`,
`CreationDefaultsRepositoryTest`, `CreationDefaultsMigrationTest`,
`CreationDefaultsBackupTest` y `CreationDefaultsUiTest`. Pruebas existentes actualizan
solo expectativas de Room actual, fixtures/version futura de backup y desplazamiento
a destinos reales del drawer en `PeopleUiTest`/`BackupUiTest`/`FinancialUiTest`.
`NodeCopyUiTest` y `ObligationReportUiTest` actualizan también su expectativa de Room a v14.

### Validación del bloque de defaults

Se añadieron **31 métodos / 41 ejecuciones**: CreationDefaultsTest (12 JVM),
CreationDefaultsDraftTest (4 JVM), CreationDefaultsRepositoryTest (4 × API 24/28),
CreationDefaultsMigrationTest (1 × API 24/28), CreationDefaultsBackupTest
(5 × API 24/28) y CreationDefaultsUiTest (5 Compose API 28). Cubren precedencia
por propiedad, NONE/OFF/conjuntos vacíos, 12000 niveles/ciclos, clamp/bisiesto,
fecha actual/mes/año, Monday, zonas/Locale, DST de 23 h, hueco y hora repetida,
latencia NOTE, Batch independiente, borrador existente/descarte, edición/Move,
persistencia/reapertura, referencias/cascades/reset, migración del schema v13,
backup v7/v1–v6, validación y rollback después de borrar/reinsertar datos,
configuración global/contextual, estado tras recreación y campos inválidos.
La prueba contextual también comprueba Atrás en el dispatcher de la ventana
modal: pide descarte con cambios y permite continuar editando.

La tanda dirigida final pasó **442/442** en 59 clases, sin errores/omitidos,
junto a `assembleDebug`; `git diff --check` pasó antes de la suite general.
Los dos fallos de interacción iniciales (PeopleUiTest y BackupUiTest) se
corrigieron desplazando el drawer hasta el destino antes del toque, conservando
sus verificaciones de persistencia/restore.

Se ejecutó **UNA sola vez** `:app:testDebugUnitTest --offline`: **681 casos,
671 correctos, 10 fallidos, 0 errores y 0 omitidos**. Las **41 nuevas pasan**.
Frente al baseline **595/601**, hay 80 casos añadidos y ninguno eliminado,
incluidos los bloques UX 3/4 posteriores al baseline. Cinco fallos coinciden con
los históricos: ScrollRestorationTest.dashboardRetainsScrollAcrossProjectVisitAndRecreation,
ScrollRestorationTest.mapRetainsScrollWhileClosedAndAcrossRecreation,
LargeListsTest.largeTaskLayerScrollsWithoutNavigatingIntoLeaf,
LargeListsTest.projectListLongPressDragReordersByStableId y
MvpReadinessTest.narrowDashboardAndDrawerExposeNoNonfunctionalFeatures.
No se modificaron esos tests ni se arreglaron sus bloques.

Los otros cinco fallos fueron comprobaciones de pruebas que necesitaban adaptación:
NodeCopyUiTest y ObligationReportUiTest todavía esperaban Room v13; los tres
FinancialUiTest tocaban Obligaciones fuera del viewport del drawer. Se actualizaron
solo esas expectativas a v14 y el scroll antes del toque. La verificación posterior
**NodeCopyUiTest + ObligationReportUiTest + FinancialUiTest + CreationDefaultsUiTest
pasó 13/13**, junto a `assembleDebug`. La comprobación modal de Atrás también pasó
por separado. No se repitió la suite completa: su resultado registrado sigue
siendo 671/681, sin extrapolar una suite general verde. No hubo fallos de PNG/memoria;
el renderer pasó sus cuatro casos en la suite. Hubo el aviso de decoder de
Robolectric ya conocido, sin fallo asociado.

Reportes conservados: `/tmp/arachnode-defaults-directed-report/index.html`,
`/tmp/arachnode-defaults-full-report/index.html` y
`/tmp/arachnode-defaults-post-full-report/index.html`; XML en las carpetas paralelas
`*-results`. `assembleDebug` final y `git diff --check` correctos. No se ejecutó
lint completo ni Release. No cambia app 0.2.2 / code 4; sin commit, push, tag ni
publicación. La validación física y benchmark permanecen pendientes.
