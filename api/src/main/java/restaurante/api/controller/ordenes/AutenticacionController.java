package restaurante.api.controller.ordenes;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import restaurante.api.infra.security.DatosLoginRespuesta;
import restaurante.api.infra.security.LimiteIntentosLoginService;
import restaurante.api.infra.security.RoutingService;
import restaurante.api.infra.security.TokenService;
import restaurante.api.usuario.DatosAutenticacionUsuario;
import restaurante.api.usuario.Usuario;

import java.util.Map;

@RestController
@RequestMapping("/login")
public class AutenticacionController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService token;

    @Autowired
    private RoutingService routingService;

    @Autowired
    private LimiteIntentosLoginService limiteIntentos;

    @PostMapping
    public ResponseEntity realizarLogin(@RequestBody @Valid DatosAutenticacionUsuario datos) {
        // Freno a la adivinación de contraseñas: se comprueba ANTES de tocar la
        // base, para que un ataque tampoco sirva para saturar MySQL.
        if (limiteIntentos.estaBloqueado(datos.email())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", "Demasiados intentos fallidos",
                    "mensaje", "Espera " + limiteIntentos.minutosRestantes(datos.email())
                            + " minuto(s) antes de volver a intentar."
            ));
        }

        Authentication authToken = new UsernamePasswordAuthenticationToken(datos.email(), datos.contrasena());

        Usuario usuario;
        try {
            var usuarioAutenticado = authenticationManager.authenticate(authToken);
            usuario = (Usuario) usuarioAutenticado.getPrincipal();
        } catch (AuthenticationException e) {
            limiteIntentos.registrarFallo(datos.email());
            throw e;
        }

        limiteIntentos.registrarExito(datos.email());

        String jwt = token.generarToken(usuario);
        String destino = routingService.rutaPorRol(usuario.getRol());

        return ResponseEntity.ok(new DatosLoginRespuesta(
                jwt,
                usuario.getRol().name(),
                usuario.getNombre(),
                usuario.getId_usuarios(),
                usuario.getSeccion(),
                destino
        ));
    }
}
