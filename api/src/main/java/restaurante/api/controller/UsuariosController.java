package restaurante.api.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import restaurante.api.usuario.*;

import java.util.List;

@RequestMapping("/usuarios")
@RestController
public class UsuariosController {

    @Autowired
    UsuarioRepository repository;

    @Transactional
    @PostMapping
    public void registrar(@RequestBody @Valid DatosRegistroUsuario datosRegistroUsuario){
        repository.save(new Usuario(datosRegistroUsuario));
    }

    @GetMapping
    public Page<DatosListaUsuario> listar( @PageableDefault(size = 10, sort = {"nombre"}) Pageable pagina){
        return repository.findAllByEstatusTrue(pagina).map(DatosListaUsuario::new);
    }

    @PutMapping
    @Transactional
    public void actualizar(@RequestBody @Valid DatosActualizacionUsuario datos){
     var medico = repository.getReferenceById(datos.id_usuarios());
    medico.actualizarInformacion(datos);
    }

    @Transactional
    @DeleteMapping("/{id}")
    public void eliminarLogico(@PathVariable Long id){
        var medico = repository.getReferenceById(id);
        medico.eliminarUsuario(id);
    }

    @Transactional
    @DeleteMapping("activar/{id}")
    public void activar(@PathVariable Long id){
        var medico = repository.getReferenceById(id);
        medico.activarUsuario(id);
    }

    @Transactional
    @DeleteMapping("eliminar/{id}")
    public void eliminar(@PathVariable Long id){
         repository.deleteById(id);
    }

}
