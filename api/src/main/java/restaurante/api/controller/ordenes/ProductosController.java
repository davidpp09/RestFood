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
import restaurante.api.infra.errores.RecursoNoEncontradoException;
import restaurante.api.infra.errores.ValidacionException;
import restaurante.api.producto.DatosActualizacionDia;
import restaurante.api.producto.DatosNuevoPlatilloDia;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
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

    /**
     * Alta de un platillo del día por el repartidor.
     *
     * Es un endpoint aparte del POST /productos general a propósito: ese permite
     * elegir categoría y crearía productos en cualquier parte de la carta. Aquí
     * la categoría la resuelve el servidor, así que el permiso que se le da al
     * repartidor es exactamente el que necesita y ni uno más.
     *
     * Nace APAGADO: dar de alta y poner en el menú de hoy son dos cosas. Si
     * naciera activo se saltaría el tope de la categoría, que es justo lo que
     * cabe en el recuadro del PDF.
     */
    @PostMapping("/dia")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'REPARTIDOR')")
    public ResponseEntity<DatosRespuestaProducto> registrarDelDia(
            @RequestBody @Valid DatosNuevoPlatilloDia datos,
            UriComponentsBuilder uriComponentsBuilder) {

        Categoria categoriaDia = categoriaRepository.findCategoriaDelDia()
                .orElseThrow(() -> new ValidacionException(
                        "No existe la categoría \"Comida del día\": pídele al administrador que la cree"));

        String nombre = datos.nombre().trim();

        var existente = repository.findByNombre(nombre);
        if (existente.isPresent()) {
            Producto producto = existente.get();
            if (!Boolean.TRUE.equals(producto.getEliminado())) {
                throw new ValidacionException("Ya existe un platillo con ese nombre: " + producto.getNombre());
            }
            // Estaba archivado: se revive en vez de duplicarlo, para no llenar el
            // catálogo de repetidos y no perder su historial de ventas.
            producto.restaurar(
                    new DatosRegistroProducto(nombre, datos.precio(), datos.precio(), false, categoriaDia.getId_categorias()),
                    categoriaDia);
            return ResponseEntity.ok(new DatosRespuestaProducto(producto));
        }

        var nuevo = new DatosRegistroProducto(
                nombre, datos.precio(), datos.precio(), false, categoriaDia.getId_categorias());
        Producto producto = repository.save(new Producto(nuevo, categoriaDia));

        URI url = uriComponentsBuilder.path("/productos/{id}")
                .buildAndExpand(producto.getId_productos()).toUri();
        return ResponseEntity.created(url).body(new DatosRespuestaProducto(producto));
    }

    /**
     * Archiva un platillo del día. Borrado suave siempre: el producto puede tener
     * ventas viejas colgando y el historial no se toca.
     *
     * Solo funciona sobre la categoría del día — si no, este permiso serviría para
     * que un repartidor borrara cualquier cosa de la carta.
     */
    @DeleteMapping("/dia/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'REPARTIDOR')")
    public ResponseEntity<Void> archivarDelDia(@PathVariable Long id) {
        Producto producto = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el platillo " + id));

        Categoria categoriaDia = categoriaRepository.findCategoriaDelDia()
                .orElseThrow(() -> new ValidacionException("No existe la categoría \"Comida del día\""));

        if (!categoriaDia.getId_categorias().equals(producto.getCategoria().getId_categorias())) {
            throw new ValidacionException(
                    "Desde aquí solo se archivan platillos del día, y ese es de " + producto.getCategoria().getNombre());
        }

        producto.marcarEliminado();
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/dia")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'REPARTIDOR')")
    public ResponseEntity<?> actualizarDia(@PathVariable Long id, @RequestBody DatosActualizacionDia datos) {
        Producto producto = repository.findById(id).orElseThrow();
        if (Boolean.TRUE.equals(datos.disponibilidad()) && !producto.getDisponibilidad()) {
            Categoria categoria = producto.getCategoria();
            // El tope vive en la base (columna max_activos), no clavado aquí: el menú
            // del día admite 5 porque su recuadro en el PDF tiene 5 renglones.
            int tope = categoria.getMaxActivos() != null
                    ? categoria.getMaxActivos()
                    : Categoria.TOPE_POR_DEFECTO;
            long activos = repository.countActivosPorCategoria(categoria.getId_categorias());
            if (activos >= tope) {
                return ResponseEntity.badRequest()
                        .body("Máximo " + tope + " platillos activos en " + categoria.getNombre());
            }
        }
        producto.actualizarDia(datos);
        return ResponseEntity.ok(new DatosRespuestaProducto(producto));
    }

    @PutMapping("/desactivar-dia/{categoriaId}")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'REPARTIDOR')")
    public ResponseEntity<Void> desactivarDia(@PathVariable Long categoriaId) {
        repository.desactivarPorCategoria(categoriaId);
        return ResponseEntity.noContent().build();
    }
}
