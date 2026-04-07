package io.github.HenriqueMichelini.craftalism.authserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.github.HenriqueMichelini.craftalism.authserver.keys.RsaKeyProperties;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Core Spring Authorization Server configuration.
 *
 * <p>Exposes the standard OAuth2 endpoints:
 * <ul>
 *   <li>POST {@code /oauth2/token}        — issue tokens (client_credentials grant)</li>
 *   <li>POST {@code /oauth2/introspect}   — validate tokens</li>
 *   <li>POST {@code /oauth2/revoke}       — revoke tokens</li>
 *   <li>GET  {@code /oauth2/jwks} — public key set for JWT verification</li>
 *   <li>GET  {@code /.well-known/oauth-authorization-server} — discovery metadata</li>
 * </ul>
 */
@Configuration
public class AuthorizationServerConfig {

    @Value("${auth.issuer-uri}")
    private String issuerUri;

    // ── Security filter chain for Authorization Server endpoints ──────────────

    /**
     * Order(1): handles all Authorization Server protocol endpoints.
     * Any request that doesn't match a protocol endpoint falls through to Order(2).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
        HttpSecurity http
    ) throws Exception {
        // Build the configurer directly — replaces the deprecated applyDefaultSecurity()
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
            OAuth2AuthorizationServerConfigurer.authorizationServer();

        // Scope this filter chain only to the Authorization Server's own endpoints
        // (e.g. /oauth2/token, /oauth2/introspect, /oauth2/jwks)
        RequestMatcher endpointsMatcher =
            authorizationServerConfigurer.getEndpointsMatcher();

        http
            .securityMatcher(endpointsMatcher)
            .with(authorizationServerConfigurer, configurer ->
                // Enable OIDC discovery: /.well-known/openid-configuration
                configurer.oidc(Customizer.withDefaults())
            )
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            // Disable CSRF only for the token endpoints — they use HTTP Basic / bearer,
            // not cookie-based sessions, so CSRF does not apply
            .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher));

        return http.build();
    }

    // ── JDBC repositories — all state lives in PostgreSQL ─────────────────────

    /**
     * Stores registered clients (the Minecraft server) in PostgreSQL.
     * The {@link io.github.HenriqueMichelini.craftalism.authserver.service.ClientRegistrationService}
     * seeds the Minecraft client on startup via this repository.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
        JdbcTemplate jdbcTemplate
    ) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * Stores issued tokens and active authorizations in PostgreSQL.
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(
        JdbcTemplate jdbcTemplate,
        RegisteredClientRepository registeredClientRepository
    ) {
        return new JdbcOAuth2AuthorizationService(
            jdbcTemplate,
            registeredClientRepository
        );
    }

    /**
     * Stores authorization consents in PostgreSQL.
     * Not used for client_credentials, but required by the framework.
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
        JdbcTemplate jdbcTemplate,
        RegisteredClientRepository registeredClientRepository
    ) {
        return new JdbcOAuth2AuthorizationConsentService(
            jdbcTemplate,
            registeredClientRepository
        );
    }

    // ── JWT signing ────────────────────────────────────────────────────────────

    /**
     * JWK source used to sign JWTs.
     * The public key is exposed at {@code /oauth2/jwks} so the API
     * can validate tokens locally without calling the Auth Server.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(RsaKeyProperties keys) {
        RSAKey rsaKey = new RSAKey.Builder(keys.publicKey())
            .privateKey(keys.privateKey())
            .keyID(calculateKeyId(keys))
            .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /**
     * JWT decoder — used internally by the introspection endpoint.
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    // ── Authorization server metadata ─────────────────────────────────────────

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().issuer(issuerUri).build();
    }

    // ── Shared beans ──────────────────────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private String calculateKeyId(RsaKeyProperties keys) {
        try {
            byte[] digest = MessageDigest
                .getInstance("SHA-256")
                .digest(keys.publicKey().getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "JVM does not support SHA-256 for RSA key identifier calculation.",
                exception
            );
        }
    }
}
