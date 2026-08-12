-- =============================================================================
-- RestFood — Biblioteca de consultas de análisis
-- =============================================================================
--
-- Todas las consultas de este archivo fueron ejecutadas contra la base de
-- produccion el 2026-07-27. Corren tal cual en MySQL 8 con only_full_group_by.
--
-- TRES REGLAS QUE SE APLICAN EN TODAS. Ver docs/analitica/catalogo-datos.md.
--
--   1. TODA fecha lleva `- INTERVAL 6 HOUR`.
--      La base guarda en UTC pero la app produce hora local (CST, UTC-6).
--      Sin la resta, las horas pico salen corridas y las ventas de la noche
--      se cargan al dia siguiente. Mexico no usa horario de verano: el
--      desfase es fijo.
--
--   2. Ventas => `estatus = 'PAGADA'`.
--      Hay 149 canceladas (total 0) y 11 zombis en PREPARANDO que suman
--      $2,140 fantasma.
--
--   3. Ingresos => `orden_detalle.subtotal`, nunca `productos.precio_*`.
--      El precio del detalle es la foto del momento de la venta.
--
-- La ventana de operacion real empieza el 2026-07-20. Antes hay dias sueltos
-- de pruebas. Donde importa, las consultas lo filtran.
-- =============================================================================


-- =============================================================================
-- BLOQUE 1 — VENTAS
-- =============================================================================

-- 1.1 Ventas por dia (dia local, no UTC)
SELECT DATE(o.fecha_cierre - INTERVAL 6 HOUR)              AS dia,
       COUNT(*)                                            AS ordenes,
       ROUND(SUM(o.total), 2)                              AS ingreso,
       ROUND(AVG(o.total), 2)                              AS ticket_promedio
FROM ordenes o
WHERE o.estatus = 'PAGADA'
GROUP BY dia
ORDER BY dia DESC;


-- 1.2 Ventas por dia partidas por servicio (desayuno vs comida)
SELECT DATE(o.fecha_cierre - INTERVAL 6 HOUR)                              AS dia,
       ROUND(SUM(CASE WHEN o.servicio = 'DESAYUNO' THEN o.total END), 2)   AS desayuno,
       ROUND(SUM(CASE WHEN o.servicio = 'COMIDA'   THEN o.total END), 2)   AS comida,
       ROUND(SUM(o.total), 2)                                              AS total
FROM ordenes o
WHERE o.estatus = 'PAGADA'
GROUP BY dia
ORDER BY dia DESC;


-- 1.3 Mesa vs Para Llevar
SELECT o.tipo,
       COUNT(*)                AS ordenes,
       ROUND(SUM(o.total), 2)  AS ingreso,
       ROUND(AVG(o.total), 2)  AS ticket_promedio
FROM ordenes o
WHERE o.estatus = 'PAGADA'
GROUP BY o.tipo;


-- 1.4 Dia de la semana (para saber que dias son fuertes)
--     Con ~6 dias de operacion real esto todavia es indicativo, no concluyente.
SELECT DAYOFWEEK(o.fecha_cierre - INTERVAL 6 HOUR)        AS num_dia,  -- 1=domingo
       DAYNAME(o.fecha_cierre - INTERVAL 6 HOUR)          AS dia,
       COUNT(DISTINCT DATE(o.fecha_cierre - INTERVAL 6 HOUR)) AS dias_observados,
       COUNT(*)                                           AS ordenes,
       ROUND(SUM(o.total), 2)                             AS ingreso
FROM ordenes o
WHERE o.estatus = 'PAGADA'
  AND o.fecha_cierre >= '2026-07-20'
GROUP BY num_dia, dia
ORDER BY num_dia;


-- =============================================================================
-- BLOQUE 2 — HORAS PICO
-- =============================================================================

-- 2.1 Curva de demanda por hora local, separada por servicio.
--     Usa fecha_apertura: la pregunta es cuando LLEGA el cliente, no cuando paga.
SELECT hora_local,
       SUM(CASE WHEN servicio = 'DESAYUNO' THEN n ELSE 0 END) AS desayuno,
       SUM(CASE WHEN servicio = 'COMIDA'   THEN n ELSE 0 END) AS comida,
       SUM(n)                                                 AS total
FROM (
  SELECT HOUR(o.fecha_apertura - INTERVAL 6 HOUR) AS hora_local,
         o.servicio,
         COUNT(*)                                 AS n
  FROM ordenes o
  WHERE o.estatus = 'PAGADA'
    AND o.fecha_apertura >= '2026-07-20'
  GROUP BY hora_local, o.servicio
) t
GROUP BY hora_local
ORDER BY hora_local;


-- 2.2 Ingreso por hora local — donde esta el dinero, no solo el volumen
SELECT HOUR(o.fecha_cierre - INTERVAL 6 HOUR) AS hora_local,
       COUNT(*)                               AS ordenes,
       ROUND(SUM(o.total), 2)                 AS ingreso
FROM ordenes o
WHERE o.estatus = 'PAGADA'
  AND o.fecha_cierre >= '2026-07-20'
GROUP BY hora_local
ORDER BY hora_local;


-- 2.3 Mapa de calor dia x hora (para una vista tipo heatmap)
SELECT DATE(o.fecha_apertura - INTERVAL 6 HOUR)  AS dia,
       HOUR(o.fecha_apertura - INTERVAL 6 HOUR)  AS hora_local,
       COUNT(*)                                  AS ordenes,
       ROUND(SUM(o.total), 2)                    AS ingreso
FROM ordenes o
WHERE o.estatus = 'PAGADA'
  AND o.fecha_apertura >= '2026-07-20'
GROUP BY dia, hora_local
ORDER BY dia, hora_local;


-- =============================================================================
-- BLOQUE 3 — PRODUCTOS
-- =============================================================================

-- 3.1 Ranking de productos. Trae unidades E ingreso porque NO dan el mismo orden.
SELECT p.nombre                    AS producto,
       c.nombre                    AS categoria,
       SUM(d.cantidad)             AS unidades,
       ROUND(SUM(d.subtotal), 2)   AS ingreso,
       ROUND(SUM(d.subtotal) / SUM(d.cantidad), 2) AS precio_promedio,
       COUNT(DISTINCT d.id_orden)  AS ordenes_en_que_aparece
FROM orden_detalle d
JOIN ordenes   o ON o.id_ordenes   = d.id_orden AND o.estatus = 'PAGADA'
JOIN productos p ON p.id_productos = d.id_producto
JOIN categorias c ON c.id_categorias = p.id_categoria
GROUP BY p.id_productos, p.nombre, c.nombre
ORDER BY ingreso DESC;


-- 3.2 Productos del menu actual que NUNCA se han vendido.
--     Filtra eliminado=0: la pregunta es sobre el menu vigente.
SELECT p.nombre     AS producto,
       c.nombre     AS categoria,
       p.precio_comida,
       p.disponibilidad
FROM productos p
JOIN categorias c ON c.id_categorias = p.id_categoria
WHERE p.eliminado = 0
  AND NOT EXISTS (
        SELECT 1
        FROM orden_detalle d
        JOIN ordenes o ON o.id_ordenes = d.id_orden AND o.estatus = 'PAGADA'
        WHERE d.id_producto = p.id_productos)
ORDER BY c.nombre, p.nombre;


-- 3.3 Ingreso y mix por categoria
SELECT c.nombre                                     AS categoria,
       c.impresora,
       COUNT(DISTINCT p.id_productos)               AS productos_en_menu,
       COALESCE(SUM(d.cantidad), 0)                 AS unidades,
       ROUND(COALESCE(SUM(d.subtotal), 0), 2)       AS ingreso
FROM categorias c
LEFT JOIN productos p ON p.id_categoria = c.id_categorias AND p.eliminado = 0
LEFT JOIN orden_detalle d ON d.id_producto = p.id_productos
LEFT JOIN ordenes o ON o.id_ordenes = d.id_orden AND o.estatus = 'PAGADA'
GROUP BY c.id_categorias, c.nombre, c.impresora
ORDER BY ingreso DESC;


-- 3.4 Que se vende en desayuno vs en comida.
--     Detecta productos que solo funcionan en un servicio.
SELECT p.nombre AS producto,
       SUM(CASE WHEN o.servicio = 'DESAYUNO' THEN d.cantidad ELSE 0 END) AS uds_desayuno,
       SUM(CASE WHEN o.servicio = 'COMIDA'   THEN d.cantidad ELSE 0 END) AS uds_comida,
       SUM(d.cantidad)                                                   AS uds_total
FROM orden_detalle d
JOIN ordenes   o ON o.id_ordenes   = d.id_orden AND o.estatus = 'PAGADA'
JOIN productos p ON p.id_productos = d.id_producto
GROUP BY p.id_productos, p.nombre
HAVING uds_total >= 5
ORDER BY uds_total DESC;


-- 3.5 Evolucion del precio de venta de un producto.
--     Posible porque orden_detalle guarda el precio del momento.
SELECT p.nombre                  AS producto,
       d.precio_unitario,
       COUNT(*)                  AS veces,
       MIN(DATE(o.fecha_cierre - INTERVAL 6 HOUR)) AS desde,
       MAX(DATE(o.fecha_cierre - INTERVAL 6 HOUR)) AS hasta
FROM orden_detalle d
JOIN ordenes   o ON o.id_ordenes   = d.id_orden AND o.estatus = 'PAGADA'
JOIN productos p ON p.id_productos = d.id_producto
GROUP BY p.id_productos, p.nombre, d.precio_unitario
HAVING COUNT(*) > 0
ORDER BY p.nombre, d.precio_unitario;


-- =============================================================================
-- BLOQUE 4 — EMPLEADOS
-- =============================================================================

-- 4.1 Ventas por empleado
SELECT u.nombre                  AS empleado,
       u.rol,
       COUNT(*)                  AS ordenes,
       ROUND(SUM(o.total), 2)    AS ingreso,
       ROUND(AVG(o.total), 2)    AS ticket_promedio
FROM ordenes o
JOIN usuarios u ON u.id_usuarios = o.id_usuario
WHERE o.estatus = 'PAGADA'
  AND o.fecha_cierre >= '2026-07-20'
GROUP BY u.id_usuarios, u.nombre, u.rol
ORDER BY ingreso DESC;


-- 4.2 Ordenes abandonadas por empleado. LEER ANTES DE USAR.
--
--     Esto NO mide cancelaciones de ventas. El dialogo de Nueva Entrega crea
--     la orden en la base al abrirse; si se cierra sin enviar a cocina, la
--     interfaz la cancela para no dejar una orden fantasma. De 152 canceladas
--     historicas, solo 3 llegaron a tener un platillo.
--
--     Por eso los repartidores salen altisimo (103 casos, vida promedio 122
--     segundos, cero platillos siempre) y los meseros bajo: es la pantalla,
--     no la persona. NO uses esta consulta para comparar desempenno.
--     Para cancelaciones reales, ver 4.5.
SELECT u.nombre                                                              AS empleado,
       u.rol,
       COUNT(*)                                                              AS ordenes_totales,
       SUM(CASE WHEN o.estatus = 'CANCELADA' THEN 1 ELSE 0 END)              AS canceladas,
       ROUND(100.0 * SUM(CASE WHEN o.estatus = 'CANCELADA' THEN 1 ELSE 0 END)
                   / COUNT(*), 1)                                            AS pct_cancelacion
FROM ordenes o
JOIN usuarios u ON u.id_usuarios = o.id_usuario
WHERE o.fecha_apertura >= '2026-07-20'
GROUP BY u.id_usuarios, u.nombre, u.rol
ORDER BY pct_cancelacion DESC;


-- 4.5 Cancelaciones REALES: ordenes que llegaron a tener platillos y aun asi
--     se cancelaron. Estas si son eventos de negocio. Son 3 en todo el
--     historico, contra 149 abandonos vacios.
SELECT o.id_ordenes,
       DATE(o.fecha_apertura - INTERVAL 6 HOUR) AS dia,
       o.tipo, o.servicio,
       u.nombre                                 AS empleado,
       u.rol,
       COUNT(e.id_evento)                       AS platillos_capturados,
       ROUND(SUM(e.precio_unitario * e.cantidad_nueva), 2) AS valor_estimado
FROM ordenes o
JOIN usuarios u      ON u.id_usuarios = o.id_usuario
JOIN eventos_orden e ON e.id_orden    = o.id_ordenes AND e.tipo_evento = 'PLATILLO_NUEVO'
WHERE o.estatus = 'CANCELADA'
GROUP BY o.id_ordenes, dia, o.tipo, o.servicio, u.nombre, u.rol
ORDER BY dia DESC;


-- 4.3 Que platillos se cancelan mas (desde la bitacora de eventos).
--     Solo captura cancelaciones de PLATILLO individual, no de mesa completa.
SELECT e.nombre_producto        AS producto,
       COUNT(*)                 AS veces_cancelado,
       COUNT(DISTINCT e.nombre_mesero) AS meseros_distintos
FROM eventos_orden e
WHERE e.tipo_evento = 'PLATILLO_CANCELADO'
GROUP BY e.nombre_producto
ORDER BY veces_cancelado DESC;


-- 4.4 Productividad por hora trabajada (proxy: horas con actividad)
SELECT u.nombre                                                     AS empleado,
       COUNT(DISTINCT DATE(o.fecha_apertura - INTERVAL 6 HOUR))     AS dias_activo,
       COUNT(*)                                                     AS ordenes,
       ROUND(COUNT(*) / COUNT(DISTINCT DATE(o.fecha_apertura - INTERVAL 6 HOUR)), 1)
                                                                    AS ordenes_por_dia
FROM ordenes o
JOIN usuarios u ON u.id_usuarios = o.id_usuario
WHERE o.estatus = 'PAGADA'
  AND o.fecha_apertura >= '2026-07-20'
GROUP BY u.id_usuarios, u.nombre
ORDER BY ordenes_por_dia DESC;


-- =============================================================================
-- BLOQUE 5 — MESAS Y TIEMPOS
-- =============================================================================

-- 5.1 Rotacion por mesa: cuantas veces se usa y cuanto deja
SELECT m.numero                                    AS mesa,
       COUNT(*)                                    AS veces_usada,
       ROUND(SUM(o.total), 2)                      AS ingreso,
       ROUND(AVG(o.total), 2)                      AS ticket_promedio,
       ROUND(AVG(TIMESTAMPDIFF(MINUTE, o.fecha_apertura, o.fecha_cierre)), 0) AS min_promedio
FROM ordenes o
JOIN mesas m ON m.id_mesas = o.id_mesa
WHERE o.estatus = 'PAGADA'
  AND o.fecha_apertura >= '2026-07-20'
GROUP BY m.id_mesas, m.numero
ORDER BY ingreso DESC;


-- 5.2 Distribucion de la duracion de las cuentas.
--     El promedio (154 min) enganna: lo inflan cuentas que se cierran al cerrar
--     el dia. Esta consulta muestra la forma real de la distribucion.
SELECT CASE
         WHEN mins <  15 THEN '00-15 min'
         WHEN mins <  30 THEN '15-30 min'
         WHEN mins <  60 THEN '30-60 min'
         WHEN mins < 120 THEN '1-2 h'
         WHEN mins < 240 THEN '2-4 h'
         ELSE '4+ h (probable cierre tardio)'
       END                       AS rango,
       COUNT(*)                  AS ordenes,
       ROUND(AVG(total), 2)      AS ticket_promedio
FROM (
  SELECT TIMESTAMPDIFF(MINUTE, o.fecha_apertura, o.fecha_cierre) AS mins,
         o.total
  FROM ordenes o
  WHERE o.estatus = 'PAGADA'
    AND o.tipo = 'LOZA'
    AND o.fecha_apertura >= '2026-07-20'
) t
GROUP BY rango
ORDER BY MIN(mins);


-- 5.3 Tiempo de atencion: de abrir la mesa al primer platillo capturado.
--     Solo se puede medir con la bitacora de eventos.
SELECT e.nombre_mesero                       AS mesero,
       COUNT(*)                              AS mesas_medidas,
       ROUND(AVG(t.segundos), 0)             AS seg_promedio,
       MAX(t.segundos)                       AS seg_maximo
FROM (
  SELECT ap.id_orden,
         TIMESTAMPDIFF(SECOND, ap.timestamp, MIN(pl.timestamp)) AS segundos
  FROM eventos_orden ap
  JOIN eventos_orden pl
    ON pl.id_orden = ap.id_orden
   AND pl.tipo_evento = 'PLATILLO_NUEVO'
   AND pl.timestamp >= ap.timestamp
  WHERE ap.tipo_evento = 'MESA_ABIERTA'
  GROUP BY ap.id_orden, ap.timestamp
) t
JOIN eventos_orden e ON e.id_orden = t.id_orden AND e.tipo_evento = 'MESA_ABIERTA'
WHERE t.segundos BETWEEN 0 AND 3600
GROUP BY e.nombre_mesero
ORDER BY seg_promedio;


-- =============================================================================
-- BLOQUE 6 — CALIDAD DE DATOS (correr antes de confiar en cualquier tablero)
-- =============================================================================

-- 6.1 Ordenes zombis: abiertas y nunca cerradas
SELECT o.id_ordenes,
       DATE(o.fecha_apertura - INTERVAL 6 HOUR) AS dia_apertura,
       o.estatus,
       o.total,
       u.nombre AS empleado
FROM ordenes o
JOIN usuarios u ON u.id_usuarios = o.id_usuario
WHERE o.fecha_cierre IS NULL
  AND DATE(o.fecha_apertura - INTERVAL 6 HOUR) < DATE(NOW())
ORDER BY o.fecha_apertura;


-- 6.2 Cuadre general: que las cifras del tablero coincidan
SELECT (SELECT COUNT(*) FROM ordenes)                                  AS ordenes_totales,
       (SELECT COUNT(*) FROM ordenes WHERE estatus = 'PAGADA')         AS pagadas,
       (SELECT COUNT(*) FROM ordenes WHERE estatus = 'CANCELADA')      AS canceladas,
       (SELECT COUNT(*) FROM ordenes
         WHERE estatus NOT IN ('PAGADA','CANCELADA'))                  AS abiertas,
       (SELECT ROUND(SUM(total),2) FROM ordenes WHERE estatus='PAGADA') AS ingreso_total,
       (SELECT ROUND(SUM(d.subtotal),2)
          FROM orden_detalle d
          JOIN ordenes o ON o.id_ordenes = d.id_orden
         WHERE o.estatus = 'PAGADA')                                   AS suma_detalles;
-- ingreso_total y suma_detalles deben coincidir. Si no, hay ordenes cuyo
-- total no se recalculo bien.


-- 6.3 Ordenes sin renglones (deberian ser solo canceladas)
SELECT o.estatus, COUNT(*) AS n
FROM ordenes o
WHERE NOT EXISTS (SELECT 1 FROM orden_detalle d WHERE d.id_orden = o.id_ordenes)
GROUP BY o.estatus;


-- =============================================================================
-- BLOQUE 7 — TEXTO LIBRE (insumo para el RAG)
-- =============================================================================

-- 7.1 Comentarios mas frecuentes en los platillos
SELECT TRIM(LOWER(d.comentarios)) AS comentario,
       COUNT(*)                   AS veces
FROM orden_detalle d
WHERE d.comentarios IS NOT NULL AND TRIM(d.comentarios) <> ''
GROUP BY comentario
ORDER BY veces DESC, comentario;


-- 7.2 Productos que mas se personalizan
SELECT p.nombre                                      AS producto,
       COUNT(*)                                      AS renglones,
       SUM(CASE WHEN d.comentarios IS NOT NULL
                 AND TRIM(d.comentarios) <> '' THEN 1 ELSE 0 END) AS con_comentario,
       ROUND(100.0 * SUM(CASE WHEN d.comentarios IS NOT NULL
                               AND TRIM(d.comentarios) <> '' THEN 1 ELSE 0 END)
                   / COUNT(*), 1)                    AS pct_personalizado
FROM orden_detalle d
JOIN productos p ON p.id_productos = d.id_producto
GROUP BY p.id_productos, p.nombre
HAVING renglones >= 10
ORDER BY pct_personalizado DESC;


-- =============================================================================
-- BLOQUE 8 — RECONSTRUIR UNA CUENTA (auditoria / soporte)
-- =============================================================================

-- 8.1 Linea de tiempo completa de una orden. Cambia el :id_orden.
SELECT e.timestamp - INTERVAL 6 HOUR AS hora_local,
       e.tipo_evento,
       e.nombre_mesero,
       e.nombre_producto,
       e.cantidad_anterior,
       e.cantidad_nueva,
       e.precio_unitario,
       e.comentarios_nuevo
FROM eventos_orden e
WHERE e.id_orden = 1  -- <<< cambiar
ORDER BY e.timestamp;
