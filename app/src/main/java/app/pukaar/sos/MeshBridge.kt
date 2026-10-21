package app.pukaar.sos

import android.content.Context
import android.util.Log
import com.bitchat.android.service.MeshServiceHolder

/** The one place Pukaar talks to bitchat's mesh: public broadcasts on the mesh timeline. */
object MeshBridge {
    fun broadcast(context: Context, content: String): Boolean = runCatching {
        MeshServiceHolder.getUnifiedOrCreate(context.applicationContext).sendMessage(content)
        true
    }.getOrElse {
        Log.w("PukaarMesh", "Mesh send failed: ${it.message}")
        false
    }

    fun myPeerId(context: Context): String? =
        runCatching { MeshServiceHolder.getUnifiedOrCreate(context.applicationContext).myPeerID }.getOrNull()
}
