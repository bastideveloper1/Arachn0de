# Decisiones para la futura limpieza de menús

## Modo de selección múltiple — aprobado, pendiente de implementación

- La pulsación larga mantiene su función de arrastre en el modo normal.
- «Seleccionar varios» en el menú ⋮ activa un modo explícito y selecciona la tarjeta de origen.
- El modo muestra el contador de elementos seleccionados y una acción «Salir».
- Tocar una tarjeta alterna su selección sin abrirla ni cambiar su estado de tarea.
- La selección se distingue mediante borde y marca separados del control de estado.
- Durante el modo, tocar el control de estado selecciona la tarjeta; no cambia Pendiente/Por verificar/Completada.
- El arrastre queda desactivado mientras se seleccionan elementos.
- La barra inferior muestra temporalmente las acciones de grupo disponibles.
- Atrás o «Salir» cierran el modo y recuperan la navegación normal.
- No se añaden controles permanentes a las tarjetas.

Decisión acordada con el usuario. Esta nota no autoriza iniciar la implementación.

## Primera limpieza de menús

- Retirados Subir/Bajar y el cierre Cancelar de los menús exteriores normales; se conservan cancelaciones de formularios y confirmaciones.
- Valores iniciales permanece en Contenido y deja de duplicarse en Opciones.
- Editar se sustituye por Contenido en las tarjetas normales de Núcleo, Capa, Tarea y Nota. Los editores compartidos de creación, plantillas y flujos especializados permanecen. Por instrucción posterior, Usar Guardado se conserva intacto y su comprobación se difiere a Android real; no se declara validado ese flujo.
- Núcleos completan la validación de Contenido y su acceso exterior. Contenido incorpora responsables, etiquetas y prioridad mediante paneles de propiedad existentes, sin volver a abrir ProjectDialog. La descripción reutiliza el editor de adjuntos, no un campo de texto simple. El bloqueo AppNotIdleException también se reproduce con ProjectDialog aislado, sin la integración anterior; aún no atribuirlo a los nuevos campos ni declarar identificada su causa final.
- Historial registra creación, completado/pago y reapertura; no es un historial general de edición.
- Historial se conserva. Por decisión posterior del usuario se retiran de la interfaz Guardar como plantilla y Usar Guardado, conservando sus datos y compatibilidad histórica.
- Recurrencias conserva el administrador global y el del Núcleo. La frecuencia propia se configura al crear una tarea; editar una ocurrencia existente no modifica su regla. Contenido ofrece el acceso a la regla existente (el mismo administrador ya utilizado por el icono de recurrencia) o indica Sin recurrencia. No se añade edición individual de frecuencia ni se elimina ningún administrador.
- Plantillas: retirados los accesos Usar Guardado y Guardar como plantilla tanto de los editores de creación/edición como de Contenido y menús de tarjeta. Se conserva el almacenamiento, la compatibilidad con backups y las tarjetas existentes; no se modifican ni eliminan datos.
- Decisión de prioridad: Contenido ya confirma/cancela sus demás propiedades; la prioridad de tareas normales usa ahora el mismo panel por propiedad y sus confirmaciones de grupo. No hay guardado inmediato. Se conserva el flujo especializado de Sprint, fuera del alcance.
- Estados de tareas se encuentra también en Contenido y conserva el submenú existente, herencia, excepciones y confirmación de conversión. Valores iniciales sigue solo en Contenido.
- Selección múltiple y agrupación de acciones siguen pendientes.

## Equivalencia de controles y accesos — menús normales unificados

| Tipo | Controles del editor existente cubiertos en Contenido | Diferencias pendientes antes de retirar Editar |
|---|---|---|
| Núcleo | Título con límite de Núcleo; descripción, formato e imágenes; responsables; catálogo de etiquetas; prioridad; tecnologías ordenadas. Conserva planificación, fotografía, valores iniciales y estados. | Ninguna diferencia funcional identificada; validado el acceso exterior. Revisión visual final en teléfono pendiente. |
| Capa | Título, descripción y herramientas/adjuntos, responsables, etiquetas, tecnologías y Usar Guardado. Conserva valores iniciales y estados. | Acceso exterior a Contenido implementado y comprobado. Plantillas retiradas de la interfaz, sin borrar sus datos. |
| Tarea | Lo anterior, prioridad confirmada, fechas de inicio/vencimiento, pago y confirmación de retirada, estados y acceso a su regla recurrente existente. | Acceso exterior a Contenido implementado y comprobado. Plantillas retiradas de la interfaz, sin borrar sus datos. |
| Nota | Título, descripción y herramientas/adjuntos, responsables, etiquetas, tecnologías y Usar Guardado. | Acceso exterior a Contenido implementado y comprobado. Plantillas retiradas de la interfaz, sin borrar sus datos. |

- Se compararon ProjectDialog y NodeDialog directamente. Tipo, creación por lotes y frecuencia recurrente son controles de creación; no son propiedades editables de una tarjeta existente. Los editores de creación, planificación, Metro y plantillas permanecen.
- Planificación de Núcleo y fechas de tarea son funciones distintas y conservan sus controles propios. El pago conserva importe, moneda y validaciones; el estado real no se sustituye por una plantilla.
- La edición por propiedad conserva borradores ante errores y comprobaciones del valor original. Un cambio de responsables/etiquetas/prioridad de Núcleo reutiliza el escritor y la transacción existentes sin reemplazar el resto de metadatos.
- Los accesos exteriores normales de Núcleo/Capa/Tarea/Nota muestran Contenido y abren sus propiedades en el contexto correcto. Se conserva Editar en los flujos especializados, sin retirar sus capacidades. La equivalencia de controles se revisó contra el editor existente; Usar Guardado no se declara validado y mantiene su implementación anterior.

### Registro histórico: cierre anterior por límite de créditos

No se declara finalizada la equivalencia ni se retira Editar de ningún tipo. No se iniciarán más compilaciones en esta pasada.

- Compilación de aplicación y pruebas: correcta. La ejecución focalizada terminó con 1 prueba aprobada y 4 fallidas.
- Aprobada en la última ejecución: descripción de Núcleo, cancelación, guardado y reapertura. En ejecuciones anteriores aprobaron responsables de Núcleo y prioridad de tarea con cancelación/confirmación, sin alterar completado, fechas ni pago.
- Prioridad de Núcleo: guardado/cancelación/reapertura alcanzados; falla la comprobación de desbordamiento con fuente 1,6 incluso con altura mínima de 48 dp. Pendiente aislar la restricción real, sin retirar la aserción.
- Etiquetas de Núcleo: selección, guardado y selección restaurada alcanzados; al terminar la prueba busca Cancelar aunque el panel sin cambios ofrece Cerrar. Pendiente corregir esa expectativa y ejecutar solo ese caso.
- Adjuntos de Núcleo: la preparación de prueba ya utiliza la sincronización de directorios del fixture existente. Ahora falla al consultar compose.activity desde el callback de transacción en IO (Main looper can only be controlled from its thread in PAUSED mode). Capturar la aplicación/repositorio en el hilo de prueba antes del callback; todavía no se validó el recorrido visual.
- ProjectDialog aislado: todas las cargas terminan (propiedades, adjuntos y tecnologías true); Recomposer no tiene trabajo pendiente, pero se registran aproximadamente 16.400 cambios de coordinadores de layout y TextLayoutResultProxy/TextLayoutResult en 60 s. Se confirma un ciclo de medida/layout en esta prueba, no una espera de carga de metadatos. Falta aislar el componente y confirmar su alcance real; no se ha modificado el editor compartido para ocultar el problema.
- Usar Guardado en Capa/Tarea/Nota: los tres casos anteriores quedaron bloqueados por AppNotIdle al abrir la biblioteca. No se repitieron en la última tanda. Pendiente aislar ese bloqueo y comprobar la carga de la asociación original de tecnologías antes de aplicar la plantilla, además de cancelación, confirmación y reapertura.
- La prueba nueva de título de Núcleo aún no se ejecutó. El acceso exterior Contenido y la retirada de Editar permanecen pendientes hasta completar estas verificaciones.
- Revisar en teléfono fuentes ampliadas y los selectores/adjuntos una vez completadas las pruebas. No se generó APK, no se cambió versión ni se modificaron esquemas, repositorios o formatos de backup durante esta fase.

### Reanudación: resultados actuales

- Aprobadas individualmente: etiquetas de Núcleo (guardar/reabrir/cerrar); título (cancelar, validar, guardar/reabrir con fuente ampliada); adjuntos (cancelar retirada, guardar retirada y conservar referencia compartida); prioridad (cancelar/guardar/reabrir, sin desbordamiento con fuente 1,6); acceso exterior (elemento correcto, Contenido seleccionado, título y regreso a lista sin escribir datos). Responsables y descripción ya habían aprobado y no se repitieron.
- El recorte de prioridad era horizontal: el chip se ampliaba, pero su texto conservaba un ancho intrínseco insuficiente. Se ajustó el ancho del propio texto; se mantuvo la aserción de ausencia de desbordamiento y las áreas de 48 dp.
- El fallo de adjuntos era del fixture: se capturó la aplicación antes de entrar en el callback de IO. El cierre de etiquetas sin modificaciones es Cerrar, y se corrigió únicamente la expectativa de la prueba.
- Núcleos normales: sustituido Editar por Contenido en la lista y resultados del calendario, con navegación existente y consumo de la solicitud al entrar. Se conserva el editor compartido para creación y flujos que todavía lo necesitan. No se modificaron arrastre, selector Ver, Sprint ni selección múltiple.
- Usar Guardado espera la carga del catálogo/asociaciones de tecnologías antes de abrir el flujo desde Contenido, para conservar su comparación original al aplicar una plantilla.
- Sigue bloqueada la prueba de Usar Guardado de Tarea por AppNotIdle; no se repitieron los casos equivalentes de Capa y Nota. También sigue fallando el ProjectDialog aislado. Un experimento que evitaba publicar estado reactivo desde onTextLayout no resolvió ninguno de esos bloqueos y fue retirado; no queda un cambio especulativo en AttachmentDescriptionField. La causa final no está aislada y no se declaran esos tipos equivalentes.
- Compilaciones correctas y prueba del acceso exterior aprobada. No se ejecutaron suites generales, ni se generaron APK, cambios de versión, firmas, commits o push.

Pendiente: aislar una sola reproducción del bloqueo de biblioteca/editor, validar selección/aplicación/cancelación/guardado/reapertura y tecnologías de Usar Guardado; solo entonces retirar Editar de Capa/Tarea/Nota y habilitar su acceso exterior a Contenido. En teléfono revisar fuentes ampliadas, controles y selectores de Núcleo.

### Aislamiento exclusivo de Usar Guardado

No se modificó producción, Núcleos, persistencia, esquemas ni dependencias en esta investigación. Editar permanece en Capa/Tarea/Nota.

Se añadieron y ejecutaron únicamente tres reproducciones focalizadas, en dos ejecuciones secuenciales:

1. `nodeEditorAloneRendersAndClosesWithoutSaving`: APROBADA. El editor existente aislado permite editar y cerrar, y conserva el valor almacenado sin guardar.
2. `savedLibraryAloneLoadsAndClosesWithoutChangingTheDraft`: BLOQUEADA por AppNotIdle. La biblioteca reproduce el fallo sin el editor ni navegación. Por tanto, el anidamiento de ventanas y el formulario de la tarjeta no son condiciones necesarias para reproducirlo.
3. `materialSearchDialogWithoutLibraryOrSelectorsSettles`: BLOQUEADA por AppNotIdle tras 60 s. Solo utiliza AlertDialog + OutlinedTextField y un botón, sin repositorios, plantillas, listas, selectores ni integración de Contenido. Reproduce el fallo sin ejecutar lógica de biblioteca.

Conclusión verificada: el bloqueo se reproduce en la combinación mínima de diálogo Material y campo de texto del entorno de pruebas actual (Robolectric 4.17, SDK 28, renderizado nativo). La compilación no presenta errores; las ejecuciones fallan por sincronización/renderizado, no por compilación. No se ha aislado el defecto interno del componente/runtime ni demostrado que ocurra en Android real, por lo que no se atribuye definitivamente a Robolectric ni a la aplicación. Tampoco hay evidencia para modificar recuperación, selección, aplicación o persistencia de las plantillas.

Impedimento concreto: las consultas de interfaz de Compose esperan un estado inactivo que ese diálogo mínimo no alcanza; no permiten completar las comprobaciones de selección, aplicación, cancelación, guardado y reapertura. Cambiar dependencias, presentación del diálogo o la sincronización de la prueba sin identificar el defecto sería especulativo. No se elevaron tiempos de espera ni se desactivaron aserciones para hacer pasar los casos.

Pendiente: resolver/confirmar la reproducción mínima en un runtime Android independiente antes de elegir una corrección; después ejecutar únicamente los tres recorridos existentes de Usar Guardado para Capa/Tarea/Nota, incluida conservación de valores y tecnologías. Las cinco pruebas aprobadas de Núcleos no se repitieron. No se declaró equivalencia completa ni se retiró Editar de esos tipos.

Registros locales: `/tmp/arachnode-saved-isolation.log` y `/tmp/arachnode-saved-material-isolation.log`.

### Unificación efectiva de menús de Capa/Tarea/Nota

Esta sección actualiza las decisiones históricas que conservaban Editar a la espera de investigar Usar Guardado. El usuario ha indicado detener esa investigación, conservar la función y continuar la unificación de menús.

- Las tarjetas normales sustituyen la acción Editar por Contenido. Se reutiliza exactamente la navegación de apertura de la tarjeta; una solicitud dirigida al identificador del elemento despliega el NodeBottomModule existente en Contenido. No se añade otro editor ni se duplican paneles.
- La tarjeta compartida de resultados del calendario utiliza la misma solicitud para tareas/notas normales. Los contextos especializados mantienen su editor cuando no utilizan el módulo normal; Sprint y las tarjetas identificadas como herramientas/roles de planificación conservan su acceso existente.
- Contenido conserva título, descripción y herramientas/adjuntos, responsables, etiquetas, tecnologías y Usar Guardado; las tareas además conservan prioridad, fechas, pago, estados y acceso a su regla recurrente existente. Capas conservan valores iniciales y estados. No se añaden propiedades nuevas ni se cambian sus escritores.
- Los menús interiores normales ya no tenían una acción Editar separada. La excepción de edición específica de Kanban permanece porque no utiliza el encabezado/contexto normal. No se suprimió código compartido de creación, plantillas ni flujos específicos.
- No se tocó Usar Guardado ni se investigó su reproducción mínima. Su validación de selección/aplicación/persistencia continúa pendiente de Android real. No se declara equivalencia funcional comprobada de ese flujo.
- Única comprobación nueva ejecutada: `ordinaryLayerTaskAndNoteMenusOpenTheirContentAndSaveTheCorrectTitle`, APROBADA. En un recorrido comprueba los tres tipos: ausencia de Editar en su menú normal, apertura de Contenido del destino correcto, presencia de Usar Guardado sin abrirlo, edición y guardado del título, regreso a la lista y conservación de tipo, descripción, padre y completado. Compilación correcta. No se ejecutaron suites ni pruebas anteriores.
- Archivos de interfaz afectados: NodeComponents.kt, NodeBottomModule.kt, ProjectScreen.kt y ContextCalendarTaskCard.kt. Se añadió la comprobación a ContentEquivalenceUiTest.kt. Núcleos no cambia de comportamiento.
- Sin cambios de persistencia, dependencias, versión, APK, firma, commit ni push. Pendiente en Android real: Usar Guardado y revisión visual de los accesos compartidos del calendario y contextos especializados; estos últimos no forman parte de la prueba nueva.

### Decisión vigente: retirada de plantillas de la interfaz

El usuario autorizó retirar Usar Guardado y Guardar como plantilla, sin desmantelar el almacenamiento. Se eliminaron sus accesos de NodeDialog, Contenido y el menú de NodeCard, junto con las solicitudes de apertura que solo utilizaban esos botones. Se conservan los repositorios, modelos, formato de plantillas, exportación/restauración de backups y tarjetas creadas previamente. Los diálogos internos sin acceso de producción y sus comprobaciones de compatibilidad se mantienen.

Se retiraron únicamente las pruebas de accesos que ya no existen y se actualizó la prueba de menús para esperar su ausencia. No se retomó la investigación de renderizado. Las notas anteriores sobre validar Usar Guardado en Android son históricas y ya no constituyen trabajo pendiente de esta función retirada.

Validación de esta retirada: búsqueda de referencias de producción confirma que no quedan accesos a ambos diálogos ni solicitudes automáticas; git diff --check correcto. No se ejecutaron compilaciones ni pruebas en esta retirada. No se cambió persistencia, versión, APK, firma, commit ni push.

### Nombres compactos de propiedades

Renombrados los accesos Editar tecnologías → Tecnologías y Estados de tareas… → Estados, incluidos los filtros de composición de Contenido/Opciones y las expectativas de pruebas. Cambiar estado conserva su nombre y comportamiento. Solo cambian etiquetas; no se ejecutaron compilaciones ni pruebas para este ajuste textual.

## Compartir y exportar — implementación actual

- Entrada única en ⋮ exterior y Opciones interior; el mismo panel Modular organiza
  Copiar, Imagen, Documento y Datos. Retiradas las entradas individuales anteriores.
- Núcleo: copia individual/con descendientes, PDF, DOCX, Markdown y JSON.
- Capa: lo anterior más compartir/guardar PNG mediante la implementación existente.
- Tarea/Nota: copia individual, compartir/guardar PNG, Markdown y JSON.
- PDF/DOCX conservan su configurador y alcance anteriores. Solo se elige el formato
  inicial desde el panel. La elección Solo elemento/Con descendientes del panel
  corresponde exclusivamente a Markdown/JSON.
- Copia conserva contenido, selector existente de descendientes y portapapeles.
  PNG conserva resolución, nombre, caché cifrada y mecanismo Android anteriores.
- JSON parcial autorizado explícitamente por el usuario: mismo serializador de
  campos del backup dentro de un contenedor identificado; no es un respaldo
  restaurable. Adjuntos en ZIP con rutas relativas, sin preferencias privadas.
- No se recuperan plantillas ni se cambian otros menús, datos o versiones.

Validación de esta implementación: compilación correcta y 6 pruebas focalizadas
aprobadas (4 de exportación, 1 de panel con fuente 1,5× y 1 de backup
completo). Incluyen alcance individual/descendientes, ausencia de
elementos ajenos, estados/fechas/etiquetas/código, JSON no restaurable como backup,
ZIP con audio y referencias relativas, y rechazo de corrupción. Pendiente en
teléfono: Sharesheet/SAF real, cancelación y proveedores que rechacen limpieza,
previsualización PDF/DOCX, plegado, temas y lectura de archivos en otras apps.

## Buscador unificado — consultas y accesos

Auditoría de los recorridos existentes:
- Buscar: ElementSearch indexa título/descripción de Núcleos, Capas, Tareas, Notas
  y Borradores; apertura directa y FavoriteToggle con AccessRepository.
- Favoritos: marcas de AccessState, sin gestión adicional; integrado completamente.
- Atención: tareas hoja no completadas y con proyecto, según AttentionPolicy
  (vencimientos/prioridad reales). La vista detallada conserva agrupación por
  motivo y filtros de fecha, Persona, etiquetas y prioridad.
- Obligaciones: FinancialSnapshot consulta tareas hoja con pago y proyecto,
  pendientes/completadas; mantiene períodos, Personas, totales por moneda e informe
  PNG para guardar/compartir. Sigue necesitando su pantalla especializada.
- Recurrencias: gestor de reglas y futuras ocurrencias, responsables, destino,
  pausa/reanudación, finalización y eliminación confirmada. El filtro del buscador
  localiza tareas con ocurrencias registradas; no crea resultados ficticios para
  reglas que aún no generaron tareas. El gestor conserva todas esas operaciones.
- Recientes/Frecuentes: mismo historial y contadores de AccessRepository.
  Etiquetas: mismas asociaciones TagState de Núcleos/nodos, sin nueva gestión.

Implementación:
- Todos los filtros activos se intersectan (AND), también con título/descripción.
  Recientes y Frecuentes son modos independientes y se pueden limitar con filtros.
- Inicio sin texto ni filtros muestra recientes; al escribir o activar criterios
  consulta el índice completo. Todos y Restablecer consultan todos explícitamente.
- Campo y controles abajo, panel Modular desplazable y plegable; los resultados
  permanecen desplazables. El plegado retira el foco y cierra el teclado.
- Filas compactas con tipo, ubicación, favorito y título de hasta dos líneas.
  Para Núcleos no se repite su título como ubicación.
- Texto, criterios, modo y lista permanecen en AppRoot durante la sesión.
  El estado del panel se conserva al abrir y regresar de un resultado.
- Se retiran Favoritos y Atención de Inicio. Atención detallada sigue accesible
  al activar su filtro; se puede volver al buscador conservando la consulta.
- Se conservan Obligaciones y Recurrencias en Organizar, además de accesos desde
  sus filtros. Sus pantallas/repositorios no se eliminan ni se reconstruyen.
- Sin nuevas preferencias, migraciones, formatos de backup ni cargas de adjuntos.
  Los criterios son exclusivamente estado transitorio de navegación.

Validación del buscador: compilación correcta y cinco casos focalizados aprobados:
tres de UnifiedSearchTest, apertura real y conservación de Favoritos en
ElementAccessUiTest, y UnifiedSearchUiTest para filtros, Atención por prioridad y
vencimiento, exclusión de completadas, acceso detallado, plegado y restablecimiento.
La primera ejecución aprobó cuatro casos; solo se repitió el del panel después
de corregir su expectativa sobre la visibilidad del primer elemento de LazyColumn
(la lista conserva la posición por clave). Sin cambios de producción para ello.
Logs: /tmp/arachnode-unified-search.log y /tmp/arachnode-unified-search-panel.log.

Pendiente en teléfono: teclado real, fuente ampliada, Charcoal/Dark/Green Bash,
retorno desde consultas detalladas y gestores, y pausa/reanudación/edición de
programaciones desde esos accesos. Sus implementaciones existentes se conservan.
No se generó APK, firma, cambio de versión, commit ni push.
