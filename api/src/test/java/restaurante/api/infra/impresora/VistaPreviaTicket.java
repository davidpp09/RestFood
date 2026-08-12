package restaurante.api.infra.impresora;

import com.github.anastaciocintra.escpos.EscPos;
import org.junit.jupiter.api.Test;
import restaurante.api.orden.DatosRespuestaCuenta;
import restaurante.api.ordenDetalle.DatosDetalleRespuesta;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ver el ticket de cierre sin gastar papel ni ocupar la impresora.
 *
 *   ./mvnw test -Dtest=VistaPreviaTicket
 *
 * NO corre en el CI: surefire solo recoge las clases que terminan en Test o
 * Tests, igual que pasa con CalibradorPlantillaMenu. Es una herramienta, no una
 * prueba — no afirma nada, solo dibuja.
 *
 * Existe por la lección del 2026-08-01: comprobar coordenadas en la cabeza no es
 * comprobar. Al mover una columna del ticket, mirar el dibujo antes de mandar
 * nada a producción; los recuadros marcan dónde cae la columna 48.
 */
class VistaPreviaTicket {

    @Test
    void dibujar() throws Exception {
        imprimirEnPantalla("Valeria", List.of(
                platillo(2, "Pozole", "95", "190"),
                platillo(1, "Agua de jamaica", "25", "25")), "215");

        // Nombre más largo que la lista de platillos: la columna sigue sola.
        imprimirEnPantalla("Guadalupe", List.of(
                platillo(1, "Bistec a la Tampiquena", "120", "120")), "120");

        // Un nombre de platillo que no cabe: debe recortarse él, no empujar la letra.
        imprimirEnPantalla("Ana", List.of(
                platillo(1, "Camarones al mojo de ajo con arroz y ensalada", "260", "260")), "260");

        // Sin mesera: el ticket sale como siempre, sin columna.
        imprimirEnPantalla(null, List.of(platillo(1, "Cafe", "30", "30")), "30");
    }

    private static DatosDetalleRespuesta platillo(int cantidad, String nombre, String unitario, String subtotal) {
        return new DatosDetalleRespuesta(1L, cantidad, new BigDecimal(unitario), new BigDecimal(subtotal),
                null, 1L, 1L, nombre);
    }

    private static void imprimirEnPantalla(String mesera, List<DatosDetalleRespuesta> platillos, String total)
            throws Exception {
        var ticket = new DatosRespuestaCuenta(1L, 42, "5", "LOZA",
                LocalDateTime.now(), LocalDateTime.now(),
                platillos, new BigDecimal(total), "CERRADA", mesera, "COMIDA");

        var salida = new ByteArrayOutputStream();
        var escpos = new EscPos(salida);
        new ImpresoraService().escribirTicketCliente(escpos, ticket);
        escpos.close();

        // Fuera los bytes de control ESC/POS; queda el papel tal como se lee.
        String texto = new String(salida.toByteArray(), StandardCharsets.UTF_8)
                .replaceAll("[\\x00-\\x09\\x0B-\\x1F]", "");

        System.out.println();
        System.out.println("mesera: " + (mesera == null ? "(ninguna)" : mesera));
        System.out.println("+" + "-".repeat(48) + "+");
        for (String linea : texto.split("\n")) {
            // Las últimas 48 columnas son el contenido: lo de antes son los bytes
            // de estilo del renglón, que en pantalla se ven como basura.
            String contenido = linea.length() >= 48 ? linea.substring(linea.length() - 48) : linea;
            System.out.println("|" + String.format("%-48s", contenido) + "|");
        }
        System.out.println("+" + "-".repeat(48) + "+");
    }
}
