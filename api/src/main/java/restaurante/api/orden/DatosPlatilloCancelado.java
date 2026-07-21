package restaurante.api.orden;

import java.time.LocalDateTime;

// Un platillo que fue cancelado dentro de una comanda (evento PLATILLO_CANCELADO),
// para poder ver en el panel de admin qué se canceló, cuándo y quién lo hizo.
public record DatosPlatilloCancelado(
        String nombre,
        Integer cantidad,
        LocalDateTime hora,
        String canceladoPor,
        String comentarios
) {}
