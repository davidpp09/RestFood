package restaurante.api.inventario;

import java.time.LocalDateTime;

public record DatosRespuestaMovimiento(
        Long id_movimiento,
        Long id_insumo,
        String insumo,
        TipoMovimiento tipo,
        Integer cantidad,
        String motivo,
        String usuario,
        LocalDateTime fecha
) {
    public DatosRespuestaMovimiento(MovimientoInventario m) {
        this(m.getId_movimiento(), m.getInsumo().getId_insumos(), m.getInsumo().getNombre(),
             m.getTipo(), m.getCantidad(), m.getMotivo(), m.getUsuario().getNombre(), m.getFecha());
    }
}
