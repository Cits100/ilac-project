# ILAC Mobile App - Aplicación Flutter

Aplicación móvil en Flutter para la gestión de órdenes de trabajo de mantenimiento de Interflon.

## Requisitos

- Flutter 3.0 o superior
- Android Studio (para emulador Android)
- Chrome (para pruebas web)

## Instalación

### 1. Clonar el repositorio

```bash
git clone https://github.com/Cits100/ilac-project.git
cd ilac-project/mobile-app
```

### 2. Instalar dependencias

```bash
flutter pub get
```

### 3. Ejecutar la aplicación

#### En Chrome (recomendado para pruebas):
```bash
flutter run -d chrome
```

#### En emulador Android:
```bash
flutter run
```

#### En Windows:
```bash
flutter run -d windows
```

## Configuración del Backend

Editar el archivo `lib/services/api_service.dart`:

```dart
// Para emulador Android usar 10.0.2.2
static const String baseUrl = 'http://10.0.2.2:8080/api';

// Para Chrome/Windows usar localhost
static const String baseUrl = 'http://localhost:8080/api';

// Para dispositivo real usar IP del servidor
static const String baseUrl = 'http://192.168.1.XXX:8080/api';
```

## Variables de Entorno

No requiere variables de entorno. La configuración se realiza directamente en el código.

## Estructura del Proyecto

```
mobile-app/
├── lib/
│   ├── main.dart
│   ├── models/
│   │   └── work_order.dart
│   ├── services/
│   │   ├── api_service.dart
│   │   ├── auth_service.dart
│   │   ├── connectivity_service.dart
│   │   └── database_service.dart
│   ├── screens/
│   │   ├── login/
│   │   │   └── login_screen.dart
│   │   ├── dashboard/
│   │   │   └── dashboard_screen.dart
│   │   ├── work_order/
│   │   │   └── work_order_list_screen.dart
│   │   └── task/
│   │       ├── task_list_screen.dart
│   │       └── task_detail_screen.dart
│   ├── widgets/
│   └── utils/
│       └── app_theme.dart
├── android/
├── ios/
├── web/
├── windows/
├── linux/
├── macos/
└── pubspec.yaml
```

## Base de Datos Local (SQLite)

La aplicación utiliza SQLite para almacenamiento local:

| Tabla | Descripción |
|-------|-------------|
| `work_orders` | Órdenes de trabajo |
| `tasks` | Tareas de cada orden |
| `task_details` | Detalles de cada tarea |
| `offline_queue` | Cola de acciones pendientes |
| `user_session` | Sesión del usuario |

## Características

### Autenticación
- Login con credenciales
- Persistencia de sesión
- Credenciales encriptadas en almacenamiento seguro

### Órdenes de Trabajo
- Lista de órdenes del equipo
- Lista de órdenes personales
- Indicador de tareas completadas

### Tareas
- Detalle completo de cada tarea
- Información de ubicación, máquina, producto
- Estado de completitud

### Comentarios
- Agregar texto
- Adjuntar imagen (cámara o galería)
- Envío inmediato o cola offline

### Modo Offline
- Datos almacenados localmente
- Cola de sincronización
- Procesamiento automático al recuperar conexión

## Permisos

La aplicación solicita los siguientes permisos:

- **Cámara**: Para tomar fotos de comentarios
- **Almacenamiento**: Para seleccionar imágenes de la galería

## Colores del Tema

| Elemento | Color |
|----------|-------|
| Primary | Rojo (#D32F2F) |
| Background | Negro (#212121) |
| Surface | Gris Oscuro (#424242) |
| Text | Blanco (#FFFFFF) |

## Usuario de Prueba

| Campo | Valor |
|-------|-------|
| Email | `ctapia@retailsbs.com` |
| Contraseña | `IlacTest1` |

## Dependencias Principales

- `http`: Comunicación HTTP
- `sqflite`: Base de datos SQLite
- `flutter_secure_storage`: Almacenamiento seguro
- `connectivity_plus`: Detección de conexión
- `image_picker`: Selección de imágenes
- `permission_handler`: Manejo de permisos

## Desarrollo

### Analizar código
```bash
flutter analyze
```

### Ejecutar tests
```bash
flutter test
```

### Build para producción

#### Android:
```bash
flutter build apk
```

#### iOS:
```bash
flutter build ios
```

#### Web:
```bash
flutter build web
```

#### Windows:
```bash
flutter build windows
```

## Solución de Problemas

### Error de conexión
- Verificar que el backend esté ejecutándose
- Verificar la URL en `api_service.dart`
- Para Android emulador usar `10.0.2.2` en lugar de `localhost`

### Permisos denegados
- Verificar permisos en configuración del dispositivo
- Reiniciar la aplicación

### Datos no se actualizan
- Hacer pull-to-refresh en la pantalla principal
- Verificar conexión a internet

## Licencia

Proyecto privado - Uso académico
