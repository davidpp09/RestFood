package restaurante.api.inventario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DatosRegistroInsumo(
        @NotBlank String nombre,
        @NotNull Unidad unidad,
        @PositiveOrZero Integer stock_minimo
) {
}
