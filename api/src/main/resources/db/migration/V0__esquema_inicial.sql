-- Esquema inicial de RestFood, extraído de producción el 2026-07-24.
--
-- Punto de partida de todo el historial de migraciones: reconstruye desde cero
-- las 7 tablas del sistema. Producción NO ejecuta este script — su marca de
-- baseline de Flyway está en la versión 0, así que lo salta por diseño; solo
-- corre en bases nuevas (CI, staging, un servidor de reemplazo).
--
-- Ojo: aquí `productos` NO lleva la columna `eliminado` a propósito. Esa la
-- agrega la V1, que es donde pertenece históricamente. V0 + V1 = producción.
--
-- Sin valores AUTO_INCREMENT (dependen de los datos, no del esquema) y sin la
-- tabla flyway_schema_history (la administra Flyway).

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `categorias` (
  `id_categorias` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(255) DEFAULT NULL,
  `impresora` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id_categorias`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `eventos_orden` (
  `id_evento` bigint NOT NULL AUTO_INCREMENT,
  `cantidad_anterior` int DEFAULT NULL,
  `cantidad_nueva` int DEFAULT NULL,
  `comentarios_anterior` varchar(255) DEFAULT NULL,
  `comentarios_nuevo` varchar(255) DEFAULT NULL,
  `id_mesa` bigint DEFAULT NULL,
  `nombre_mesero` varchar(255) NOT NULL,
  `nombre_producto` varchar(255) DEFAULT NULL,
  `precio_unitario` decimal(38,2) DEFAULT NULL,
  `timestamp` datetime(6) NOT NULL,
  `tipo_evento` enum('MESA_ABIERTA','MESA_CERRADA','MESA_CANCELADA','PLATILLO_CANCELADO','PLATILLO_MODIFICADO','PLATILLO_NUEVO') NOT NULL,
  `id_orden` bigint NOT NULL,
  `id_usuario` bigint NOT NULL,
  PRIMARY KEY (`id_evento`),
  KEY `FKfcmtxrmo7gha11x2ruraej6fq` (`id_orden`),
  KEY `FKo8u7g7v43cpgh3jlt52wrhbc` (`id_usuario`),
  CONSTRAINT `FKfcmtxrmo7gha11x2ruraej6fq` FOREIGN KEY (`id_orden`) REFERENCES `ordenes` (`id_ordenes`),
  CONSTRAINT `FKo8u7g7v43cpgh3jlt52wrhbc` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuarios`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mesas` (
  `id_mesas` bigint NOT NULL AUTO_INCREMENT,
  `numero` varchar(255) DEFAULT NULL,
  `estado` varchar(20) NOT NULL DEFAULT 'LIBRE',
  PRIMARY KEY (`id_mesas`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `orden_detalle` (
  `id_detalle` bigint NOT NULL AUTO_INCREMENT,
  `cantidad` int NOT NULL,
  `precio_unitario` decimal(38,2) DEFAULT NULL,
  `subtotal` decimal(38,2) DEFAULT NULL,
  `comentarios` varchar(255) DEFAULT NULL,
  `id_orden` bigint NOT NULL,
  `id_producto` bigint NOT NULL,
  PRIMARY KEY (`id_detalle`),
  KEY `id_orden_idx` (`id_orden`),
  KEY `id_producto_idx` (`id_producto`),
  CONSTRAINT `id_orden` FOREIGN KEY (`id_orden`) REFERENCES `ordenes` (`id_ordenes`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `id_producto` FOREIGN KEY (`id_producto`) REFERENCES `productos` (`id_productos`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `ordenes` (
  `id_ordenes` bigint NOT NULL AUTO_INCREMENT,
  `fecha_apertura` datetime NOT NULL,
  `estatus` varchar(20) NOT NULL,
  `total` decimal(38,2) DEFAULT NULL,
  `id_usuario` bigint NOT NULL,
  `id_mesa` bigint DEFAULT NULL,
  `tipo` varchar(20) NOT NULL,
  `fecha_cierre` datetime(6) DEFAULT NULL,
  `servicio` enum('COMIDA','DESAYUNO') DEFAULT NULL,
  `numero_comanda` int NOT NULL,
  PRIMARY KEY (`id_ordenes`),
  KEY `id_usuario_idx` (`id_usuario`),
  KEY `id_mesa_idx` (`id_mesa`),
  CONSTRAINT `id_mesa` FOREIGN KEY (`id_mesa`) REFERENCES `mesas` (`id_mesas`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `id_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuarios`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `productos` (
  `id_productos` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(255) DEFAULT NULL,
  `precio_comida` decimal(38,2) DEFAULT NULL,
  `precio_desayuno` decimal(38,2) DEFAULT NULL,
  `disponibilidad` bit(1) DEFAULT b'1',
  `id_categoria` bigint NOT NULL,
  PRIMARY KEY (`id_productos`),
  KEY `id_categoria_idx` (`id_categoria`),
  CONSTRAINT `id_categoria` FOREIGN KEY (`id_categoria`) REFERENCES `categorias` (`id_categorias`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `usuarios` (
  `id_usuarios` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(255) DEFAULT NULL,
  `rol` varchar(45) NOT NULL,
  `contrasena` varchar(255) NOT NULL,
  `estatus` bit(1) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `seccion` int DEFAULT NULL,
  PRIMARY KEY (`id_usuarios`),
  UNIQUE KEY `email_UNIQUE` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
