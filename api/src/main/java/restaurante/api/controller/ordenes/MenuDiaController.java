package restaurante.api.controller.ordenes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import restaurante.api.infra.errores.ValidacionException;
import restaurante.api.menu.MenuDiaService;
import restaurante.api.producto.Producto;
import restaurante.api.producto.ProductoRepository;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * El menú del día en PDF, armado con los platillos que ya están activos en el
 * sistema. La idea es que el menú se capture UNA sola vez: quien activa los
 * platillos en la pantalla del día ya no tiene que reescribirlos a mano en el
 * archivo, así que no hay forma de que el precio del papel y el que cobra la caja
 * se contradigan.
 */
@RequestMapping("/menu-dia")
@RestController
public class MenuDiaController {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MenuDiaService menuDiaService;

    @GetMapping("/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'REPARTIDOR')")
    public ResponseEntity<byte[]> pdf() throws IOException {
        List<Producto> platillos = productoRepository.findActivosDelDia();
        if (platillos.isEmpty()) {
            throw new ValidacionException(
                    "No hay platillos activos en Comida del día: actívalos antes de generar el menú");
        }

        byte[] pdf = menuDiaService.generar(platillos);

        // inline para que el front pueda mostrar la vista previa antes de descargar.
        var disposicion = ContentDisposition.inline()
                .filename("menu-del-dia-" + LocalDate.now() + ".pdf")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
