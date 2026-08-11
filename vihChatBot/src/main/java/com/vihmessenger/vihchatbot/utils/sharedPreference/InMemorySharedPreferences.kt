package com.vihmessenger.vihchatbot.utils.sharedPreference

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * A process-lifetime-only [SharedPreferences] used as the safe degradation path when
 * [androidx.security.crypto.EncryptedSharedPreferences] cannot be initialised
 * (VAPT F-07, CWE-311).
 *
 * The previous behaviour on a Keystore failure was to fall back to plaintext
 * `MODE_PRIVATE` prefs, which silently wrote access tokens, refresh tokens, phone number
 * and the full user profile to a backup-eligible XML file readable by root. Failing to a
 * memory-only store instead means the worst case is "the user must sign in again next
 * launch" rather than "credentials were written to disk in the clear".
 *
 * Nothing here is persisted. Registered change listeners are honoured so callers behave
 * identically, and [edit] applies synchronously — `apply()` and `commit()` are equivalent
 * because there is no I/O to defer.
 */
internal class InMemorySharedPreferences : SharedPreferences {

    private val values = ConcurrentHashMap<String, Any>()
    private val listeners =
        mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = HashMap(values)

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        listener ?: return
        synchronized(listeners) { listeners.add(listener) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        listener ?: return
        synchronized(listeners) { listeners.remove(listener) }
    }

    private fun notifyChanged(key: String?) {
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it.onSharedPreferenceChanged(this, key) }
    }

    private inner class Editor : SharedPreferences.Editor {
        // Staged so a caller that builds up an edit and never commits changes nothing,
        // matching the real SharedPreferences contract.
        private val pending = LinkedHashMap<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?
        ): SharedPreferences.Editor = apply { pending[key] = values }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun remove(key: String): SharedPreferences.Editor =
            apply { pending[key] = null }

        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }

        override fun commit(): Boolean {
            val changedKeys = mutableListOf<String?>()
            if (clearRequested) {
                changedKeys += values.keys.toList()
                values.clear()
            }
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
                changedKeys += key
            }
            pending.clear()
            clearRequested = false
            changedKeys.distinct().forEach { notifyChanged(it) }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
