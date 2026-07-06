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

            escpos.feed(5);
            escpos.cut(EscPos.CutMode.FULL);
            escpos.close();

            System.out.println("🖨️✅ Ticket [" + tipoImpresora + "] impreso en: " + nombreImpresora
                    + " (" + platillos.size() + " platillo(s))");

        } catch (Exception e) {
            System.err.println("🖨️❌ Error al imprimir en [" + tipoImpresora + "] " + nombreImpresora + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void escribirTicket(EscPos escpos, DatosTicketCocina ticket,
                                List<DatosPlatilloTicket> platillos) throws Exception {
        Style titulo = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setJustification(EscPosConst.Justification.Center)
                .setBold(true);

        Style subtitulo = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Center)
                .setBold(true);

        Style normal = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1);

        Style negrita = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setBold(true);

        Style accionStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Right);

        // Cabecera
        escpos.writeLF(titulo, "NUEVA ORDEN");
        escpos.writeLF(subtitulo, "COMANDA #" + ticket.numero_comanda());
        escpos.writeLF(SEPARADOR);

        if (ticket.tipo() == Tipo.LOZA && ticket.numero_mesa() != null) {
            // numero_mesa es el número visible de la mesa (mesas.numero), no el id interno
            escpos.writeLF(titulo, "MESA " + ticket.numero_mesa());
        } else {
            escpos.writeLF(titulo, "PARA LLEVAR");
        }

        escpos.writeLF("Mesero: " + ticket.nombre());
        escpos.writeLF(SEPARADOR);
        escpos.feed(1);

        // Platillos del grupo
        for (DatosPlatilloTicket p : platillos) {
            escpos.writeLF(negrita, p.cantidad() + "x " + p.nombre());

            if (p.comentarios() != null && !p.comentarios().isBlank()) {
                escpos.writeLF(normal, "  *" + p.comentarios() + "*");
            }

            if (p.accion() != null && !p.accion().isEmpty()) {
                String accionLimpia = p.accion().replaceAll("[^a-zA-Z ]", "").trim();
                escpos.writeLF(accionStyle, "[" + accionLimpia + "]");
            }
            escpos.feed(1);
        }

        escpos.writeLF(SEPARADOR);
        escpos.writeLF(subtitulo, "-- FIN ORDEN --");
    }

    // ─── Ticket de cliente ───────────────────────────────────────────────────────

    @Async
    public void imprimirTicketCliente(DatosRespuestaCuenta ticket) {
        try {
            OutputStream salida = abrirConexion(nombreTickets, ipTickets, puertoTickets);
            if (salida == null) {
                System.err.println("🖨️❌ No se encontró la impresora de tickets: " + nombreTickets);
                return;
            }

            EscPos escpos = new EscPos(salida);

            escribirTicketCliente(escpos, ticket);

            escpos.feed(5);
            escpos.cut(EscPos.CutMode.FULL);
            escpos.close();

            System.out.println("🖨️✅ Ticket de cliente impreso en: " + nombreTickets + " (Orden #" + ticket.id_orden() + ")");

        } catch (Exception e) {
            System.err.println("🖨️❌ Error al imprimir ticket de cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void escribirTicketCliente(EscPos escpos, DatosRespuestaCuenta ticket) throws Exception {
        Style titulo = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
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

        Style derecha = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Right);

        Style totalStyle = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setBold(true);

        // Cabecera
        escpos.writeLF(titulo, "RESTFOOD");
        escpos.writeLF(centro, "Ticket de Venta");
        escpos.writeLF(SEPARADOR);

        // Datos de la orden
        escpos.writeLF(normal, "Orden  : #" + ticket.id_orden());
        escpos.writeLF(normal, "Comanda: #" + ticket.numero_comanda());
        if (ticket.numeroMesa() != null) {
            escpos.writeLF(normal, "Mesa   : " + ticket.numeroMesa());
        } else {
            escpos.writeLF(normal, "Tipo   : Para llevar");
        }
        if (ticket.fechaCierre() != null) {
            escpos.writeLF(normal, "Fecha  : " + ticket.fechaCierre().toLocalDate());
            escpos.writeLF(normal, "Hora   : " + ticket.fechaCierre().toLocalTime().withNano(0));
        }
        escpos.writeLF(SEPARADOR);
        escpos.writeLF(negrita, "CONSUMO");
        escpos.writeLF(SEPARADOR);
        escpos.feed(1);

        // Platillos
        for (DatosDetalleRespuesta p : ticket.platillos()) {
            // Nombre + cantidad
            escpos.writeLF(negrita, p.cantidad() + "x " + p.nombre_producto());
            if (p.comentarios() != null && !p.comentarios().isBlank()) {
                escpos.writeLF(normal, "  (" + p.comentarios() + ")");
            }
            // Precio unitario izq, subtotal der
            String precioFila = "$" + p.precio_unitario() + "    $" + p.subtotal();
            escpos.writeLF(derecha, precioFila);
            escpos.feed(1);
        }

        escpos.writeLF(SEPARADOR);
        escpos.writeLF(normal, "");

        // Total
        escpos.writeLF(negrita, "TOTAL:");
        escpos.writeLF(totalStyle, "$" + ticket.total());

        escpos.writeLF(SEPARADOR);
        escpos.feed(1);
        escpos.writeLF(centro, "!Gracias por su visita!");
        escpos.writeLF(centro, "Vuelva pronto :)");
    }
}
