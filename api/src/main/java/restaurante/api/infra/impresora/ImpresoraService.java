package restaurante.api.infra.impresora;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;
import com.github.anastaciocintra.output.PrinterOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import restaurante.api.orden.DatosPlatilloTicket;
import restaurante.api.orden.DatosTicketCocina;
import restaurante.api.orden.Tipo;

import javax.print.PrintService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImpresoraService {

    private static final String SIN_IMPRESION = "SIN_IMPRESION";
    private static final String COCINA_2      = "COCINA_2";

    @Value("${impresora.cocina1.nombre}")
    private String nombreCocina1;

    @Value("${impresora.cocina2.nombre}")
    private String nombreCocina2;

    /**
     * Punto de entrada. Agrupa los platillos del ticket por su impresora destino
     * y envía un ticket separado a cada una. Los platillos con "SIN_IMPRESION" (o null)
     * se descartan silenciosamente.
     *
     * Para migrar a TCP/IP en el futuro: reemplaza {@code abrirConexionUsb} por
     * {@code abrirConexionSocket(ip, puerto)} y pasa las coordenadas como parámetro.
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
            String nombreImpresora = COCINA_2.equals(tipoImpresora) ? nombreCocina2 : nombreCocina1;
            imprimirEnImpresora(nombreImpresora, tipoImpresora, ticket, platillos);
        });
    }

    // ─── Impresión física ────────────────────────────────────────────────────────

    private void imprimirEnImpresora(String nombreImpresora, String tipoImpresora,
                                     DatosTicketCocina ticket, List<DatosPlatilloTicket> platillos) {
        try {
            PrintService printService = PrinterOutputStream.getPrintServiceByName(nombreImpresora);
            if (printService == null) {
                System.err.println("🖨️❌ No se encontró la impresora [" + tipoImpresora + "]: " + nombreImpresora);
                return;
            }

            PrinterOutputStream printerOutputStream = new PrinterOutputStream(printService);
            EscPos escpos = new EscPos(printerOutputStream);

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
        escpos.writeLF(subtitulo, "ORDEN #" + ticket.id_orden());
        escpos.writeLF("================================");

        if (ticket.tipo() == Tipo.LOZA && ticket.id_mesa() != null) {
            escpos.writeLF(titulo, "MESA " + ticket.id_mesa());
        } else {
            escpos.writeLF(titulo, "PARA LLEVAR");
        }

        escpos.writeLF("Mesero: " + ticket.nombre());
        escpos.writeLF("================================");
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

        escpos.writeLF("================================");
        escpos.writeLF(normal, "        -- FIN ORDEN --         ");
    }
}
