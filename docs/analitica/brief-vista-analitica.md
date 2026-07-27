# Brief para diseño — Vista de análisis de RestFood

Esto es lo que se le pasa a diseño para que construya la vista. Contiene las preguntas de negocio reales, qué widget responde cada una, con qué datos, y los contratos JSON que el backend tendría que exponer.

**Contexto que diseño necesita saber:** RestFood es el sistema de un restaurante en producción. El usuario de esta vista es **David (dueño/admin), en un solo dispositivo, revisando al final del día o entre servicios**. No es un tablero corporativo para diez personas. Eso cambia las decisiones: menos filtros, más respuestas directas.

---

## Lo que ya existe

Tres endpoints de admin en `AdminController`:

| Endpoint | Devuelve |
|---|---|
| `GET /admin/corte-dia?fecha=` | Totales del día: por empleado, desayuno/comida, platillos loza/llevar, total general |
| `GET /admin/comandas-dia?fecha=` | Todas las comandas del día con su detalle completo |
| `GET /admin/cancelaciones?desde=&hasta=` | Cancelaciones agrupadas por mesero, con los productos cancelados |

Todos son **de un día** (o un rango corto) y **operativos**: sirven para cuadrar la caja hoy. Ninguno responde preguntas de tendencia, ranking o comparación entre periodos. Ahí está el hueco que llena la vista nueva.

**La vista nueva no reemplaza el corte del día.** Son cosas distintas: el corte es "¿cuadró hoy?", la vista de análisis es "¿cómo vamos?".

---

## Las preguntas, en orden de importancia

Salieron de mirar qué se puede responder con los datos que hay. Están ordenadas por cuánto ayudan a decidir algo.

### 1. ¿Cómo vamos comparado con antes?

La pregunta que se hace todos los días. Necesita: ingreso del periodo, número de órdenes, ticket promedio, y **la variación contra el periodo anterior equivalente**.

Un número solo no dice nada. "$34,000" no significa nada; "$34,000, +12% vs la semana pasada" sí.

### 2. ¿A qué horas llega la gente?

Determina cuánto personal poner y a qué hora. Los datos reales (desde el 20 de julio, hora local ya corregida):

| Hora | Desayuno | Comida | Total |
|---:|---:|---:|---:|
| 9 | 21 | 1 | 22 |
| 10 | 61 | 6 | 67 |
| 11 | 56 | 3 | 59 |
| 12 | 15 | 45 | 60 |
| 13 | 0 | 137 | **137** |
| 14 | 0 | 193 | **193** |
| 15 | 1 | 165 | **165** |
| 16 | 0 | 114 | 114 |
| 17 | 1 | 45 | 46 |
| 18 | 1 | 4 | 5 |

Dos picos limpios y separados: desayuno 10–11, comida 13–15. El de comida es tres veces más grande. Entre 12 y 13 hay un cruce donde conviven los dos servicios.

**Esta gráfica es la que más valor tiene por metro cuadrado de pantalla.**

### 3. ¿Qué se vende y qué no?

Ranking de productos. **Ojo:** por unidades y por ingreso dan órdenes distintos (ver catálogo). El widget tiene que dejar claro por cuál ordena, e idealmente permitir cambiar.

Complemento igual de útil: **los que no se venden**. Hay productos en el menú actual con cero ventas históricas — eso es menú muerto que ocupa espacio en la carta y en la cabeza del mesero.

### 4. ¿Cómo va cada quien?

Ventas por empleado y ticket promedio. Con una advertencia fuerte, abajo en "Cuidados".

### 5. ¿Cuánta comida salió de la cocina?

Platillos vendidos en el periodo. Es la carga real de trabajo de cocina, y no se deduce del número de órdenes: una cuenta puede llevar un platillo o doce.

(Aquí iba originalmente "¿qué se está cancelando?". Se cayó al descubrir que las cancelaciones no miden lo que parecen — ver Cuidados.)

---

## Estructura propuesta

Una sola página, scroll vertical, tres bandas. Diseño decide la forma; esto es el contenido y la jerarquía.

```
┌─────────────────────────────────────────────────────────┐
│  [Hoy] [7 días] [30 días] [Personalizado]               │  selector de periodo
├─────────────────────────────────────────────────────────┤
│                                                          │
│  BANDA 1 — LOS NÚMEROS                                   │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐        │
│  │ Ingreso │ │ Órdenes │ │ Ticket  │ │Platillos│        │
│  │ $34,120 │ │   287   │ │  $119   │ │   612   │        │
│  │ ▲ +12%  │ │ ▲ +8%   │ │ ▲ +4%   │ │ ▲ +9%   │        │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘        │
│                                                          │
├─────────────────────────────────────────────────────────┤
│  BANDA 2 — CUÁNDO Y CUÁNTO                               │
│  ┌──────────────────────┐ ┌──────────────────────┐      │
│  │ Ventas por día       │ │ Demanda por hora     │      │
│  │ (barras, desayuno/   │ │ (dos series:         │      │
│  │  comida apiladas)    │ │  desayuno y comida)  │      │
│  └──────────────────────┘ └──────────────────────┘      │
├─────────────────────────────────────────────────────────┤
│  BANDA 3 — QUÉ Y QUIÉN                                   │
│  ┌──────────────────────┐ ┌──────────────────────┐      │
│  │ Top productos        │ │ Por empleado         │      │
│  │ [unidades|ingreso]   │ │ (con desglose por    │      │
│  │                      │ │  tipo de orden)      │      │
│  └──────────────────────┘ └──────────────────────┘      │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Menú muerto — productos sin ventas               │    │
│  └─────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Órdenes abandonadas (informativo, discreto)      │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

**Banda 1 son las respuestas. Bandas 2 y 3 son el porqué.** Si el usuario solo mira lo de arriba y se va, ya obtuvo lo que necesitaba.

---

## Cuidados (esto no es opcional)

Son consecuencias directas de cómo son los datos. Ignorarlas produce una vista que miente con confianza.

### El eje de tiempo arranca el 20 de julio

Hay datos desde abril, pero son pruebas: días sueltos de 1 a 18 órdenes. La operación real empieza el 20 de julio con 149–184 órdenes diarias.

Una gráfica de "todo el histórico" muestra una línea plana y luego un salto vertical el 20 de julio. **Parece un despegue comercial espectacular; es solo el arranque del sistema.**

Opciones, en orden de preferencia:
1. Que el rango por defecto sea "últimos 7 días" y no ofrecer nada anterior al 20 de julio.
2. Si se muestra el histórico completo, marcar visualmente el corte con una anotación: *"antes de esta fecha: pruebas del sistema"*.

### La tasa de cancelación NO es un KPI — no la pongas

De 152 órdenes canceladas en el histórico, **solo 3 llegaron a tener un platillo**. Las otras 149 estaban vacías.

La causa es mecánica, no de negocio: el diálogo de "Nueva Entrega" crea la orden en la base **al abrirse**, antes de capturar nada. Si el repartidor lo cierra sin enviar a cocina, la interfaz cancela esa orden para no dejar un registro fantasma. Los repartidores acumulan 103 de estas, con vida promedio de **122 segundos** y cero platillos, siempre.

Entonces "cancelación" significa, casi siempre, *"se abrió un diálogo y se cerró"*. Es limpieza de interfaz.

**Requisitos, en orden:**

1. **No pongas "Tasa de cancelación" en la banda 1.** Medida en crudo da ~15% y no significa nada. Las cancelaciones reales son 3 de 1,037: 0.3%. Un KPI destacado que no mide nada es peor que un espacio vacío.
2. **Nunca compares personas con este número.** Un repartidor y un mesero igual de cuidadosos dan cifras muy distintas porque sus pantallas crean la orden en momentos distintos del flujo. Una vista que ponga a una empleada en rojo al 34% por esto va a producir una conversación injusta con una persona real, sobre un comportamiento del software.
3. **Si se muestra algo**, que sea en la banda 3 y etiquetado como lo que es: *"Órdenes abandonadas"*, con una nota de que son órdenes abiertas y cerradas sin capturar nada. Es una señal de experiencia de uso —el diálogo crea la orden demasiado pronto— no una métrica de ventas.

### "Ventas por empleado" mide captura, no desempeño

`ordenes.id_usuario` es **quién capturó la orden en la tablet**. En un turno, quien atiende y quien cobra pueden ser distintos, y quien captura acumula la venta.

Etiquétese el widget como *"Órdenes capturadas por empleado"*, no *"Ventas por empleado"*. La diferencia de una palabra cambia cómo se lee.

### El valor de lo cancelado no existe

Las órdenes canceladas tienen total 0 y cero renglones — porque, como explica el punto anterior, casi ninguna llegó a tener un platillo. **No hay dato de "$X perdidos en cancelaciones"**, y resulta que tampoco hay gran cosa que perder. Si el diseño lo pide, la respuesta es que no; ponerlo en $0 sería peor que no ponerlo.

### No hay margen, solo ingreso

Nada de "utilidad", "ganancia" o "margen" en las etiquetas. El sistema no conoce el costo de los platillos. Todo lo que se muestra es **ingreso**.

### Las horas ya vienen corregidas del backend

El backend entrega horas locales. Diseño no hace ninguna conversión. (El detalle de por qué está en el catálogo; a la vista le llega ya resuelto.)

---

## Endpoints propuestos

Cuatro endpoints nuevos bajo `/admin/analitica`, con `@PreAuthorize("hasAnyRole('ADMIN','DEV')")` como el resto de `AdminController`.

**Convenciones para los cuatro:**
- `desde` y `hasta` son fechas locales `YYYY-MM-DD`, inclusivas.
- Toda hora en la respuesta es **hora local ya corregida**.
- Todo importe es **ingreso**, solo de órdenes `PAGADA`.
- Ningún endpoint devuelve email ni nada de la tabla `usuarios` salvo `nombre` y `rol`.

### 1. `GET /admin/analitica/resumen?desde=&hasta=`

Alimenta la banda 1. Incluye el periodo anterior equivalente para las variaciones — se calcula en el backend para que la vista no tenga que hacer dos llamadas ni saber cuántos días tiene el periodo.

```json
{
  "desde": "2026-07-21",
  "hasta": "2026-07-27",
  "actual": {
    "ingreso": 34120.00,
    "ordenes": 287,
    "ticketPromedio": 118.89,
    "platillos": 612,
    "porServicio": { "desayuno": 8210.00, "comida": 25910.00 },
    "porTipo":     { "loza": 26400.00, "llevar": 7720.00 },
    "ordenesAbandonadas": 24
  },
  "anterior": {
    "ingreso": 30460.00,
    "ordenes": 266,
    "ticketPromedio": 114.51,
    "platillos": 561,
    "ordenesAbandonadas": 26
  },
  "variacion": {
    "ingreso": 12.0,
    "ordenes": 7.9,
    "ticketPromedio": 3.8,
    "platillos": 9.1
  }
}
```

`variacion` viene en porcentaje.

`ordenesAbandonadas` son las órdenes que se abrieron y se cerraron sin capturar nada (`estatus = 'CANCELADA'` con cero platillos). **No es un KPI de negocio** y no va en la banda 1 — está en el contrato porque alimenta el widget informativo de la banda 3. Ver Cuidados.

### 2. `GET /admin/analitica/serie?desde=&hasta=&granularidad=dia|hora`

Alimenta la banda 2. Un solo endpoint para las dos gráficas, cambiando `granularidad`.

Con `granularidad=dia`:

```json
{
  "granularidad": "dia",
  "puntos": [
    { "clave": "2026-07-21", "ordenes": 149, "ingreso": 31450.00,
      "desayuno": 7100.00, "comida": 24350.00 },
    { "clave": "2026-07-22", "ordenes": 153, "ingreso": 32780.00,
      "desayuno": 6890.00, "comida": 25890.00 }
  ]
}
```

Con `granularidad=hora` — `clave` es la hora local 0–23, agregada sobre todo el rango:

```json
{
  "granularidad": "hora",
  "puntos": [
    { "clave": "13", "ordenes": 137, "ingreso": 16710.00,
      "desayuno": 0.00, "comida": 16710.00 },
    { "clave": "14", "ordenes": 193, "ingreso": 21340.00,
      "desayuno": 0.00, "comida": 21340.00 }
  ]
}
```

**Solo devuelve horas con actividad.** El restaurante opera de 9 a 18; devolver 24 puntos con 14 en cero obliga a la gráfica a desperdiciar dos tercios del eje.

### 3. `GET /admin/analitica/productos?desde=&hasta=&orden=ingreso|unidades&limite=20`

```json
{
  "productos": [
    { "id": 12, "nombre": "Pechuga empanizada con papas", "categoria": "Comida",
      "unidades": 182, "ingreso": 20430.00, "precioPromedio": 112.25,
      "ordenesEnQueAparece": 171 }
  ],
  "sinVentas": [
    { "id": 88, "nombre": "Papas gajo", "categoria": "Snacks", "precio": 45.00 }
  ]
}
```

`sinVentas` sale en la misma respuesta porque alimenta el widget de "menú muerto" y se calcula sobre el mismo conjunto. Solo productos con `eliminado = 0`: la pregunta es sobre el menú vigente.

`precioPromedio` es `ingreso / unidades` — puede diferir del precio de catálogo si el precio cambió durante el periodo, y eso es correcto.

### 4. `GET /admin/analitica/empleados?desde=&hasta=`

```json
{
  "empleados": [
    {
      "nombre": "MARELI",
      "rol": "MESERO",
      "ordenes": 240,
      "ingreso": 41200.00,
      "ticketPromedio": 171.67,
      "platillos": 498,
      "porTipo": { "loza": 232, "llevar": 8 }
    }
  ],
  "abandonadas": { "MARELI": 17, "VALERIA": 17, "SRA.ANGELES": 51, "HECTOR": 47 }
}
```

**`abandonadas` no se muestra junto a los empleados.** Va en la respuesta para que el widget informativo de órdenes abandonadas (banda 3) pueda existir, pero pintarlo como una columna al lado de cada persona es exactamente el error que describen los Cuidados: son cifras que dependen de qué pantalla usa cada rol, no de qué tan bien trabaja.

---

## Implementación en el backend

Notas para quien escriba los endpoints, no para diseño:

- Las consultas están resueltas en [`consultas.sql`](consultas.sql) y probadas contra producción. Los bloques 1–4 cubren los cuatro endpoints.
- La corrección de zona horaria se hace **en la consulta** (`- INTERVAL 6 HOUR`), no en Java. Así el mismo SQL sirve para la API y para Metabase o exportaciones.
- Los parámetros `desde`/`hasta` llegan como `LocalDate` y se convierten con `atStartOfDay()` / `atTime(LocalTime.MAX)`, igual que ya lo hace `OrdenService`. Esa parte funciona: el driver aplica la misma conversión en ambos lados. **No mezclar la corrección manual de 6h con parámetros `LocalDateTime` en la misma consulta** — se corregiría dos veces.
- Con 1,169 órdenes y 2,040 renglones no hace falta cachear ni pre-agregar nada. Cuando la tabla llegue a cientos de miles, se revisa; hoy sería optimización prematura.
- Cada endpoint nuevo debería llevar su test, siguiendo el patrón de `OrdenServiceTest`.
