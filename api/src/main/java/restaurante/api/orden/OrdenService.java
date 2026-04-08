package restaurante.api.orden;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import restaurante.api.admin.DatosCorteDia;
import restaurante.api.admin.DatosVentaEmpleado;
import restaurante.api.infra.errores.ValidacionException;
import restaurante.api.mesa.Estado;
import restaurante.api.mesa.MesaRepository;
import restaurante.api.ordenDetalle.*;
import restaurante.api.producto.ProductoRepository;
import restaurante.api.usuario.Roles;
import restaurante.api.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrdenService {

    @Autowired
    OrdenRepository ordenRepository;
    @Autowired
    MesaRepository mesaRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    ProductoRepository productoRepository;
    @Autowired
    OrdenDetalleRepository ordenDetalleRepository;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;


    private boolean esSuperUsuario(Roles rol) {
        return rol.equals(Roles.DEV) || rol.equals(Roles.ADMIN);
    }

    @Transactional // ✅ faltaba
    public Long abrirCuenta(DatosAbrirOrden datos) {
        var usuario = usuarioRepository.getReferenceById(datos.id_usuario());
        boolean conMesa = usuario.getRol().equals(Roles.MESERO) || esSuperUsuario(usuario.getRol());

        if (conMesa) {
            if (datos.id_mesa() == null) {
                throw new ValidacionException("Debes indicar el número de mesa.");
            }
            var mesa = mesaRepository.getReferenceById(datos.id_mesa());
            if (mesa.getEstado() == Estado.OCUPADA) {
                throw new ValidacionException("La mesa ya está en uso, no se puede abrir otra cuenta.");
            }
            mesa.abrirMesa();
            Orden ordenGuardada = ordenRepository.save(new Orden(mesa, usuario, datos.tipo(), datos.servicio()));

            DatosMesaAbierta avisoMesa = new DatosMesaAbierta(
                    datos.id_mesa(),
                    mesa.getEstado(),
                    usuario.getNombre(),
                    ordenGuardada.getId_ordenes()
            );
            messagingTemplate.convertAndSend("/topic/mesas", avisoMesa);
            System.out.println("✅ [WS /topic/mesas] Mesa abierta: " + avisoMesa);
            return ordenGuardada.getId_ordenes();
        } else {
            // REPARTIDOR u otros roles sin mesa
            Orden ordenGuardada = ordenRepository.save(new Orden(null, usuario, datos.tipo(), datos.servicio()));
            System.out.println("✅ Orden sin mesa creada, id: " + ordenGuardada.getId_ordenes());
            return ordenGuardada.getId_ordenes();
        }
    }

    public Page<DatosListaOrden> listar(Pageable pagina) {
        return ordenRepository.findAllByTipo(pagina, Tipo.LOZA).map(DatosListaOrden::new);
    }

    @Transactional
    public DatosRespuestaOrden enviarOrden(DatosSincronizarComanda datos) {
        var orden = ordenRepository.findByIdConBloqueo(datos.id_orden()).orElseThrow();
        var usuario = usuarioRepository.getReferenceById(datos.id_usuario());

        if (orden.getEstatus().equals(Estatus.PAGADA)) {
            throw new ValidacionException("La orden ya fue pagada, no puedes modificarla.");
        }

        // ✅ Superusuarios pueden modificar cualquier orden
        if (!esSuperUsuario(usuario.getRol()) && !orden.getUsuario().getId_usuarios().equals(datos.id_usuario())) {
            throw new ValidacionException("Solo el mesero asignado puede modificar esta orden.");
        }

        List<DatosPlatilloTicket> ticketCocina = new ArrayList<>();
        for (DatosPlatilloLote platillo : datos.platillos()) {
            if (platillo.id_detalle() == null) {
                var producto = productoRepository.getReferenceById(platillo.id_producto());
                OrdenDetalle detalle = new OrdenDetalle(platillo, producto, orden);
                ordenDetalleRepository.save(detalle);
                ticketCocina.add(new DatosPlatilloTicket("🟢 NUEVO", producto.getNombre(), platillo.cantidad(), platillo.comentarios()));
            } else {
                if (platillo.cantidad() == 0) {
                    var producto = productoRepository.getReferenceById(platillo.id_producto());
                    ordenDetalleRepository.deleteById(platillo.id_detalle());
                    ticketCocina.add(new DatosPlatilloTicket("🔴 CANCELADO", producto.getNombre(), 0, "No preparar"));
                } else {
                    var modificado = ordenDetalleRepository.getReferenceById(platillo.id_detalle());
                    var producto = productoRepository.getReferenceById(platillo.id_producto());
                    modificado.actualizarPlatillo(platillo);
                    ticketCocina.add(new DatosPlatilloTicket("🟡 MODIFICADO", producto.getNombre(), platillo.cantidad(), platillo.comentarios()));
                }
            }
        }

        var platosActualizados = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes());
        orden.recalcularTotal(platosActualizados);

        List<DatosDetalleRespuesta> platillosMapeados = platosActualizados.stream()
                .map(DatosDetalleRespuesta::new)
                .toList();

        DatosRespuestaOrden respuesta = new DatosRespuestaOrden(orden.getId_ordenes(), orden.getTotal(), platillosMapeados);
        DatosTicketCocina ticketFinal = new DatosTicketCocina(orden.getMesa().getId_mesas(), orden.getId_ordenes(), orden.getUsuario().getNombre(), orden.getTipo(), ticketCocina);

        messagingTemplate.convertAndSend("/topic/mesas", ticketFinal);
        System.out.println("✅ [WS /topic/mesas] Ticket enviado orden #" + orden.getId_ordenes() + " con " + ticketCocina.size() + " platillos");

        return respuesta;
    }

    @Transactional
    public void darCuenta(Long id) {
        var orden = ordenRepository.findById(id).orElseThrow();
        orden.finalizar();

        if (orden.getMesa() != null) {
            orden.getMesa().liberar();

            // ✅ Avisamos al frontend que la mesa quedó libre
            DatosMesaAbierta avisoMesa = new DatosMesaAbierta(
                    orden.getMesa().getId_mesas(),
                    orden.getMesa().getEstado(),
                    "",
                    null
            );
            messagingTemplate.convertAndSend("/topic/mesas", avisoMesa);
            System.out.println("✅ [WS /topic/mesas] Mesa liberada: " + orden.getMesa().getId_mesas());
        }
    }

    @Transactional
    public DatosCorteDia master() {
        var inicio = LocalDate.now().atStartOfDay();
        var fin = LocalDate.now().atTime(LocalTime.MAX);
        List<Orden> ordenes = ordenRepository.findByFechaCierreBetweenAndEstatus(inicio, fin, Estatus.PAGADA);
        var ventasAgrupadas = ordenes.stream()
                .collect(Collectors.groupingBy(o -> o.getUsuario().getNombre()));
        return new DatosCorteDia(
                ventaEmpleados(ventasAgrupadas),
                totalDesayuno(ordenes),
                totalComida(ordenes),
                platillosLoza(inicio, fin),
                platillosParaLlevar(inicio, fin),
                totalGeneral(ordenes)
        );
    }

    // --- Métodos de cálculo ---
    public BigDecimal totalGeneral(List<Orden> ordenes) {
        return ordenes.stream().map(Orden::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalDesayuno(List<Orden> ordenes) {
        return ordenes.stream().filter(o -> Servicio.DESAYUNO.equals(o.getServicio())).map(Orden::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalComida(List<Orden> ordenes) {
        return ordenes.stream().filter(o -> Servicio.COMIDA.equals(o.getServicio())).map(Orden::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long platillosParaLlevar(LocalDateTime inicio, LocalDateTime fin) {
        return ordenDetalleRepository.contarPlatillosPorTipo(inicio, fin, Tipo.LLEVAR);
    }

    public Long platillosLoza(LocalDateTime inicio, LocalDateTime fin) {
        return ordenDetalleRepository.contarPlatillosPorTipo(inicio, fin, Tipo.LOZA);
    }

    public List<DatosVentaEmpleado> ventaEmpleados(Map<String, List<Orden>> ventasPorNombre) {
        return ventasPorNombre.entrySet().stream()
                .map(entry -> new DatosVentaEmpleado(entry.getKey(), entry.getValue().size(),
                        entry.getValue().stream().map(Orden::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
    }
}