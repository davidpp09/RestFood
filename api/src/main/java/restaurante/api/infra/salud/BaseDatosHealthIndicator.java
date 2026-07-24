package restaurante.api.infra.salud;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Comprueba que la aplicación puede LEER SUS DATOS, no solo que la conexión
 * a MySQL sigue abierta.
 *
 * El indicador que trae Spring por defecto usa Connection.isValid(): pregunta
 * si el socket sigue vivo. Se comprobó en staging que eso devuelve UP incluso
 * después de borrar la base entera — la conexión del pool seguía abierta.
 *
 * Este hace una consulta real. Si la tabla no está, si los permisos cambiaron
 * o si la base desapareció, responde DOWN y /actuator/health devuelve HTTP 503.
 */
@Component("basedatos")
public class BaseDatosHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbc;

    public BaseDatosHealthIndicator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Health health() {
        try {
            Integer productos = jdbc.queryForObject("SELECT COUNT(*) FROM productos", Integer.class);
            return Health.up()
                    .withDetail("productos", productos)
                    .build();
        } catch (Exception e) {
            // Sin el mensaje completo: puede traer nombres de tablas y usuarios,
            // y este endpoint se sirve sin autenticación.
            return Health.down()
                    .withDetail("fallo", e.getClass().getSimpleName())
                    .build();
        }
    }
}
