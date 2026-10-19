# Óptica App

Aplicación móvil para Android orientada a la gestión de ópticas. Integra expedientes clínicos con graduación, inventario, punto de venta, órdenes de laboratorio y administración del personal, con sincronización en tiempo real mediante Firebase.

| | |
|---|---|
| **Asignatura** | Taller de Desarrollo de Aplicaciones Móviles |
| **Integrantes** | Gilberto Navarrete García · Luisa Fernanda Mancilla Portillo |
| **Plataforma** | Android 8.0 (API 26) o superior · `compileSdk` 36 |
| **Lenguaje** | Java 11 y XML |
| **Bibliotecas** | Material Components 3, Navigation Component 2.9, ViewBinding, Glide 4.16, Credential Manager |
| **Servicios** | Firebase Authentication y Cloud Firestore (plan gratuito Spark) |
| **Paquete** | `mx.itch.opticaapp` |

---

## Contenido

1. [Funcionalidades](#1-funcionalidades)
2. [Roles y permisos](#2-roles-y-permisos)
3. [Instalación y configuración](#3-instalación-y-configuración)
4. [Arquitectura](#4-arquitectura)
5. [Validación de datos](#5-validación-de-datos)
6. [Datos almacenados en Firebase](#6-datos-almacenados-en-firebase)
7. [Seguridad](#7-seguridad)
8. [Interfaz de usuario](#8-interfaz-de-usuario)
9. [Limitaciones conocidas](#9-limitaciones-conocidas)
10. [Licencias](#10-licencias)

---

## 1. Funcionalidades

| Módulo | Descripción |
|--------|-------------|
| **Acceso** | Inicio de sesión con correo y contraseña o con cuenta de Google; recuperación de contraseña por correo. |
| **Registro** | Alta de una óptica nueva (el usuario queda como administrador) o incorporación a una existente mediante código de invitación. |
| **Panel** | Avance de la meta semanal de ventas, ventas y pacientes del día, órdenes pendientes y productos con existencias bajas. |
| **Pacientes** | Alta y edición de datos (nombre, teléfono y edad), búsqueda por nombre o teléfono, orden por fecha o alfabético y fichas con la última consulta y la graduación vigente. |
| **Expediente clínico** | Registro de consultas (esfera, cilindro, eje, adición, distancia pupilar, agudeza visual, tipo de lente), historial y receta en PDF. |
| **Inventario** | Catálogo con fotografía, búsqueda, alta, edición y baja de productos, y alerta de existencias mínimas. El detalle de cada producto muestra sus características, variantes de color (con alta de colores exactos) y la opción de estampar el logotipo de la óptica con un costo adicional. |
| **Punto de venta** | Carrito, anticipo y saldo, método de pago, descuento automático de existencias, nota de venta en PDF y orden de laboratorio opcional. |
| **Laboratorio** | Seguimiento de órdenes en cuatro etapas: Enviado, En proceso, Recibido y Entregado, con la fecha de cada una. |
| **Administración** | Datos y logotipo de la óptica, personal y roles, creación de cuentas para el equipo y corte de caja del día. El logotipo aparece en el detalle de los productos y en las recetas y notas de venta en PDF. |
| **Configuración** | Tema claro, oscuro o del sistema, texto grande, modo sin imágenes y fondo de pantalla. |

## 2. Roles y permisos

| Función | Administrador | Optometrista | Vendedor |
|---------|:---:|:---:|:---:|
| Panel | ✓ | ✓ (indicadores clínicos) | ✓ (indicadores de venta) |
| Pacientes: consultar | ✓ | ✓ | ✓ |
| Pacientes: registrar, editar datos y capturar graduación | ✓ | ✓ | — |
| Inventario y punto de venta | ✓ | — | ✓ |
| Agregar o quitar variantes de color | ✓ | — | ✓ |
| Eliminar productos | ✓ | — | — |
| Subir o cambiar el logotipo de la óptica | ✓ | — | — |
| Laboratorio: consultar y actualizar estatus | ✓ | ✓ | ✓ |
| Laboratorio: crear órdenes manuales | ✓ | — | ✓ |
| Menú **Administración** | ✓ | — | — |
| Mi perfil y Configuración | ✓ | ✓ | ✓ |

El dueño de la óptica (quien la registró) siempre conserva el rol de administrador.

## 3. Instalación y configuración

### 3.1 Requisitos

- Android Studio (versión reciente compatible con AGP 8.13).
- Un proyecto de Firebase con acceso a la consola.
- Emulador o dispositivo con Android 8.0 o superior. El inicio de sesión con Google requiere Google Play Services.

### 3.2 Configuración de Firebase

1. En <https://console.firebase.google.com>, crear un proyecto y registrar una aplicación **Android** con el paquete `mx.itch.opticaapp`.
2. Descargar `google-services.json` y colocarlo en la carpeta `app/`. Sin este archivo la compilación falla con el mensaje *"File google-services.json is missing"*.
3. En **Authentication › Método de acceso**, habilitar **Correo electrónico/contraseña** y **Google**.
   - Para Google es necesario registrar la huella SHA-1 del equipo de desarrollo. Se obtiene con `./gradlew signingReport` (en Windows, `gradlew signingReport`) y se agrega en **Configuración del proyecto › Tus apps › Agregar huella digital**. Después se vuelve a descargar `google-services.json`.
4. En **Firestore Database**, crear la base de datos en modo producción y publicar en la pestaña **Reglas** el contenido de `firebase/firestore.rules`.

No es necesario habilitar Cloud Storage ni contratar el plan Blaze: las fotografías se almacenan comprimidas dentro de Firestore (sección 6.3).

### 3.3 Compilación y primer uso

1. Abrir la carpeta `OpticaApp` en Android Studio (**File › Open**) y esperar la sincronización de Gradle.
2. Ejecutar la aplicación en el emulador o dispositivo.
3. Seleccionar **Registrar mi óptica** para crear la cuenta del dueño, que queda como administrador.
4. Incorporar al personal por cualquiera de estas vías, ambas en el menú lateral › **Administración**:
   - **Nueva cuenta**: el administrador captura nombre, correo, contraseña temporal y puesto, sin cerrar su propia sesión.
   - **Datos de la óptica**: se comparte el código de invitación; la persona elige **Unirme con código** en su dispositivo.

## 4. Arquitectura

La aplicación usa una sola `Activity` (`MainActivity`) con 15 `Fragment` conectados por Navigation Component (`res/navigation/nav_graph.xml`). Las vistas se enlazan con ViewBinding y los datos se leen con *listeners* de Firestore en tiempo real.

### 4.1 Estructura de paquetes

```
mx.itch.opticaapp
├── MainActivity        Barra superior, barra inferior, menú lateral y navegación
├── OpticaApp           Clase Application (aplica el tema guardado)
├── data/
│   ├── Sesion          Usuario y óptica de la sesión; referencias a sus colecciones
│   └── Preferencias    Preferencias locales (SharedPreferences)
├── model/              Clases que Firestore convierte en documentos
├── ui/                 Un paquete por módulo (Fragment y Adapter)
└── util/
    ├── Validaciones    Reglas de validación de formularios
    ├── Formato         Moneda, números, teléfonos e iniciales
    ├── Fechas          Formatos de fecha e intervalos (día, semana)
    ├── Imagenes        Cámara, compresión de fotografías y carga con Glide
    ├── SelectorDeFoto  Diálogo "Tomar foto / Elegir de la galería" reutilizable
    ├── Aviso           Aviso flotante de confirmación ("Producto guardado")
    ├── Pdf             Receta y nota de venta con PdfDocument
    ├── Animaciones     Animaciones de entrada de cada pantalla
    └── AlEscribir      TextWatcher simplificado
```

### 4.2 Navegación

- **Barra inferior:** Inicio, Pacientes, Inventario, Ventas y Laboratorio. Cada sección conserva su propio historial.
- **Menú lateral:** Mi perfil, Configuración, el grupo Administración (solo administrador) y Cerrar sesión. Sus pantallas se abren sobre la sección actual y el botón Atrás regresa a ella.
- La navegación está bloqueada en el inicio de sesión y el registro, y se reinicia al iniciar o cerrar sesión.

### 4.3 Pantallas

| # | Pantalla | Clase | Layout |
|---|----------|-------|--------|
| 1 | Inicio de sesión | `ui/login/LoginFragment` | `fragment_login.xml` |
| 2 | Registro y unión con código | `ui/registro/RegistroFragment` | `fragment_registro.xml` |
| 3 | Panel principal | `ui/panel/PanelFragment`, `FondoLentes` | `fragment_panel.xml` |
| 4 | Pacientes (alta y edición en `DialogoPaciente`) | `ui/pacientes/PacientesFragment`, `PacientesAdapter`, `DialogoPaciente` | `fragment_pacientes.xml`, `item_paciente.xml`, `dialog_paciente.xml` |
| 5 | Expediente clínico | `ui/expediente/ExpedienteFragment`, `ConsultasAdapter` | `fragment_expediente.xml` |
| 6 | Inventario | `ui/inventario/InventarioFragment`, `ProductosAdapter` | `fragment_inventario.xml`, `item_producto.xml` |
| 7 | Detalle de producto | `ui/inventario/ProductoDetalleFragment`, `MuestraColor` | `fragment_producto_detalle.xml`, `item_color.xml`, `item_etiqueta.xml`, `dialog_color.xml` |
| 8 | Alta y edición de producto | `ui/inventario/ProductoFormFragment` | `fragment_producto_form.xml` |
| 9 | Punto de venta | `ui/venta/VentaFragment`, `CarritoAdapter` | `fragment_venta.xml` |
| 10 | Órdenes de laboratorio | `ui/laboratorio/LaboratorioFragment`, `OrdenesAdapter` | `fragment_laboratorio.xml` |
| 11 | Configuración | `ui/ajustes/AjustesFragment` | `fragment_ajustes.xml` |
| 12 | Mi perfil | `ui/administracion/PerfilFragment` | `fragment_perfil.xml` |
| 13 | Datos de la óptica | `ui/administracion/OpticaFragment` | `fragment_optica.xml` |
| 14 | Personal | `ui/administracion/PersonalFragment`, `PersonalAdapter` | `fragment_personal.xml`, `item_persona.xml` |
| 15 | Nueva cuenta | `ui/administracion/NuevaCuentaFragment` | `fragment_nueva_cuenta.xml` |
| — | Corte de caja (diálogo) | `ui/administracion/CorteDeCaja` | — |

### 4.4 Detalle de producto

- **Características:** se capturan en el formulario separadas por coma y se muestran como etiquetas.
- **Variantes de color:** carrusel de círculos con nombre y código hexadecimal. El color elegido se marca con un anillo doble y una textura de tela (`MuestraColor`). Administrador y vendedor pueden agregar un color exacto (`#RRGGBB`, con vista previa) o quitarlo manteniendo presionado el círculo.
- **Personalización:** si el producto lo permite, un interruptor agrega el estampado del logotipo de la óptica y su costo al precio. El administrador puede subir o cambiar el logotipo desde ahí mismo.
- **Venta:** "Vender" agrega al carrito el producto con el color y el estampado elegidos; cada combinación ocupa su propio renglón (por ejemplo, "Paño · Gris Oxford · logo estampado") y el costo del estampado se suma al precio unitario.

### 4.5 Edición de pacientes

`DialogoPaciente` se usa tanto para el alta como para la edición (botón de lápiz en el expediente o pulsación larga en la lista). La edición solo modifica nombre, teléfono y edad; la graduación y el historial no cambian. Las ventas y órdenes anteriores conservan el nombre con que se registraron.

### 4.6 Creación de cuentas por el administrador

`FirebaseAuth` inicia sesión automáticamente con la cuenta que crea. Para que el administrador no pierda su sesión, **Nueva cuenta** utiliza una segunda instancia de Firebase (`"altas"`) que crea la cuenta, guarda el documento `users/{uid}` con el código de invitación de la óptica y cierra su propia sesión. Si el documento no se puede guardar, la cuenta recién creada se elimina para permitir un nuevo intento.

## 5. Validación de datos

Todas las reglas están centralizadas en `util/Validaciones`. Cada formulario revisa todos sus campos a la vez y marca cada error en su propio campo. Además, los layouts limitan la entrada con `android:maxLength` y, cuando aplica, con `android:digits`, de modo que el teclado no permite escribir caracteres o longitudes fuera de regla.

| Dato | Regla |
|------|-------|
| Nombre de persona | Obligatorio, de 3 a 60 caracteres; solo letras (incluye acentos y ñ), espacios, punto, apóstrofo y guion. |
| Nombre de óptica o producto | Obligatorio, de 2 a 60 caracteres, con al menos una letra o número. |
| Correo electrónico | Obligatorio, formato válido, sin espacios, máximo 100 caracteres. |
| Contraseña | De 8 a 64 caracteres, con mayúscula, minúscula, número y símbolo, sin espacios. Se confirma en un segundo campo. |
| Teléfono | **Exactamente 10 dígitos** (lada y número). Se rechazan longitudes distintas y secuencias de un solo dígito repetido. Se almacena solo con dígitos y se muestra como `747 123 4567`. |
| Dirección | De 5 a 150 caracteres. |
| Edad | Número entero de 0 a 120. |
| Código de invitación | Formato `OPT-XXXXXX` (6 letras o números). |
| Precio | Mayor que $0 y hasta $999,999.99, con un máximo de 2 decimales. |
| Meta semanal | Opcional, de $0 a $9,999,999.99, con un máximo de 2 decimales. |
| Existencias / stock mínimo | Enteros de 0 a 99,999 y de 0 a 9,999, respectivamente. |
| Esfera | Obligatoria, de −20.00 a +20.00 en pasos de 0.25. |
| Cilindro | Opcional, de −10.00 a +10.00 en pasos de 0.25. |
| Eje | De 1° a 180°; obligatorio cuando hay cilindro y no permitido sin cilindro. |
| Adición | De +0.25 a +4.00 en pasos de 0.25; obligatoria en al menos un ojo para lentes bifocales y progresivos. |
| Distancia pupilar | Opcional, de 40 a 80 mm en pasos de 0.5. |
| Agudeza visual | Opcional, notación de Snellen (por ejemplo, `20/40`). |
| Observaciones, descripciones | Máximo 500 y 300 caracteres, respectivamente. |
| Características de producto | Hasta 5, separadas por coma, de 2 a 20 caracteres cada una; se eliminan repetidas. |
| Nombre de color | De 2 a 20 caracteres (letras, números y espacios); no se repite en el mismo producto. |
| Código de color | Formato hexadecimal `#RRGGBB`; no se repite en el mismo producto. |
| Costo del estampado | De $0 a $9,999.99, con un máximo de 2 decimales. |
| Números en general | Se aceptan coma o punto decimal; se rechazan expresiones como `NaN`, `Infinity` o notación científica. |

Verificaciones adicionales de consistencia:

- **Pacientes duplicados:** al registrar o editar, si ya existe otro paciente con el mismo teléfono o el mismo nombre, se solicita confirmación antes de guardar. Si en la edición no cambió ningún dato, no se escribe en la base de datos.
- **Productos duplicados:** no se permiten dos productos con el mismo nombre en el inventario.
- **Venta:** la cantidad no puede superar las existencias (considerando lo que ya está en el carrito); el anticipo no puede ser negativo ni mayor que el total; un saldo pendiente y una orden de laboratorio requieren un paciente registrado.
- **Nueva cuenta:** no se permite usar el correo del propio administrador; los correos ya registrados se rechazan.
- **Personal:** nadie puede cambiar su propio rol ni el del dueño de la óptica.
- **Mi perfil:** no se escribe en la base de datos si el nombre no cambió.

## 6. Datos almacenados en Firebase

La aplicación no utiliza servidores propios ni Cloud Functions: todas las escrituras se realizan desde el cliente y están sujetas a las reglas de seguridad (sección 7).

### 6.1 Authentication

| Origen | Información |
|--------|-------------|
| Registro con correo y Nueva cuenta | Cuenta de correo y contraseña. La contraseña la administra Firebase de forma cifrada; nunca se escribe en Firestore. |
| Inicio con Google | Cuenta de Google vinculada al proyecto. |
| Recuperar o cambiar contraseña | Solicitud a Firebase para enviar un enlace de restablecimiento al correo. |

### 6.2 Cloud Firestore

Cada documento contiene todos los campos públicos de su clase en `model/`; el identificador es el nombre del documento y no se repite como campo.

```
users/{uid}                                   model/Usuario
  nombre, email, rol (ADMIN | OPTOMETRISTA | VENDEDOR), opticaId, codigoUsado, fotoUrl

codigos/{OPT-XXXXXX}
  opticaId

opticas/{opticaId}                            model/Optica
  nombre, telefono, estado, direccion, metaSemanal, codigoInvitacion, ownerId, logoUrl

opticas/{opticaId}/pacientes/{id}             model/Paciente
  nombre, telefono, edad, fechaRegistro, ultimaConsulta, resumenGraduacion

opticas/{opticaId}/pacientes/{id}/consultas/{id}   model/Consulta
  fecha, odEsfera, odCilindro, odEje, odAdicion, oiEsfera, oiCilindro, oiEje, oiAdicion,
  distanciaPupilar, agudezaVisual, tipoLente, observaciones, optometristaId, optometristaNombre

opticas/{opticaId}/productos/{id}             model/Producto
  nombre, categoria, descripcion, precio, stock, stockMinimo, fotoUrl,
  etiquetas [texto], colores [{nombre, hex}], personalizable, costoPersonalizacion

opticas/{opticaId}/ventas/{id}                model/Venta
  pacienteId, pacienteNombre, items [{productoId, nombre, categoria, precio, cantidad}],
  total, anticipo, saldo, metodoPago, vendedorId, vendedorNombre, fecha

opticas/{opticaId}/ordenes/{id}               model/Orden
  ventaId, pacienteId, pacienteNombre, descripcion, estatus (ENVIADO | EN_PROCESO | RECIBIDO | ENTREGADO),
  fechaCreacion, fechaEnvio, fechaEnProceso, fechaRecibido, fechaEntregado
```

Operaciones compuestas, ejecutadas en un solo lote (`WriteBatch`) para que se apliquen completas o no se apliquen:

| Operación | Escrituras |
|-----------|-----------|
| Registro de óptica | Documento de la óptica, código de invitación y usuario administrador. |
| Venta | Documento de la venta, resta de existencias por producto (`FieldValue.increment`) y, si se solicita, la orden de laboratorio. |
| Consulta | Documento de la consulta y actualización de `ultimaConsulta` y `resumenGraduacion` del paciente. |

Actualizaciones parciales: datos del paciente (`nombre`, `telefono`, `edad`), estatus de órdenes, rol del personal, datos y logotipo de la óptica, y variantes de color de un producto (`FieldValue.arrayUnion` / `arrayRemove`).

La única eliminación de documentos que realiza la aplicación es la de productos (solo administrador).

### 6.3 Fotografías

Las fotografías se guardan dentro de Firestore, sin Cloud Storage:

| Campo | Contenido | Lado máximo |
|-------|-----------|-------------|
| `users/{uid}.fotoUrl` | Foto de perfil | 320 px |
| `opticas/{opticaId}.logoUrl` | Logotipo | 400 px |
| `opticas/{opticaId}/productos/{id}.fotoUrl` | Foto del producto | 640 px |

`Imagenes.convertir()` acepta imágenes de cámara o galería (JPEG, PNG, WebP o HEIC), corrige la orientación, reduce la resolución y comprime en JPEG hasta un máximo de 120 KB. El resultado se guarda como texto `data:image/jpeg;base64,…`, que Glide muestra igual que una URL. El archivo original del dispositivo no se envía. Para los PDF, `Imagenes.decodificar()` convierte el logotipo de nuevo en imagen y se dibuja en la esquina superior derecha.

### 6.4 Información que permanece en el dispositivo

Preferencias de apariencia (tema, texto grande, modo sin imágenes), el fondo de pantalla elegido y los PDF de recetas y notas de venta. Los PDF se comparten mediante el menú del sistema y no pasan por Firebase.

## 7. Seguridad

Las reglas de `firebase/firestore.rules` establecen:

- Cada usuario puede leer su documento y los de su misma óptica.
- Un usuario solo puede crearse como administrador de una óptica de la que es dueño, o como optometrista o vendedor con un código de invitación válido para esa óptica.
- Un usuario puede modificar sus propios datos, pero no su rol ni su óptica; el administrador puede modificar el rol del personal de su óptica.
- Los códigos de invitación pueden consultarse de uno en uno, pero no listarse.
- Los datos de cada óptica (pacientes, productos, ventas y órdenes) solo son accesibles para su personal; los datos generales de la óptica solo los modifica su administrador.

## 8. Interfaz de usuario

### 8.1 Sistema visual

| Elemento | Valor |
|----------|-------|
| Fondo | Degradado lila a rosa (`degradado_*`); en modo oscuro, azul noche a morado. |
| Superficies | Hoja blanca con esquinas superiores de 36 dp (`bg_hoja`). |
| Texto principal | Índigo oscuro `#1E1B4B` (`texto`). |
| Texto de apoyo | Gris `#64748B` (`texto_secundario`), en formato normal, sin mayúsculas forzadas. |
| Acción principal | Azul cielo `#3BB4F2` (`cielo`). |
| Insignias de rol | Fondo `#EDE9FE`, texto `#5B21B6` (`Texto.Pildora.Rol`). |
| Tipografía | Lexend en cuatro grosores: Light (títulos grandes), Regular (texto), Medium (etiquetas, botones y menús) y SemiBold (títulos de sección, nombres y precios). |
| Iconografía | Íconos vectoriales de trazo fino (`res/drawable/ic_*`). |

Colores: `res/values/colors.xml` y `res/values-night/colors.xml`. Estilos reutilizables: `res/values/styles.xml`. Tema: `res/values/themes.xml`. Todos los layouts incluyen datos de ejemplo (`tools:text`) para revisarlos en la vista **Design** de Android Studio.

### 8.2 Menú lateral

Fondo de vidrio esmerilado (`bg_menu_vidrio`): degradado lila translúcido, borde blanco fino y esquinas derechas redondeadas. El encabezado muestra la foto con borde translúcido, la insignia de óptica y rol, el nombre y el correo, actualizados en tiempo real desde `users/{uid}`. La opción **Personal** muestra el número de integrantes mediante una consulta de conteo de Firestore. En Android 12 o superior, el contenido detrás del menú se desenfoca mientras está abierto.

### 8.3 Panel principal

`FondoLentes` dibuja la imagen de los lentes dos veces: nítida fuera de la hoja y desenfocada dentro de ella, lo que produce el efecto de vidrio en cualquier versión de Android sin bibliotecas adicionales. La imagen tiene animación de entrada, flotación, destello periódico, desplazamiento según la inclinación del dispositivo (acelerómetro) y giro al pulsarla. Las animaciones se detienen fuera de la pantalla y respetan la opción del sistema que desactiva animaciones.

### 8.4 Animaciones

- **Entre pantallas** (`res/anim`): las secciones cambian con un desvanecimiento y una ligera escala; los detalles y formularios entran desde la derecha.
- **Dentro de cada pantalla** (`util/Animaciones`): el encabezado aparece en cascada y la hoja sube; las listas muestran sus elementos de forma escalonada. La animación se ejecuta solo la primera vez que se abre cada pantalla.

## 9. Limitaciones conocidas

- Las restricciones por rol se aplican en la interfaz. Las reglas de Firestore limitan el acceso por óptica, pero no distinguen entre optometrista y vendedor dentro de la misma óptica.
- Las validaciones de formato se realizan en la aplicación; las reglas de Firestore no validan el contenido de cada campo.
- Si dos usuarios venden la última pieza de un producto al mismo tiempo, las existencias pueden quedar en negativo; la aplicación lo muestra como "Agotado".
- Las operaciones realizadas sin conexión se guardan en el dispositivo y se sincronizan al recuperar la red; mientras tanto, el botón correspondiente permanece en espera.
- Al guardar las fotografías dentro de los documentos, cada lectura de una lista también descarga sus imágenes. Para inventarios de varios cientos de productos convendría migrarlas a Cloud Storage.

## 10. Licencias

- **Lexend** — SIL Open Font License 1.1 (`licencias/OFL_Lexend.txt`).
- Bibliotecas de AndroidX, Material Components, Firebase y Glide — Apache License 2.0.
