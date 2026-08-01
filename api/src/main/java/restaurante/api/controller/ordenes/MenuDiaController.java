package restaurante.api.controller.ordenes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import restaurante.api.infra.errores.ValidacionException;
import restaurante.api.menu.EnlacesDeDescarga;
import restaurante.api.menu.MenuDiaService;
import restaurante.api.producto.Producto;
import restaurante.api.producto.ProductoRepository;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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

    @Autowired
    private EnlacesDeDescarga enlacesDeDescarga;

    @GetMapping("/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'REPARTIDOR')")
    public ResponseEntity<byte[]> pdf() throws IOException {
        byte[] pdf = menuDiaService.generar(platillosDelDia());

        // inline para que el front pueda mostrar la vista previa antes de descargar.
        var disposicion = ContentDisposition.inline()
                .filename(nombreDelArchivo() + ".pdf")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * El mismo menú como imagen, para la vista previa en las tablets: el WebView
     * de Android no sabe dibujar un PDF y el recuadro salía en blanco.
     */
    @GetMapping("/imagen")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'REPARTIDOR')")
    public ResponseEntity<byte[]> imagen() throws IOException {
        byte[] png = menuDiaService.generarImagen(platillosDelDia());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(nombreDelArchivo() + ".png").build().toString())
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    /**
     * Entrega una URL de descarga que se autentica sola durante 3 minutos.
     *
     * La pantalla la pide con su token normal y luego manda ahí al navegador. El
     * porqué de este rodeo está explicado en {@link EnlacesDeDescarga}: una
     * descarga del navegador no lleva la cabecera Authorization, y en el WebView
     * de Android la vía del blob no funciona.
     */
    @PostMapping("/enlace-descarga")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'REPARTIDOR')")
    public ResponseEntity<Map<String, String>> enlaceDescarga() {
        // Que truene aquí si no hay platillos, antes de dar un enlace inservible.
        platillosDelDia();

        String token = enlacesDeDescarga.emitir();
        return ResponseEntity.ok(Map.of("url", "/menu-dia/descargar?t=" + token));
    }

    /**
     * La descarga en sí. Va sin JWT a propósito — el token de la query es la
     * credencial — así que está en la lista de rutas abiertas de
     * SecurityConfigurations.
     */
    @GetMapping("/descargar")
    public ResponseEntity<byte[]> descargar(@RequestParam("t") String token) throws IOException {
        if (!enlacesDeDescarga.esValido(token)) {
            throw new ValidacionException("El enlace de descarga venció; vuelve a generar el menú");
        }

        byte[] pdf = menuDiaService.generar(platillosDelDia());

        // attachment (y no inline) es lo que hace que el navegador lo GUARDE en
        // vez de intentar mostrarlo. En la tablet del repartidor eso es todo el
        // punto: el archivo tiene que quedar en Descargas para adjuntarlo.
        var disposicion = ContentDisposition.attachment()
                .filename(nombreDelArchivo() + ".pdf")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private List<Producto> platillosDelDia() {
        List<Producto> platillos = productoRepository.findActivosDelDia();
        if (platillos.isEmpty()) {
            throw new ValidacionException(
                    "No hay platillos activos en Comida del día: actívalos antes de generar el menú");
        }
        return platillos;
    }

    /**
     * Los días en español, sin acentos y en minúscula.
     *
     * Escritos a mano y no con {@code getDisplayName(TextStyle.FULL, locale)} por
     * dos razones: el nombre del archivo no debe depender de con qué idioma
     * arranque la JVM, y "miércoles" y "sábado" llevan acento — que en el nombre
     * de un archivo que viaja por WhatsApp a la papelería es una invitación a que
     * llegue como "mi%C3%A9rcoles".
     *
     * {@link DayOfWeek#getValue()} va de 1 (lunes) a 7 (domingo).
     */
    private static final String[] DIAS = {
            "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"
    };

    /**
     * El nombre con el que la papelería recibe el archivo: {@code sabado-01-08-26}.
     *
     * Lo pidió David así, con el día por delante: quien lo imprime necesita ver de
     * un vistazo de qué día es el menú, y "menu-del-dia-2026-08-01" obliga a
     * calcular el día de la semana en la cabeza.
     */
    private String nombreDelArchivo() {
        return nombreDelArchivo(LocalDate.now());
    }

    /** Recibe el día en vez de leer el reloj, para poder comprobarlo con fechas fijas. */
    static String nombreDelArchivo(LocalDate dia) {
        return DIAS[dia.getDayOfWeek().getValue() - 1]
                + dia.format(DateTimeFormatter.ofPattern("-dd-MM-yy"));
    }
}
