package restaurante.api.inventario;

import jakarta.validation.constraints.PositiveOrZero;

public record DatosActualizacionInsumo(
        String nombre,
        Unidad unidad,
        @PositiveOrZero Integer stock_minimo,
        Boolean activo
) {
}
