package restaurante.api.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import restaurante.api.orden.DatosAgregarPlatillo;
import restaurante.api.orden.OrdenService;
import restaurante.api.ordenDetalle.DatosRegistroOrdenDetalle;
import restaurante.api.ordenDetalle.OrdenDetalle;
import restaurante.api.ordenDetalle.OrdenDetalleRepository;

@RequestMapping("/ordendetalles")
@RestController
public class OrdenDetalleController {

    @Autowired
    OrdenService ordenService;

    @PostMapping
    public void regristrar(@RequestBody @Valid DatosAgregarPlatillo datos){
        ordenService.agregarPlatillo(datos);
    }
}
