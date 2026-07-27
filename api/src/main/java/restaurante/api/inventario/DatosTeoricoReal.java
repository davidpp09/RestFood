package restaurante.api.inventario;

/**
 * Un renglón del reporte que es el motivo de todo este frente: comparar lo que
 * el sistema cree que se consumió contra lo que de verdad falta del
 * refrigerador.
 *
 * `ajustes` es el número que importa. Sale de los conteos físicos y es, por
 * definición, **lo que nadie pudo explicar**: ni venta, ni merma registrada, ni
 * compra. Merma no anotada, porciones más generosas de lo definido, o robo.
 * El reporte no distingue entre esas causas y no debe pretender hacerlo — solo
 * señala dónde mirar.
 *
 * Se mira la TENDENCIA, no el número de una semana. Una varianza del 3 al 5%
 * del consumo es normal: las porciones varían de mano a mano. Lo que se vigila
 * es que crezca, o que un insumo se despegue del resto.
 */
public record DatosTeoricoReal(
        Long id_insumo,
        String nombre,
        Unidad unidad,

        /** Lo que entró por compras en el periodo. */
        int compras,

        /** Consumo por ventas: lo que las recetas dicen que se usó. */
        int consumo_ventas,

        /** Merma registrada a propósito por alguien, más las cancelaciones. */
        int merma,

        /**
         * Diferencia encontrada en los conteos físicos. Negativo = faltaba
         * producto; positivo = había de más (casi siempre, una compra sin
         * capturar).
         */
        int ajustes,

        /** Existencia actual según el kardex. */
        int stock,

        /**
         * `ajustes` como porcentaje del consumo total. Es lo comparable entre
         * insumos: 10 piezas de diferencia en la pechuga no significan lo mismo
         * que 10 en la arrachera.
         */
        double porcentaje_varianza,

        /**
         * Un stock negativo no es un error de cálculo: es mercancía que entró y
         * nadie capturó. Se marca aparte porque invalida el resto del renglón —
         * mientras haya negativos, la varianza no significa nada.
         */
        boolean stock_negativo
) {
    public static DatosTeoricoReal de(Insumo insumo, int compras, int consumoVentas,
                                      int merma, int ajustes, int stock) {
        int consumoTotal = Math.abs(consumoVentas) + Math.abs(merma);
        double porcentaje = consumoTotal == 0
                ? 0.0
                : Math.round((Math.abs(ajustes) * 10000.0) / consumoTotal) / 100.0;

        return new DatosTeoricoReal(
                insumo.getId_insumos(), insumo.getNombre(), insumo.getUnidad(),
                compras, consumoVentas, merma, ajustes, stock, porcentaje, stock < 0);
    }
}
