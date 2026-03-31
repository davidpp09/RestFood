package restaurante.api.orden;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DatosListaOrden(
        LocalDateTime fecha_apertura,
        Estatus estatus,
        BigDecimal total,
        Long id_usuario,
        Long id_mesa,
         Tipo tipo,
        LocalDateTime fecha_cierre,
        Servicio servicio
) {
    public DatosListaOrden(Orden orden) {
        this(
                orden.getFecha_apertura(),
                orden.getEstatus(),
                orden.getTotal(),
                orden.getUsuario().getId_usuarios(),
                orden.getMesa().getId_mesas(),
                orden.getTipo(),
                orden.getFecha_cierre(),
                orden.getServicio());
    }
}
