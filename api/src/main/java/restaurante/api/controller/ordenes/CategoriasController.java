package restaurante.api.controller.ordenes;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.categoria.Categoria;
import restaurante.api.categoria.CategoriaRepository;
import restaurante.api.categoria.DatosRegistroCategoria;
import restaurante.api.categoria.DatosRespuestaCategoria;

import java.net.URI;

@RequestMapping("/categorias")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
public class CategoriasController {

    @Autowired
    private CategoriaRepository repository;

    @PostMapping
    @Transactional
    public ResponseEntity<DatosRespuestaCategoria> registrar(@RequestBody @Valid DatosRegistroCategoria datosRegistroCategoria, UriComponentsBuilder uriComponentsBuilder) {
        Categoria categoria = repository.save(new Categoria(datosRegistroCategoria));
        DatosRespuestaCategoria datosRespuesta = new DatosRespuestaCategoria(
                categoria.getId_categorias(),
                categoria.getNombre(),
                categoria.getImpresora()
        );
        URI url = uriComponentsBuilder.path("/categorias/{id}").buildAndExpand(categoria.getId_categorias()).toUri();
        return ResponseEntity.created(url).body(datosRespuesta);
    }
}