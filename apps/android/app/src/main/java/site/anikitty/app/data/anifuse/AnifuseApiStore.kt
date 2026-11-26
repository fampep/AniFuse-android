// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-visible AniFuse resolver API configuration.
 *
 * The hosted demo API ships as the default; self-hosters point the app at
 * their own instance from Settings → AniFuse API. Every API consumer reads
 * the live [baseUrl] flow, so a change takes effect without a restart.
 */
class AnifuseApiStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _baseUrl = MutableStateFlow(normalize(prefs.getString(KEY_BASE_URL, null)))
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    fun setBaseUrl(raw: String) {
        val normalized = normalize(raw)
        _baseUrl.value = normalized
        if (normalized == DEFAULT_BASE_URL) {
            prefs.edit().remove(KEY_BASE_URL).apply()
        } else {
            prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        }
    }

    fun resetBaseUrl() = setBaseUrl(DEFAULT_BASE_URL)

    /** True when the user changed the endpoint away from the hosted demo. */
    val isCustom: Boolean get() = _baseUrl.value != DEFAULT_BASE_URL

    companion object {
        const val DEFAULT_BASE_URL = "https://143-246-203-212.sslip.io"
        private const val PREFS = "anikitty_anifuse"
        private const val KEY_BASE_URL = "base_url"

        /** Strip whitespace and trailing slashes; blank input falls back to the demo. */
        fun normalize(raw: String?): String {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isBlank()) return DEFAULT_BASE_URL
            val noScheme = trimmed.substringAfter("://")
            val scheme = if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
                trimmed.substringBefore("://")
            } else {
                "https"
            }
            return "$scheme://${noScheme.trimEnd('/')}"
        }
    }
}
