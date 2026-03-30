package restaurante.api.orden;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import restaurante.api.mesa.Estado;
import restaurante.api.mesa.MesaRepository;
import restaurante.api.ordenDetalle.DatosPlatilloLote;
import restaurante.api.ordenDetalle.DatosSincronizarComanda;
import restaurante.api.ordenDetalle.OrdenDetalle;
import restaurante.api.ordenDetalle.OrdenDetalleRepository;
import restaurante.api.producto.ProductoRepository;
import restaurante.api.usuario.UsuarioRepository;

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
    public void abrirCuenta(DatosAbrirOrden datos){
        var usuario = usuarioRepository.getReferenceById(datos.id_usuario());
        var mesa = mesaRepository.getReferenceById(datos.id_mesa());
        if (mesa.getEstado() == Estado.OCUPADA) {
            throw new RuntimeException("La mesa ya está en uso, no se puede abrir otra cuenta.");
        }

        mesa.abrirMesa();
        ordenRepository.save(new Orden(mesa,usuario,datos.tipo()));
    }


    public Page<DatosListaOrden> listar(Pageable pagina){
        return ordenRepository.findAllByTipo(pagina,Tipo.LOZA).map(DatosListaOrden::new);
    }

    @Transactional
    public void enviarOrden(DatosSincronizarComanda datos){
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
                var modificado = ordenDetalleRepository.getReferenceById(platillo.id_detalle());
                modificado.actualizarPlatillo(platillo);
            }
        }
        var platosActualizados = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes());
        orden.recalcularTotal(platosActualizados);
    }

}
