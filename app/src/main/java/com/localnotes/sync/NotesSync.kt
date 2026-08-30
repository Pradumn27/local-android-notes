package com.localnotes.sync

import kotlinx.coroutines.flow.StateFlow

const val SYNC_HTTP_PORT = 18765
const val SYNC_BEACON_PORT = 18766
const val SYNC_PROTOCOL_VERSION = 1
const val SYNC_SERVICE = "local-notes"

data class SyncPeer(
    val name: String,
    val host: String,
    val port: Int,
)

data class SyncStatus(
    val connectedPeer: SyncPeer? = null,
    val lastError: String? = null,
    val lastSyncedAt: Long? = null,
    val listening: Boolean = false,
    val scanning: Boolean = false,
    val pairing: Boolean = false,
    val syncing: Boolean = false,
    val paired: Boolean = false,
    val live: Boolean = false,
    val peers: List<SyncPeer> = emptyList(),
    val progress: String? = null,
    val lastResult: String? = null,
    val liveWidgetsOptedIn: Boolean = false,
)

data class SyncReport(
    val pulled: Int = 0,
    val pushed: Int = 0,
    val deleted: Int = 0,
    val folders: Int = 0,
) {
    fun summary(): String {
        return buildString {
            append("Synced")
            if (pulled > 0) append(" · $pulled in")
            if (pushed > 0) append(" · $pushed out")
            if (deleted > 0) append(" · $deleted deleted")
            if (folders > 0) append(" · $folders folders")
            if (pulled == 0 && pushed == 0 && deleted == 0) append(" · already up to date")
        }
    }
}

class NeedsPinException : Exception("PIN required")
class WrongPinException : Exception("Wrong PIN")

interface NotesSyncClient {
    val status: StateFlow<SyncStatus>
    suspend fun startListening()
    suspend fun stop()
    suspend fun pull()
    suspend fun pushDirty()
    suspend fun pair(peer: SyncPeer, pin: String)
    suspend fun pairManual(host: String, port: Int, pin: String)
    suspend fun syncNow()
    fun startAutoSync()
    fun notifyLocalChange(noteId: String? = null)
    fun setLiveWidgetsOptIn(enabled: Boolean)
    fun reconnectNow()
}
