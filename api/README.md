# RestFood API - Sistema de Gestión de Restaurante (Backend)

API que gestiona la lógica de negocio, persistencia y comunicación en tiempo real del sistema RestFood.

## Tecnologías

- **Java 17** + **Spring Boot 3.5.x**
- **Spring Security + JWT** — autenticación y autorización por roles
- **Spring Data JPA + MySQL 8** — persistencia
- **Flyway** — migraciones de base de datos
- **WebSockets (STOMP)** — notificaciones en tiempo real
- **Lombok** — reducción de boilerplate
- **SpringDoc OpenAPI (Swagger)** — documentación interactiva
- **Escpos-Coffee** — impresión de tickets térmicos ESC/POS

## Roles del sistema

| Rol | Acceso |
|-----|--------|
| `DEV` | Total |
| `ADMIN` | Total excepto dev |
| `CAJERO` | Órdenes y reportes |
| `MESERO` | Mesas y órdenes propias |
| `REPARTIDOR` | Órdenes para llevar |

## Endpoints principales

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/ordenes` | Abrir mesa/orden |
| `POST` | `/ordendetalles` | Sincronizar comanda |
| `PUT` | `/ordenes/{id}/cerrar` | Cobrar y cerrar mesa |
| `POST` | `/ordenes/{id}/reenviar-cocina` | Reenviar orden completa a cocina |
| `POST` | `/ordenes/{id}/reimprimir-ticket` | Reimprimir ticket de cliente |
| `GET` | `/admin?fecha=YYYY-MM-DD` | Reporte completo del día |
| `GET` | `/admin/cancelaciones?desde=&hasta=` | Cancelaciones por mesero |
| `GET` | `/ordenes/activa/{id_mesa}` | Orden activa de una mesa |

## WebSocket topics

| Topic | Descripción |
|-------|-------------|
| `/topic/mesas` | Estado de mesas en tiempo real |
| `/topic/cocina` | Tickets y acciones de cocina |
| `/topic/tickets` | Ticket de cliente al cerrar mesa |

## Impresoras

El sistema soporta hasta 3 impresoras térmicas configuradas en `application.properties`:

```properties
impresora.cocina1.nombre=POS-58 (1)   # Cocina principal
impresora.cocina2.nombre=POS-58 (2)   # Cocina secundaria (opcional)
impresora.tickets.nombre=POS-58 (3)   # Tickets de cliente
```

Preparado para migrar a TCP/IP: descomenta las líneas de IP/puerto en `application.properties`.

## Historial de eventos (`eventos_orden`)

Cada acción sobre una orden queda registrada automáticamente:

| Evento | Cuándo |
|--------|--------|
| `MESA_ABIERTA` | Al abrir una orden |
| `PLATILLO_NUEVO` | Al agregar un platillo |
| `PLATILLO_MODIFICADO` | Al cambiar cantidad o comentarios |
| `PLATILLO_CANCELADO` | Al eliminar un platillo |
| `MESA_CERRADA` | Al cobrar la mesa |

Útil para auditoría, reportes y entrenamiento de modelos de IA para detección de patrones.

## Requisitos

- MySQL 8.0+ con base de datos `restaurante`
- Variable de entorno `JWT_SECRET`

## Instalación

```bash
cd RestFoodB/api
./mvnw spring-boot:run
```

Documentación Swagger disponible en `http://localhost:8080/swagger-ui.html`
