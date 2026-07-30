package restaurante.api.producto;

import restaurante.api.categoria.DatosRespuestaCategoria;

import java.math.BigDecimal;

public record DatosRespuestaProducto(
        Long id,
        String nombre,
        BigDecimal precioComida,
        BigDecimal precioDesayuno,
        Boolean disponibilidad,
        DatosRespuestaCategoria categoria
) {
    public DatosRespuestaProducto(Producto producto) {
        this(
                producto.getId_productos(),
                producto.getNombre(),
                producto.getPrecio_comida(),
                producto.getPrecio_desayuno(),
                producto.getDisponibilidad(),
                new DatosRespuestaCategoria(producto.getCategoria())
        );
    }
}
