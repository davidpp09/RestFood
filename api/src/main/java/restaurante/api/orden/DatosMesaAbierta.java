package restaurante.api.orden;

import restaurante.api.mesa.Estado;
import java.time.LocalDateTime;

public record DatosMesaAbierta(
        Long id_mesa,
        Estado estado,
        String nombre_mesero,
        Long id_orden,
        Integer numero_comanda,
        LocalDateTime fechaApertura
) {
}