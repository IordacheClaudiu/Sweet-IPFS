package utils.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Base64.NO_WRAP
import java.nio.charset.Charset
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Crypto(private val alias: String) {

    private val ANDROID_KEY_STORE = "AndroidKeyStore"
    private val keyStore: KeyStore
    private val RSA_TRANSFORMATION = "RSA/ECB/OAEPPadding"
    private val AES_TRANSFORMATION = "AES/CBC/PKCS7Padding"

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

    fun decryptRSA(data: String): ByteArray {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE , privateKey)
        val decodedEncryptedData = Base64.decode(data , Base64.DEFAULT)
        return cipher.doFinal(decodedEncryptedData)
    }

    fun decryptAES(data: String , secretBytes: ByteArray, iv: String): String {
        val secretKey = SecretKeySpec(secretBytes , 0 , secretBytes.size , "AES")
        val raw = secretKey.encoded
        val aesKeySpec = SecretKeySpec(raw , "AES")
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val decodedEncryptedData = Base64.decode(data, Base64.DEFAULT)
        var decodedIV = Base64.decode(iv, Base64.DEFAULT)
        cipher.init(Cipher.DECRYPT_MODE , aesKeySpec , IvParameterSpec(decodedIV))
        val original = cipher.doFinal(decodedEncryptedData)
        return String(original , Charset.forName("UTF-8"))
    }

    fun encrypt(message: String): String {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        val publicKey = privateKeyEntry(alias)?.certificate?.publicKey
        cipher.init(Cipher.ENCRYPT_MODE , publicKey)
        val bytArray = message.toByteArray(charset("UTF-8"))
        return Base64.encodeToString(cipher.doFinal(bytArray) , Base64.DEFAULT)
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
                                KeyProperties.DIGEST_SHA1 ,
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