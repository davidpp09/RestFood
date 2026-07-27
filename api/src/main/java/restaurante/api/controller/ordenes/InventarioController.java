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
import java.time.LocalDate;
import java.util.List;

/**
 * Inventario, Fase 1: el kardex a mano.
 *
 * Reparto de permisos: la captura (entradas, mermas y conteos) es de ADMIN/DEV.
 * COCINA solo CONSULTA existencias — necesita saber con qué cuenta para el
 * servicio, pero no mueve el kardex.
 *
 * Ojo: esto no se resuelve escondiendo botones en el frontend. Un menú oculto
 * no es un permiso: quien tenga el rol puede mandar la petición a mano. El
 * control real es este @PreAuthorize.
 */
@RequestMapping("/inventario")
@RestController
public class InventarioController {

    @Autowired
    private InsumoRepository insumoRepository;

    @Autowired
    private InventarioService service;

    @Autowired
    private RecetaService recetaService;

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
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<DatosRespuestaMovimiento> registrarMovimiento(
            @RequestBody @Valid DatosRegistroMovimiento datos,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.registrar(datos, usuario));
    }

    @GetMapping("/insumos/{id}/kardex")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<List<DatosRespuestaMovimiento>> kardex(@PathVariable Long id) {
        return ResponseEntity.ok(service.kardex(id));
    }

    // ---------- Existencias ----------

    @GetMapping("/existencias")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'COCINA')")
    public ResponseEntity<List<DatosExistencia>> existencias() {
        return ResponseEntity.ok(service.existencias());
    }

    // ---------- Teórico contra real (Fase 2) ----------

    /**
     * El reporte por el que existe todo este frente. Sin fechas, toma el mes en
     * curso: es el periodo con el que se piensa la operación.
     *
     * Solo ADMIN y DEV. No es un dato para la cocina — es el que se usa para
     * preguntarle a la cocina.
     */
    @GetMapping("/reportes/teorico-real")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<List<DatosTeoricoReal>> teoricoContraReal(
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        return ResponseEntity.ok(service.teoricoContraReal(d, h));
    }

    // ---------- Recetas (qué platillo consume qué insumo) ----------

    // Todas las relaciones de un jalón: permite a la pantalla marcar los
    // platillos que ya cuelgan de OTRO insumo y los que no cuelgan de ninguno.
    @GetMapping("/recetas")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<List<DatosRelacionProducto>> todasLasRecetas() {
        return ResponseEntity.ok(recetaService.todas());
    }

    @GetMapping("/insumos/{id}/receta")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<DatosRespuestaReceta> obtenerReceta(@PathVariable Long id) {
        return ResponseEntity.ok(recetaService.obtener(id));
    }

    // PUT y no POST: reemplaza la receta completa del insumo. Mandar una lista
    // vacía desliga todos sus platillos, que es justo lo que se espera al
    // desmarcar todo en la pantalla.
    @PutMapping("/insumos/{id}/receta")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<DatosRespuestaReceta> guardarReceta(@PathVariable Long id,
                                                              @RequestBody @Valid DatosGuardarReceta datos) {
        return ResponseEntity.ok(recetaService.guardar(id, datos));
    }

    // ---------- Conteo físico ----------

    @PostMapping("/conteos")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<DatosRespuestaConteo> registrarConteo(
            @RequestBody @Valid DatosRegistroConteo datos,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.registrarConteo(datos, usuario));
    }
}
