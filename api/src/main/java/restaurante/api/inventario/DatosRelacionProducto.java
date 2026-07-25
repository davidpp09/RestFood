package restaurante.api.inventario;

/**
 * Una relación vista "de plano": qué platillo, qué insumo, cuánto.
 *
 * Sirve para el panorama completo, que es lo que permite a la pantalla marcar
 * un platillo que ya está en OTRO insumo. Sin esta vista global, cada receta
 * se edita a ciegas y es fácil terminar descontando dos insumos por un
 * platillo que solo lleva uno.
 */
public record DatosRelacionProducto(
        Long id_producto,
        String producto,
        Long id_insumo,
        String insumo,
        Integer cantidad
) {
    public DatosRelacionProducto(ProductoInsumo r) {
        this(r.getProducto().getId_productos(), r.getProducto().getNombre(),
             r.getInsumo().getId_insumos(), r.getInsumo().getNombre(), r.getCantidad());
    }
}
