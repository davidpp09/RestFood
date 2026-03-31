package restaurante.api.orden;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import restaurante.api.mesa.Estado;
import restaurante.api.mesa.MesaRepository;
import restaurante.api.ordenDetalle.*;
import restaurante.api.producto.ProductoRepository;
import restaurante.api.usuario.UsuarioRepository;
import java.util.List;

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
        ordenRepository.save(new Orden(mesa,usuario,datos.tipo(),datos.servicio()));
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


}
