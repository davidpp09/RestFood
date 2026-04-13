package restaurante.api.controller.ordenes;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.mesa.DatosRegistroMesa;
import restaurante.api.mesa.DatosRespuestaMesa;
import restaurante.api.mesa.Mesa;
import restaurante.api.mesa.MesaRepository;

import java.net.URI;
import java.util.List;

@RequestMapping("/mesas")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'MESERO')")
public class MesasController {

    @Autowired
    private MesaRepository repository;

    @Autowired
    private restaurante.api.orden.OrdenRepository ordenRepository;

    @Autowired
    private restaurante.api.ordenDetalle.OrdenDetalleRepository ordenDetalleRepository;

    @PostMapping
    @Transactional
    public ResponseEntity<DatosRespuestaMesa> registrar(@RequestBody @Valid DatosRegistroMesa datosRegistroMesa, UriComponentsBuilder uriComponentsBuilder) {
        Mesa mesa = repository.save(new Mesa(datosRegistroMesa));
        DatosRespuestaMesa datosRespuesta = new DatosRespuestaMesa(
                mesa.getId_mesas(),
                mesa.getNumero(),
                mesa.getEstado().toString(),
                null,
                null,
                java.util.List.of()
        );
        URI url = uriComponentsBuilder.path("/mesas/{id}").buildAndExpand(mesa.getId_mesas()).toUri();
        return ResponseEntity.created(url).body(datosRespuesta);
    }

    @GetMapping
    public ResponseEntity<List<DatosRespuestaMesa>> listar() {
        var mesas = repository.findAll().stream()
                .map(m -> {
                    var orden = ordenRepository.findActivaByMesa(m.getId_mesas()).orElse(null);
                    List<restaurante.api.ordenDetalle.DatosDetalleRespuesta> platillos = java.util.List.of();
                    String nombreMesero = null;
                    if (orden != null) {
                        platillos = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes())
                                .stream().map(restaurante.api.ordenDetalle.DatosDetalleRespuesta::new).toList();
                        nombreMesero = orden.getUsuario().getNombre();
                    }
                    return new DatosRespuestaMesa(
                            m.getId_mesas(),
                            m.getNumero(),
                            m.getEstado().toString(),
                            orden != null ? orden.getId_ordenes() : null,
                            nombreMesero,
                            platillos
                    );
                })
                .toList();
        return ResponseEntity.ok(mesas);
    }

    @GetMapping("/rango/{inicio}/{fin}")
    public ResponseEntity<List<DatosRespuestaMesa>> mesasRango(@PathVariable long inicio, @PathVariable long fin) {
        var mesas = repository.buscarPorRango(inicio, fin).stream()
                .map(m -> {
                    var orden = ordenRepository.findActivaByMesa(m.getId_mesas()).orElse(null);
                    List<restaurante.api.ordenDetalle.DatosDetalleRespuesta> platillos = java.util.List.of();
                    String nombreMesero = null;
                    if (orden != null) {
                        platillos = ordenDetalleRepository.findAllByOrdenId(orden.getId_ordenes())
                                .stream().map(restaurante.api.ordenDetalle.DatosDetalleRespuesta::new).toList();
                        nombreMesero = orden.getUsuario().getNombre();
                    }
                    return new DatosRespuestaMesa(
                            m.getId_mesas(),
                            m.getNumero(),
                            m.getEstado().toString(),
                            orden != null ? orden.getId_ordenes() : null,
                            nombreMesero,
                            platillos
                    );
                })
                .toList();
        return ResponseEntity.ok(mesas);
    }
}