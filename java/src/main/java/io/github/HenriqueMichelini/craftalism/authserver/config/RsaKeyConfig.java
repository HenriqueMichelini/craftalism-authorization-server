package io.github.HenriqueMichelini.craftalism.authserver.config;

import io.github.HenriqueMichelini.craftalism.authserver.keys.RsaKeyProperties;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the RSA keypair used for JWT signing.
 *
 * <p>In production, keys are injected via {@code RSA_PRIVATE_KEY} and
 * {@code RSA_PUBLIC_KEY} environment variables (PEM format, with literal
 * {@code \n} between lines). Generate them with {@code ./generate-keys.sh}.
 *
 * <p>If either key is absent (local dev only), an ephemeral keypair is
 * generated at startup with a loud warning. Never rely on this in production
 * — ephemeral keys mean all previously issued tokens become invalid on restart.
 */
@Configuration
public class RsaKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(
        RsaKeyConfig.class
    );

    @Value("${rsa.private-key:}")
    private String privateKeyPem;

    @Value("${rsa.public-key:}")
    private String publicKeyPem;

    @Bean
    public RsaKeyProperties rsaKeyProperties() throws Exception {
        if (!privateKeyPem.isBlank() && !publicKeyPem.isBlank()) {
            log.info("RSA keys loaded from environment variables.");
            return new RsaKeyProperties(
                parsePublicKey(privateKeyPem, publicKeyPem),
                parsePrivateKey(privateKeyPem)
            );
        }

        log.warn("=========================================================");
        log.warn("  RSA_PRIVATE_KEY / RSA_PUBLIC_KEY not set.");
        log.warn("  Generating an EPHEMERAL keypair.");
        log.warn("  All tokens will be invalid after a restart.");
        log.warn("  Run ./generate-keys.sh and set the env vars.");
        log.warn("=========================================================");

        return generateEphemeralKeyPair();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Parses a PKCS8 PEM private key.
     * The PEM string may use literal {@code \n} (from .env files) or real newlines.
     */
    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String stripped = stripPem(pem, "PRIVATE KEY");
        byte[] decoded = Base64.getDecoder().decode(stripped);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(
            spec
        );
    }

    /**
     * Parses an X.509 PEM public key.
     */
    private RSAPublicKey parsePublicKey(String privateKeyPemUnused, String pem)
        throws Exception {
        String stripped = stripPem(pem, "PUBLIC KEY");
        byte[] decoded = Base64.getDecoder().decode(stripped);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
            spec
        );
    }

    /**
     * Removes PEM header/footer and all whitespace (including literal {@code \n}).
     */
    private String stripPem(String pem, String label) {
        return pem
            .replace("\\n", "\n") // literal \n from .env files
            .replace("-----BEGIN " + label + "-----", "")
            .replace("-----END " + label + "-----", "")
            .replaceAll("\\s+", "");
    }

    private RsaKeyProperties generateEphemeralKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return new RsaKeyProperties(
            (RSAPublicKey) keyPair.getPublic(),
            (RSAPrivateKey) keyPair.getPrivate()
        );
    }
}
