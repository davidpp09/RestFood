package restaurante.api.producto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.categoria.Categoria;
import restaurante.api.categoria.CategoriaRepository;
import restaurante.api.controller.ordenes.ProductosController;
import restaurante.api.infra.errores.ValidacionException;
import restaurante.api.ordenDetalle.OrdenDetalleRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    // lenient porque no todos los tests llegan a usar el id: los que rechazan
    // antes (nombre repetido) no lo consultan, y el modo estricto lo marcaría
    // como stub sobrante.
    private Categoria categoriaDelDia() {
        var categoria = Mockito.mock(Categoria.class);
        Mockito.lenient().when(categoria.getId_categorias()).thenReturn(7L);
        return categoria;
    }

    // 🧪 TEST 6: el repartidor da de alta un platillo del día
    @Test
    void registrarDelDia_CreaEnLaCategoriaDelDiaYApagado() {
        var categoria = categoriaDelDia();
        when(categoriaRepository.findCategoriaDelDia()).thenReturn(Optional.of(categoria));
        when(repository.findByNombre("Pollo en pipián")).thenReturn(Optional.empty());
        when(repository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        var datos = new DatosNuevoPlatilloDia("Pollo en pipián", new BigDecimal("105"));
        var respuesta = controller.registrarDelDia(datos, UriComponentsBuilder.newInstance());

        assertEquals(201, respuesta.getStatusCode().value());

        var guardado = ArgumentCaptor.forClass(Producto.class);
        verify(repository).save(guardado.capture());
        assertEquals("Pollo en pipián", guardado.getValue().getNombre());
        assertEquals(categoria, guardado.getValue().getCategoria(),
                "Debe caer en Comida del día, no en la categoría que mande el cliente");
        assertFalse(guardado.getValue().getDisponibilidad(),
                "Nace apagado: dar de alta no es ponerlo en el menú de hoy");
    }

    // 🧪 TEST 7: no se permiten nombres repetidos (saldrían dos veces en el menú)
    @Test
    void registrarDelDia_ConNombreRepetido_Rechaza() {
        var categoria = categoriaDelDia();
        when(categoriaRepository.findCategoriaDelDia()).thenReturn(Optional.of(categoria));
        var yaExiste = new Producto(
                new DatosRegistroProducto("Pollo en pipián", new BigDecimal("105"),
                        new BigDecimal("105"), true, 7L), categoria);
        when(repository.findByNombre("Pollo en pipián")).thenReturn(Optional.of(yaExiste));

        var datos = new DatosNuevoPlatilloDia("Pollo en pipián", new BigDecimal("110"));

        assertThrows(ValidacionException.class,
                () -> controller.registrarDelDia(datos, UriComponentsBuilder.newInstance()));
        verify(repository, never()).save(any(Producto.class));
    }

    // 🧪 TEST 8: si estaba archivado se revive, no se duplica
    @Test
    void registrarDelDia_ConNombreArchivado_LoRevive() {
        var categoria = categoriaDelDia();
        when(categoriaRepository.findCategoriaDelDia()).thenReturn(Optional.of(categoria));
        var archivado = new Producto(
                new DatosRegistroProducto("Pollo en pipián", new BigDecimal("105"),
                        new BigDecimal("105"), true, 7L), categoria);
        archivado.marcarEliminado();
        when(repository.findByNombre("Pollo en pipián")).thenReturn(Optional.of(archivado));

        var datos = new DatosNuevoPlatilloDia("Pollo en pipián", new BigDecimal("120"));
        var respuesta = controller.registrarDelDia(datos, UriComponentsBuilder.newInstance());

        assertEquals(200, respuesta.getStatusCode().value());
        assertFalse(archivado.getEliminado(), "Debió revivir el registro archivado");
        assertEquals(new BigDecimal("120"), archivado.getPrecio_comida());
        verify(repository, never()).save(any(Producto.class));
    }

    // 🧪 TEST 9: el nombre se limpia de espacios antes de guardarlo.
    // Justo el tipo de errata que hubo que corregir con la migración V6.
    @Test
    void registrarDelDia_RecortaEspaciosDelNombre() {
        var categoria = categoriaDelDia();
        when(categoriaRepository.findCategoriaDelDia()).thenReturn(Optional.of(categoria));
        when(repository.findByNombre("Pollo en pipián")).thenReturn(Optional.empty());
        when(repository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.registrarDelDia(
                new DatosNuevoPlatilloDia("  Pollo en pipián  ", new BigDecimal("105")),
                UriComponentsBuilder.newInstance());

        var guardado = ArgumentCaptor.forClass(Producto.class);
        verify(repository).save(guardado.capture());
        assertEquals("Pollo en pipián", guardado.getValue().getNombre());
    }

    // 🧪 TEST 10: archivar solo alcanza a la categoría del día, no a toda la carta
    @Test
    void archivarDelDia_ConPlatilloDeOtraCategoria_Rechaza() {
        var otraCategoria = Mockito.mock(Categoria.class);
        when(otraCategoria.getId_categorias()).thenReturn(1L);
        when(otraCategoria.getNombre()).thenReturn("Comida");

        var producto = new Producto(
                new DatosRegistroProducto("Pechuga empanizada con papas", new BigDecimal("113"),
                        new BigDecimal("113"), true, 1L), otraCategoria);
        // El mock de la categoría se arma FUERA del when(...): armarlo dentro
        // significa stubbear un mock mientras se stubbea otro, y Mockito lo
        // rechaza con un error que no se parece en nada a la causa.
        var categoriaDia = categoriaDelDia();
        when(repository.findById(1L)).thenReturn(Optional.of(producto));
        when(categoriaRepository.findCategoriaDelDia()).thenReturn(Optional.of(categoriaDia));

        assertThrows(ValidacionException.class, () -> controller.archivarDelDia(1L));
        assertFalse(producto.getEliminado(), "No debió tocar un platillo de la carta");
    }

    // 🧪 TEST 11: archivar uno del día sí funciona, y es borrado suave
    @Test
    void archivarDelDia_ConPlatilloDelDia_LoMarcaEliminado() {
        var categoria = categoriaDelDia();
        var producto = new Producto(
                new DatosRegistroProducto("Pollo en pipián", new BigDecimal("105"),
                        new BigDecimal("105"), true, 7L), categoria);
        when(repository.findById(1L)).thenReturn(Optional.of(producto));
        when(categoriaRepository.findCategoriaDelDia()).thenReturn(Optional.of(categoria));

        var respuesta = controller.archivarDelDia(1L);

        assertEquals(204, respuesta.getStatusCode().value());
        assertTrue(producto.getEliminado());
        assertFalse(producto.getDisponibilidad());
        verify(repository, never()).delete(any(Producto.class));
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
