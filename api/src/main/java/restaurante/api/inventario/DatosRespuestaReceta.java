package restaurante.api.inventario;

import java.util.List;

public record DatosRespuestaReceta(
        Long id_insumo,
        String insumo,
        Unidad unidad,
        List<Linea> platillos
) {
    public record Linea(Long id_producto, String producto, Integer cantidad) {
        public Linea(ProductoInsumo r) {
            this(r.getProducto().getId_productos(), r.getProducto().getNombre(), r.getCantidad());
        }
    }

    public static DatosRespuestaReceta de(Insumo insumo, List<ProductoInsumo> renglones) {
        return new DatosRespuestaReceta(
                insumo.getId_insumos(), insumo.getNombre(), insumo.getUnidad(),
                renglones.stream().map(Linea::new).toList());
    }
}
