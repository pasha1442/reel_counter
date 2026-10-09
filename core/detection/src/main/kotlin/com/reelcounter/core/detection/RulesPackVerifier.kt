package com.reelcounter.core.detection

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Checks that a downloaded rules pack was signed by our server key (ECDSA P-256, SHA-256).
 * A pack that fails verification is ignored and the last good pack stays in use.
 */
class RulesPackVerifier(publicKeyBase64: String) {
    private val publicKey: PublicKey = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)))

    fun verify(packBytes: ByteArray, signatureBase64: String): Boolean = runCatching {
        Signature.getInstance(ALGORITHM).run {
            initVerify(publicKey)
            update(packBytes)
            verify(Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)

    companion object {
        const val ALGORITHM = "SHA256withECDSA"
    }
}
