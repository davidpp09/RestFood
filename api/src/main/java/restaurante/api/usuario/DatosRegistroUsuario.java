package restaurante.api.usuario;

import jakarta.validation.constraints.*;

public record DatosRegistroUsuario(

        @NotBlank String nombre,
        @NotNull Roles rol,
        @NotBlank
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
        message = "Debe tener mayúscula, minúscula, número y símbolo")
        String contrasena,
        @NotBlank @Email String email
) {
}
