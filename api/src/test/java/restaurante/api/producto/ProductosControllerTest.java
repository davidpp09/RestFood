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
