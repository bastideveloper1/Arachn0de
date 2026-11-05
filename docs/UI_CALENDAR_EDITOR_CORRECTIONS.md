# Correcciones de interfaz, calendario y editor

## Resultado por tarea

1. **Los iconos de tareas pendientes y de tareas en revisión dentro de una tarjeta deben corregirse.** Los contadores interiores reutilizan `CompactCardGlyph` e `InteriorIndicatorCount`: envoltorio de 28 dp dentro de 48 dp e insignia numérica común. Pendientes vuelve al recurso original de tareas `lucide_list_checks`; Por verificar conserva `lucide_scan_eye`. Se reduce el espacio superior y entre título e indicadores. No cambian consultas, estados, semáforo ni vistas previas compactas. Cero mantiene la presentación sin insignia, como los demás contadores.
2. **el icono de esfuerzo en el menú debe ser el de la flama, hay uno de mancuerna.** Se reutiliza `LocalFireDepartment`, ya usado por Esfuerzo, exclusivamente en su acción contextual. La escala y comportamiento no cambian.
3. **al crear elemento los iconos estan muy a la izquierda, cuesta seleccionarlos.** Las opciones iniciales ocupan todo el ancho, los tipos tienen superficies mínimas de 48 dp y margen lateral, y la fila de metadatos de creación se centra con separación lateral. Se conservan las acciones y el flujo de plantillas; no cambia su persistencia.
4. **el icono de bandera Urgente en calendario superior está bien solo que es muy grande.** La bandera conserva el recurso y pasa de 24 a 20 dp, igual que la estrella; el control sigue midiendo 48 dp.
5. **Al esconder el calendario y entrar a otro lugar no persiste y se vuelve a abrir.** La minimización es compartida y se guarda como booleano en las preferencias cifradas de la sesión. Los filtros y las fechas siguen siendo independientes por ámbito. Se incluye en exportación/restauración y se valida antes de sustituir datos; sin clave histórica, empieza expandido. No cambia Room ni el formato de backup.
6. **Ahora que queda espacio arriba quizá ahí puede aparecer el número de capa.** Evaluado, sin implementación: `currentLayer = pathNodes.size` expresa profundidad, no un identificador estable. El ID real es el ID de Node. No se presenta la profundidad como número estable. Queda pendiente definir si se desea un identificador legible y cuál sería su significado, antes de colocarlo en cabecera.
7. **Núcleos/Tarjetas.** Pendiente de especificación. Los cambios compartidos de envoltorio y margen se aplican también a los indicadores interiores de Núcleos; no se añaden requisitos a sus tarjetas.
8. **El modo código en descripción sigue presentando problemas.** La selección normaliza solamente delimitadores de código válidos que intersectan o lindan con el fragmento. Se conservan el texto y el formato del exterior; una selección parcial divide/reúne los tramos de código. El caret sin selección conserva su funcionamiento anterior. No hay reparaciones masivas de delimitadores malformados. El editor delega el scroll vertical en el contenedor existente, compartiendo coordenadas entre texto y fondo de código. Los marcadores almacenados y los otros formatos conservan su formato histórico.

## Archivos modificados en esta intervención

Rutas relativas a `app/src/main/java/com/r0ybt/arachn0de/`:

- `ui/CompactCardIconButton.kt`, `ui/InteriorTaskCounts.kt`, `ui/DetailPresentation.kt`, `ui/ProjectScreen.kt`.
- `ui/NodeBottomModule.kt`, `ui/ActionComponents.kt`.
- `ui/CreationFields.kt`, `ui/EditDialogs.kt`, `ui/StructuralTemplatesUi.kt`.
- `ui/ContextCalendar.kt`, `domain/model/CalendarPresentationPreference.kt`, `backup/BackupData.kt`.
- `ui/AttachmentDescriptionField.kt`, `ui/state/AttachmentDraftEditing.kt`.

Pruebas en `app/src/test/java/com/r0ybt/arachn0de/`:

- `ui/CodeSelectionNormalizationTest.kt`, `ui/AttachmentEditingStateTest.kt`.
- `ui/CalendarPresentationPersistenceTest.kt`, `security/CalendarPresentationBackupTest.kt`.
- `ui/DescriptionLongCodeLayoutTest.kt`, `ui/ElementCorrectionVisualTest.kt`.

Documentación: `ARCHITECTURE.md`, `BACKUP_FORMAT.md` y este informe.

## Validación focalizada

- Compilación de depuración correcta y `git diff --check` sin errores.
- Formato normal, código completo, selección mixta, selección parcial, repetición, caracteres especiales y multilínea; conservación del exterior y restauración mediante el Saver existente.
- Pruebas existentes de edición y referencias. Dos expectativas históricas se actualizaron para excluir el resaltado retirado de la lista de formatos editables, sin recuperar esa función.
- Descripción de más de 60 líneas: el scroll pertenece al contenedor y los controles quedan fuera de su área.
- Indicadores informativos con insignias comunes, sin acciones nuevas; selección mediante etiquetas con objetivos de 48 dp.
- Minimización compartida entre ámbitos y recreación del almacén de preferencias.
- Exportación y restauración de la preferencia, backup histórico sin clave y rechazo de tipo inválido sin reemplazar datos anteriores.

Finalmente pasaron los 26 casos distintos ejecutados; solo se repitieron los afectados por ajustes posteriores o por correcciones de expectativas/pruebas. No se ejecutó la suite completa.

## Límites

Pendiente revisión física con teclado, fuentes ampliadas y skins; la recreación de preferencias se prueba automáticamente, sin acreditar un cierre físico y reapertura del proceso Android. Los delimitadores históricos malformados se conservan, sin saneamiento global. Las tareas 6 y 7 permanecen pendientes según lo indicado.

Sin APK oficial, cambios de versión, acceso a credenciales, cambios de firma, commits ni publicaciones.
