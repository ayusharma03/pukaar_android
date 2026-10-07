package app.pukaar.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.PukaarTheme
import app.pukaar.ui.theme.PukaarThemeMode
import app.pukaar.ui.theme.StatusColor
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R

/** Dark and light previews at the 360 × 800 dp design frame. Wrap content in [PreviewTheme]. */
@Preview(name = "Dark", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Light", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class PukaarPreviews

/** Follows the preview's uiMode, so [PukaarPreviews] shows both themes. */
@Composable
fun PreviewTheme(content: @Composable () -> Unit) = PukaarTheme(PukaarThemeMode.System) {
    Surface(color = MaterialTheme.colorScheme.surface, content = content)
}

/** 64 dp top app bar with an optional back button and trailing actions. */
@Composable
fun PukaarTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(start = if (onBack == null) PukaarDimens.space4 else PukaarDimens.space1, end = PukaarDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                PukaarIcon(Sym.arrowBack, stringResource(R.string.pk_back))
            }
        }
        Column(Modifier.weight(1f).semantics { heading() }) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1), content = actions)
    }
}

/** Red "SOS" pill for top bars on Chat and Guides: SOS is always reachable (handoff global rules). */
@Composable
fun SosPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = MaterialTheme.status
    Box(
        modifier
            .heightIn(min = PukaarDimens.minTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(s.sosFill)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1),
        ) {
            // Text only: the Material Symbols "sos" glyph is itself the letters SOS.
            Text(stringResource(R.string.pk_sos), style = MaterialTheme.typography.labelLarge, color = s.onSosFill)
        }
    }
}

/** Filled 56 dp primary action with an optional leading icon. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    height: Dp = PukaarDimens.primaryButtonHeight,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = PukaarDimens.space5, vertical = PukaarDimens.space2),
    ) { ButtonContent(text, icon) }
}

@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, enabled: Boolean = true) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = PukaarDimens.minTarget),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = PukaarDimens.space4, vertical = PukaarDimens.space2),
    ) { ButtonContent(text, icon) }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = PukaarDimens.minTarget),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = PukaarDimens.space4, vertical = PukaarDimens.space2),
    ) { ButtonContent(text, icon) }
}

@Composable
fun PlainButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = PukaarDimens.minTarget), shape = CircleShape) {
        ButtonContent(text, icon)
    }
}

@Composable
private fun ButtonContent(text: String, icon: String?) {
    if (icon != null) {
        PukaarIcon(icon, null, size = 20.dp)
        Box(Modifier.width(PukaarDimens.space2))
    }
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = PukaarDimens.space2).semantics { heading() },
    )
}

/** A rounded card on a surfaceContainer level (depth without shadows). */
@Composable
fun PukaarCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    padding: Dp = PukaarDimens.space4,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Cards in the designs always span the content width.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = color,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2), content = content)
    }
}

/** Icon plus text on a status container, for warnings and tips. */
@Composable
fun InfoBox(
    icon: String,
    text: String,
    colors: StatusColor?,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val container = colors?.container ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val content = colors?.onContainer ?: MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3), verticalAlignment = Alignment.Top) {
            PukaarIcon(icon, null, tint = content)
            Column(Modifier.weight(1f)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = content)
                Text(text, style = MaterialTheme.typography.bodyLarge, color = content)
            }
        }
        if (action != null) Box(Modifier.padding(start = 36.dp)) { action() }
    }
}

/** Small "On"/"Off" style tag. */
@Composable
fun Tag(text: String, colors: StatusColor?, modifier: Modifier = Modifier) {
    val bg = colors?.container ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = colors?.onContainer ?: MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .padding(horizontal = PukaarDimens.space2, vertical = 2.dp),
    )
}

/** A tappable settings or list row, at least 56 dp tall. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String? = null,
    iconPainter: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = PukaarDimens.space4, vertical = PukaarDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space4),
    ) {
        when {
            icon != null -> PukaarIcon(icon, null, tint = iconTint)
            iconPainter != null -> Icon(painterResource(iconPainter), null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** Letter avatar used for contacts and chat senders. */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Onboarding progress: step [current] of [total], as dots. */
@Composable
fun StepDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.pk_step_of, current, total)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val active = i < current
            Box(
                Modifier
                    .size(width = if (i == current - 1) 20.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

/** Single-choice segmented row of chips (theme, sensitivity, blood group). */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2),
        verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                Modifier
                    .heightIn(min = PukaarDimens.minTarget)
                    .clip(MaterialTheme.shapes.small)
                    .then(
                        if (isSelected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                    )
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = PukaarDimens.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1),
            ) {
                if (isSelected) PukaarIcon(Sym.check, null, size = 18.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@PukaarPreviews
@Composable
private fun CommonPreview() = PreviewTheme {
    Column(verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
        PukaarTopBar("Disaster Relief", subtitle = "18 phones reachable · rescuers can read this", actions = { SosPill({}) })
        Column(Modifier.padding(horizontal = PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
            PrimaryButton("Continue", {}, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
                TonalButton("Pick from contacts", {}, icon = Sym.contacts)
                OutlineButton("Add", {}, icon = Sym.personAdd)
            }
            InfoBox(Sym.warning, "The arrow shows a straight line, not a route.", MaterialTheme.status.warning)
            StepDots(3, 7)
            ChoiceChips(listOf("Dark", "Light", "Sunlight", "System"), "Dark", { it }, {})
            ListRow("Language", subtitle = "English", icon = Sym.translate, onClick = {}, trailing = { Tag("On", MaterialTheme.status.confirmed) })
        }
    }
}
