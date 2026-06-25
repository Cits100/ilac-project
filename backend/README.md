# ILAC Backend - API REST con Spring Boot

Backend en Java Spring Boot que se comunica con el sistema ILAC de Interflon para gestionar órdenes de trabajo de mantenimiento.

## Arquitectura

```
┌─────────────────────────────────────────────────────────────────┐
│                        Cliente (Flutter)                        │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    AuthInterceptor (Filtro)                      │
│  • Registra TODAS las peticiones (método, URI, IP, User-Agent)  │
│  • Valida token de sesión para endpoints protegidos             │
│  • Registra tiempo de respuesta                                 │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    IlacController (REST)                         │
│  • Endpoints públicos: /api/dashboard                           │
│  • Endpoints protegidos: /api/comment, /api/complete-task, etc. │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   WorkOrderService (Dominio)                     │
│  • Lógica de negocio                                            │
│  • Parsing de órdenes, tareas, comentarios                      │
│  • Operaciones CRUD en ILAC                                     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                     IlacClient (Cliente HTTP)                    │
│  • Comunicación HTTP con ILAC                                   │
│  • Manejo de cookies, User-Agent, timeouts                      │
│  • Parsing básico de HTML y CSRF                                │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   SessionService (Sesiones)                      │
│  • Gestión de tokens UUID                                       │
│  • Almacenamiento de sesiones por usuario                       │
│  • Expiración de sesiones (2 horas)                             │
└─────────────────────────────────────────────────────────────────┘
```

## Requisitos

- Java 17 o superior
- Maven 3.6 o superior

## Instalación

### 1. Clonar el repositorio

```bash
git clone https://github.com/Cits100/ilac-project.git
cd ilac-project/backend
```

### 2. Configurar variables de entorno (opcional)

```bash
export ILAC_BASE_URL=https://ilac.interflon.net
```

O crear archivo `src/main/resources/application.properties`:

```properties
ilac.base-url=https://ilac.interflon.net
server.port=8080
```

### 3. Compilar y ejecutar

```bash
# Compilar
mvn clean compile

# Ejecutar
mvn spring-boot:run
```

El servidor estará disponible en `http://localhost:8080`

## Variables de Entorno

| Variable | Descripción | Valor por defecto |
|----------|-------------|-------------------|
| `ILAC_BASE_URL` | URL base del sistema ILAC | `https://ilac.interflon.net` |
| `SERVER_PORT` | Puerto del servidor | `8080` |

## API Endpoints

### Autenticación

#### Dashboard (Login + Obtener Datos)
```
POST /api/dashboard
Body: { "identity": "usuario@correo.com", "credential": "password" }
Response: { "success": true, "sessionToken": "uuid", "userName": "...", "workOrders": {...} }
```

### Endpoints Protegidos (requieren token)

Los endpoints protegidos requieren el token en el header:
```
Authorization: Bearer <session-token>
```

#### Obtener Comentarios
```
POST /api/task-comments
Body: { "taskId": "123456" }
Response: { "success": true, "comments": [...] }
```

#### Agregar Comentario
```
POST /api/comment
Body: { "taskId": "123456", "comment": "texto", "imageBase64": "opcional", "imageName": "opcional" }
Response: { "success": true, "message": "Comentario agregado exitosamente" }
```

#### Editar Comentario
```
POST /api/edit-comment
Body: { "commentId": "123456", "comment": "nuevo texto" }
Response: { "success": true, "message": "Comentario editado exitosamente" }
```

#### Marcar Tarea como Completada
```
POST /api/complete-task
Body: { "taskId": "123456" }
Response: { "success": true, "message": "Tarea marcada como completada" }
```

#### Rechazar Tarea
```
POST /api/reject-task
Body: { "taskId": "123456", "reason": "motivo del rechazo" }
Response: { "success": true, "message": "Tarea rechazada exitosamente" }
```

#### Aceptar Tarea
```
POST /api/accept-task
Body: { "taskId": "123456" }
Response: { "success": true, "message": "Tarea aceptada exitosamente" }
```

## Estructura del Proyecto

```
backend/
├── pom.xml
└── src/
    └── main/
        ├── java/com/ilac/
        │   ├── IlacApplication.java              # Punto de entrada
        │   ├── config/
        │   │   ├── AuthInterceptor.java          # Filtro de autenticación + logging
        │   │   └── WebMvcConfig.java             # Configuración MVC
        │   ├── controller/
        │   │   └── IlacController.java           # Endpoints REST
        │   ├── model/
        │   │   ├── LoginRequest.java             # DTO: credenciales
        │   │   ├── LoginResponse.java            # DTO: respuesta login
        │   │   ├── FullDashboardResponse.java    # DTO: dashboard completo
        │   │   ├── TokenRequest.java             # DTO: peticiones con token
        │   │   ├── WorkOrder.java                # Modelo: orden de trabajo
        │   │   ├── Task.java                     # Modelo: tarea
        │   │   ├── TaskDetail.java               # Modelo: detalle de tarea
        │   │   └── TaskComment.java              # Modelo: comentario
        │   └── service/
        │       ├── IlacClient.java               # Cliente HTTP para ILAC
        │       ├── SessionService.java           # Gestión de sesiones
        │       └── WorkOrderService.java         # Lógica de negocio
        └── resources/
            └── application.properties            # Configuración
```

## Dependencias

- **Spring Boot Starter Web** - Framework REST
- **Jsoup** - Parsing HTML
- **Lombok** - Reducción de boilerplate

## Características

### Autenticación por Token
- Login retorna token UUID
- Token se envía en header `Authorization: Bearer <token>`
- Sesiones expiran después de 2 horas
- Soporte multi-usuario simultáneo

### Logging
- Todas las peticiones HTTP registradas
- Tiempo de respuesta por endpoint
- Errores y excepciones detalladas
- IP y User-Agent del cliente

### Manejo de Errores
- ILAC retorna HTTP 500 después del login exitoso
- El backend maneja este caso automáticamente
- CSRF token se extrae de múltiples formatos (Unicode, escapado, normal)

## Usuario de Prueba

| Campo | Valor |
|-------|-------|
| Email | `ctapia@retailsbs.com` |
| Contraseña | `IlacTest1` |

## Desarrollo

### Compilar
```bash
mvn clean compile
```

### Ejecutar tests
```bash
mvn test
```

### Empaquetar
```bash
mvn package
```

## Licencia

Proyecto privado - Uso académico
