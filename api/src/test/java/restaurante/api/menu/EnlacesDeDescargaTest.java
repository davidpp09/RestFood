package restaurante.api.menu;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que protege este test: que la puerta sin JWT de {@code /menu-dia/descargar}
 * siga siendo una puerta y no un hueco.
 *
 * Es la única ruta de la API que se abrió sin token de sesión, así que lo que
 * aquí se comprueba no es "que funcione" sino **que siga sin abrirse de más**:
 * que un token inventado no entre, que el que venció deje de entrar, y que dos
 * emisiones no se parezcan.
 */
class EnlacesDeDescargaTest {

    private final EnlacesDeDescarga enlaces = new EnlacesDeDescarga();

    @Test
    void el_token_recien_emitido_es_valido() {
        assertTrue(enlaces.esValido(enlaces.emitir()));
    }

    @Test
    void un_token_inventado_no_entra() {
        assertFalse(enlaces.esValido("un-token-cualquiera"));
        assertFalse(enlaces.esValido(""));
        assertFalse(enlaces.esValido(null));
    }

    @Test
    void el_token_vencido_deja_de_entrar() {
        // Vida negativa: nace vencido, y así el test no espera 3 minutos.
        var yaVencidos = new EnlacesDeDescarga(Duration.ofSeconds(-1));
        assertFalse(yaVencidos.esValido(yaVencidos.emitir()));
    }

    /**
     * A propósito NO es de un solo uso: el gestor de descargas de Android puede
     * repetir la petición y la descarga se vería rota sin motivo. Si alguien lo
     * cambia a un solo uso, que sepa que rompe la tablet del repartidor.
     */
    @Test
    void el_mismo_token_sirve_mientras_no_venza() {
        String token = enlaces.emitir();
        assertTrue(enlaces.esValido(token));
        assertTrue(enlaces.esValido(token));
    }

    @Test
    void dos_emisiones_no_dan_el_mismo_token() {
        String uno = enlaces.emitir();
        String otro = enlaces.emitir();

        assertNotEquals(uno, otro);
        // 32 bytes en Base64 sin relleno = 43 caracteres. Si esto baja, alguien
        // acortó el token y lo volvió adivinable.
        assertEquals(43, uno.length());
    }
}
