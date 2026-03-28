package restaurante.api.producto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DatosRegistroProducto(

        @NotBlank String nombre,
        @Positive BigDecimal precio_comida,
        @Positive BigDecimal precio_desayuno,
        @NotNull Boolean disponibilidad,
        @NotNull Long id_categoria
) {
}
