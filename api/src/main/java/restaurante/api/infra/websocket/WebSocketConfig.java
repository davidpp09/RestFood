package restaurante.api.infra.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed-origins:*}")
    private String[] origenesPermitidos;

    @Autowired
    private WebSocketAuthInterceptor authInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 📮 Buzón para que el servidor envíe mensajes al cliente (ej: /topic/cocina)
        config.enableSimpleBroker("/topic");

        // 📥 Prefijo para mensajes que el cliente envía al servidor
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Valida el JWT en el CONNECT y bloquea SUBSCRIBE/SEND anónimos (ver WebSocketAuthInterceptor)
        registration.interceptors(authInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 🔌 Acepta orígenes desde propiedad cors.allowed-origins (patrón, p. ej. http://192.168.*.*:5173)
        registry.addEndpoint("/ws-restfood")
                .setAllowedOriginPatterns(origenesPermitidos)
                .withSockJS();
    }

}
