package app.pukaar.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R

// Material Symbols Rounded (weight 400, grade 0, optical size 24), bundled offline.
// The fonts contain only the 80 icons used in the Pukaar designs.
// To add an icon: regenerate the fonts with tools/make_icon_fonts.sh and add its constant here.

private val SymbolsOutlined = FontFamily(Font(R.font.material_symbols_rounded_fill0))
private val SymbolsFilled = FontFamily(Font(R.font.material_symbols_rounded_fill1))

/** Icon names from the designs, mapped to their Material Symbols codepoints. */
object Sym {
    const val add = "\uE145"
    const val addLocationAlt = "\uEF3A"
    const val ambulance = "\uF803"
    const val arrowBack = "\uE5C4"
    const val arrowForward = "\uE5C8"
    const val backpack = "\uF19C"
    const val battery3Bar = "\uF09E"
    const val battery5Bar = "\uF0A0"
    const val batterySaver = "\uEFDE"
    const val bluetooth = "\uE1A7"
    const val brightnessLow = "\uE1AD"
    const val call = "\uF0D4"
    const val chat = "\uE0C9"
    const val check = "\uE668"
    const val checkCircle = "\uF0BE"
    const val chevronRight = "\uE5CC"
    const val close = "\uE5CD"
    const val cloudDone = "\uE2BF"
    const val contacts = "\uE0BA"
    const val delete = "\uE92E"
    const val doNotDisturbOn = "\uF08F"
    const val download = "\uF090"
    const val downloadDone = "\uF091"
    const val e911Emergency = "\uF119"
    const val earthquake = "\uF64F"
    const val edit = "\uF097"
    const val emergency = "\uE1EB"
    const val energySavingsLeaf = "\uEC1A"
    const val explore = "\uE87A"
    const val flashlightOn = "\uF00B"
    const val flood = "\uEBE6"
    const val forum = "\uE8AF"
    const val group = "\uEA21"
    const val help = "\uE8FD"
    const val home = "\uE9B2"
    const val homeHealth = "\uE4B9"
    const val info = "\uE88E"
    const val lightbulb = "\uE90F"
    const val localFireDepartment = "\uEF55"
    const val localHospital = "\uE548"
    const val localPolice = "\uEF56"
    const val locationOn = "\uF1DB"
    const val map = "\uE55B"
    const val menuBook = "\uEA19"
    const val moreVert = "\uE5D4"
    const val myLocation = "\uE55C"
    const val nightShelter = "\uF1F1"
    const val notifications = "\uE7F5"
    const val person = "\uF0D3"
    const val personAdd = "\uEA4D"
    const val photoCamera = "\uE412"
    const val playArrow = "\uE037"
    const val remove = "\uE15B"
    const val restartAlt = "\uF053"
    const val schedule = "\uEFD6"
    const val scheduleSend = "\uEA0A"
    const val search = "\uEF7A"
    const val send = "\uE163"
    const val sendToMobile = "\uF2D2"
    const val settings = "\uE8B8"
    const val signalCellularAlt = "\uE202"
    const val signalCellularOff = "\uE1D0"
    const val smartphone = "\uE7BA"
    const val sms = "\uE625"
    const val sos = "\uEBF7"
    const val supportAgent = "\uF0E2"
    const val textIncrease = "\uEAE2"
    const val timer = "\uE425"
    const val toggleOn = "\uE9F6"
    const val translate = "\uE8E2"
    const val verified = "\uEF76"
    const val verifiedUser = "\uF013"
    const val vibration = "\uF2CB"
    const val visibility = "\uE8F4"
    const val volunteerActivism = "\uEA70"
    const val warning = "\uF083"
    const val widgets = "\uE1BD"
    const val wifi = "\uE63E"
    const val wifiOff = "\uE648"
    const val woman = "\uE13E"
}

/**
 * Draws a Material Symbols Rounded icon.
 * filled = true for the selected navigation item and active states.
 * Icons don't grow with the user's font size; text does.
 */
@Composable
fun PukaarIcon(
    icon: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    filled: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Box(modifier.size(size).then(semantics), contentAlignment = Alignment.Center) {
        Text(
            text = icon,
            color = tint,
            style = TextStyle(
                fontFamily = if (filled) SymbolsFilled else SymbolsOutlined,
                fontSize = (size.value / fontScale).sp,
                lineHeight = (size.value / fontScale).sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
