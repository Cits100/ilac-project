# ILAC Backend - API REST con Spring Boot

Backend en Java Spring Boot que se comunica con el sistema ILAC de Interflon para gestionar órdenes de trabajo de mantenimiento.

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

### 1. Dashboard (Login + Obtener Datos)

**Endpoint:** `POST /api/dashboard`

**Body:**
```json
{
  "identity": "usuario@correo.com",
  "credential": "password"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "userName": "usuario@correo.com",
  "workOrders": {
    "teamWorkOrders": [...],
    "myWorkOrders": [...]
  }
}
```

### 2. Agregar Comentario

**Endpoint:** `POST /api/comment`

**Body:**
```json
{
  "identity": "usuario@correo.com",
  "credential": "password",
  "taskId": "4440612",
  "comment": "Texto del comentario",
  "imageBase64": "opcional_base64_imagen",
  "imageName": "imagen.jpg"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Comment added successfully"
}
```

### 3. Marcar Tarea como Completada

**Endpoint:** `POST /api/complete-task`

**Body:**
```json
{
  "identity": "usuario@correo.com",
  "credential": "password",
  "taskId": "4440612"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Task marked as completed"
}
```

## Estructura del Proyecto

```
backend/
├── pom.xml
└── src/
    └── main/
        ├── java/com/ilac/
        │   ├── IlacApplication.java
        │   ├── controller/
        │   │   └── IlacController.java
        │   ├── model/
        │   │   ├── LoginRequest.java
        │   │   ├── LoginResponse.java
        │   │   ├── FullDashboardResponse.java
        │   │   ├── CommentRequest.java
        │   │   ├── TaskActionRequest.java
        │   │   ├── WorkOrder.java
        │   │   ├── Task.java
        │   │   └── TaskDetail.java
        │   └── service/
        │       ├── SessionService.java
        │       └── WorkOrderService.java
        └── resources/
            └── application.properties
```

## Dependencias

- Spring Boot Starter Web
- Jsoup (HTML parsing)
- Lombok (reducción de boilerplate)

## Multi-Soporte de Usuarios

El backend soporta múltiples usuarios simultáneos. Las sesiones se almacenan por usuario y expiran después de 2 horas.

## Manejo de Errores

El sistema ILAC de Interflon retorna un error HTTP 500 después del login exitoso. El backend maneja este caso:
1. Ignora el error 500
2. Navega de vuelta a la página principal
3. Verifica si el login fue exitoso

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
