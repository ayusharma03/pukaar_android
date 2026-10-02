package app.pukaar.data

import app.pukaar.ui.theme.PukaarThemeMode

/** What rescuers see with an SOS (onboarding 1.5, Settings > Profile). */
data class Profile(
    val name: String = "",
    val phone: String = "",
    /** One of [BloodGroups], "unknown", or null when not given. */
    val bloodGroup: String? = null,
    val medicalNotes: String = "",
)

val BloodGroups = listOf("A+", "A−", "B+", "B−", "O+", "O−", "AB+", "AB−")
const val BloodGroupUnknown = "unknown"

enum class ContactTestStatus { NotTested, Sending, Sent, Delivered, Failed }

data class EmergencyContact(
    val id: String,
    val name: String,
    val phone: String,
    val relation: String = "",
    val testStatus: ContactTestStatus = ContactTestStatus.NotTested,
    val testedAt: Long? = null,
)

const val MaxEmergencyContacts = 5

enum class ShakeSensitivity { Low, Medium, High }

data class PukaarSettings(
    val theme: PukaarThemeMode = PukaarThemeMode.Dark,
    val shakeEnabled: Boolean = true,
    val shakeSensitivity: ShakeSensitivity = ShakeSensitivity.Medium,
    val countdownSeconds: Int = 5,
    val batterySaverAuto: Boolean = true,
    val batterySaverNow: Boolean = false,
    /** Configurable number for the "District control" calling card. */
    val districtControlNumber: String = "",
)

val CountdownChoices = listOf(3, 5, 10)

enum class OfflineItemKind { Map, Places, Guides }

/** One downloadable piece of an offline area (onboarding 1.7, Settings > Offline data). */
data class OfflineItem(
    val kind: OfflineItemKind,
    val sizeMb: Int,
    /** True when the data is on this phone (bundled or downloaded). */
    val saved: Boolean,
)

data class OfflineRegion(
    val id: String,
    val name: String,
    val items: List<OfflineItem>,
    val updatedAt: Long? = null,
) {
    val totalMb: Int get() = items.sumOf { it.sizeMb }
    val missingMb: Int get() = items.filterNot { it.saved }.sumOf { it.sizeMb }
}
