# Changelog

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
