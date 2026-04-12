package restaurante.api.controller.ordenes;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import restaurante.api.orden.DatosRespuestaOrden;
import restaurante.api.orden.OrdenService;

import java.util.List;

@RestController
@RequestMapping("/cocina")
@SecurityRequirement(name = "bearer-key")
@PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'COCINA')")
public class CocinaController {

    @Autowired
    private OrdenService ordenService;

    @GetMapping
    public ResponseEntity<List<DatosRespuestaOrden>> listarOrdenesPendientes() {
        return ResponseEntity.ok(ordenService.listarOrdenesCocina());
    }

    @PatchMapping("/{id}/servido")
    public ResponseEntity<Void> marcarComoServido(@PathVariable Long id) {
        ordenService.marcarOrdenServida(id);
        return ResponseEntity.noContent().build();
    }
}
