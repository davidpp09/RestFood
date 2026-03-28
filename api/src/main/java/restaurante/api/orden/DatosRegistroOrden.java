package restaurante.api.orden;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DatosRegistroOrden(

        @NotNull @PastOrPresent LocalDateTime fecha_apertura,
        @NotNull Estatus estatus,
        @PositiveOrZero BigDecimal total,
        @NotNull Long id_usuario,
        Long id_mesa,
        @NotNull Tipo tipo
) {
}
