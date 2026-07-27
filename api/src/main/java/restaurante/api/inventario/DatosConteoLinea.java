package restaurante.api.inventario;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DatosConteoLinea(
        @NotNull Long id_insumo,
        @NotNull @PositiveOrZero Integer cantidad_contada
) {
}
