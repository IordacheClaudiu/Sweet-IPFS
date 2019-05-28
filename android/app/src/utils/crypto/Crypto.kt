package utils.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Base64.NO_WRAP
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import javax.crypto.Cipher
import java.util.Base64.getEncoder



class Crypto(private val alias: String) {

    private val ANDROID_KEY_STORE = "AndroidKeyStore"
    private val keyStore: KeyStore
    private val TRANSFORMATION = "RSA/ECB/OAEPPadding"


    val publicKey: String?
        get() {
            val publicKey = privateKeyEntry(alias)?.certificate?.publicKey ?: return null
            return Base64.encodeToString(publicKey.encoded , NO_WRAP)
        }

    private val privateKey: PrivateKey?
        get() {
            return privateKeyEntry(alias)?.privateKey ?: null
        }

    init {
        keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        if (! hasKeyEntry(alias)) {
            initKeys()
        }
    }

    fun decrypt(data: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val encryptedData = Base64.decode(data, Base64.DEFAULT)
        val decodedData = cipher.doFinal(encryptedData)
        return String(decodedData)
    }

    fun encrypt(message: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val publicKey = privateKeyEntry(alias)?.certificate?.publicKey
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val bytArray = message.toByteArray(charset("UTF-8"))
        return Base64.encodeToString(cipher.doFinal(bytArray), Base64.DEFAULT)
    }

    private fun initKeys() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA , ANDROID_KEY_STORE)
        keyPairGenerator.initialize(
                KeyGenParameterSpec.Builder(
                        alias ,
                        KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                        .setDigests(
                                KeyProperties.DIGEST_SHA1,
                                KeyProperties.DIGEST_SHA256 ,
                                KeyProperties.DIGEST_SHA384 ,
                                KeyProperties.DIGEST_SHA512)
                        .build())
        keyPairGenerator.generateKeyPair()
    }

    private fun hasKeyEntry(alias: String): Boolean {
        return try {
            keyStore.load(null)
            keyStore.containsAlias(alias)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun privateKeyEntry(alias: String): KeyStore.PrivateKeyEntry? {
        return try {
            keyStore.load(null)
            when (val entry = keyStore.getEntry(alias , null)) {
                null -> null
                !is KeyStore.PrivateKeyEntry -> null
                else -> entry
            }
        } catch (e: Exception) {
            null
        }
    }
}