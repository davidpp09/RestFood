package restaurante.api.controller;


import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import restaurante.api.categoria.Categoria;
import restaurante.api.categoria.DatosRegistroCategoria;
import restaurante.api.categoria.CategoriaRepository;

@RequestMapping("/categorias")
@RestController
public class CategoriasController {

    @Autowired
    CategoriaRepository repository;

    public void registrar(@RequestBody @Valid DatosRegistroCategoria datosRegistroCategoria) {

        repository.save(new Categoria(datosRegistroCategoria));

    }

}

