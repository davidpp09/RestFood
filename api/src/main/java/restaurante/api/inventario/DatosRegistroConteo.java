package restaurante.api.inventario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DatosRegistroConteo(
        String notas,
        @NotEmpty @Valid List<DatosConteoLinea> lineas
) {
}
