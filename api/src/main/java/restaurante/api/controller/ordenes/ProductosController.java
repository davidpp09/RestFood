package restaurante.api.controller.ordenes;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.categoria.Categoria;
import restaurante.api.categoria.CategoriaRepository;
import restaurante.api.producto.DatosActualizacionDia;
import restaurante.api.producto.DatosActualizacionProducto;
import restaurante.api.producto.DatosRegistroProducto;
import restaurante.api.producto.DatosRespuestaProducto;
import restaurante.api.ordenDetalle.OrdenDetalleRepository;
import restaurante.api.producto.Producto;
import restaurante.api.producto.ProductoRepository;

import java.net.URI;
import java.util.List;

@RequestMapping("/productos")
@RestController
public class ProductosController {

    @Autowired
    private ProductoRepository repository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private OrdenDetalleRepository ordenDetalleRepository;

    @PostMapping
    @Transactional
    @PreAuthorize("hasRole('DEV')")
    public ResponseEntity<DatosRespuestaProducto> registrar(@RequestBody @Valid DatosRegistroProducto datos, UriComponentsBuilder uriComponentsBuilder) {
        Categoria categoria = categoriaRepository.findById(datos.id_categoria()).orElseThrow();
        // El nombre es único: si existe un producto con borrado suave se reutiliza ese registro
        var existente = repository.findByNombre(datos.nombre());
        if (existente.isPresent() && Boolean.TRUE.equals(existente.get().getEliminado())) {
            Producto revivido = existente.get();
            revivido.restaurar(datos, categoria);
            URI url = uriComponentsBuilder.path("/productos/{id}").buildAndExpand(revivido.getId_productos()).toUri();
            return ResponseEntity.created(url).body(new DatosRespuestaProducto(revivido));
        }
        Producto producto = repository.save(new Producto(datos, categoria));
        URI url = uriComponentsBuilder.path("/productos/{id}").buildAndExpand(producto.getId_productos()).toUri();
        return ResponseEntity.created(url).body(new DatosRespuestaProducto(producto));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'MESERO', 'REPARTIDOR')")
    public ResponseEntity<List<DatosRespuestaProducto>> listar() {
        var lista = repository.findAllWithCategoria().stream().map(DatosRespuestaProducto::new).toList();
        return ResponseEntity.ok(lista);
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("hasRole('DEV')")
    public ResponseEntity<DatosRespuestaProducto> actualizar(@PathVariable Long id,
                                                              @RequestBody @Valid DatosActualizacionProducto datos) {
        Producto producto = repository.findById(id).orElseThrow();
        Categoria categoria = datos.id_categoria() != null
                ? categoriaRepository.findById(datos.id_categoria()).orElseThrow()
                : null;
        producto.actualizar(datos, categoria);
        return ResponseEntity.ok(new DatosRespuestaProducto(producto));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasRole('DEV')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        Producto producto = repository.findById(id).orElseThrow();
        if (ordenDetalleRepository.existePorProducto(id)) {
            // Con ventas registradas no se puede borrar físicamente (FK protege el historial)
            producto.marcarEliminado();
        } else {
            repository.delete(producto);
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/dia")
    @Transactional
    @PreAuthorize("hasAnyRole('DEV', 'REPARTIDOR')")
    public ResponseEntity<?> actualizarDia(@PathVariable Long id, @RequestBody DatosActualizacionDia datos) {
        Producto producto = repository.findById(id).orElseThrow();
        if (Boolean.TRUE.equals(datos.disponibilidad()) && !producto.getDisponibilidad()) {
            long activos = repository.countActivosPorCategoria(producto.getCategoria().getId_categorias());
            if (activos >= 7) {
                return ResponseEntity.badRequest().body("Máximo 7 platillos activos por categoría");
            }
        }
        producto.actualizarDia(datos);
        return ResponseEntity.ok(new DatosRespuestaProducto(producto));
    }

    @PutMapping("/desactivar-dia/{categoriaId}")
    @Transactional
    @PreAuthorize("hasAnyRole('DEV', 'REPARTIDOR')")
    public ResponseEntity<Void> desactivarDia(@PathVariable Long categoriaId) {
        repository.desactivarPorCategoria(categoriaId);
        return ResponseEntity.noContent().build();
    }
}
