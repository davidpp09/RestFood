package restaurante.api.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import restaurante.api.usuario.UsuarioRepository;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository repository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 1. Obtener el token del encabezado "Authorization"
        var authHeader = request.getHeader("Authorization");

        if (authHeader != null) {
            // 2. Limpiar el token (quitar "Bearer ")
            var token = authHeader.replace("Bearer ", "");

            // 3. Validar el token y obtener el "subject" (el email del usuario)
            var nombreUsuario = tokenService.getSubject(token);

            if (nombreUsuario != null) {
                // 4. Si el token es válido, buscamos al usuario en la BD
                var usuario = repository.findByEmail(nombreUsuario);

                // 5. Forzamos el inicio de sesión en el sistema de Spring
                var authentication = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 6. ¡Siga su camino! (Pasa al siguiente filtro o al Controller)
        filterChain.doFilter(request, response);
    }
}