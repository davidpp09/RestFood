package restaurante.api.orden;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import restaurante.api.ordenDetalle.DatosDetalleRespuesta;

import java.math.BigDecimal;
import java.util.List;

public record DatosRespuestaOrden(

        @NotNull
        @Positive
        Long id_orden,
        Integer numero_comanda,
        @NotNull
        @PositiveOrZero
        BigDecimal total,
        @NotNull
        @Valid
        List<DatosDetalleRespuesta> platillos,
        String tipo,
        Long id_mesa,
        String servicio

) {
}
