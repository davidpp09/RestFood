package restaurante.api.controller.ordenes;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import restaurante.api.infra.security.DatosJWTToken;
import restaurante.api.infra.security.TokenService;
import restaurante.api.usuario.DatosAutenticacionUsuario;
import restaurante.api.usuario.Usuario;

@RestController
@RequestMapping("/login")
public class AutenticacionController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService token;

    @PostMapping
    public ResponseEntity realizarLogin(@RequestBody @Valid DatosAutenticacionUsuario datos) {
        // 1. Empaquetamos las credenciales
        Authentication authToken = new UsernamePasswordAuthenticationToken(datos.email(), datos.contrasena());

        // 2. El manager valida contra la BD y AutenticacionService
        var usuarioAutenticado = authenticationManager.authenticate(authToken);

        // 3. ¡Aquí está el truco!
        // getPrincipal() nos devuelve el objeto Usuario que definimos
        var JWTtoken = token.generarToken((Usuario) usuarioAutenticado.getPrincipal());

        // 4. Devolvemos el token envuelto en nuestro nuevo DTO
        return ResponseEntity.ok(new DatosJWTToken(JWTtoken));
    }
}