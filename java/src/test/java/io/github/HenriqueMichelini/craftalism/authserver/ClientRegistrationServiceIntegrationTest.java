package io.github.HenriqueMichelini.craftalism.authserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.HenriqueMichelini.craftalism.authserver.service.ClientRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClientRegistrationServiceIntegrationTest {

    @Autowired
    private ClientRegistrationService clientRegistrationService;

    @Autowired
    private RegisteredClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registerMinecraftServerClient_reconcilesSecretDriftForExistingClient() {
        String clientId = "minecraft-drift-client";
        String secretA = "secret-a";
        String secretB = "secret-b";

        ReflectionTestUtils.setField(
            clientRegistrationService,
            "minecraftClientId",
            clientId
        );
        ReflectionTestUtils.setField(
            clientRegistrationService,
            "minecraftClientSecret",
            secretA
        );
        clientRegistrationService.registerMinecraftServerClient();

        RegisteredClient initialClient = clientRepository.findByClientId(clientId);
        assertThat(initialClient).isNotNull();
        assertThat(initialClient.getClientSecret()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(secretA, initialClient.getClientSecret()))
            .isTrue();

        String initialStoredSecret = initialClient.getClientSecret();

        ReflectionTestUtils.setField(
            clientRegistrationService,
            "minecraftClientSecret",
            secretB
        );
        clientRegistrationService.registerMinecraftServerClient();

        RegisteredClient reconciledClient = clientRepository.findByClientId(clientId);
        assertThat(reconciledClient).isNotNull();
        assertThat(reconciledClient.getClientSecret()).isNotEqualTo(initialStoredSecret);
        assertThat(
            passwordEncoder.matches(secretB, reconciledClient.getClientSecret())
        ).isTrue();
        assertThat(reconciledClient.getClientAuthenticationMethods())
            .containsExactlyInAnyOrder(
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                ClientAuthenticationMethod.CLIENT_SECRET_POST
            );
        assertThat(reconciledClient.getAuthorizationGrantTypes()).containsExactly(
            AuthorizationGrantType.CLIENT_CREDENTIALS
        );
        assertThat(reconciledClient.getScopes()).containsExactlyInAnyOrder(
            "api:read",
            "api:write"
        );
    }

    @Test
    void registerMinecraftServerClient_rejectsBlankClientSecret() {
        ReflectionTestUtils.setField(
            clientRegistrationService,
            "minecraftClientId",
            "minecraft-invalid-secret-client"
        );
        ReflectionTestUtils.setField(
            clientRegistrationService,
            "minecraftClientSecret",
            "   "
        );

        assertThatThrownBy(() ->
                clientRegistrationService.registerMinecraftServerClient()
            )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("minecraft.client.secret must not be blank.");

        assertThat(
            clientRepository.findByClientId("minecraft-invalid-secret-client")
        ).isNull();
    }

    @Test
    void registerDashboardBffClient_withConfiguredSecret_registersClient() {
        String clientId = "dashboard-bff-test-client";
        String secret = "dashboard-secret";

        ReflectionTestUtils.setField(
            clientRegistrationService,
            "dashboardBffClientId",
            clientId
        );
        ReflectionTestUtils.setField(
            clientRegistrationService,
            "dashboardBffClientSecret",
            secret
        );

        clientRegistrationService.registerDashboardBffClient();

        RegisteredClient registeredClient = clientRepository.findByClientId(clientId);
        assertThat(registeredClient).isNotNull();
        assertThat(registeredClient.getClientSecret()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(secret, registeredClient.getClientSecret()))
            .isTrue();
        assertThat(registeredClient.getAuthorizationGrantTypes()).containsExactly(
            AuthorizationGrantType.CLIENT_CREDENTIALS
        );
        assertThat(registeredClient.getScopes()).containsExactlyInAnyOrder(
            "api:read",
            "api:write",
            "market:admin"
        );
        assertThat(registeredClient.getClientAuthenticationMethods())
            .containsExactlyInAnyOrder(
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                ClientAuthenticationMethod.CLIENT_SECRET_POST
            );
    }

    @Test
    void registerDashboardBffClient_reconcilesMissingMarketAdminScope() {
        String clientId = "dashboard-bff-scope-drift-client";

        ReflectionTestUtils.setField(
            clientRegistrationService,
            "dashboardBffClientId",
            clientId
        );
        ReflectionTestUtils.setField(
            clientRegistrationService,
            "dashboardBffClientSecret",
            "dashboard-secret"
        );
        clientRegistrationService.registerDashboardBffClient();

        RegisteredClient registeredClient = clientRepository.findByClientId(clientId);
        RegisteredClient driftedClient = RegisteredClient.from(registeredClient)
            .scopes(scopes -> {
                scopes.clear();
                scopes.add("api:read");
                scopes.add("api:write");
            })
            .build();
        clientRepository.save(driftedClient);

        clientRegistrationService.registerDashboardBffClient();

        RegisteredClient reconciledClient = clientRepository.findByClientId(clientId);
        assertThat(reconciledClient.getScopes()).containsExactlyInAnyOrder(
            "api:read",
            "api:write",
            "market:admin"
        );
    }

    @Test
    void registerMinecraftServerClient_removesUnauthorizedScopeDrift() {
        String clientId = "minecraft-scope-drift-client";

        ReflectionTestUtils.setField(
            clientRegistrationService,
            "minecraftClientId",
            clientId
        );
        ReflectionTestUtils.setField(
            clientRegistrationService,
            "minecraftClientSecret",
            "minecraft-secret"
        );
        clientRegistrationService.registerMinecraftServerClient();

        RegisteredClient registeredClient = clientRepository.findByClientId(clientId);
        RegisteredClient driftedClient = RegisteredClient.from(registeredClient)
            .scope("market:admin")
            .build();
        clientRepository.save(driftedClient);

        clientRegistrationService.registerMinecraftServerClient();

        RegisteredClient reconciledClient = clientRepository.findByClientId(clientId);
        assertThat(reconciledClient.getScopes()).containsExactlyInAnyOrder(
            "api:read",
            "api:write"
        );
    }

    @Test
    void registerDashboardBffClient_withoutConfiguredSecret_skipsClient() {
        String clientId = "dashboard-bff-disabled-client";

        ReflectionTestUtils.setField(
            clientRegistrationService,
            "dashboardBffClientId",
            clientId
        );
        ReflectionTestUtils.setField(
            clientRegistrationService,
            "dashboardBffClientSecret",
            ""
        );

        clientRegistrationService.registerDashboardBffClient();

        assertThat(clientRepository.findByClientId(clientId)).isNull();
    }
}
