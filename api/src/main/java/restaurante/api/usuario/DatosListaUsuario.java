package restaurante.api.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DatosListaUsuario(
        @Positive
        Long id_usuarios,
        @NotBlank
        String nombre,
        @NotNull
        Roles rol,
        @NotNull
        Boolean estatus,
        @NotBlank
        @Email
        String email,
        Integer seccion
) {
    // Nunca incluir la contraseña (ni siquiera el hash) en respuestas de la API
    public DatosListaUsuario(Usuario usuario) {
        this(usuario.getId_usuarios(), usuario.getNombre(), usuario.getRol(), usuario.getEstatus(), usuario.getEmail(), usuario.getSeccion());
    }
}
