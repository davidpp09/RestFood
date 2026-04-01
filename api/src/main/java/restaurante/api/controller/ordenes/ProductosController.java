package restaurante.api.controller.ordenes;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.producto.DatosRegistroProducto;
import restaurante.api.producto.DatosRespuestaProducto;
import restaurante.api.producto.Producto;
import restaurante.api.producto.ProductoRepository;

import java.net.URI;

@RequestMapping("/productos")
@RestController
public class ProductosController {

    @Autowired
    private ProductoRepository repository;

    @PostMapping
    @Transactional
    public ResponseEntity<DatosRespuestaProducto> registrar(@RequestBody @Valid DatosRegistroProducto datosRegistroProducto, UriComponentsBuilder uriComponentsBuilder) {
        Producto producto = repository.save(new Producto(datosRegistroProducto));
        DatosRespuestaProducto datosRespuesta = new DatosRespuestaProducto(
                producto.getId_productos(),
                producto.getNombre(),
                producto.getPrecio_comida(),
                producto.getPrecio_desayuno(),
                producto.getDisponibilidad()
        );
        URI url = uriComponentsBuilder.path("/productos/{id}").buildAndExpand(producto.getId_productos()).toUri();
        return ResponseEntity.created(url).body(datosRespuesta);
    }

    @GetMapping
    public ResponseEntity<Page<DatosRespuestaProducto>> listar(@PageableDefault(size = 10, sort = {"nombre"}) Pageable pagina) {
        // .map(DatosRespuestaProducto::new) usa el constructor que acabamos de crear
        var page = repository.findAll(pagina).map(DatosRespuestaProducto::new);
        return ResponseEntity.ok(page);
    }
}