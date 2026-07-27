package restaurante.api.ordenDetalle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import restaurante.api.categoria.Categoria;
import restaurante.api.orden.Orden;
import restaurante.api.orden.Servicio;
import restaurante.api.producto.DatosActualizacionProducto;
import restaurante.api.producto.DatosRegistroProducto;
import restaurante.api.producto.Producto;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * El dinero de una orden. Un botón roto lo reporta una mesera en cinco minutos;
 * un total que suma mal se descubre en tres meses, cuando ya no hay forma de
 * saber cuánto se perdió.
 *
 * Lo que se fija aquí es la regla central: **el precio se congela cuando el
 * platillo entra a la orden**. La carta cambia de precio a media tarde y las
 * cuentas ya abiertas no se enteran — eso es lo correcto, y es exactamente el
 * tipo de cosa que un refactor rompe sin que nadie lo note.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrecioCongeladoTest {

    @Mock
    private Orden orden;

    /** Enchiladas: $80 a la hora de la comida, $70 en el desayuno. */
    private Producto enchiladas() {
        var datos = new DatosRegistroProducto(
                "Enchiladas", new BigDecimal("80"), new BigDecimal("70"), true, 1L);
        return new Producto(datos, Mockito.mock(Categoria.class));
    }

    private DatosPlatilloLote pedir(int cantidad) {
        return new DatosPlatilloLote(null, 1L, cantidad, null);
    }

    private OrdenDetalle detalleEn(Servicio servicio, int cantidad, Producto producto) {
        when(orden.getServicio()).thenReturn(servicio);
        return new OrdenDetalle(pedir(cantidad), producto, orden);
    }

    @Test
    @DisplayName("En COMIDA se cobra el precio de comida")
    void tomaElPrecioDeComida() {
        var detalle = detalleEn(Servicio.COMIDA, 1, enchiladas());
        assertEquals(0, new BigDecimal("80").compareTo(detalle.getPrecio_unitario()),
                "A la hora de la comida se cobra precio_comida, no el de desayuno.");
    }

    @Test
    @DisplayName("En DESAYUNO se cobra el precio de desayuno")
    void tomaElPrecioDeDesayuno() {
        var detalle = detalleEn(Servicio.DESAYUNO, 1, enchiladas());
        assertEquals(0, new BigDecimal("70").compareTo(detalle.getPrecio_unitario()),
                "Los precios de desayuno y comida están separados a propósito; "
                        + "cobrar el de comida en el desayuno es cobrarle de más al cliente.");
    }

    @Test
    @DisplayName("El subtotal es cantidad × precio unitario")
    void subtotalEsCantidadPorPrecio() {
        var detalle = detalleEn(Servicio.COMIDA, 3, enchiladas());
        assertEquals(0, new BigDecimal("240").compareTo(detalle.getSubtotal()),
                "3 × $80 = $240.");
    }

    @Test
    @DisplayName("Si sube el precio de la carta, la orden YA ABIERTA no cambia")
    void elPrecioQuedaCongeladoAunqueCambieLaCarta() {
        var producto = enchiladas();
        var detalle = detalleEn(Servicio.COMIDA, 2, producto);

        // El encargado sube el precio a media tarde, con la mesa todavía abierta.
        producto.actualizar(new DatosActualizacionProducto(
                null, new BigDecimal("95"), null, null, null), null);

        assertEquals(0, new BigDecimal("80").compareTo(detalle.getPrecio_unitario()),
                "El precio se congeló al pedir. Si esto falla, a un cliente sentado "
                        + "se le cobra un precio distinto del que vio en la carta.");
        assertEquals(0, new BigDecimal("160").compareTo(detalle.getSubtotal()),
                "El subtotal tiene que seguir en 2 × $80.");
    }

    @Test
    @DisplayName("Al corregir la cantidad se recalcula con el precio CONGELADO")
    void alCambiarCantidadUsaElPrecioCongelado() {
        var producto = enchiladas();
        var detalle = detalleEn(Servicio.COMIDA, 2, producto);

        producto.actualizar(new DatosActualizacionProducto(
                null, new BigDecimal("95"), null, null, null), null);

        // La mesera corrige: eran 4, no 2.
        detalle.actualizarPlatillo(new DatosPlatilloLote(1L, 1L, 4, "sin cebolla"));

        assertEquals(0, new BigDecimal("320").compareTo(detalle.getSubtotal()),
                "4 × $80 = $320, con el precio viejo. Este es el caso que más fácil "
                        + "se rompe: recalcular leyendo el precio actual del producto "
                        + "daría $380 y le cobraría de más al cliente por corregir una cantidad.");
        assertEquals(4, detalle.getCantidad());
        assertEquals("sin cebolla", detalle.getComentarios());
    }

    @Test
    @DisplayName("Bajar la cantidad también recalcula bien")
    void alBajarCantidadRecalcula() {
        var detalle = detalleEn(Servicio.COMIDA, 5, enchiladas());
        detalle.actualizarPlatillo(new DatosPlatilloLote(1L, 1L, 1, null));
        assertEquals(0, new BigDecimal("80").compareTo(detalle.getSubtotal()),
                "1 × $80 = $80.");
    }
}
