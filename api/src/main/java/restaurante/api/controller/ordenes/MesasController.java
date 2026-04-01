package restaurante.api.controller.ordenes;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.mesa.DatosRegistroMesa;
import restaurante.api.mesa.DatosRespuestaMesa;
import restaurante.api.mesa.Mesa;
import restaurante.api.mesa.MesaRepository;

import java.net.URI;
import java.util.List;

@RequestMapping("/mesas")
@RestController
public class MesasController {

    @Autowired
    private MesaRepository repository;

    @PostMapping
    @Transactional
    public ResponseEntity<DatosRespuestaMesa> registrar(@RequestBody @Valid DatosRegistroMesa datosRegistroMesa, UriComponentsBuilder uriComponentsBuilder) {
        Mesa mesa = repository.save(new Mesa(datosRegistroMesa));
        DatosRespuestaMesa datosRespuesta = new DatosRespuestaMesa(
                mesa.getId_mesas(),
                mesa.getNumero(),
                mesa.getEstado().toString()
        );
        URI url = uriComponentsBuilder.path("/mesas/{id}").buildAndExpand(mesa.getId_mesas()).toUri();
        return ResponseEntity.created(url).body(datosRespuesta);
    }

    @GetMapping
    public ResponseEntity<List<DatosRespuestaMesa>> listar() {
        var mesas = repository.findAll().stream()
                .map(m -> new DatosRespuestaMesa(m.getId_mesas(), m.getNumero(), m.getEstado().toString()))
                .toList();
        return ResponseEntity.ok(mesas);
    }
}