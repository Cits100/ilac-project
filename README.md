# ILAC Interflon - Sistema de Mantenimiento

Sistema completo para la gestión de órdenes de trabajo de mantenimiento de Interflon.

## Estructura del Proyecto

```
ilac-project/
├── backend/          # API REST en Spring Boot (Java)
├── mobile-app/       # Aplicación móvil en Flutter
└── README.md         # Este archivo
```

## Componentes

### 1. Backend (Spring Boot)
API REST que se comunica con el sistema ILAC de Interflon para:
- Autenticación de usuarios
- Obtener órdenes de trabajo (personales y del equipo)
- Obtener detalles de tareas
- Agregar comentarios con imágenes
- Marcar tareas como completadas

**Tecnologías:** Java 17, Spring Boot 3.2, Jsoup

📖 [Ver documentación del Backend](./backend/README.md)

### 2. Aplicación Móvil (Flutter)
Aplicación móvil que consume el backend para mostrar:
- Lista de órdenes de trabajo (equipo y personal)
- Detalle de tareas
- Comentarios con imágenes
- Soporte offline con cola de sincronización

**Tecnologías:** Flutter, SQLite, Material Design 3

📖 [Ver documentación de la App Móvil](./mobile-app/README.md)

## Requisitos Previos

- Java 17 o superior
- Maven 3.6 o superior
- Flutter 3.0 o superior
- Android Studio (para emulador Android)
- Cuenta de ILAC Interflon

## Inicio Rápido

### 1. Iniciar el Backend

```bash
cd backend

# Configurar variables de entorno (opcional)
export ILAC_BASE_URL=https://ilac.interflon.net

# Ejecutar
mvn spring-boot:run
```

El backend estará disponible en `http://localhost:8080`

### 2. Iniciar la App Móvil

```bash
cd mobile-app

# Instalar dependencias
flutter pub get

# Ejecutar en Chrome
flutter run -d chrome

# O en emulador Android
flutter run
```

## Variables de Entorno

| Variable | Descripción | Valor por defecto |
|----------|-------------|-------------------|
| `ILAC_BASE_URL` | URL base del sistema ILAC | `https://ilac.interflon.net` |

## Usuario de Prueba

| Campo | Valor |
|-------|-------|
| Email | `ctapia@retailsbs.com` |
| Contraseña | `IlacTest1` |

## API Endpoints

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/api/dashboard` | Login + obtener todas las órdenes |
| `POST` | `/api/comment` | Agregar comentario a tarea |
| `POST` | `/api/complete-task` | Marcar tarea como completada |

## Arquitectura

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Flutter App   │────▶│  Spring Boot    │────▶│  ILAC Interflon │
│   (Mobile)      │     │  (Backend)      │     │  (Web)          │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │
        ▼
┌─────────────────┐
│   SQLite DB     │
│   (Local)       │
└─────────────────┘
```

## Características

### Backend
- ✅ Soporte multi-usuario (sesiones concurrentes)
- ✅ Scraping de páginas web
- ✅ Manejo de errores (error 500 del sitio ILAC)
- ✅ Endpoints RESTful

### App Móvil
- ✅ Material Design 3
- ✅ Modo offline con cola de sincronización
- ✅ Almacenamiento local (SQLite)
- ✅ Credenciales encriptadas
- ✅ Cámara y galería para imágenes
- ✅ Persistencia de sesión

## Licencia

Proyecto privado - Uso académico

## Autor

Desarrollado como proyecto de título.
