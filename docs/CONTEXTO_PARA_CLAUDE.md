# Arachn0de: contexto funcional para evaluar necesidades y herramientas complementarias

Documento preparado el 25 de octubre de 2026 a partir del código y la documentación del proyecto. Describe el árbol de trabajo actual, no únicamente la última APK instalada. No es una certificación de que todos los flujos se hayan probado físicamente en un teléfono.

## Encargo para quien lea este documento

Estoy desarrollando Arachn0de, una aplicación personal Android. Quiero consejos sobre herramientas, métodos o mejoras que me ayuden a controlar el tiempo, la productividad, los gastos y la toma de decisiones.

Antes de recomendarme otra aplicación o una función, considera lo que ya tengo. No propongas reconstruir módulos equivalentes ni añadir funciones solo por acumularlas. Detecta necesidades realmente no cubiertas, falta de conexiones entre funciones, información que no estoy midiendo y complejidad que podría simplificarse.

Distingue en tu respuesta:

1. Lo que Arachn0de ya resuelve.
2. Lo que podría resolverse usando mejor lo existente.
3. Lo que requiere una mejora pequeña o conectar componentes existentes.
4. Lo que justifica una herramienta externa o un módulo nuevo.
5. Las preguntas sobre mis hábitos y necesidades que necesitas aclarar antes de recomendar.

No asumas que una vista de calendario equivale a planificación automática, que Pomodoro mide el tiempo por tarea, que tener cuentas equivale a tener presupuestos ni que registrar personas equivale a colaboración multiusuario.

## 1. Qué tipo de aplicación es

Arachn0de combina organización jerárquica, tareas, notas, planificación de proyectos, pagos, personas y herramientas auxiliares.

- Es Android, local y diseñada para funcionar sin Internet.
- No necesita cuenta de usuario en un servidor ni tiene backend propio de colaboración.
- Los datos privados se guardan cifrados; tiene seguridad de acceso y respaldos/restauración.
- Internet no es necesario para las operaciones de organización y finanzas descritas. La comprobación/descarga autorizada de actualizaciones es una función distinta.
- Su diseño favorece acciones accesibles con una mano, paneles inferiores y componentes compartidos.
- Tiene temas y skins; la presentación puede cambiar sin crear versiones diferentes de las tareas o del dinero.

## 2. Modelo principal: Núcleos, Capas, Tareas y Notas

La estructura general puede representarse así:

```text
Núcleo: Hogar
├── Capa: Servicios
│   ├── Tarea de pago: Plan de teléfono
│   └── Tarea: Revisar contrato
├── Capa: Mantenimiento
│   └── Capa: Cocina
│       └── Tarea: Reparar una bisagra
└── Nota: Medidas y materiales
```

**Núcleo:** contenedor principal. Puede representar un proyecto, área de vida, cliente, tema u otro ámbito. Tiene nombre, descripción y propiedades asociadas.

**Capa:** contenedor explícito dentro de un Núcleo. En la jerarquía normal puede contener otras Capas y elementos. Permite subdividir un proyecto en niveles; no es solamente una etiqueta.

**Tarea:** acción que puede completarse. El modelo también permite estructuras de tareas con hijos, con restricciones: el checkbox y el estado operativo de completado corresponden a las acciones finales, no a contar nuevamente sus contenedores como trabajo independiente.

**Nota:** información o referencia. No debe confundirse con una tarea pendiente. Hay conversión entre Tarea y Nota cuando las relaciones y el contexto lo permiten.

No se permiten todas las combinaciones indiscriminadamente: por ejemplo, una tarea con pago no puede convertirse libremente en contenedor de subtareas. Existen validaciones para evitar ciclos, relaciones inválidas y movimientos que romperían contextos de gestión.

El progreso de Núcleos y Capas se calcula a partir de sus elementos de trabajo, en vez de mantener porcentajes manuales independientes que puedan contradecir sus tareas.

## 3. Qué puede tener una tarea o su contexto

Según el tipo y el ámbito:

- Título y descripción.
- Estado de tarea.
- Prioridad.
- Fecha de inicio y vencimiento, ambas opcionales.
- Personas responsables; una tarea normal puede tener varias.
- Etiquetas y filtros; la selección habitual de etiqueta es única opcional, con compatibilidad de datos anteriores.
- Adjuntos, imágenes y contenido de referencia.
- Relaciones con tecnologías del catálogo.
- Fijado/favoritos según el elemento.
- Historial de cambios y eventos.
- Recurrencia, si corresponde.
- Cantidad y moneda cuando representa un pago.
- Fase de trabajo cuando participa en un Sprint.

Hay creación múltiple, grupos de creación, orden manual, búsqueda y operaciones de movimiento/copia. Los traslados entre Núcleos y Capas conservan identidades y relaciones cuando son compatibles; se bloquean si romperían referencias de gestión o recurrencia. Algunas conversiones permiten transformar un Núcleo en Capa y viceversa con controles de integridad.

**Borradores y captura rápida:** permiten registrar contenido sin decidir inmediatamente su ubicación definitiva y moverlo después. La recepción externa activa es textual; no debe suponerse una captura universal de cualquier formato o una importación bancaria.

## 4. Estados y productividad básica

Hay configuración heredable de tareas con dos o tres estados:

- Pendiente.
- Por verificar, cuando el ámbito utiliza tres estados.
- Completada.

La herencia permite configurar un ámbito sin editar individualmente cada tarea; también hay reglas para modificaciones explícitas y cambios entre dos/tres estados.

Esto es distinto de las cinco fases de Sprint descritas más adelante. No son dos listas equivalentes de estados que deban mezclarse.

Hay filtros, indicadores, recuentos de pendientes y prioridad alta. Existe también lógica de Atención para resumir señales de tareas y propagarlas por la jerarquía; no debe confundirse con una recomendación inteligente que decida automáticamente qué tarea conviene ejecutar. Los indicadores de personas se actualizan desde sus relaciones reales, incluidas las tareas recurrentes. Las tarjetas mantienen acceso a información del elemento y su contexto.

Existe un sonido opcional al completar tareas, con configuración global y personalización por skin.

## 5. Fechas, vencimientos y calendario

Las tareas admiten inicio y vencimiento sin exigir ambos. Si existen ambos, el vencimiento no puede preceder al inicio.

El estado temporal es derivado de las fechas y del momento actual: programada, activa, próxima a vencer, vencida o completada. La ventana de proximidad de la política actual es de 24 horas. Este estado temporal no sustituye el estado Pendiente/Por verificar/Completada.

El calendario permite consultar y filtrar por fechas, Hoy y otras escalas/ámbitos disponibles. Hoy funciona como selección que puede activarse y desactivarse. Hay calendario contextual en la organización y una vista propia.

También existen eventos propios, con fecha, hora opcional, descripción, color y personas asociadas. Los cumpleaños de Personas se proyectan en el calendario; pueden existir con día/mes y sin año.

**No asumir:** sincronización bidireccional con Google Calendar/Outlook, reserva automática de horas libres, reprogramación inteligente o que todo vencimiento produzca una alarma. Una fecha, una visualización y una notificación son capacidades distintas.

## 6. Recurrencias

Existen recurrencias diarias, semanales, mensuales y anuales, con intervalos y controles de vigencia/fin según la regla.

La regla recurrente y sus ocurrencias tienen identidad propia. Las ocurrencias son tareas concretas; no se trata simplemente de cambiar la fecha de una única tarea y perder el pasado.

- Las reglas pueden estar activas, pausadas o finalizadas.
- La generación evita duplicados y contempla tareas atrasadas.
- Las fechas se calculan desde el ancla original; un día 31 mensual no deriva permanentemente al 28 después de febrero.
- Se conservan responsables, contexto e historial según las reglas del módulo.
- Una tarea de pago puede ser recurrente: por ejemplo, el pago mensual del teléfono.

Una recurrencia es una programación repetida. No implica que exista un módulo especializado de hábitos con rachas, adherencia, recaídas, objetivos y análisis conductual.

## 7. Planificación y gestión de proyectos

Un Núcleo normal puede utilizar gestión de proyecto opcional, con metodología Ágil, Tradicional o Híbrida. Activar esta gestión no crea una copia del proyecto ni transforma automáticamente todas sus tareas.

Hay componentes para:

- Acta: propósito, alcance, exclusiones, entregables y condiciones de éxito.
- Objetivos.
- Criterios de aceptación y su validación.
- Épicas e historias mediante roles de Capas.
- Backlog y orden propio.
- Relaciones entre objetivos, épicas, historias y tareas originales, sin trasladarlas obligatoriamente.
- Participantes y responsabilidades organizativas.

Estos participantes y roles son registros locales de organización. No equivalen a invitaciones, cuentas remotas, permisos compartidos ni trabajo simultáneo con otras personas.

### Sprint y Kanban

Las Capas Sprint y sus tareas utilizan las fases:

1. No planificada.
2. Planificada.
3. Haciendo.
4. Terminada.
5. Validada.

Existe una vista Kanban y cálculo de progreso de Sprint. Una tarea puede vincularse explícitamente desde otra ubicación sin duplicarla.

La planificación de iteraciones registra alcance inicial, incorporaciones posteriores y cierres con una instantánea histórica. Reabrir genera un nuevo ciclo en vez de reescribir el cierre anterior.

### Gantt

Existe un Gantt que reutiliza jerarquía, fechas, responsables y progresos, con anotaciones y exportación gráfica. No debe interpretarse como un planificador profesional completo: no se han incorporado dependencias, hitos ni cálculo de ruta crítica en la implementación documentada.

## 8. Pagos como tareas

Una tarea puede representar una cantidad pendiente de pago. Conserva su ubicación original en Núcleos/Capas y puede tener vencimiento, recurrencia y responsable.

Hay una vista agregada de Pagos y presentación de importes pendientes. No hace falta mover una tarea al Banco para pagarla.

Es importante diferenciar:

- Estado del trabajo/tarea.
- Cantidad económica original.
- Cantidad abonada.
- Saldo pendiente.
- Vencimiento de la obligación.

El sistema integra pagos parciales. Las tarjetas con abonos muestran el pendiente, y el detalle conserva información e historial. Existen informes de pagos y reportes jerárquicos en formatos como PNG, PDF y DOCX, según el tipo de informe.

No se debe considerar dinero pagado solo porque una tarjeta tenga una cantidad configurada o una fecha de vencimiento.

## 9. Banco: dinero registrado y pagos

Banco es un **tipo especial de Núcleo**, no un árbol financiero independiente ni una sección separada que duplique las tareas.

```text
Núcleo Banco
├── Cuenta personal — Persona A
├── Caja física — Persona A
└── Otra cuenta — Persona B
```

- Las cuentas son Capas directamente bajo el Núcleo Banco.
- No hay subcuentas jerárquicas ni Capas anidadas dentro de cuentas.
- Una persona puede tener varias cuentas.
- Los saldos se calculan desde operaciones; no son un número que se cambia sin historial.

Acciones principales:

**Ingresar dinero:** aumenta el saldo registrado de una cuenta.

**Retirar dinero:** disminuye el saldo registrado; no permite superar lo disponible.

**Pagar:** selecciona quién paga, cantidad y tareas pendientes. Tiene dos modalidades:

- **Desde saldo:** descuenta dinero de una cuenta.
- **Directamente:** registra el pago sin descontar saldo de una cuenta.

Banco opera actualmente en CLP; que una tarea pueda expresar una cantidad y moneda no implica conversión de divisas ni una contabilidad multimoneda completa.

Los nombres de las operaciones son opcionales. El pago reutiliza las tarjetas reales de tareas, con selección, distribución sugerida y edición manual. Se valida el total, el pendiente de cada tarea y el saldo, cuando corresponde.

Hay historial, anulación/corrección mediante los flujos existentes, prevención de duplicados, revalidación antes de confirmar y transacciones con rollback. Las transferencias históricas se conservan, aunque ya no se ofrecen como acción principal.

**Restricción financiera actual:** solo pueden pagarse tareas con exactamente un responsable. Las tareas sin responsable o con varios quedan excluidas de pagos ejecutables. Las compartidas no se dividen automáticamente ni cambian sus responsables; se identifican como pago compartido no disponible.

Se añadió eliminación confirmada de cuentas y de su historial asociado. Preserva el Banco, las personas y las tareas; recalcula pendientes. Protege operaciones que dejarían otra cuenta negativa y advierte si la cuenta tiene datos de Ahorro asociados.

**Límite esencial:** Banco es un registro local, no una conexión con el banco real ni una pasarela de pago. Confirmar un movimiento no ejecuta una transferencia electrónica.

## 10. Control de gastos: qué hay y qué no conviene asumir

Ya existen importes, deudas/pagos pendientes, vencimientos, recurrencias, responsables, cuentas, ingresos, retiros, pagos parciales, saldos e historial.

Eso cubre registro y seguimiento de obligaciones. No demuestra que exista un sistema completo de finanzas personales.

No se identifica como módulo especializado implementado:

- Presupuesto mensual por categorías y comparación planificado/real.
- Límites de gasto y alertas de desvío presupuestario.
- Clasificación analítica completa de gastos.
- Proyección de flujo de caja y escenarios financieros.
- Conciliación con extractos bancarios importados.
- Seguimiento integral de tarjetas de crédito, cortes, cuotas, intereses y comisiones.
- Patrimonio neto, inversiones o contabilidad tributaria.
- Reparto de deudas entre varias personas.

Estos son ámbitos para evaluar, no una recomendación de implementarlos todos. Las etiquetas o recurrencias existentes podrían cubrir parte de una necesidad sin justificar otro módulo.

La detección de compras CMR mediante notificaciones de otras aplicaciones fue retirada. No está activa ni debe contarse como función disponible. No hay lectura de SMS, correo o conexión bancaria para reconstruir compras.

## 11. Tiempo y concentración

**Pomodoro:** temporizador global con trabajo, descansos cortos/largos, ciclos, pausa/reanudación, sonidos, configuración y avisos propios. Puede seguir accesible al navegar.

Es global: no está vinculado a una tarea/Núcleo como registro automático de tiempo dedicado. No debe venderse como timesheet ni como comparación de estimación frente a tiempo real por tarea.

**Metro:** planificador/seguimiento local de viajes, rutas y etapas, con avisos y correcciones de posición. Tiene aprendizaje de tiempos a partir de confirmaciones del usuario, estadísticas y separación entre estimaciones y observaciones. Es una herramienta de desplazamiento; no mide la productividad del trabajo.

Tareas, calendario, fechas, prioridades, Kanban, Sprint y Gantt aportan otras piezas de planificación temporal. No implican por sí solas balance de carga diaria, agenda automática ni análisis de dónde se está yendo el tiempo real.

## 12. Personas y Antisocial

Antisocial es un espacio privado de perfiles y relaciones, no una red social conectada a Internet.

Sus secciones actuales son Inicio, Personas, Tecnologías y Proyectos. Incluye:

- Identidad/perfil principal local.
- Personas, relaciones y favoritos.
- Nombre, biografía, cumpleaños, teléfono, correo y enlaces.
- Avatar, banner y publicaciones/recuerdos con fotografías.
- Asociaciones de personas con publicaciones.
- Relación con tecnologías y proyectos.

Los cumpleaños admiten año opcional; no se calcula edad si falta. Las imágenes de avatar y publicaciones tienen flujos privados de cámara/galería y eliminación de metadatos sensibles antes del almacenamiento cifrado.

Las personas se reutilizan como responsables y titulares, evitando crear otro catálogo separado para Banco o proyectos. Existen campos históricos que se preservan en los datos pero ya no se muestran en el perfil simplificado; no deben presentarse como capacidades activas de la interfaz.

## 13. Herramientas visuales, documentación y organización

- Diagramas: documentos visuales editables, formas, conexiones, anotaciones y exportaciones según el flujo.
- Wireframe: dibujos/moldes de pantallas para organizar y editar ideas de interfaz.
- Tecnologías: catálogo con información, iconos y vínculos con personas/proyectos/elementos.
- Etiquetas, fijado/favoritos, búsqueda y filtros.
- Descripciones y notas como documentación contextual.
- Adjuntos y herramientas de anotación de imágenes.
- Grabación/reproducción de audio y exportación de adjuntos.
- Compartir/exportar texto, contexto estructurado o tarjetas como imagen, según la función.

Tener diagramas y notas permite documentar una decisión manualmente. No equivale a un motor especializado de decisiones multicriterio, alternativas, pesos, incertidumbre o revisión posterior de resultados.

## 14. Juegos y Ahorro

Hay un juego tipo Carrera y una Tragaperras independiente de la jerarquía de trabajo.

La Tragaperras conserva:

- **Tradicional:** créditos ficticios, sin valor monetario real.
- **Tareas:** selección aleatoria de una tarea disponible dentro de un Núcleo; no sustituye un sistema de priorización ni afirma elegir la mejor tarea.
- **Ahorro:** caja física financiada por el propietario e integrada con una cuenta de Banco.

En Ahorro:

- La cuenta vinculada representa la caja. Su saldo existente queda disponible para premios sin registrar otro ingreso.
- CAJA abre directamente el depósito: $1.000 compran 5 tiradas; $40.000 compran 200. DEPOSITAR registra un único ingreso en la cuenta y crea los créditos. Las tiradas no generan movimientos bancarios.
- Las recargas de reserva son capital adicional, no ahorro generado ni ganancia.
- Antes de girar, el fondo libre debe cubrir el premio máximo de la tabla aplicable.
- Los premios pendientes se reservan hasta pulsar RETIRAR, que registra un único egreso. Cerrar el panel conserva el premio pendiente. Las decisiones históricas de conservación siguen siendo compatibles.
- Existen libro, historial, estadísticas, recuperación y prevención de cobros/depósitos duplicados.
- El dinero protegido y los premios pendientes no pueden respaldar otra tirada.
- Los retiros externos que dejan falta de respaldo bloquean el juego.

La nueva fachada selvática tiene ocho símbolos y premios de 1.000, 2.000, 3.000, 4.000, 5.000, 7.000, 10.000 y 20.000 CLP. La tabla financiera predeterminada mantiene RTP exacto del 30 % y conserva las condiciones de créditos históricos. La reserva máxima nueva es de 20.000 CLP.

No es una inversión, una rentabilidad financiera ni una conexión con apuestas externas. El propietario aporta el capital y confirma el manejo físico del dinero. Tampoco hay desbloqueos por niveles de ahorro ni otros modelos de máquinas implementados.

## 15. Privacidad, persistencia y recuperación

Hay bóveda/seguridad de acceso, SQLCipher y mecanismos de cifrado de datos y archivos. La arquitectura contempla separación de sesiones/perfiles de seguridad, protección de capturas y tratamiento de archivos privados.

El sistema de respaldos conserva datos, relaciones y bytes de archivos necesarios; no debe limitarse a exportar rutas del dispositivo. La restauración valida antes de reemplazar y preserva los datos previos ante fallos. Hay compatibilidad histórica y manejo transaccional de relaciones y cambios.

Los archivos confirmados y los temporales tienen ciclos de vida y limpieza controlados; no se barre indiscriminadamente el almacenamiento privado. La eliminación física se separa del commit y respeta referencias/reservas.

No asumir nube, sincronización entre dispositivos, copia remota automática, colaboración en tiempo real ni acceso bancario. El actualizador utiliza autorización del usuario y la identidad de firma oficial; no instala silenciosamente.

## 16. Estado real y límites de esta descripción

La última APK oficial empaquetada es 0.18.0/code33. Después se implementaron cambios locales, incluida eliminación de cuentas, reconocimiento del saldo de la cuenta en Ahorro y la fachada selvática. No todos esos cambios están en esa APK.

La integración selvática y sus compatibilidades tienen compilación y 40 pruebas focalizadas aprobadas. Eso no certifica cada módulo histórico de la aplicación ni sustituye revisión visual, tacto, sonido y comportamiento del fabricante en teléfono real.

Hay documentación histórica de desarrollo que contiene planes y etapas anteriores. No debe interpretarse cada apartado de esa historia como una función actual ni como un pendiente todavía vigente.

## 17. Qué quiero que evalúes

Con este contexto, ayúdame a encontrar baches reales en cuatro áreas:

**Tiempo:** planificación del día, capacidad disponible, tiempo real invertido, estimaciones y desvíos, revisión semanal, distracciones y relación entre calendario/tareas/Pomodoro.

**Productividad:** convertir objetivos en trabajo concreto, limitar trabajo simultáneo, seleccionar qué hacer, detectar tareas estancadas y comprobar resultados sin añadir formularios innecesarios.

**Gastos:** distinguir deudas de gastos reales, presupuesto frente a movimientos, compromisos futuros, recurrencias, caja disponible y necesidades no cubiertas por Banco.

**Decisiones:** registrar alternativas, criterios, coste/beneficio, información faltante, incertidumbre y revisión de lo decidido. No dar por existente un módulo especializado solo porque hay notas y diagramas.

Primero pregúntame lo imprescindible sobre cómo uso hoy la aplicación. Después entrega una tabla con: necesidad, capacidad existente aprovechable, bache concreto, solución mínima, posible herramienta externa y beneficio frente al coste de mantenerla.

Prioriza pocas propuestas con utilidad comprobable, privacidad y bajo mantenimiento. Diferencia una carencia confirmada de una hipótesis que requiere revisar un flujo. No confundas añadir pantallas con mejorar el control real de mi vida o de mis proyectos.
