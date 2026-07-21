package restaurante.api.orden;

import restaurante.api.ordenDetalle.DatosDetalleRespuesta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Una comanda/orden vista desde el panel de admin, con todo su detalle de
// platillos, para revisar lo que capturó cada empleado a lo largo del día.
public record DatosComandaEmpleado(
        Long id_orden,
        Integer numero_comanda,
        LocalDateTime fechaApertura,
        LocalDateTime fechaCierre,
        Estatus estatus,
        Tipo tipo,
        Servicio servicio,
        BigDecimal total,
        String numeroMesa,        // null si es Para Llevar
        Long id_usuario,
        String nombreEmpleado,
        String rolEmpleado,
        List<DatosDetalleRespuesta> platillos,
        List<DatosPlatilloCancelado> cancelados
) {}
