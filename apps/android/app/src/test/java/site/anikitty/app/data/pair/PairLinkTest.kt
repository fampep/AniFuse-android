// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.pair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairLinkTest {
    @Test
    fun codeIsSixUnambiguousCharacters() {
        val code = pairingCode { index -> index % 32 }
        assertTrue(isPairCode(code))
        assertEquals(6, code.length)
        assertFalse(code.contains('0'))
        assertFalse(code.contains('O'))
        assertFalse(code.contains('1'))
        assertFalse(code.contains('I'))
    }

    @Test
    fun pairUriRoundTripsWithoutAToken() {
        val offer = PairOffer(code = "AB234K", host = "192.168.1.20", port = 47655)
        val uri = pairUri(offer)
        assertFalse(uri.contains("token", ignoreCase = true))
        assertEquals(offer, parsePairUri(uri))
    }

    @Test
    fun publicAndNamedHostsAreRejected() {
        assertNull(parsePairUri("anikitty://pair?code=AB234K&host=8.8.8.8&port=9"))
        assertNull(parsePairUri("anikitty://pair?code=AB234K&host=tv.local&port=9"))
        assertNull(parsePairUri("anikitty://pair?code=AB234K&host=192.168.1.255&port=9"))
        assertNull(parsePairUri("anikitty://anilist?code=AB234K&host=192.168.1.20&port=9"))
        assertTrue(isPrivateLanHost("10.1.2.3"))
        assertTrue(isPrivateLanHost("172.16.0.4"))
        assertFalse(isPrivateLanHost("172.32.0.4"))
    }

    @Test
    fun codesMatchIgnoresCaseAndRejectsAMismatch() {
        assertTrue(codesMatch("ab234k", "AB234K"))
        assertFalse(codesMatch("AB234K", "AB234L"))
        assertFalse(codesMatch("AB234K", "AB234"))
    }

    @Test
    fun codeExpiresAfterTenMinutes() {
        val issued = 1_000L
        assertTrue(pairCodeFresh(issued, issued + PAIR_TTL_MS))
        assertFalse(pairCodeFresh(issued, issued + PAIR_TTL_MS + 1))
        assertFalse(pairCodeFresh(issued, issued - 1))
    }

    @Test
    fun handoffRoundTripKeepsHistoryAndDropsBlankToken() {
        val handoff = PairHandoff(
            code = "AB234K",
            token = "token-value",
            deviceName = "Pixel",
            historyJson = """{"app":"anikitty","progress":[]}""",
        )
        val parsed = parsePairHandoff(pairHandoffJson(handoff))
        assertEquals(handoff, parsed)
        assertNull(parsePairHandoff("""{"code":"AB234K","token":""}"""))
    }

    @Test
    fun replyAndSecretCompare() {
        val reply = PairReply(ok = true, secret = "abc123", name = "Living Room", error = null)
        assertEquals(reply, parsePairReply(pairReplyJson(reply)))
        assertTrue(secretsMatch("abc123", "abc123"))
        assertFalse(secretsMatch("abc123", "abc124"))
        assertFalse(secretsMatch(null, "abc123"))
        assertEquals("abc123abc123abcd", parseUnpairSecret(unpairJson("abc123abc123abcd")))
        assertNull(parseUnpairSecret("""{"secret":"short"}"""))
    }
}
