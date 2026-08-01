package restaurante.api.menu;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import restaurante.api.producto.DatosRegistroProducto;
import restaurante.api.producto.Producto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que protege este test: que el PDF del menú siga saliendo bien el día que
 * alguien toque el servicio o se regenere la plantilla en Canva.
 *
 * Comprueba tres cosas distintas, porque son tres formas distintas de romperlo:
 *   1. Que los platillos nuevos aparezcan, con su precio y en mayúsculas.
 *   2. Que aparezcan en las MISMAS coordenadas que los renglones originales
 *      (si se desalinean, el menú sale con el texto encima de otra línea).
 *   3. Que los renglones fijos y la página 1 sigan intactos.
 */
class MenuDiaServiceTest {

    private final MenuDiaService servicio = new MenuDiaService();

    private record Renglon(String texto, float x, float y) {}

    private Producto platillo(String nombre, String precio) {
        var datos = new DatosRegistroProducto(
                nombre, new BigDecimal(precio), new BigDecimal(precio), true, 7L);
        return new Producto(datos, null);
    }

    /** Los 5 que estaban activos en producción el 28 de julio de 2026. */
    private List<Producto> cincoPlatillos() {
        return List.of(
                platillo("Espinacas a la crema con queso doble crema", "90"),
                platillo("Tortitas de pollo en salsa roja", "100"),
                platillo("Estofado de tocino, zanahoria y cebollitas", "110"),
                platillo("Costilla de cerdo en salsa de mango habanero", "115"),
                platillo("Pechuga rellena en crema de almendra", "115"));
    }

    private List<Renglon> renglonesDePagina(byte[] pdf, int pagina) throws Exception {
        List<Renglon> renglones = new ArrayList<>();
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFTextStripper extractor = new PDFTextStripper() {
                @Override
                protected void writeString(String texto, List<TextPosition> posiciones) {
                    if (posiciones.isEmpty()) return;
                    TextPosition primera = posiciones.get(0);
                    renglones.add(new Renglon(
                            texto.trim(), primera.getXDirAdj(), primera.getYDirAdj()));
                }
            };
            extractor.setSortByPosition(true);
            extractor.setStartPage(pagina);
            extractor.setEndPage(pagina);
            extractor.getText(documento);
        }
        return renglones;
    }

    // 🧪 TEST 1: los 5 platillos salen con su precio, en mayúsculas y en las dos copias
    @Test
    void generar_EscribeLosCincoPlatillosEnLasDosCopiasDelDisenio() throws Exception {
        byte[] pdf = servicio.generar(cincoPlatillos());

        List<Renglon> renglones = renglonesDePagina(pdf, 2);

        record Esperado(String nombre, String precio) {}
        var esperados = List.of(
                new Esperado("ESPINACAS A LA CREMA CON QUESO DOBLE CREMA", "$90"),
                new Esperado("TORTITAS DE POLLO EN SALSA ROJA", "$100"),
                new Esperado("ESTOFADO DE TOCINO, ZANAHORIA Y CEBOLLITAS", "$110"),
                new Esperado("COSTILLA DE CERDO EN SALSA DE MANGO HABANERO", "$115"),
                new Esperado("PECHUGA RELLENA EN CREMA DE ALMENDRA", "$115"));

        for (Esperado esperado : esperados) {
            long veces = renglones.stream()
                    .filter(r -> r.texto().startsWith(esperado.nombre())
                              && r.texto().endsWith(esperado.precio()))
                    .count();
            assertEquals(2, veces,
                    "El diseño va duplicado lado a lado, así que '" + esperado.nombre()
                    + "' debe aparecer 2 veces y apareció " + veces);
        }
    }

    // 🧪 TEST 2: van en las mismas coordenadas que los renglones de la plantilla.
    // Si esto falla, el texto sale desalineado o encima de otro renglón.
    @Test
    void generar_RespetaLasCoordenadasDeLaPlantilla() throws Exception {
        byte[] pdf = servicio.generar(cincoPlatillos());
        List<Renglon> renglones = renglonesDePagina(pdf, 2);

        // Calibradas con CalibradorPlantillaMenu: copia izquierda y copia derecha.
        float[] xEsperada = {19.86f, 317.63f};
        float[] yPrimera = {91.89f, 93.47f};
        float interlinea = 13.5555f;

        for (int copia = 0; copia < 2; copia++) {
            for (int renglon = 0; renglon < MenuDiaService.RENGLONES; renglon++) {
                float x = xEsperada[copia];
                float y = yPrimera[copia] + renglon * interlinea;

                boolean hayTextoNuevoAhi = renglones.stream().anyMatch(r ->
                        Math.abs(r.x() - x) < 0.5f
                        && Math.abs(r.y() - y) < 0.5f
                        && r.texto().contains("$")
                        && r.texto().matches("^[A-ZÁÉÍÓÚÑ].*"));

                assertTrue(hayTextoNuevoAhi,
                        "Falta el renglón " + (renglon + 1) + " de la copia " + (copia + 1)
                        + " en x=" + x + " y=" + y);
            }
        }
    }

    // 🧪 TEST 3: no se rompió nada más del menú
    @Test
    void generar_NoTocaLosRenglonesFijosNiLaPrimeraPagina() throws Exception {
        byte[] pdf = servicio.generar(cincoPlatillos());

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertEquals(2, documento.getNumberOfPages(), "El menú debe seguir teniendo 2 páginas");
        }

        String pagina2 = String.join(" | ",
                renglonesDePagina(pdf, 2).stream().map(Renglon::texto).toList());

        // Del renglón 6 al 13 son platillos fijos: deben seguir ahí.
        assertTrue(pagina2.contains("POZOLE DE POLLO O CERDO"), "Se perdió el pozole (renglón fijo)");
        assertTrue(pagina2.contains("CHILE EN NOGADA"), "Se perdió el chile en nogada (renglón fijo)");
        assertTrue(pagina2.contains("MOJARRA FRITA CON ENSALADA"), "Se perdió la mojarra (renglón fijo)");

        String pagina1 = String.join(" | ",
                renglonesDePagina(pdf, 1).stream().map(Renglon::texto).toList());
        assertTrue(pagina1.contains("Desayunos") || pagina1.contains("Antojitos"),
                "La página 1 (desayunos) no debe tocarse");
    }

    // 🧪 TEST 4: con menos de 5 activos el renglón queda vacío, no con el platillo de ayer
    @Test
    void generar_ConMenosDeCincoDejaLosRenglonesSobrantesEnBlanco() throws Exception {
        var dos = List.of(
                platillo("Pollo en salsa ranchera", "100"),
                platillo("Bistec en salsa verde con nopales", "105"));

        byte[] pdf = servicio.generar(dos);
        List<Renglon> renglones = renglonesDePagina(pdf, 2);

        // Los renglones 3, 4 y 5 de la copia izquierda deben quedar sin nada escrito.
        float[] yVacios = {119.00f, 132.56f, 146.11f};
        for (float y : yVacios) {
            boolean hayAlgo = renglones.stream().anyMatch(r ->
                    Math.abs(r.x() - 19.86f) < 1f
                    && Math.abs(r.y() - y) < 1f
                    && !r.texto().isBlank());
            assertFalse(hayAlgo,
                    "El renglón en y=" + y + " debía quedar en blanco al haber solo 2 platillos");
        }
    }

    // 🧪 TEST 5: en el recuadro del día no queda nada más que el menú de hoy.
    //
    // Desde la plantilla del 2026-08-01, Canva ya NO exporta los 5 renglones de
    // relleno: el recuadro viene vacío y el servicio solo escribe. Antes venían
    // con platillos de ejemplo y había que borrarlos, y este test comprobaba ese
    // borrado. Como esa comprobación se quedaría sin nada que vigilar, aquí se
    // comprueba lo que de verdad importa y sigue pudiendo romperse: que cada
    // platillo del día salga UNA sola vez.
    //
    // Si algún día vuelve una plantilla con relleno y el borrado falla, el texto
    // viejo y el nuevo quedan superpuestos: el PDF se ve casi bien, pero al
    // copiarlo salen los dos menús entreverados. Eso es lo que se caza aquí.
    @Test
    void generar_DejaSoloElMenuDeHoyEnElRecuadro() throws Exception {
        byte[] pdf = servicio.generar(cincoPlatillos());

        List<String> renglones = renglonesDePagina(pdf, 2).stream().map(Renglon::texto).toList();
        String pagina2 = String.join(" | ", renglones);

        for (Producto platillo : cincoPlatillos()) {
            String enMayusculas = platillo.getNombre().toUpperCase();
            long veces = renglones.stream().filter(r -> r.contains(enMayusculas)).count();

            // 2 y no 1: el diseño va duplicado lado a lado para imprimir y cortar.
            assertEquals(2, veces,
                    "'" + enMayusculas + "' debería salir 2 veces (una por copia) y salió " + veces);
        }

        // Red de seguridad por si vuelve una plantilla con platillos de relleno.
        List<String> deLaPlantillaVieja = List.of(
                "MANITAS DE CERDO EN MORITA",
                "AGUACATE RELLENO DE ENSALADA RUSA",
                "MILANESA DE POLLO A LA HAWAIANA");

        for (String viejo : deLaPlantillaVieja) {
            assertFalse(pagina2.contains(viejo),
                    "Quedó texto de una plantilla anterior en el PDF: '" + viejo + "'");
        }
    }

    // 🧪 TEST 6: la vista previa en PNG sale de verdad.
    // Existe porque el WebView de Android no dibuja PDFs y la vista previa salía
    // en blanco en la tablet del repartidor. Si esto se rompe, vuelve el recuadro
    // vacío — y como no da error, nadie se entera hasta que alguien lo mira.
    @Test
    void generarImagen_DevuelveUnPngDeLaPaginaDelMenu() throws Exception {
        byte[] png = servicio.generarImagen(cincoPlatillos());

        // Firma de un PNG: 0x89 'P' 'N' 'G'. Comprueba que salió una imagen de
        // verdad y no, por ejemplo, el PDF sin convertir.
        assertTrue(png.length > 4, "La imagen salió vacía");
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);

        var imagen = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        assertTrue(imagen != null, "El PNG no se pudo volver a leer como imagen");

        // A 150 DPI una hoja carta da del orden de 1275×1650. No se fija el número
        // exacto —depende del tamaño de la plantilla— pero sí que no salga una
        // miniatura ilegible en la tablet.
        assertTrue(imagen.getWidth() > 800,
                "La vista previa salió demasiado chica para leerse: " + imagen.getWidth() + "px de ancho");
    }
}
