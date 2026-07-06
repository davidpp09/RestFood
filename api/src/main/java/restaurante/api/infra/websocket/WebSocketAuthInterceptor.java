package restaurante.api.infra.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import restaurante.api.infra.security.TokenService;
import restaurante.api.usuario.Usuario;
import restaurante.api.usuario.UsuarioRepository;

/**
 * Autenticación a nivel STOMP: el handshake HTTP de SockJS sigue siendo permitAll,
 * pero el frame CONNECT debe traer un JWT válido (el frontend ya lo envía en
 * connectHeaders.Authorization). Sin token válido no hay sesión STOMP, y por lo
 * tanto tampoco SUBSCRIBE: nadie sin login puede ver /topic/tickets, /topic/cocina
 * o /topic/mesas. Además se bloquea que un cliente publique (SEND) directo a
 * /topic/* — con el simple broker, sin este bloqueo cualquiera podía inyectar
 * tickets falsos al panel de cocina.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // heartbeats y frames internos pasan
        }

        switch (accessor.getCommand()) {
            case CONNECT -> autenticar(accessor);
            case SUBSCRIBE -> {
                if (accessor.getUser() == null) {
                    throw new MessagingException("Suscripción rechazada: sesión no autenticada.");
                }
            }
            case SEND -> {
                String destino = accessor.getDestination();
                if (accessor.getUser() == null) {
                    throw new MessagingException("Envío rechazado: sesión no autenticada.");
                }
                if (destino != null && destino.startsWith("/topic")) {
                    throw new MessagingException("Envío rechazado: los clientes no pueden publicar directo a /topic.");
                }
            }
            default -> { /* DISCONNECT, ACK, etc. pasan */ }
        }
        return message;
    }

    private void autenticar(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Conexión WebSocket rechazada: falta el token.");
        }
        try {
            String email = tokenService.getSubject(header.substring(7).trim());
            var usuario = (Usuario) usuarioRepository.findByEmail(email);
            if (usuario == null || !usuario.isEnabled()) {
                throw new MessagingException("Conexión WebSocket rechazada: usuario inválido o desactivado.");
            }
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    usuario.getUsername(), null, usuario.getAuthorities()));
        } catch (MessagingException e) {
            throw e;
        } catch (RuntimeException e) {
            // Token falso, manipulado o expirado (TokenService lanza RuntimeException)
            throw new MessagingException("Conexión WebSocket rechazada: token inválido o expirado.");
        }
    }
}
