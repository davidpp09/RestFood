package restaurante.api.infra.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfigurations {

    @Autowired
    private SecurityFilter securityFilter; // 1. Inyectamos tu filtro (el guardia)

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(req -> {
                    // 1. Lo que es público para todos 🔓
                    req.requestMatchers(HttpMethod.POST, "/login").permitAll();
                    req.requestMatchers(HttpMethod.POST, "/usuarios").permitAll();

                    // 2. Reglas por Rol (Sala VIP) 🛡️
                    // Solo ADMIN y DEV pueden entrar a cualquier ruta que empiece con /admin
                    req.requestMatchers("/admin/**").hasAnyRole("ADMIN", "DEV");
                    req.requestMatchers("/usuarios/**").hasAnyRole("ADMIN", "DEV", "CAJERO");
                    req.requestMatchers(HttpMethod.GET, "/productos/**").authenticated();
                    req.requestMatchers("/productos/**").hasAnyRole("ADMIN", "DEV");
                    // 3. Todo lo demás (Solo con estar logueado basta) 🔑
                    req.anyRequest().authenticated();
                })
                // 2. ¡ESTA ES LA LÍNEA MÁGICA!
                // Le decimos: "Ejecuta mi filtro ANTES del filtro de login de Spring"
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}