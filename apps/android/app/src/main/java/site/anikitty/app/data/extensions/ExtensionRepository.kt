// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.extensions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray

/**
 * Persists user-installed extension manifests (BYO stream servers) plus the
 * enabled/disabled state of each. Disabled extensions stay installed — they
 * just leave the failover chain until re-enabled.
 */
class ExtensionRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _installed = MutableStateFlow(loadAll())
    val installed: StateFlow<List<ExtensionManifest>> = _installed.asStateFlow()

    private val _disabled = MutableStateFlow(loadDisabled())
    val disabled: StateFlow<Set<String>> = _disabled.asStateFlow()

    fun get(id: String): ExtensionManifest? = _installed.value.firstOrNull { it.id == id }

    fun isEnabled(id: String): Boolean = id !in _disabled.value

    fun setEnabled(id: String, enabled: Boolean) {
        val next = _disabled.value.toMutableSet()
        if (enabled) next.remove(id) else next.add(id)
        prefs.edit().putString(KEY_DISABLED, next.joinToString(",")).apply()
        _disabled.update { next }
    }

    /** Clears every disable flag so the full failover race is live again. */
    fun enableAll() {
        if (_disabled.value.isEmpty()) return
        prefs.edit().remove(KEY_DISABLED).apply()
        _disabled.update { emptySet() }
    }

    /**
     * Force-enable specific ids (bundled sites). Restored providers that were
     * left disabled from an older build come back into the race.
     */
    fun ensureEnabled(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val next = _disabled.value.toMutableSet()
        var changed = false
        for (id in ids) {
            if (next.remove(id)) changed = true
        }
        if (!changed) return
        prefs.edit().putString(KEY_DISABLED, next.joinToString(",")).apply()
        _disabled.update { next }
    }

    fun upsert(manifest: ExtensionManifest) {
        val next = _installed.value.filterNot { it.id == manifest.id } + manifest
        persist(next)
        _installed.update { next.sortedBy { it.name.lowercase() } }
    }

    fun remove(id: String) {
        val next = _installed.value.filterNot { it.id == id }
        persist(next)
        _installed.update { next }
        setEnabled(id, enabled = true)
    }

    private fun loadDisabled(): Set<String> =
        prefs.getString(KEY_DISABLED, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    private fun loadAll(): List<ExtensionManifest> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(ExtensionManifestParser.parse(arr.getString(i)))
                }
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<ExtensionManifest>) {
        val arr = JSONArray()
        list.forEach { arr.put(ExtensionManifestParser.toJson(it)) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private companion object {
        const val PREFS = "anikitty_extensions"
        const val KEY_LIST = "installed_json"
        const val KEY_DISABLED = "disabled_ids"
    }
}
