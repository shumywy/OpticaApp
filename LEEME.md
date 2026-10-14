# Óptica App — proyecto de Android Studio

App en Java + XML. Usa una sola Activity, 11 Fragments y Firebase (Auth, Firestore, Storage).

## Diseño (estilo "catálogo")

Basado en la referencia de la tienda de lentes:

- **Fondo en degradado lila → rosa** detrás de todo. Arriba de cada pantalla va un texto chico en negritas y un **título enorme y delgado** (como "Chimi Eyewear / Collection").
- **Hoja blanca** con las esquinas de arriba muy redondeadas, donde va el contenido (`bg_hoja`).
- **Textos azul marino**, **botón principal azul cielo** con sombra azul, **etiquetas color vino** en mayúsculas (como "CORE COLLECTION").
- **Mosaicos lila** para productos e indicadores, **pestañas subrayadas** con un punto bajo la elegida (como "BLACK LENS · CLEAR LENS") y un **contador con línea** ("08/24").
- **Íconos de línea delgada**. La barra superior es transparente y solo lleva íconos: ⁘ abre el menú lateral y ‹ regresa.

Dónde se cambia:

- Colores: `res/values/colors.xml` (claro) y `res/values-night/colors.xml` (oscuro). Por ejemplo, `cielo` es el botón principal y `degradado_*` el fondo.
- Fuentes (en `res/font`, licencia libre OFL, ver carpeta `licencias/`):
  - **Lexend Exa** (ancha) para títulos, precios y nombres.
  - **Barlow Condensed** (angosta) para títulos de sección.
  - **Lexend** para el texto normal.
- Estilos reutilizables: `res/values/styles.xml`.
  - Textos: `Texto.Hero`, `Texto.Eyebrow`, `Texto.Categoria`, `Texto.Titulo`, `Texto.Seccion`, `Texto.Precio`.
  - Campos y botones: `Campo`, `Campo.Busqueda`, `Boton`.
  - Contenedores y controles: `Tarjeta.Mosaico`, `Tarjeta.Cristal`, `Segmento`, `Filtro`.
- Tema: `res/values/themes.xml`.
- Para ver cada pantalla sin ejecutar la app, abre su layout y elige la pestaña **Design** o **Split**: los textos de ejemplo vienen de `tools:text`.

## 1. Abrir el proyecto

1. Android Studio > **File > Open** > elige la carpeta `OpticaApp`.
2. Espera a que termine el **Gradle Sync**. Si Android Studio ofrece actualizar el plugin de Android (AGP), puedes aceptar.

> **Antes de compilar se necesita `app/google-services.json`.** Sin ese archivo, la compilación falla con el error
> *"File google-services.json is missing"*. Ese archivo es de tu proyecto de Firebase y no viene incluido (paso 2).

## 2. Configurar Firebase (una sola vez)

1. Entra a <https://console.firebase.google.com> y crea un proyecto.
2. Agrega una app **Android** con el paquete `mx.itch.opticaapp`.
3. Descarga `google-services.json` y cópialo dentro de la carpeta `app/`.
4. **Authentication > Sign-in method**: activa **Correo electrónico/contraseña** y **Google**.
   - Para Google también debes registrar la huella **SHA-1** de tu computadora: en Android Studio abre la terminal y ejecuta
     `./gradlew signingReport` (en Windows: `gradlew signingReport`). Copia el SHA-1 de *debug* en
     **Configuración del proyecto > Tus apps > Agregar huella digital**.
   - Después vuelve a descargar `google-services.json` y reemplaza el anterior.
5. **Firestore Database**: créala (modo producción) y en la pestaña **Reglas** pega el contenido de `firebase/firestore.rules`.
6. **Storage**: créalo y en **Reglas** pega `firebase/storage.rules`.
   - Desde febrero de 2026, Firebase solo da Storage a proyectos con **plan Blaze** (pide tarjeta, pero tiene una capa gratuita:
     5 GB guardados y 100 GB de descarga al mes). Sin Storage la app funciona, pero no se suben las fotos de perfil, logotipos ni productos.

## 3. Probar

1. Ejecuta la app en un emulador o teléfono (Android 8.0 o superior).
2. **Registrar mi óptica**: crea la cuenta del dueño (queda como Administrador).
3. En **Perfil y ajustes** (ícono de persona arriba a la derecha) copia el **código de invitación**.
4. En otro emulador o teléfono elige **Unirme con código**, pega el código y elige el puesto (Optometrista o Vendedor).

## Pantallas y dónde está su código

| # | Pantalla | Archivo Java | Layout |
|---|----------|--------------|--------|
| 1 | Inicio de sesión | `ui/login/LoginFragment` | `fragment_login.xml` |
| 2 | Registro de óptica / unirse con código | `ui/registro/RegistroFragment` | `fragment_registro.xml` |
| 3 | Panel principal (avance semanal) | `ui/panel/PanelFragment` | `fragment_panel.xml` |
| 4 | Pacientes | `ui/pacientes/PacientesFragment` | `fragment_pacientes.xml` |
| 5 | Expediente y graduación | `ui/expediente/ExpedienteFragment` | `fragment_expediente.xml` |
| 6 | Inventario en catálogo (+ formulario de producto) | `ui/inventario/InventarioFragment`, `ProductoFormFragment` | `fragment_inventario.xml`, `item_producto.xml`, `fragment_producto_form.xml` |
| 6b | Detalle de producto (botón **Vender**) | `ui/inventario/ProductoDetalleFragment` | `fragment_producto_detalle.xml` |
| 7 | Punto de venta | `ui/venta/VentaFragment` | `fragment_venta.xml` |
| 8 | Órdenes de laboratorio | `ui/laboratorio/LaboratorioFragment` | `fragment_laboratorio.xml` |
| 9 | Perfil y ajustes | `ui/ajustes/AjustesFragment` | `fragment_ajustes.xml` |

Otros archivos importantes:

- `MainActivity` — barra superior, barra inferior, menú lateral y navegación (`res/navigation/nav_graph.xml`).

### Animaciones

**Entre pantallas** (`res/anim`, usadas en `nav_graph.xml` y en `MainActivity.navegarSeccion()`):

| Cuándo | Animación |
|--------|-----------|
| Cambiar de sección (barra inferior, menú lateral, mosaicos del panel) | La pantalla anterior se desvanece y la nueva aparece creciendo un poco (`nav_seccion_entrar` / `nav_seccion_salir`) |
| Abrir un detalle o formulario (expediente, producto, registro, ajustes) | Entra desde la derecha; al regresar sale hacia la derecha (`nav_entrar`, `nav_salir`, `nav_regresar_*`) |

**Dentro de cada fragmento** (`util/Animaciones.java`, se llama al final de `onViewCreated`):

- Todas: el título sobre el degradado baja en cascada y la hoja blanca sube.
- Además, cada pantalla anima sus propias piezas:

| Pantalla | Además… |
|----------|---------|
| Inicio de sesión / Registro | Solo título en cascada y hoja |
| Panel | Íconos de arriba en cascada, tarjeta de avance crece, mosaicos uno tras otro y la barra de avance se llena |
| Pacientes / Laboratorio | Renglones uno tras otro (`anim/lista_entrada`) y el botón + con rebote |
| Inventario | Mosaicos del catálogo uno tras otro y el botón + |
| Expediente | Iniciales con rebote e historial uno tras otro |
| Detalle de producto | La foto crece y el pie con el precio sube al final |
| Punto de venta | El pie con el total sube; los productos del carrito aparecen y se desvanecen al agregar o quitar |
| Formulario de producto | Foto, botones y título en cascada |
| Perfil y ajustes | La foto crece |

- La entrada se hace solo la primera vez: al volver con "atrás" no se repite.
- Si el teléfono tiene las animaciones desactivadas (Opciones de desarrollador → Escala de animación), todo aparece al instante.

### Detalle de producto

Al tocar un mosaico del inventario se abre el detalle: foto grande, categoría, nombre, descripción y existencias, que se actualizan en tiempo real.

- **Vender** abre Punto de venta con ese producto ya agregado al carrito (argumento `productoId` de `VentaFragment`).
- El lápiz abre el formulario para editarlo. Si el producto se elimina, el detalle se cierra solo.

### Menú lateral (Navigation Drawer)

Se abre deslizando desde la orilla izquierda, con el botón ☰ de la barra superior o con el botón ☰ del panel.

- `activity_main.xml` — el `DrawerLayout` contiene el contenido normal y el `NavigationView` (el panel que sale).
- `nav_header.xml` — encabezado con foto, nombre, correo y "Óptica · Rol". `MainActivity.escucharUsuario()` lo llena con un listener en tiempo real sobre `users/{uid}` en Firestore: si cambias la foto en Ajustes, el menú se actualiza solo.
- `menu/menu_lateral.xml` — las secciones. Sus `id` son los mismos que los destinos del `nav_graph`, así `NavigationUI` navega y marca la sección actual. "Configuración" lleva `menuCategory="secondary"` para que "atrás" regrese a donde estabas.
- Por rol: el optometrista no ve Inventario ni Ventas (igual que en la barra inferior).
- Está bloqueado en el inicio de sesión y el registro. "Atrás" con el menú abierto lo cierra.
- "Cerrar sesión" pide confirmación y usa `MainActivity.cerrarSesion()`, que también usa la pantalla de Ajustes.
- `data/Sesion` — usuario y óptica que iniciaron sesión, y las colecciones de esa óptica.
- `data/Preferencias` — tema, modo sin imágenes, texto grande y fondo de pantalla (SharedPreferences).
- `model/` — clases que Firestore convierte en documentos (Usuario, Optica, Paciente, Consulta, Producto, Venta, Orden).
- `util/Validaciones` — reglas de contraseña segura y rangos de la graduación.
- `util/Imagenes` — cámara, subida a Storage y carga con Glide.
- `util/Pdf` — receta y nota de venta en PDF con la clase `PdfDocument` de Android.

## Estructura en Firestore

```
users/{uid}                         nombre, email, rol, opticaId, fotoUrl, codigoUsado
codigos/{codigo}                    opticaId
opticas/{opticaId}                  nombre, telefono, estado, direccion, metaSemanal, codigoInvitacion, logoUrl, ownerId
opticas/{opticaId}/pacientes/{id}   nombre, telefono, edad, fechaRegistro, ultimaConsulta, resumenGraduacion
   └─ consultas/{id}                graduación de cada ojo, DIP, agudeza, tipo de lente, observaciones
opticas/{opticaId}/productos/{id}   nombre, categoria, descripcion, precio, stock, stockMinimo, fotoUrl
opticas/{opticaId}/ventas/{id}      cliente, items, total, anticipo, saldo, metodoPago, vendedor, fecha
opticas/{opticaId}/ordenes/{id}     venta, paciente, descripcion, estatus y fecha de cada etapa
```

## Pendientes conocidos

- **Tema:** el control de Ajustes cambia entre claro, oscuro o el del teléfono. Si piden de 3 a 5 combinaciones de color, se agregan como temas extra en `themes.xml`.
- **Personal:** al unirse con un código, el empleado elige su puesto. Después el administrador puede cambiarlo en *Personal y roles*.
- **Sin internet:** Firestore guarda lo leído y muestra los datos. Una venta hecha sin conexión se guarda en el teléfono y se sube al volver la red, pero el botón queda en espera mientras tanto.
- **Existencias:** si dos personas venden la última pieza al mismo tiempo, el stock puede quedar en negativo; la app lo marca como "Agotado".
