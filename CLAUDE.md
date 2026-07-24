# CLAUDE.md — RestFood Backend

Guía para Claude Code al trabajar en este repo. El contexto general del proyecto está en `README.md` — leerlo primero.

## Stack
- Spring Boot 3.5, Java 17, Maven (wrapper en `api/`)
- MySQL, JPA/Hibernate, Spring Security + JWT, WebSocket (STOMP)
- Impresoras térmicas ESC/POS por red

## Comandos (correr desde `api/`)
```bash
./mvnw test                 # correr tests — SIEMPRE antes de mergear
./mvnw package              # generar el jar
./mvnw spring-boot:run      # levantar en desarrollo
```

## Flujo de trabajo (obligatorio)
1. Nunca commitear directo a `main`.
2. Crear rama: `feat/<descripcion>` o `fix/<descripcion>`.
3. Commits pequeños, en español, formato conventional: `feat: ...`, `fix: ...`, `refactor: ...`.
4. **Sin atribución de Claude en los commits** (sin Co-Authored-By).
5. Correr `./mvnw test` antes de abrir PR.
6. PR a `main` → revisión → merge.

## Reglas del proyecto
- Este sistema está EN PRODUCCIÓN en un restaurante real. No desplegar sin que David lo apruebe.
- **Todo cambio de esquema va en una migración de Flyway** (`api/src/main/resources/db/migration/`), nunca a mano en la base. El esquema completo se reconstruye desde `V0__esquema_inicial.sql`; producción lo salta por su marca de baseline en la versión 0.
- El CI levanta un MySQL vacío y deja que Flyway lo construya, así que una migración rota se detecta en el PR. Con `ddl-auto=validate`, si agregas un campo a una entidad y olvidas la migración, el CI falla.
- Explicar a David el porqué de cada práctica nueva: el objetivo es que aprenda el proceso, no solo el resultado. Las explicaciones largas van al handbook (`~/restfood-handbook/`), no solo al chat.
