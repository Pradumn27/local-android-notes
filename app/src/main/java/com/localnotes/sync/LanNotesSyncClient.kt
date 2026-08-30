package com.localnotes.sync

import android.content.Context
import com.localnotes.data.repository.NotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class LanNotesSyncClient(
    context: Context,
    private val repository: NotesRepository,
) : NotesSyncClient {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("notes_sync", Context.MODE_PRIVATE)
    private val discovery = UdpDiscovery(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val recentlyPushed = ConcurrentHashMap<String, Long>()
    private var scanJob: Job? = null
    private var loopJob: Job? = null
    private var pushJob: Job? = null
    private var lastRevision: String? = null
    private var autoStarted = false

    private val _status = MutableStateFlow(
        SyncStatus(
            paired = prefs.getString(KEY_TOKEN, null) != null,
            connectedPeer = savedPeer(),
            liveWidgetsOptedIn = LiveSyncService.optedIn(appContext),
        ),
    )
    override val status: StateFlow<SyncStatus> = _status

    override fun setLiveWidgetsOptIn(enabled: Boolean) {
        LiveSyncService.setOptedIn(appContext, enabled)
        if (enabled) {
            LiveSyncService.startIfAllowed(appContext)
        } else {
            LiveSyncService.stop(appContext)
        }
        _status.update { it.copy(liveWidgetsOptedIn = enabled && LiveSyncService.notificationsAllowed(appContext)) }
    }

    override fun startAutoSync() {
        if (autoStarted) return
        autoStarted = true
        scope.launch { startListening() }
        loopJob = scope.launch { autoLoop() }
    }

    override fun notifyLocalChange(noteId: String?) {
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(if (noteId == null) 250 else 800)
            val peer = savedPeer() ?: return@launch
            val token = prefs.getString(KEY_TOKEN, null) ?: return@launch
            if (!_status.value.live && !tryConnect(peer, token)) return@launch
            runCatching {
                mutex.withLock {
                    val api = MacNotesApi(peer.host, peer.port, token)
                    val engine = SyncEngine(repository, api)
                    if (noteId != null) {
                        val appleId = engine.pushNote(noteId)
                        if (appleId != null) recentlyPushed[appleId] = System.currentTimeMillis()
                    } else {
                        engine.run()
                    }
                    lastRevision = runCatching { api.revision() }.getOrNull() ?: lastRevision
                }
                _status.update {
                    it.copy(
                        lastSyncedAt = System.currentTimeMillis(),
                        lastResult = "Live",
                        lastError = null,
                        live = true,
                    )
                }
            }.onFailure { error ->
                if (error is NeedsPinException || error is WrongPinException) {
                    markDisconnected("Enter the PIN again")
                }
            }
        }
    }

    override suspend fun startListening() {
        if (scanJob?.isActive == true) return
        _status.update { it.copy(listening = true, scanning = true) }
        scanJob = scope.launch {
            try {
                discovery.scan { peer ->
                    _status.update { current ->
                        val peers = (current.peers + peer).distinctBy { it.host + ":" + it.port }
                        current.copy(peers = peers, scanning = true, listening = true)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            } finally {
                _status.update { it.copy(scanning = false) }
            }
        }
    }

    override suspend fun stop() {
        // Live sync stays on; this only pauses extra UI scanning.
        scanJob?.cancel()
        scanJob = null
        _status.update { it.copy(scanning = false, listening = loopJob?.isActive == true) }
    }

    override suspend fun pull() = syncNow()

    override suspend fun pushDirty() = syncNow()

    override suspend fun pair(peer: SyncPeer, pin: String) {
        pairInternal(peer, pin)
    }

    override suspend fun pairManual(host: String, port: Int, pin: String) {
        val trimmed = host.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("Enter the Mac address")
        val name = runCatching { MacNotesApi(trimmed, port).hello().optString("deviceName") }
            .getOrNull()
            ?.ifBlank { null }
            ?: trimmed
        pairInternal(SyncPeer(name, trimmed, port), pin)
    }

    override suspend fun syncNow() {
        val peer = savedPeer() ?: throw IllegalStateException("Pair with your Mac first")
        val token = prefs.getString(KEY_TOKEN, null) ?: throw NeedsPinException()
        _status.update { it.copy(syncing = true, lastError = null, progress = "Syncing…") }
        try {
            val report = mutex.withLock {
                withContext(Dispatchers.IO) {
                    val api = MacNotesApi(peer.host, peer.port, token)
                    val report = SyncEngine(
                        repository = repository,
                        api = api,
                        skipAppleIds = recentAppleIds(),
                        onProgress = { message -> _status.update { it.copy(progress = message) } },
                    ).run()
                    lastRevision = runCatching { api.revision() }.getOrNull()
                    report
                }
            }
            _status.update {
                it.copy(
                    syncing = false,
                    progress = null,
                    lastSyncedAt = System.currentTimeMillis(),
                    lastResult = report.summary(),
                    paired = true,
                    live = true,
                    connectedPeer = peer,
                    lastError = null,
                )
            }
        } catch (error: Exception) {
            _status.update {
                it.copy(
                    syncing = false,
                    progress = null,
                    live = false,
                    lastError = error.message ?: "Sync failed",
                    paired = error !is NeedsPinException && error !is WrongPinException,
                )
            }
            throw error
        }
    }

    private suspend fun autoLoop() {
        while (true) {
            val token = prefs.getString(KEY_TOKEN, null)
            val peer = preferredPeer()
            if (token != null && peer != null) {
                if (_status.value.live) {
                    pollOnce(peer, token)
                } else {
                    tryConnect(peer, token)
                }
            } else {
                _status.update { it.copy(live = false) }
            }
            delay(if (_status.value.live) 700 else 3_500)
        }
    }

    private fun preferredPeer(): SyncPeer? {
        val saved = savedPeer()
        val discovered = _status.value.peers
        if (saved != null) {
            discovered.find { it.host == saved.host }?.let { return it.copy(name = saved.name) }
            return saved
        }
        return discovered.firstOrNull()
    }

    private suspend fun tryConnect(peer: SyncPeer, token: String): Boolean {
        return runCatching {
            val hello = withContext(Dispatchers.IO) {
                MacNotesApi(peer.host, peer.port, token).hello()
            }
            if (!hello.optBoolean("tokenValid")) {
                markDisconnected("Enter the PIN again")
                return false
            }
            rememberPeer(peer)
            _status.update {
                it.copy(
                    live = true,
                    paired = true,
                    connectedPeer = peer.copy(name = hello.optString("deviceName").ifBlank { peer.name }),
                    lastError = null,
                    lastResult = "Live",
                )
            }
            runCatching { syncNow() }
            true
        }.getOrElse { error ->
            _status.update { it.copy(live = false) }
            if (error is NeedsPinException) markDisconnected("Enter the PIN again")
            false
        }
    }

    private suspend fun pollOnce(peer: SyncPeer, token: String) {
        val api = MacNotesApi(peer.host, peer.port, token)
        val revision = runCatching { withContext(Dispatchers.IO) { api.revision() } }.getOrElse { error ->
            if (error is NeedsPinException) markDisconnected("Enter the PIN again")
            else _status.update { it.copy(live = false) }
            return
        }
        if (revision.isBlank() || revision == lastRevision) return
        runCatching {
            mutex.withLock {
                val live = runCatching { api.liveNote() }.getOrNull()
                if (live != null && live.appleId !in recentAppleIds()) {
                    applyLive(live)
                } else {
                    SyncEngine(
                        repository = repository,
                        api = api,
                        skipAppleIds = recentAppleIds(),
                    ).run()
                }
                lastRevision = revision
            }
            _status.update {
                it.copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    lastResult = "Live",
                    lastError = null,
                    live = true,
                )
            }
        }
    }

    private suspend fun applyLive(live: RemoteNote) {
        val existing = repository.noteByAppleId(live.appleId)
        if (existing?.dirty == true) return
        val folders = repository.allFolders()
        val folderId = live.folderAppleId
            ?.let { appleId -> folders.find { it.appleId == appleId }?.id }
            ?: repository.defaultFolderId()
        val now = System.currentTimeMillis()
        repository.applyRemoteNote(
            appleId = live.appleId,
            folderId = folderId,
            title = live.title,
            plaintext = live.plaintext,
            html = live.html,
            createdAt = live.createdAt,
            modifiedAt = maxOf(live.modifiedAt, now),
            passwordProtected = live.passwordProtected,
        )
    }

    private suspend fun pairInternal(peer: SyncPeer, pin: String) {
        val trimmed = pin.trim()
        if (trimmed.length < 4) throw WrongPinException()
        _status.update { it.copy(pairing = true, lastError = null, progress = "Checking PIN…") }
        try {
            val token = withContext(Dispatchers.IO) {
                MacNotesApi(peer.host, peer.port).hello()
                MacNotesApi(peer.host, peer.port).pair(trimmed)
            }
            rememberPeer(peer, token)
            _status.update {
                it.copy(
                    pairing = false,
                    paired = true,
                    live = true,
                    connectedPeer = peer,
                    progress = null,
                    lastError = null,
                )
            }
            startAutoSync()
            LiveSyncService.startIfAllowed(appContext)
            syncNow()
        } catch (error: Exception) {
            _status.update {
                it.copy(
                    pairing = false,
                    progress = null,
                    lastError = error.message ?: "Could not pair",
                )
            }
            throw error
        }
    }

    private fun rememberPeer(peer: SyncPeer, token: String? = null) {
        prefs.edit()
            .putString(KEY_HOST, peer.host)
            .putInt(KEY_PORT, peer.port)
            .putString(KEY_NAME, peer.name)
            .apply {
                if (token != null) putString(KEY_TOKEN, token)
            }
            .apply()
    }

    private fun markDisconnected(message: String) {
        _status.update {
            it.copy(
                live = false,
                lastError = message,
                lastResult = null,
            )
        }
    }

    private fun recentAppleIds(): Set<String> {
        val cutoff = System.currentTimeMillis() - 4_000
        return recentlyPushed.filterValues { it >= cutoff }.keys
    }

    private fun savedPeer(): SyncPeer? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        return SyncPeer(
            name = prefs.getString(KEY_NAME, null) ?: host,
            host = host,
            port = prefs.getInt(KEY_PORT, SYNC_HTTP_PORT),
        )
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_NAME = "name"
    }
}
