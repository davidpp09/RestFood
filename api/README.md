# 🍽️ RestFood API - Sistema de Gestión de Restaurante (Backend)

Esta es la API robusta encargada de gestionar toda la lógica de negocio, persistencia de datos y comunicación en tiempo real para el sistema RestFood.

## 🚀 Tecnologías Utilizadas
*   **Java 17** y **Spring Boot 3.5.x**
*   **Spring Security & JWT:** Autenticación y autorización segura.
*   **Spring Data JPA:** Gestión de base de datos MySQL.
*   **Flyway:** Migraciones de base de datos automatizadas.
*   **WebSockets (STOMP):** Notificaciones en tiempo real para cocina y mesas.
*   **Lombok:** Reducción de código repetitivo.
*   **SpringDoc OpenAPI (Swagger):** Documentación interactiva de la API.
*   **Escpos-Coffee:** Soporte para impresión de tickets térmicos.

## 🛠️ Configuración y Requisitos
1.  **Base de Datos:** Requiere MySQL 8.0+. Crea una base de datos llamada `restaurante`.
2.  **Variables de Entorno:**
    *   `JWT_SECRET`: Clave secreta para la firma de tokens (ej: `mi_clave_secreta_123`).
3.  **Archivo `application.properties`:**
    *   Asegúrate de configurar `spring.datasource.username` y `spring.datasource.password`.

## 📦 Instalación y Ejecución
1.  Clona el repositorio.
2.  Navega a la carpeta `RestFoodB/api`.
3.  Ejecuta el proyecto con Maven:
    ```bash
    ./mvnw spring-boot:run
    ```

## 📖 Documentación de la API
Una vez que el servidor esté corriendo, puedes acceder a la documentación interactiva en:
`http://localhost:8080/swagger-ui.html`

## 🖨️ Soporte de Impresión
El sistema está preparado para impresoras USB locales y tiene soporte pre-configurado para migrar a impresoras de red (TCP/IP) simplemente ajustando el `application.properties`.
