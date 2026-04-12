package restaurante.api.ordenDetalle;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record DatosDetalleRespuesta(
        @Positive
        Long id_detalle,
        @NotNull
        @Positive
        Integer cantidad,
        @NotNull
        @PositiveOrZero
        BigDecimal precio_unitario,
        @NotNull
        @PositiveOrZero
        BigDecimal subtotal,
        String comentarios,
        @NotNull
        @Positive
        Long id_orden,
        @NotNull
        @Positive
        Long id_producto,
        String nombre_producto
) {
    public DatosDetalleRespuesta(OrdenDetalle ordenDetalle) {
        this(
                ordenDetalle.getId_detalle(),
                ordenDetalle.getCantidad(),
                ordenDetalle.getPrecio_unitario(),
                ordenDetalle.getSubtotal(),
                ordenDetalle.getComentarios(),
                ordenDetalle.getOrden().getId_ordenes(),
                ordenDetalle.getProducto().getId_productos(),
                ordenDetalle.getProducto().getNombre()
        );

    }
}
