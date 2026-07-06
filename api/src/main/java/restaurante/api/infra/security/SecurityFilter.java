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
        var authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            var token = authHeader.substring(7).trim();

            try {
                var nombreUsuario = tokenService.getSubject(token);
                if (nombreUsuario != null) {
                    var usuario = repository.findByEmail(nombreUsuario);
                    // isEnabled(): un usuario desactivado pierde el acceso de inmediato,
                    // aunque su token JWT siga siendo criptográficamente válido.
                    if (usuario != null && usuario.isEnabled()) {
                        var authentication = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (RuntimeException e) {
                // Token inválido/expirado: no autenticamos, la cadena decidirá (EntryPoint → 401)
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}