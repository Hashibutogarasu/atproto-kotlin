package io.github.kikin81.atproto.samples.bluesky.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json

/**
 * Persistent single-slot session store backed by [EncryptedSharedPreferences]
 * (AES-256-GCM, Android Keystore master key).
 *
 * The store is factored into a [SessionStore] + a pluggable [SlotBackend] so
 * unit tests can supply an in-memory backend without Robolectric or Android
 * instrumentation. Production callers construct the store via
 * [SessionStore.forContext] which wires up [EncryptedSharedPreferences].
 */
class SessionStore(
    private val backend: SlotBackend,
    private val json: Json = DefaultJson,
) {
    fun load(): Session? {
        val raw = backend.read() ?: return null
        return runCatching { json.decodeFromString(Session.serializer(), raw) }.getOrNull()
    }

    fun save(session: Session) {
        backend.write(json.encodeToString(Session.serializer(), session))
    }

    fun clear() {
        backend.clear()
    }

    /**
     * Minimal single-slot persistence contract. Concrete impls just need to
     * hold at most one string at a time. In production this is
     * [EncryptedSharedPreferences]; in tests it's an in-memory fake.
     */
    interface SlotBackend {
        fun read(): String?
        fun write(value: String)
        fun clear()
    }

    companion object {
        private const val PREFS_FILE = "kikinlex_atproto_session"
        private const val KEY_SESSION = "session_json"

        private val DefaultJson = Json { ignoreUnknownKeys = true }

        /**
         * Production factory: constructs a [SessionStore] backed by
         * [EncryptedSharedPreferences]. Call from an Application/Activity
         * context.
         */
        fun forContext(context: Context): SessionStore = SessionStore(backend = EncryptedPrefsSlot(context.applicationContext))

        private class EncryptedPrefsSlot(appContext: Context) : SlotBackend {
            private val prefs: SharedPreferences by lazy {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }

            override fun read(): String? = prefs.getString(KEY_SESSION, null)

            override fun write(value: String) {
                prefs.edit().putString(KEY_SESSION, value).apply()
            }

            override fun clear() {
                prefs.edit().remove(KEY_SESSION).apply()
            }
        }
    }
}
