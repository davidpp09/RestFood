package restaurante.api.inventario;

import java.time.LocalDateTime;
import java.util.List;

public record DatosRespuestaConteo(
        Long id_conteo,
        LocalDateTime fecha,
        String usuario,
        String notas,
        List<DatosRespuestaConteoLinea> lineas
) {
    public DatosRespuestaConteo(ConteoFisico c) {
        this(c.getId_conteo(), c.getFecha(), c.getUsuario().getNombre(), c.getNotas(),
             c.getDetalles().stream().map(DatosRespuestaConteoLinea::new).toList());
    }
}
