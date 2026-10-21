package app.pukaar.ui.screens.guides

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pukaar.model.ConnectionStatus
import app.pukaar.ui.components.ConnectionPill
import app.pukaar.ui.components.InfoBox
import app.pukaar.ui.components.OutlineButton
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PukaarCard
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.components.PukaarTopBar
import app.pukaar.ui.components.SectionHeader
import app.pukaar.ui.components.SosPill
import app.pukaar.ui.components.TonalButton
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R

/** Guides (2s): disaster guides and preparedness checklists, all saved on the phone. */
@Composable
fun GuidesScreen(
    connection: ConnectionStatus,
    checked: Map<String, Set<String>>,
    onSos: () -> Unit,
    onNetwork: () -> Unit,
    onGuide: (String) -> Unit,
    onChecklist: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(R.string.pk_tab_guides), actions = { SosPill(onSos) })
        ConnectionPill(connection, Modifier.padding(horizontal = PukaarDimens.space4), onClick = onNetwork)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3),
        ) {
            SectionHeader(stringResource(R.string.pk_guides_what_to_do))
            Guides.forEach { g ->
                GuideRow(g.icon, stringResource(g.title), stringResource(R.string.pk_guides_stages)) { onGuide(g.id) }
            }
            SectionHeader(stringResource(R.string.pk_guides_get_ready))
            Checklists.forEach { c ->
                val total = c.itemIds.size
                val done = checked[c.id].orEmpty().count { it in c.itemIds }
                GuideRow(
                    c.icon,
                    stringResource(c.title),
                    if (total > 0) stringResource(R.string.pk_guides_progress, done, total) else stringResource(R.string.pk_guides_coming),
                    progress = if (total > 0) done / total.toFloat() else null,
                ) { onChecklist(c.id) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2), modifier = Modifier.padding(vertical = PukaarDimens.space2)) {
                PukaarIcon(Sym.downloadDone, null, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.pk_guides_saved), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GuideRow(icon: String, title: String, subtitle: String, progress: Float? = null, onClick: () -> Unit) {
    PukaarCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PukaarDimens.space3) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
            Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                PukaarIcon(icon, null, size = 28.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space1)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.status.confirmed.main,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            PukaarIcon(Sym.chevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Guide detail (2t): Before / During / After tabs with numbered steps. */
@Composable
fun GuideDetailScreen(def: GuideDef, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(GuideStage.During.ordinal) }
    var large by rememberSaveable { mutableStateOf(false) }
    val stage = GuideStage.entries[tab]
    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(def.title), onBack = onBack, actions = {
            TonalButton(stringResource(R.string.pk_guide_large_text), { large = !large }, icon = Sym.textIncrease)
        })
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            GuideStage.entries.forEach { s ->
                Tab(
                    selected = tab == s.ordinal,
                    onClick = { tab = s.ordinal },
                    modifier = Modifier.heightIn(min = PukaarDimens.minTarget),
                    text = { Text(stringResource(s.label), style = MaterialTheme.typography.titleMedium) },
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space4),
        ) {
            val arrayRes = def.steps[stage]
            if (arrayRes == null) {
                InfoBox(Sym.menuBook, stringResource(R.string.pk_guide_not_added), null)
            } else {
                val steps = stringArrayResource(arrayRes)
                val style: TextStyle = if (large) MaterialTheme.typography.headlineSmall.copy(fontWeight = MaterialTheme.typography.bodyLarge.fontWeight)
                else MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.1f)
                steps.forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
                            Text(step, style = style)
                            // Illustration slot: drawings in the brand style are still to come (handoff open items).
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(2.4f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center,
                            ) { PukaarIcon(def.icon, null, size = 32.dp, tint = MaterialTheme.colorScheme.outline) }
                        }
                    }
                }
            }
            Text(
                stringResource(if (def.sample) R.string.pk_guide_source_sample else R.string.pk_guide_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Checklist (2u): grouped tick boxes, a progress bar and Reset. Saved on the phone (FR-24). */
@Composable
fun ChecklistScreen(def: ChecklistDef, checked: Set<String>, onBack: () -> Unit, onToggle: (String, Boolean) -> Unit, onReset: () -> Unit) {
    val total = def.itemIds.size
    val done = checked.count { it in def.itemIds }
    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(def.title), onBack = onBack, actions = {
            if (total > 0) OutlineButton(stringResource(R.string.pk_reset), onReset, icon = Sym.restartAlt)
        })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2),
        ) {
            if (total == 0) {
                InfoBox(Sym.menuBook, stringResource(R.string.pk_checklist_not_added), null)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pk_checklist_progress, done, total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${(done * 100) / total}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.status.confirmed.main)
            }
            LinearProgressIndicator(
                progress = { done / total.toFloat() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 8.dp),
                color = MaterialTheme.status.confirmed.main,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            def.groups.forEach { group ->
                SectionHeader(stringResource(group.title), Modifier.padding(top = PukaarDimens.space2))
                group.items.forEach { item ->
                    val isChecked = item.id in checked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.Checkbox) { onToggle(item.id, !isChecked) }
                            .padding(horizontal = PukaarDimens.space1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.status.confirmed.main,
                                checkmarkColor = MaterialTheme.status.confirmed.onMain,
                            ),
                        )
                        Text(
                            stringResource(item.label),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = PukaarDimens.space2),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
            Box(Modifier.size(PukaarDimens.space4))
        }
    }
}

@PukaarPreviews
@Composable
private fun GuidesPreview() = PreviewTheme {
    GuidesScreen(ConnectionStatus.mesh(4), mapOf("gobag" to setOf("ids", "bank", "water", "food", "medicines", "torch", "whistle")), {}, {}, {}, {})
}

@PukaarPreviews
@Composable
private fun GuideDetailPreview() = PreviewTheme { GuideDetailScreen(Guides.first()) {} }

@PukaarPreviews
@Composable
private fun ChecklistPreview() = PreviewTheme {
    ChecklistScreen(Checklists.first(), setOf("ids", "bank", "water", "food", "medicines", "torch", "whistle"), {}, { _, _ -> }, {})
}
