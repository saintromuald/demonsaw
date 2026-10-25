package org.demonsaw.app

import android.content.Context
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

    fun connect(context: Context, host: String, port: Int, name: String, passphrase: String) {
        viewModelScope.launch {
            _state.update { it.copy(connecting = true, error = null) }

            val started = withContext(dispatcher) {
                try {
                    val config = File(context.filesDir, "client.toml")
                    config.writeText(buildConfig(host, port, name, passphrase))
                    Native.start(config.absolutePath)
                } catch (t: Throwable) {
                    false
                }
            }

            if (!started) {
                _state.update { it.copy(connecting = false, error = "Could not start the client. Check the router address.") }
                return@launch
            }

            context.getSharedPreferences("demonsaw", Context.MODE_PRIVATE).edit()
                .putString("host", host)
                .putInt("port", port)
                .putString("name", name)
                .putString("passphrase", passphrase)
                .apply()

            startPolling()
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { withContext(dispatcher) { Native.chat(text.trim()) } }
    }

    fun browse() {
        viewModelScope.launch { withContext(dispatcher) { Native.browse() } }
    }

    fun join() {
        viewModelScope.launch { withContext(dispatcher) { Native.join() } }
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        viewModelScope.launch {
            withContext(dispatcher) { Native.stop() }
            _state.value = UiState()
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
        super.onCleared()
    }
}
