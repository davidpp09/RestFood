package restaurante.api.inventario;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * La cantidad se pide SIEMPRE en positivo: quien captura dice "llegaron 20" o
 * "se echaron a perder 3", nunca "menos 3". El signo lo pone el servidor según
 * el tipo, que es donde puede validarse en un solo lugar.
 */
public record DatosRegistroMovimiento(
        @NotNull Long id_insumo,
        @NotNull TipoMovimiento tipo,
        @NotNull @Positive Integer cantidad,
        String motivo
) {
}
