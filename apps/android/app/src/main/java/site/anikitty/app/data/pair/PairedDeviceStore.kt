// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.pair

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PairedPeer(
    val name: String,
    val host: String,
    val port: Int,
    val secret: String,
    val pairedAtEpochMs: Long,
)

/** TVs this phone has signed in, plus the secret a TV uses to recognize an unpair. */
class PairedDeviceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun peers(): List<PairedPeer> {
        val raw = prefs.getString(KEY_PEERS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val secret = obj.optString("secret")
                val host = obj.optString("host")
                val port = obj.optInt("port")
                if (secret.isBlank() || !isPrivateLanHost(host) || port !in 1..65535) continue
                add(
                    PairedPeer(
                        name = obj.optString("name").ifBlank { "TV" },
                        host = host,
                        port = port,
                        secret = secret,
                        pairedAtEpochMs = obj.optLong("pairedAtEpochMs"),
                    ),
                )
            }
        }
    }

    fun rememberPeer(peer: PairedPeer) {
        val next = peers().filterNot { it.secret == peer.secret } + peer
        val array = JSONArray()
        next.forEach { item ->
            array.put(
                JSONObject()
                    .put("name", item.name)
                    .put("host", item.host)
                    .put("port", item.port)
                    .put("secret", item.secret)
                    .put("pairedAtEpochMs", item.pairedAtEpochMs),
            )
        }
        prefs.edit().putString(KEY_PEERS, array.toString()).apply()
    }

    fun forgetPeer(secret: String) {
        val next = peers().filterNot { secretsMatch(it.secret, secret) }
        if (next.isEmpty()) {
            prefs.edit().remove(KEY_PEERS).apply()
            return
        }
        val array = JSONArray()
        next.forEach { item ->
            array.put(
                JSONObject()
                    .put("name", item.name)
                    .put("host", item.host)
                    .put("port", item.port)
                    .put("secret", item.secret)
                    .put("pairedAtEpochMs", item.pairedAtEpochMs),
            )
        }
        prefs.edit().putString(KEY_PEERS, array.toString()).apply()
    }

    fun localSecret(): String? = prefs.getString(KEY_SECRET, null)?.takeIf { it.isNotBlank() }

    fun localPeerName(): String? = prefs.getString(KEY_PEER, null)?.takeIf { it.isNotBlank() }

    fun setLocalSession(secret: String?, peerName: String?) {
        prefs.edit().apply {
            if (secret.isNullOrBlank()) remove(KEY_SECRET) else putString(KEY_SECRET, secret)
            if (peerName.isNullOrBlank()) remove(KEY_PEER) else putString(KEY_PEER, peerName)
        }.apply()
    }

    fun savedPort(): Int = prefs.getInt(KEY_PORT, 47655).takeIf { it in 1..65535 } ?: 47655

    fun savePort(port: Int) {
        if (port in 1..65535) prefs.edit().putInt(KEY_PORT, port).apply()
    }

    private companion object {
        const val PREFS = "anikitty_paired_devices"
        const val KEY_PEERS = "peers"
        const val KEY_SECRET = "local_secret"
        const val KEY_PEER = "local_peer"
        const val KEY_PORT = "port"
    }
}
