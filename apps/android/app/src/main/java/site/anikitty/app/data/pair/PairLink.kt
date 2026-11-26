// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.pair

import org.json.JSONObject
import java.net.URLDecoder
import java.security.SecureRandom

/** How long a code on the TV stays valid. */
const val PAIR_TTL_MS = 10 * 60_000L

private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val CODE_LENGTH = 6

/**
 * What the QR contains. The AniList token is never in here.
 * [host] is a private IPv4 address on the same LAN.
 */
data class PairOffer(
    val code: String,
    val host: String,
    val port: Int,
)

data class PairHandoff(
    val code: String,
    val token: String,
    val deviceName: String,
    val historyJson: String?,
)

data class PairReply(
    val ok: Boolean,
    val secret: String?,
    val name: String?,
    val error: String?,
)

fun pairingCode(nextInt: (Int) -> Int = SecureRandom()::nextInt): String =
    buildString(CODE_LENGTH) {
        repeat(CODE_LENGTH) { append(CODE_ALPHABET[nextInt(CODE_ALPHABET.length)]) }
    }

fun isPairCode(raw: String?): Boolean {
    val code = raw?.trim()?.uppercase() ?: return false
    if (code.length != CODE_LENGTH) return false
    return code.all { it in CODE_ALPHABET }
}

fun pairCodeFresh(issuedAtEpochMs: Long, nowEpochMs: Long, ttlMs: Long = PAIR_TTL_MS): Boolean {
    val age = nowEpochMs - issuedAtEpochMs
    return age in 0..ttlMs
}

/** True for loopback and private IPv4. Hostnames are rejected. */
fun isPrivateLanHost(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4) return false
    val numbers = parts.map { it.toIntOrNull() ?: return false }
    if (numbers.any { it !in 0..255 }) return false
    val last = numbers[3]
    if (last == 0 || last == 255) return false
    val a = numbers[0]
    val b = numbers[1]
    return a == 10 ||
        a == 127 ||
        (a == 192 && b == 168) ||
        (a == 169 && b == 254) ||
        (a == 172 && b in 16..31)
}

fun pairUri(offer: PairOffer): String =
    "anikitty://pair?code=${offer.code}&host=${offer.host}&port=${offer.port}"

fun parsePairUri(raw: String?): PairOffer? {
    val text = raw?.trim().orEmpty()
    if (!text.startsWith("anikitty://pair")) return null
    val query = text.substringAfter('?', "")
    if (query.isEmpty() || query == text) return null
    val map = LinkedHashMap<String, String>()
    query.split('&').forEach { part ->
        val index = part.indexOf('=')
        if (index <= 0) return@forEach
        val key = URLDecoder.decode(part.substring(0, index), Charsets.UTF_8.name())
        val value = URLDecoder.decode(part.substring(index + 1), Charsets.UTF_8.name())
        map[key] = value
    }
    val code = map["code"] ?: return null
    val host = map["host"] ?: return null
    val port = map["port"]?.toIntOrNull() ?: return null
    if (!isPairCode(code) || !isPrivateLanHost(host) || port !in 1..65535) return null
    return PairOffer(code.trim().uppercase(), host, port)
}

fun codesMatch(expected: String, given: String): Boolean {
    val left = expected.trim().uppercase()
    val right = given.trim().uppercase()
    if (!isPairCode(left) || !isPairCode(right)) return false
    var diff = 0
    for (index in left.indices) diff = diff or (left[index].code xor right[index].code)
    return diff == 0
}

fun secretsMatch(expected: String?, given: String?): Boolean {
    if (expected.isNullOrBlank() || given.isNullOrBlank()) return false
    if (expected.length != given.length) return false
    var diff = 0
    for (index in expected.indices) diff = diff or (expected[index].code xor given[index].code)
    return diff == 0
}

fun newPairSecret(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

fun pairHandoffJson(handoff: PairHandoff): String {
    val body = JSONObject()
        .put("code", handoff.code)
        .put("token", handoff.token)
        .put("deviceName", handoff.deviceName)
    if (!handoff.historyJson.isNullOrBlank()) body.put("history", handoff.historyJson)
    return body.toString()
}

fun parsePairHandoff(raw: String?): PairHandoff? {
    if (raw.isNullOrBlank() || raw.length > 2_000_000) return null
    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val code = obj.optString("code")
    val token = obj.optString("token")
    if (!isPairCode(code) || token.isBlank() || token.length > 4096) return null
    val name = obj.optString("deviceName").trim().ifBlank { "Phone" }.take(80)
    val history = obj.optString("history").takeIf { it.isNotBlank() }
    return PairHandoff(code.trim().uppercase(), token, name, history)
}

fun pairReplyJson(reply: PairReply): String {
    val body = JSONObject().put("ok", reply.ok)
    reply.secret?.let { body.put("secret", it) }
    reply.name?.let { body.put("name", it) }
    reply.error?.let { body.put("error", it) }
    return body.toString()
}

fun parsePairReply(raw: String?): PairReply? {
    if (raw.isNullOrBlank()) return null
    val obj = runCatching { JSONObject(raw.trim()) }.getOrNull() ?: return null
    if (!obj.has("ok")) return null
    return PairReply(
        ok = obj.optBoolean("ok", false),
        secret = obj.optString("secret").takeIf { it.isNotBlank() },
        name = obj.optString("name").takeIf { it.isNotBlank() },
        error = obj.optString("error").takeIf { it.isNotBlank() },
    )
}

fun unpairJson(secret: String): String = JSONObject().put("secret", secret).toString()

fun parseUnpairSecret(raw: String?): String? {
    if (raw.isNullOrBlank() || raw.length > 512) return null
    val secret = runCatching { JSONObject(raw).optString("secret") }.getOrNull() ?: return null
    if (secret.length !in 16..128 || secret.any { !it.isLetterOrDigit() }) return null
    return secret
}
