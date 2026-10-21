package app.pukaar.ui.screens.guides

import androidx.annotation.StringRes
import app.pukaar.ui.theme.Sym
import com.bitchat.android.R

/**
 * Guide and checklist structure. The words live in string resources so a language is added with
 * translation files only (FR-27). Content must come from official sources such as NDMA (FR-25):
 * a stage with no array yet shows "not added yet" instead of made-up advice.
 */
enum class GuideStage(@StringRes val label: Int) { Before(R.string.pk_guide_before), During(R.string.pk_guide_during), After(R.string.pk_guide_after) }

data class GuideDef(
    val id: String,
    @StringRes val title: Int,
    val icon: String,
    val steps: Map<GuideStage, Int>,
    /** True while the wording is the designers' sample and not yet the official text. */
    val sample: Boolean,
)

val Guides = listOf(
    GuideDef("flood", R.string.pk_guide_flood, Sym.flood, mapOf(GuideStage.During to R.array.pk_guide_flood_during), sample = true),
    GuideDef("earthquake", R.string.pk_guide_earthquake, Sym.earthquake, emptyMap(), sample = false),
    GuideDef("fire", R.string.pk_guide_fire, Sym.localFireDepartment, emptyMap(), sample = false),
)

data class ChecklistItem(val id: String, @StringRes val label: Int)
data class ChecklistGroup(@StringRes val title: Int, val items: List<ChecklistItem>)
data class ChecklistDef(val id: String, @StringRes val title: Int, val icon: String, val groups: List<ChecklistGroup>) {
    val itemIds: List<String> get() = groups.flatMap { g -> g.items.map { it.id } }
}

val Checklists = listOf(
    ChecklistDef(
        "gobag", R.string.pk_checklist_gobag, Sym.backpack,
        listOf(
            ChecklistGroup(R.string.pk_gobag_group_documents, listOf(
                ChecklistItem("ids", R.string.pk_gobag_ids),
                ChecklistItem("bank", R.string.pk_gobag_bank),
            )),
            ChecklistGroup(R.string.pk_gobag_group_food, listOf(
                ChecklistItem("water", R.string.pk_gobag_water),
                ChecklistItem("food", R.string.pk_gobag_food),
                ChecklistItem("ors", R.string.pk_gobag_ors),
            )),
            ChecklistGroup(R.string.pk_gobag_group_health, listOf(
                ChecklistItem("medicines", R.string.pk_gobag_medicines),
                ChecklistItem("firstaid", R.string.pk_gobag_firstaid),
            )),
            ChecklistGroup(R.string.pk_gobag_group_power, listOf(
                ChecklistItem("torch", R.string.pk_gobag_torch),
                ChecklistItem("powerbank", R.string.pk_gobag_powerbank),
                ChecklistItem("batteries", R.string.pk_gobag_batteries),
            )),
            ChecklistGroup(R.string.pk_gobag_group_other, listOf(
                ChecklistItem("whistle", R.string.pk_gobag_whistle),
                ChecklistItem("rope", R.string.pk_gobag_rope),
            )),
        ),
    ),
    // Items still to come from NDMA's home-safety guidance.
    ChecklistDef("home", R.string.pk_checklist_home, Sym.homeHealth, emptyList()),
)

fun guide(id: String) = Guides.firstOrNull { it.id == id }
fun checklist(id: String) = Checklists.firstOrNull { it.id == id }
