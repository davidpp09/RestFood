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
    private static final String SEPARADOR = "=".repeat(48);

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
        Style subtitulo = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Center)
                .setBold(true);

        Style normal = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1);

        Style negrita = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setBold(true);

        String rayita = "-".repeat(48);

        // Cabecera: tipo + comanda, quién la levantó y hora de envío a cocina
        String horaEnvio = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        if (ticket.tipo() == Tipo.LOZA) {
            escpos.writeLF(subtitulo, "LOZA - Comanda #" + ticket.numero_comanda());
            escpos.writeLF(normal, filaTicket("Mesera: " + ticket.nombre(), horaEnvio));
        } else {
            escpos.writeLF(subtitulo, "LLEVAR - Comanda #" + ticket.numero_comanda());
            escpos.writeLF(normal, filaTicket("Repartidor: " + ticket.nombre(), horaEnvio));
        }
        escpos.writeLF(rayita);

        // Platillos: cantidad + nombre con su estado en la misma línea
        for (DatosPlatilloTicket p : platillos) {
            String accion = p.accion() == null ? "" : p.accion().replaceAll("[^a-zA-Z ]", "").trim();
            escpos.writeLF(negrita, filaTicket(p.cantidad() + "x " + p.nombre(), accion));

            if (p.comentarios() != null && !p.comentarios().isBlank()) {
                escpos.writeLF(normal, "   * " + p.comentarios() + " *");
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
        try {
            OutputStream salida = abrirConexion(nombreTickets, ipTickets, puertoTickets);
            if (salida == null) {
                System.err.println("🖨️❌ No se encontró la impresora de tickets (tiempos): " + nombreTickets);
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

            // Compacto: una línea de identidad y solo los tiempos pedidos, en grande
            escpos.writeLF(subtitulo, "TIEMPOS - Comanda #" + numeroComanda);
            escpos.writeLF(rayita);

            if (tiempos.consomeSeguro() > 0)    escpos.writeLF(grande, tiempos.consomeSeguro() + "x Consome");
            if (tiempos.sopaCremaSegura() > 0)  escpos.writeLF(grande, tiempos.sopaCremaSegura() + "x Sopa/Crema");
            if (tiempos.arrozSeguro() > 0)      escpos.writeLF(grande, tiempos.arrozSeguro() + "x Arroz");
            if (tiempos.espaguettiSeguro() > 0) escpos.writeLF(grande, tiempos.espaguettiSeguro() + "x Espaguetti");

            escpos.writeLF(rayita);

            escpos.feed(4);
            escpos.cut(EscPos.CutMode.FULL);
            escpos.close();

            System.out.println("🖨️✅ Talón de tiempos impreso en: " + nombreTickets + " (Comanda #" + numeroComanda + ")");
        } catch (Exception e) {
            System.err.println("🖨️❌ Error al imprimir talón de tiempos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─── Ticket de cliente ───────────────────────────────────────────────────────

    @Async
    public void imprimirTicketCliente(DatosRespuestaCuenta ticket) {
        // Con mesa -> impresora de meseras (ticket final de la mesa).
        // Sin mesa (LLEVAR/entrega) -> impresora de tickets de la zona de repartidores.
        boolean esMesa = ticket.numeroMesa() != null;
        String nombre = esMesa ? nombreMeseras : nombreTickets;
        String ip     = esMesa ? ipMeseras     : ipTickets;
        int puerto    = esMesa ? puertoMeseras : puertoTickets;
        try {
            OutputStream salida = abrirConexion(nombre, ip, puerto);
            if (salida == null) {
                System.err.println("🖨️❌ No se encontró la impresora de " + (esMesa ? "meseras" : "tickets") + ": " + nombre);
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

    /** Fila de 48 columnas con texto a la izquierda y algo alineado a la derecha. */
    private static String filaTicket(String izquierda, String derecha) {
        int anchoIzq = 48 - derecha.length() - 1;
        if (izquierda.length() > anchoIzq) izquierda = izquierda.substring(0, anchoIzq);
        return String.format("%-" + anchoIzq + "s %s", izquierda, derecha);
    }

    /** Fila de 48 columnas: producto + precio unitario + importe (32+8+8). */
    private static String filaTicket3(String izquierda, String unitario, String importe) {
        if (izquierda.length() > 32) izquierda = izquierda.substring(0, 32);
        return String.format("%-32s%8s%8s", izquierda, unitario, importe);
    }

    /** Dinero sin decimales cuando es cantidad exacta ($50); con centavos solo si los hay. */
    private static String dinero(java.math.BigDecimal v) {
        if (v == null) return "$0";
        var limpio = v.stripTrailingZeros();
        return limpio.scale() <= 0 ? "$" + limpio.toPlainString() : String.format("$%.2f", v);
    }

    // Ticket compacto (2026-07-16, pedido de David): mínimo papel — encabezado de
    // una línea, cada platillo en una sola línea con su importe, total y despedida.
    private void escribirTicketCliente(EscPos escpos, DatosRespuestaCuenta ticket) throws Exception {
        Style centroNegrita = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Center)
                .setBold(true);

        Style centro = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Center);

        Style normal = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1);

        Style negrita = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setBold(true);

        String rayita = "-".repeat(48);

        // Cabecera: RESTFOOD + mesera y servicio en una sola línea
        escpos.writeLF(centroNegrita, "RESTFOOD");
        String servicio = "COMIDA".equalsIgnoreCase(ticket.servicio()) ? "Comida"
                : "DESAYUNO".equalsIgnoreCase(ticket.servicio()) ? "Desayuno"
                : ticket.servicio() != null ? ticket.servicio() : "";
        String quien = ticket.nombreMesero() != null ? ticket.nombreMesero() : "";
        String cabecera = (quien + (servicio.isEmpty() ? "" : " - " + servicio)).trim();
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

        // Platillos: cantidad y nombre + precio unitario + importe en la misma línea
        for (DatosDetalleRespuesta p : ticket.platillos()) {
            escpos.writeLF(normal, filaTicket3(
                    p.cantidad() + "x " + p.nombre_producto(),
                    dinero(p.precio_unitario()),
                    dinero(p.subtotal())));
        }

        escpos.writeLF(rayita);
        escpos.writeLF(negrita, filaTicket("TOTAL", dinero(ticket.total())));
        escpos.feed(1);
        escpos.writeLF(centro, "!Vuelva pronto!");
    }
}
