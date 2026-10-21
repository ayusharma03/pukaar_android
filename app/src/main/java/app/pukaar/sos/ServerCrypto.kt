package app.pukaar.sos

import com.bitchat.android.BuildConfig
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import java.util.Base64

/**
 * Crypto shared with the rescuer server (docs/protocol.md §3).
 *
 * - Server → mesh packets (acks, official messages) are signed with the server's Ed25519 key.
 *   Phones accept them only if the signature checks out against [signKey]; anything else is dropped.
 * - Phone → server contact details are sealed to the server's X25519 key ([boxKey]) so phones
 *   relaying them over the public mesh can't read them.
 *
 * Both public keys come from the build (`PUKAAR_SERVER_SIGN_KEY`, `PUKAAR_SERVER_BOX_KEY`),
 * base64url without padding, 32 raw bytes each.
 */
object ServerCrypto {
    private const val SEAL_INFO = "pukaar-contacts-v1"
    private const val SEAL_AAD = "PKCT1"
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val unb64 = Base64.getUrlDecoder()

    val signKey: ByteArray? by lazy { decodeKey(BuildConfig.PUKAAR_SERVER_SIGN_KEY) }
    val boxKey: ByteArray? by lazy { decodeKey(BuildConfig.PUKAAR_SERVER_BOX_KEY) }

    fun decodeKey(value: String): ByteArray? =
        value.trim().takeIf { it.isNotEmpty() }?.let { runCatching { unb64.decode(it) }.getOrNull() }?.takeIf { it.size == 32 }

    fun encode(bytes: ByteArray): String = b64.encodeToString(bytes)

    /** True only for a valid Ed25519 signature by [publicKey] over the UTF-8 [message]. Fails closed. */
    fun verify(message: String, signature: String, publicKey: ByteArray? = signKey): Boolean {
        if (publicKey == null || signature.isEmpty()) return false
        return runCatching {
            val sig = unb64.decode(signature)
            if (sig.size != 64) return false
            val data = message.toByteArray(Charsets.UTF_8)
            Ed25519Signer().run {
                init(false, Ed25519PublicKeyParameters(publicKey, 0))
                update(data, 0, data.size)
                verifySignature(sig)
            }
        }.getOrDefault(false)
    }

    /**
     * Seals [plain] so only the holder of the private key for [serverPublicKey] can read it.
     * Output: base64url(ephemeralPublic(32) || ChaCha20-Poly1305 ciphertext+tag).
     * Key = HKDF-SHA256(X25519(eph, server), salt = ephPub || serverPub, info = "pukaar-contacts-v1").
     * The key is fresh per message, so a zero nonce is safe.
     */
    fun seal(plain: ByteArray, serverPublicKey: ByteArray, random: SecureRandom = SecureRandom()): String {
        val eph = X25519PrivateKeyParameters(random)
        val ephPub = eph.generatePublicKey().encoded
        val key = deriveKey(eph, X25519PublicKeyParameters(serverPublicKey, 0), ephPub, serverPublicKey)
        val cipher = ChaCha20Poly1305().apply { init(true, AEADParameters(KeyParameter(key), 128, ByteArray(12), SEAL_AAD.toByteArray())) }
        val out = ByteArray(cipher.getOutputSize(plain.size))
        val n = cipher.processBytes(plain, 0, plain.size, out, 0)
        cipher.doFinal(out, n)
        return encode(ephPub + out)
    }

    /** Opens a [seal]ed message. Only the server does this in production; kept here for tests. */
    fun open(sealed: String, serverPrivateKey: ByteArray): ByteArray? = runCatching {
        val bytes = unb64.decode(sealed)
        val ephPub = bytes.copyOfRange(0, 32)
        val ct = bytes.copyOfRange(32, bytes.size)
        val priv = X25519PrivateKeyParameters(serverPrivateKey, 0)
        val serverPub = priv.generatePublicKey().encoded
        val key = deriveKey(priv, X25519PublicKeyParameters(ephPub, 0), ephPub, serverPub)
        val cipher = ChaCha20Poly1305().apply { init(false, AEADParameters(KeyParameter(key), 128, ByteArray(12), SEAL_AAD.toByteArray())) }
        val out = ByteArray(cipher.getOutputSize(ct.size))
        val n = cipher.processBytes(ct, 0, ct.size, out, 0)
        val total = n + cipher.doFinal(out, n)
        out.copyOf(total)
    }.getOrNull()

    private fun deriveKey(priv: X25519PrivateKeyParameters, peer: X25519PublicKeyParameters, ephPub: ByteArray, serverPub: ByteArray): ByteArray {
        val shared = ByteArray(32)
        X25519Agreement().apply { init(priv) }.calculateAgreement(peer, shared, 0)
        val key = ByteArray(32)
        HKDFBytesGenerator(SHA256Digest()).apply {
            init(HKDFParameters(shared, ephPub + serverPub, SEAL_INFO.toByteArray()))
        }.generateBytes(key, 0, 32)
        return key
    }
}
