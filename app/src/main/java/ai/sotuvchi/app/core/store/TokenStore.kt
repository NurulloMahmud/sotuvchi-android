package ai.sotuvchi.app.core.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

/** Plain key-value storage for the few things the app remembers. */
interface KeyValueStore {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun remove(key: String)
}

private val Context.sotuvchiDataStore: DataStore<Preferences> by preferencesDataStore(name = "sotuvchi")

class DataStoreKeyValueStore(context: Context) : KeyValueStore {
    private val store = context.applicationContext.sotuvchiDataStore

    override suspend fun get(key: String): String? = store.data.first()[stringPreferencesKey(key)]

    override suspend fun set(key: String, value: String) {
        store.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun remove(key: String) {
        store.edit { it.remove(stringPreferencesKey(key)) }
    }
}

/** In-memory store for tests. */
class MemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun get(key: String): String? = values[key]
    override suspend fun set(key: String, value: String) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
}

/** Encrypts and decrypts small secrets. */
interface SecretBox {
    fun encrypt(plain: String): String
    fun decrypt(encoded: String): String?
}

/** AES-GCM with a key that lives in the Android Keystore and never leaves it. */
class KeystoreSecretBox(private val alias: String = "sotuvchi_session_key") : SecretBox {
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$data"
    }

    override fun decrypt(encoded: String): String? = try {
        val (iv, data) = encoded.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (_: Exception) {
        // The key was lost (for example a restored backup): treat as signed out.
        null
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/** Test box that stores the secret as it is. */
class PlainSecretBox : SecretBox {
    override fun encrypt(plain: String): String = "plain:$plain"
    override fun decrypt(encoded: String): String? = encoded.removePrefix("plain:").takeIf { encoded.startsWith("plain:") }
}

/** The session token: AES-GCM ciphertext from the Keystore key, kept in DataStore. */
class TokenStore(private val store: KeyValueStore, private val box: SecretBox, private val key: String) {
    suspend fun read(): String? = store.get(key)?.let { box.decrypt(it) }

    suspend fun write(token: String) = store.set(key, box.encrypt(token))

    suspend fun clear() = store.remove(key)
}
