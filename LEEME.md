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
3. Para dar de alta a tu equipo tienes dos caminos (menú lateral → **Administración**):
   - **Nueva cuenta**: escribes nombre, correo, contraseña temporal y puesto, y la cuenta queda lista sin que tú salgas de la tuya.
   - **Datos de la óptica** → copia o comparte el **código de invitación**; en otro teléfono la persona elige **Unirme con código**, lo pega y elige su puesto.

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
| 9 | Configuración (tema, texto grande, fondo, cerrar sesión) | `ui/ajustes/AjustesFragment` | `fragment_ajustes.xml` |
| 10 | Mi perfil (foto, nombre, contraseña) | `ui/administracion/PerfilFragment` | `fragment_perfil.xml` |
| 11 | Datos de la óptica (datos, meta, logotipo, código) | `ui/administracion/OpticaFragment` | `fragment_optica.xml` |
| 12 | Personal (equipo y roles) | `ui/administracion/PersonalFragment`, `PersonalAdapter` | `fragment_personal.xml`, `item_persona.xml` |
| 13 | Nueva cuenta | `ui/administracion/NuevaCuentaFragment` | `fragment_nueva_cuenta.xml` |
| — | Corte de caja (diálogo) | `ui/administracion/CorteDeCaja` | — |

Otros archivos importantes:

- `MainActivity` — barra superior, barra inferior, menú lateral y navegación (`res/navigation/nav_graph.xml`).

### Panel con efecto de vidrio

El Inicio (`fragment_panel.xml`) lleva unos lentes grandes que flotan detrás del contenido. La tarjeta de avance y los mosaicos son de "vidrio": blanco translúcido con borde fino, así que se ven los lentes a través.

- `ui/panel/FondoLentes.java` dibuja la imagen dos veces en el mismo lugar:
  - `drawable-nodpi/lentes_hero.webp` (nítida) **fuera** de la hoja.
  - `drawable-nodpi/lentes_hero_difuso.webp` (desenfocada) **dentro** de la hoja.

  Así parece vidrio esmerilado en cualquier versión de Android, sin librerías.
- Los lentes nunca se quedan quietos (`FondoLentes`):
  - **Entrada**: llegan desde la derecha con un pequeño rebote.
  - **Balanceo**: flotan arriba y abajo y se ladean apenas (±2.5°) en un ciclo de 6 s.
  - **Destello**: una vez por ciclo pasa un brillo diagonal por los cristales, como luz sobre el acetato.
  - **Inclinación**: con el acelerómetro se mueven unos 16 dp hacia donde inclinas el teléfono (efecto de profundidad).
  - **Toque**: al tocarlos dan una vuelta completa y un saltito.

  Todo se pausa al salir de la pantalla y se apaga si el teléfono tiene las animaciones desactivadas. Las constantes (`CICLO_MS`, `GIRO_GRADOS`, `INCLINACION_DP`…) están al principio de la clase.
- La etiqueta "Óptica · Rol" va en una píldora de vidrio blanca (`Texto.Categoria.Pildora`) para que se lea encima de los lentes.
- Fondos: `bg_vidrio`, `bg_vidrio_mosaico`, `bg_hoja_vidrio` y `bg_aurora` (resplandor morado). Sus colores (`vidrio_*`, `hoja_vidrio_*`, `aurora_*`) tienen versión para modo oscuro.
- Para cambiar la imagen, reemplaza los dos `.webp` por otra imagen con fondo transparente (y una copia borrosa). El tamaño se ajusta con `ESCALA` y la altura con `SOBRE_EL_ANCLA` en `FondoLentes`.

### Animaciones

**Entre pantallas** (`res/anim`, usadas en `nav_graph.xml` y en `MainActivity.navegarSeccion()`):

| Cuándo | Animación |
|--------|-----------|
| Cambiar de sección (barra inferior, menú lateral, mosaicos del panel) | La pantalla anterior se desvanece y la nueva aparece creciendo un poco (`nav_seccion_entrar` / `nav_seccion_salir`) |
| Abrir un detalle o formulario (expediente, producto, registro, pantallas del menú lateral) | Entra desde la derecha; al regresar sale hacia la derecha (`nav_entrar`, `nav_salir`, `nav_regresar_*`) |

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
| Mi perfil / Datos de la óptica | La foto o el logotipo crece y el botón de cámara aparece después |
| Personal | Renglones uno tras otro y el botón + con rebote |
| Configuración / Nueva cuenta | Solo título en cascada y hoja |

- La entrada se hace solo la primera vez: al volver con "atrás" no se repite.
- Si el teléfono tiene las animaciones desactivadas (Opciones de desarrollador → Escala de animación), todo aparece al instante.

### Detalle de producto

Al tocar un mosaico del inventario se abre el detalle: foto grande, categoría, nombre, descripción y existencias, que se actualizan en tiempo real.

- **Vender** abre Punto de venta con ese producto ya agregado al carrito (argumento `productoId` de `VentaFragment`).
- El lápiz abre el formulario para editarlo. Si el producto se elimina, el detalle se cierra solo.

### Menú lateral (Navigation Drawer)

Se abre deslizando desde la orilla izquierda, con el botón ☰ de la barra superior o con el botón ☰ del panel.

- `activity_main.xml` — el `DrawerLayout` contiene el contenido normal y el `NavigationView` (el panel que sale).
- `nav_header.xml` — encabezado con foto, nombre, correo y "Óptica · Rol". `MainActivity.escucharUsuario()` lo llena con un listener en tiempo real sobre `users/{uid}` en Firestore: si cambias la foto en Mi perfil, el menú se actualiza solo. Tocar el encabezado abre Mi perfil.
- `menu/menu_lateral.xml` — el menú lateral es para **cuenta y administración**; las secciones de trabajo (Inicio, Pacientes, Inventario, Ventas, Laboratorio) se quedan en la barra inferior:

  | Grupo | Opciones | Quién lo ve |
  |-------|----------|-------------|
  | Mi cuenta | Mi perfil · Configuración | Todos |
  | **Administración** | Datos de la óptica · Personal · Nueva cuenta · Corte de caja | Solo administrador |
  | — | Cerrar sesión | Todos |

- Sus `id` son los destinos del `nav_graph`. Todas llevan `menuCategory="secondary"`: se abren encima de la sección donde estabas y "atrás" regresa a ella; si pasas de una pantalla del menú a otra, la nueva reemplaza a la anterior para que no se amontonen. `MainActivity.marcarMenuLateral()` marca la opción de la pantalla actual (incluido el submenú de Administración).
- **Nueva cuenta** crea la cuenta en una segunda instancia de Firebase (`"altas"`), así el administrador no pierde su sesión. Esa instancia guarda `users/{uid}` con el código de invitación de la óptica, igual que si la persona se uniera sola, por eso las reglas de Firestore no cambian. Si el documento no se puede guardar, la cuenta se borra para poder intentarlo de nuevo.
- **Personal** escucha en tiempo real a la gente de la óptica. Al tocar a alguien: cambiar su rol o mandarle el correo para crear otra contraseña. Nadie puede cambiar su propio rol.
- **Corte de caja** suma las ventas del día y el dinero cobrado por método de pago.
- Está bloqueado en el inicio de sesión y el registro. "Atrás" con el menú abierto lo cierra.
- "Cerrar sesión" pide confirmación y usa `MainActivity.cerrarSesion()`, que también usa Configuración.
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

- **Tema:** el control de Configuración cambia entre claro, oscuro o el del teléfono. Si piden de 3 a 5 combinaciones de color, se agregan como temas extra en `themes.xml`.
- **Personal:** al unirse con un código, el empleado elige su puesto. Después el administrador puede cambiarlo en *Personal y roles*.
- **Sin internet:** Firestore guarda lo leído y muestra los datos. Una venta hecha sin conexión se guarda en el teléfono y se sube al volver la red, pero el botón queda en espera mientras tanto.
- **Existencias:** si dos personas venden la última pieza al mismo tiempo, el stock puede quedar en negativo; la app lo marca como "Agotado".
