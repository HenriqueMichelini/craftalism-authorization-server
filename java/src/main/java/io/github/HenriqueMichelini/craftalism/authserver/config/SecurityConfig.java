package io.github.HenriqueMichelini.craftalism.authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Default security filter chain — Order(2).
 *
 * <p>Intentionally minimal. All Authorization Server beans
 * (filter chain, RegisteredClientRepository, JWKSource, JwtDecoder,
 * AuthorizationServerSettings, PasswordEncoder) live in
 * {@link AuthorizationServerConfig} and
 * {@link io.github.HenriqueMichelini.craftalism.authserver.service.ClientRegistrationService}.
 *
 * <p>This class only covers what the Auth Server protocol filter chain (Order 1)
 * does NOT handle: the health probe and the blanket deny-all rule.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain defaultHttpSecurityFilterChain(HttpSecurity http)
        throws Exception {
        http
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth ->
                auth
                    // Health probe + public auth metadata endpoints
                    .requestMatchers(
                        "/actuator/health",
                        "/oauth2/jwks",
                        "/.well-known/oauth-authorization-server",
                        "/.well-known/openid-configuration"
                    )
                    .permitAll()
                    // Public GET API resources
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/players",
                        "/api/players/**",
                        "/api/balances",
                        "/api/balances/**",
                        "/api/transactions",
                        "/api/transactions/**"
                    )
                    .permitAll()
                    .anyRequest()
                    .denyAll()
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
