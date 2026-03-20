package io.github.HenriqueMichelini.craftalism.authserver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void tokenEndpoint_withValidClientCredentials_returnsJwt()
        throws Exception {
        mockMvc
            .perform(
                post("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "client_credentials")
                    .param("scope", "api:read")
                    // Base64("minecraft-server:test-secret")
                    .header(
                        "Authorization",
                        "Basic bWluZWNyYWZ0LXNlcnZlcjp0ZXN0LXNlY3JldA=="
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").isNumber());
    }

    @Test
    void tokenEndpoint_issuesJwtCompatibleWithCraftalismApiResourceServer()
        throws Exception {
        String responseBody = mockMvc
            .perform(
                post("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "client_credentials")
                    .param("scope", "api:read api:write")
                    .header(
                        "Authorization",
                        "Basic bWluZWNyYWZ0LXNlcnZlcjp0ZXN0LXNlY3JldA=="
                    )
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode tokenResponse = objectMapper.readTree(responseBody);
        SignedJWT accessToken = SignedJWT.parse(
            tokenResponse.get("access_token").asText()
        );

        assertThat(accessToken.getHeader().getAlgorithm().getName())
            .isEqualTo("RS256");
        assertThat(accessToken.getJWTClaimsSet().getIssuer())
            .isEqualTo("http://localhost:9000");
        assertThat(accessToken.getJWTClaimsSet().getSubject())
            .isEqualTo("minecraft-server");
        assertThat(accessToken.getJWTClaimsSet().getIssueTime()).isNotNull();
        assertThat(accessToken.getJWTClaimsSet().getExpirationTime()).isNotNull();
        assertThat(accessToken.getJWTClaimsSet().getClaim("scope"))
            .satisfies(scopeClaim -> {
                assertThat(scopeClaim).isInstanceOfAny(
                    String.class,
                    Collection.class
                );

                if (scopeClaim instanceof String stringScopeClaim) {
                    assertThat(stringScopeClaim.split(" "))
                        .contains("api:read", "api:write");
                    return;
                }

                @SuppressWarnings("unchecked")
                Collection<String> collectionScopeClaim =
                    (Collection<String>) scopeClaim;

                assertThat(collectionScopeClaim).contains(
                    "api:read",
                    "api:write"
                );
            });
    }

    @Test
    void tokenEndpoint_withInvalidSecret_returnsUnauthorized()
        throws Exception {
        mockMvc
            .perform(
                post("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "client_credentials")
                    // Base64("minecraft-server:wrong-secret")
                    .header(
                        "Authorization",
                        "Basic bWluZWNyYWZ0LXNlcnZlcjp3cm9uZy1zZWNyZXQ="
                    )
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenEndpoint_withNoCredentials_returnsUnauthorized()
        throws Exception {
        mockMvc
            .perform(
                post("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "client_credentials")
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void jwksEndpoint_isPubliclyAccessible() throws Exception {
        mockMvc
            .perform(get("/oauth2/jwks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keys").isArray());
    }

    @Test
    void discoveryEndpoint_isPubliclyAccessible() throws Exception {
        mockMvc
            .perform(get("/.well-known/openid-configuration"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issuer").value("http://localhost:9000"))
            .andExpect(
                jsonPath("$.jwks_uri").value("http://localhost:9000/oauth2/jwks")
            );
    }

    @Test
    void healthEndpoint_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void unknownEndpoint_isDeniedByDefaultPolicy() throws Exception {
        mockMvc.perform(get("/internal")).andExpect(status().isForbidden());
    }
}
