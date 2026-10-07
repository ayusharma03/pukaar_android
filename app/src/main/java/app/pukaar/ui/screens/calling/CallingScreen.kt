package app.pukaar.ui.screens.calling

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.pukaar.data.EmergencyContact
import app.pukaar.ui.components.InfoBox
import app.pukaar.ui.components.InitialAvatar
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PrimaryButton
import app.pukaar.ui.components.PukaarCard
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.components.PukaarTopBar
import app.pukaar.ui.components.SectionHeader
import app.pukaar.ui.components.TonalButton
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R

data class EmergencyNumber(val icon: String, val label: Int, val number: String, val highlighted: Boolean = false)

/** National numbers from the design (FR-17). District control comes from Settings. */
fun emergencyNumbers(districtNumber: String) = listOfNotNull(
    EmergencyNumber(Sym.emergency, R.string.pk_call_112, "112", highlighted = true),
    EmergencyNumber(Sym.localPolice, R.string.pk_call_police, "100"),
    EmergencyNumber(Sym.ambulance, R.string.pk_call_ambulance, "108"),
    EmergencyNumber(Sym.localFireDepartment, R.string.pk_call_fire, "101"),
    EmergencyNumber(Sym.woman, R.string.pk_call_women, "1091"),
    districtNumber.takeIf { it.isNotBlank() }?.let { EmergencyNumber(Sym.supportAgent, R.string.pk_call_district, it) },
)

/** Places a call when the Phone permission is granted, otherwise opens the dialler with the number. */
fun placeCall(context: Context, number: String) {
    val uri = Uri.fromParts("tel", number, null)
    val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

/** Emergency calling (2r). */
@Composable
fun CallingScreen(
    hasSignal: Boolean,
    numbers: List<EmergencyNumber>,
    contacts: List<EmergencyContact>,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onSos: () -> Unit,
    onSetDistrict: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(R.string.pk_call_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3),
        ) {
            if (!hasSignal) {
                InfoBox(
                    Sym.signalCellularOff,
                    stringResource(R.string.pk_call_no_signal_body),
                    MaterialTheme.status.warning,
                    title = stringResource(R.string.pk_call_no_signal_title),
                    action = {
                        PrimaryButton(
                            stringResource(R.string.pk_call_send_sos),
                            onSos,
                            icon = Sym.e911Emergency,
                            containerColor = MaterialTheme.status.sosFill,
                            contentColor = MaterialTheme.status.onSosFill,
                            height = PukaarDimens.minTarget,
                        )
                    },
                )
            }

            numbers.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
                    row.forEach { n -> CallCard(n, Modifier.weight(1f)) { onCall(n.number) } }
                    if (row.size == 1) {
                        if (numbers.none { it.label == R.string.pk_call_district }) {
                            SetDistrictCard(Modifier.weight(1f), onSetDistrict)
                        } else Box(Modifier.weight(1f))
                    }
                }
            }
            if (numbers.size % 2 == 0 && numbers.none { it.label == R.string.pk_call_district }) {
                Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
                    SetDistrictCard(Modifier.weight(1f), onSetDistrict)
                    Box(Modifier.weight(1f))
                }
            }

            if (contacts.isNotEmpty()) {
                SectionHeader(stringResource(R.string.pk_call_your_contacts))
                PukaarCard(padding = PukaarDimens.space3) {
                    contacts.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
                            InitialAvatar(c.name)
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                if (c.relation.isNotBlank()) Text(c.relation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TonalButton(stringResource(R.string.pk_call), { onCall(c.phone) }, icon = Sym.call)
                        }
                    }
                }
            }
            Box(Modifier.heightIn(min = PukaarDimens.space4))
        }
    }
}

@Composable
private fun CallCard(n: EmergencyNumber, modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(n.label)
    val description = stringResource(R.string.pk_call_card_description, label, n.number)
    val bg = if (n.highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val fg = if (n.highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .heightIn(min = PukaarDimens.callCardHeight)
            .clip(MaterialTheme.shapes.large)
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(PukaarDimens.space3),
        verticalArrangement = Arrangement.spacedBy(PukaarDimens.space1),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PukaarIcon(n.icon, null, size = 32.dp, tint = if (n.highlighted) fg else MaterialTheme.colorScheme.primary)
            PukaarIcon(Sym.call, null, size = 20.dp, tint = fg)
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = fg)
        Text(n.number, style = MaterialTheme.typography.headlineSmall, color = fg)
    }
}

@Composable
private fun SetDistrictCard(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .heightIn(min = PukaarDimens.callCardHeight)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(PukaarDimens.space3),
        verticalArrangement = Arrangement.spacedBy(PukaarDimens.space1),
    ) {
        PukaarIcon(Sym.supportAgent, null, size = 32.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.pk_call_district), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.pk_call_district_set), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@PukaarPreviews
@Composable
private fun CallingPreview() = PreviewTheme {
    CallingScreen(
        hasSignal = false,
        numbers = emergencyNumbers("06272 245 001"),
        contacts = listOf(EmergencyContact("1", "Suresh Kumar", "9845012399", "Husband"), EmergencyContact("2", "Priya Singh", "9931044821", "Sister")),
        onBack = {}, onCall = {}, onSos = {}, onSetDistrict = {},
    )
}
