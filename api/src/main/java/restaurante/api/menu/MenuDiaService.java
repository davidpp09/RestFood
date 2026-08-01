package restaurante.api.menu;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import restaurante.api.producto.Producto;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genera el PDF del menú del día a partir de la plantilla de Canva.
 *
 * Funciona en dos pasos:
 *   1. BORRA los 5 renglones que rotan, quitando del content stream las órdenes
 *      de dibujar esos caracteres.
 *   2. Escribe encima los platillos que hoy están activos en el sistema.
 *
 * Por qué se borra y no se tapa con un rectángulo blanco: tapar deja el texto
 * viejo dentro del archivo. Se ve bien, pero al copiar el texto del PDF salen los
 * dos menús entreverados letra por letra. Borrarlo de verdad deja un archivo
 * limpio.
 *
 * Lo que hace esto posible es cómo exporta Canva: **un carácter por operador de
 * texto, cada uno con su propia matriz `Tm`**. Así cada letra trae su coordenada
 * explícita y se puede filtrar por posición sin interpretar el PDF completo.
 *
 * OJO con la plantilla: el diseño va DUPLICADO lado a lado en la misma página
 * (dos copias para imprimir y cortar), y no están alineadas: la copia derecha va
 * 1.58 pt más arriba. Por eso hay dos juegos de coordenadas.
 *
 * Las coordenadas salieron de {@code CalibradorPlantillaMenu}. Si se rehace la
 * plantilla en Canva, hay que volver a correrlo y actualizarlas.
 */
@Service
public class MenuDiaService {

    private static final String RUTA_PLANTILLA = "/menu/plantilla-menu.pdf";

    /** El recuadro "Nuestra Comida del Día" está en la página 2 (índice 0). */
    private static final int INDICE_PAGINA = 1;

    /** Renglones que rotan. Del 6 al 13 son fijos y no se tocan. */
    public static final int RENGLONES = 5;

    /** Resolución de la vista previa en PNG: legible en una tablet sin pesar de más. */
    private static final int DPI_VISTA_PREVIA = 150;

    /** Dónde empieza el texto en cada copia del diseño. */
    private static final float[] X_COPIA = {19.86f, 317.63f};

    /** Hasta dónde llega el texto de cada copia (para no borrar la de al lado). */
    private static final float ANCHO_COPIA = 270f;

    /**
     * Línea base de los 5 renglones que rotan, en coordenadas del PDF (desde
     * abajo). Primera fila: copia izquierda. Segunda: copia derecha.
     */
    private static final float[][] Y_RENGLON = {
            {758.19f, 744.63f, 731.08f, 717.52f, 703.97f},
            {756.61f, 743.06f, 729.50f, 715.95f, 702.39f}
    };

    /** Margen al comparar coordenadas: los acentos y las comas se salen un poco. */
    private static final float TOLERANCIA_Y = 2.5f;

    private static final float TAMANO_FUENTE = 9f;
    private static final float TAMANO_FUENTE_MINIMO = 7f;

    /** Ancho al que se rellena con puntos para que los precios queden en columna. */
    private static final float ANCHO_LINEA = 263f;

    private final PDType1Font helvetica =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    public byte[] generar(List<Producto> platillos) throws IOException {
        try (PDDocument documento = Loader.loadPDF(leerPlantilla())) {
            PDPage pagina = documento.getPage(INDICE_PAGINA);

            borrarRenglonesQueRotan(documento, pagina);
            escribirPlatillos(documento, pagina, platillos);

            var salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    /**
     * El mismo menú, pero como imagen PNG.
     *
     * Existe por las tablets: el WebView de Android **no trae visor de PDF**, así
     * que un {@code <iframe>} apuntando al PDF se queda en blanco y no hay error
     * en ninguna parte. Una imagen sí se dibuja en cualquier navegador, sin
     * depender de plugins ni de abrir otra aplicación.
     *
     * Es solo para MIRAR. Lo que se manda a imprimir sigue siendo el PDF, que es
     * vectorial y no se pixela; este PNG a 150 DPI es para revisar en pantalla
     * que no haya una errata antes de mandarlo.
     */
    public byte[] generarImagen(List<Producto> platillos) throws IOException {
        try (PDDocument documento = Loader.loadPDF(generar(platillos))) {
            BufferedImage imagen = new PDFRenderer(documento).renderImageWithDPI(INDICE_PAGINA, DPI_VISTA_PREVIA);

            var salida = new ByteArrayOutputStream();
            if (!ImageIO.write(imagen, "png", salida)) {
                throw new IOException("No se pudo codificar el PNG de la vista previa del menú");
            }
            return salida.toByteArray();
        }
    }

    private byte[] leerPlantilla() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(RUTA_PLANTILLA)) {
            if (in == null) {
                throw new IOException("No se encontró la plantilla del menú en " + RUTA_PLANTILLA);
            }
            return in.readAllBytes();
        }
    }

    /**
     * Recorre el content stream y descarta los operadores que dibujan texto dentro
     * de los 5 renglones que rotan. Los operadores de posición (Tm, Td...) se
     * dejan intactos a propósito: mover el cursor no pinta nada, y quitarlos sí
     * podría descolocar lo que viene después.
     */
    private void borrarRenglonesQueRotan(PDDocument documento, PDPage pagina) throws IOException {
        List<Object> tokens = new PDFStreamParser(pagina).parse();
        List<Object> limpios = new ArrayList<>(tokens.size());
        List<Object> operandos = new ArrayList<>();

        float x = 0, y = 0, interlineado = 0;

        for (Object token : tokens) {
            if (!(token instanceof Operator operador)) {
                operandos.add(token);
                continue;
            }

            String nombre = operador.getName();
            switch (nombre) {
                case "BT" -> { x = 0; y = 0; }
                case "Tm" -> {
                    if (operandos.size() >= 6) {
                        x = numero(operandos, operandos.size() - 2);
                        y = numero(operandos, operandos.size() - 1);
                    }
                }
                case "Td" -> {
                    if (operandos.size() >= 2) {
                        x += numero(operandos, operandos.size() - 2);
                        y += numero(operandos, operandos.size() - 1);
                    }
                }
                case "TD" -> {
                    if (operandos.size() >= 2) {
                        x += numero(operandos, operandos.size() - 2);
                        float ty = numero(operandos, operandos.size() - 1);
                        y += ty;
                        interlineado = -ty;
                    }
                }
                case "TL" -> {
                    if (!operandos.isEmpty()) {
                        interlineado = numero(operandos, operandos.size() - 1);
                    }
                }
                case "T*" -> y -= interlineado;
                case "'", "\"" -> y -= interlineado;
                default -> { }
            }

            boolean dibujaTexto = nombre.equals("Tj") || nombre.equals("TJ")
                    || nombre.equals("'") || nombre.equals("\"");

            if (dibujaTexto && estaEnUnRenglonQueRota(x, y)) {
                operandos.clear();   // se va el operador y su texto
                continue;
            }

            limpios.addAll(operandos);
            limpios.add(operador);
            operandos.clear();
        }

        PDStream nuevoContenido = new PDStream(documento);
        try (OutputStream out = nuevoContenido.createOutputStream(COSName.FLATE_DECODE)) {
            new ContentStreamWriter(out).writeTokens(limpios);
        }
        pagina.setContents(nuevoContenido);
    }

    private boolean estaEnUnRenglonQueRota(float x, float y) {
        for (int copia = 0; copia < X_COPIA.length; copia++) {
            boolean dentroDeLaCopia = x >= X_COPIA[copia] - 1f
                    && x <= X_COPIA[copia] + ANCHO_COPIA;
            if (!dentroDeLaCopia) continue;

            for (float yRenglon : Y_RENGLON[copia]) {
                if (Math.abs(y - yRenglon) <= TOLERANCIA_Y) return true;
            }
        }
        return false;
    }

    private void escribirPlatillos(PDDocument documento, PDPage pagina, List<Producto> platillos)
            throws IOException {
        try (PDPageContentStream lienzo = new PDPageContentStream(
                documento, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {

            lienzo.setNonStrokingColor(0f, 0f, 0f);

            for (int copia = 0; copia < X_COPIA.length; copia++) {
                for (int renglon = 0; renglon < RENGLONES; renglon++) {
                    // Menos de 5 platillos activos deja el renglón vacío, que es lo
                    // correcto: mejor en blanco que anunciando el platillo de ayer.
                    if (renglon >= platillos.size()) continue;

                    escribirRenglon(lienzo, X_COPIA[copia], Y_RENGLON[copia][renglon],
                            platillos.get(renglon));
                }
            }
        }
    }

    private void escribirRenglon(PDPageContentStream lienzo, float x, float y, Producto platillo)
            throws IOException {
        String nombre = platillo.getNombre().trim().toUpperCase(new Locale("es", "MX"));
        String precio = formatearPrecio(platillo.getPrecio_comida());

        float tamano = tamanoQueQuepa(nombre, precio);

        lienzo.beginText();
        lienzo.setFont(helvetica, tamano);
        lienzo.newLineAtOffset(x, y);
        lienzo.showText(conPuntosDeRelleno(nombre, precio, tamano));
        lienzo.endText();
    }

    /**
     * Baja el tamaño de la letra si el nombre es tan largo que no dejaría hueco ni
     * para los puntos. Así un platillo de nombre kilométrico se aprieta en vez de
     * desbordarse encima del precio.
     */
    private float tamanoQueQuepa(String nombre, String precio) throws IOException {
        for (float tamano = TAMANO_FUENTE; tamano >= TAMANO_FUENTE_MINIMO; tamano -= 0.25f) {
            float necesario = ancho(nombre, tamano) + ancho(precio, tamano) + ancho("..", tamano);
            if (necesario <= ANCHO_LINEA) return tamano;
        }
        return TAMANO_FUENTE_MINIMO;
    }

    private String conPuntosDeRelleno(String nombre, String precio, float tamano)
            throws IOException {
        float disponible = ANCHO_LINEA - ancho(nombre, tamano) - ancho(precio, tamano);
        int cuantos = Math.max(2, Math.round(disponible / ancho(".", tamano)));
        return nombre + ".".repeat(cuantos) + precio;
    }

    private float ancho(String texto, float tamano) throws IOException {
        return helvetica.getStringWidth(texto) / 1000f * tamano;
    }

    private float numero(List<Object> operandos, int indice) {
        Object valor = operandos.get(indice);
        return valor instanceof COSNumber n ? n.floatValue() : 0f;
    }

    /** 115.00 -> "$115"   115.50 -> "$115.50" */
    private String formatearPrecio(BigDecimal precio) {
        BigDecimal limpio = precio.stripTrailingZeros();
        return limpio.scale() <= 0
                ? "$" + limpio.toBigInteger()
                : "$" + limpio.toPlainString();
    }
}
