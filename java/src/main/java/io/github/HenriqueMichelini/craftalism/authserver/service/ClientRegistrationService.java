package io.github.HenriqueMichelini.craftalism.authserver.service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
 * Seeds configured OAuth2 client registrations into PostgreSQL
 * on application startup — only if the clients don't already exist.
 *
 * <p>This is idempotent: re-deploying the service will not create duplicates
 * or overwrite an existing registration.
 *
 * <p>The seeded service clients authenticate using the
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4">
 * client_credentials</a> grant, which is designed exactly for this use case:
 * machine-to-machine authentication with no human user involved.
 */
@Service
public class ClientRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(
        ClientRegistrationService.class
    );
    private static final PasswordEncoder LEGACY_BCRYPT_PASSWORD_ENCODER =
        new BCryptPasswordEncoder();
    private static final Set<ClientAuthenticationMethod> CONFIDENTIAL_CLIENT_AUTH_METHODS =
        Set.of(
            ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
            ClientAuthenticationMethod.CLIENT_SECRET_POST
        );
    private static final Set<AuthorizationGrantType> CLIENT_CREDENTIALS_GRANT_TYPES =
        Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS);
    private static final Set<String> API_SCOPES = Set.of("api:read", "api:write");

    private final RegisteredClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${minecraft.client.id}")
    private String minecraftClientId;

    @Value("${minecraft.client.secret}")
    private String minecraftClientSecret;

    @Value("${dashboard.bff.client.id}")
    private String dashboardBffClientId;

    @Value("${dashboard.bff.client.secret:}")
    private String dashboardBffClientSecret;

    public ClientRegistrationService(
        RegisteredClientRepository clientRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void registerConfiguredClients() {
        registerMinecraftServerClient();
        registerDashboardBffClient();
    }

    public void registerMinecraftServerClient() {
        registerConfidentialClient(
            minecraftClientId,
            minecraftClientSecret,
            "Minecraft Game Server",
            "minecraft.client.secret"
        );
    }

    public void registerDashboardBffClient() {
        if (dashboardBffClientSecret == null || dashboardBffClientSecret.isEmpty()) {
            log.info(
                "Dashboard BFF client '{}' not registered because dashboard.bff.client.secret is not configured.",
                dashboardBffClientId
            );
            return;
        }

        registerConfidentialClient(
            dashboardBffClientId,
            dashboardBffClientSecret,
            "Dashboard BFF",
            "dashboard.bff.client.secret"
        );
    }

    private void registerConfidentialClient(
        String clientId,
        String clientSecret,
        String clientName,
        String clientSecretPropertyName
    ) {
        String normalizedClientSecret = normalizeAndValidateClientSecret(
            clientSecret,
            clientSecretPropertyName
        );
        RegisteredClient existingClient = clientRepository.findByClientId(clientId);

        if (existingClient != null) {
            reconcileExistingClient(existingClient, normalizedClientSecret);
            return;
        }

        RegisteredClient confidentialClient = RegisteredClient.withId(
            UUID.randomUUID().toString()
        )
            .clientId(clientId)
            .clientName(clientName)
            // Secret is bcrypt-hashed before storage
            .clientSecret(passwordEncoder.encode(normalizedClientSecret))
            // CLIENT_SECRET_BASIC: credentials sent via HTTP Basic Auth header
            // This is the most interoperable and widely supported method
            .clientAuthenticationMethod(
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC
            )
            // CLIENT_SECRET_POST: credentials sent in form body
            .clientAuthenticationMethod(
                ClientAuthenticationMethod.CLIENT_SECRET_POST
            )
            // client_credentials: machine-to-machine, no user involved
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            // Scopes the service client is allowed to request
            .scope("api:read")
            .scope("api:write")
            .tokenSettings(
                TokenSettings.builder()
                    // Self-contained JWT — the API validates it locally
                    // without calling the Auth Server on every request
                    .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                    // Short lifetime: limits the damage window if a token is
                    // intercepted. The client must re-authenticate after expiry.
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

        clientRepository.save(confidentialClient);
        log.info("Client '{}' registered successfully.", clientId);
    }

    private String normalizeAndValidateClientSecret(
        String clientSecret,
        String propertyName
    ) {
        if (clientSecret == null) {
            throw new IllegalStateException(
                propertyName + " must be provided."
            );
        }

        String normalizedSecret = clientSecret.trim();
        if (normalizedSecret.isBlank()) {
            throw new IllegalStateException(
                propertyName + " must not be blank."
            );
        }

        return normalizedSecret;
    }

    private void reconcileExistingClient(
        RegisteredClient existingClient,
        String normalizedClientSecret
    ) {
        boolean secretDrift = !secretMatchesConfigured(
            normalizedClientSecret,
            existingClient.getClientSecret()
        );

        boolean authMethodDrift = !existingClient
            .getClientAuthenticationMethods()
            .equals(
                CONFIDENTIAL_CLIENT_AUTH_METHODS
            );

        boolean grantTypeDrift = !existingClient
            .getAuthorizationGrantTypes()
            .equals(CLIENT_CREDENTIALS_GRANT_TYPES);

        boolean scopeDrift = !existingClient.getScopes().equals(API_SCOPES);

        if (!secretDrift && !authMethodDrift && !grantTypeDrift && !scopeDrift) {
            log.info(
                "Client '{}' already registered and in sync.",
                existingClient.getClientId()
            );
            return;
        }

        RegisteredClient.Builder clientBuilder = RegisteredClient.from(
            existingClient
        );

        if (secretDrift) {
            clientBuilder.clientSecret(passwordEncoder.encode(normalizedClientSecret));
        }

        if (authMethodDrift) {
            clientBuilder.clientAuthenticationMethods(methods -> {
                methods.clear();
                methods.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
                methods.add(ClientAuthenticationMethod.CLIENT_SECRET_POST);
            });
        }

        if (grantTypeDrift) {
            clientBuilder.authorizationGrantTypes(types -> {
                types.clear();
                types.add(AuthorizationGrantType.CLIENT_CREDENTIALS);
            });
        }

        if (scopeDrift) {
            clientBuilder.scopes(scopes -> {
                scopes.clear();
                scopes.addAll(API_SCOPES);
            });
        }

        clientRepository.save(clientBuilder.build());
        log.info(
            "Client '{}' reconciled with configured seed contract.",
            existingClient.getClientId()
        );
    }

    private boolean secretMatchesConfigured(
        String rawSecret,
        String storedSecret
    ) {
        try {
            return passwordEncoder.matches(rawSecret, storedSecret);
        } catch (IllegalArgumentException exception) {
            if (storedSecret != null && storedSecret.startsWith("$2")) {
                return LEGACY_BCRYPT_PASSWORD_ENCODER.matches(
                    rawSecret,
                    storedSecret
                );
            }

            return false;
        }
    }
}
