# Arachn0de — Architecture & Product Specification

## 1. Propósito de este documento

Este documento es la fuente principal de contexto técnico y funcional de Arachn0de.

Antes de realizar cambios importantes en el proyecto, debe consultarse este documento.

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
isCompletable
isCompleted
position
createdAt
updatedAt
```

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

# 9. Modelo de progreso

Los nodos pueden ser completables o estructurales.

Sin embargo, la experiencia de Arachn0de no expone esa elección al usuario como un tipo técnico.

La estructura determina el comportamiento real:

- un elemento sin hijos se comporta como tarea;
- un elemento con hijos se comporta como una capa o contenedor;
- el progreso de una capa se deriva de sus descendientes;
- la interfaz no exige una elección manual de "tipo".

El modelo interno puede seguir manteniendo `isCompletable` para simplificar persistencia y cálculo de progreso, pero la regla visible es que el usuario crea elementos y la jerarquía define si son tareas o capas.

Un nodo completable puede estar:

```text
Pending
Completed
```

Ejemplo:

```text
Temporada 1
├── Episodio 1 ✓
├── Episodio 2 ✓
├── Episodio 3 ○
└── Episodio 4 ○
```

El progreso de `Temporada 1` sería:

```text
50 %
```

Los contenedores deben poder derivar su progreso a partir de sus descendientes relevantes.

Un nodo con hijos no debe poder marcarse manualmente como completado; su progreso debe derivarse de sus descendientes.

Si un nodo tiene hijos, completar ese nodo nunca debe completar automáticamente a sus descendientes ni propagarse en cascada a la rama completa.

El progreso puede propagarse hacia niveles superiores.

Evitar almacenar porcentajes derivados si pueden calcularse de manera segura desde los datos originales.

La implementación concreta debe mantenerse sencilla durante el MVP.

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

- marcar nodos completables;
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
6. marcar elementos completables;
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
- Las capas vacías deben mostrarse como estado vacío, con la posibilidad de crear un nuevo nodo dentro de esa capa.

## Reglas de implementación

- Se mantiene la arquitectura actual de UI → Repository → Room.
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
- Los nodos completable siguen representando trabajo medible en progreso.
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
6. completar o descompletar nodos completable;
7. ver progreso actualizado por capa y por nodo;
8. cerrar y reabrir la aplicación y conservar la estructura.

Este sprint sigue siendo una mejora incremental del MVP, no una reescritura del núcleo de datos.

---

# 18. Fuera del alcance del MVP

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

# 19. Roadmap conceptual

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

# 20. Compatibilidad con el futuro

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

# 21. Instrucciones para agentes de código

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

# 22. Principio rector

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
