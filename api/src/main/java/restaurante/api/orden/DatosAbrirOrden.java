package restaurante.api.orden;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DatosAbrirOrden(
        Long id_mesa,
        @NotNull
        Long id_usuario,
        @NotNull
        Tipo tipo,
        @NotNull
        Servicio servicio

) {
}
