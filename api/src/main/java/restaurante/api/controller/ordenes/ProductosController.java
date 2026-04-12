package restaurante.api.controller.ordenes;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.producto.DatosRegistroProducto;
import restaurante.api.producto.DatosRespuestaProducto;
import restaurante.api.producto.Producto;
import restaurante.api.producto.ProductoRepository;

import java.net.URI;
import java.util.List;

@RequestMapping("/productos")
@RestController
public class ProductosController {

    @Autowired
    private ProductoRepository repository;

    @PostMapping
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<DatosRespuestaProducto> registrar(@RequestBody @Valid DatosRegistroProducto datosRegistroProducto, UriComponentsBuilder uriComponentsBuilder) {
        Producto producto = repository.save(new Producto(datosRegistroProducto));
        DatosRespuestaProducto datosRespuesta = new DatosRespuestaProducto(producto);
        URI url = uriComponentsBuilder.path("/productos/{id}").buildAndExpand(producto.getId_productos()).toUri();
        return ResponseEntity.created(url).body(datosRespuesta);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'MESERO')")
    public ResponseEntity<List<DatosRespuestaProducto>> listar() {
        var lista = repository.findAllWithCategoria().stream().map(DatosRespuestaProducto::new).toList();
        return ResponseEntity.ok(lista);
    }
}