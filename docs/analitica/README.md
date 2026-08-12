# Analítica de RestFood — qué datos hay y cómo usarlos

Este directorio responde a una pregunta concreta: **¿qué se puede analizar con los datos que RestFood ya genera, y qué haría falta para analizar más?**

Sirve para dos cosas distintas:

1. **Encargarle a diseño una vista de análisis** — hay un brief con las preguntas de negocio, los widgets y los contratos JSON que el backend tendría que exponer.
2. **Montar un RAG** (preguntarle en lenguaje natural a los datos: *"¿qué platillo se vende menos los martes?"*).

## Los cuatro documentos

| Archivo | Para qué |
|---|---|
| [`catalogo-datos.md`](catalogo-datos.md) | Diccionario de datos tabla por tabla, con el volumen real medido y **las trampas** que invalidan un análisis si no las conoces |
| [`consultas.sql`](consultas.sql) | ~25 consultas KPI listas para copiar y pegar, ya corregidas por las trampas |
| [`brief-vista-analitica.md`](brief-vista-analitica.md) | Lo que le pasas a diseño: preguntas de negocio → pantallas → endpoints y JSON |
| [`rag.md`](rag.md) | Diseño del RAG: por qué text-to-SQL y no embeddings, arquitectura, seguridad y costo |

También hay un script de exportación: [`../../scripts/exportar-analitica.sh`](../../scripts/exportar-analitica.sh). Genera CSV para Excel/Metabase y JSONL para el RAG, **solo con SELECTs**, sin tocar nada.

---

## Lo que hay (medido el 2026-07-27, base de producción)

| Tabla | Filas | Rango |
|---|---:|---|
| `ordenes` | 1,169 | 2026-04-11 → 2026-07-27 |
| `orden_detalle` | 2,040 | — |
| `eventos_orden` | 3,820 | 2026-04-15 → 2026-07-27 |
| `productos` | 238 | en 8 categorías |
| `usuarios` | 10 | 3 meseros, 3 repartidores, 1 cocina, 1 admin, 2 dev |
| `mesas` | 50 | — |
| `insumos`, `movimientos_inventario`, `producto_insumo`, `conteos_fisicos` | 0 | tablas de la V2, aún sin datos |

**Ventas registradas:** $214,200 en 1,009 órdenes pagadas (ticket promedio $212).

**Ojo con la ventana útil.** Aunque hay datos desde abril, la operación real y continua empieza el **20 de julio de 2026**: antes hay días sueltos de 1–18 órdenes (pruebas), desde el 20 hay 149–184 órdenes diarias. Para cualquier análisis serio de tendencia, filtra `>= 2026-07-20`; hoy eso son ~6 días de operación, suficiente para ver patrones de hora y producto, **no** para estacionalidad semanal o mensual. Eso llega solo con el tiempo.

---

## Las tres trampas que tienes que conocer

Están explicadas a fondo en el catálogo, pero si solo lees una sección de todo esto, que sea esta.

### 1. Las fechas en la base están 6 horas adelantadas

La app guarda `LocalDateTime.now()` (hora local, CST = UTC−6) pero el driver JDBC tiene `serverTimezone=UTC`, así que **una orden de las 12:04 de la tarde se guarda como `18:04`**.

Esto **no es un bug de la aplicación**: la app convierte igual al escribir y al consultar, así que el corte del día que ves en el panel de admin está correcto. El problema aparece cuando alguien consulta la base **directamente** — un Metabase, un Excel, una consulta SQL a mano, o el RAG. Ahí las horas pico salen mal y las ventas de la noche se le cargan al día siguiente.

Comprobado empíricamente: los DESAYUNO se concentran en las horas crudas 15–17 (= 9–11 locales) y las COMIDA en 19–21 (= 13–15 locales). Cuadra exactamente con un restaurante mexicano de comida corrida.

**La regla, sin excepción:** toda consulta SQL directa resta 6 horas.

```sql
-- MAL: da la hora en UTC, la gráfica de horas pico sale corrida
SELECT HOUR(fecha_apertura), COUNT(*) FROM ordenes GROUP BY 1;

-- BIEN
SELECT HOUR(fecha_apertura - INTERVAL 6 HOUR), COUNT(*) FROM ordenes GROUP BY 1;
```

México eliminó el horario de verano en 2022, así que el desfase es fijo de 6 horas todo el año. No hay que preocuparse por DST.

### 2. Casi ninguna "cancelación" es una cancelación

De **152 órdenes canceladas, solo 3 llegaron a tener un platillo**. Las otras 149 nacieron y murieron vacías, con `total = 0` y cero renglones.

No es que se pierda el dato: es que nunca hubo nada que perder. El flujo de "Nueva Entrega" **crea la orden en la base al abrir el diálogo**, antes de capturar nada; si el repartidor cierra sin enviar a cocina, la interfaz la cancela para no dejar una orden fantasma. Los repartidores acumulan 103 de estas, con una vida promedio de **122 segundos** y cero platillos, siempre.

Entonces `estatus = 'CANCELADA'` significa, casi siempre, *"se abrió un diálogo y se cerró sin usarlo"*. Es limpieza de la interfaz, no un evento de negocio.

Dos consecuencias:

- **La tasa de cancelación no sirve como KPI.** Medida en crudo da ~15% y no dice nada del restaurante. Las cancelaciones reales son 3 de 1,037 pagadas: 0.3%.
- **Nunca compares personas con este número.** Un repartidor y un mesero con la misma disciplina dan cifras muy distintas, porque sus pantallas crean la orden en momentos diferentes del flujo.

Sí es una señal útil, pero de otra cosa: 103 órdenes fantasma dicen que el diálogo de entregas crea la orden demasiado pronto. Es un hallazgo de experiencia de uso.

### 3. Los precios del histórico son fotos, no referencias

`orden_detalle.precio_unitario` guarda el precio **del momento de la venta**, no un JOIN a `productos.precio_comida`. Eso está bien hecho: si mañana subes el precio de la milanesa, las ventas de ayer no cambian.

La consecuencia es que **para analizar ingresos siempre usas `orden_detalle.subtotal`**, nunca el precio actual del producto. Y que un análisis de "elasticidad de precio" sí es posible: puedes ver a qué precio se vendió cada unidad a lo largo del tiempo.

---

## Lo que se puede responder hoy

Sin tocar una línea de código:

- Ventas por día, por servicio (desayuno/comida), por tipo (mesa/para llevar)
- Horas pico reales, por servicio
- Ranking de productos por unidades e ingreso; productos que nunca se venden
- Ingreso y mix por categoría
- Ventas por empleado, ticket promedio por empleado
- Órdenes abandonadas (el "ruido" de la trampa 2) — útil como señal de uso, no de ventas
- Rotación de mesas y duración de las cuentas
- Comentarios de los clientes (234 líneas con texto libre, 168 distintos)
- Reconstrucción minuto a minuto de cualquier cuenta, desde `eventos_orden`

## Lo que NO se puede responder hoy

- **Margen o utilidad** — no hay costo de los platillos. Solo ingreso. (Las tablas de inventario de la V2 abren esta puerta, pero están vacías.)
- **Tasa de cancelación como métrica de negocio** — ver trampa 2: el número existe pero mide otra cosa.
- **Forma de pago** — no se registra efectivo vs tarjeta.
- **Número de comensales** — no se captura, así que no hay "gasto por persona", solo por cuenta.
- **Tiempo de cocina** — no hay marca de "platillo listo", solo apertura y cierre de la orden.
- **Clientes recurrentes** — no hay identidad del cliente. Todo es anónimo.

Cada una está desarrollada en el catálogo con lo que costaría agregarla.

---

## Antes de exponer nada al exterior

`usuarios` tiene emails y hashes de contraseña. Ninguna vista de análisis, exportación o RAG debe tocar esa tabla más allá de `nombre` y `rol`. El script de exportación ya lo respeta y las consultas de este directorio también.
