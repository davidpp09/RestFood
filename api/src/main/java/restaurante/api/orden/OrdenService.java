package restaurante.api.orden;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import restaurante.api.admin.DatosCorteDia;
import restaurante.api.admin.DatosVentaEmpleado;
import restaurante.api.mesa.Estado;
import restaurante.api.mesa.MesaRepository;
import restaurante.api.ordenDetalle.*;
import restaurante.api.producto.ProductoRepository;
import restaurante.api.usuario.Usuario;
import restaurante.api.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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


    @Transactional
    public Long abrirCuenta(DatosAbrirOrden datos){
        var usuario = usuarioRepository.getReferenceById(datos.id_usuario());
        var mesa = mesaRepository.getReferenceById(datos.id_mesa());
        if (mesa.getEstado() == Estado.OCUPADA) {
            throw new RuntimeException("La mesa ya está en uso, no se puede abrir otra cuenta.");
        }

        mesa.abrirMesa();
        Orden ordenGuardada = ordenRepository.save(new Orden(mesa, usuario, datos.tipo(), datos.servicio()));
        return ordenGuardada.getId_ordenes();
    }

    public Page<DatosListaOrden> listar(Pageable pagina){
        return ordenRepository.findAllByTipo(pagina,Tipo.LOZA).map(DatosListaOrden::new);
    }

    @Transactional
    public DatosRespuestaOrden enviarOrden(DatosSincronizarComanda datos){
        var orden = ordenRepository.findByIdConBloqueo(datos.id_orden()).orElseThrow();
        if (!orden.getUsuario().getId_usuarios().equals(datos.id_usuario())) {
            throw new RuntimeException("Solo un mesero puede tener la orden de la mesa");
        }
        for (DatosPlatilloLote platillo : datos.platillos()) {
            if (platillo.id_detalle() == null){
                var producto = productoRepository.getReferenceById(platillo.id_producto());
                OrdenDetalle detalle = new OrdenDetalle(platillo, producto, orden);
                ordenDetalleRepository.save(detalle);
            } else {
                if(platillo.cantidad()==0){
                    ordenDetalleRepository.deleteById(platillo.id_detalle());
                }else{
                var modificado = ordenDetalleRepository.getReferenceById(platillo.id_detalle());
                modificado.actualizarPlatillo(platillo);
                }
            }
        }
        var platosActualizados = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes());
        orden.recalcularTotal(platosActualizados);


        List<DatosDetalleRespuesta> platillosMapeados = platosActualizados.stream()
                .map(DatosDetalleRespuesta::new)
                .toList();

        return new DatosRespuestaOrden(orden.getId_ordenes(), orden.getTotal(), platillosMapeados);
    }

    @Transactional
    public void darCuenta(Long id){
        var orden = ordenRepository.findById(id).orElseThrow();

        orden.finalizar();
        orden.getMesa().liberar();

    }




    @Transactional
    public BigDecimal totalGeneral(List<Orden> ordenes){
        return ordenes.stream().map(Orden::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    @Transactional
    public BigDecimal totalDesayuno(List<Orden> ordenes) {
        return ordenes.stream()
                .filter(o -> Servicio.DESAYUNO.equals(o.getServicio()))
                .map(Orden::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    @Transactional
    public BigDecimal totalComida(List<Orden> ordenes) {
        return ordenes.stream()
                .filter(o -> Servicio.COMIDA.equals(o.getServicio()))
                .map(Orden::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    @Transactional
    public Long pedidosParaLlevar(List<Orden> ordenes){
        return ordenes.stream().filter(o -> o.getTipo().equals(Tipo.LLEVAR)).count();
    }
    @Transactional
    public Long pedidosLoza(List<Orden> ordenes){
        return ordenes.stream().filter(o -> o.getTipo().equals(Tipo.LOZA)).count();
    }
    @Transactional
    public List<DatosVentaEmpleado> ventaEmpleados(Map<String, List<Orden>> ventasPorNombre) {
        return ventasPorNombre.entrySet().stream()
                .map(entry -> new DatosVentaEmpleado(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream()
                                .map(Orden::getTotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                )).toList();
    }

    @Transactional
    public DatosCorteDia corteDia(List<DatosVentaEmpleado> datosVentaEmpleados, BigDecimal totalDesayuno, BigDecimal totalComida, Long totalMesas, Long totalParaLlevar, BigDecimal totalGeneral){
        return new DatosCorteDia(
                datosVentaEmpleados,
                totalDesayuno,
                totalComida,
                totalMesas,
                totalParaLlevar,
                totalGeneral
        );
    }

    @Transactional
    public DatosCorteDia master() {
        var inicio = LocalDate.now().atStartOfDay();
        var fin = LocalDate.now().atTime(LocalTime.MAX);
        List<Orden> ordenes = ordenRepository.findByFechaCierreBetweenAndEstatus(inicio, fin,Estatus.PAGADA);
        var ventasAgrupadas = ordenes.stream()
                .collect(Collectors.groupingBy(o -> o.getUsuario().getNombre()));
        return new DatosCorteDia(
                ventaEmpleados(ventasAgrupadas), // List<DatosVentaEmpleado>
                totalDesayuno(ordenes),          // BigDecimal
                totalComida(ordenes),            // BigDecimal
                pedidosLoza(ordenes),            // Long (totalMesas)
                pedidosParaLlevar(ordenes),      // Long (totalParaLlevar)
                totalGeneral(ordenes)            // BigDecimal
        );
    }
}


