package restaurante.api.producto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import restaurante.api.categoria.Categoria;
import restaurante.api.categoria.CategoriaRepository;
import restaurante.api.controller.ordenes.ProductosController;
import restaurante.api.ordenDetalle.OrdenDetalleRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductosControllerTest {

    @Mock
    private ProductoRepository repository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private OrdenDetalleRepository ordenDetalleRepository;

    @InjectMocks
    private ProductosController controller;

    private Producto productoDePrueba() {
        var datos = new DatosRegistroProducto("Enchiladas", new BigDecimal("80"), new BigDecimal("70"), true, 1L);
        return new Producto(datos, Mockito.mock(Categoria.class));
    }

    // 🧪 TEST 1: producto sin ventas → se borra físicamente
    @Test
    void eliminar_ProductoSinVentas_BorraFisicamente() {
        var producto = productoDePrueba();
        when(repository.findById(1L)).thenReturn(Optional.of(producto));
        when(ordenDetalleRepository.existePorProducto(1L)).thenReturn(false);

        var respuesta = controller.eliminar(1L);

        assertEquals(204, respuesta.getStatusCode().value());
        verify(repository).delete(producto);
    }

    /** Producto apagado, listo para intentar activarlo. */
    private Producto productoApagadoEn(Categoria categoria) {
        var datos = new DatosRegistroProducto(
                "Tortitas de pollo en salsa roja",
                new BigDecimal("100"), new BigDecimal("100"), false, 7L);
        return new Producto(datos, categoria);
    }

    // 🧪 TEST 3: el menú del día tope en 5 (su recuadro del PDF tiene 5 renglones)
    @Test
    void actualizarDia_ConElCupoLleno_RechazaYNoActiva() {
        var categoria = Mockito.mock(Categoria.class);
        when(categoria.getMaxActivos()).thenReturn(5);
        when(categoria.getId_categorias()).thenReturn(7L);
        when(categoria.getNombre()).thenReturn("Comida del dia");

        var producto = productoApagadoEn(categoria);
        when(repository.findById(1L)).thenReturn(Optional.of(producto));
        when(repository.countActivosPorCategoria(7L)).thenReturn(5L);

        var respuesta = controller.actualizarDia(1L, new DatosActualizacionDia(null, true));

        assertEquals(400, respuesta.getStatusCode().value());
        assertFalse(producto.getDisponibilidad(), "No debió activarse con el cupo lleno");
        assertTrue(respuesta.getBody().toString().contains("5"),
                "El mensaje debe decir cuál es el tope");
    }

    // 🧪 TEST 4: con cupo libre sí activa
    @Test
    void actualizarDia_ConCupoLibre_Activa() {
        var categoria = Mockito.mock(Categoria.class);
        when(categoria.getMaxActivos()).thenReturn(5);
        when(categoria.getId_categorias()).thenReturn(7L);

        var producto = productoApagadoEn(categoria);
        when(repository.findById(1L)).thenReturn(Optional.of(producto));
        when(repository.countActivosPorCategoria(7L)).thenReturn(4L);

        var respuesta = controller.actualizarDia(1L, new DatosActualizacionDia(null, true));

        assertEquals(200, respuesta.getStatusCode().value());
        assertTrue(producto.getDisponibilidad());
    }

    // 🧪 TEST 5: las demás categorías siguen en 7, no se les bajó el tope
    @Test
    void actualizarDia_OtraCategoriaConservaSuTopeDeSiete() {
        var categoria = Mockito.mock(Categoria.class);
        when(categoria.getMaxActivos()).thenReturn(7);
        when(categoria.getId_categorias()).thenReturn(1L);

        var producto = productoApagadoEn(categoria);
        when(repository.findById(1L)).thenReturn(Optional.of(producto));
        when(repository.countActivosPorCategoria(1L)).thenReturn(6L);

        var respuesta = controller.actualizarDia(1L, new DatosActualizacionDia(null, true));

        assertEquals(200, respuesta.getStatusCode().value(),
                "Con 6 activos y tope 7 todavía cabe uno más");
        assertTrue(producto.getDisponibilidad());
    }

    // 🧪 TEST 2: producto con ventas → borrado suave, nunca delete físico
    @Test
    void eliminar_ProductoConVentas_MarcaEliminado() {
        var producto = productoDePrueba();
        when(repository.findById(1L)).thenReturn(Optional.of(producto));
        when(ordenDetalleRepository.existePorProducto(1L)).thenReturn(true);

        var respuesta = controller.eliminar(1L);

        assertEquals(204, respuesta.getStatusCode().value());
        assertTrue(producto.getEliminado());
        assertFalse(producto.getDisponibilidad());
        verify(repository, never()).delete(producto);
        verify(repository, never()).deleteById(1L);
    }
}
