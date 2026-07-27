package restaurante.api.orden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import restaurante.api.mesa.MesaRepository;
import restaurante.api.ordenDetalle.OrdenDetalleRepository;
import restaurante.api.producto.ProductoRepository;
import restaurante.api.usuario.Roles;
import restaurante.api.usuario.Usuario;
import restaurante.api.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * El corte del día es el número con el que se cuadra la caja. Si suma mal, el
 * error no se ve: cuadra contra sí mismo y nadie tiene con qué compararlo.
 *
 * Los métodos de suma son públicos y puros, así que se prueban directos, sin
 * base de datos. Lo que se fija:
 *   - que el total general sea la suma de todo lo PAGADO,
 *   - que desayuno y comida partan ese total sin perder ni duplicar un peso,
 *   - que la venta por empleado sume lo de cada quien y no lo de otro.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorteDiaTest {

    @Mock private OrdenRepository ordenRepository;
    @Mock private MesaRepository mesaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private OrdenDetalleRepository ordenDetalleRepository;

    @InjectMocks
    private OrdenService servicio;

    private Orden orden(String importe, Servicio servicio) {
        Orden o = Mockito.mock(Orden.class);
        when(o.getTotal()).thenReturn(new BigDecimal(importe));
        when(o.getServicio()).thenReturn(servicio);
        return o;
    }

    private Orden ordenDe(String importe, Servicio serv, String empleado, Roles rol) {
        Orden o = orden(importe, serv);
        Usuario u = Mockito.mock(Usuario.class);
        when(u.getNombre()).thenReturn(empleado);
        when(u.getRol()).thenReturn(rol);
        when(o.getUsuario()).thenReturn(u);
        return o;
    }

    @Test
    @DisplayName("El total general suma todas las órdenes pagadas")
    void totalGeneralSumaTodo() {
        var ordenes = List.of(
                orden("120.50", Servicio.COMIDA),
                orden("80", Servicio.DESAYUNO),
                orden("249.50", Servicio.COMIDA));

        assertEquals(0, new BigDecimal("450.00").compareTo(servicio.totalGeneral(ordenes)),
                "120.50 + 80 + 249.50 = 450.00");
    }

    @Test
    @DisplayName("Sin ventas el corte da cero, no explota")
    void sinVentasDaCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(servicio.totalGeneral(List.of())),
                "Un día sin órdenes pagadas es un corte en cero, no un error.");
    }

    @Test
    @DisplayName("Desayuno y comida separan el total sin perder un peso")
    void desayunoMasComidaEsElTotal() {
        var ordenes = List.of(
                orden("100", Servicio.DESAYUNO),
                orden("250", Servicio.COMIDA),
                orden("50.25", Servicio.DESAYUNO),
                orden("99.75", Servicio.COMIDA));

        var desayuno = servicio.totalDesayuno(ordenes);
        var comida   = servicio.totalComida(ordenes);

        assertEquals(0, new BigDecimal("150.25").compareTo(desayuno));
        assertEquals(0, new BigDecimal("349.75").compareTo(comida));
        assertEquals(0, servicio.totalGeneral(ordenes).compareTo(desayuno.add(comida)),
                "Si esta igualdad se rompe, hay dinero que aparece en el total y "
                        + "no en ningún turno, o al revés. Es la comprobación que "
                        + "delata una orden mal clasificada.");
    }

    @Test
    @DisplayName("La venta por empleado suma lo de cada quien")
    void ventaPorEmpleado() {
        var porNombre = Map.of(
                "ANGELES", List.of(
                        ordenDe("100", Servicio.COMIDA, "ANGELES", Roles.MESERO),
                        ordenDe("55.50", Servicio.COMIDA, "ANGELES", Roles.MESERO)),
                "LUPE", List.of(
                        ordenDe("200", Servicio.DESAYUNO, "LUPE", Roles.MESERO)));

        var ventas = servicio.ventaEmpleados(porNombre);
        assertEquals(2, ventas.size());

        var angeles = ventas.stream().filter(v -> v.nombre().equals("ANGELES")).findFirst().orElseThrow();
        assertEquals(2, angeles.cantidad(), "ANGELES cerró 2 cuentas.");
        assertEquals(0, new BigDecimal("155.50").compareTo(angeles.total()),
                "100 + 55.50. Si esto suma mal, se le paga comisión o se le reclama "
                        + "faltante a la persona equivocada.");

        var lupe = ventas.stream().filter(v -> v.nombre().equals("LUPE")).findFirst().orElseThrow();
        assertEquals(1, lupe.cantidad());
        assertEquals(0, new BigDecimal("200").compareTo(lupe.total()));
    }

    @Test
    @DisplayName("La suma por empleado cuadra con el total general")
    void loDeCadaEmpleadoCuadraConElTotal() {
        var a1 = ordenDe("100", Servicio.COMIDA, "ANGELES", Roles.MESERO);
        var a2 = ordenDe("55.50", Servicio.COMIDA, "ANGELES", Roles.MESERO);
        var l1 = ordenDe("200", Servicio.DESAYUNO, "LUPE", Roles.MESERO);

        var porNombre = Map.of("ANGELES", List.of(a1, a2), "LUPE", List.of(l1));

        var sumaEmpleados = servicio.ventaEmpleados(porNombre).stream()
                .map(v -> v.total())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, servicio.totalGeneral(List.of(a1, a2, l1)).compareTo(sumaEmpleados),
                "Lo que reporta el desglose por mesero tiene que ser lo mismo que el "
                        + "total del día. Si no cuadra, el corte se contradice a sí mismo.");
    }
}
