package restaurante.api.infra.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El reloj se inyecta para poder adelantar el tiempo sin que el test espere
 * 5 minutos de verdad. Un test que duerme es un test que nadie corre.
 */
class LimiteIntentosLoginServiceTest {

    private static final String USUARIO = "mesera@restfood.com";

    /** Reloj movible: empieza en un instante fijo y avanza cuando se le pide. */
    private static class RelojFalso extends Clock {
        private Instant ahora = Instant.parse("2026-07-24T20:00:00Z");

        void avanzar(Duration d) { ahora = ahora.plus(d); }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    @Test
    @DisplayName("Con menos fallos que el límite, el usuario sigue pudiendo entrar")
    void bajoDelLimite_NoBloquea() {
        var reloj = new RelojFalso();
        var servicio = new LimiteIntentosLoginService(reloj);

        for (int i = 0; i < LimiteIntentosLoginService.MAX_INTENTOS - 1; i++) {
            servicio.registrarFallo(USUARIO);
        }

        assertFalse(servicio.estaBloqueado(USUARIO));
    }

    @Test
    @DisplayName("Al alcanzar el límite de fallos, el usuario queda bloqueado")
    void alLimite_Bloquea() {
        var reloj = new RelojFalso();
        var servicio = new LimiteIntentosLoginService(reloj);

        for (int i = 0; i < LimiteIntentosLoginService.MAX_INTENTOS; i++) {
            servicio.registrarFallo(USUARIO);
        }

        assertTrue(servicio.estaBloqueado(USUARIO));
    }

    @Test
    @DisplayName("El bloqueo se levanta solo cuando pasa el tiempo")
    void pasadoElTiempo_SeDesbloquea() {
        var reloj = new RelojFalso();
        var servicio = new LimiteIntentosLoginService(reloj);
        for (int i = 0; i < LimiteIntentosLoginService.MAX_INTENTOS; i++) {
            servicio.registrarFallo(USUARIO);
        }
        assertTrue(servicio.estaBloqueado(USUARIO));

        reloj.avanzar(LimiteIntentosLoginService.DURACION_BLOQUEO.plusSeconds(1));

        assertFalse(servicio.estaBloqueado(USUARIO));
    }

    @Test
    @DisplayName("Un login correcto borra los fallos anteriores")
    void exito_LimpiaElHistorial() {
        var reloj = new RelojFalso();
        var servicio = new LimiteIntentosLoginService(reloj);
        for (int i = 0; i < LimiteIntentosLoginService.MAX_INTENTOS - 1; i++) {
            servicio.registrarFallo(USUARIO);
        }

        servicio.registrarExito(USUARIO);
        servicio.registrarFallo(USUARIO);

        assertFalse(servicio.estaBloqueado(USUARIO));
    }

    @Test
    @DisplayName("Bloquear a un usuario no deja fuera a los demás")
    void bloqueo_NoAfectaAOtrosUsuarios() {
        var reloj = new RelojFalso();
        var servicio = new LimiteIntentosLoginService(reloj);
        for (int i = 0; i < LimiteIntentosLoginService.MAX_INTENTOS; i++) {
            servicio.registrarFallo(USUARIO);
        }

        assertTrue(servicio.estaBloqueado(USUARIO));
        assertFalse(servicio.estaBloqueado("cocinero@restfood.com"));
    }

    @Test
    @DisplayName("Mayúsculas y espacios no sirven para saltarse el límite")
    void mismoUsuarioDistintaEscritura_CuentaIgual() {
        var reloj = new RelojFalso();
        var servicio = new LimiteIntentosLoginService(reloj);

        servicio.registrarFallo("  MESERA@restfood.com ");
        servicio.registrarFallo("Mesera@RestFood.com");
        servicio.registrarFallo(USUARIO);
        servicio.registrarFallo(USUARIO);
        servicio.registrarFallo(USUARIO);

        assertTrue(servicio.estaBloqueado(USUARIO));
    }
}
