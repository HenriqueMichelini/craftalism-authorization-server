package io.github.HenriqueMichelini.craftalism.authserver.service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;

/**
 * Seeds the Minecraft server's OAuth2 client registration into PostgreSQL
 * on application startup — only if the client doesn't already exist.
 *
 * <p>This is idempotent: re-deploying the service will not create duplicates
 * or overwrite an existing registration.
 *
 * <p>The Minecraft server authenticates using the
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4">
 * client_credentials</a> grant, which is designed exactly for this use case:
 * machine-to-machine authentication with no human user involved.
 */
@Service
public class ClientRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(
        ClientRegistrationService.class
    );

    private final RegisteredClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${minecraft.client.id}")
    private String clientId;

    @Value("${minecraft.client.secret}")
    private String clientSecret;

    public ClientRegistrationService(
        RegisteredClientRepository clientRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void registerMinecraftServerClient() {
        if (clientRepository.findByClientId(clientId) != null) {
            log.info("Client '{}' already registered — skipping.", clientId);
            return;
        }

        RegisteredClient minecraftServer = RegisteredClient.withId(
            UUID.randomUUID().toString()
        )
            .clientId(clientId)
            .clientName("Minecraft Game Server")
            // Secret is bcrypt-hashed before storage
            .clientSecret(passwordEncoder.encode(clientSecret))
            // CLIENT_SECRET_BASIC: credentials sent via HTTP Basic Auth header
            // This is the most interoperable and widely supported method
            .clientAuthenticationMethod(
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC
            )
            // client_credentials: machine-to-machine, no user involved
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            // Scopes the Minecraft server is allowed to request
            .scope("api:read")
            .scope("api:write")
            .tokenSettings(
                TokenSettings.builder()
                    // Self-contained JWT — the API validates it locally
                    // without calling the Auth Server on every request
                    .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                    // Short lifetime: limits the damage window if a token is
                    // intercepted. The plugin must re-authenticate after expiry.
                    .accessTokenTimeToLive(Duration.ofMinutes(5))
                    .build()
            )
            .clientSettings(
                ClientSettings.builder()
                    // No user consent screen needed for service clients
                    .requireAuthorizationConsent(false)
                    .build()
            )
            .build();

        clientRepository.save(minecraftServer);
        log.info("Client '{}' registered successfully.", clientId);
    }
}
