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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
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

        ReflectionTestUtils.setField(clientRegistrationService, "clientId", clientId);
        ReflectionTestUtils.setField(
            clientRegistrationService,
            "clientSecret",
            secretA
        );
        clientRegistrationService.registerMinecraftServerClient();

        RegisteredClient initialClient = clientRepository.findByClientId(clientId);
        assertThat(initialClient).isNotNull();
        assertThat(passwordEncoder.matches(secretA, initialClient.getClientSecret()))
            .isTrue();

        String initialStoredSecret = initialClient.getClientSecret();

        ReflectionTestUtils.setField(
            clientRegistrationService,
            "clientSecret",
            secretB
        );
        clientRegistrationService.registerMinecraftServerClient();

        RegisteredClient reconciledClient = clientRepository.findByClientId(clientId);
        assertThat(reconciledClient).isNotNull();
        assertThat(reconciledClient.getClientSecret()).isNotEqualTo(initialStoredSecret);
        assertThat(
            passwordEncoder.matches(secretB, reconciledClient.getClientSecret())
        ).isTrue();
        assertThat(reconciledClient.getClientAuthenticationMethods()).containsExactly(
            ClientAuthenticationMethod.CLIENT_SECRET_BASIC
        );
        assertThat(reconciledClient.getAuthorizationGrantTypes()).containsExactly(
            AuthorizationGrantType.CLIENT_CREDENTIALS
        );
    }

    @Test
    void registerMinecraftServerClient_rejectsBlankClientSecret() {
        ReflectionTestUtils.setField(
            clientRegistrationService,
            "clientId",
            "minecraft-invalid-secret-client"
        );
        ReflectionTestUtils.setField(
            clientRegistrationService,
            "clientSecret",
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
}
