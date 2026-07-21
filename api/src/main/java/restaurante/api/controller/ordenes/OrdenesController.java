package restaurante.api.controller.ordenes;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.orden.*;


@RequestMapping("/ordenes")
@RestController
public class OrdenesController {

    @Autowired
    private OrdenService service;

    @PostMapping
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'MESERO', 'REPARTIDOR')")
    public ResponseEntity<DatosApertura> abrirMesa(@RequestBody @Valid DatosAbrirOrden datos, UriComponentsBuilder uriBuilder) {
        DatosApertura apertura = service.abrirCuenta(datos);
        var url = uriBuilder.path("/ordenes/{id}").buildAndExpand(apertura.id_orden()).toUri();
        return ResponseEntity.created(url).body(apertura);
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

    @GetMapping("/entregas/hoy")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'REPARTIDOR')")
    public ResponseEntity<List<DatosEntregaHoy>> obtenerEntregasHoy() {
        return ResponseEntity.ok(service.obtenerEntregasHoy());
    }

    // Las comandas del día del empleado autenticado (mesero ve solo las suyas),
    // para revisarlas y reimprimir su ticket.
    @GetMapping("/mis-comandas")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'MESERO', 'REPARTIDOR', 'CAJERO')")
    public ResponseEntity<List<DatosComandaEmpleado>> misComandas() {
        return ResponseEntity.ok(service.obtenerMisComandasDelDia());
    }

    @PutMapping("/{id}/cerrar")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'CAJERO', 'MESERO', 'REPARTIDOR')")
    public ResponseEntity<DatosRespuestaCuenta> darCuenta(@PathVariable Long id) {
        var ticket = service.darCuenta(id);
        return ResponseEntity.ok(ticket);
    }

    @PutMapping("/{id}/cancelar")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'CAJERO', 'MESERO', 'REPARTIDOR')")
    public ResponseEntity<Void> cancelarOrden(@PathVariable Long id) {
        service.cancelarOrden(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reimprimir-ticket")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'CAJERO', 'MESERO')")
    public ResponseEntity<DatosRespuestaCuenta> reimprimirTicket(@PathVariable Long id) {
        return ResponseEntity.ok(service.reimprimirTicket(id));
    }

    @PostMapping("/{id}/reenviar-cocina")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'MESERO')")
    public ResponseEntity<Void> reenviarACocina(@PathVariable Long id) {
        service.reenviarACocina(id);
        return ResponseEntity.ok().build();
    }
}