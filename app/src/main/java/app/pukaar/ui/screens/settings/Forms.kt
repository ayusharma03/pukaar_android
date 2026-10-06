package app.pukaar.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pukaar.data.BloodGroupUnknown
import app.pukaar.data.BloodGroups
import app.pukaar.data.ContactTestStatus
import app.pukaar.data.EmergencyContact
import app.pukaar.data.MaxEmergencyContacts
import app.pukaar.data.Profile
import app.pukaar.data.PukaarStore
import app.pukaar.sos.SmsSender
import app.pukaar.ui.components.ChoiceChips
import app.pukaar.ui.components.InitialAvatar
import app.pukaar.ui.components.OutlineButton
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PukaarCard
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.components.SectionHeader
import app.pukaar.ui.components.TonalButton
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.PukaarTextStyles
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R
import java.util.Date

/** Name, phone, blood group and medical notes (onboarding 1.5, Settings > Profile). */
@Composable
fun ProfileForm(profile: Profile, onChange: (Profile) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
        OutlinedTextField(
            value = profile.name,
            onValueChange = { onChange(profile.copy(name = it)) },
            label = { Text(stringResource(R.string.pk_profile_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        OutlinedTextField(
            value = profile.phone,
            onValueChange = { onChange(profile.copy(phone = it)) },
            label = { Text(stringResource(R.string.pk_profile_phone)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        SectionHeader(stringResource(R.string.pk_profile_blood))
        ChoiceChips(
            options = BloodGroups + BloodGroupUnknown,
            selected = profile.bloodGroup,
            label = { if (it == BloodGroupUnknown) stringResource(R.string.pk_profile_blood_unknown) else it },
            onSelect = { onChange(profile.copy(bloodGroup = if (profile.bloodGroup == it) null else it)) },
        )
        OutlinedTextField(
            value = profile.medicalNotes,
            onValueChange = { onChange(profile.copy(medicalNotes = it)) },
            label = { Text(stringResource(R.string.pk_profile_notes)) },
            placeholder = { Text(stringResource(R.string.pk_profile_notes_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/** Reads name and number from a picked phone-book entry. */
private fun readPickedContact(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null,
    )?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "") to (c.getString(1) ?: "") else null }
}.getOrNull()

/**
 * Emergency contacts list with pick, add, remove and (optionally) test SMS.
 * Used by onboarding 1.6 and Settings > Emergency contacts (2w).
 */
@Composable
fun ContactsEditor(contacts: List<EmergencyContact>, showTests: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EmergencyContact?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val picked = readPickedContact(context, uri)
            if (picked == null || !PukaarStore.addContact(picked.first, picked.second)) message = R.string.pk_contacts_not_added
        }
    }
    val full = contacts.size >= MaxEmergencyContacts

    Column(modifier, verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
        contacts.forEach { c -> ContactCard(c, showTests, onEdit = { editing = c }) }
        Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
            TonalButton(
                stringResource(R.string.pk_contacts_pick),
                { picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) },
                Modifier.weight(1f),
                icon = Sym.contacts,
                enabled = !full,
            )
            OutlineButton(stringResource(R.string.pk_contacts_add_manually), { showAdd = true }, Modifier.weight(1f), icon = Sym.personAdd, enabled = !full)
        }
        Text(
            if (showTests && !full) stringResource(R.string.pk_contacts_count_more, contacts.size, MaxEmergencyContacts, MaxEmergencyContacts - contacts.size)
            else stringResource(R.string.pk_contacts_count, contacts.size, MaxEmergencyContacts),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showAdd) AddContactDialog(onDismiss = { showAdd = false }) { name, phone, relation ->
        if (!PukaarStore.addContact(name, phone, relation)) message = R.string.pk_contacts_not_added
        showAdd = false
    }
    editing?.let { c ->
        AddContactDialog(onDismiss = { editing = null }, initial = c) { name, phone, relation ->
            val numberChanged = PukaarStore.normalizePhone(phone) != PukaarStore.normalizePhone(c.phone)
            PukaarStore.updateContact(
                c.copy(
                    name = name.trim(), phone = phone.trim(), relation = relation.trim(),
                    // A new number hasn't been tested yet.
                    testStatus = if (numberChanged) ContactTestStatus.NotTested else c.testStatus,
                    testedAt = if (numberChanged) null else c.testedAt,
                ),
            )
            editing = null
        }
    }
    message?.let { res ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(stringResource(res, MaxEmergencyContacts)) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.pk_ok)) } },
        )
    }
}

@Composable
private fun ContactCard(c: EmergencyContact, showTests: Boolean, onEdit: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    PukaarCard(Modifier.fillMaxWidth(), padding = PukaarDimens.space3) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
            InitialAvatar(c.name)
            Column(Modifier.weight(1f)) {
                Text(c.name.ifBlank { c.phone }, style = MaterialTheme.typography.titleMedium)
                Text(listOf(c.relation, c.phone).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showTests) {
                Box {
                    IconButton(onClick = { menu = true }) { PukaarIcon(Sym.moreVert, stringResource(R.string.pk_more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.pk_contacts_edit)) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.pk_contacts_send_test)) }, onClick = { menu = false; sendTest(context, c) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.pk_remove)) }, onClick = { menu = false; PukaarStore.removeContact(c.id) })
                    }
                }
            } else {
                IconButton(onClick = { PukaarStore.removeContact(c.id) }) { PukaarIcon(Sym.close, stringResource(R.string.pk_contacts_remove_named, c.name)) }
            }
        }
        if (showTests) TestStatusLine(c) { sendTest(context, c) }
    }
}

@Composable
private fun TestStatusLine(c: EmergencyContact, onSend: () -> Unit) {
    val s = MaterialTheme.status
    val context = LocalContext.current
    val date = c.testedAt?.let { DateFormat.getMediumDateFormat(context).format(Date(it)) } ?: ""
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1), modifier = Modifier.padding(start = 52.dp)) {
        val (icon, text, color) = when (c.testStatus) {
            ContactTestStatus.Delivered -> Triple(Sym.checkCircle, stringResource(R.string.pk_contacts_test_delivered, date), s.confirmed.main)
            ContactTestStatus.Sent -> Triple(Sym.check, stringResource(R.string.pk_contacts_test_sent, date), s.confirmed.main)
            ContactTestStatus.Sending -> Triple(Sym.schedule, stringResource(R.string.pk_contacts_test_sending), MaterialTheme.colorScheme.onSurfaceVariant)
            ContactTestStatus.Failed -> Triple(Sym.warning, stringResource(R.string.pk_contacts_test_failed), s.warning.main)
            ContactTestStatus.NotTested -> Triple(Sym.help, stringResource(R.string.pk_contacts_test_none), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PukaarIcon(icon, null, size = 16.dp, tint = color)
        Text(text, style = PukaarTextStyles.deliveryState, color = color, modifier = Modifier.weight(1f))
        if (c.testStatus == ContactTestStatus.NotTested || c.testStatus == ContactTestStatus.Failed) {
            TextButton(onClick = onSend) { Text(stringResource(R.string.pk_contacts_send_test)) }
        }
    }
}

private fun sendTest(context: Context, c: EmergencyContact) {
    if (!SmsSender.canSend(context)) {
        PukaarStore.updateContact(c.copy(testStatus = ContactTestStatus.Failed))
        return
    }
    val name = PukaarStore.profile.value.name.ifBlank { context.getString(R.string.pk_someone) }
    PukaarStore.updateContact(c.copy(testStatus = ContactTestStatus.Sending))
    SmsSender.send(
        context, c.phone, context.getString(R.string.pk_sms_test, name),
        onSent = { ok ->
            val current = PukaarStore.contacts.value.firstOrNull { it.id == c.id } ?: return@send
            PukaarStore.updateContact(current.copy(testStatus = if (ok) ContactTestStatus.Sent else ContactTestStatus.Failed, testedAt = System.currentTimeMillis()))
        },
        onDelivered = {
            val current = PukaarStore.contacts.value.firstOrNull { it.id == c.id } ?: return@send
            PukaarStore.updateContact(current.copy(testStatus = ContactTestStatus.Delivered, testedAt = System.currentTimeMillis()))
        },
    )
}

@Composable
private fun AddContactDialog(onDismiss: () -> Unit, initial: EmergencyContact? = null, onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var relation by remember { mutableStateOf(initial?.relation.orEmpty()) }
    val valid = PukaarStore.normalizePhone(phone).count { it.isDigit() } >= 5
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.pk_contacts_add_title else R.string.pk_contacts_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.pk_profile_name)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.pk_profile_phone)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = phone.isNotBlank() && !valid)
                OutlinedTextField(relation, { relation = it }, label = { Text(stringResource(R.string.pk_contacts_relation)) },
                    placeholder = { Text(stringResource(R.string.pk_contacts_relation_hint)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, phone, relation) }, enabled = valid) { Text(stringResource(if (initial == null) R.string.pk_add else R.string.pk_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pk_cancel)) } },
    )
}

@PukaarPreviews
@Composable
private fun FormsPreview() = PreviewTheme {
    Column(Modifier.padding(PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space4)) {
        ProfileForm(Profile("Meena Kumari", "+91 98450 12345", "B+", ""), {})
    }
}
