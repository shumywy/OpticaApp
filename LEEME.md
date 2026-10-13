# Óptica App — proyecto de Android Studio

App en Java + XML. Usa una sola Activity, 10 Fragments y Firebase (Auth, Firestore, Storage).

## Diseño

Sigue el estilo de referencia: fondo gris muy claro, tarjetas blancas con esquinas redondeadas, acento azul,
íconos dentro de círculos o cuadros grises, controles segmentados, switches azules y barra inferior sin fondo
detrás del ícono activo.

- Colores: `res/values/colors.xml` (claro) y `res/values-night/colors.xml` (oscuro). Cambiar `azul` cambia el acento en toda la app.
- Estilos reutilizables: `res/values/styles.xml` (Campo, Selector, Boton, Segmento, Filtro, Tarjeta, Fila, IconoPanel…).
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
| 6 | Inventario (+ formulario de producto) | `ui/inventario/InventarioFragment`, `ProductoFormFragment` | `fragment_inventario.xml`, `fragment_producto_form.xml` |
| 7 | Punto de venta | `ui/venta/VentaFragment` | `fragment_venta.xml` |
| 8 | Órdenes de laboratorio | `ui/laboratorio/LaboratorioFragment` | `fragment_laboratorio.xml` |
| 9 | Perfil y ajustes | `ui/ajustes/AjustesFragment` | `fragment_ajustes.xml` |

Otros archivos importantes:

- `MainActivity` — barra superior, barra inferior y navegación (`res/navigation/nav_graph.xml`).
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
