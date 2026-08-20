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

    // ─── El nombre de quien atendió, ahora en el pie (2026-08-20) ────────────

    @Test
    @DisplayName("el pie del ticket trae el nombre de la mesera en vez de la despedida")
    void elPieTraeElNombre() throws Exception {
        String texto = new String(imprimir(ticketCon(new BigDecimal("120"))), StandardCharsets.UTF_8);

        assertTrue(texto.contains("MARELI"), "el nombre de quien atendio debe salir en el ticket");
        assertFalse(texto.contains("Vuelva pronto"), "la despedida cede su renglon al nombre");
        // El nombre va al final: despues de el ya no hay mas texto del ticket.
        assertTrue(texto.lastIndexOf("MARELI") > texto.lastIndexOf("TOTAL"),
                "el nombre va debajo del total, no solo en la cabecera");
    }

    @Test
    @DisplayName("sin mesera el pie se queda con la despedida de siempre")
    void sinMeseraQuedaLaDespedida() throws Exception {
        var ticket = new DatosRespuestaCuenta(
                1L, 42, "5", "LOZA",
                LocalDateTime.now(), LocalDateTime.now(),
                List.of(new DatosDetalleRespuesta(1L, 1, new BigDecimal("30"), new BigDecimal("30"),
                        null, 1L, 1L, "Cafe")),
                new BigDecimal("30"), "CERRADA", null, "COMIDA");

        String texto = new String(imprimir(ticket), StandardCharsets.UTF_8);

        assertTrue(texto.contains("Vuelva pronto"), "el ticket no puede acabar en seco");
    }

    @Test
    @DisplayName("el nombre ya no baja por la orilla derecha")
    void elMargenDerechoQuedaLimpio() throws Exception {
        // Hasta el 2026-08-19 el margen se leia MARELI de arriba abajo. Si esta
        // lista vuelve a traer letras, es que regreso la columna vertical.
        String texto = new String(imprimir(ticketCon(new BigDecimal("120"))), StandardCharsets.UTF_8);

        assertEquals(List.of(), margenDerechoDe(texto), "el margen derecho debe estar vacio");
    }

    @Test
    @DisplayName("el platillo recupera las columnas que financiaban el margen")
    void elPlatilloRecuperaSusColumnas() {
        // 48 columnas menos las dos de dinero (6 + 6) = 36 para el nombre, cuatro
        // mas de las 32 que tenia con el margen puesto.
        String fila = ImpresoraService.filaTicket3("1x " + "A".repeat(60), "$260", "$260", 48);

        assertEquals(48, fila.length(), "la fila no puede pasarse del papel");
        assertEquals("1x " + "A".repeat(33), fila.substring(0, 36),
                "el nombre del platillo dispone de 36 columnas, no de 32");
        assertTrue(fila.endsWith("$260"));

        // Un nombre de 36 caracteres justos cabia cortado con el margen puesto y
        // ahora entra entero.
        String cabe = ImpresoraService.filaTicket3("1x Camarones al mojo de ajo con arro",
                "$260", "$260", 48);
        assertTrue(cabe.startsWith("1x Camarones al mojo de ajo con arro"));
    }

    @Test
    @DisplayName("el TOTAL a doble tamano se queda intacto")
    void elTotalNoSeEncoge() throws Exception {
        String texto = new String(imprimir(ticketCon(new BigDecimal("808"))), StandardCharsets.UTF_8);

        assertTrue(texto.contains(ImpresoraService.filaAncho("TOTAL", "$808", 24)),
                "el total debe seguir armado a 24 columnas de fuente doble");
    }

    // ─── Ayudas ──────────────────────────────────────────────────────────────

    /**
     * Las letras que quedaron pegadas a la orilla derecha, de arriba abajo.
     * Desde el 2026-08-20 debe salir vacia: es el centinela de que la columna
     * vertical no volvio.
     *
     * Cada renglon viene precedido por los bytes ESC/POS que fijan su estilo, asi
     * que no se puede medir la linea entera: se toman las ultimas 48 columnas,
     * que son el contenido, y se acepta como letra del margen la que este sola
     * al final detras de espacios. Las rayitas terminan en '-' y el total mide
     * menos de 48 columnas, asi que ninguno de los dos se cuela.
     */
    private static List<String> margenDerechoDe(String ticket) {
        var letras = new java.util.ArrayList<String>();
        for (String linea : ticket.split("\n")) {
            if (linea.length() < 48) continue;
            String contenido = linea.substring(linea.length() - 48);
            char ultima = contenido.charAt(47);
            if (Character.isLetterOrDigit(ultima) && contenido.charAt(46) == ' ') {
                letras.add(String.valueOf(ultima));
            }
        }
        return letras;
    }

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
