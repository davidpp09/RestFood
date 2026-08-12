package restaurante.api.infra.impresora;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;
import com.github.anastaciocintra.output.PrinterOutputStream;
import com.github.anastaciocintra.output.TcpIpOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import restaurante.api.orden.DatosPlatilloTicket;
import restaurante.api.orden.DatosRespuestaCuenta;
import restaurante.api.orden.DatosTicketCocina;
import restaurante.api.orden.Tipo;
import restaurante.api.ordenDetalle.DatosDetalleRespuesta;
import restaurante.api.ordenDetalle.DatosTiemposComanda;

import javax.print.PrintService;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImpresoraService {

    // Valores homologados de categorias.impresora (2026-07-06). El ruteo es por
    // coincidencia EXACTA: cualquier otro valor cae a COCINA_1 con aviso en el log.
    private static final String SIN_IMPRESION = "SIN_IMPRESION";
    private static final String COCINA_1      = "COCINA_1";
    private static final String COCINA_2      = "COCINA_2";

    // Papel 80mm a fuente normal ≈ 48 columnas (58mm serían 32)
    private static final int ANCHO_TICKET = 48;
    private static final String SEPARADOR = "=".repeat(ANCHO_TICKET);

    // El ticket de cierre le cede una franja del margen derecho al nombre de la
    // mesera, que baja letra por renglón (pedido de David 2026-08-11). Son 3
    // espacios de aire y la columna 48 para la letra.
    private static final int ANCHO_COLUMNA_NOMBRE = 4;
    private static final int ANCHO_CONTENIDO = ANCHO_TICKET - ANCHO_COLUMNA_NOMBRE;

    @Value("${impresora.cocina1.nombre}")
    private String nombreCocina1;
    @Value("${impresora.cocina1.ip:}")
    private String ipCocina1;
    @Value("${impresora.cocina1.puerto:9100}")
    private int puertoCocina1;

    @Value("${impresora.cocina2.nombre}")
    private String nombreCocina2;
    @Value("${impresora.cocina2.ip:}")
    private String ipCocina2;
    @Value("${impresora.cocina2.puerto:9100}")
    private int puertoCocina2;

    @Value("${impresora.tickets.nombre}")
    private String nombreTickets;
    @Value("${impresora.tickets.ip:}")
    private String ipTickets;
    @Value("${impresora.tickets.puerto:9100}")
    private int puertoTickets;

    @Value("${impresora.meseras.nombre}")
    private String nombreMeseras;
    @Value("${impresora.meseras.ip:}")
    private String ipMeseras;
    @Value("${impresora.meseras.puerto:9100}")
    private int puertoMeseras;

    // Repartidores cuyo ticket de entrega va a COCINA2 en vez de REPARTIDORES.
    // Se normaliza a mayúsculas para comparar contra usuarios.nombre.
    @Value("${impresora.entregas.cocina2.repartidores:}")
    private List<String> repartidoresCocina2;

    /**
     * Punto de entrada. Agrupa los platillos del ticket por su impresora destino
     * y envía un ticket separado a cada una. Los platillos con "SIN_IMPRESION" (o null)
     * se descartan silenciosamente.
     */
    @Async
    public void imprimirComandaCocina(DatosTicketCocina ticket) {
        // Agrupar por impresora, descartar SIN_IMPRESION y nulos
        Map<String, List<DatosPlatilloTicket>> grupos = ticket.platillos().stream()
                .filter(p -> p.impresora() != null && !p.impresora().isBlank() && !SIN_IMPRESION.equals(p.impresora()))
                .collect(Collectors.groupingBy(DatosPlatilloTicket::impresora));

        if (grupos.isEmpty()) {
            System.out.println("🖨️ No hay platillos imprimibles en esta orden (todos son SIN_IMPRESION).");
            return;
        }

        grupos.forEach((tipoImpresora, platillos) -> {
            boolean esCocina2 = COCINA_2.equals(tipoImpresora);
            if (!esCocina2 && !COCINA_1.equals(tipoImpresora)) {
                // Valor no homologado: se imprime en COCINA_1 para no perder la comanda,
                // pero como grupo separado — corrige categorias.impresora cuanto antes.
                System.err.println("🖨️⚠️ Valor de impresora desconocido: '" + tipoImpresora
                        + "' — homologa categorias.impresora a COCINA_1 / COCINA_2 / SIN_IMPRESION. Imprimiendo en COCINA_1.");
            }
            String nombreImpresora = esCocina2 ? nombreCocina2 : nombreCocina1;
            String ip = esCocina2 ? ipCocina2 : ipCocina1;
            int puerto = esCocina2 ? puertoCocina2 : puertoCocina1;
            imprimirEnImpresora(nombreImpresora, ip, puerto, tipoImpresora, ticket, platillos);
        });
    }

    // ─── Impresión física ────────────────────────────────────────────────────────

    /**
     * Abre la conexión hacia la impresora física. Si {@code ip} viene configurada
     * (impresora.*.ip en application.properties), imprime por red (puerto 9100,
     * estándar RAW/JetDirect). Si no, cae de vuelta a la cola USB/CUPS por nombre.
     */
    private OutputStream abrirConexion(String nombreImpresora, String ip, int puerto) throws Exception {
        if (ip != null && !ip.isBlank()) {
            return new TcpIpOutputStream(ip, puerto);
        }
        PrintService printService = PrinterOutputStream.getPrintServiceByName(nombreImpresora);
        return printService != null ? new PrinterOutputStream(printService) : null;
    }

    private void imprimirEnImpresora(String nombreImpresora, String ip, int puerto, String tipoImpresora,
                                     DatosTicketCocina ticket, List<DatosPlatilloTicket> platillos) {
        try {
            OutputStream salida = abrirConexion(nombreImpresora, ip, puerto);
            if (salida == null) {
                System.err.println("🖨️❌ No se encontró la impresora [" + tipoImpresora + "]: " + nombreImpresora);
                return;
            }

            EscPos escpos = new EscPos(salida);

            escribirTicket(escpos, ticket, platillos);

            escpos.feed(4);
            escpos.cut(EscPos.CutMode.FULL);
            escpos.close();

            System.out.println("🖨️✅ Ticket [" + tipoImpresora + "] impreso en: " + nombreImpresora
                    + " (" + platillos.size() + " platillo(s))");

        } catch (Exception e) {
            System.err.println("🖨️❌ Error al imprimir en [" + tipoImpresora + "] " + nombreImpresora + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Comanda de cocina compacta (2026-07-16): mismo aire que el ticket de cuenta —
    // LOZA/LLEVAR con su comanda, quién la levantó, y cada platillo en una línea
    // con su estado (NUEVO/MODIFICADO/CANCELADO); comentarios en renglón propio.
    private void escribirTicket(EscPos escpos, DatosTicketCocina ticket,
                                List<DatosPlatilloTicket> platillos) throws Exception {
        // Cabecera LLEVAR/LOZA en doble ancho y alto (2x2, pedido de David 2026-07-21)
        Style subtitulo = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setJustification(EscPosConst.Justification.Center)
                .setBold(true);

        Style normal = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1);

        // Comentarios en doble ancho y alto (2x2, pedido de David 2026-07-21)
        Style comentario = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2);

        // Platillos en doble ancho y alto (pedido de David 2026-07-17) — a este
        // tamaño solo caben 24 columnas por línea
        Style platilloGrande = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setBold(true);

        String rayita = "-".repeat(48);

        // Cabecera: solo el tipo (LLEVAR/LOZA), quién la levantó y hora de envío a cocina
        String horaEnvio = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        if (ticket.tipo() == Tipo.LOZA) {
            escpos.writeLF(subtitulo, "LOZA");
            escpos.writeLF(normal, filaTicket("Mesera: " + ticket.nombre(), horaEnvio));
        } else {
            escpos.writeLF(subtitulo, "LLEVAR");
            escpos.writeLF(normal, filaTicket("Repartidor: " + ticket.nombre(), horaEnvio));
        }
        escpos.writeLF(rayita);

        // Platillos: cantidad + nombre en grande (envuelto a 24 columnas sin cortar
        // palabras); el estado va en su propio renglón normal, alineado a la derecha
        for (DatosPlatilloTicket p : platillos) {
            String accion = p.accion() == null ? "" : p.accion().replaceAll("[^a-zA-Z ]", "").trim();
            for (String linea : envolver(p.cantidad() + "x " + p.nombre(), 24)) {
                escpos.writeLF(platilloGrande, linea);
            }
            if (!accion.isBlank()) {
                escpos.writeLF(normal, filaTicket("", accion));
            }

            if (p.comentarios() != null && !p.comentarios().isBlank()) {
                for (String linea : envolver("* " + p.comentarios() + " *", 22)) {
                    escpos.writeLF(comentario, "  " + linea);
                }
            }
        }

        escpos.writeLF(rayita);
    }

    // ─── Talón de tiempos (PARA LLEVAR) ──────────────────────────────────────────

    /**
     * Imprime en la impresora de la zona de repartidores el talón con los tiempos
     * marcados en una orden PARA LLEVAR (consomé / sopa-crema / arroz / espaguetti),
     * para que el repartidor sepa qué empacar de cada tiempo.
     */
    @Async
    public void imprimirTiemposLlevar(Integer numeroComanda, String nombre, DatosTiemposComanda tiempos) {
        // Mismo ruteo que el ticket de entrega: repartidores configurados
        // (p. ej. SRA.ANGELES) imprimen su talón en COCINA2, el resto en REPARTIDORES,
        // para que a cada quien le salga todo junto.
        boolean aCocina2 = esRepartidorCocina2(nombre);
        String impNombre = aCocina2 ? nombreCocina2 : nombreTickets;
        String impIp     = aCocina2 ? ipCocina2     : ipTickets;
        int    impPuerto = aCocina2 ? puertoCocina2 : puertoTickets;
        try {
            OutputStream salida = abrirConexion(impNombre, impIp, impPuerto);
            if (salida == null) {
                System.err.println("🖨️❌ No se encontró la impresora del talón: " + impNombre);
                return;
            }

            EscPos escpos = new EscPos(salida);

            Style subtitulo = new Style()
                    .setFontSize(Style.FontSize._1, Style.FontSize._1)
                    .setJustification(EscPosConst.Justification.Center)
                    .setBold(true);
            Style grande = new Style()
                    .setFontSize(Style.FontSize._2, Style.FontSize._2)
                    .setBold(true);

            String rayita = "-".repeat(48);

            // Título según el grupo marcado: DESAYUNO (café/jugo) o TIEMPOS (comida).
            String titulo = tiempos.tieneDesayuno() && !tiempos.tieneComida() ? "DESAYUNO" : "TIEMPOS";
            escpos.writeLF(subtitulo, titulo + " - Comanda #" + numeroComanda);
            escpos.writeLF(rayita);

            // COMIDA (1er y 2do tiempo)
            if (tiempos.consomeSeguro() > 0)    escpos.writeLF(grande, tiempos.consomeSeguro() + "x Consome");
            if (tiempos.sopaCremaSegura() > 0)  escpos.writeLF(grande, tiempos.sopaCremaSegura() + "x Sopa/Crema");
            if (tiempos.arrozSeguro() > 0)      escpos.writeLF(grande, tiempos.arrozSeguro() + "x Arroz");
            if (tiempos.espaguettiSeguro() > 0) escpos.writeLF(grande, tiempos.espaguettiSeguro() + "x Espaguetti");
            // DESAYUNO (bebida)
            if (tiempos.cafeSeguro() > 0)       escpos.writeLF(grande, tiempos.cafeSeguro() + "x Cafe");
            if (tiempos.jugoSeguro() > 0)       escpos.writeLF(grande, tiempos.jugoSeguro() + "x Jugo");

            escpos.writeLF(rayita);

            escpos.feed(4);
            escpos.cut(EscPos.CutMode.FULL);
            escpos.close();

            System.out.println("🖨️✅ Talón impreso en: " + impNombre + " (Comanda #" + numeroComanda + ")");
        } catch (Exception e) {
            System.err.println("🖨️❌ Error al imprimir talón: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─── Ticket de cliente ───────────────────────────────────────────────────────

    @Async
    public void imprimirTicketCliente(DatosRespuestaCuenta ticket) {
        // Con mesa -> impresora de meseras (ticket final de la mesa).
        // Sin mesa (LLEVAR/entrega) -> impresora de tickets de la zona de repartidores,
        // salvo repartidores configurados para imprimir su entrega en COCINA2.
        boolean esMesa = ticket.numeroMesa() != null;
        boolean entregaACocina2 = !esMesa && esRepartidorCocina2(ticket.nombreMesero());

        String nombre;
        String ip;
        int puerto;
        if (esMesa) {
            nombre = nombreMeseras; ip = ipMeseras; puerto = puertoMeseras;
        } else if (entregaACocina2) {
            nombre = nombreCocina2; ip = ipCocina2; puerto = puertoCocina2;
        } else {
            nombre = nombreTickets; ip = ipTickets; puerto = puertoTickets;
        }
        try {
            OutputStream salida = abrirConexion(nombre, ip, puerto);
            if (salida == null) {
                System.err.println("🖨️❌ No se encontró la impresora de cliente: " + nombre);
                return;
            }

            EscPos escpos = new EscPos(salida);

            escribirTicketCliente(escpos, ticket);

            // Mínimo para que el texto libre la cuchilla sin desperdiciar papel
            escpos.feed(4);
            escpos.cut(EscPos.CutMode.FULL);
            escpos.close();

            System.out.println("🖨️✅ Ticket de cliente impreso en: " + nombre + " (Orden #" + ticket.id_orden() + ")");

        } catch (Exception e) {
            System.err.println("🖨️❌ Error al imprimir ticket de cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** true si el ticket de entrega de este repartidor debe imprimirse en COCINA2. */
    private boolean esRepartidorCocina2(String nombreMesero) {
        if (nombreMesero == null || repartidoresCocina2 == null) return false;
        return repartidoresCocina2.stream()
                .anyMatch(r -> r.trim().equalsIgnoreCase(nombreMesero.trim()));
    }

    /** Parte el texto en líneas de máximo {@code ancho} columnas sin cortar palabras. */
    private static List<String> envolver(String texto, int ancho) {
        var lineas = new java.util.ArrayList<String>();
        var actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            if (actual.length() == 0) {
                actual.append(palabra);
            } else if (actual.length() + 1 + palabra.length() <= ancho) {
                actual.append(' ').append(palabra);
            } else {
                lineas.add(actual.toString());
                actual = new StringBuilder(palabra);
            }
        }
        if (actual.length() > 0) lineas.add(actual.toString());
        return lineas;
    }

    /** Fila de 48 columnas con texto a la izquierda y algo alineado a la derecha. */
    private static String filaTicket(String izquierda, String derecha) {
        return filaAncho(izquierda, derecha, 48);
    }

    /**
     * Igual que {@link #filaTicket}, pero con el ancho como parámetro: a fuente
     * doble (FontSize._2) en papel de 80mm solo caben 24 columnas, no 48, así que
     * una fila armada a 48 se partiría en dos renglones.
     * Visible para pruebas.
     */
    static String filaAncho(String izquierda, String derecha, int ancho) {
        int anchoIzq = ancho - derecha.length() - 1;
        // Si lo de la derecha ya llena la fila, se imprime solo eso: es el dato
        // que importa (el importe), y "%-0s" ni siquiera es un formato válido.
        if (anchoIzq <= 0) return derecha;
        if (izquierda.length() > anchoIzq) izquierda = izquierda.substring(0, anchoIzq);
        return String.format("%-" + anchoIzq + "s %s", izquierda, derecha);
    }

    /**
     * Ancho de cada columna de dinero. Eran 8 y estaban desperdiciadas: el precio
     * más caro de la carta es $200 y el importe más alto jamás cobrado en un
     * renglón fue $800 — cuatro caracteres. Bajarlas a 6 es lo que deja que el
     * nombre del platillo conserve sus 32 columnas después de cederle 4 al
     * nombre de la mesera; si no, 30 platillos más saldrían cortados.
     */
    private static final int ANCHO_IMPORTE = 6;

    /**
     * Fila de producto + precio unitario + importe, con el ancho total como
     * parámetro (48 normalmente, 44 cuando el nombre de la mesera baja por la
     * orilla derecha).
     *
     * Si algún día un importe no cupiera en sus 6 columnas, el espacio se le
     * quita al nombre del platillo —que de todos modos ya viene recortado— y
     * nunca a la fila: una fila más ancha que el papel se parte en dos renglones
     * y descuadra la columna del nombre.
     *
     * Visible para pruebas.
     */
    static String filaTicket3(String izquierda, String unitario, String importe, int ancho) {
        int anchoUnitario = Math.max(ANCHO_IMPORTE, unitario.length() + 1);
        int anchoImporte = Math.max(ANCHO_IMPORTE, importe.length() + 1);
        int anchoIzq = ancho - anchoUnitario - anchoImporte;
        if (anchoIzq <= 0) return (unitario + " " + importe).trim();
        if (izquierda.length() > anchoIzq) izquierda = izquierda.substring(0, anchoIzq);
        return String.format("%-" + anchoIzq + "s%" + anchoUnitario + "s%" + anchoImporte + "s",
                izquierda, unitario, importe);
    }

    /**
     * El nombre que baja por el margen derecho del ticket, una letra por renglón.
     *
     * Solo el nombre de pila y sin acentos, por dos razones prácticas: la
     * impresora térmica no dibuja de forma fiable lo que se sale de ASCII, y un
     * "Ma. de los Angeles" completo se comería el ticket a razón de un renglón
     * por letra. Devuelve vacío si no hay nombre, y entonces el ticket sale
     * exactamente como antes salvo por las 4 columnas de aire a la derecha.
     *
     * Visible para pruebas.
     */
    static List<String> nombreVertical(String nombre) {
        if (nombre == null || nombre.isBlank()) return List.of();
        String pila = nombre.trim().split("\\s+")[0];
        String limpio = java.text.Normalizer.normalize(pila, java.text.Normalizer.Form.NFD)
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase();
        return limpio.chars().mapToObj(c -> String.valueOf((char) c)).toList();
    }

    /**
     * Pega una línea de contenido con la letra que le toca del margen derecho.
     * El contenido se recorta a 44 columnas para que la letra caiga siempre en la
     * 48: un platillo de nombre largo empuja su propio texto, nunca la columna.
     *
     * Visible para pruebas.
     */
    static String conMargen(String contenido, String letra) {
        if (contenido.length() > ANCHO_CONTENIDO) contenido = contenido.substring(0, ANCHO_CONTENIDO);
        // Sin letra no vale la pena mandar 4 espacios por el cable a la impresora.
        if (letra.isEmpty()) return contenido.stripTrailing();
        return String.format("%-" + ANCHO_CONTENIDO + "s%" + ANCHO_COLUMNA_NOMBRE + "s", contenido, letra);
    }

    /** Dinero sin decimales cuando es cantidad exacta ($50); con centavos solo si los hay. */
    private static String dinero(java.math.BigDecimal v) {
        if (v == null) return "$0";
        var limpio = v.stripTrailingZeros();
        return limpio.scale() <= 0 ? "$" + limpio.toPlainString() : String.format("$%.2f", v);
    }

    // Ticket compacto (2026-07-16, pedido de David): mínimo papel — encabezado de
    // una línea, cada platillo en una sola línea con su importe, total y despedida.
    // Visible para pruebas: así el test arma el ticket contra un flujo en memoria
    // y revisa los bytes ESC/POS, sin necesidad de una impresora física.
    void escribirTicketCliente(EscPos escpos, DatosRespuestaCuenta ticket) throws Exception {
        Style centroNegrita = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Center)
                .setBold(true);

        Style centro = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Center);

        Style normal = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1);

        // Total en doble ancho y alto (pedido de David 2026-07-28): a fuente
        // normal las meseras confundían el 0 con el 8 al cobrar. El ancho doble
        // es el que separa esos dos dígitos; el alto solo acompaña.
        Style totalGrande = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setBold(true);

        // Las rayitas también se quedan en 44 para que la columna del nombre sea
        // una franja limpia por la orilla y no la corte ningún separador.
        String rayita = "-".repeat(ANCHO_CONTENIDO);

        // Cabecera: RESTFOOD + mesera y servicio en una sola línea
        escpos.writeLF(centroNegrita, "RESTFOOD");
        String servicio = "COMIDA".equalsIgnoreCase(ticket.servicio()) ? "Comida"
                : "DESAYUNO".equalsIgnoreCase(ticket.servicio()) ? "Desayuno"
                : ticket.servicio() != null ? ticket.servicio() : "";
        // Sin mesera la cabecera salía como "- Comida", con el guion colgando.
        // Se destapó al dibujar el ticket con la vista previa el 2026-08-11.
        String quien = ticket.nombreMesero() != null ? ticket.nombreMesero().trim() : "";
        String cabecera = quien.isEmpty() ? servicio
                : servicio.isEmpty() ? quien
                : quien + " - " + servicio;
        if (!cabecera.isEmpty()) {
            escpos.writeLF(centro, cabecera);
        }
        String horaTicket = (ticket.fechaCierre() != null ? ticket.fechaCierre().toLocalTime() : java.time.LocalTime.now())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        if (ticket.numeroMesa() == null) {
            escpos.writeLF(centro, "PARA LLEVAR - Comanda #" + ticket.numero_comanda() + " - " + horaTicket);
        } else {
            escpos.writeLF(centro, "Comanda #" + ticket.numero_comanda() + " - " + horaTicket);
        }
        escpos.writeLF(rayita);

        // Platillos: cantidad y nombre + precio unitario + importe en la misma
        // línea, con el nombre de la mesera bajando por la orilla derecha.
        //
        // Si el nombre tiene más letras que platillos la columna sigue sola unos
        // renglones más; si tiene menos, se acaba y ya. El bloque de arriba (la
        // cabecera) y el de abajo (el TOTAL) no la llevan: el TOTAL va a doble
        // tamaño desde el 2026-07-28 —24 columnas, no 48— porque las meseras
        // confundían el 0 con el 8, y meterle una letra al margen obligaría a
        // encogerlo otra vez. Ese renglón se queda como está.
        List<String> letras = nombreVertical(ticket.nombreMesero());
        int renglones = Math.max(ticket.platillos().size(), letras.size());
        for (int i = 0; i < renglones; i++) {
            String contenido = "";
            if (i < ticket.platillos().size()) {
                DatosDetalleRespuesta p = ticket.platillos().get(i);
                contenido = filaTicket3(
                        p.cantidad() + "x " + p.nombre_producto(),
                        dinero(p.precio_unitario()),
                        dinero(p.subtotal()),
                        ANCHO_CONTENIDO);
            }
            escpos.writeLF(normal, conMargen(contenido, i < letras.size() ? letras.get(i) : ""));
        }

        escpos.writeLF(rayita);
        // A doble tamaño la fila mide 24 columnas, no 48 (ver filaAncho).
        escpos.writeLF(totalGrande, filaAncho("TOTAL", dinero(ticket.total()), 24));
        escpos.feed(1);
        escpos.writeLF(centro, "!Vuelva pronto!");
    }
}
