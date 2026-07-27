-- Inventario, Fase 1: el kardex.
--
-- Cuatro tablas nuevas. Ninguna toca las existentes, así que la migración es
-- puramente aditiva: si algo saliera mal, el sistema de órdenes sigue igual.
--
-- La regla que gobierna el diseño: EL STOCK NO SE GUARDA COMO NÚMERO. Se
-- calcula sumando `movimientos_inventario.cantidad`. Por eso no existe una
-- columna `stock_actual` en `insumos` — sería un dato que puede contradecir su
-- propia historia, y cuando no cuadrara no habría forma de saber qué lo movió.
-- Es la misma idea de `eventos_orden`, aplicada a ingredientes.
--
-- Todo se cuenta en unidades ENTERAS (piezas o porciones). Sin gramos y sin
-- decimales: si un platillo necesitara "media porción", la porción está mal
-- definida y hay que partirla. En cuanto aparecen decimales, el conteo físico
-- y el sistema dejan de hablar el mismo idioma.

-- Los insumos que sí se controlan. De los 225 productos, el sistema solo mira
-- los que estén aquí (control selectivo / clasificación ABC): el 20% de los
-- insumos concentra el 80% del valor, y ese 20% es el que se vigila.
CREATE TABLE `insumos` (
  `id_insumos` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(255) NOT NULL,
  `unidad` enum('PIEZA','PORCION') NOT NULL,
  -- Umbral para la alerta de "ya casi se acaba". 0 = sin alerta.
  `stock_minimo` int NOT NULL DEFAULT 0,
  `activo` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id_insumos`),
  UNIQUE KEY `insumo_nombre_UNIQUE` (`nombre`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- La receta: cuánto de cada insumo consume cada platillo.
-- Se llena en la Fase 2; aquí solo queda la estructura lista.
-- Un producto sin renglones aquí simplemente no descuenta nada.
CREATE TABLE `producto_insumo` (
  `id_producto_insumo` bigint NOT NULL AUTO_INCREMENT,
  `id_producto` bigint NOT NULL,
  `id_insumo` bigint NOT NULL,
  `cantidad` int NOT NULL,
  PRIMARY KEY (`id_producto_insumo`),
  -- Un platillo no puede llevar dos renglones del mismo insumo: o son 2 piezas
  -- en un renglón, o es un error de captura.
  UNIQUE KEY `receta_producto_insumo_UNIQUE` (`id_producto`,`id_insumo`),
  KEY `receta_insumo_idx` (`id_insumo`),
  CONSTRAINT `receta_producto_fk` FOREIGN KEY (`id_producto`) REFERENCES `productos` (`id_productos`) ON DELETE RESTRICT,
  CONSTRAINT `receta_insumo_fk` FOREIGN KEY (`id_insumo`) REFERENCES `insumos` (`id_insumos`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- El kardex. Append-only por diseño: nada se edita ni se borra. Un error se
-- corrige con otro movimiento que lo compensa, igual que en contabilidad.
--
-- `cantidad` lleva SIGNO: positivo entra, negativo sale. Así el stock es un
-- SUM() y no hay que interpretar el tipo para saber en qué dirección va.
CREATE TABLE `movimientos_inventario` (
  `id_movimiento` bigint NOT NULL AUTO_INCREMENT,
  `id_insumo` bigint NOT NULL,
  -- INICIAL: el conteo del día que se prende el sistema (una sola vez por insumo)
  -- COMPRA:  llegó mercancía          (+)
  -- MERMA:   se echó a perder o se canceló ya cocinado  (-)
  -- VENTA:   descuento automático por receta, Fase 2    (-)
  -- AJUSTE:  diferencia que dejó un conteo físico       (+/-)
  `tipo` enum('INICIAL','COMPRA','MERMA','VENTA','AJUSTE') NOT NULL,
  `cantidad` int NOT NULL,
  `motivo` varchar(255) DEFAULT NULL,
  -- Solo lo llenan VENTA y la MERMA por cancelación: deja el rastro hasta la
  -- orden que movió el inventario.
  `id_orden` bigint DEFAULT NULL,
  `id_usuario` bigint NOT NULL,
  `fecha` datetime(6) NOT NULL,
  PRIMARY KEY (`id_movimiento`),
  KEY `movimiento_insumo_idx` (`id_insumo`),
  KEY `movimiento_fecha_idx` (`fecha`),
  KEY `movimiento_orden_idx` (`id_orden`),
  CONSTRAINT `movimiento_insumo_fk` FOREIGN KEY (`id_insumo`) REFERENCES `insumos` (`id_insumos`) ON DELETE RESTRICT,
  CONSTRAINT `movimiento_orden_fk` FOREIGN KEY (`id_orden`) REFERENCES `ordenes` (`id_ordenes`) ON DELETE RESTRICT,
  CONSTRAINT `movimiento_usuario_fk` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuarios`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- El conteo físico: alguien abre el refrigerador y cuenta. Es lo único que
-- convierte al kardex en información y no en un eco de lo que uno mismo tecleó.
CREATE TABLE `conteos_fisicos` (
  `id_conteo` bigint NOT NULL AUTO_INCREMENT,
  `fecha` datetime(6) NOT NULL,
  `id_usuario` bigint NOT NULL,
  `notas` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id_conteo`),
  KEY `conteo_usuario_idx` (`id_usuario`),
  CONSTRAINT `conteo_usuario_fk` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuarios`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- `cantidad_teorica` se guarda como foto del momento, aunque se pueda
-- recalcular: después del AJUSTE el kardex ya cuadra, y sin esta columna la
-- varianza histórica se perdería. Es el número que dice si algo se está yendo.
CREATE TABLE `conteo_detalle` (
  `id_conteo_detalle` bigint NOT NULL AUTO_INCREMENT,
  `id_conteo` bigint NOT NULL,
  `id_insumo` bigint NOT NULL,
  `cantidad_contada` int NOT NULL,
  `cantidad_teorica` int NOT NULL,
  PRIMARY KEY (`id_conteo_detalle`),
  UNIQUE KEY `conteo_insumo_UNIQUE` (`id_conteo`,`id_insumo`),
  KEY `conteo_detalle_insumo_idx` (`id_insumo`),
  CONSTRAINT `conteo_detalle_conteo_fk` FOREIGN KEY (`id_conteo`) REFERENCES `conteos_fisicos` (`id_conteo`) ON DELETE RESTRICT,
  CONSTRAINT `conteo_detalle_insumo_fk` FOREIGN KEY (`id_insumo`) REFERENCES `insumos` (`id_insumos`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
