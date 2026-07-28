package restaurante.api.infra.impresora;

import com.github.anastaciocintra.escpos.EscPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import restaurante.api.orden.DatosRespuestaCuenta;
import restaurante.api.ordenDetalle.DatosDetalleRespuesta;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El ticket se arma contra un flujo en memoria, no contra una impresora: lo que
 * se revisa son los bytes ESC/POS que saldrían por el cable.
 *
 * Motivo de estas pruebas (2026-07-28): las meseras confundían el 0 con el 8 en
 * el total al cobrar. El arreglo fue imprimirlo a doble ancho y alto, y a ese
 * tamaño la fila mide 24 columnas en vez de 48.
 */
class ImpresoraServiceTest {

    /** GS ! n — el comando ESC/POS que fija el tamaño de carácter. */
    private static final byte GS = 0x1D;
    private static final byte SIGNO_ADMIRACION = 0x21;

    private DatosRespuestaCuenta ticketCon(BigDecimal total) {
        var platillo = new DatosDetalleRespuesta(
                1L, 1, new BigDecimal("120"), new BigDecimal("120"),
                null, 1L, 1L, "Bistec a la Tampiquena");
        return new DatosRespuestaCuenta(
                1L, 42, "5", "LOZA",
                LocalDateTime.now(), LocalDateTime.now(),
                List.of(platillo), total, "CERRADA", "MARELI", "COMIDA");
    }

    private byte[] imprimir(DatosRespuestaCuenta ticket) throws Exception {
        var salida = new ByteArrayOutputStream();
        var escpos = new EscPos(salida);
        new ImpresoraService().escribirTicketCliente(escpos, ticket);
        escpos.close();
        return salida.toByteArray();
    }

    @Test
    @DisplayName("el total sale a doble ancho y alto")
    void totalEnDobleTamano() throws Exception {
        byte[] bytes = imprimir(ticketCon(new BigDecimal("808")));

        int donde = indiceDe(bytes, "TOTAL");
        assertTrue(donde >= 0, "el ticket debe traer la linea del TOTAL");

        // El GS ! que precede al texto tiene que pedir doble ancho (bit alto) y
        // doble alto (bit bajo): 0x11. Con 0x00 volveriamos al tamano confuso.
        byte tamano = tamanoVigenteEn(bytes, donde);
        assertEquals(0x10, tamano & 0xF0, "el total debe ir a doble ancho");
        assertEquals(0x01, tamano & 0x0F, "el total debe ir a doble alto");
    }

    @Test
    @DisplayName("el importe del total viaja completo en el ticket")
    void importeCompleto() throws Exception {
        String texto = new String(imprimir(ticketCon(new BigDecimal("1020"))), StandardCharsets.UTF_8);
        assertTrue(texto.contains("TOTAL"), "falta la etiqueta TOTAL");
        assertTrue(texto.contains("$1020"), "falta el importe del total");
    }

    @Test
    @DisplayName("a doble tamano la fila del total mide 24 columnas, no 48")
    void filaDelTotalMide24Columnas() {
        String fila = ImpresoraService.filaAncho("TOTAL", "$808", 24);
        assertEquals(24, fila.length(), "una fila mas larga se partiria en dos renglones");
        assertTrue(fila.startsWith("TOTAL"));
        assertTrue(fila.endsWith("$808"));
    }

    @Test
    @DisplayName("si el importe llena la fila, se imprime el importe y no se rompe")
    void importeQueLlenaLaFila() {
        String fila = ImpresoraService.filaAncho("TOTAL", "$123456789012345678901234", 24);
        assertEquals("$123456789012345678901234", fila);
    }

    // ─── Ayudas ──────────────────────────────────────────────────────────────

    private static int indiceDe(byte[] bytes, String texto) {
        byte[] aguja = texto.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i <= bytes.length - aguja.length; i++) {
            boolean coincide = true;
            for (int j = 0; j < aguja.length; j++) {
                if (bytes[i + j] != aguja[j]) { coincide = false; break; }
            }
            if (coincide) return i;
        }
        return -1;
    }

    /** Ultimo tamano de caracter (GS ! n) configurado antes de la posicion dada. */
    private static byte tamanoVigenteEn(byte[] bytes, int posicion) {
        byte tamano = 0x00;
        for (int i = 0; i + 2 < posicion; i++) {
            if (bytes[i] == GS && bytes[i + 1] == SIGNO_ADMIRACION) {
                tamano = bytes[i + 2];
            }
        }
        return tamano;
    }
}
