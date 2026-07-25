package restaurante.api.inventario;

public record DatosRespuestaInsumo(
        Long id_insumos,
        String nombre,
        Unidad unidad,
        Integer stock_minimo,
        Boolean activo
) {
    public DatosRespuestaInsumo(Insumo i) {
        this(i.getId_insumos(), i.getNombre(), i.getUnidad(), i.getStock_minimo(), i.getActivo());
    }
}
