#!/usr/bin/env bash
#
# Exporta los datos de RestFood para análisis externo.
#
#   - CSV para Excel, Metabase, Google Sheets, pandas...
#   - JSONL desnormalizado para alimentar el corpus de un RAG
#
# SOLO EJECUTA SELECTs. No modifica nada. Es seguro correrlo con el
# restaurante abierto, aunque conviene hacerlo fuera de las horas pico
# (13:00-15:00) para no competir por la base con la operación.
#
# Aplica las tres reglas del catálogo de datos automáticamente:
#   1. Resta 6 horas a todas las fechas (la base guarda en UTC, la app
#      produce hora local CST). Ver docs/analitica/catalogo-datos.md.
#   2. Filtra estatus = 'PAGADA' en las ventas.
#   3. Usa orden_detalle.subtotal para los ingresos, no el precio de catálogo.
#
# NUNCA exporta usuarios.email ni usuarios.contrasena. Las columnas se
# seleccionan de forma explícita, jamás con SELECT *.
#
# Uso:
#   ./scripts/exportar-analitica.sh [directorio-destino]
#
# Por omisión escribe en ./exportacion-analitica-AAAAMMDD/
#
set -euo pipefail

DESTINO="${1:-./exportacion-analitica-$(date +%Y%m%d)}"
ENV_FILE="${RESTFOOD_ENV:-/etc/restfood/backend.env}"
DB_NAME="${DB_NAME:-restaurante}"
DB_HOST="${DB_HOST:-127.0.0.1}"

# --- Credenciales -------------------------------------------------------------
# Se leen del mismo archivo que usa el backend. Si tienes un usuario de solo
# lectura, expórtalo antes:  DB_USER=analitica DB_PASSWORD=... ./exportar...
if [[ -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" ]]; then
  if [[ -r "$ENV_FILE" ]]; then
    eval "$(grep -E '^DB_(USER|PASSWORD)=' "$ENV_FILE" | sed 's/^/export /')"
  elif sudo -n test -r "$ENV_FILE" 2>/dev/null; then
    eval "$(sudo grep -E '^DB_(USER|PASSWORD)=' "$ENV_FILE" | sed 's/^/export /')"
  else
    echo "No pude leer las credenciales de $ENV_FILE." >&2
    echo "Exporta DB_USER y DB_PASSWORD, o corre con sudo." >&2
    exit 1
  fi
fi
export MYSQL_PWD="$DB_PASSWORD"

mkdir -p "$DESTINO"

# mysql --batch devuelve TSV con \N para los nulos y escapa tabuladores y saltos
# de línea dentro de los valores. Se convierte a CSV con comillas.
consulta_csv() {
  local archivo="$1" sql="$2"
  mysql --batch --raw -u "$DB_USER" -h "$DB_HOST" "$DB_NAME" -e "$sql" \
    | python3 -c '
import csv, sys
w = csv.writer(sys.stdout, lineterminator="\n")
for linea in sys.stdin:
    campos = linea.rstrip("\n").split("\t")
    w.writerow(["" if c == "\\N" else c.replace("\\t","\t").replace("\\n","\n") for c in campos])
' > "$DESTINO/$archivo"
  local filas=$(( $(wc -l < "$DESTINO/$archivo") - 1 ))
  printf '  %-28s %6s filas\n' "$archivo" "$filas"
}

echo "Exportando a $DESTINO/"
echo

# =============================================================================
# CSV — para hojas de cálculo y herramientas de BI
# =============================================================================

# Grano de venta: un renglón por platillo vendido, con todo el contexto ya unido.
# Es el archivo del que se puede sacar casi cualquier análisis con una tabla
# dinámica, sin volver a hacer JOINs.
consulta_csv "ventas_detalle.csv" "
SELECT d.id_detalle,
       o.id_ordenes                              AS id_orden,
       DATE(o.fecha_cierre - INTERVAL 6 HOUR)    AS dia,
       HOUR(o.fecha_cierre - INTERVAL 6 HOUR)    AS hora,
       DAYNAME(o.fecha_cierre - INTERVAL 6 HOUR) AS dia_semana,
       o.servicio, o.tipo,
       u.nombre  AS empleado,
       u.rol     AS rol_empleado,
       m.numero  AS mesa,
       c.nombre  AS categoria,
       p.nombre  AS producto,
       d.cantidad, d.precio_unitario, d.subtotal,
       d.comentarios
FROM orden_detalle d
JOIN ordenes    o ON o.id_ordenes   = d.id_orden AND o.estatus = 'PAGADA'
JOIN productos  p ON p.id_productos = d.id_producto
JOIN categorias c ON c.id_categorias = p.id_categoria
JOIN usuarios   u ON u.id_usuarios  = o.id_usuario
LEFT JOIN mesas m ON m.id_mesas     = o.id_mesa
ORDER BY o.fecha_cierre, d.id_detalle"

# Grano de cuenta: una fila por orden. Incluye canceladas y abiertas, con su
# estatus, para poder analizar cancelaciones y calidad de datos.
consulta_csv "ordenes.csv" "
SELECT o.id_ordenes,
       DATE(o.fecha_apertura - INTERVAL 6 HOUR)  AS dia_apertura,
       o.fecha_apertura - INTERVAL 6 HOUR        AS apertura_local,
       o.fecha_cierre   - INTERVAL 6 HOUR        AS cierre_local,
       TIMESTAMPDIFF(MINUTE, o.fecha_apertura, o.fecha_cierre) AS minutos_abierta,
       o.estatus, o.tipo, o.servicio, o.total, o.numero_comanda,
       u.nombre AS empleado,
       u.rol    AS rol_empleado,
       m.numero AS mesa,
       (SELECT COUNT(*) FROM orden_detalle d WHERE d.id_orden = o.id_ordenes) AS renglones
FROM ordenes o
JOIN usuarios u ON u.id_usuarios = o.id_usuario
LEFT JOIN mesas m ON m.id_mesas  = o.id_mesa
ORDER BY o.fecha_apertura"

# Catálogo, con las ventas acumuladas de cada producto.
consulta_csv "productos.csv" "
SELECT p.id_productos, p.nombre AS producto, c.nombre AS categoria, c.impresora,
       p.precio_comida, p.precio_desayuno, p.disponibilidad, p.eliminado,
       COALESCE(v.unidades, 0) AS unidades_vendidas,
       COALESCE(v.ingreso, 0)  AS ingreso_historico
FROM productos p
JOIN categorias c ON c.id_categorias = p.id_categoria
LEFT JOIN (
    SELECT d.id_producto, SUM(d.cantidad) AS unidades, SUM(d.subtotal) AS ingreso
    FROM orden_detalle d
    JOIN ordenes o ON o.id_ordenes = d.id_orden AND o.estatus = 'PAGADA'
    GROUP BY d.id_producto
) v ON v.id_producto = p.id_productos
ORDER BY ingreso_historico DESC"

# Bitácora de eventos. Sin id_usuario ni nada de la tabla usuarios más allá del
# nombre, que ya viene desnormalizado en la propia tabla.
consulta_csv "eventos.csv" "
SELECT e.id_evento, e.id_orden, e.id_mesa,
       e.timestamp - INTERVAL 6 HOUR AS momento_local,
       e.tipo_evento, e.nombre_mesero, e.nombre_producto,
       e.cantidad_anterior, e.cantidad_nueva, e.precio_unitario,
       e.comentarios_anterior, e.comentarios_nuevo
FROM eventos_orden e
ORDER BY e.timestamp"

# Resumen diario, listo para graficar sin agregaciones extra.
consulta_csv "resumen_diario.csv" "
SELECT DATE(o.fecha_cierre - INTERVAL 6 HOUR) AS dia,
       COUNT(*)                               AS ordenes,
       ROUND(SUM(o.total), 2)                 AS ingreso,
       ROUND(AVG(o.total), 2)                 AS ticket_promedio,
       SUM(CASE WHEN o.servicio = 'DESAYUNO' THEN o.total ELSE 0 END) AS ingreso_desayuno,
       SUM(CASE WHEN o.servicio = 'COMIDA'   THEN o.total ELSE 0 END) AS ingreso_comida,
       SUM(CASE WHEN o.tipo = 'LOZA'   THEN 1 ELSE 0 END) AS ordenes_mesa,
       SUM(CASE WHEN o.tipo = 'LLEVAR' THEN 1 ELSE 0 END) AS ordenes_llevar
FROM ordenes o
WHERE o.estatus = 'PAGADA'
GROUP BY dia
ORDER BY dia"

# =============================================================================
# JSONL — corpus para el RAG
# =============================================================================
# Una línea por orden, con su detalle anidado y ya redactada en prosa. Sirve
# tanto para inspeccionar los datos a mano como para pasárselos a un modelo
# como contexto. Ver docs/analitica/rag.md.

echo
mysql --batch --raw --skip-column-names -u "$DB_USER" -h "$DB_HOST" "$DB_NAME" -e "
SELECT JSON_OBJECT(
  'id_orden',     o.id_ordenes,
  'fecha',        DATE_FORMAT(o.fecha_cierre - INTERVAL 6 HOUR, '%Y-%m-%d'),
  'hora',         DATE_FORMAT(o.fecha_cierre - INTERVAL 6 HOUR, '%H:%i'),
  'dia_semana',   DAYNAME(o.fecha_cierre - INTERVAL 6 HOUR),
  'servicio',     o.servicio,
  'tipo',         o.tipo,
  'mesa',         m.numero,
  'empleado',     u.nombre,
  'rol_empleado', u.rol,
  'total',        o.total,
  'minutos',      TIMESTAMPDIFF(MINUTE, o.fecha_apertura, o.fecha_cierre),
  'platillos', (
      SELECT JSON_ARRAYAGG(JSON_OBJECT(
                 'producto',  p.nombre,
                 'categoria', c.nombre,
                 'cantidad',  d.cantidad,
                 'precio',    d.precio_unitario,
                 'subtotal',  d.subtotal,
                 'nota',      d.comentarios))
      FROM orden_detalle d
      JOIN productos  p ON p.id_productos  = d.id_producto
      JOIN categorias c ON c.id_categorias = p.id_categoria
      WHERE d.id_orden = o.id_ordenes),
  'resumen', CONCAT(
      'Orden ', o.id_ordenes, ' del ',
      DATE_FORMAT(o.fecha_cierre - INTERVAL 6 HOUR, '%Y-%m-%d'), ' a las ',
      DATE_FORMAT(o.fecha_cierre - INTERVAL 6 HOUR, '%H:%i'), '. Servicio de ',
      LOWER(o.servicio), ', ',
      CASE o.tipo WHEN 'LOZA' THEN CONCAT('en mesa ', COALESCE(m.numero, '?'))
                  ELSE 'para llevar' END,
      '. Capturada por ', u.nombre, ' (', LOWER(u.rol), '). Total ', o.total, ' pesos.')
) AS j
FROM ordenes o
JOIN usuarios u ON u.id_usuarios = o.id_usuario
LEFT JOIN mesas m ON m.id_mesas  = o.id_mesa
WHERE o.estatus = 'PAGADA'
ORDER BY o.fecha_cierre" > "$DESTINO/ordenes.jsonl"

printf '  %-28s %6s líneas\n' "ordenes.jsonl" "$(wc -l < "$DESTINO/ordenes.jsonl")"

# Comentarios de los clientes, agrupados. Es el único texto libre del sistema.
mysql --batch --raw --skip-column-names -u "$DB_USER" -h "$DB_HOST" "$DB_NAME" -e "
SELECT JSON_OBJECT('comentario', comentario, 'veces', veces, 'productos', productos)
FROM (
  SELECT TRIM(d.comentarios)                         AS comentario,
         COUNT(*)                                    AS veces,
         GROUP_CONCAT(DISTINCT p.nombre SEPARATOR ', ') AS productos
  FROM orden_detalle d
  JOIN productos p ON p.id_productos = d.id_producto
  WHERE d.comentarios IS NOT NULL AND TRIM(d.comentarios) <> ''
  GROUP BY comentario
) t
ORDER BY veces DESC" > "$DESTINO/comentarios.jsonl"

printf '  %-28s %6s líneas\n' "comentarios.jsonl" "$(wc -l < "$DESTINO/comentarios.jsonl")"

echo
echo "Listo. Recordatorios:"
echo "  - Las fechas ya vienen en hora local (se restaron 6 horas)."
echo "  - Los CSV de ventas solo incluyen órdenes PAGADAS."
echo "  - ordenes.csv sí incluye canceladas y abiertas, con su estatus."
echo "  - No se exportó ningún email ni contraseña."
echo
du -sh "$DESTINO"
