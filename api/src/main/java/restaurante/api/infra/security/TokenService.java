package restaurante.api.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import restaurante.api.usuario.Usuario;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class TokenService {

    @Value("${api.security.secret}")
    private String apiSecret;

    public String generarToken(Usuario usuario) {
        try {
            Algorithm algoritmo = Algorithm.HMAC256(apiSecret);

            return JWT.create()
                    .withIssuer("restfood")
                    .withSubject(usuario.getEmail())
                    .withClaim("id", usuario.getId_usuarios())
                    .withClaim("role", usuario.getRol().toString())
                    .withClaim("seccion", usuario.getSeccion())
                    .withExpiresAt(generarFechaExpiracion())
                    .sign(algoritmo);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Error al generar el token JWT", exception);
        }
    }

    private Instant generarFechaExpiracion() {
        // El token durará 2 horas por seguridad
        return LocalDateTime.now().plusHours(12).toInstant(ZoneOffset.of("-06:00"));
    }

    public String getSubject(String token) {
        if (token == null) {
            throw new RuntimeException("El token no puede ser nulo");
        }

        try {
            // 1. Definimos el mismo algoritmo con nuestra llave secreta
            Algorithm algoritmo = Algorithm.HMAC256(apiSecret);

            // 2. Creamos el verificador que exige que el emisor sea "restfood"
            return JWT.require(algoritmo)
                    .withIssuer("restfood")
                    .build()
                    .verify(token) // Aquí es donde ocurre la magia de la validación ✨
                    .getSubject(); // Extraemos el email

        } catch (JWTVerificationException exception) {
            // Si el token es falso, expiró o fue manipulado, lanzamos un error
            throw new RuntimeException("Token JWT inválido o expirado", exception);
        }
    }
}