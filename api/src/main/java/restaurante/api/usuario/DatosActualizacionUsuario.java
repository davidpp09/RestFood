package restaurante.api.usuario;

import jakarta.validation.constraints.NotNull;

public record DatosActualizacionUsuario(
        @NotNull
        Long id_usuarios,
        String nombre,
        String email
) {
}
