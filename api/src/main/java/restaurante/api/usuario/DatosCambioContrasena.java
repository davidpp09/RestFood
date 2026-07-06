package restaurante.api.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// Misma política de contraseña que el registro (DatosRegistroUsuario)
public record DatosCambioContrasena(
        @NotBlank
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
                message = "Debe tener mayúscula, minúscula, número y símbolo")
        String contrasena
) {
}
