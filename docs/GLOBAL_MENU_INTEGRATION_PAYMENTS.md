# Integración del menú global y Pagos

## Alcance

Se reutiliza el panel experimental, sin cambiar sus cuatro categorías, entradas, dos columnas, callbacks ni mecanismos de cierre. Se elimina su fila exclusiva de 48 dp del pie compartido. El acceso es ahora una ranura explícita de 48 × 48 dp, al extremo derecho de las barras habituales, con icono Menú y separador discreto.

Integración:

- Núcleos: misma fila de Crear núcleo / Crear borrador.
- Interior de Núcleos, Capas, Tareas y Notas: misma fila de Contenido / Crear / Ver / Opciones; Banco y cuentas reutilizan esta barra habitual.
- Borradores: misma fila de Crear borrador, fuera de sus editores.
- Antisocial: listado (Buscar Personas / Nueva Persona) e Inicio del perfil (Foto / Banner / Opciones), cuando hay espacio.
- Buscador: barra plegada; fila Restablecer / Aplicar cuando cabe.

No se agrega el acceso a componentes inferiores genéricos, audio ni Pomodoro. Tampoco aparece en cabeceras experimentales de herramientas ni modales. Wireframe, Diagramas, Metro, Tragaperras, Juego, editores, anotaciones, visores, Configuración, Apariencia y demás pantallas secundarias conservan sus controles propios. Pomodoro recibe un botón explícito Volver en su cabecera; las otras herramientas ya disponían de retorno visible. Se mantienen los callbacks y estados de navegación existentes.

## Adaptabilidad y excepciones

Las barras de Núcleos, del interior, del perfil y del buscador expandido comparan el espacio de sus etiquetas antes y después de reservar la ranura. Se omite únicamente el acceso opcional si reduciría un objetivo por debajo de 48 dp o aumentaría la altura de la fila. No se recortan etiquetas ni se añaden filas o desplazamiento horizontal. La excepción depende del ancho efectivo, la fuente y las etiquetas activas (incluido Por verificar). Se conservan las acciones contextuales y el retorno al contexto anterior.

En el listado de Antisocial se reserva además espacio para el campo Buscar Personas: el acceso se omite por debajo de 360 dp multiplicados por la escala de fuente. Inicio del perfil tiene su propia comprobación de ajuste. Los controles de edición de foto y opciones no reciben el acceso.

Calendario no tiene una barra inferior de navegación apropiada: queda como excepción, sin inventar una fila nueva. Las barras de editores y herramientas especializadas se excluyen por diseño, aunque tengan espacio.

Los insets existentes continúan siendo responsabilidad de AppSafeArea y BottomModulesFrame/Dialog; el botón no añade otro consumidor de insets. La exclusión de modales usa la profundidad existente del anfitrión de módulos. Se conservan los bloqueos de operaciones ocupadas.

## Descripción y Pagos

La descripción se extrae del antiguo elemento monolítico de contexto y pasa a ser un elemento independiente de la lista. Su altura depende del contenido real y la siguiente sección conserva la separación habitual de 8 dp. Se elimina el mínimo de altura y el margen particular de esa tarjeta. Descripción vacía no produce un elemento ni un hueco propio. Pagos permanece en el flujo vertical de la sección siguiente, sin posiciones absolutas ni offsets.

Pagos reduce cifras de titleMedium a bodySmall, título a labelMedium, etiquetas a 10 sp y padding vertical a 8 dp. Total pendiente usa seminegrita. La distribución de tres columnas exige que quepan tanto las cifras como las etiquetas; de lo contrario pasa al flujo vertical con texto envolvente. Se mantienen íntegros los importes y el formateo monetario. No cambian cálculos, monedas, Banco, estados, skins ni almacenamiento.

## Validación dirigida

Se reutilizan ExperimentalMenuPresentationTest, ExperimentalGlobalMenuIntegrationTest, CompactPreviewUiTest, ContextCalendarIntegrationTest y BankUiTest. Nueve pruebas dirigidas distintas pasaron. Se comprueban callbacks y cierres del panel, acceso único dentro de la barra, objetivos de 48 dp, regreso de Wireframe a la misma Capa, conservación de la búsqueda e insets simulados del teclado. El recorrido real incluye Antisocial y Borradores; Banco verifica su acceso y conserva el flujo de crear cuenta, ingresar, cancelar retiro y anular, con exclusión del menú en el diálogo de edición. La matriz del interior compara altura, accesibilidad y texto a 280/320/411 dp y fuentes 1/1,5/2. La composición real de Capa cubre descripciones breves, multilínea y vacías, con fuentes 1/1,5. La prueba monetaria utiliza varias monedas e importes de cuarenta dígitos a 240 dp y fuente 1,6.

Las comprobaciones automatizadas usan Robolectric; no sustituyen la revisión visual en el Pixel 7a físico. No se genera ni firma un APK oficial.
