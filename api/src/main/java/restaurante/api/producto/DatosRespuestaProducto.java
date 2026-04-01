package restaurante.api.producto;

import java.math.BigDecimal;

public record DatosRespuestaProducto(
        Long id,
        String nombre,
        BigDecimal precioComida,
        BigDecimal precioDesayuno,
        Boolean disponibilidad
) {
    // Este es el constructor que necesita el Controller para el .map()
    public DatosRespuestaProducto(Producto producto) {
        this(
                producto.getId_productos(),
                producto.getNombre(),
                producto.getPrecio_comida(),
                producto.getPrecio_desayuno(),
                producto.getDisponibilidad()
        );
    }
}
