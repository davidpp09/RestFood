package restaurante.api.orden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import restaurante.api.infra.errores.ValidacionException;
import restaurante.api.mesa.Estado;
import restaurante.api.mesa.Mesa;
import restaurante.api.mesa.MesaRepository;
import restaurante.api.usuario.Roles;
import restaurante.api.usuario.Usuario;
import restaurante.api.usuario.UsuarioRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdenServiceTest {

    @Mock
    private OrdenRepository ordenRepository;
    @Mock
    private MesaRepository mesaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private OrdenService ordenService;

    // 🧪 TEST 1: La orden ya está pagada
    @Test
    void enviarOrden_OrdenPagada_LanzaExcepcion() {
        var datos = new restaurante.api.ordenDetalle.DatosSincronizarComanda(1L, 1L, List.of(), null);
        var ordenFalsa = Mockito.mock(Orden.class);

        when(ordenFalsa.getEstatus()).thenReturn(Estatus.PAGADA);
        when(ordenRepository.findByIdConBloqueo(1L)).thenReturn(Optional.of(ordenFalsa));

        assertThrows(ValidacionException.class, () -> ordenService.enviarOrden(datos));
    }

    // 🧪 TEST 2: La mesa ya está ocupada
    @Test
    void abrirCuenta_MesaOcupada_LanzaExcepcion() {
        var datos = new DatosAbrirOrden(1L, 1L, Tipo.LOZA, Servicio.COMIDA);
        var usuarioFalso = Mockito.mock(Usuario.class);
        var mesaFalsa = Mockito.mock(Mesa.class);

        when(usuarioFalso.getRol()).thenReturn(Roles.MESERO);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(usuarioFalso);

        when(mesaFalsa.getEstado()).thenReturn(Estado.OCUPADA);
        when(mesaRepository.getReferenceById(1L)).thenReturn(mesaFalsa);

        assertThrows(ValidacionException.class, () -> ordenService.abrirCuenta(datos));
    }

    // 🧪 TEST 3: El usuario no es el dueño de la orden
    @Test
    void enviarOrden_UsuarioDiferente_LanzaExcepcion() {
        // CORRECCIÓN AQUÍ: (id_usuario = 99L, id_orden = 1L)
        var datos = new restaurante.api.ordenDetalle.DatosSincronizarComanda(99L, 1L, List.of(), null);
        var ordenFalsa = Mockito.mock(Orden.class);
        var usuarioDuenio = Mockito.mock(Usuario.class);

        when(ordenFalsa.getEstatus()).thenReturn(Estatus.PREPARANDO);

        // El dueño real es el ID 1
        when(usuarioDuenio.getId_usuarios()).thenReturn(1L);
        when(ordenFalsa.getUsuario()).thenReturn(usuarioDuenio);

        // Mockito ahora sí espera correctamente que busquen la orden 1L
        when(ordenRepository.findByIdConBloqueo(1L)).thenReturn(Optional.of(ordenFalsa));

        assertThrows(ValidacionException.class, () -> ordenService.enviarOrden(datos));
    }
}