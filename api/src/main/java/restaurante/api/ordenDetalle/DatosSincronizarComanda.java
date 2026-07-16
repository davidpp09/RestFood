package restaurante.api.ordenDetalle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record DatosSincronizarComanda(
        @NotNull
        @Positive
        Long id_usuario,

        @NotNull
        @Positive
        Long id_orden,

        @NotNull
        @Valid
        List<DatosPlatilloLote> platillos,

        // Opcional — solo lo manda el panel de entregas (PARA LLEVAR)
        DatosTiemposComanda tiempos
) {

}