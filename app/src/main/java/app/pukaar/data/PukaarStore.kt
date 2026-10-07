package app.pukaar.data

import android.content.Context
import android.content.SharedPreferences
import app.pukaar.ui.theme.PukaarThemeMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Everything Pukaar saves on the phone: profile, contacts, settings, checklists and onboarding.
 * Backed by SharedPreferences so it works offline and survives restarts. Read through the flows.
 */
object PukaarStore {
    private const val PREFS = "pukaar"
    private val gson = Gson()
    private lateinit var prefs: SharedPreferences

    private val _onboardingDone = MutableStateFlow(false)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    private val _settings = MutableStateFlow(PukaarSettings())
    val settings: StateFlow<PukaarSettings> = _settings.asStateFlow()

    /** Checklist id → ticked item ids. */
    private val _checklists = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val checklists: StateFlow<Map<String, Set<String>>> = _checklists.asStateFlow()

    /** Offline downloads the user chose "Later" for, to download when internet returns. */
    private val _offlinePending = MutableStateFlow(false)
    val offlinePending: StateFlow<Boolean> = _offlinePending.asStateFlow()

    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            load()
            initialized = true
        }
    }

    private fun load() {
        _onboardingDone.value = prefs.getBoolean("onboarding_done", false)
        loadChatSeen()
        _offlinePending.value = prefs.getBoolean("offline_pending", false)
        _profile.value = Profile(
            name = prefs.getString("profile_name", "") ?: "",
            phone = prefs.getString("profile_phone", "") ?: "",
            bloodGroup = prefs.getString("profile_blood", null),
            medicalNotes = prefs.getString("profile_notes", "") ?: "",
        )
        _contacts.value = readJson<List<EmergencyContact>>("contacts", object : TypeToken<List<EmergencyContact>>() {})
            ?.map { it.sanitized() }
            ?: emptyList()
        _settings.value = PukaarSettings(
            theme = enumOr(prefs.getString("theme", null), PukaarThemeMode.Dark),
            shakeEnabled = prefs.getBoolean("shake_enabled", true),
            shakeSensitivity = enumOr(prefs.getString("shake_sensitivity", null), ShakeSensitivity.Medium),
            countdownSeconds = prefs.getInt("countdown_seconds", 5).coerceIn(3, 10),
            batterySaverAuto = prefs.getBoolean("battery_saver_auto", true),
            batterySaverNow = prefs.getBoolean("battery_saver_now", false),
            districtControlNumber = prefs.getString("district_control", "") ?: "",
        )
        _checklists.value = readJson<Map<String, List<String>>>("checklists", object : TypeToken<Map<String, List<String>>>() {})
            ?.mapValues { it.value.toSet() }
            ?: emptyMap()
    }

    // Gson bypasses Kotlin defaults, so guard fields that may be missing in older saves.
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun EmergencyContact.sanitized() = copy(
        id = id ?: UUID.randomUUID().toString(),
        name = name ?: "",
        phone = phone ?: "",
        relation = relation ?: "",
        testStatus = testStatus ?: ContactTestStatus.NotTested,
    )

    private fun <T> readJson(key: String, type: TypeToken<T>): T? =
        prefs.getString(key, null)?.let { runCatching { gson.fromJson<T>(it, type.type) }.getOrNull() }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    // MARK: onboarding

    fun setOnboardingDone() {
        prefs.edit().putBoolean("onboarding_done", true).apply()
        _onboardingDone.value = true
    }

    fun setOfflinePending(pending: Boolean) {
        prefs.edit().putBoolean("offline_pending", pending).apply()
        _offlinePending.value = pending
    }

    // MARK: profile

    fun saveProfile(profile: Profile) {
        prefs.edit()
            .putString("profile_name", profile.name.trim())
            .putString("profile_phone", profile.phone.trim())
            .putString("profile_blood", profile.bloodGroup)
            .putString("profile_notes", profile.medicalNotes.trim())
            .apply()
        _profile.value = profile.copy(name = profile.name.trim(), phone = profile.phone.trim(), medicalNotes = profile.medicalNotes.trim())
    }

    // MARK: contacts

    /** Adds a contact unless the list is full or the number is already there. Returns false if not added. */
    fun addContact(name: String, phone: String, relation: String = ""): Boolean {
        val current = _contacts.value
        val normalized = normalizePhone(phone)
        if (current.size >= MaxEmergencyContacts || normalized.isEmpty()) return false
        if (current.any { normalizePhone(it.phone) == normalized }) return false
        writeContacts(current + EmergencyContact(UUID.randomUUID().toString(), name.trim(), phone.trim(), relation.trim()))
        return true
    }

    fun updateContact(contact: EmergencyContact) {
        writeContacts(_contacts.value.map { if (it.id == contact.id) contact else it })
    }

    fun removeContact(id: String) {
        writeContacts(_contacts.value.filterNot { it.id == id })
    }

    private fun writeContacts(list: List<EmergencyContact>) {
        prefs.edit().putString("contacts", gson.toJson(list)).apply()
        _contacts.value = list
    }

    // MARK: settings

    fun updateSettings(transform: (PukaarSettings) -> PukaarSettings) {
        val next = transform(_settings.value).let { it.copy(countdownSeconds = it.countdownSeconds.coerceIn(3, 10)) }
        prefs.edit()
            .putString("theme", next.theme.name)
            .putBoolean("shake_enabled", next.shakeEnabled)
            .putString("shake_sensitivity", next.shakeSensitivity.name)
            .putInt("countdown_seconds", next.countdownSeconds)
            .putBoolean("battery_saver_auto", next.batterySaverAuto)
            .putBoolean("battery_saver_now", next.batterySaverNow)
            .putString("district_control", next.districtControlNumber.trim())
            .apply()
        _settings.value = next
    }

    // MARK: checklists

    fun setChecked(checklistId: String, itemId: String, checked: Boolean) {
        val current = _checklists.value[checklistId].orEmpty()
        val next = if (checked) current + itemId else current - itemId
        writeChecklists(_checklists.value + (checklistId to next))
    }

    fun resetChecklist(checklistId: String) {
        writeChecklists(_checklists.value - checklistId)
    }

    private fun writeChecklists(map: Map<String, Set<String>>) {
        prefs.edit().putString("checklists", gson.toJson(map.mapValues { it.value.toList() })).apply()
        _checklists.value = map
    }

    // MARK: chat

    /** When the user last looked at the Disaster Relief chat, for the unread badge on Home. */
    private val _chatSeenAt = MutableStateFlow(0L)
    val chatSeenAt: StateFlow<Long> = _chatSeenAt.asStateFlow()

    fun markChatSeen() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("chat_seen_at", now).apply()
        _chatSeenAt.value = now
    }

    internal fun loadChatSeen() {
        _chatSeenAt.value = prefs.getLong("chat_seen_at", 0L)
    }

    /**
     * A random id for this install, used only to count broadcast reach on the dashboard. Not linked
     * to the person: not their number, not bitchat's identity.
     */
    @Synchronized
    fun installId(): String {
        prefs.getString("install_id", null)?.let { return it }
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = java.security.SecureRandom()
        val id = (1..16).map { chars[random.nextInt(chars.length)] }.joinToString("")
        prefs.edit().putString("install_id", id).apply()
        return id
    }

    /** Official broadcasts shown on this phone and not yet reported to the server (reach, change 6). */
    @Synchronized
    fun noteBroadcastSeen(id: String) {
        if (!Regex("[a-z0-9]{1,16}").matches(id)) return
        val reported = prefs.getStringSet("broadcasts_reported", emptySet()).orEmpty()
        if (id in reported) return
        prefs.edit().putStringSet("broadcasts_unreported", unreportedBroadcasts() + id).apply()
    }

    fun unreportedBroadcasts(): Set<String> = prefs.getStringSet("broadcasts_unreported", emptySet()).orEmpty().toSet()

    @Synchronized
    fun markBroadcastsReported(ids: Collection<String>) {
        // Keep the reported list short; the server counts each device once anyway.
        val reported = (prefs.getStringSet("broadcasts_reported", emptySet()).orEmpty() + ids).toList().takeLast(200).toSet()
        prefs.edit()
            .putStringSet("broadcasts_reported", reported)
            .putStringSet("broadcasts_unreported", unreportedBroadcasts() - ids.toSet())
            .apply()
    }

    fun normalizePhone(phone: String): String = phone.filter { it.isDigit() || it == '+' }
}
