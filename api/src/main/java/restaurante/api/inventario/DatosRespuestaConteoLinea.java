package restaurante.api.inventario;

public record DatosRespuestaConteoLinea(
        Long id_insumo,
        String nombre,
        Integer cantidad_contada,
        Integer cantidad_teorica,
        Integer varianza
) {
    public DatosRespuestaConteoLinea(ConteoDetalle d) {
        this(d.getInsumo().getId_insumos(), d.getInsumo().getNombre(),
             d.getCantidad_contada(), d.getCantidad_teorica(), d.varianza());
    }
}
