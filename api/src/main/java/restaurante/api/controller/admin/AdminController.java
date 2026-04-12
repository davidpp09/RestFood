package restaurante.api.controller.admin;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import restaurante.api.admin.DatosCorteDia;
import restaurante.api.orden.OrdenService;

@RequestMapping("/admin")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
public class AdminController {

    @Autowired
    OrdenService ordenService;

    @GetMapping
    public ResponseEntity<DatosCorteDia> corteDia() {
        var datos = ordenService.master();
        return ResponseEntity.ok(datos);
    }
}
