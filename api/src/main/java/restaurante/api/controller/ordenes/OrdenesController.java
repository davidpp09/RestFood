package restaurante.api.controller.ordenes;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.orden.DatosAbrirOrden;
import restaurante.api.orden.DatosListaOrden;
import restaurante.api.orden.DatosRespuestaCuenta;
import restaurante.api.orden.DatosRespuestaOrden;
import restaurante.api.orden.OrdenService;

@RequestMapping("/ordenes")
@RestController
public class OrdenesController {

    @Autowired
    private OrdenService service;

    @PostMapping
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'MESERO', 'REPARTIDOR')")
    public ResponseEntity<Long> abrirMesa(@RequestBody @Valid DatosAbrirOrden datos, UriComponentsBuilder uriBuilder) {
        Long id = service.abrirCuenta(datos);
        var url = uriBuilder.path("/ordenes/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(url).body(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'CAJERO', 'MESERO')")
    public ResponseEntity<Page<DatosListaOrden>> mostrarMesas(@PageableDefault(size = 10) Pageable pagina) {
        var page = service.listar(pagina);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/activa/{id_mesa}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'CAJERO', 'MESERO')")
    public ResponseEntity<DatosRespuestaOrden> obtenerOrdenActiva(@PathVariable Long id_mesa) {
        return ResponseEntity.ok(service.obtenerOrdenActiva(id_mesa));
    }

    @PutMapping("/{id}/cerrar")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'CAJERO', 'MESERO')")
    public ResponseEntity<DatosRespuestaCuenta> darCuenta(@PathVariable Long id) {  // ⬅️ CAMBIO AQUÍ
        var ticket = service.darCuenta(id);  // ⬅️ CAMBIO AQUÍ
        return ResponseEntity.ok(ticket);    // ⬅️ CAMBIO AQUÍ
    }
}