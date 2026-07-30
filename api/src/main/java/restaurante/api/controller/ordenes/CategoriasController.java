package restaurante.api.controller.ordenes;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.categoria.Categoria;
import restaurante.api.categoria.CategoriaRepository;
import restaurante.api.categoria.DatosRegistroCategoria;
import restaurante.api.categoria.DatosRespuestaCategoria;

import java.net.URI;

@RequestMapping("/categorias")
@RestController
public class CategoriasController {

    @Autowired
    private CategoriaRepository repository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'REPARTIDOR')")
    public ResponseEntity<List<DatosRespuestaCategoria>> listar() {
        var lista = repository.findAll().stream()
                .map(DatosRespuestaCategoria::new)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<DatosRespuestaCategoria> registrar(@RequestBody @Valid DatosRegistroCategoria datosRegistroCategoria, UriComponentsBuilder uriComponentsBuilder) {
        Categoria categoria = repository.save(new Categoria(datosRegistroCategoria));
        DatosRespuestaCategoria datosRespuesta = new DatosRespuestaCategoria(categoria);
        URI url = uriComponentsBuilder.path("/categorias/{id}").buildAndExpand(categoria.getId_categorias()).toUri();
        return ResponseEntity.created(url).body(datosRespuesta);
    }
}