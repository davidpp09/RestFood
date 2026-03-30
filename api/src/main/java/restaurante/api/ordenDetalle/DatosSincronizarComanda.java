package restaurante.api.ordenDetalle;

import java.util.List;

public record DatosSincronizarComanda(
        Long id_usuario,
        Long id_orden,
        List<DatosPlatilloLote> platillos
) {

}