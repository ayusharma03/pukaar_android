package app.pukaar.ui.screens.chat

import android.content.Context
import app.pukaar.sos.MeshBridge
import com.bitchat.android.services.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Chat messages written while no phone was in range. bitchat broadcasts are fire-and-forget,
 * so these are sent again as soon as a phone comes near ("messages waiting" on Network).
 */
object ChatOutbox {
    private val _waiting = MutableStateFlow<List<String>>(emptyList())
    val waiting: StateFlow<List<String>> = _waiting.asStateFlow()
    private var started = false

    fun add(content: String) {
        _waiting.value = _waiting.value + content
    }

    fun start(context: Context, scope: CoroutineScope) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            AppStateStore.directPeers.collect { peers ->
                if (peers.isEmpty()) return@collect
                val pending = _waiting.value
                if (pending.isEmpty()) return@collect
                _waiting.value = emptyList()
                pending.forEach { MeshBridge.broadcast(app, it) }
            }
        }
    }
}
