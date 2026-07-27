package restaurante.api.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caddy termina el TLS y le habla al backend por http en loopback. Sin
 * `server.forward-headers-strategy`, Spring ve `http` y considera CRUZADA una
 * petición que para el navegador de la tablet es del MISMO origen — de ahí que
 * hubiera que mantener listas de orígenes en cors.allowed-origins.
 *
 * Estos tests fijan las dos mitades del arreglo:
 *   1. que la propiedad esté puesta (si alguien la quita, esto avisa), y
 *   2. que con X-Forwarded-Proto la petición deje de contar como CORS.
 *
 * Es un test de unidad a propósito: ejercita el mismo ForwardedHeaderFilter que
 * instala Spring Boot con la estrategia FRAMEWORK, sin levantar el contexto.
 *
 * Ver CorsOrigenesTest: la lista de orígenes se mantiene como red de seguridad
 * y sigue siendo la que protege si algún día se quita esta cabecera.
 */
class ForwardedHeadersTest {

    /** El origen que manda la tablet: https, sin puerto explícito. */
    private static final String ORIGEN_TABLET = "https://192.168.10.100";
    private static final String IP_SERVIDOR = "192.168.10.100";

    /**
     * Pasa la petición por el filtro y devuelve la que ve la aplicación. Es
     * exactamente lo que hace Spring Boot con forward-headers-strategy=FRAMEWORK.
     */
    private static HttpServletRequest trasElFiltro(MockHttpServletRequest peticion) throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        new ForwardedHeaderFilter().doFilter(peticion, new MockHttpServletResponse(), cadena);
        return (HttpServletRequest) cadena.getRequest();
    }

    /** Una petición como la que llega de Caddy: http por dentro, https por fuera. */
    private static MockHttpServletRequest peticionDeUnaTabletPorHttps() {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/login");
        peticion.setScheme("http");
        peticion.setServerName(IP_SERVIDOR);
        peticion.setServerPort(8080);
        peticion.addHeader("Origin", ORIGEN_TABLET);
        // Las tres cabeceras que Caddy pone solo por hacer reverse_proxy.
        peticion.addHeader("X-Forwarded-Proto", "https");
        peticion.addHeader("X-Forwarded-Host", IP_SERVIDOR);
        peticion.addHeader("X-Forwarded-For", "192.168.10.115");
        return peticion;
    }

    @Test
    @DisplayName("Con X-Forwarded-Proto, el backend ve el esquema real (https)")
    void elEsquemaVisibleEsElDeLaTablet() throws Exception {
        assertEquals("https", trasElFiltro(peticionDeUnaTabletPorHttps()).getScheme(),
                "Si esto es http, Spring y cualquier URL que construya el backend "
                        + "creerán que el sitio es http, y el navegador bloqueará por contenido mixto.");
    }

    @Test
    @DisplayName("Con la cabecera, la petición de la tablet deja de ser CORS")
    void dejaDeSerPeticionCruzada() throws Exception {
        assertFalse(CorsUtils.isCorsRequest(trasElFiltro(peticionDeUnaTabletPorHttps())),
                "Es el objetivo del cambio: mismo origen, así que CORS ni se evalúa "
                        + "y la lista de orígenes deja de ser lo que sostiene el login.");
    }

    @Test
    @DisplayName("Sin la cabecera SÍ era CORS — así se veía el fallo del 2026-07-25")
    void sinLaCabeceraEraPeticionCruzada() throws Exception {
        MockHttpServletRequest peticion = peticionDeUnaTabletPorHttps();
        peticion.removeHeader("X-Forwarded-Proto");
        peticion.removeHeader("X-Forwarded-Host");

        assertTrue(CorsUtils.isCorsRequest(trasElFiltro(peticion)),
                "Este es el mundo de antes del arreglo. Si algún día este test falla, "
                        + "es que Spring cambió de criterio y el porqué del cambio ya no aplica.");
    }

    @Test
    @DisplayName("La propiedad forward-headers-strategy sigue configurada")
    void laPropiedadSigueConfigurada() throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(in, "No se encontró application.properties en el classpath");
            props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        String valor = props.getProperty("server.forward-headers-strategy");
        assertNotNull(valor, "Falta server.forward-headers-strategy: sin ella, "
                + "las peticiones de las tablets vuelven a contar como cruzadas.");
        assertTrue(valor.contains("FRAMEWORK"),
                "Se esperaba la estrategia FRAMEWORK, pero está: " + valor);
    }
}
