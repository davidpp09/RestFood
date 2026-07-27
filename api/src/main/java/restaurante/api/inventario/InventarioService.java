package restaurante.api.inventario;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import restaurante.api.infra.errores.RecursoNoEncontradoException;
import restaurante.api.infra.errores.ValidacionException;
import restaurante.api.usuario.Usuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Toda la escritura del kardex pasa por aquí. Los controllers no crean
 * movimientos a mano: si lo hicieran, cada uno tendría que acordarse de poner
 * el signo correcto y de validar, y tarde o temprano uno se olvidaría.
 */
@Service
public class InventarioService {

    @Autowired
    private InsumoRepository insumoRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    @Autowired
    private ConteoFisicoRepository conteoRepository;

    /** Inyectado para que los tests puedan fijar la fecha en vez de depender del reloj real. */
    @Autowired
    private Clock reloj;

    /**
     * Registra un movimiento. La cantidad llega SIEMPRE en positivo desde el
     * cliente ("llegaron 20", "se echaron a perder 3") y aquí se le pone el
     * signo según el tipo. Es un solo lugar donde equivocarse, en vez de uno
     * por pantalla.
     */
    @Transactional
    public DatosRespuestaMovimiento registrar(DatosRegistroMovimiento datos, Usuario usuario) {
        Insumo insumo = insumoRepository.findById(datos.id_insumo())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el insumo " + datos.id_insumo()));

        if (Boolean.FALSE.equals(insumo.getActivo())) {
            throw new ValidacionException("El insumo '" + insumo.getNombre() + "' está dado de baja");
        }
        if (datos.tipo() == TipoMovimiento.VENTA) {
            // Las ventas las genera el descuento automático de la Fase 2, no una persona.
            throw new ValidacionException("Los movimientos de VENTA los genera el sistema, no se capturan a mano");
        }
        if (datos.tipo() == TipoMovimiento.AJUSTE) {
            // Los ajustes salen de un conteo físico, que es lo que los justifica.
            throw new ValidacionException("Los AJUSTE se generan al registrar un conteo físico");
        }
        if (datos.tipo() == TipoMovimiento.INICIAL
                && movimientoRepository.existsByInsumoAndTipo(insumo, TipoMovimiento.INICIAL)) {
            throw new ValidacionException(
                    "'" + insumo.getNombre() + "' ya tiene su conteo inicial. Para corregirlo, usa un conteo físico");
        }

        int cantidadConSigno = aplicarSigno(datos.tipo(), datos.cantidad());
        var movimiento = new MovimientoInventario(
                insumo, datos.tipo(), cantidadConSigno, datos.motivo(), usuario, ahora());

        return new DatosRespuestaMovimiento(movimientoRepository.save(movimiento));
    }

    /**
     * Registra un conteo físico y genera, por cada insumo que no cuadró, un
     * AJUSTE con la diferencia. Después de esto la suma del kardex es igual a
     * lo que hay en el refrigerador — que es todo el punto del ejercicio.
     *
     * Los insumos que cuadraron no generan movimiento: un AJUSTE de cero sería
     * ruido en el historial.
     */
    @Transactional
    public DatosRespuestaConteo registrarConteo(DatosRegistroConteo datos, Usuario usuario) {
        LocalDateTime ahora = ahora();
        ConteoFisico conteo = conteoRepository.save(new ConteoFisico(usuario, datos.notas(), ahora));

        for (DatosConteoLinea linea : datos.lineas()) {
            Insumo insumo = insumoRepository.findById(linea.id_insumo())
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el insumo " + linea.id_insumo()));

            int teorico = insumoRepository.stockDe(insumo.getId_insumos()).intValue();
            int contado = linea.cantidad_contada();

            conteo.agregar(new ConteoDetalle(conteo, insumo, contado, teorico));

            int diferencia = contado - teorico;
            if (diferencia != 0) {
                movimientoRepository.save(new MovimientoInventario(
                        insumo, TipoMovimiento.AJUSTE, diferencia,
                        "Conteo físico #" + conteo.getId_conteo(), usuario, ahora));
            }
        }
        return new DatosRespuestaConteo(conteoRepository.save(conteo));
    }

    public List<DatosExistencia> existencias() {
        return insumoRepository.existencias();
    }

    public List<DatosRespuestaMovimiento> kardex(Long idInsumo) {
        return movimientoRepository.kardexDe(idInsumo).stream()
                .map(DatosRespuestaMovimiento::new).toList();
    }

    /**
     * MERMA y VENTA salen; INICIAL y COMPRA entran. El signo no se le pide a
     * quien captura porque no es información suya: es una consecuencia del tipo.
     */
    static int aplicarSigno(TipoMovimiento tipo, int cantidadPositiva) {
        int conSigno = switch (tipo) {
            case MERMA, VENTA -> -cantidadPositiva;
            default -> cantidadPositiva;
        };
        if (!tipo.permiteCantidad(conSigno)) {
            throw new ValidacionException("Cantidad inválida para un movimiento de tipo " + tipo);
        }
        return conSigno;
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }
}
