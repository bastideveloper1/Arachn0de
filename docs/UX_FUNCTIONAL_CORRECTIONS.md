# Correcciones funcionales y UX

Intervención sin APK, cambios de versión, commit ni push. No se realizan auditorías de seguridad/rendimiento ni se añaden nuevas preferencias, tablas o recursos gráficos.

## Presentación y audio

- Banco: colores semánticos explícitos para el contenido de movimientos y paneles, conservando los tonos de ingresos, egresos y operaciones anuladas. Cuentas usan el texto primario del tema.
- Núcleos Banco: icono Material AccountBalance en la zona inferior de la tarjeta, sin restaurar los indicadores habituales. Conserva imagen, skin, favorito y opciones.
- Tragaperras: ToneGenerator mantiene STREAM_MUSIC, tipos de tono y duraciones. Ganancia del generador 35→75 sobre 100; no cambia el volumen del dispositivo, reglas ni animaciones. La comprobación auditiva y distorsión percibida requieren teléfono.
- Grabadora: punto de grabación pulsante y barras de nivel más visibles dentro del mismo objetivo táctil de 48 dp. Indicador de 28 dp; pausa estática y atenuada. No cambia el motor de grabación.

## Destinos y movimientos

DestinationExplorer reutiliza el panel modular y sus botones inferiores. La ruta navegable muestra Núcleos y contenedores; la lista tiene desplazamiento propio. Buscar destino está en el pie junto a Cancelar y Mover aquí, fuera de la lista y protegido por imePadding del panel. Los resultados globales muestran su ubicación y conducen al contenedor sin mover todavía. Tareas y Notas pueden explorarse pero no aceptan hijos. Se excluyen origen/descendientes y Banco como destino de elementos normales.

NodeDestinationDialog sirve al movimiento individual, selección múltiple y Borradores. Los dos puntos de entrada usan el mismo explorador y buscador inferior. La conversión de un Núcleo completo en Capa conserva su diálogo existente (es una operación de conversión distinta).

NodeRepository.moveToContainer revalida destino y origen en Room. Los movimientos dentro del mismo Núcleo reutilizan moveNode. Entre Núcleos se cambian projectId y parentId dentro de una única transacción, sin borrar/recrear nodos ni adjuntos. Las FK compuestas se difieren hasta confirmar y se comprueban explícitamente antes de terminar; no se desactiva foreign_keys. Conserva IDs, bytes y relaciones por identidad, configuración de Capas, orden y reglas recurrentes internas. No divide grupos ni mueve cuentas de Banco.

Limitaciones estructurales: relaciones de gestión/planificación ligadas a otro contexto bloquean el cambio de Núcleo; una ocurrencia recurrente aislada no se separa de su regla, ni se trasladan reglas con ocurrencias vivas fuera del contenedor. Se informa del conflicto sin cambios parciales. No se crean contenedores convirtiendo Tareas automáticamente desde este selector.

## Persistencia y backup

Sin migración de datos ni cambio de formato. La regla SQLite antigua se ajusta para permitir Capas entre Núcleos; sigue impidiendo estructuras en Borradores. Se reinstala al abrir Room 53, igual que las reglas idempotentes existentes. Las relaciones y archivos conservan sus IDs y ubicaciones de almacenamiento. No se generan ni eliminan archivos en el traslado. Las configuraciones scoped por proyecto se remapean transaccionalmente. Los respaldos existentes conservan el esquema y la validación. Las pruebas dirigidas incluyen round-trip posterior al traslado y rollback ante fallo.

## Archivos principales

- ui/BankUi.kt
- ui/ProjectComponents.kt
- ui/AudioUi.kt
- slots/SlotSounds.kt
- ui/DestinationExplorer.kt (compartido nuevo)
- ui/ModularPanel.kt (pie personalizable opcional; resto de llamadas conserva distribución)
- ui/ProjectScreen.kt
- ui/DraftsScreen.kt
- access/ElementSearch.kt (parentKey calculado en memoria)
- data/repository/NodeRepository.kt
- data/local/DraftsMigration.kt y Arachn0deDatabase.kt (regla de traslado actualizada al abrir instalaciones existentes, sin reescribir datos)

Tests: ux/MoveContainerTest, ux/DestinationExplorerTest, ux/VisualFeedbackTest, ux/SlotSoundLevelTest; adaptación de NodeMoveUiTest, la prueba afectada de BankVisualCompactTest y DraftsMigrationTest (compara las columnas originales tras actualizar a la versión Room vigente).

Validación final: compilación correcta (`BUILD SUCCESSFUL`) y 14 métodos focalizados aprobados mediante ejecuciones dirigidas:

- 4 pruebas de repositorio: Tareas entre Capas/Núcleos y salida de Borradores; Capa completa con adjuntos, responsables, etiquetas, configuración, recurrencia y round-trip de backup; historial de abonos/saldo y exclusión de cuentas Banco; destinos inválidos y rollback de toda la Capa ante fallo.
- 2 pruebas del explorador: búsqueda global, ruta, exclusión del origen y confirmación explícita; buscador/acciones inferiores separados de la lista y accesibles con insets de teclado.
- 2 pruebas de feedback: contraste semántico de texto bancario en Charcoal/Dark/Green Bash; grabación activa y pausa con la misma huella de indicador.
- 1 prueba de sonidos: STREAM_MUSIC, ganancia aumentada dentro del rango previsto y tonos/duraciones originales.
- 3 pruebas de navegación: mover Capa abierta conservando contexto, mover Tarea desde su menú y recuperarse de un fallo sin cerrar el selector.
- 1 prueba visual de tarjeta Banco: indicador visible solo en Banco, altura compacta sin aumento, cuentas y skins conservadas; indicador acotado al área de texto.
- 1 prueba de migración histórica de Borradores: preservación de tablas/referencias y rollback al actualizar una base antigua a Room 53.

La primera ejecución detectó la restricción SQLite antigua y selectores UI obsoletos; se corrigieron y repitieron solo los casos afectados. La comprobación adicional de altura detectó y corrigió la alineación exclusiva de Banco. No se ocultan esos intentos: las últimas ejecuciones correspondientes aprobaron todos los métodos. Los hashes de fuentes coinciden con la compilación final; el backend financiero y versionName/versionCode permanecen intactos. `git diff --check` correcto. La APK oficial existente mantiene su SHA-256; no se construyó otra APK. En teléfono: comprobar sonido multimedia, nivel sin distorsión, pulso de grabación, skins con títulos largos, teclado/fuentes ampliadas y navegación tras cambiar de Núcleo.
