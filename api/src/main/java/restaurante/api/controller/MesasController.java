package restaurante.api.controller;


import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import restaurante.api.mesa.DatosRegistroMesa;
import restaurante.api.mesa.Mesa;
import restaurante.api.mesa.MesaRepository;
import restaurante.api.usuario.DatosRegistroUsuario;

@RequestMapping("/mesas")
@RestController
public class MesasController {

    @Autowired
    MesaRepository repository;

    @PostMapping
    public void registrar(@RequestBody @Valid DatosRegistroMesa datosRegistroMesa){
        repository.save(new Mesa(datosRegistroMesa));
    }



}
