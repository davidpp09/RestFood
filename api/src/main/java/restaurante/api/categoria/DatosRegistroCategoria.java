package restaurante.api.categoria;

import jakarta.validation.constraints.NotBlank;

public record DatosRegistroCategoria(

        @NotBlank String nombre,
        @NotBlank String impresora
) {
}
