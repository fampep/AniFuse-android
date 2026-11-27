// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * BabaStream AES-GCM + Cap proof-of-work used by [TwoDHiveServer].
 * MegaPlay crypto is intentionally absent — 2Dhive never opens that host.
 */
object TwoDHiveCrypto {
    private val random = SecureRandom()
    private const val GCM_TAG_BITS = 128

    fun encryptAesGcm(keyBase64: String, plaintext: String): String {
        val keyBytes = Base64.getDecoder().decode(keyBase64)
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decryptAesGcm(keyBase64: String, encryptedBase64: String): String {
        val keyBytes = Base64.getDecoder().decode(keyBase64)
        val dataBytes = Base64.getDecoder().decode(encryptedBase64)
        if (dataBytes.size <= 12) return ""
        val iv = dataBytes.copyOfRange(0, 12)
        val cipherText = dataBytes.copyOfRange(12, dataBytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    fun fnvPrng(seed: String, targetLen: Int): String {
        var t = 2166136261L
        for (i in seed.indices) {
            t = t xor seed[i].code.toLong()
            t = (t + (t shl 1) + (t shl 4) + (t shl 7) + (t shl 8) + (t shl 24)) and 0xFFFFFFFFL
        }
        var i = t.toInt()
        val sb = StringBuilder()
        while (sb.length < targetLen) {
            i = i xor (i shl 13)
            i = i xor (i ushr 17)
            i = i xor (i shl 5)
            val u = i.toLong() and 0xFFFFFFFFL
            var hex = java.lang.Long.toHexString(u)
            while (hex.length < 8) hex = "0$hex"
            sb.append(hex)
        }
        return sb.substring(0, targetLen)
    }

    fun solvePow(salt: String, target: String): Int {
        val targetBits = 4 * target.length
        val fullBytes = targetBits / 8
        val remBits = targetBits % 8
        val remMask = if (remBits > 0) (0xFF shl (8 - remBits)) and 0xFF else 0
        val paddedTarget = if (target.length % 2 == 0) target else "${target}0"
        val targetBytes = ByteArray(paddedTarget.length / 2)
        for (i in targetBytes.indices) {
            targetBytes[i] = paddedTarget.substring(2 * i, 2 * i + 2).toInt(16).toByte()
        }
        val saltBytes = salt.toByteArray(StandardCharsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val inputBuf = ByteArray(saltBytes.size + 16)
        System.arraycopy(saltBytes, 0, inputBuf, 0, saltBytes.size)
        var nonce = 0
        while (true) {
            val len = writeInt(nonce, inputBuf, saltBytes.size)
            md.update(inputBuf, 0, saltBytes.size + len)
            val digest = md.digest()
            var match = true
            for (i in 0 until fullBytes) {
                if (digest[i] != targetBytes[i]) {
                    match = false
                    break
                }
            }
            if (match && remBits > 0) {
                if ((digest[fullBytes].toInt() and remMask) != (targetBytes[fullBytes].toInt() and remMask)) {
                    match = false
                }
            }
            if (match) return nonce
            nonce++
        }
    }

    fun solveCapChallenges(token: String, count: Int, sLen: Int, dLen: Int): List<Int> {
        val n = count.coerceIn(1, 256)
        val solutions = IntArray(n)
        val pool = Executors.newFixedThreadPool(minOf(4, n))
        try {
            val futures = ArrayList<Future<*>>(n)
            for (idx in 1..n) {
                futures += pool.submit {
                    val salt = fnvPrng("$token$idx", sLen)
                    val target = fnvPrng("${token}${idx}d", dLen)
                    solutions[idx - 1] = solvePow(salt, target)
                }
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
        return solutions.toList()
    }

    private fun writeInt(v: Int, b: ByteArray, offset: Int): Int {
        if (v == 0) {
            b[offset] = '0'.code.toByte()
            return 1
        }
        var len = 0
        var temp = v
        while (temp > 0) {
            len++
            temp /= 10
        }
        var curr = v
        for (i in len - 1 downTo 0) {
            b[offset + i] = ('0'.code + (curr % 10)).toByte()
            curr /= 10
        }
        return len
    }
}
