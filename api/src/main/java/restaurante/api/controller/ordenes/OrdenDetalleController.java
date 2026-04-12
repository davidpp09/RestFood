package restaurante.api.controller.ordenes;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.orden.DatosRespuestaOrden;
import restaurante.api.orden.OrdenService;
import restaurante.api.ordenDetalle.DatosSincronizarComanda;

import java.net.URI;

@RequestMapping("/ordendetalles")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'MESERO', 'REPARTIDOR')")
public class OrdenDetalleController {

    @Autowired
    private OrdenService ordenService;

    @PostMapping
    @Transactional
    public ResponseEntity<DatosRespuestaOrden> registrar(@RequestBody @Valid DatosSincronizarComanda datos, UriComponentsBuilder uriComponentsBuilder) {
        DatosRespuestaOrden datosRespuesta = ordenService.enviarOrden(datos);
        URI url = uriComponentsBuilder.path("/ordenes/{id}").buildAndExpand(datosRespuesta.id_orden()).toUri();
        return ResponseEntity.created(url).body(datosRespuesta);
    }
}