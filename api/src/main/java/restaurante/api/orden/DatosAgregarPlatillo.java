package restaurante.api.orden;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DatosAgregarPlatillo(
        @NotNull
        Long id_usuario,
        @NotNull
        Long id_orden,
        @NotNull
        Long id_producto,
        @Positive
        Integer cantidad,
        String comentarios
) {
}
