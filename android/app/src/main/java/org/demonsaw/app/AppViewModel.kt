package org.demonsaw.app

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

data class UiState(
    val connecting: Boolean = false,
    val snapshot: Snapshot = Snapshot(),
    val error: String? = null,
    val notice: String? = null,
) {
    val running: Boolean get() = snapshot.running
}

class AppViewModel : ViewModel() {
    private val dispatcher = Executors
        .newSingleThreadExecutor { runnable -> Thread(runnable, "demonsaw-native") }
        .asCoroutineDispatcher()

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var pollJob: Job? = null

    @Volatile
    private var appContext: Context? = null

    fun connect(context: Context, host: String, port: Int, name: String, passphrase: String) {
        viewModelScope.launch {
            _state.update { it.copy(connecting = true, error = null) }

            val started = withContext(dispatcher) {
                try {
                    val config = File(context.filesDir, "client.toml")
                    config.writeText(buildConfig(host, port, name, passphrase))
                    if (Native.start(config.absolutePath)) {
                        val downloads = defaultDownloadPath(context)
                        Native.command(Native.CMD_SET_DOWNLOAD_PATH, downloads, "")
                        true
                    } else {
                        false
                    }
                } catch (t: Throwable) {
                    false
                }
            }

            if (!started) {
                _state.update { it.copy(connecting = false, error = "Could not start the client. Check the router address.") }
                return@launch
            }

            appContext = context.applicationContext
            ForegroundService.start(context.applicationContext)

            context.getSharedPreferences("demonsaw", Context.MODE_PRIVATE).edit()
                .putString("host", host)
                .putInt("port", port)
                .putString("name", name)
                .putString("passphrase", passphrase)
                .apply()

            startPolling()
        }
    }

    fun send(text: String) = command(Native.CMD_CHAT, text, "")

    fun sendPm(clientId: String, text: String) = command(Native.CMD_PM, clientId, text)

    fun sendRoom(roomId: String, text: String) = command(Native.CMD_ROOM_CHAT, roomId, text)

    fun joinRoom(roomId: String) = command(Native.CMD_ROOM_JOIN, roomId, "")

    fun quitRoom(roomId: String) = command(Native.CMD_ROOM_QUIT, roomId, "")

    fun browse(key: String) = command(Native.CMD_BROWSE, key, "")

    fun mute(clientId: String, value: Boolean) = command(Native.CMD_MUTE, clientId, if (value) "1" else "0")

    fun shareAdd(path: String) {
        command(Native.CMD_SHARE_ADD, path, "") { ok ->
            if (!ok) _state.update { it.copy(notice = "Path not found: $path") }
        }
    }

    fun shareRemove(path: String) = command(Native.CMD_SHARE_REMOVE, path, "")

    fun download(key: String) = command(Native.CMD_DOWNLOAD, key, "")

    fun downloadAll(key: String) = command(Native.CMD_DOWNLOAD_ALL, key, "")

    fun cancelDownload(id: String) = command(Native.CMD_CANCEL_DOWNLOAD, id, "")

    fun cancelUpload(id: String) = command(Native.CMD_CANCEL_UPLOAD, id, "")

    fun setDownloadPath(path: String) = command(Native.CMD_SET_DOWNLOAD_PATH, path, "")

    fun search(text: String) = command(Native.CMD_SEARCH, text, "")

    fun join() = command(Native.CMD_JOIN, "", "")

    fun refreshRooms() = command(Native.CMD_ROOMS_REFRESH, "", "")

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        viewModelScope.launch {
            withContext(dispatcher) { Native.stop() }
            appContext?.let { ForegroundService.stop(it) }
            appContext = null
            _state.value = UiState()
        }
    }

    private fun command(type: Int, a: String, b: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(dispatcher) {
                try {
                    Native.command(type, a, b)
                } catch (t: Throwable) {
                    "{\"ok\":false}"
                }
            }
            onResult(result.contains("\"ok\":true"))
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val snapshot = withContext(dispatcher) {
                    try {
                        parseSnapshot(Native.snapshot())
                    } catch (t: Throwable) {
                        Snapshot()
                    }
                }
                _state.update { it.copy(snapshot = snapshot, connecting = false) }
                delay(700)
            }
        }
    }

    private fun buildConfig(host: String, port: Int, name: String, passphrase: String): String {
        val safeName = escape(name.ifBlank { "Android" })
        val safeHost = escape(host)
        val safePass = escape(passphrase)

        return buildString {
            appendLine("[[client]]")
            appendLine("\tenabled = true")
            appendLine("\tname = \"$safeName\"")
            appendLine("\tcolor = \"0c9bdc\"")
            appendLine("\t[[client.router]]")
            appendLine("\t\tenabled = true")
            appendLine("\t\tname = \"Router\"")
            appendLine("\t\taddress = \"$safeHost\"")
            appendLine("\t\tport = $port")
            if (passphrase.isNotBlank()) {
                appendLine("\t[[client.group]]")
                appendLine("\t\tenabled = true")
                appendLine("\t\tentropy = \"$safePass\"")
            }
        }
    }

    private fun escape(value: String): String = buildString {
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        Native.stop()
        appContext?.let { ForegroundService.stop(it) }
        super.onCleared()
    }

    companion object {
        fun defaultDownloadPath(context: Context): String {
            val external = if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
                File(Environment.getExternalStorageDirectory(), "Download/Demonsaw")
            } else {
                null
            }
            val dir = external ?: File(context.getExternalFilesDir(null) ?: context.filesDir, "Downloads")
            dir.mkdirs()
            return dir.absolutePath
        }
    }
}
