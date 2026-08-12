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

    // ─── El nombre de la mesera bajando por el margen derecho (2026-08-11) ────

    @Test
    @DisplayName("el nombre se parte en letras, una por renglon")
    void nombreLetraPorRenglon() {
        assertEquals(List.of("V", "A", "L", "E", "R", "I", "A"),
                ImpresoraService.nombreVertical("Valeria"));
    }

    @Test
    @DisplayName("solo el nombre de pila y sin acentos: la termica no dibuja lo que no es ASCII")
    void nombreSoloPilaYSinAcentos() {
        assertEquals(List.of("J", "O", "S", "E"), ImpresoraService.nombreVertical("José Pérez Loza"));
        assertEquals(List.of(), ImpresoraService.nombreVertical(null));
        assertEquals(List.of(), ImpresoraService.nombreVertical("   "));
    }

    @Test
    @DisplayName("la letra cae siempre en la columna 48, aunque el platillo tenga nombre largo")
    void laLetraNoSeMueve() {
        String corto = ImpresoraService.conMargen("2x Agua", "V");
        String largo = ImpresoraService.conMargen("1x " + "Bistec".repeat(20), "V");

        assertEquals(48, corto.length());
        assertEquals(48, largo.length());
        assertTrue(corto.endsWith("V"), "la letra debe quedar pegada a la orilla derecha");
        assertTrue(largo.endsWith("V"), "un nombre largo debe recortarse a si mismo, no empujar la columna");
    }

    @Test
    @DisplayName("sin letra no se mandan espacios de relleno por el cable")
    void sinLetraNoHayRelleno() {
        assertEquals("2x Agua", ImpresoraService.conMargen("2x Agua", ""));
    }

    @Test
    @DisplayName("MARELI baja por la orilla del ticket aunque solo haya un platillo")
    void elNombreSigueCuandoSeAcabanLosPlatillos() throws Exception {
        // El ticket de prueba trae UN platillo y la mesera se llama MARELI: la
        // columna tiene que continuar 5 renglones despues del ultimo platillo.
        String texto = new String(imprimir(ticketCon(new BigDecimal("120"))), StandardCharsets.UTF_8);

        assertEquals(List.of("M", "A", "R", "E", "L", "I"), margenDerechoDe(texto),
                "el margen derecho debe leerse MARELI de arriba abajo");
    }

    @Test
    @DisplayName("el nombre del platillo no pierde columnas por la del margen")
    void elPlatilloConservaSusColumnas() throws Exception {
        // Los platillos de aqui tienen nombres larguisimos (hasta 62 caracteres),
        // asi que cada columna que pierden se nota. Ceder 4 al nombre de la mesera
        // sin tocar nada mas habria cortado 30 platillos mas de los que ya se
        // cortaban; el espacio salio de las columnas de dinero, que sobraban.
        String texto = new String(imprimir(ticketCon(new BigDecimal("120"))), StandardCharsets.UTF_8);

        assertTrue(texto.contains("1x Bistec a la Tampiquena"),
                "el nombre del platillo debe caber entero, como cabia antes del margen");
    }

    @Test
    @DisplayName("un importe descomunal se come el nombre, nunca desborda la fila")
    void importeGrandeNoDesbordaLaFila() {
        String fila = ImpresoraService.conMargen(
                ImpresoraService.filaTicket3("1x Camarones al mojo de ajo con arroz",
                        "$1234.56", "$98765.43", 44), "A");

        assertEquals(48, fila.length(), "la fila no puede pasarse del papel: se partiria en dos renglones");
        assertTrue(fila.endsWith("A"), "la letra del margen se queda en su columna");
        assertTrue(fila.contains("$98765.43"), "el importe es el dato que no se puede recortar");
    }

    @Test
    @DisplayName("el TOTAL a doble tamano se queda intacto: no lo encoge la columna del nombre")
    void elTotalNoSeEncoge() throws Exception {
        String texto = new String(imprimir(ticketCon(new BigDecimal("808"))), StandardCharsets.UTF_8);

        assertTrue(texto.contains(ImpresoraService.filaAncho("TOTAL", "$808", 24)),
                "el total debe seguir armado a 24 columnas de fuente doble, sin letra al margen");
    }

    // ─── Ayudas ──────────────────────────────────────────────────────────────

    /**
     * Las letras que quedaron pegadas a la orilla derecha, de arriba abajo.
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
