package restaurante.api.infra.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Frena los intentos de adivinar contraseñas en /login.
 *
 * Se cuenta POR USUARIO, no por IP, y eso es deliberado: el backend está
 * detrás de Caddy, así que todas las peticiones le llegan desde 127.0.0.1.
 * Bloquear por IP bloquearía a todas las tablets a la vez — el ataque se
 * convertiría en apagar el restaurante entero.
 *
 * El bloqueo es corto a propósito (5 minutos). Suficiente para que probar
 * contraseñas al azar deje de ser viable, y poco para que una mesera que se
 * equivocó cinco veces no se quede fuera media jornada.
 *
 * El registro vive en memoria: se pierde al reiniciar, y no hace falta más.
 * Un ataque real dura minutos, no reinicios.
 */
@Service
public class LimiteIntentosLoginService {

    static final int MAX_INTENTOS = 5;
    static final Duration DURACION_BLOQUEO = Duration.ofMinutes(5);

    private final Clock reloj;
    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    public LimiteIntentosLoginService(Clock reloj) {
        this.reloj = reloj;
    }

    private record Registro(int fallos, Instant ultimoFallo) {}

    /** ¿Este usuario está bloqueado ahora mismo? */
    public boolean estaBloqueado(String usuario) {
        Registro r = registros.get(clave(usuario));
        if (r == null || r.fallos() < MAX_INTENTOS) {
            return false;
        }
        boolean expirado = Instant.now(reloj).isAfter(r.ultimoFallo().plus(DURACION_BLOQUEO));
        if (expirado) {
            registros.remove(clave(usuario));
            return false;
        }
        return true;
    }

    /** Registra un intento fallido. Al llegar a MAX_INTENTOS, empieza el bloqueo. */
    public void registrarFallo(String usuario) {
        registros.compute(clave(usuario), (k, actual) -> {
            int fallos = (actual == null) ? 1 : actual.fallos() + 1;
            return new Registro(fallos, Instant.now(reloj));
        });
    }

    /** Login correcto: se borra el historial de ese usuario. */
    public void registrarExito(String usuario) {
        registros.remove(clave(usuario));
    }

    /** Minutos que le faltan al bloqueo, para poder decírselo a quien lo sufre. */
    public long minutosRestantes(String usuario) {
        Registro r = registros.get(clave(usuario));
        if (r == null) {
            return 0;
        }
        Duration restante = Duration.between(Instant.now(reloj), r.ultimoFallo().plus(DURACION_BLOQUEO));
        return Math.max(1, restante.toMinutes() + 1);
    }

    // Normalizar evita que "Ana@x.com" y "ana@x.com" cuenten por separado
    // y dupliquen los intentos permitidos.
    private String clave(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase();
    }
}
