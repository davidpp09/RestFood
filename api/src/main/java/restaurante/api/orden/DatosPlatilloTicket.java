package restaurante.api.orden;

public record DatosPlatilloTicket(
        String accion,       // "🟢 NUEVO", "🔴 CANCELADO", "🟡 MODIFICADO"
        String nombre,       // Ej: "Pechuga Empanizada"
        Integer cantidad,    // Ej: 2
        String comentarios,  // Ej: "Sin cebolla"
        String impresora     // "COCINA_1", "COCINA_2", "SIN_IMPRESION"
) {
}