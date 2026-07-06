package restaurante.api.orden;

import java.util.List;

public record DatosTicketCocina(
        Long id_mesa,
        String numero_mesa, // número visible de la mesa (mesas.numero) — es lo que se imprime
        Long id_orden,
        Integer numero_comanda,
        String nombre,
        Tipo tipo,
        List<DatosPlatilloTicket> platillos
) {
}