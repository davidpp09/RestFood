package restaurante.api.inventario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import restaurante.api.orden.Orden;
import restaurante.api.producto.Producto;
import restaurante.api.usuario.Usuario;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El descuento automático de la Fase 2. Aquí se decide si el teórico va a
 * significar algo o va a ser ruido, así que conviene tenerlo clavado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsumoInventarioServiceTest {

    @Mock private ProductoInsumoRepository recetaRepository;
    @Mock private MovimientoInventarioRepository movimientoRepository;

    @InjectMocks private ConsumoInventarioService servicio;

    private static final long ID_EMPANADAS = 207L;

    private Producto empanadas() {
        Producto p = Mockito.mock(Producto.class);
        when(p.getId_productos()).thenReturn(ID_EMPANADAS);
        when(p.getNombre()).thenReturn("Empanadas de platano rellenas con carne");
        return p;
    }

    private Orden comanda() {
        Orden o = Mockito.mock(Orden.class);
        when(o.getNumero_comanda()).thenReturn(42);
        return o;
    }

    private Insumo carneMolida() {
        Insumo i = Mockito.mock(Insumo.class);
        when(i.getNombre()).thenReturn("Carne molida");
        return i;
    }

    /** Una orden de empanadas consume 2 porciones de carne molida. */
    private void recetaDeDosPorciones(Insumo insumo) {
        ProductoInsumo linea = Mockito.mock(ProductoInsumo.class);
        when(linea.getInsumo()).thenReturn(insumo);
        when(linea.getCantidad()).thenReturn(2);
        when(recetaRepository.porProducto(ID_EMPANADAS)).thenReturn(List.of(linea));
    }

    private List<MovimientoInventario> movimientosGuardados(int cuantos) {
        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoRepository, times(cuantos)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("Descuenta cantidad × receta: 3 órdenes de empanadas son 6 porciones")
    void descuentaLaCantidadPorLaReceta() {
        Insumo carne = carneMolida();
        recetaDeDosPorciones(carne);

        servicio.descontarPorComanda(empanadas(), 3, comanda(), Mockito.mock(Usuario.class));

        var mov = movimientosGuardados(1).get(0);
        assertEquals(TipoMovimiento.VENTA, mov.getTipo());
        assertEquals(-6, mov.getCantidad(),
                "3 órdenes × 2 porciones = 6, y sale, así que va en negativo. "
                        + "Si esto descontara 3, el teórico se despegaría al doble de velocidad "
                        + "y la diferencia parecería robo.");
        assertSame(carne, mov.getInsumo());
    }

    @Test
    @DisplayName("El movimiento queda ligado a la orden que lo causó")
    void elMovimientoApuntaALaOrden() {
        recetaDeDosPorciones(carneMolida());
        Orden orden = comanda();

        servicio.descontarPorComanda(empanadas(), 1, orden, Mockito.mock(Usuario.class));

        var mov = movimientosGuardados(1).get(0);
        assertSame(orden, mov.getOrden(),
                "Sin la liga a la orden, un consumo raro no se puede rastrear hasta la mesa.");
        assertTrue(mov.getMotivo().contains("42"), "El motivo debe citar la comanda: " + mov.getMotivo());
    }

    @Test
    @DisplayName("Un platillo sin receta no mueve el inventario")
    void platilloSinRecetaNoMueveNada() {
        when(recetaRepository.porProducto(ID_EMPANADAS)).thenReturn(List.of());

        servicio.descontarPorComanda(empanadas(), 5, comanda(), Mockito.mock(Usuario.class));

        verify(movimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Bajar la cantidad no devuelve nada al inventario")
    void unIncrementoNegativoNoMueveNada() {
        recetaDeDosPorciones(carneMolida());

        // Así llama OrdenService cuando el mesero corrige de 4 a 2 platillos.
        servicio.descontarPorComanda(empanadas(), -2, comanda(), Mockito.mock(Usuario.class));

        verify(movimientoRepository, never()).save(any());
        // La comida ya se cocinó. Devolverla al inventario haría que el teórico
        // mintiera a favor, que es justo lo que este sistema existe para evitar.
    }

    @Test
    @DisplayName("Cantidad cero no genera un renglón vacío en el kardex")
    void cantidadCeroNoMueveNada() {
        recetaDeDosPorciones(carneMolida());
        servicio.descontarPorComanda(empanadas(), 0, comanda(), Mockito.mock(Usuario.class));
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cancelar reclasifica de venta a merma sin cambiar la existencia")
    void cancelarReclasificaSinTocarElStock() {
        recetaDeDosPorciones(carneMolida());

        servicio.reclasificarCancelacionComoMerma(empanadas(), 2, comanda(), Mockito.mock(Usuario.class));

        var movs = movimientosGuardados(2);

        var reversa = movs.get(0);
        assertEquals(TipoMovimiento.REVERSA, reversa.getTipo(),
                "REVERSA y no AJUSTE: AJUSTE está reservado para lo que un conteo "
                        + "físico no pudo explicar, y es el número del reporte teórico "
                        + "contra real. Una reversa está explicada, y mezclarlas infla "
                        + "la varianza con ruido — se detectó viendo el reporte real.");
        assertEquals(4, reversa.getCantidad(), "Revierte las 4 porciones que se habían contado como venta.");

        var merma = movs.get(1);
        assertEquals(TipoMovimiento.MERMA, merma.getTipo());
        assertEquals(-4, merma.getCantidad(), "Y las vuelve a sacar, ahora como merma.");

        assertEquals(0, reversa.getCantidad() + merma.getCantidad(),
                "El efecto neto sobre la existencia tiene que ser CERO: la carne ya se "
                        + "cocinó y no regresa al refrigerador. Lo que cambia es la "
                        + "clasificación, para que la comida tirada no se esconda dentro "
                        + "de las ventas y el food cost salga bien mientras se desperdicia.");
    }

    @Test
    @DisplayName("Cancelar un platillo sin receta no mueve nada")
    void cancelarSinRecetaNoMueveNada() {
        when(recetaRepository.porProducto(ID_EMPANADAS)).thenReturn(List.of());
        servicio.reclasificarCancelacionComoMerma(empanadas(), 3, comanda(), Mockito.mock(Usuario.class));
        verify(movimientoRepository, never()).save(any());
    }
}
