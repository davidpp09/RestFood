package restaurante.api.controller.ordenes;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.inventario.*;
import restaurante.api.usuario.Usuario;

import java.net.URI;
import java.util.List;

/**
 * Inventario, Fase 1: el kardex a mano.
 *
 * Reparto de permisos: COCINA es quien tiene la mercancía enfrente, así que
 * captura entradas, mermas y conteos. El alta de insumos y el catálogo son de
 * ADMIN/DEV, porque definir QUÉ se controla es una decisión de negocio, no de
 * operación.
 */
@RequestMapping("/inventario")
@RestController
public class InventarioController {

    @Autowired
    private InsumoRepository insumoRepository;

    @Autowired
    private InventarioService service;

    // ---------- Catálogo de insumos ----------

    @PostMapping("/insumos")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<DatosRespuestaInsumo> registrarInsumo(@RequestBody @Valid DatosRegistroInsumo datos,
                                                                UriComponentsBuilder uriBuilder) {
        // El nombre es único: si existe uno dado de baja, se reactiva en vez de
        // chocar contra la restricción — mismo criterio que en productos.
        var existente = insumoRepository.findByNombre(datos.nombre());
        if (existente.isPresent()) {
            Insumo insumo = existente.get();
            insumo.actualizar(new DatosActualizacionInsumo(null, datos.unidad(), datos.stock_minimo(), true));
            return ResponseEntity.ok(new DatosRespuestaInsumo(insumo));
        }
        Insumo insumo = insumoRepository.save(new Insumo(datos));
        URI url = uriBuilder.path("/inventario/insumos/{id}").buildAndExpand(insumo.getId_insumos()).toUri();
        return ResponseEntity.created(url).body(new DatosRespuestaInsumo(insumo));
    }

    @GetMapping("/insumos")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'COCINA')")
    public ResponseEntity<List<DatosRespuestaInsumo>> listarInsumos() {
        return ResponseEntity.ok(insumoRepository.findByActivoTrueOrderByNombreAsc()
                .stream().map(DatosRespuestaInsumo::new).toList());
    }

    @PutMapping("/insumos/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<DatosRespuestaInsumo> actualizarInsumo(@PathVariable Long id,
                                                                 @RequestBody @Valid DatosActualizacionInsumo datos) {
        Insumo insumo = insumoRepository.findById(id).orElseThrow();
        insumo.actualizar(datos);
        return ResponseEntity.ok(new DatosRespuestaInsumo(insumo));
    }

    @DeleteMapping("/insumos/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<Void> desactivarInsumo(@PathVariable Long id) {
        // Nunca se borra: sus movimientos son historia y la FK los protege.
        insumoRepository.findById(id).orElseThrow().desactivar();
        return ResponseEntity.noContent().build();
    }

    // ---------- Movimientos ----------

    // Sin @Transactional aquí: el servicio ya lo es. Si el controller abriera
    // su propia transacción y atrapara el error dentro, la transacción quedaría
    // marcada para rollback y el commit reventaría en 500 — tapando el mensaje
    // que se quería dar. Las reglas de negocio suben como ValidacionException y
    // las formatea TratadorDeErrores.
    @PostMapping("/movimientos")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'COCINA')")
    public ResponseEntity<DatosRespuestaMovimiento> registrarMovimiento(
            @RequestBody @Valid DatosRegistroMovimiento datos,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.registrar(datos, usuario));
    }

    @GetMapping("/insumos/{id}/kardex")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'COCINA')")
    public ResponseEntity<List<DatosRespuestaMovimiento>> kardex(@PathVariable Long id) {
        return ResponseEntity.ok(service.kardex(id));
    }

    // ---------- Existencias ----------

    @GetMapping("/existencias")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'COCINA')")
    public ResponseEntity<List<DatosExistencia>> existencias() {
        return ResponseEntity.ok(service.existencias());
    }

    // ---------- Conteo físico ----------

    @PostMapping("/conteos")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'COCINA')")
    public ResponseEntity<DatosRespuestaConteo> registrarConteo(
            @RequestBody @Valid DatosRegistroConteo datos,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.registrarConteo(datos, usuario));
    }
}
