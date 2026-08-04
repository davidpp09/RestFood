package restaurante.api.menu;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * HERRAMIENTA, no test de regresión. Está @Disabled a propósito para que no
 * corra en el CI: no comprueba nada, solo imprime dónde está cada renglón de la
 * plantilla del menú.
 *
 * Para qué sirve: si algún día se rehace el PDF en Canva, las coordenadas que
 * usa MenuDiaService dejan de servir. Correr esto vuelve a sacarlas:
 *
 *   ./mvnw test -Dtest=CalibradorPlantillaMenu -Dcalibrar=true -DfailIfNoTests=false
 *
 * y copiar los valores que imprime a las constantes de MenuDiaService.
 * Sin la propiedad -Dcalibrar=true no corre, así que el CI lo ignora.
 */
class CalibradorPlantillaMenu {

    private record Renglon(String texto, float x, float y, float alto, String fuente, float tamano) {}

    /**
     * Volca el texto de la página 2 del PDF YA GENERADO. Sirve para ver cómo queda
     * el renglón cuando el texto nuevo se superpone al viejo que quedó debajo del
     * parche blanco.
     *
     *   ./mvnw test -Dtest=CalibradorPlantillaMenu -Dcalibrar=true -DfailIfNoTests=false
     */
    @Test
    @EnabledIfSystemProperty(named = "calibrar", matches = "true")
    void volcarTextoDelMenuGenerado() throws Exception {
        byte[] generado;

        // Con -Dpdf=<ruta> vuelca un PDF ya existente (por ejemplo el que devolvió
        // el endpoint), en vez de generar uno nuevo. Sirve para comprobar la cadena
        // completa: base de datos -> consulta -> PDF que viaja por HTTP.
        String rutaExterna = System.getProperty("pdf");
        if (rutaExterna != null) {
            generado = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(rutaExterna));
            System.out.println(">>> Leyendo PDF externo: " + rutaExterna);
        } else {
            var servicio = new restaurante.api.menu.MenuDiaService();
            var platillos = List.of(
                    platilloDePrueba("Espinacas a la crema con queso doble crema", "90"),
                    platilloDePrueba("Tortitas de pollo en salsa roja", "100"),
                    platilloDePrueba("Estofado de tocino, zanahoria y cebollitas", "110"),
                    platilloDePrueba("Costilla de cerdo en salsa de mango habanero", "115"),
                    platilloDePrueba("Pechuga rellena en crema de almendra", "115"));
            generado = servicio.generar(platillos);
        }

        System.out.println("\n=== Texto de la pagina 2 del PDF GENERADO (y < 200) ===");
        try (PDDocument documento = Loader.loadPDF(generado)) {
            PDFTextStripper extractor = new PDFTextStripper() {
                @Override
                protected void writeString(String texto, List<TextPosition> posiciones) {
                    if (posiciones.isEmpty()) return;
                    TextPosition p = posiciones.get(0);
                    if (p.getYDirAdj() < 200) {
                        System.out.printf("x=%7.2f y=%7.2f | %s%n",
                                p.getXDirAdj(), p.getYDirAdj(), texto.trim());
                    }
                }
            };
            extractor.setSortByPosition(true);
            extractor.setStartPage(2);
            extractor.setEndPage(2);
            extractor.getText(documento);
        }
    }

    /** Muestra cómo están guardados los renglones en el content stream (un Tj por línea? partidos?). */
    @Test
    @EnabledIfSystemProperty(named = "calibrar", matches = "true")
    void volcarTokensDeTextoDeLaPagina2() throws Exception {
        byte[] pdf;
        try (InputStream in = getClass().getResourceAsStream("/menu/plantilla-menu.pdf")) {
            pdf = in.readAllBytes();
        }

        System.out.println("\n=== Tokens de texto de la pagina 2 (primeros 60) ===");
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            var pagina = documento.getPage(1);
            var parser = new org.apache.pdfbox.pdfparser.PDFStreamParser(pagina);
            List<Object> tokens = parser.parse();

            // El primer renglón que rota está en y=758.19 del espacio de usuario
            // (850.08 - 91.89). Si los Tm traen esa Y directamente, se puede filtrar por
            // coordenada sin pelear con matrices de transformación.
            System.out.println("--- Tm con Y entre 700 y 760 (los 5 renglones que rotan) ---");
            float yTm = -1;
            for (int i = 0; i < tokens.size(); i++) {
                if (!(tokens.get(i) instanceof org.apache.pdfbox.contentstream.operator.Operator op)) continue;
                if (!op.getName().equals("Tm")) continue;
                if (i < 6) continue;
                Object f = tokens.get(i - 1);
                Object e = tokens.get(i - 2);
                if (f instanceof org.apache.pdfbox.cos.COSNumber ny
                        && e instanceof org.apache.pdfbox.cos.COSNumber nx) {
                    if (ny.floatValue() > 700 && ny.floatValue() < 760 && Math.abs(ny.floatValue() - yTm) > 0.1f) {
                        System.out.printf("  Tm  x=%7.2f  y=%7.2f%n", nx.floatValue(), ny.floatValue());
                        yTm = ny.floatValue();
                    }
                }
            }

            int mostrados = 0;
            for (int i = 0; i < tokens.size() && mostrados < 20; i++) {
                Object t = tokens.get(i);
                if (!(t instanceof org.apache.pdfbox.contentstream.operator.Operator op)) continue;
                String nombre = op.getName();
                if (!nombre.equals("Tj") && !nombre.equals("TJ") && !nombre.equals("Tm")
                        && !nombre.equals("Td")) continue;

                StringBuilder operandos = new StringBuilder();
                for (int j = Math.max(0, i - 7); j < i; j++) {
                    Object o = tokens.get(j);
                    if (o instanceof org.apache.pdfbox.contentstream.operator.Operator) continue;
                    if (o instanceof org.apache.pdfbox.cos.COSString s) {
                        operandos.append("STR<").append(s.getString()).append("> ");
                    } else if (o instanceof org.apache.pdfbox.cos.COSArray a) {
                        StringBuilder texto = new StringBuilder();
                        for (var elem : a) {
                            if (elem instanceof org.apache.pdfbox.cos.COSString s2) texto.append(s2.getString());
                        }
                        operandos.append("ARR<").append(texto).append("> ");
                    } else if (o instanceof org.apache.pdfbox.cos.COSNumber n) {
                        operandos.append(String.format("%.2f ", n.floatValue()));
                    }
                }
                System.out.printf("%-4s %s%n", nombre, operandos.toString().trim());
                mostrados++;
            }
        }
    }

    /**
     * Deja un PDF de muestra en el Escritorio para revisarlo a ojo. El test comprueba
     * coordenadas y texto, pero que se VEA bien solo lo dice un humano abriéndolo.
     */
    @Test
    @EnabledIfSystemProperty(named = "calibrar", matches = "true")
    void generarMuestraEnDisco() throws Exception {
        var servicio = new restaurante.api.menu.MenuDiaService();

        // Los 5 que estaban activos en producción el 28 de julio de 2026.
        var platillos = List.of(
                platilloDePrueba("Espinacas a la crema con queso doble crema", "90"),
                platilloDePrueba("Tortitas de pollo en salsa roja", "100"),
                platilloDePrueba("Estofado de tocino, zanahoria y cebollitas", "110"),
                platilloDePrueba("Costilla de cerdo en salsa de mango habanero", "115"),
                platilloDePrueba("Pechuga rellena en crema de almendra", "115"));

        byte[] pdf = servicio.generar(platillos);

        // En target/ y no en el Escritorio: la ruta estaba escrita como "Desktop"
        // y este equipo lo tiene en español ("Escritorio"), así que la herramienta
        // reventaba con NoSuchFileException justo cuando se necesitaba.
        var destino = java.nio.file.Path.of("target", "MUESTRA-menu-del-dia.pdf")
                .toAbsolutePath();
        java.nio.file.Files.createDirectories(destino.getParent());
        java.nio.file.Files.write(destino, pdf);

        System.out.println("\n>>> Muestra generada en: " + destino);
        System.out.println(">>> Abrela para revisar que los 5 renglones se vean bien.");
    }

    private restaurante.api.producto.Producto platilloDePrueba(String nombre, String precio) {
        var datos = new restaurante.api.producto.DatosRegistroProducto(
                nombre, new java.math.BigDecimal(precio), new java.math.BigDecimal(precio), true, 7L);
        return new restaurante.api.producto.Producto(datos, null);
    }

    @Test
    @EnabledIfSystemProperty(named = "calibrar", matches = "true")
    void imprimirCoordenadasDeLaPagina2() throws Exception {
        byte[] pdf;
        try (InputStream in = getClass().getResourceAsStream("/menu/plantilla-menu.pdf")) {
            pdf = in.readAllBytes();
        }

        List<Renglon> renglones = new ArrayList<>();

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFTextStripper extractor = new PDFTextStripper() {
                @Override
                protected void writeString(String texto, List<TextPosition> posiciones) {
                    if (posiciones.isEmpty()) return;
                    TextPosition primera = posiciones.get(0);
                    renglones.add(new Renglon(
                            texto.trim(),
                            primera.getXDirAdj(),
                            primera.getYDirAdj(),
                            primera.getHeightDir(),
                            primera.getFont() != null ? primera.getFont().getName() : "?",
                            primera.getFontSizeInPt()));
                }
            };
            extractor.setSortByPosition(true);
            extractor.setStartPage(2);
            extractor.setEndPage(2);
            extractor.getText(documento);

            System.out.println("=== Tamaño de la página 2 ===");
            var caja = documento.getPage(1).getMediaBox();
            System.out.printf("ancho=%.2f  alto=%.2f%n", caja.getWidth(), caja.getHeight());
        }

        System.out.println("\n=== Renglones del recuadro 'Nuestra Comida del Día' ===");
        System.out.println("(x, y medidos desde la esquina superior izquierda)");
        for (Renglon r : renglones) {
            String t = r.texto().toUpperCase();
            boolean esDelRecuadro = t.contains("MANITAS") || t.contains("POLLO EN SALSA RANCHERA")
                    || t.contains("AGUACATE RELLENO") || t.contains("BISTEC EN SALSA VERDE")
                    || t.contains("FILETE EN SALSA DE MANGO") || t.contains("MILANESA DE POLLO")
                    || t.contains("POZOLE DE POLLO") || t.contains("NUESTRA COMIDA");
            if (esDelRecuadro) {
                System.out.printf("x=%7.2f  y=%7.2f  alto=%5.2f  %-28s %4.1fpt  | %s%n",
                        r.x(), r.y(), r.alto(), r.fuente(), r.tamano(), r.texto());
            }
        }

        System.out.println("\n=== Todo lo que hay arriba de y=260 (para ubicar el recuadro) ===");
        renglones.stream()
                .filter(r -> r.y() < 260)
                .forEach(r -> System.out.printf("x=%7.2f  y=%7.2f  | %s%n", r.x(), r.y(), r.texto()));

        // Ancho que ocupan las líneas originales en Helvetica 9pt. Es el objetivo al
        // que hay que rellenar con puntos para que el precio quede alineado.
        System.out.println("\n=== Ancho de las lineas originales (Helvetica 9pt) ===");
        var helvetica = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
        for (Renglon r : renglones) {
            if (r.y() > 260) continue;
            if (!r.texto().contains("$")) continue;
            float ancho = helvetica.getStringWidth(r.texto()) / 1000 * 9f;
            System.out.printf("ancho=%7.2f  fin_x=%7.2f  | %s%n", ancho, r.x() + ancho, r.texto());
        }
    }
}
