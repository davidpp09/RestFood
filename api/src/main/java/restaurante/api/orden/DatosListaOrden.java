package restaurante.api.orden;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import restaurante.api.usuario.Usuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DatosListaOrden(
        LocalDateTime fecha_apertura,
        Estatus estatus,
        BigDecimal total,
        Long id_usuario,
        Long id_mesa,
         Tipo tipo
) {
    public DatosListaOrden(Orden orden) {
        this(
                orden.getFecha_apertura(),
                orden.getEstatus(),
                orden.getTotal(),
                orden.getUsuario().getId_usuarios(),
                orden.getMesa().getId_mesas(),
                orden.getTipo());
    }
}
