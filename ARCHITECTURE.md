# Arachn0de — Architecture & Product Specification

## 1. Propósito de este documento

Este documento es la fuente principal de contexto técnico y funcional de Arachn0de.

Antes de realizar cambios importantes en el proyecto, debe consultarse este documento.

Es una fuente de verdad viva: cada cambio arquitectónico o regla importante debe documentarse en la misma intervención que modifica el código. Distinguir siempre lo implementado, lo descartado y lo pendiente; no presentar objetivos de sprints futuros como capacidades actuales.

El objetivo es mantener una arquitectura coherente, evitar implementaciones innecesarias y permitir que el proyecto crezca progresivamente sin tener que reconstruir su núcleo.

La versión actual corresponde al primer MVP de Arachn0de: **v0.1.0**.

La configuración Android debe declarar `versionName = "0.1.0"`.

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
createdAt
updatedAt
```

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
```

Desde el esquema Room 3, `isStructural` e `isCompletable` no son columnas persistidas ni parámetros de creación/edición. El dominio expone `hasChildren` derivado de las relaciones persistidas; `isStructural = hasChildren` e `isCompletable = !hasChildren` son propiedades calculadas de solo lectura.

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

La estructura persistida es la única fuente de verdad:

- Node sin hijos = hoja/tarea, siempre completable.
- Node con uno o más hijos = capa/contenedor, nunca completable manualmente.
- El usuario no elige un tipo técnico.
- No existen hojas estructurales no completables, aunque versiones anteriores permitían crearlas.

Se descarta mantener `isStructural` e `isCompletable` almacenados como fuentes independientes de la estructura.

## Transiciones estabilizadas en Sprint 5.5, bloque 1

- Hoja pendiente + primer hijo: pasa a capa y conserva `isCompleted = false`.
- Hoja completada + primer hijo: pasa a capa y se borra su antiguo completado manual (`isCompleted = false`). Su progreso depende desde ese momento de las hojas descendientes.
- Capa pierde su último hijo, por eliminación o traslado: vuelve a ser una hoja pendiente y completable. Nunca recupera un completado manual histórico.
- Crear, trasladar o eliminar hijos aplica estas reglas en la misma operación atómica.
- Editar título/descripción no modifica estructura, posición ni completado.
- Completar/descompletar una capa se rechaza; nunca hay completado automático en cascada, ni hacia hijos ni hacia padres.
- Completar una hoja no la elimina ni la oculta.

## Cálculo exacto de progreso

Cada hoja aporta una unidad de trabajo, con igual peso. Su progreso es 0/1 o 1/1. Una capa suma las hojas de todos sus descendientes; no se cuenta a sí misma ni se promedian los porcentajes de sus hijos.

```text
Temporada 1
├── Episodio 1 ✓
├── Episodio 2 ✓
├── Episodio 3 ○
└── Episodio 4 ○

Progreso: 2 / 4 = 50 %
```

El porcentaje entero se trunca: `completed * 100 / total`. Los estados son `NOT_STARTED`, `PARTIAL` y `COMPLETE`. Con estas reglas, todo nodo de un árbol válido tiene al menos una hoja relevante; un nodo vacío es una tarea pendiente con total 1. Un proyecto sin nodos tiene una instantánea vacía.

Los porcentajes y contadores no se guardan en SQLite. `NodeTreeSnapshot` calcula todos los progresos de una instantánea con un recorrido iterativo de hojas a raíz, sin profundidad fija. Un ciclo o relación inválida produce un error de integridad controlado, no recursión infinita.

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

El esquema actual es la versión 3. Se conservan los esquemas históricos 1 y 2 y sus rutas de migración; no se usa `fallbackToDestructiveMigration`.

### Invariantes de nodos

- Todo nodo pertenece a un proyecto existente. Identidad y proyecto de un nodo son inmutables.
- El padre debe existir y pertenecer al mismo proyecto. SQLite lo exige mediante una clave foránea compuesta `(projectId, parentId)` → `(projectId, id)` y su índice único de referencia.
- No se permite autoparentesco ni mover un ancestro debajo de un descendiente. Repository valida la cadena de padres dentro de la transacción; triggers SQLite rechazan ciclos incluso al escribir directamente sin Repository.
- Los recorridos de ancestros usan IDs visitados. La consulta recursiva de defensa SQL usa `UNION` para no repetir IDs. El cálculo de descendientes/progreso es iterativo y verifica que todos los nodos hayan podido procesarse.
- Triggers SQLite impiden completar manualmente contenedores y normalizan el completado de los padres afectados al insertar, mover o eliminar hijos. Se instalan en bases nuevas y durante la migración 2→3; no aparecen en el JSON de esquema de Room, por lo que tienen pruebas explícitas.
- Crear, editar, trasladar, completar/descompletar, alternar completado y borrar nodos pasan por transacciones de Room. Las escrituras de contenido, estructura y completado actualizan únicamente sus campos respectivos.
- La eliminación de un nodo elimina su subárbol; la eliminación de un proyecto elimina sus nodos. El borrado por DAO/Repository desconecta los vínculos internos antes de eliminar, dentro de una única transacción, para evitar cascadas recursivas dependientes de la profundidad. Las claves foráneas CASCADE se conservan como defensa del esquema; SQL directo que omita este procedimiento sigue sujeto al límite de SQLite.
- `moveNode(id, parentId)` separa traslado de edición. `parentId = null` significa mover a raíz; no significa conservar el padre. No se añade una interfaz de traslado en este bloque.
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
- Las Capas de cebolla responden a la pregunta: “¿Cómo llegué hasta aquí?” y se mantienen separadas del mapa de capas del proyecto.
- El mapa de capas responde a la pregunta: “¿Qué contiene este proyecto y a qué parte quiero ir?” y debe permitir navegar directamente a cualquier punto de la jerarquía, incluso fuera de la rama actual.
- El selector de Capas de cebolla debe limitar su altura máxima y permitir scroll interno para soportar profundidades arbitrarias sin romper la interfaz.
- El proyecto puede tener varias ramas, por lo que no existe una sola “capa siguiente”; la navegación debe permitir saltos a cualquier nodo del árbol completo desde el mapa.
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

El menú de nodo permite mover arriba/abajo un lugar entre hermanos, tanto en raíz como dentro de una capa. Las opciones se deshabilitan en los extremos y durante una operación. La tarjeta conserva su ID, padre, descendientes, contenido y completado. El mapa y la lista reciben el orden nuevo a través del flujo de Room. No se implementa arrastrar ni traslado entre padres desde la UI en este bloque.

`reorderNode` lee el nodo y sus hermanos dentro de una transacción, valida el padre esperado para rechazar acciones sobre una ubicación obsoleta, intercambia vecinos y asigna posiciones contiguas 0..n−1. No se utiliza el índice visual como identidad. Un nodo ausente o con padre distinto devuelve false; mover más allá de un extremo es una operación idempotente que también puede normalizar el grupo. Se actualizan únicamente posición y fecha de modificación, nunca una entidad completa. La fecha cambia para los dos nodos intercambiados; la reparación de numeración conserva fechas históricas.

La política para datos antiguos es conservar el orden visible por posición, fecha de creación e ID y compactar cada grupo de hermanos por separado. `observePreparedProjectState`, utilizado por la pantalla, normaliza todos los grupos del proyecto en una única transacción antes de emitir el contenido inicial. `observeProjectState` y los demás observadores generales siguen siendo de solo lectura. La pantalla espera la primera instantánea validada antes de mostrar una ruta restaurada. Las normalizaciones posteriores sin cambios no escriben filas. No se modifica otro proyecto ni se requiere migración de esquema (Room permanece en versión 3).

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

## Pendiente para los siguientes bloques

- **Navegación y estado:** validar muerte real del proceso y gestos predictivos en dispositivos; comprobar límites del Bundle con entradas grandes. Borradores, diálogos, desplazamiento e idempotencia de creaciones están implementados en el bloque de recuperación. Atrás desde raíz y ubicación ante recreación están cubiertos por el bloque 4.
- **Orden y escala:** benchmark/profiling en dispositivo y evaluación de paginación/incrementalidad si las mediciones lo requieren. Las listas lazy, claves estables, mapa iterativo y expansión funcional están implementados. La observación aún carga todo el proyecto.
- **Arquitectura de UI:** evaluar ViewModels solo si más adelante se requiere mantener tareas fuera de la composición; la recuperación actual no los necesita. La extracción de MainActivity, los errores de persistencia y la propiedad de Room están resueltos en el bloque 5.
- **UI/UX y accesibilidad:** insets, descripciones accesibles, métricas Activos/Hoy simuladas, búsqueda vacía, drawer sin destinos funcionales, responsive de métricas, acciones secundarias de proyectos, continuidad del recorrido visual y soporte efectivo de modo claro.
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

# 21. Roadmap conceptual

La evolución prevista de Arachn0de incluye aproximadamente:

## Etapa 1
Núcleo, proyectos, nodos y Capas de cebolla.

## Etapa 2
Tareas enriquecidas, prioridades, estados, etiquetas, filtros y plantillas.

## Etapa 3
Objetivos, backlog, sprints, hitos, Kanban y Gantt.

## Etapa 4
Versiones, releases, Change Sets, changelog e integración con Git.

## Etapa 5
Project Path, archivos, código e issues asociados a archivos.

## Etapa 6
Personas, equipos, roles, disponibilidad y Scrum Poker.

## Etapa 7
Riesgos, reuniones, agenda, decisiones e historial del proyecto.

## Etapa 8
Motor de Atención.

El sistema podrá identificar información que merece atención:

```text
Proyecto con tareas vencidas
Hito próximo
Trabajo bloqueado
Proyecto estancado
```

Debe presentar señales al usuario sin sustituir sus decisiones.

## Etapa 9
Métricas, progreso histórico, tendencias, velocidad y estimado vs. real.

## Etapa 10
Uso personal del motor de Arachn0de.

Ejemplos:

- seguimiento de series y episodios;
- colecciones;
- compras en cuotas;
- personas asociadas a pagos;
- cuotas restantes;
- saldo mensual y total.

## Etapa 11
Privacidad avanzada, cifrado local y protección de acceso.

## Etapa 12
Sincronización local entre dispositivos mediante LAN.

## Etapa 13
Arachn0de Desktop.

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
