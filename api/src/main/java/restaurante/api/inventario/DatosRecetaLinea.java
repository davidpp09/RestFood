package restaurante.api.inventario;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DatosRecetaLinea(
        @NotNull Long id_producto,
        @NotNull @Positive Integer cantidad
) {
}
