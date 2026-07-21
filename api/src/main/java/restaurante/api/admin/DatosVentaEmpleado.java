package restaurante.api.admin;

import java.math.BigDecimal;

public record DatosVentaEmpleado(
        String nombre,
        String rol,
        Integer cantidad,
        BigDecimal total
) {
}
