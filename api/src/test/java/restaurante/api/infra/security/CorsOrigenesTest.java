package restaurante.api.infra.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * El 2026-07-25 las tablets dejaron de poder iniciar sesión al migrarlas a
 * https: el backend respondía 403 "Invalid CORS request". La lista de orígenes
 * permitidos solo tenía entradas http, y el navegador manda el origen SIN
 * puerto (`https://192.168.10.100`, con el 443 implícito), forma que los
 * patrones con `:*` no cubren.
 *
 * El test no inventa la lista: la lee del propio application.properties, para
 * que no puedan desincronizarse. Si alguien recorta los orígenes, esto avisa
 * antes de que lo descubra una mesera con la tablet en la mano.
 */
class CorsOrigenesTest {

    /** Lo que de verdad manda el navegador de las tablets, sin puerto explícito. */
    private static final String ORIGEN_TABLETS_HTTPS = "https://192.168.10.100";
    private static final String ORIGEN_TABLETS_HTTP = "http://192.168.10.100";

    private static CorsConfiguration configuracionConLosPatronesDeProduccion() throws IOException {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(patronesPorDefectoDelProperties());
        cors.setAllowCredentials(true);
        return cors;
    }

    /**
     * Extrae el valor por defecto de `cors.allowed-origins`, es decir lo que
     * queda dentro de `${CORS_ORIGINS:...}` cuando no hay variable de entorno.
     * Ese default es el que usan staging y desarrollo.
     */
    private static List<String> patronesPorDefectoDelProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream in = CorsOrigenesTest.class.getResourceAsStream("/application.properties")) {
            assertNotNull(in, "No se encontró application.properties en el classpath");
            props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        String valor = props.getProperty("cors.allowed-origins");
        assertNotNull(valor, "Falta la propiedad cors.allowed-origins");

        int inicioDefault = valor.indexOf(':', valor.indexOf("${")) + 1;
        String porDefecto = valor.substring(inicioDefault, valor.lastIndexOf('}'));
        return Arrays.asList(porDefecto.split(","));
    }

    @Test
    @DisplayName("El origen https de las tablets (sin puerto) se acepta")
    void aceptaElOrigenHttpsDeLasTablets() throws IOException {
        assertEquals(ORIGEN_TABLETS_HTTPS,
                configuracionConLosPatronesDeProduccion().checkOrigin(ORIGEN_TABLETS_HTTPS),
                "Es el origen exacto que manda la tablet por https. Si esto falla, "
                        + "el login devuelve 403 'Invalid CORS request'.");
    }

    @Test
    @DisplayName("El origen http sigue aceptándose: las tablets migran de a poco")
    void aceptaElOrigenHttpDeLasTablets() throws IOException {
        assertEquals(ORIGEN_TABLETS_HTTP,
                configuracionConLosPatronesDeProduccion().checkOrigin(ORIGEN_TABLETS_HTTP),
                "Mientras haya tablets sin migrar, http tiene que seguir funcionando.");
    }

    @Test
    @DisplayName("El frontend de desarrollo (vite en :5173) sigue aceptándose")
    void aceptaElOrigenDeDesarrollo() throws IOException {
        assertEquals("http://localhost:5173",
                configuracionConLosPatronesDeProduccion().checkOrigin("http://localhost:5173"));
    }
}
