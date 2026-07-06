package restaurante.api.usuario;

import jakarta.validation.constraints.NotNull;

public record DatosActualizacionUsuario(
        @NotNull
        Long id_usuarios,
        String nombre,
        String email,
        Roles rol,      // opcional: si viene null no se toca
        Integer seccion // opcional: sección de mesas (solo tiene sentido para MESERO)
) {
}
