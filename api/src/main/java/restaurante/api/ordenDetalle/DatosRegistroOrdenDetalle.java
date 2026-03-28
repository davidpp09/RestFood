package restaurante.api.ordenDetalle;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DatosRegistroOrdenDetalle(


        @Positive Integer cantidad,
        @Positive BigDecimal precio_unitario,
        @Positive BigDecimal subtotal,
        String comentarios,
        @NotNull Long id_orden,
        @NotNull Long id_producto
) {
}
