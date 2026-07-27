# Catálogo de datos de RestFood

Diccionario tabla por tabla, con la semántica de negocio de cada campo, el perfil real de los datos (medido el 2026-07-27 contra producción) y las trampas.

Fuente del esquema: `api/src/main/resources/db/migration/`. Las entidades JPA viven en `api/src/main/java/restaurante/api/`.

---

## Mapa de relaciones

```
usuarios ──< ordenes >── mesas
              │
              ├──< orden_detalle >── productos >── categorias
              │
              └──< eventos_orden
```

Una orden pertenece a un usuario (quien la capturó) y opcionalmente a una mesa (null si es Para Llevar). Contiene renglones de detalle, uno por platillo. Los eventos son la bitácora de auditoría de todo lo que le pasó a esa orden.

---

## `ordenes` — el hecho central

1,169 filas. Es la tabla de la que cuelga todo lo demás.

| Columna | Tipo | Significado |
|---|---|---|
| `id_ordenes` | bigint PK | |
| `fecha_apertura` | datetime | Cuándo se abrió la cuenta. **+6h respecto a la hora local** |
| `fecha_cierre` | datetime(6) | Cuándo se pagó o canceló. Null solo si sigue abierta |
| `estatus` | varchar(20) | `PREPARANDO` / `SERVIDO` / `PAGADA` / `CANCELADA` |
| `total` | decimal | Suma de los subtotales. **0 en las canceladas** |
| `tipo` | varchar(20) | `LOZA` (comer aquí) / `LLEVAR` (para llevar y repartos) |
| `servicio` | enum | `DESAYUNO` / `COMIDA`. Define qué lista de precios aplicó |
| `numero_comanda` | int | Consecutivo **por usuario y por día**, no global |
| `id_usuario` | FK | Quien capturó la orden |
| `id_mesa` | FK nullable | Null cuando es Para Llevar |

### Distribución real

| estatus | tipo | servicio | órdenes | suma |
|---|---|---|---:|---:|
| PAGADA | LOZA | COMIDA | 579 | $134,380 |
| PAGADA | LLEVAR | COMIDA | 227 | $38,995 |
| PAGADA | LOZA | DESAYUNO | 138 | $30,710 |
| PAGADA | LLEVAR | DESAYUNO | 65 | $10,115 |
| CANCELADA | (todas) | (ambos) | 149 | $0 |
| PREPARANDO | (todas) | (ambos) | 11 | $2,140 |

**Total pagado: $214,200 en 1,009 órdenes. Ticket promedio $212.**

La comida en mesa es el 63% del ingreso. El desayuno es el 19%. Para llevar, sumando ambos servicios, es el 23%.

### Qué significa cada estatus, en la operación

- **`PREPARANDO`** — cuenta abierta, cocina trabajando. Es el estado inicial de toda orden.
- **`SERVIDO`** — la comida salió pero la cuenta no se ha pagado. Sigue ocupando la mesa. Si el mesero agrega otro platillo, la orden **vuelve** a `PREPARANDO` (`Orden.reabrir()`), para que reaparezca en el panel de cocina.
- **`PAGADA`** — terminal. Es el único estatus que cuenta como venta.
- **`CANCELADA`** — terminal. Ver la trampa de abajo.

### Trampa: 11 órdenes zombis

Hay 11 órdenes en `PREPARANDO` con `fecha_cierre = NULL` que quedaron colgadas de días anteriores — se abrieron y nunca se cerraron ni se cancelaron. Suman $2,140 fantasma.

**Todo análisis de ventas debe filtrar `estatus = 'PAGADA'`.** Si alguien suma `total` sin filtrar, infla la cifra con esas órdenes y con las canceladas (aunque esas sean 0).

### Trampa: `numero_comanda` no es único

Es `MAX(numero_comanda) + 1` **dentro del día y del usuario** (ver `OrdenRepository.maxNumeroComandaByUsuarioIdAndFechaBetween`). Dos meseros distintos tienen su comanda #5 el mismo día. Nunca lo uses como identificador; usa `id_ordenes`.

### Fecha de apertura vs fecha de cierre — cuál usar

Depende de la pregunta, y el backend ya toma partido:

- **Corte del día / ingresos**: `fecha_cierre`. Es lo que hace `OrdenService.master()`. Una cuenta que se abre a las 11:50 y se paga a las 12:10 cuenta en el turno en que se cobró.
- **Demanda / hora pico / carga de cocina**: `fecha_apertura`. Es cuándo llegó el cliente.

Si mezclas las dos en el mismo tablero sin decirlo, los números no cuadran y parece un error. No lo es.

**Duraciones medidas, y por qué el promedio miente.** Sobre todo el histórico, el promedio apertura→cierre de las pagadas es 154.5 min. Sobre la operación real (mesa, desde el 20 de julio) es **38.2 min**. La diferencia son las órdenes de prueba de abril, que quedaron abiertas horas.

La distribución real desde el 20 de julio, en mesa:

| Duración | Órdenes | Ticket promedio |
|---|---:|---:|
| 0–15 min | 14 | $88 |
| 15–30 min | 156 | $155 |
| **30–60 min** | **432** | **$260** |
| 1–2 h | 35 | $284 |
| 2–4 h | 1 | $80 |

Dos tercios de las cuentas duran entre 30 y 60 minutos, y son las que más dejan. Las cuentas exprés (menos de 15 min) dejan un tercio de eso. Es el tipo de cosa que un promedio solo no muestra: úsese la distribución, no la media.

---

## `orden_detalle` — los renglones de venta

2,040 filas. Un renglón por platillo pedido.

| Columna | Tipo | Significado |
|---|---|---|
| `id_detalle` | bigint PK | |
| `cantidad` | int | Cuántas unidades |
| `precio_unitario` | decimal | **Foto del precio al momento de la venta** |
| `subtotal` | decimal | `cantidad × precio_unitario` |
| `comentarios` | varchar(255) | Texto libre del mesero: "sin cebolla", "bien cocido" |
| `id_orden` | FK | |
| `id_producto` | FK | |

### El precio es una foto, y eso es correcto

`OrdenDetalle` copia el precio del producto al crear el renglón, eligiendo `precio_comida` o `precio_desayuno` según el `servicio` de la orden. Si mañana cambias el precio en el catálogo, el histórico **no se mueve**.

Dos consecuencias:

1. **Para ingresos, siempre `orden_detalle.subtotal`.** Nunca `cantidad × productos.precio_comida` — eso te daría lo que valdría hoy, no lo que se cobró.
2. **Se puede analizar la evolución de precios.** `SELECT precio_unitario, COUNT(*) ... GROUP BY` sobre un producto muestra a qué precios se ha vendido y cuánto se vendió a cada uno.

### Los comentarios son datos, no ruido

234 renglones tienen comentario, 168 de ellos distintos. Es la única fuente de texto libre del sistema: preferencias, alergias, modificaciones. Vale para dos cosas:

- Detectar modificaciones frecuentes ("sin cebolla" repetido 20 veces sugiere cambiar la receta base o agregar la variante al menú).
- Alimentar el corpus del RAG, que puede responder preguntas cualitativas sobre lo que piden los clientes.

---

## `productos` y `categorias` — el catálogo

238 productos en 8 categorías.

| Columna (`productos`) | Significado |
|---|---|
| `nombre` | Único |
| `precio_comida` / `precio_desayuno` | Dos listas de precios; la orden elige según `servicio` |
| `disponibilidad` | Si se puede pedir hoy (se apaga cuando se acaba) |
| `eliminado` | **Borrado suave** — el producto sale del menú pero el histórico sobrevive |
| `id_categoria` | FK |

`categorias.impresora` no es un dato de catálogo, es **ruteo físico**: dice a qué impresora térmica va la comanda (`COCINA_1`, `COCINA_2`, `SIN_IMPRESION`). Como efecto secundario es un proxy útil de carga de trabajo por estación.

### El borrado suave importa para el análisis

`Producto.marcarEliminado()` pone `eliminado = true` y `disponibilidad = false`, pero **no borra la fila**. Eso existe precisamente para que las ventas históricas de un producto descontinuado sigan siendo consultables.

Consecuencia: un análisis de "productos que no se venden" debe distinguir *nunca se vendió* de *ya no está en el menú*. Filtra `eliminado = 0` cuando la pregunta es sobre el menú actual; no lo filtres cuando la pregunta es sobre el histórico.

### Ingreso por categoría (histórico completo, solo pagadas)

| Categoría | Impresora | Productos | Unidades | Ingreso |
|---|---|---:|---:|---:|
| Comida | COCINA_1 | 91 | 1,400 | $130,290 |
| Comida del día | COCINA_1 | 37 | 381 | $42,500 |
| Especialidades | COCINA_1 | 27 | 93 | $13,100 |
| Mariscos | COCINA_1 | 16 | 78 | $11,230 |
| Antojitos | COCINA_2 | 24 | 102 | $10,245 |
| Bebidas | SIN_IMPRESION | 13 | 136 | $4,880 |
| Extras | SIN_IMPRESION | 19 | 260 | $3,505 |
| Snacks | COCINA_1 | 11 | 5 | $590 |

Dos cosas saltan a la vista y valen como ejemplo de qué tipo de decisión permiten estos datos:

- **COCINA_1 concentra prácticamente todo.** 182 de los 238 productos y el 92% del ingreso salen por una sola impresora. Si alguna vez hay que balancear carga entre estaciones, aquí está la evidencia.
- **Snacks vendió 5 unidades en tres meses** con 11 productos en el menú. Es una categoría candidata a revisión.

### Top 10 productos (unidades, histórico, solo pagadas)

| Producto | Unidades | Ingreso |
|---|---:|---:|
| Pechuga empanizada con papas | 182 | $20,430 |
| Huevo estrellado | 145 | $1,450 |
| Milanesa con papas | 107 | $11,980 |
| Enchiladas sencillas | 96 | $6,055 |
| Huevos en salsa verde | 95 | $6,015 |
| Refresco | 86 | $3,010 |
| Sopes sencillos | 65 | $4,380 |
| Tacos dorados de pollo | 53 | $5,010 |
| Huevos a la mexicana | 53 | $3,655 |
| Filete empanizado con papas | 50 | $7,000 |

Nótese que el ranking por unidades y el ranking por ingreso **no son el mismo**: "Huevo estrellado" es el #2 en volumen y aporta $1,450; "Filete empanizado" es el #10 en volumen y aporta $7,000. Cualquier vista que muestre "los más vendidos" tiene que decir explícitamente por cuál de los dos criterios ordena.

---

## `usuarios` — los empleados

10 filas: 3 MESERO, 3 REPARTIDOR, 1 COCINA, 1 ADMIN, 2 DEV.

| Columna | Nota para analítica |
|---|---|
| `nombre` | Único, en mayúsculas. **El único campo seguro de exponer** |
| `rol` | `ADMIN` / `MESERO` / `COCINA` / `CAJERO` / `DEV` / `REPARTIDOR`. Seguro de exponer |
| `seccion` | Zona del restaurante asignada al mesero. Solo aplica a MESERO |
| `email` | **PII — no exponer** |
| `contrasena` | **Hash — jamás exponer ni exportar** |
| `estatus` | Activo/inactivo (baja suave) |

`eventos_orden` ya guarda `nombre_mesero` desnormalizado precisamente para no tener que hacer JOIN contra esta tabla en consultas de análisis.

**Regla firme:** ninguna vista, exportación o RAG toca `email` ni `contrasena`. El script de exportación selecciona columnas explícitas por esta razón.

---

## `mesas`

50 filas. `numero` (etiqueta) y `estado` (`LIBRE` / `OCUPADA` / `SUCIA` / `RESERVADA`).

El estado es **el ahora**, no historia — no hay bitácora de cambios de estado de mesa. Para analizar ocupación histórica hay que reconstruirla desde `ordenes` (apertura → cierre por mesa) o desde los eventos `MESA_ABIERTA` / `MESA_CERRADA`.

---

## `eventos_orden` — la bitácora, y la joya escondida

3,820 filas. Es la tabla más rica del sistema para análisis de comportamiento y la más ignorada.

| Columna | Significado |
|---|---|
| `tipo_evento` | `MESA_ABIERTA` / `PLATILLO_NUEVO` / `PLATILLO_MODIFICADO` / `PLATILLO_CANCELADO` / `MESA_CERRADA` / `MESA_CANCELADA` |
| `timestamp` | Cuándo pasó. **También +6h** |
| `nombre_mesero`, `id_mesa` | Desnormalizados a propósito, para consultar sin JOINs |
| `nombre_producto`, `precio_unitario` | Null en eventos de mesa (apertura/cierre) |
| `cantidad_anterior` / `cantidad_nueva` | El antes y el después de una modificación |
| `comentarios_anterior` / `comentarios_nuevo` | Igual, para el texto |

### Distribución

| Tipo | Eventos | Desde |
|---|---:|---|
| PLATILLO_NUEVO | 1,936 | 2026-04-15 |
| MESA_CERRADA | 949 | 2026-04-15 |
| MESA_ABIERTA | 722 | 2026-04-15 |
| MESA_CANCELADA | 149 | 2026-07-01 |
| PLATILLO_CANCELADO | 42 | 2026-04-15 |
| PLATILLO_MODIFICADO | 22 | 2026-06-29 |

Los eventos no empiezan todos el mismo día porque se fueron agregando conforme se instrumentó el sistema. **`MESA_CANCELADA` solo existe desde el 1 de julio y `PLATILLO_MODIFICADO` desde el 29 de junio** — cualquier serie de tiempo sobre esos dos tipos parece "creciente" solo porque antes no se registraban. No es una tendencia, es instrumentación.

### Para qué sirve realmente

Con `PLATILLO_NUEVO` y su `timestamp` se puede reconstruir **cómo se arma una cuenta**: qué se pide primero, cuánto tarda entre el primer platillo y el último, si el cliente pide postre después. Eso no está en `orden_detalle`, que solo tiene el estado final.

Ejemplo de pregunta que solo esta tabla responde: *"¿cuánto tiempo pasa entre que se abre la mesa y se captura el primer platillo?"* — es una medida directa de qué tan rápido atiende un mesero.

---

## Tablas de inventario (V2, vacías)

`insumos`, `producto_insumo`, `movimientos_inventario`, `conteos_fisicos` existen en la base local (migración V2 aplicada el 2026-07-27) pero **tienen 0 filas** y la migración no está en `main` todavía — vive en las ramas `feat/inventario-fase1` / `feat/inventario-recetas`.

Vale la pena mencionarlas porque **son el camino al margen**: `producto_insumo` es la receta (qué insumos lleva cada platillo) y con costos de insumo se puede calcular costo por platillo, y por lo tanto utilidad. Hoy la analítica solo ve ingreso.

No hay nada que analizar ahí hasta que se cargue el catálogo de insumos y las recetas.

---

## Las trampas, en una lista

Repetido del README, aquí con el detalle técnico.

### 1. Desfase de 6 horas en todos los datetime

**Qué pasa.** `application.properties` fija `serverTimezone=UTC` en la URL JDBC. La app construye las fechas con `LocalDateTime.now()`, que es hora local (CST, UTC−6). El driver interpreta ese `LocalDateTime` como si fuera UTC al escribir, así que lo guarda +6h.

**Por qué la app sí funciona.** El driver hace la misma conversión en los parámetros de consulta. `OrdenService.master()` construye la ventana con `fecha.atStartOfDay()` … `fecha.atTime(LocalTime.MAX)` y el driver la convierte igual. Ambos lados se desplazan lo mismo, así que la comparación es correcta. Por eso el corte del día que David usa a diario da bien.

**Quién sí se rompe.** Cualquier cliente que no pase por la app: SQL a mano, Metabase, Excel, el RAG. Ese ve las fechas crudas.

**Evidencia.** Distribución de horas cruda vs corregida, órdenes pagadas desde el 20 de julio:

| Servicio | Hora cruda pico | Hora local (cruda − 6) |
|---|---|---|
| DESAYUNO | 15, 16, 17 | 9, 10, 11 |
| COMIDA | 19, 20, 21 | 13, 14, 15 |

Un restaurante que desayuna a las 9–11 y come a las 13–15. La corrección es correcta.

**Efecto secundario grave:** las órdenes de la tarde-noche (hora local 18–23) tienen hora cruda 0–5, es decir **se guardan con la fecha del día siguiente**. Son ~40 órdenes en el histórico. Un `GROUP BY DATE(fecha_apertura)` sin corregir se las carga al día equivocado.

**La corrección.** `fecha - INTERVAL 6 HOUR`. México no usa horario de verano desde 2022, así que el offset es fijo.

**La solución de fondo** (fuera del alcance de este documento, requiere despliegue): quitar `serverTimezone=UTC` de la URL JDBC o cambiarlo a `America/Mexico_City`. **No hacerlo a la ligera** — cambiaría la interpretación de los 1,169 registros ya guardados y habría que migrar los datos existentes en la misma operación. Hoy la convención de restar 6 horas es más barata y no toca producción.

### 2. Las canceladas no conservan su contenido

**Medido:** de 149 órdenes canceladas, **0 tienen renglones en `orden_detalle`** y todas tienen `total = 0`. Solo 3 tienen eventos `PLATILLO_NUEVO` que permitan reconstruir algo ($350 en total).

Es coherente con que la mayoría de las cancelaciones son cuentas abiertas por error y cerradas rápido (duración promedio 29.8 min contra 154.6 de las pagadas). Pero significa que **el valor de lo cancelado no es una métrica disponible**.

Lo que sí se puede medir: cuántas cancelaciones, de quién, en qué días. `AdminController.cancelaciones` ya expone parte de esto, y hay picos claros (28 el 23 de julio, 26 el 17).

### La trampa dentro de la trampa: casi ninguna "cancelación" es una cancelación

Este es el hallazgo más contraintuitivo de todo el análisis, y el que más fácil lleva a una conclusión equivocada.

De **152 órdenes canceladas en todo el histórico, solo 3 llegaron a tener un platillo**. Las otras 149 nacieron y murieron vacías.

Desglosado por rol:

| Rol | Canceladas | Vida promedio | Llegaron a tener platillos |
|---|---:|---:|---:|
| REPARTIDOR | 103 | **122 segundos** | **0** |
| MESERO | 49 | ~90 min | 3 |

La causa está en el frontend, y el propio código la explica (`EntregasPanel.jsx`):

```js
// Cancela la orden en el servidor — sin esto la orden queda fantasma
// (0 platillos, PREPARANDO) bloqueando el historial y el contador
```

El flujo del repartidor **crea la orden en la base al abrir el diálogo de "Nueva Entrega"**, antes de capturar ningún platillo. Si cierra el diálogo sin enviar a cocina, la interfaz cancela esa orden para no dejar un registro fantasma. Dos minutos de vida, cero platillos, las 103 veces.

Es decir: `estatus = 'CANCELADA'` **no significa "se canceló una venta"**. En el 98% de los casos significa "se abrió un diálogo y se cerró sin usarlo". Es limpieza automática de la interfaz, no un evento de negocio.

**Consecuencias, todas importantes:**

1. **La "tasa de cancelación" no es un KPI de negocio.** Medida tal cual, es ~15% y no dice nada del restaurante. Las cancelaciones reales son 3 de 1,037 órdenes pagadas: 0.3%.
2. **No hay que buscar explicaciones de negocio para las diferencias entre personas.** Los repartidores tienen tasas altas porque *su pantalla crea la orden antes*, no porque cancelen ventas. Un mesero y un repartidor con la misma disciplina producen números muy distintos.
3. **Explica por qué las canceladas están vacías** (la trampa anterior): nunca tuvieron nada que perder. La pregunta "¿cuánto valía lo cancelado?" resulta ser casi irrelevante, no solo irrespondible.
4. **Sí es una señal útil, pero de otra cosa.** 103 órdenes fantasma abiertas y abandonadas en unas semanas dice que el diálogo de entregas crea la orden demasiado pronto. Es un hallazgo de experiencia de uso, no de ventas.

**Regla para cualquier vista o consulta:** si se va a hablar de cancelaciones, distinguir entre *órdenes abandonadas* (vacías, segundos de vida — ruido de interfaz) y *cancelaciones reales* (llegaron a tener platillos). Mezclarlas produce un número grande y sin significado, y peor, señala a personas por un comportamiento del software.

Cómo separarlas:

```sql
-- Cancelaciones reales: llegaron a tener al menos un platillo
SELECT COUNT(DISTINCT e.id_orden)
FROM eventos_orden e
JOIN ordenes o ON o.id_ordenes = e.id_orden
WHERE o.estatus = 'CANCELADA' AND e.tipo_evento = 'PLATILLO_NUEVO';
```

**Cómo se arreglaría:** que `cancelar()` emita un evento `PLATILLO_CANCELADO` por cada renglón vivo antes de vaciar la orden, o que se conserve el `total` en un campo aparte. Es un cambio de código en `Orden.cancelar()` + `OrdenService`, no solo de esquema.

### 3. 150 órdenes sin detalle

149 canceladas + 1 en PREPARANDO. Un `INNER JOIN` entre `ordenes` y `orden_detalle` las descarta silenciosamente, lo cual **está bien para análisis de ventas** (no vendieron nada) pero **está mal para contar órdenes**. Si el tablero dice "1,019 órdenes" en un lado y "1,169" en otro, la diferencia son estas.

### 4. Serie temporal desigual

Datos desde el 11 de abril, pero operación continua desde el **20 de julio**. Entre medias hay días de 1, 2 y 8 órdenes que son pruebas del sistema.

Una gráfica de "ventas por día" sin filtrar muestra una explosión el 20 de julio que parece un éxito comercial espectacular. Es solo el arranque real del sistema. **Filtra `>= 2026-07-20` o marca visualmente el corte.**

---

## Qué falta para poder analizar más

Ordenado por relación valor/esfuerzo. Cada uno requiere migración Flyway y despliegue, así que son decisiones de David, no cambios que se hagan solos.

| Falta | Para responder | Costo aproximado |
|---|---|---|
| **Forma de pago** en `ordenes` | Efectivo vs tarjeta, cuadre de caja | 1 columna + 1 campo en el flujo de cobro |
| **Número de comensales** en `ordenes` | Gasto por persona, ocupación real | 1 columna + 1 input al abrir mesa |
| **Conservar valor de canceladas** | Costo real de las cancelaciones | Cambio en `Orden.cancelar()` (ver trampa 2) |
| **Marca de "platillo listo"** | Tiempo de cocina por platillo | 1 columna + acción en el panel de cocina |
| **Costo de insumos + recetas** | **Margen y utilidad**, no solo ingreso | Ya existe el esquema (V2), falta cargar datos |
| **Identidad del cliente** (teléfono en Para Llevar) | Recurrencia, clientes frecuentes | 1 columna; ojo con implicaciones de datos personales |

El de mayor impacto es el de costos: convierte todo el tablero de "cuánto vendimos" a "cuánto ganamos", que es una pregunta distinta y mucho más útil.
