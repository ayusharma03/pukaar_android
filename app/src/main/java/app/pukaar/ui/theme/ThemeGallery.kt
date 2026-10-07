package app.pukaar.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitchat.android.R

// A quick visual check that the theme, fonts and icons are wired up correctly.
// Open this file in Android Studio and look at the Preview pane. Not shipped in the UI.

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Gallery() {
    val s = MaterialTheme.status
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_pukaar_mark), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Text("  Pukaar  पुकार", style = MaterialTheme.typography.headlineMedium)
            }
            Text("Help that travels without a network", style = MaterialTheme.typography.bodyLarge)
            Text("मदद को सूचना मिल गई है", style = MaterialTheme.typography.displaySmall, color = s.confirmed.main)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("SOS", s.sos.main, s.sos.onMain)
                Swatch("Mesh", s.mesh.main, s.mesh.onMain)
                Swatch("Radio", s.radio.main, s.radio.onMain)
                Swatch("OK", s.confirmed.main, s.confirmed.onMain)
                Swatch("Warn", s.warning.main, s.warning.onMain)
                Swatch("Primary", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Sym.sos, Sym.nightShelter, Sym.flood, Sym.earthquake, Sym.explore, Sym.forum, Sym.call, Sym.verified)
                    .forEach { PukaarIcon(it, null, size = 28.dp) }
                PukaarIcon(Sym.home, null, size = 28.dp, filled = true)
                Icon(painterResource(R.drawable.ic_mesh), null, tint = s.mesh.main, modifier = Modifier.size(28.dp))
                Icon(painterResource(R.drawable.ic_radio), null, tint = s.radio.main, modifier = Modifier.size(28.dp))
            }

            Box(
                Modifier.size(PukaarDimens.sosButtonSize).clip(CircleShape).background(s.sosFill),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SOS", style = PukaarTextStyles.sosButtonLabel, color = s.onSosFill)
                    Text("Tap for help", style = PukaarTextStyles.sosButtonHint, color = s.onSosFill)
                }
            }
        }
    }
}

@Composable
private fun Swatch(label: String, bg: Color, fg: Color) {
    Box(Modifier.clip(MaterialTheme.shapes.small).background(bg).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(name = "Dark", widthDp = 360, heightDp = 800)
@Composable
private fun GalleryDark() = PukaarTheme(PukaarThemeMode.Dark) { Gallery() }

@Preview(name = "Light", widthDp = 360, heightDp = 800)
@Composable
private fun GalleryLight() = PukaarTheme(PukaarThemeMode.Light) { Gallery() }

@Preview(name = "Sunlight", widthDp = 360, heightDp = 800)
@Composable
private fun GallerySunlight() = PukaarTheme(PukaarThemeMode.Sunlight) { Gallery() }
