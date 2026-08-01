package restaurante.api.menu;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enlaces de descarga de vida corta para el menú del día.
 *
 * POR QUÉ EXISTE:
 *
 * La API se autentica con un JWT en la cabecera {@code Authorization}, y eso lo
 * puede mandar el JavaScript pero NO una descarga del navegador: cuando el
 * gestor de descargas de Android pide el archivo, lo hace por su cuenta y sin
 * cabeceras nuestras. El primer intento fue esquivarlo bajando el PDF con axios
 * y volviéndolo un {@code blob:}, pero en el WebView de Android **una descarga
 * desde blob: no se dispara nunca** — el {@code DownloadListener} no se entera —
 * y el clic acababa contando como ventana nueva, que en modo kiosko está
 * prohibida. De ahí el mensaje de "popups disabled" que veía el repartidor.
 *
 * La salida es una URL normal que se autentica sola con un token en la query.
 * La pantalla, ya autenticada, pide el enlace; el navegador lo abre como una
 * descarga cualquiera.
 *
 * POR QUÉ ES ACEPTABLE:
 *
 *   - El token es aleatorio de 256 bits ({@link SecureRandom}), no adivinable.
 *   - Vive 3 minutos: el tiempo de pulsar Descargar, no más.
 *   - Solo abre el menú del día, que es justamente el papel que se le da a los
 *     clientes. No da acceso a nada más de la API.
 *
 * POR QUÉ NO ES DE UN SOLO USO: el gestor de descargas de Android puede repetir
 * la petición (reintento, o un HEAD antes del GET). Con un solo uso, el segundo
 * intento fallaría y la descarga se vería rota sin motivo aparente. Se prefirió
 * acotar por tiempo, que es lo que de verdad limita la exposición.
 */
@Component
public class EnlacesDeDescarga {

    private static final Duration VIDA_POR_DEFECTO = Duration.ofMinutes(3);

    /**
     * Tope de tokens vivos. El mapa se limpia solo al emitir, pero un tope evita
     * que un fallo en bucle lo haga crecer sin freno.
     */
    private static final int MAXIMO_VIVOS = 100;

    private final Map<String, Instant> vencimientos = new ConcurrentHashMap<>();
    private final SecureRandom aleatorio = new SecureRandom();
    private final Duration vida;

    public EnlacesDeDescarga() {
        this(VIDA_POR_DEFECTO);
    }

    /** Para el test: poder emitir un token que ya nazca vencido sin esperar 3 minutos. */
    EnlacesDeDescarga(Duration vida) {
        this.vida = vida;
    }

    /** Emite un token nuevo y devuelve su texto para ponerlo en la URL. */
    public String emitir() {
        purgarVencidos();
        if (vencimientos.size() >= MAXIMO_VIVOS) {
            throw new IllegalStateException("Demasiados enlaces de descarga vivos");
        }

        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        vencimientos.put(token, Instant.now().plus(vida));
        return token;
    }

    /** ¿Este token sigue vivo? Los vencidos se descartan al consultarlos. */
    public boolean esValido(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Instant vence = vencimientos.get(token);
        if (vence == null) {
            return false;
        }
        if (Instant.now().isAfter(vence)) {
            vencimientos.remove(token);
            return false;
        }
        return true;
    }

    private void purgarVencidos() {
        Instant ahora = Instant.now();
        vencimientos.entrySet().removeIf(e -> ahora.isAfter(e.getValue()));
    }
}
