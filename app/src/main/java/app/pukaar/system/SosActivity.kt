package app.pukaar.system

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.pukaar.PukaarIntents
import app.pukaar.data.PukaarStore
import app.pukaar.sos.SosManager
import app.pukaar.sos.SosNotifier
import app.pukaar.sos.SosStage
import app.pukaar.ui.components.DeliveryState
import app.pukaar.ui.components.OutlineButton
import app.pukaar.ui.components.PrimaryButton
import app.pukaar.ui.screens.home.toDelivery
import app.pukaar.ui.screens.sos.SosCountdownRoute
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarTheme
import app.pukaar.ui.theme.status
import com.bitchat.android.MainActivity
import com.bitchat.android.R

/**
 * SOS countdown that can show over the lock screen, for the Quick Settings tile, widgets and shake
 * (FR-13). Shows no personal data; after sending, a short status and a way into the app.
 */
class SosActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        SosNotifier.cancelShake(this)

        // An SOS is already running: go straight to its status.
        if (SosManager.active.value?.closed == false) {
            openApp(PukaarIntents.ROUTE_SOS_STATUS)
            finish()
            return
        }

        setContent {
            val settings by PukaarStore.settings.collectAsState()
            PukaarTheme(settings.theme) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    var sent by rememberSaveable { mutableStateOf(false) }
                    if (!sent) {
                        SosCountdownRoute(practice = false, onCancel = { finish() }, onSent = { sent = true })
                    } else {
                        SentScreen(onOpen = { openApp(PukaarIntents.ROUTE_SOS_STATUS) }, onClose = { finish() })
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ShakeTrigger.countdownShowing = true
    }

    override fun onStop() {
        super.onStop()
        ShakeTrigger.countdownShowing = false
    }

    /** Opens Pukaar, asking the user to unlock first if the phone is locked. */
    private fun openApp(route: String) {
        val launch = {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(PukaarIntents.EXTRA_ROUTE, route),
            )
            finish()
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launch()
            })
        } else {
            launch()
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, SosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun SentScreen(onOpen: () -> Unit, onClose: () -> Unit) {
    val sos by SosManager.active.collectAsState()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(PukaarDimens.space5),
        verticalArrangement = Arrangement.spacedBy(PukaarDimens.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.pk_sos_hero_sending), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, color = MaterialTheme.status.sos.main)
        Text(stringResource(R.string.pk_lock_sent_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        sos?.let { DeliveryState(it.stage.toDelivery(it.relayPeers)) } ?: DeliveryState(SosStage.Sending.toDelivery(0))
        PrimaryButton(stringResource(R.string.pk_lock_open_app), onOpen, Modifier.fillMaxWidth())
        OutlineButton(stringResource(R.string.pk_close), onClose, Modifier.fillMaxWidth())
    }
}
