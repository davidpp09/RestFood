package restaurante.api.mesa;

import restaurante.api.ordenDetalle.DatosDetalleRespuesta;
import java.util.List;

public record DatosRespuestaMesa(
    Long id_mesa, 
    String numero, 
    String estado, 
    Long id_orden,
    String nombre_mesero,
    List<DatosDetalleRespuesta> platillos
) {
}
