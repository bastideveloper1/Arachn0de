# Changelog

## 0.25.0 — release oficial local, código 40

- Incluye el estado actual anterior al nuevo rediseño de navegación.
- Simplificación de Tecnologías y herramientas de gestión, conservando datos históricos.
- Plantillas estructurales de Núcleos, Capas, Tareas y Notas con recursos cifrados e instancias independientes.
- Depuración del catálogo de temas y corrección del cierre antes de la contraseña por inicialización circular.
- Compatibilidad histórica preservada; payload de plantilla v2 no legible por versiones antiguas.

## 0.18.0 — release oficial local, código 33

- Tragaperras Ahorro: compras físicas confirmadas, cuenta Banco asociada, créditos persistentes, RTP exacto configurable, reserva máxima, premios pendientes y retiro/conservación.
- Libro cifrado y backup JSON 51 con recuperación de giros, versiones de configuración e integridad de movimientos.
- Incluye las correcciones UX locales: contraste de Banco, explorador de destinos, traslados transaccionales, grabadora y volumen de Tragaperras.
- Recursos históricos, modos Tradicional/Tareas y funcionalidades existentes conservados. Sin acceso a notificaciones ajenas.

## 0.17.1 — release oficial local, código 32

- Retirada de detección automática CMR: sin servicio ni capacidad de acceso a notificaciones ajenas, controles o reconocimiento.
- Compatibilidad histórica de datos y respaldos conservada; Banco, pagos, abonos parciales y demás funcionalidades intactos.
- Permisos de cámara, micrófono, avisos propios y actualizador sin cambios. Misma firma y applicationId oficiales.
- APK release no depurable firmada y verificada, sin acceso a notificaciones ajenas; certificado oficial conservado.

## 0.17.0 — release oficial local, código 31

- Indicadores de personas actualizados en tarjetas y tareas recurrentes sin depender de abrir otras vistas.
- Banco: Núcleos sin indicadores habituales, cuenta y titular combinados, formulario Pagar y tarjetas de selección compactos conservando skins.
- Abonos parciales: pendiente en tarjetas exteriores e información completa en el detalle e historial.
- Conserva los demás cambios locales y recursos históricos. CMR permanece congelado, sin emisores autorizados ni detección real activa.
- APK release no depurable firmada y verificada; misma identidad oficial y certificado que 0.16.0/code30.

## 0.16.0 — release oficial local, código 30

- Primera versión de Banca: Núcleo especial con cuentas de primer nivel, varias cuentas por persona e historial financiero.
- Ingresar dinero, Retirar dinero y Pagar; nombres opcionales, pagos desde saldo/directos, selección de tarjetas originales y reparto automático o manual.
- Validación inmediata y confirmación compacta, sumas exactas, pagos parciales, anulación, idempotencia y rollback; tareas compartidas excluidas de pagos ejecutables.
- Migración aditiva Room 52→53 y backup JSON 49 con historial, asignaciones y compatibilidad anterior.
- Conserva las mejoras de Antisocial, skins y sonidos actuales; Tragaperras Tradicional/Tareas, menú inferior y transparencia estable al parpadear.
- Conserva Carrera y los recursos gráficos/sonoros existentes. Misma identidad oficial de firma y paquete; APK release firmada y verificada.


## 0.15.0 — preparación oficial local, código 29

- Tragaperras independiente: Tradicional y sorteo de Tareas, sonidos de prueba, créditos y premios ficticios de sesión.
- Acceso directo a Tragaperras desde Inicio del menú lateral.
- Botón Hoy del calendario contextual permite seleccionar y quitar la fecha actual.
- Conserva todas las mejoras previas del árbol de trabajo. Firma y artefacto final pendientes de verificación.

## 0.12.0 — preparación oficial local, código 26

- Menú inferior de dos niveles con skin existente y destinos reorganizados.
- Filtros contextuales de Atención/Favoritos y calendario antes de la información de ubicación.
- Respuesta optimista de Favoritos y conservación del tema detrás de la cobertura de privacidad.
- Degradado compartido más suave y con mayor zona de ilustración visible.
- Misma identidad, cifrado y persistencia. Rendimiento real pendiente de revisión en teléfono.
- APK oficial verificada: certificado habitual, ZIP íntegro y SHA-256 fdc1c287ebc1a709450a6c34418c9751f894f2a6aa8e7d7791c1e9d1fd11250a.

## 0.11.0 — preparación oficial local, código 25

- Calendario contextual plegable en Núcleos y Capas, con navegación a las tarjetas originales.
- Optimización de preferencias pequeñas y limpieza de imágenes; se mantiene lectura por bloques para valores grandes.
- Correcciones de tarjetas, Semáforo, superposición del editor y barra de anotaciones.
- Eliminada la línea redundante de estado de tarea; se conserva su casilla funcional.
- Misma identidad de firma, cifrado, Room 50 y backup JSON hasta 43. Firma y entrega pendientes de verificación.

## 0.10.0 — preparación oficial local, código 24

- Diagramas: separación de Guardar, reutilización de configuración animada, colores GIF y refinamiento visual del editor.
- PDF: previsualización a pantalla completa con pinza y regreso al configurador; separación de controles.
- Núcleos: recurrencia compacta y contexto de regreso al listado; se conservan Borradores y mejoras previas.
- Skins: editor interactivo, controles visuales y correcciones de guardado de imágenes grandes.
- Se conservan identidad de firma, cifrado y compatibilidad de datos. Room 50; backup JSON hasta 43.
- Firma, SHA-256 y entrega sujetos a verificación del nuevo artefacto; comprobaciones físicas pendientes.

## 0.9.0 — preparación local, código 23

- Incluye los cambios actuales de Diagramas y el pulido de skins personalizadas.
- Conserva originales cifrados de imágenes de temas y sus bytes en respaldo/restauración.
- Room 48; lector de backup hasta JSON 41, compatible con formatos anteriores.
- Misma identidad y certificado oficial. Firma y entrega pendientes de verificación.
- Correcciones adicionales del PDF aplazadas; sin publicación automática.

## 0.5.1 — corrección local, código 19, firma pendiente

- Pie global compartido por navegación, editores y selectores, a ancho completo.
- Menor separación vertical; Grabación encima de Pomodoro, con divisores de 1 dp.
- Compactación independiente de ambas barras, conservando las sesiones activas.
- Los editores minimizados reservan su espacio sin superponerse a las barras.
- La atenuación por notificaciones no pausa el micrófono; se respetan las interrupciones reales.
- Sin cambios de base de datos, respaldo, identidad ni clave de firma.

## 0.5.0 — preparación local, código 18, pendiente de firma

- Temas Charcoal y Dark seleccionables y persistentes.
- Pomodoro offline con vista completa y barra compacta.
- Calendario con fechas especiales y cumpleaños derivados de Personas, con filtros.
- Mejoras de edición modular, encabezados contextuales y presentación de tarjetas.
- Ajustes de Antisocial y de recepción y exportación de texto compartido.
- Conserva el identificador y la firma oficial; sin publicación.

## 0.4.0 — preparación local, pendiente de firma

- Gestión de proyectos integrada: Acta, Equipo, Objetivos, Backlog, planificación de
  Sprints, Kanban directo, Gantt y demostración explícita con datos locales reales.
- Cuadrículas de Personas de tres columnas con fotos completas en cuadros rectos,
  nombre separado y menú inferior, sin margen dentro de la región fotográfica.
- Previsualizaciones compactas sin descripción; contenido íntegro en detalle/editor.
- Pagos agrupa Este mes, Próximo mes y Total pendiente por moneda, usando el cálculo
  existente y excluyendo pagos completados de esos importes.
- Código Android 12, Room 38 y backup hasta JSON 30. Reutiliza firma oficial existente;
  no modifica la paleta ni añade mecanismos de seguridad. No se ha publicado.


## 0.3.0 — Preparación para distribución manual

Versión Android `0.3.0`, código `9`, posterior al código `8` del build de pruebas. Firma oficial e identificador conservados; APK pendiente de introducir credenciales localmente, sin publicación.

- Formularios y edición directa; tarjetas compactas de proyectos, fotografías y conversión reversible Proyecto ↔ Capa.
- Juego offline ampliado: bots, rutas alternativas, pantanos, cuevas, araña, combate por dados, niebla individual y Ojo de exploración; finalización visual y espera de turnos corregidas.
- Room 23 y backup lógico 15 preservan productividad y partida; lectura de respaldos históricos.
- Incorporados los cinco PNG definitivos del tablero, con transparencia y proporciones originales; la araña se dibuja como entidad independiente.

## 0.2.4 — Beta en preparación

Versión Android `0.2.4`, código `6`. Sin publicación en esta preparación.

- Capas explícitas con cero o más hijos; conservar una Capa al quedar vacía.
- Mover un Proyecto dentro de otro Proyecto o Capa, conservando su árbol y configuración.
- Modo Sprint opcional para tareas directas de una Capa: cinco etapas y vista vertical colapsable.
- Mejoras de dogfooding: selectores de recurrencia, orden combinado, copia compacta de pendientes y guardado de configuración.
- Destinos Move válidos y conversión automática de tarea pendiente en Capa al recibir un hijo; completadas recientes primero en orden Manual; barra superior con el Proyecto raíz.
- Room 16: migraciones no destructivas 14→15→16 desde v0.2.3.
- Backup v9 conserva Capas vacías y Modo Sprint, con lectura v1–v8.

Notas para revisión: [RELEASE_NOTES_v0.2.4.md](RELEASE_NOTES_v0.2.4.md).

## 0.2.3 — Beta en preparación

Versión Android `0.2.3`, código `5`. Sin publicación en esta preparación.

- Recurrencia, Calendario Hoy/Semana/Mes y filtros universales.
- Etiquetas, historial, prioridad y Atención 2.0.
- Creación/edición 2.0, recuperación de borradores, Undo y acciones múltiples.
- Grupos de creación, edición de grupo y copia de Proyectos.
- Navegación móvil adaptativa, acciones e iconos locales consistentes y orden contextual.
- Valores predeterminados globales/Proyecto/Capa, con herencia.
- Room v14: cadena no destructiva desde v8 (v0.2.2). Backup v7 con lectura v1–v7.

Notas para revisión: [RELEASE_NOTES_v0.2.3.md](RELEASE_NOTES_v0.2.3.md).

## 0.2.0 — Registro histórico de preparación

El checkout actual contiene el tag v0.2.0. Este registro describe su preparación original. Versión Android: `0.2.0`, código `2`.

- Capas de cebolla: navegación jerárquica, movimiento entre capas, orden manual y progreso derivado de tareas descendientes.
- Personas y responsables; fechas de tareas y vista Atención con propagación por la jerarquía.
- Notas y conversión Tarea/Nota; creación múltiple, numeración y fechas finitas.
- Calendario mensual derivado.
- Obligaciones: importes por moneda, agregación por Capa/Proyecto, períodos, filtros por Persona y vista transversal.
- Informe de Obligaciones en PNG para compartir o guardar (#23).
- Copiar contexto estructurado en Markdown o texto plano (#55A).
- Versionado formal, infraestructura de APK Release firmado y procedimiento manual de GitHub Releases (#30–32).

Room permanece en v8. La comprobación y descarga de actualizaciones desde la app (#33–34) siguen pendientes.

## 0.1.0 — Base MVP histórica

Proyectos, Nodes jerárquicos y progreso derivado. Esta entrada describe la base del proyecto; no acredita una Release publicada ni un tag existente.
