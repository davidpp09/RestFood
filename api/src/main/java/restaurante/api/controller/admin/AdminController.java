package restaurante.api.controller.admin;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import restaurante.api.admin.DatosCancelacionMesero;
import restaurante.api.admin.DatosCorteDia;
import restaurante.api.orden.OrdenService;

import java.util.List;

@RequestMapping("/admin")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
public class AdminController {

    @Autowired
    OrdenService ordenService;

    @GetMapping("/corte-dia")
    public ResponseEntity<DatosCorteDia> corteDia(
            @RequestParam(required = false) LocalDate fecha) {
        var datos = ordenService.master(fecha != null ? fecha : LocalDate.now());
        return ResponseEntity.ok(datos);
    }

    @GetMapping("/cancelaciones")
    public ResponseEntity<List<DatosCancelacionMesero>> cancelaciones(
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        LocalDate d = desde != null ? desde : LocalDate.now();
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        return ResponseEntity.ok(ordenService.cancelacionesPorMesero(d, h));
    }
}
