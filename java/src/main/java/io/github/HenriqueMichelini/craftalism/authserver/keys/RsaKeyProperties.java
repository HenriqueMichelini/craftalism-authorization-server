package io.github.HenriqueMichelini.craftalism.authserver.keys;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Holds the RSA key pair used for JWT signing and verification.
 * Loaded once at startup by {@link com.platform.authserver.config.RsaKeyConfig}.
 */
public record RsaKeyProperties(
    RSAPublicKey publicKey,
    RSAPrivateKey privateKey
) {}
