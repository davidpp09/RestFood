package restaurante.api.inventario;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Un renglón del reporte de costos (Fase 3): cuánto cuesta la materia prima de
 * un platillo y qué porcentaje del precio de venta se lleva. Aquí se descubre
 * qué platillos dejan dinero y cuáles no.
 *
 * ADVERTENCIA QUE VA EN EL NOMBRE DE LA COLUMNA: este costo es un PISO, no el
 * costo completo. Solo suma los insumos VIGILADOS (control selectivo) — la
 * tortilla, la salsa, el gas y el aceite no están en el kardex y no aparecen
 * aquí. Sirve para comparar platillos entre sí y para ver tendencias, no para
 * decir "este platillo me cuesta exactamente tanto".
 */
public record DatosFoodCost(
        Long id_producto,
        String producto,

        /** Suma de (cantidad de receta × costo promedio) de sus insumos vigilados. */
        BigDecimal costo_insumos,

        BigDecimal precio_comida,
        BigDecimal precio_desayuno,

        /** costo_insumos como % del precio de comida. En el gremio, 30-35% es lo sano. */
        BigDecimal food_cost_pct,

        /**
         * true si algún insumo de la receta aún no tiene NINGUNA compra con
         * costo: el costo mostrado está incompleto por abajo. Se marca en vez
         * de ocultarse — un número incompleto que se sabe incompleto sirve;
         * uno que se cree completo, engaña.
         */
        boolean costo_incompleto
) {
    public static DatosFoodCost de(Long idProducto, String nombre, BigDecimal costoInsumos,
                                   BigDecimal precioComida, BigDecimal precioDesayuno,
                                   boolean incompleto) {
        BigDecimal pct = (precioComida == null || precioComida.signum() <= 0)
                ? BigDecimal.ZERO
                : costoInsumos.multiply(BigDecimal.valueOf(100))
                              .divide(precioComida, 1, RoundingMode.HALF_UP);
        return new DatosFoodCost(idProducto, nombre,
                costoInsumos.setScale(2, RoundingMode.HALF_UP),
                precioComida, precioDesayuno, pct, incompleto);
    }
}
