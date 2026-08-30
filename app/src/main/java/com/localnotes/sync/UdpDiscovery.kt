package com.localnotes.sync

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class UdpDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    suspend fun scan(onPeer: (SyncPeer) -> Unit) = withContext(Dispatchers.IO) {
        val lock = wifi.createMulticastLock("local-notes-scan")
        lock.setReferenceCounted(true)
        lock.acquire()
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            soTimeout = 1500
            bind(InetSocketAddress(SYNC_BEACON_PORT))
        }
        val buffer = ByteArray(2048)
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    parsePeer(text, packet.address.hostAddress)?.let { onPeer(it) }
                } catch (_: java.net.SocketTimeoutException) {
                    ensureActive()
                }
            }
        } finally {
            socket.close()
            if (lock.isHeld) lock.release()
        }
    }

    private fun parsePeer(text: String, fallbackHost: String?): SyncPeer? {
        return runCatching {
            val obj = JSONObject(text)
            if (obj.optString("service") != SYNC_SERVICE) return null
            val host = obj.optString("host").ifBlank { fallbackHost.orEmpty() }
            if (host.isBlank()) return null
            SyncPeer(
                name = obj.optString("name").ifBlank { host },
                host = host,
                port = obj.optInt("port", SYNC_HTTP_PORT),
            )
        }.getOrNull()
    }
}
