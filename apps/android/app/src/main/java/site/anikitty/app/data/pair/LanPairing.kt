// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.pair

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

const val PAIR_SERVICE_TYPE = "_anikitty._tcp."

data class DiscoveredTv(
    val name: String,
    val host: String,
    val port: Int,
)

fun thisDeviceName(context: Context): String {
    val global = runCatching {
        Settings.Global.getString(context.contentResolver, "device_name")
    }.getOrNull()
    val raw = global?.takeIf { it.isNotBlank() } ?: Build.MODEL
    val cleaned = raw.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim()
    return cleaned.ifBlank { "AniFuse" }.take(48)
}

fun siteLocalIpv4(): String? {
    val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull()
        ?: return null
    val addresses = interfaces
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .sortedBy { iface ->
            val name = iface.name.orEmpty()
            when {
                name.startsWith("wlan") || name.startsWith("wifi") -> 0
                name.startsWith("eth") -> 1
                else -> 2
            }
        }
        .flatMap { it.inetAddresses.toList() }
    return addresses
        .filterIsInstance<Inet4Address>()
        .mapNotNull { it.hostAddress }
        .firstOrNull { isPrivateLanHost(it) }
}

/**
 * One LAN socket for the TV. Sign-in is accepted only while [acceptSignIn] is set.
 * Unpair is accepted whenever this process remembers a secret.
 * The HTTP body is a raw socket so the app can keep blocking cleartext everywhere else.
 */
class LanPairServer(
    private val context: Context,
) {
    @Volatile var listener: LanPairListener? = null
    @Volatile private var acceptingSignIn: Boolean = false

    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var socket: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var code: String = pairingCode()
    @Volatile private var issuedAt: Long = System.currentTimeMillis()
    private var failures = 0

    fun setAcceptSignIn(enabled: Boolean) {
        val turnedOn = enabled && !acceptingSignIn
        acceptingSignIn = enabled
        if (turnedOn) rotateCode()
        else publish()
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({ loop() }, "anikitty-pair").also { it.start() }
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        unregister()
        thread?.join(1500)
        thread = null
    }

    private fun loop() {
        val server = ServerSocket()
        socket = server
        try {
            server.reuseAddress = true
            val preferred = PairedDeviceStore(context).savedPort()
            try {
                server.bind(InetSocketAddress(preferred), 4)
            } catch (_: Exception) {
                server.bind(InetSocketAddress(0), 4)
            }
            PairedDeviceStore(context).savePort(server.localPort)
            register(server.localPort)
            publish()
            server.soTimeout = 1000
            while (running.get()) {
                if (acceptingSignIn && !pairCodeFresh(issuedAt, System.currentTimeMillis())) rotateCode()
                val client = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    if (!running.get()) break
                    continue
                }
                try {
                    client.soTimeout = 8000
                    handle(client)
                } catch (_: Exception) {
                    // A bad request must not stop the listener.
                } finally {
                    runCatching { client.close() }
                }
            }
        } finally {
            runCatching { server.close() }
            unregister()
            if (running.get()) running.set(false)
            main.post { listener?.onOffer(null) }
        }
    }

    private fun handle(client: Socket) {
        val request = readHttp(client) ?: return
        when {
            request.method == "POST" && request.path == "/pair" -> handlePair(client, request.body)
            request.method == "POST" && request.path == "/unpair" -> handleUnpair(client, request.body)
            else -> writeJson(client, 404, "Not Found", pairReplyJson(PairReply(false, null, null, "Not found")))
        }
    }

    private fun handlePair(client: Socket, body: String) {
        if (!acceptingSignIn || !pairCodeFresh(issuedAt, System.currentTimeMillis())) {
            writeJson(client, 409, "Conflict", pairReplyJson(PairReply(false, null, null, "That code expired. Scan the TV again.")))
            return
        }
        val handoff = parsePairHandoff(body)
        if (handoff == null || !codesMatch(code, handoff.code)) {
            failures += 1
            if (failures >= 8) rotateCode()
            writeJson(client, 400, "Bad Request", pairReplyJson(PairReply(false, null, null, "That code does not match the TV.")))
            return
        }
        failures = 0
        val reply = listener?.accept(handoff)
            ?: PairReply(false, null, null, "The TV is not ready")
        if (reply.ok) acceptingSignIn = false
        writeJson(client, if (reply.ok) 200 else 401, if (reply.ok) "OK" else "Unauthorized", pairReplyJson(reply))
    }

    private fun handleUnpair(client: Socket, body: String) {
        val secret = parseUnpairSecret(body)
        val ok = secret != null && listener?.unpair(secret) == true
        val reply = if (ok) {
            PairReply(true, null, null, null)
        } else {
            PairReply(false, null, null, "This TV is not paired with that phone")
        }
        writeJson(client, if (ok) 200 else 403, if (ok) "OK" else "Forbidden", pairReplyJson(reply))
    }

    private fun rotateCode() {
        code = pairingCode()
        issuedAt = System.currentTimeMillis()
        failures = 0
        publish()
    }

    private fun publish() {
        val host = siteLocalIpv4()
        val port = socket?.localPort ?: return
        val offer = if (host != null && acceptingSignIn) PairOffer(code, host, port) else null
        val current = listener
        main.post { current?.onOffer(offer) }
    }

    private fun register(port: Int) {
        val manager = context.getSystemService(NsdManager::class.java) ?: return
        nsd = manager
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        multicast = wifi?.createMulticastLock("anikitty-pair")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val info = NsdServiceInfo().apply {
            serviceName = thisDeviceName(context)
            serviceType = PAIR_SERVICE_TYPE
            this.port = port
        }
        val callback = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = callback
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, callback) }
    }

    private fun unregister() {
        val manager = nsd
        val callback = registration
        if (manager != null && callback != null) {
            runCatching { manager.unregisterService(callback) }
        }
        registration = null
        nsd = null
        runCatching { if (multicast?.isHeld == true) multicast?.release() }
        multicast = null
    }
}

interface LanPairListener {
    fun onOffer(offer: PairOffer?)
    fun accept(handoff: PairHandoff): PairReply
    fun unpair(secret: String): Boolean
}

fun postPair(offer: PairOffer, handoff: PairHandoff): PairReply =
    postJson(offer.host, offer.port, "/pair", pairHandoffJson(handoff))

fun postUnpair(host: String, port: Int, secret: String): PairReply =
    postJson(host, port, "/unpair", unpairJson(secret))

private fun postJson(host: String, port: Int, path: String, json: String): PairReply {
    if (!isPrivateLanHost(host) || port !in 1..65535) {
        return PairReply(false, null, null, "The TV address is not on this network")
    }
    val socket = Socket()
    return try {
        socket.soTimeout = 8000
        socket.connect(InetSocketAddress(host, port), 8000)
        val body = json.toByteArray(StandardCharsets.UTF_8)
        val head = "POST $path HTTP/1.1\r\nHost: $host:$port\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(head.toByteArray(StandardCharsets.US_ASCII))
        out.write(body)
        out.flush()
        val text = socket.getInputStream().readBytes().toString(StandardCharsets.UTF_8)
        val raw = text.substringAfter("\r\n\r\n", "")
        parsePairReply(raw) ?: PairReply(false, null, null, "The TV sent an unexpected reply")
    } catch (_: Exception) {
        PairReply(false, null, null, "Could not reach the TV. Use the same Wi-Fi.")
    } finally {
        runCatching { socket.close() }
    }
}

fun discoverTvs(context: Context, waitMs: Long = 3500L): List<DiscoveredTv> {
    val manager = context.getSystemService(NsdManager::class.java) ?: return emptyList()
    val found = ArrayList<DiscoveredTv>()
    val lock = Object()
    val discovery = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (!serviceInfo.serviceType.contains("anikitty")) return
            runCatching {
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        if (!isPrivateLanHost(host)) return
                        synchronized(lock) {
                            found.add(DiscoveredTv(resolved.serviceName, host, resolved.port))
                        }
                    }
                })
            }
        }
    }
    val ready = java.util.concurrent.CountDownLatch(1)
    Handler(Looper.getMainLooper()).post {
        runCatching { manager.discoverServices(PAIR_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }
        ready.countDown()
    }
    ready.await()
    try {
        Thread.sleep(waitMs)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
    Handler(Looper.getMainLooper()).post {
        runCatching { manager.stopServiceDiscovery(discovery) }
    }
    synchronized(lock) {
        return found.distinctBy { "${it.host}:${it.port}" }
    }
}

private data class HttpRequest(val method: String, val path: String, val body: String)

private fun readHttp(socket: Socket): HttpRequest? {
    val input = socket.getInputStream()
    val header = ByteArrayOutputStream()
    val one = ByteArray(1)
    while (header.size() < 8192) {
        val read = input.read(one)
        if (read < 0) return null
        header.write(one, 0, read)
        val latin = String(header.toByteArray(), LATIN)
        val split = latin.indexOf("\r\n\r\n")
        if (split < 0) continue
        val head = latin.substring(0, split)
        val lines = head.split("\r\n")
        val request = lines.firstOrNull()?.split(' ') ?: return null
        if (request.size < 2) return null
        val length = lines.firstNotNullOfOrNull { line ->
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                line.substringAfter(':').trim().toIntOrNull()
            } else {
                null
            }
        } ?: 0
        if (length !in 0..2_000_000) return null
        val already = latin.substring(split + 4).toByteArray(LATIN)
        val body = ByteArray(length)
        var filled = 0
        if (already.isNotEmpty() && length > 0) {
            val copy = minOf(already.size, length)
            System.arraycopy(already, 0, body, 0, copy)
            filled = copy
        }
        while (filled < length) {
            val readBody = input.read(body, filled, length - filled)
            if (readBody < 0) return null
            filled += readBody
        }
        return HttpRequest(request[0], request[1].substringBefore('?'), String(body, StandardCharsets.UTF_8))
    }
    return null
}

private fun writeJson(socket: Socket, status: Int, reason: String, json: String) {
    val bytes = json.toByteArray(StandardCharsets.UTF_8)
    val head = "HTTP/1.1 $status $reason\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
    val out = socket.getOutputStream()
    out.write(head.toByteArray(StandardCharsets.US_ASCII))
    out.write(bytes)
    out.flush()
}

private val LATIN: Charset = Charsets.ISO_8859_1
