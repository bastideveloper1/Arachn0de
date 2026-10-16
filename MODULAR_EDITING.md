# Edición modular inferior: extensión progresiva

## Componente y contrato de presentación

`ui/ModularPanel.kt` extrae el diseño aprobado de Calendario:

- `ModularPanelSurface`: superficie tonal elevada y delimitación superior fina, usando los tokens actuales de apariencia.
- `ModularPanelLayout`: altura natural acotada por el espacio disponible; encabezado y acciones fuera del cuerpo desplazable. Un campo multilínea o una lista lazy puede conservar su propio desplazamiento, sin otro scroll vertical alrededor. La presentación adaptable de Descripción conserva el límite proporcional del piloto y su ampliación explícita.
- `modularPanelFieldModifier`: acerca el campo enfocado cuando cambia el espacio del IME. El foco/teclado automático opcional se solicita una vez al entrar, sin volver a solicitarlo al ocultar el teclado.
- `ModularPanelDialog`: presentación inferior para diálogos pequeños existentes; consume insets de área segura e IME, permite cierre exterior y Atrás mediante el callback original. No posee borradores ni modifica datos.

Calendario y creación rápida conservan sus hosts integrados y reciben los insets de su pantalla. No se añade una ventana modal a Calendario. Etiquetas conserva su `ModalBottomSheet` y el cierre por arrastre; reutiliza superficie, distribución y foco comunes dentro de ese host.

La confirmación, cancelación, validación y navegación siguen perteneciendo a cada operación. No se añade autosalvado, persistencia durable ni claves nuevas de preferencias. Los filtros y etiquetas que ya se aplicaban inmediatamente conservan ese comportamiento; no se inventa una transacción de borrador para ellos.

## Adaptaciones realizadas

| Área | Operaciones adaptadas |
| --- | --- |
| Calendario | Editor de Evento y sus propiedades; filtros. Título y Descripción solicitan foco y teclado al abrirse. Se conservan los buffers, selección, reducción y guardado final existentes. |
| Tareas y Notas dentro de Núcleos/Capas | Creación rápida y sus selectores de vencimiento y recurrencia. Conserva el mismo `EditorDraft` y la transferencia a Formulario completo. |
| Asignaciones de Núcleos, Capas, Tareas y recurrencias | Selector compartido `ResponsibleDialog`: búsqueda, cuadrícula y selección provisional existentes, con Cancelar/Guardar accesibles fuera del cuerpo. |
| Listas de trabajo y Seguimiento | `ScopeFiltersDialog`, utilizado en `ProjectScreen` y `AttentionScreen`. Se conserva el estado y la combinación de filtros existentes. |
| Recursos y etiquetas de tarjetas/formularios | `TagSelector`: búsqueda, selección y creación existentes; la lista conserva su scroll y las acciones inferiores quedan fuera. |
| Personas / Antisocial | `PersonSectionEditor` para Notas y campos de Perfil; conserva copias provisionales y guardado de la sección. Contacto y los campos que abren el editor unificado siguen usando ese editor. |
| Ajustes → Diseño | Paleta de colores de indicadores; conserva selección inmediata y restablecimiento existentes. |
| Copia de tarjetas | `CopyTextChoiceDialog`, conservando las dos opciones y callbacks existentes. |

## Pendientes estructurales, sin intervenir

- `EditDialogs.kt`: `NodeDialog` y `ProjectDialog` contienen adjuntos, importaciones, fechas, pagos, recurrencia, tecnologías y estados de borrador compartidos. Dividirlos en propiedades requiere una fase específica de integración; no se convirtió el formulario completo automáticamente.
- `PeopleScreen.kt`: editor unificado de identidad/perfil y avatar. `PersonRelationEditor` tiene su propia selección de destino y etapas; debe evaluarse la navegación entre etapas antes de modularizarlo.
- `RecurrenceRuleDialog`, `CharterSectionEditor`, gestión de tecnologías/etiquetas y `CreationDefaultsScreen`: formularios compuestos con validaciones, búsquedas, acciones anidadas u ordenación. Su conversión integral queda pendiente de definir cada adaptación, en lugar de cambiar simultáneamente todos los formularios.
- Menús contextuales inferiores existentes: no se reorganizan. Visores de imágenes, galerías, mapas, Gantt y consultas extensas conservan sus pantallas justificadas.
- Seguridad, respaldos y bóvedas: no se modifica su interfaz ni arquitectura en esta fase.

Estas secciones siguen funcionando con su presentación anterior. La extensión global todavía es progresiva; no se declara terminada la conversión de toda la aplicación.

## Validación dirigida

Archivos de esta fase (rutas de interfaz relativas a `app/src/main/java/com/r0ybt/arachn0de/ui/`):

- Nuevo: `ModularPanel.kt`.
- Adaptados: `CalendarEventEditor.kt`, `CalendarFiltersDialog.kt`, `QuickCreateBar.kt`, `ScopeFiltersDialog.kt`, `PersonComponents.kt`, `PersonDetailUi.kt`, `TagUi.kt`, `AppearanceScreen.kt` y `CopyTextChoiceDialog.kt`.
- Pruebas: nuevo `app/src/test/java/com/r0ybt/arachn0de/ui/ModularPanelTest.kt`; ampliados `QuickCreateBarTest.kt` y `PersonInterfaceCleanupTest.kt` del mismo directorio.
- Documentación: este archivo, `MODULAR_EDITING.md`.

15 casos distintos aprobados con `testDebugUnitTest` (sin suite completa):

- `ModularPanelTest`: cuerpo largo y acciones visibles a fuente 1,6×; cambio de espacio disponible, crecimiento y restauración del texto; cancelación/confirmación local y reapertura sin panel residual; selección real de Responsables; navegación del menú de Persona dentro de filtros del Calendario.
- `CalendarEventPilotTest`: crecimiento, scroll, ampliación/reducción de Descripción y restauración de selección; independencia y conservación de buffers de texto/hora/color.
- `TagsUiTest`: selección, reemplazo, retirada y cierre; búsqueda entre 200 etiquetas; limpieza de filtros de ámbito. El caso de selección/cierre se repitió únicamente tras conservar el host con arrastre.
- `IndicatorColorsTest`: selección de paleta y restablecimiento desde Diseño.
- `QuickCreateBarTest`: transferencia del mismo borrador/cancelación; retorno desde recurrencia; selector de vencimiento con fecha/hora.
- `PersonInterfaceCleanupTest`: cancelación y guardado reales de Notas sin alterar los demás campos del perfil.

La compilación Kotlin Debug se ejecutó como parte de las comprobaciones. `git diff --check` y comparación con copias previas de los archivos de interfaz sin incidencias. No se cambiaron Room, SQLCipher, respaldos, dependencias ni modelos de negocio.

### Pendiente en Android físico

Las reducciones de altura de las pruebas simulan espacio disponible; **no acreditan el teclado Android real**. Revisar:

1. Calendario: abrir Título/Descripción, escribir, ocultar y reabrir IME; el panel permanece abierto, recupera altura natural y conserva texto/cursor.
2. Acciones y campo enfocado sobre IME en pantalla pequeña, horizontal y con fuentes grandes; animaciones sin saltos.
3. Descripción extensa: scroll interno, ampliar, reducir a adaptable y regresar al compacto sin pérdida.
4. Búsqueda en Responsables/etiquetas con teclado; cierre por arrastre de etiquetas y cancelación exterior/Atrás sin aperturas accidentales.
5. Contraste real de superficie/borde, ergonomía del pulgar, bloqueo/reapertura y selectores Android de documentos de los formularios conservados.

No se generó APK Release ni se hicieron commits o push.

## Patrón maestro: subeditores sin Reducir y extensión a texto

En Nuevo evento se retira Reducir de las seis propiedades y se conservan los
nombres del campo en el encabezado y Cancelar/Confirmar abajo. Atrás mantiene
la diferencia funcional anterior: conserva los buffers provisionales al volver
al menú y, en Descripción ampliada, vuelve primero a la presentación adaptable.
Cancelar reinicia solo el buffer activo; Confirmar lo incorpora al borrador.
Guardar sigue siendo la única escritura definitiva. La cuadrícula aprobada de
Nuevo evento conserva sus seis etiquetas fijas, tres columnas, dos filas,
iconos, medidas y colores.

Editar evento reutiliza el mismo patrón y conserva su opción existente Eliminar
con la confirmación original. No muestra valores dentro de los accesos a campos.

`ModularFieldMenu` reutiliza los botones medidos del patrón maestro con el número
de columnas apropiado; `ModularOptionGrid` mantiene la exigencia de seis opciones
y tres columnas para Eventos. `ModularTextFieldsEditor` centraliza menú, buffers
provisionales, confirmación, cancelación, Atrás y adaptación al teclado para
secciones simples de texto, sin escribir en repositorios antes del guardado final.

Se adaptan Notas (General) y los cuatro campos de Perfil de Personas: Cualidades,
Habilidades, Intereses y Disponibilidad. Las opciones conservan sus nombres y no
muestran valores ni resúmenes. Guardar sección mantiene su nombre y callback;
Cancelar/Confirmar de cada campo regresan al menú sin alterar los demás valores.
La restauración de estado conserva confirmados y provisionales. No se añaden
campos, persistencia, formatos ni controles ajenos a este flujo.

Se conservan por especialización: los filtros/selectores que aplican cambios
inmediatamente, paletas, listas de etiquetas y responsables, formularios con
adjuntos/archivos, pagos/recurrencias, identidad y datos personales, relaciones,
gestión de tecnologías y secciones con sugerencias específicas. Convertirlos al
flujo de confirmación por propiedad requiere decidir antes cómo conservar sus
validaciones, acciones y cancelaciones. Fotografías, galerías, calendarios,
mapas y consultas extensas no se convierten en menús. No se declara completada
la conversión de toda Arachn0de.

Pendientes físicos: IME real, animaciones al abrir/cerrar teclado, zonas táctiles,
contraste y fuentes grandes. Las pruebas de navegación del diálogo deben enviar
Atrás a su propia ventana; enviar la pulsación a la Activity subyacente no prueba
el cierre del subeditor modal.

Validación de esta extensión: siete casos dirigidos aprobados. Tres cubren los
seis subeditores de Evento, la conservación/cancelación de buffers y Descripción
ampliada con Atrás; los restantes verifican la cuadrícula aprobada, guardado real
de Notas sin alterar otros datos, buffers de cuatro campos tras recreación y el
flujo de creación/edición/eliminación confirmada de Eventos. El caso de Atrás
modal se repitió solo tras corregir el destino de la pulsación en la prueba.
Compilación Kotlin Debug y `git diff --check` correctos.

Archivos de esta extensión: `ModularPanel.kt`, `CalendarEventEditor.kt`,
`PersonDetailUi.kt`; pruebas `CalendarEventPilotTest.kt`, `CalendarEventsUiTest.kt`,
`ModularPanelTest.kt`, `PersonInterfaceCleanupTest.kt`; este documento.

## Antisocial: menús automáticos al seleccionar sección

Esta etapa sustituye las rutas anteriores por tarjetas/lápices en las secciones
compatibles. General abre Apodo, Descripción y Notas; Contacto abre Teléfonos,
Correos, Direcciones y Enlaces; Perfil abre Cualidades, Habilidades, Intereses,
Disponibilidad y Fecha de nacimiento. Las tarjetas superiores pasan a consulta,
sin lápices ni pulsaciones de edición redundantes. El panel se integra debajo
de esa consulta, usando el host común en modo inline y los insets del IME.

Los menús se abren al seleccionar la sección, incluso al volver a pulsar su
icono. Al cambiar de sección se conservan los borradores en el estado privado
de UI; al regresar se muestra el menú completo sin descartar los buffers.
Cancelar de un campo reinicia solo ese buffer. Confirmar actualiza únicamente
el borrador de sección. Guardar sección mantiene el guardado transaccional
existente, sincroniza Apodo con la identidad pública cuando corresponde y
conserva los datos personales, avatar, relaciones y otras secciones. Se respetan
el Apodo obligatorio, los límites vigentes de Descripción/Biografía y el selector
existente de Fecha de nacimiento; no se crean campos ni formatos nuevos.

Tecnologías abre directamente su selector existente al entrar, sin el paso por
Asociar Tecnología. Volver a pulsar la pestaña reabre el selector. Su búsqueda,
selección de dominio y retirada de asociaciones conservan el funcionamiento
especializado anterior.

Se mantienen como consultas o flujos especializados Inicio/publicaciones,
fotografías, Red, relaciones y Proyectos y trabajo. Datos personales sigue dentro
del editor unificado Editar perfil junto con avatar e identidad; no existe una
pestaña independiente y no se inventa una para esta tarea. Sus campos Nombre,
Apellidos y DNI / RUT y su guardado permanecen intactos. No se modifican
persistencia, cifrado, respaldos ni navegación principal.

Archivos: `ModularPanel.kt`, `PersonDetailUi.kt`, `PersonIntegrationUi.kt`,
`PersonInterfaceCleanupTest.kt` y este documento. Pendientes físicos: IME,
rotación, superficies táctiles, contraste, pantallas pequeñas y fuentes ampliadas;
los selectores especializados conservados requieren su revisión visual habitual.

Validación de esta etapa: cinco pruebas dirigidas aprobadas (menús automáticos y
buffers entre secciones, selector directo de Tecnologías, cancelación/guardado de
Notas, navegación de las siete secciones y conservación de campos del componente
compartido tras recreación). Compilación Kotlin Debug y `git diff --check`
correctos. El primer intento del caso de Tecnologías falló al preparar archivos
del fixture por una limitación de fsync de Robolectric; se corrigió únicamente
la preparación del catálogo en la prueba y el caso pasó. No se ejecutaron suites
generales ni se generó una APK.
