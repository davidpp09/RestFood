package restaurante.api.inventario;

/**
 * Lo que se ve en la pantalla de existencias. `stock` no sale de una columna:
 * es la suma del kardex, calculada al momento de consultar.
 */
public record DatosExistencia(
        Long id_insumos,
        String nombre,
        Unidad unidad,
        Integer stock,
        Integer stock_minimo,
        Boolean bajo_minimo
) {
    /**
     * Constructor que usa la consulta JPQL.
     *
     * Recibe `stock` como Long porque eso es lo que devuelve SUM() y no hay
     * forma de que devuelva otra cosa. Y `bajo_minimo` se deriva aquí, no con
     * un CASE WHEN dentro del SELECT: en Java se prueba con un test, en SQL
     * solo se puede probar levantando la base.
     */
    public DatosExistencia(Long id_insumos, String nombre, Unidad unidad, Long stock, Integer stock_minimo) {
        this(id_insumos, nombre, unidad,
             stock.intValue(),
             stock_minimo,
             estaBajoMinimo(stock, stock_minimo));
    }

    /**
     * Un mínimo de 0 significa "no me avises de este". Sin esa guarda, todo
     * insumo que llegara a cero dispararía alerta aunque nadie la haya pedido.
     */
    static boolean estaBajoMinimo(long stock, int stock_minimo) {
        return stock_minimo > 0 && stock <= stock_minimo;
    }
}
