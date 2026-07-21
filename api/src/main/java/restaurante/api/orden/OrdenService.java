package restaurante.api.orden;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import restaurante.api.evento.EventoOrden;
import restaurante.api.evento.EventoOrdenRepository;
import restaurante.api.evento.TipoEvento;
import restaurante.api.admin.DatosCancelacionMesero;
import restaurante.api.admin.DatosCorteDia;
import restaurante.api.admin.DatosProductoCancelado;
import restaurante.api.admin.DatosVentaEmpleado;
import restaurante.api.infra.errores.ValidacionException;
import restaurante.api.infra.errores.RecursoNoEncontradoException;
import restaurante.api.mesa.Estado;
import restaurante.api.mesa.MesaRepository;
import restaurante.api.ordenDetalle.*;
import restaurante.api.producto.ProductoRepository;
import restaurante.api.usuario.Roles;
import restaurante.api.usuario.Usuario;
import restaurante.api.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    @Autowired
    private restaurante.api.infra.impresora.ImpresoraService impresoraService;
    @Autowired
    private EventoOrdenRepository eventoOrdenRepository;


    private boolean esSuperUsuario(Roles rol) {
        return rol.equals(Roles.DEV) || rol.equals(Roles.ADMIN);
    }

    @Transactional // ✅ faltaba
    public DatosApertura abrirCuenta(DatosAbrirOrden datos) {
        // Tomar SIEMPRE el usuario del token — nunca confiar en el id_usuario del body.
        // Lock pesimista sobre el usuario: dos aperturas simultáneas del mismo mesero
        // se serializan, y el COUNT+1 de abajo ya no puede dar comandas duplicadas.
        var autenticado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        var usuario = usuarioRepository.findByIdConBloqueo(autenticado.getId_usuarios())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        boolean conMesa = usuario.getRol().equals(Roles.MESERO) || esSuperUsuario(usuario.getRol());

        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime finDia    = LocalDate.now().atTime(LocalTime.MAX);
        int numeroComanda = ordenRepository.maxNumeroComandaByUsuarioIdAndFechaBetween(
                usuario.getId_usuarios(), inicioDia, finDia).intValue() + 1;

        if (conMesa) {
            if (datos.id_mesa() == null) {
                throw new ValidacionException("Debes indicar el número de mesa.");
            }
            // Lock pesimista: si dos meseros abren la misma mesa a la vez, el segundo
            // espera y encuentra la mesa ya OCUPADA (abrirMesa lanza la validación).
            var mesa = mesaRepository.findByIdConBloqueo(datos.id_mesa())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Mesa no encontrada"));
            mesa.abrirMesa();
            Orden ordenGuardada = ordenRepository.save(new Orden(mesa, usuario, datos.tipo(), datos.servicio(), numeroComanda));

            eventoOrdenRepository.save(new EventoOrden(ordenGuardada, usuario, TipoEvento.MESA_ABIERTA));

            DatosMesaAbierta avisoMesa = new DatosMesaAbierta(
                    datos.id_mesa(),
                    mesa.getEstado(),
                    usuario.getNombre(),
                    ordenGuardada.getId_ordenes(),
                    numeroComanda,
                    ordenGuardada.getFecha_apertura()
            );
            messagingTemplate.convertAndSend("/topic/mesas", avisoMesa);
            System.out.println("✅ [WS /topic/mesas] Mesa abierta: " + avisoMesa);
            return new DatosApertura(ordenGuardada.getId_ordenes(), numeroComanda);
        } else {
            // REPARTIDOR u otros roles sin mesa
            Orden ordenGuardada = ordenRepository.save(new Orden(null, usuario, datos.tipo(), datos.servicio(), numeroComanda));
            System.out.println("✅ Orden sin mesa creada, id: " + ordenGuardada.getId_ordenes() + " comanda #" + numeroComanda);
            return new DatosApertura(ordenGuardada.getId_ordenes(), numeroComanda);
        }
    }

    public Page<DatosListaOrden> listar(Pageable pagina) {
        return ordenRepository.findAllByTipo(pagina, Tipo.LOZA).map(DatosListaOrden::new);
    }

    @Transactional
    public DatosRespuestaOrden enviarOrden(DatosSincronizarComanda datos) {
        var orden = ordenRepository.findByIdConBloqueo(datos.id_orden())
                .orElseThrow(() -> new RecursoNoEncontradoException("Orden no encontrada"));
        // Tomar SIEMPRE el usuario del token — nunca confiar en el id_usuario del body
        var autenticado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        var usuario = usuarioRepository.getReferenceById(autenticado.getId_usuarios());

        if (orden.getEstatus().equals(Estatus.PAGADA)) {
            throw new ValidacionException("La orden ya fue pagada, no puedes modificarla.");
        }

        // ✅ Superusuarios pueden modificar cualquier orden
        if (!esSuperUsuario(usuario.getRol()) && !orden.getUsuario().getId_usuarios().equals(usuario.getId_usuarios())) {
            throw new ValidacionException("Solo el mesero asignado puede modificar esta orden.");
        }

        List<DatosPlatilloTicket> ticketCocina = new ArrayList<>();
        
        // 1. Obtener los platillos actuales de la base de datos
        var platosEnDb = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes());
        
        // 2. Obtener los IDs que vienen en el payload (los que aún existen en el carrito)
        List<Long> idsRecibidos = datos.platillos().stream()
                .map(DatosPlatilloLote::id_detalle)
                .filter(Objects::nonNull)
                .toList();

        // 3. Eliminar platillos que ya no están en el carrito
        for (OrdenDetalle platilloDb : platosEnDb) {
            if (!idsRecibidos.contains(platilloDb.getId_detalle())) {
                String impresora = platilloDb.getProducto().getCategoria().getImpresora();
                eventoOrdenRepository.save(new EventoOrden(orden, usuario, TipoEvento.PLATILLO_CANCELADO,
                        platilloDb.getProducto().getNombre(),
                        platilloDb.getCantidad(), 0,
                        platilloDb.getPrecio_unitario(),
                        platilloDb.getComentarios(), null));
                ordenDetalleRepository.delete(platilloDb);
                ticketCocina.add(new DatosPlatilloTicket("🔴 CANCELADO", platilloDb.getProducto().getNombre(), 0, "Cancelado por el mesero", impresora));
            }
        }

        // 4. Procesar platillos del frontend (nuevos o modificados)
        for (DatosPlatilloLote platillo : datos.platillos()) {
            if (platillo.id_detalle() == null) {
                var producto = productoRepository.findById(platillo.id_producto())
                        .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado: " + platillo.id_producto()));
                String impresora = producto.getCategoria().getImpresora();
                OrdenDetalle nuevoDetalle = ordenDetalleRepository.save(new OrdenDetalle(platillo, producto, orden));
                eventoOrdenRepository.save(new EventoOrden(orden, usuario, TipoEvento.PLATILLO_NUEVO,
                        producto.getNombre(),
                        null, platillo.cantidad(),
                        nuevoDetalle.getPrecio_unitario(),
                        null, platillo.comentarios()));
                ticketCocina.add(new DatosPlatilloTicket("🟢 NUEVO", producto.getNombre(), platillo.cantidad(), platillo.comentarios(), impresora));
            } else {
                var modificado = ordenDetalleRepository.findById(platillo.id_detalle())
                        .orElseThrow(() -> new RecursoNoEncontradoException("Detalle no encontrado: " + platillo.id_detalle()));
                var producto   = productoRepository.findById(platillo.id_producto())
                        .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado: " + platillo.id_producto()));
                String impresora = producto.getCategoria().getImpresora();

                boolean cambioCantidad    = !modificado.getCantidad().equals(platillo.cantidad());
                boolean cambioComentarios = (modificado.getComentarios() == null && platillo.comentarios() != null && !platillo.comentarios().isEmpty())
                        || (modificado.getComentarios() != null && !modificado.getComentarios().equals(platillo.comentarios()));

                if (cambioCantidad || cambioComentarios) {
                    eventoOrdenRepository.save(new EventoOrden(orden, usuario, TipoEvento.PLATILLO_MODIFICADO,
                            producto.getNombre(),
                            modificado.getCantidad(), platillo.cantidad(),
                            modificado.getPrecio_unitario(),
                            modificado.getComentarios(), platillo.comentarios()));
                    modificado.actualizarPlatillo(platillo);
                    ticketCocina.add(new DatosPlatilloTicket("🟡 MODIFICADO", producto.getNombre(), platillo.cantidad(), platillo.comentarios(), impresora));
                }
            }
        }

        // Si la orden ya estaba SERVIDA y el mesero le agregó/modificó platillos,
        // regresarla a PREPARANDO para que reaparezca en el panel de cocina.
        if (!ticketCocina.isEmpty()) {
            orden.reabrir();
        }

        ordenDetalleRepository.flush();
        var platosActualizados = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes());
        orden.recalcularTotal(platosActualizados);

        List<DatosDetalleRespuesta> platillosMapeados = platosActualizados.stream()
                .map(DatosDetalleRespuesta::new)
                .toList();

        Long idMesa = orden.getMesa() != null ? orden.getMesa().getId_mesas() : null;
        String numeroMesa = orden.getMesa() != null ? orden.getMesa().getNumero() : null;
        DatosRespuestaOrden respuesta  = new DatosRespuestaOrden(orden.getId_ordenes(), orden.getNumero_comanda(), orden.getTotal(), platillosMapeados, orden.getTipo().toString(), idMesa, orden.getServicio().toString());
        DatosTicketCocina  ticketFinal = new DatosTicketCocina(idMesa, numeroMesa, orden.getId_ordenes(), orden.getNumero_comanda(), orden.getUsuario().getNombre(), orden.getTipo(), ticketCocina);

        messagingTemplate.convertAndSend("/topic/cocina", ticketFinal);

        // Notificar al panel de admin
        Map<String, Object> updateAdmin = new HashMap<>();
        if (idMesa != null) {
            updateAdmin.put("id_mesa", idMesa);
            updateAdmin.put("estado", "OCUPADA");
        }
        updateAdmin.put("id_orden", orden.getId_ordenes());
        updateAdmin.put("platillos", platillosMapeados);
        messagingTemplate.convertAndSend("/topic/mesas", updateAdmin);
        
        System.out.println("✅ [WS /topic/cocina] Ticket enviado orden #" + orden.getId_ordenes() + " con " + ticketCocina.size() + " platillos");

        // Enviar impresión física
        impresoraService.imprimirComandaCocina(ticketFinal);

        // PARA LLEVAR con tiempos marcados → talón de tiempos en la impresora
        // de la zona de repartidores (solo si esta sincronización envió algo nuevo)
        if (Tipo.LLEVAR.equals(orden.getTipo()) && datos.tiempos() != null
                && datos.tiempos().tieneAlguno() && !ticketCocina.isEmpty()) {
            impresoraService.imprimirTiemposLlevar(
                    orden.getNumero_comanda(), orden.getUsuario().getNombre(), datos.tiempos());
        }

        return respuesta;
    }

    @Transactional
    public DatosRespuestaCuenta darCuenta(Long id) {
        // Lock pesimista: dos "Cerrar y Cobrar" simultáneos sobre la misma orden se
        // serializan; el segundo ve la orden ya PAGADA (finalizar lanza la validación)
        // en vez de cobrar e imprimir dos veces.
        var orden = ordenRepository.findByIdConBloqueo(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Orden no encontrada"));

        // VALIDACIÓN: Solo el mesero que abrió la orden o un ADMIN/DEV pueden cerrarla
        var usuarioAutenticado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!esSuperUsuario(usuarioAutenticado.getRol()) && !orden.getUsuario().getId_usuarios().equals(usuarioAutenticado.getId_usuarios())) {
            throw new ValidacionException("No tienes permiso para cerrar esta orden porque no la abriste tú.");
        }

        var platillos = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes());
        if (platillos.isEmpty()) {
            throw new ValidacionException("No se puede cerrar una orden sin platillos agregados.");
        }

        List<Orden> otrasOrdenes = new ArrayList<>();
        if (orden.getMesa() != null) {
            // PREPARANDO y SERVIDO: una orden servida pero sin pagar sigue ocupando la mesa
            var ordenesActivas = ordenRepository.findByMesaAndEstatusIn(
                    orden.getMesa(), List.of(Estatus.PREPARANDO, Estatus.SERVIDO));
            otrasOrdenes = ordenesActivas.stream().filter(o -> !o.getId_ordenes().equals(id)).toList();
        }

        // --- Todas las escrituras DB primero ---
        orden.finalizar(otrasOrdenes);
        // Liberar la mesa SOLO si no queda ninguna otra orden viva en ella; antes se
        // liberaba siempre y una mesa podía quedar LIBRE con una orden vieja aún abierta.
        if (orden.getMesa() != null && otrasOrdenes.isEmpty()) orden.getMesa().liberar();
        eventoOrdenRepository.save(new EventoOrden(orden, usuarioAutenticado, TipoEvento.MESA_CERRADA));

        // --- Construcción del ticket ---
        List<DatosDetalleRespuesta> platillosMapeados = platillos.stream().map(DatosDetalleRespuesta::new).toList();

        DatosRespuestaCuenta ticket = new DatosRespuestaCuenta(
                orden.getId_ordenes(),
                orden.getNumero_comanda(),
                orden.getMesa() != null ? orden.getMesa().getNumero() : null,
                orden.getTipo().toString(),
                orden.getFecha_apertura(),
                orden.getFechaCierre(),
                platillosMapeados,
                orden.getTotal(),
                orden.getEstatus().toString(),
                orden.getUsuario().getNombre(),
                orden.getServicio() != null ? orden.getServicio().toString() : null
        );

        // --- WebSocket: todos después de confirmar DB ---
        if (orden.getMesa() != null) {
            DatosMesaAbierta avisoMesa = new DatosMesaAbierta(orden.getMesa().getId_mesas(), orden.getMesa().getEstado(), "", null, null, null);
            messagingTemplate.convertAndSend("/topic/mesas", avisoMesa);
            System.out.println("✅ [WS /topic/mesas] Mesa liberada: " + orden.getMesa().getId_mesas());
        }
        messagingTemplate.convertAndSend("/topic/tickets", ticket);
        System.out.println("🖨️ [WS /topic/tickets] Ticket enviado para impresión: Orden #" + orden.getId_ordenes());
        messagingTemplate.convertAndSend("/topic/cocina", Map.of("accion", "CERRADA", "id_orden", orden.getId_ordenes()));
        System.out.println("🍳 [WS /topic/cocina] Orden cerrada: #" + orden.getId_ordenes());

        // --- Efecto secundario (impresora, fuera del camino crítico) ---
        impresoraService.imprimirTicketCliente(ticket);

        return ticket;
    }

    @Transactional
    public void cancelarOrden(Long id) {
        // Mismo lock que darCuenta: cancelaciones dobles simultáneas se serializan
        var orden = ordenRepository.findByIdConBloqueo(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Orden no encontrada"));

        var usuarioAutenticado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!esSuperUsuario(usuarioAutenticado.getRol()) && !orden.getUsuario().getId_usuarios().equals(usuarioAutenticado.getId_usuarios())) {
            throw new ValidacionException("No tienes permiso para cancelar esta orden porque no la abriste tú.");
        }

        var platillos = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes());
        if (!platillos.isEmpty()) {
            throw new ValidacionException("No se puede cancelar una orden que ya tiene platillos agregados. Usa Cerrar y Cobrar.");
        }

        orden.cancelar();
        if (orden.getMesa() != null) orden.getMesa().liberar();
        eventoOrdenRepository.save(new EventoOrden(orden, usuarioAutenticado, TipoEvento.MESA_CANCELADA));

        if (orden.getMesa() != null) {
            DatosMesaAbierta avisoMesa = new DatosMesaAbierta(orden.getMesa().getId_mesas(), orden.getMesa().getEstado(), "", null, null, null);
            messagingTemplate.convertAndSend("/topic/mesas", avisoMesa);
            System.out.println("✅ [WS /topic/mesas] Mesa cancelada y liberada: " + orden.getMesa().getId_mesas());
        }
    }

    @Transactional(readOnly = true)
    public void reenviarACocina(Long idOrden) {
        var orden = ordenRepository.findById(idOrden)
                .orElseThrow(() -> new RecursoNoEncontradoException("Orden no encontrada"));

        if (orden.getEstatus().equals(Estatus.PAGADA)) {
            throw new ValidacionException("No se puede reenviar una orden ya pagada.");
        }

        var platillos = ordenDetalleRepository.findAllByOrdenId(idOrden);

        List<DatosPlatilloTicket> ticketCocina = platillos.stream()
                .map(p -> new DatosPlatilloTicket(
                        "🔄 REENVIO",
                        p.getProducto().getNombre(),
                        p.getCantidad(),
                        p.getComentarios(),
                        p.getProducto().getCategoria().getImpresora()
                ))
                .toList();

        Long idMesa = orden.getMesa() != null ? orden.getMesa().getId_mesas() : null;
        String numeroMesa = orden.getMesa() != null ? orden.getMesa().getNumero() : null;
        DatosTicketCocina ticketFinal = new DatosTicketCocina(
                idMesa,
                numeroMesa,
                orden.getId_ordenes(),
                orden.getNumero_comanda(),
                orden.getUsuario().getNombre(),
                orden.getTipo(),
                ticketCocina
        );

        messagingTemplate.convertAndSend("/topic/cocina", ticketFinal);
        System.out.println("🔄 [WS /topic/cocina] Reenvío a cocina Orden #" + idOrden);

        impresoraService.imprimirComandaCocina(ticketFinal);
    }

    @Transactional(readOnly = true)
    public DatosRespuestaCuenta reimprimirTicket(Long idOrden) {
        var orden = ordenRepository.findById(idOrden)
                .orElseThrow(() -> new RecursoNoEncontradoException("Orden no encontrada"));

        var platillos = ordenDetalleRepository.findAllByOrdenId(idOrden);
        List<DatosDetalleRespuesta> platillosMapeados = platillos.stream()
                .map(DatosDetalleRespuesta::new)
                .toList();

        DatosRespuestaCuenta ticket = new DatosRespuestaCuenta(
                orden.getId_ordenes(),
                orden.getNumero_comanda(),
                orden.getMesa() != null ? orden.getMesa().getNumero() : null,
                orden.getTipo().toString(),
                orden.getFecha_apertura(),
                orden.getFechaCierre(),
                platillosMapeados,
                orden.getTotal(),
                orden.getEstatus().toString(),
                orden.getUsuario().getNombre(),
                orden.getServicio() != null ? orden.getServicio().toString() : null
        );

        // Re-enviar por WebSocket al frontend que esté escuchando /topic/tickets
        messagingTemplate.convertAndSend("/topic/tickets", ticket);
        System.out.println("🖨️ [WS /topic/tickets] Reimpresión ticket Orden #" + idOrden);

        // Imprimir físicamente
        impresoraService.imprimirTicketCliente(ticket);

        return ticket;
    }

    @Transactional(readOnly = true)
    public DatosRespuestaOrden obtenerOrdenActiva(Long id_mesa) {
        var orden = ordenRepository.findActivaByMesa(id_mesa)
                .orElseThrow(() -> new RecursoNoEncontradoException("No hay orden activa para esta mesa"));

        var platillos = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes());
        List<DatosDetalleRespuesta> platillosMapeados = platillos.stream()
                .map(DatosDetalleRespuesta::new)
                .toList();

        return new DatosRespuestaOrden(orden.getId_ordenes(), orden.getNumero_comanda(), orden.getTotal(), platillosMapeados, orden.getTipo().toString(), orden.getMesa() != null ? orden.getMesa().getId_mesas() : null, orden.getServicio().toString());
    }

    @Transactional(readOnly = true)
    public List<DatosEntregaHoy> obtenerEntregasHoy() {
        LocalDateTime inicio = LocalDate.now().atStartOfDay();
        LocalDateTime fin    = LocalDate.now().atTime(LocalTime.MAX);

        // Un REPARTIDOR solo ve sus propias entregas; ADMIN/DEV ven las de todos
        // (panel de admin). Sin esto, dos repartidores ven sus órdenes mezcladas.
        var autenticado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<Orden> entregas = esSuperUsuario(autenticado.getRol())
                ? ordenRepository.findEntregasDelDia(Tipo.LLEVAR, inicio, fin)
                : ordenRepository.findEntregasDelDiaByUsuario(Tipo.LLEVAR, autenticado.getId_usuarios(), inicio, fin);

        return entregas.stream()
                .map(orden -> {
                    var platillos = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes())
                            .stream().map(DatosDetalleRespuesta::new).toList();
                    return new DatosEntregaHoy(
                            orden.getId_ordenes(),
                            orden.getNumero_comanda(),
                            orden.getFecha_apertura(),
                            orden.getEstatus(),
                            orden.getTotal(),
                            orden.getUsuario().getNombre(),
                            platillos
                    );
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<DatosRespuestaOrden> listarOrdenesCocina() {
        return ordenRepository.findByEstatus(Estatus.PREPARANDO).stream().map(orden -> {
            var platillos = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes());
            List<DatosDetalleRespuesta> platillosMapeados = platillos.stream()
                    .map(DatosDetalleRespuesta::new)
                    .toList();
            return new DatosRespuestaOrden(orden.getId_ordenes(), orden.getNumero_comanda(), orden.getTotal(), platillosMapeados, orden.getTipo().toString(), orden.getMesa() != null ? orden.getMesa().getId_mesas() : null, orden.getServicio().toString());
        }).toList();
    }

    @Transactional
    public void marcarOrdenServida(Long idOrden) {
        var orden = ordenRepository.findById(idOrden)
                .orElseThrow(() -> new RecursoNoEncontradoException("Orden no encontrada"));
        orden.marcarComoServido();
        // Opcional: avisar por websocket enviando un objeto JSON válido
        messagingTemplate.convertAndSend("/topic/cocina", Map.of("mensaje", "Orden " + idOrden + " está lista", "id_orden", idOrden));
    }

    @Transactional
    public DatosCorteDia master(LocalDate fecha) {
        var inicio = fecha.atStartOfDay();
        var fin    = fecha.atTime(LocalTime.MAX);
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
                .map(entry -> {
                    // El rol se toma de la primera orden del grupo (todas son del mismo usuario)
                    String rol = entry.getValue().get(0).getUsuario().getRol().toString();
                    BigDecimal total = entry.getValue().stream()
                            .map(Orden::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new DatosVentaEmpleado(entry.getKey(), rol, entry.getValue().size(), total);
                })
                .toList();
    }

    public List<DatosCancelacionMesero> cancelacionesPorMesero(LocalDate desde, LocalDate hasta) {
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin    = hasta.atTime(LocalTime.MAX);

        var eventos = eventoOrdenRepository.findByTipoEventoAndTimestampBetween(
                TipoEvento.PLATILLO_CANCELADO, inicio, fin);

        return eventos.stream()
                .collect(Collectors.groupingBy(EventoOrden::getNombreMesero))
                .entrySet().stream()
                .map(entry -> {
                    List<DatosProductoCancelado> productos = entry.getValue().stream()
                            .collect(Collectors.groupingBy(
                                    e -> e.getNombreProducto() != null ? e.getNombreProducto() : "Desconocido",
                                    Collectors.counting()))
                            .entrySet().stream()
                            .map(p -> new DatosProductoCancelado(p.getKey(), p.getValue()))
                            .sorted(Comparator.comparingLong(DatosProductoCancelado::veces).reversed())
                            .toList();

                    return new DatosCancelacionMesero(
                            entry.getKey(),
                            (long) entry.getValue().size(),
                            productos
                    );
                })
                .sorted(Comparator.comparingLong(DatosCancelacionMesero::totalCancelaciones).reversed())
                .toList();
    }
}