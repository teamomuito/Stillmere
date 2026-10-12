package io.github.teamomuito.colony.sim

/** The calendar: 60 ticks per second at normal speed, 2,500 ticks to an hour, 60,000 to a day, 15 days to a season, 60 to a year. */
const val TICKS_PER_HOUR = 2500
const val TICKS_PER_DAY = 60000
const val DAYS_PER_SEASON = 15
const val SEASONS_PER_YEAR = 4
const val DAYS_PER_YEAR = DAYS_PER_SEASON * SEASONS_PER_YEAR
const val HOURS_PER_DAY = 24

/**
 * Many numbers in the simulation were tuned when a day was 24,000 ticks (1,000 per hour). They are still written in those
 * units. [TIME_SCALE] converts: [tk] turns a count of those old ticks into current ticks (a cooldown, a timer, how often
 * something updates), and a rate per old tick is divided by [TIME_SCALE] to get a rate per current tick. Anything given in
 * hours or days needs no conversion.
 */
const val LEGACY_TICKS_PER_HOUR = 1000
const val TIME_SCALE = TICKS_PER_HOUR / LEGACY_TICKS_PER_HOUR.toFloat()

fun tk(legacyTicks: Int): Int = Math.round(legacyTicks * TIME_SCALE)
fun tk(legacyTicks: Long): Long = Math.round(legacyTicks * TIME_SCALE.toDouble())

enum class Terrain(val label: String, val passable: Boolean, val fertility: Float, val cost: Int) {
    SOIL("Soil", true, 1f, 1),
    RICH_SOIL("Rich soil", true, 1.4f, 1),
    GRAVEL("Gravel", true, 0f, 1),
    SAND("Sand", true, 0.15f, 2),
    MARSH("Marsh", true, 0.9f, 3),
    WATER_SHALLOW("Shallow water", true, 0f, 4),
    WATER_DEEP("Deep water", false, 0f, 0),
    ROCK("Rock", false, 0f, 0),
    ICE("Ice", true, 0f, 2),
    MUD("Mud", true, 0.5f, 3),
}

enum class RockType(val label: String, val beauty: Float) {
    GRANITE("Granite", 0f), MARBLE("Marble", 1f), LIMESTONE("Limestone", 0f), SANDSTONE("Sandstone", 0f), SLATE("Slate", 0f)
}

enum class Ore(val label: String, val item: ItemType?, val yieldMin: Int, val yieldMax: Int, val work: Float) {
    NONE("", null, 0, 0, 1000f),
    STEEL("Steel", ItemType.STEEL, 14, 24, 1500f),
    SILVER("Silver", ItemType.SILVER, 40, 80, 1500f),
    GOLD("Gold", ItemType.GOLD, 10, 25, 1800f),
    HARDALLOY("Hardalloy", ItemType.HARDALLOY, 10, 20, 2200f),
    COMPONENTS("Components", ItemType.COMPONENT, 1, 3, 2000f),
}

enum class Season(val label: String) { SPRING("Spring"), SUMMER("Summer"), FALL("Fall"), WINTER("Winter") }

enum class Biome(
    val label: String, val springT: Float, val summerT: Float, val fallT: Float, val winterT: Float,
    val rain: Float, val treeDensity: Float, val soilBias: Float,
) {
    TEMPERATE("Temperate forest", 13f, 24f, 10f, -3f, 0.4f, 1f, 0f),
    BOREAL("Boreal forest", 4f, 15f, 2f, -18f, 0.35f, 1.1f, -0.05f),
    TUNDRA("Tundra", -2f, 8f, -4f, -26f, 0.2f, 0.25f, -0.12f),
    DESERT("Desert", 28f, 38f, 28f, 15f, 0.05f, 0.1f, -0.2f),
    TROPICAL("Tropical rainforest", 26f, 29f, 26f, 23f, 0.7f, 1.4f, 0.1f),
    ARID("Arid shrubland", 22f, 31f, 20f, 8f, 0.12f, 0.3f, -0.1f),
}

enum class WorkType(val label: String, val short: String) {
    FIREFIGHT("Firefight", "Fire"), PATIENT("Patient", "Rest"), DOCTOR("Doctor", "Doc"), WARDEN("Warden", "Ward"),
    HANDLE("Handle animals", "Hndl"), COOK("Cook", "Cook"), HUNT("Hunt", "Hunt"), CONSTRUCT("Construct", "Cnst"),
    GROW("Grow", "Grow"), MINE("Mine", "Mine"), PLANT_CUT("Plant cut", "Cut"), SMITH("Smith", "Smth"),
    TAILOR("Tailor", "Tail"), ART("Art", "Art"), CRAFT("Craft", "Crft"), HAUL("Haul", "Haul"),
    CLEAN("Clean", "Cln"), RESEARCH("Research", "Rsrch"),
}

enum class SkillType(val label: String) {
    MARKSMANSHIP("Marksmanship"), CLOSE_COMBAT("Close combat"), ARCHITECTURE("Architecture"), QUARRYING("Quarrying"),
    GALLEY("Galley"), HORTICULTURE("Horticulture"), HUSBANDRY("Husbandry"), WORKMANSHIP("Workmanship"),
    ARTISTRY("Artistry"), HEALING("Healing"), RAPPORT("Rapport"), SCHOLARSHIP("Scholarship"),
}

fun WorkType.skill(): SkillType? = when (this) {
    WorkType.DOCTOR -> SkillType.HEALING
    WorkType.WARDEN -> SkillType.RAPPORT
    WorkType.HANDLE -> SkillType.HUSBANDRY
    WorkType.COOK -> SkillType.GALLEY
    WorkType.HUNT -> SkillType.MARKSMANSHIP
    WorkType.CONSTRUCT -> SkillType.ARCHITECTURE
    WorkType.GROW, WorkType.PLANT_CUT -> SkillType.HORTICULTURE
    WorkType.MINE -> SkillType.QUARRYING
    WorkType.SMITH, WorkType.TAILOR, WorkType.CRAFT -> SkillType.WORKMANSHIP
    WorkType.ART -> SkillType.ARTISTRY
    WorkType.RESEARCH -> SkillType.SCHOLARSHIP
    WorkType.FIREFIGHT, WorkType.PATIENT, WorkType.HAUL, WorkType.CLEAN -> null
}

enum class PlantType(
    val label: String, val growDays: Float, val yieldType: ItemType?, val yieldCount: Int,
    val harvestWork: Int, val sowWork: Int, val crop: Boolean, val minTemp: Float = 6f, val maxTemp: Float = 42f,
    val flammable: Float = 0.5f, val wood: Boolean = false, val research: Research? = null,
) {
    OAK("Oak tree", 12f, ItemType.WOOD, 24, 450, 0, false, wood = true),
    PINE("Pine tree", 12f, ItemType.WOOD, 20, 400, 0, false, wood = true),
    PALM("Palm tree", 9f, ItemType.WOOD, 14, 300, 0, false, wood = true),
    POPLAR("Poplar", 8f, ItemType.WOOD, 18, 350, 0, false, wood = true),
    BIRCH("Birch", 8f, ItemType.WOOD, 16, 350, 0, false, wood = true),
    MAPLE("Maple", 12f, ItemType.WOOD, 22, 420, 0, false, wood = true),
    TEAK("Teak", 14f, ItemType.WOOD, 26, 480, 0, false, wood = true),
    SAGUARO("Saguaro cactus", 10f, ItemType.WOOD, 8, 280, 0, false, wood = true, flammable = 0.1f),
    BERRY("Berry bush", 5f, ItemType.BERRIES, 8, 120, 0, false),
    BRAMBLE("Brambles", 0f, null, 0, 150, 0, false),
    FERAL_MIREWORT("Feral mirewort", 6f, ItemType.MIREWORT, 3, 120, 0, false),
    RICE("Rice", 5.8f, ItemType.RICE, 12, 110, 170, true),
    POTATO("Potatoes", 5.8f, ItemType.POTATOES, 11, 110, 170, true),
    CORN("Corn", 11.5f, ItemType.CORN, 40, 110, 170, true),
    STRAWBERRY("Strawberries", 6.5f, ItemType.STRAWBERRIES, 10, 110, 170, true),
    COTTON("Cotton", 5.5f, ItemType.CLOTH, 14, 110, 170, true),
    MIREWORT("Mirewort", 8f, ItemType.MIREWORT, 5, 110, 170, true),
    HUSHMOSS("Hushmoss", 7f, ItemType.HUSHMOSS, 10, 110, 170, true),
    DREAMWEED("Dreamweed", 7f, ItemType.DREAMWEED, 8, 110, 170, true),
    WISPSILK_CROP("Wispsilk", 15f, ItemType.WISPSILK, 8, 110, 170, true, minTemp = 10f, flammable = 0.1f, research = Research.WISPSILK_RESEARCH),
    MEADOW_GRASS("Sward", 3.5f, ItemType.HAY, 20, 90, 120, true, minTemp = 3f);

    val isTree get() = wood
    val regrows get() = this == BERRY || this == FERAL_MIREWORT
}

enum class ItemCat(val label: String) {
    RESOURCE("Resources"), FOOD_PLANT("Plant food"), FOOD_MEAT("Meat"), FOOD_MEAL("Meals"), FOOD_ANIMAL("Animal feed"),
    MEDICINE("Medicine"), DRUG("Drugs"), WEAPON("Weapons"), APPAREL("Apparel"), ART("Art"), MISC("Other"),
}

enum class ItemType(
    val label: String, val cat: ItemCat, val stack: Int, val value: Float, val nutrition: Float = 0f,
    val spoilDays: Float = 0f, val flammable: Float = 0f, val weapon: Weapon? = null, val apparel: Apparel? = null,
    val potency: Float = 0f, val humanFood: Boolean = true,
    /** Highest tend quality this medicine can produce. */
    val maxTendQuality: Float = 1f,
) {
    WOOD("Wood", ItemCat.RESOURCE, 75, 0.5f, flammable = 1f),
    STONE_CHUNK("Rough rock", ItemCat.RESOURCE, 1, 0.1f),
    STONE("Cut stone", ItemCat.RESOURCE, 75, 0.6f),
    STEEL("Steel", ItemCat.RESOURCE, 75, 1.9f),
    HARDALLOY("Hardalloy", ItemCat.RESOURCE, 75, 9f),
    SILVER("Silver", ItemCat.RESOURCE, 500, 1f),
    GOLD("Gold", ItemCat.RESOURCE, 500, 10f),
    COMPONENT("Components", ItemCat.RESOURCE, 50, 32f),
    WISPSILK("Wispsilk", ItemCat.RESOURCE, 75, 2.4f, flammable = 0.4f),
    CLOTH("Cloth", ItemCat.RESOURCE, 75, 1.3f, flammable = 1f),
    LEATHER("Leather", ItemCat.RESOURCE, 75, 2.1f, flammable = 0.6f),
    WOOL("Wool", ItemCat.RESOURCE, 75, 1.4f, flammable = 1f),

    RICE("Rice", ItemCat.FOOD_PLANT, 75, 1.1f, 0.05f, 40f),
    POTATOES("Potatoes", ItemCat.FOOD_PLANT, 75, 1.1f, 0.05f, 40f),
    CORN("Corn", ItemCat.FOOD_PLANT, 75, 1.1f, 0.05f, 40f),
    STRAWBERRIES("Strawberries", ItemCat.FOOD_PLANT, 75, 1.2f, 0.05f, 10f),
    MEAT("Meat", ItemCat.FOOD_MEAT, 75, 1.8f, 0.05f, 4f),
    MANFLESH("Manflesh", ItemCat.FOOD_MEAT, 75, 1.8f, 0.05f, 4f),
    CHITIN_CUTS("Chitin cuts", ItemCat.FOOD_MEAT, 75, 1.2f, 0.05f, 4f),
    EGGS("Eggs", ItemCat.FOOD_MEAT, 75, 2f, 0.05f, 8f),
    MILK("Milk", ItemCat.FOOD_MEAT, 75, 2f, 0.05f, 4f),
    MEAL_HEARTH_STEW("Hearth stew", ItemCat.FOOD_MEAL, 10, 10f, 0.9f, 4f),
    MEAL_ROASTED_PLATTER("Roasted platter", ItemCat.FOOD_MEAL, 10, 15f, 0.9f, 4f),
    MEAL_BANQUET_SPREAD("Banquet spread", ItemCat.FOOD_MEAL, 10, 28f, 0.9f, 4f),
    MEAL_RATION("Sealed ration", ItemCat.FOOD_MEAL, 10, 12f, 0.9f, 0f),
    TRAIL_CAKE("Trail cake", ItemCat.FOOD_MEAL, 50, 3f, 0.05f, 0f),
    FEED_PELLETS("Feed pellets", ItemCat.FOOD_ANIMAL, 75, 0.9f, 0.05f, 0f, humanFood = false),
    HAY("Fodder", ItemCat.FOOD_ANIMAL, 75, 0.4f, 0.05f, 40f, flammable = 1f, humanFood = false),

    MIREWORT("Mirewort", ItemCat.MEDICINE, 75, 1f, flammable = 0.8f),
    MEDS_LEAF_POULTICE("Leaf poultice", ItemCat.MEDICINE, 25, 10f, potency = 0.6f, maxTendQuality = 0.7f),
    MEDS_STERILE_DRESSING("Sterile dressing", ItemCat.MEDICINE, 25, 18f, potency = 1.0f),

    BEER("Beer", ItemCat.DRUG, 25, 12f),
    HUSHMOSS("Dried hushmoss", ItemCat.DRUG, 75, 2f, flammable = 1f),
    JOINT("Rolled bundle", ItemCat.DRUG, 25, 14f, flammable = 1f),
    DREAMWEED("Dreamweed sprigs", ItemCat.DRUG, 75, 3f, flammable = 1f),
    PALE_DUST("Pale dust", ItemCat.DRUG, 25, 14f),
    WHITE_RUSH("White rush", ItemCat.DRUG, 25, 20f),
    SURGE_TONIC("Surge tonic", ItemCat.DRUG, 25, 32f),
    DAWN_TABLET("Dawn tablet", ItemCat.DRUG, 25, 24f),
    LUCID_DRAUGHT("Lucid draught", ItemCat.DRUG, 25, 14f),

    SCULPTURE_SMALL("Small sculpture", ItemCat.ART, 1, 80f),
    SCULPTURE_LARGE("Large sculpture", ItemCat.ART, 1, 220f),

    // Weapons
    W_POCKET_BLADE("Pocket blade", ItemCat.WEAPON, 1, 20f, weapon = Weapon.POCKET_BLADE),
    W_CLUB("Club", ItemCat.WEAPON, 1, 9f, weapon = Weapon.CLUB, flammable = 1f),
    W_SPEAR("Spear", ItemCat.WEAPON, 1, 26f, weapon = Weapon.SPEAR, flammable = 1f),
    W_MACE("Mace", ItemCat.WEAPON, 1, 40f, weapon = Weapon.MACE),
    W_BROAD_BLADE("Broad blade", ItemCat.WEAPON, 1, 90f, weapon = Weapon.BROAD_BLADE),
    W_BOW("Reedstring", ItemCat.WEAPON, 1, 22f, weapon = Weapon.BOW, flammable = 1f),
    W_LONGSTAVE("Longstave", ItemCat.WEAPON, 1, 40f, weapon = Weapon.LONGSTAVE, flammable = 1f),
    W_CYLINDER_GUN("Cylinder gun", ItemCat.WEAPON, 1, 90f, weapon = Weapon.CYLINDER_GUN),
    W_SNAP_PISTOL("Quickshot", ItemCat.WEAPON, 1, 95f, weapon = Weapon.SNAP_PISTOL),
    W_BOLT("Latch carbine", ItemCat.WEAPON, 1, 125f, weapon = Weapon.BOLT_RIFLE),
    W_SHOTGUN("Riot gun", ItemCat.WEAPON, 1, 140f, weapon = Weapon.SHOTGUN),
    W_SMG("Rapid sidearm", ItemCat.WEAPON, 1, 130f, weapon = Weapon.SMG),
    W_RIFLE("Stutter gun", ItemCat.WEAPON, 1, 190f, weapon = Weapon.RIFLE),
    W_LMG("Bipod hammer", ItemCat.WEAPON, 1, 260f, weapon = Weapon.LMG),
    W_STABSWORD("Stabsword", ItemCat.WEAPON, 1, 70f, weapon = Weapon.STABSWORD),
    W_HARDALLOY_SWORD("Hardalloy cutter", ItemCat.WEAPON, 1, 300f, weapon = Weapon.HARDALLOY_SWORD),
    W_RECURVE("Hornwound", ItemCat.WEAPON, 1, 55f, weapon = Weapon.HORNWOUND, flammable = 1f),
    W_LEVER("Swing repeater", ItemCat.WEAPON, 1, 150f, weapon = Weapon.LEVER_RIFLE),
    W_SCATTER_CANNON("Scatter cannon", ItemCat.WEAPON, 1, 210f, weapon = Weapon.SCATTER_CANNON),
    W_MAG_CARBINE("Mag carbine", ItemCat.WEAPON, 1, 210f, weapon = Weapon.MAG_CARBINE),
    W_SPINNER_RACK("Spinner rack", ItemCat.WEAPON, 1, 550f, weapon = Weapon.SPINNER_RACK),
    W_PULSE_CARBINE("Pulse carbine", ItemCat.WEAPON, 1, 480f, weapon = Weapon.PULSE_CARBINE),
    W_EMBER_THROWER("Ember thrower", ItemCat.WEAPON, 1, 220f, weapon = Weapon.EMBER_THROWER),
    W_SHRAPNEL_CHARGE("Shrapnel charges", ItemCat.WEAPON, 1, 120f, weapon = Weapon.SHRAPNEL_CHARGE),
    W_SKYFALL_BATTERY("Skyfall battery", ItemCat.WEAPON, 1, 650f, weapon = Weapon.SKYFALL_BATTERY),
    W_SNIPER("Distance piece", ItemCat.WEAPON, 1, 340f, weapon = Weapon.SNIPER),

    // Apparel
    A_TSHIRT("Cotton tee", ItemCat.APPAREL, 1, 15f, apparel = Apparel.TSHIRT, flammable = 1f),
    A_COLLAR_TUNIC("Collar tunic", ItemCat.APPAREL, 1, 25f, apparel = Apparel.COLLAR_TUNIC, flammable = 1f),
    A_PANTS("Hose", ItemCat.APPAREL, 1, 18f, apparel = Apparel.PANTS, flammable = 1f),
    A_TRIBAL("Woven wrap", ItemCat.APPAREL, 1, 25f, apparel = Apparel.TRIBAL, flammable = 0.7f),
    A_LONG_COAT("Long coat", ItemCat.APPAREL, 1, 55f, apparel = Apparel.LONG_COAT, flammable = 0.8f),
    A_PADDED_COAT("Padded coat", ItemCat.APPAREL, 1, 70f, apparel = Apparel.PADDED_COAT, flammable = 1f),
    A_HAT("Sunbrim", ItemCat.APPAREL, 1, 25f, apparel = Apparel.WIDE_BRIM_HAT, flammable = 0.8f),
    A_BEANIE("Knit cap", ItemCat.APPAREL, 1, 25f, apparel = Apparel.KNIT_CAP, flammable = 1f),
    A_RIBBED_VEST("Quilted tabard", ItemCat.APPAREL, 1, 90f, apparel = Apparel.RIBBED_VEST),
    A_RIBBED_LEGGINGS("Ribbed leggings", ItemCat.APPAREL, 1, 70f, apparel = Apparel.RIBBED_LEGGINGS),
    A_RIBBED_JACKET("Ribbed doublet", ItemCat.APPAREL, 1, 120f, apparel = Apparel.RIBBED_JACKET),
    A_RIVETED_HOOD("Riveted hood", ItemCat.APPAREL, 1, 55f, apparel = Apparel.RIVETED_HOOD),
    A_ARMOR("Brigandine", ItemCat.APPAREL, 1, 250f, apparel = Apparel.BRIGANDINE),
    A_JACKET("Canvas smock", ItemCat.APPAREL, 1, 60f, apparel = Apparel.JACKET, flammable = 1f),
    A_TRENCH_ARMOR("Trench mail", ItemCat.APPAREL, 1, 520f, apparel = Apparel.TRENCH_ARMOR),
    A_TRENCH_VISOR("Trench visor", ItemCat.APPAREL, 1, 240f, apparel = Apparel.TRENCH_VISOR),
    A_GHOSTWEAVE_HELM("Ghostweave cowl", ItemCat.APPAREL, 1, 200f, apparel = Apparel.GHOSTWEAVE_HELM),
    A_EXOSUIT("Exosuit", ItemCat.APPAREL, 1, 900f, apparel = Apparel.EXOSUIT),
    A_EXO_HELM("Exo casque", ItemCat.APPAREL, 1, 380f, apparel = Apparel.EXO_HELM),
    A_PLUMED_CROWN("Plumed crown", ItemCat.APPAREL, 1, 45f, apparel = Apparel.PLUMED_CROWN, flammable = 0.8f),
    A_GHOSTWEAVE_ARMOR("Ghostweave shell", ItemCat.APPAREL, 1, 480f, apparel = Apparel.GHOSTWEAVE_ARMOR),

    // Misc
    CORPSE_HUMAN("Corpse", ItemCat.MISC, 1, 0f, flammable = 0.3f),
    SHELL("Lobbed bomb", ItemCat.MISC, 25, 15f),
    BERRIES("Berries", ItemCat.FOOD_PLANT, 75, 1.2f, 0.05f, 10f),
    MEAL_VAT_MASH("Vat mash", ItemCat.FOOD_MEAL, 25, 3f, 0.9f, 0f),
    ;

    val isFood get() = nutrition > 0f
    val isPlantFood get() = cat == ItemCat.FOOD_PLANT
    val isGear get() = weapon != null || apparel != null
}

enum class Quality(val label: String, val mult: Float) {
    AWFUL("awful", 0.6f), POOR("poor", 0.8f), NORMAL("normal", 1f), GOOD("good", 1.15f),
    EXCELLENT("excellent", 1.3f), MASTERWORK("masterwork", 1.6f), LEGENDARY("legendary", 2f)
}

enum class DamageKind(val label: String, val sharp: Boolean, val bleed: Float, val pain: Float, val infect: Float) {
    CUT("Cut", true, 1.0f, 0.9f, 0.20f), STAB("Stab", true, 0.8f, 1.0f, 0.15f), BULLET("Gunshot", true, 0.7f, 1.1f, 0.10f),
    BRUISE("Bruise", false, 0f, 0.7f, 0f), SCRATCH("Scratch", true, 0.3f, 0.3f, 0.10f), BITE("Bite", true, 0.8f, 1.0f, 0.25f),
    BURN("Burn", false, 0f, 1.2f, 0.18f), FROSTBITE("Frostbite", false, 0f, 0.6f, 0f), BLAST("Blast", false, 0.5f, 1.2f, 0.12f),
    CRUSH("Crush", false, 0.2f, 1.1f, 0f), ACID("Acid scald", false, 0f, 1f, 0.1f),
}

enum class Weapon(
    val label: String, val ranged: Boolean, val damage: Float, val range: Float, val cooldown: Int, val accuracy: Float,
    val kind: DamageKind, val burst: Int = 1, val armorPen: Float = 0f, val warmup: Int = 0, val aoe: Float = 0f,
    /** Projectiles per burst that each roll to hit; [damage] is shared between them. */
    val pellets: Int = 1,
) {
    FISTS("Fists", false, 5f, 1.5f, 38, 0.82f, DamageKind.BRUISE),
    POCKET_BLADE("Pocket blade", false, 9f, 1.5f, 34, 0.86f, DamageKind.CUT),
    CLUB("Club", false, 11f, 1.5f, 46, 0.8f, DamageKind.BRUISE),
    SPEAR("Spear", false, 13f, 1.9f, 48, 0.82f, DamageKind.STAB),
    MACE("Mace", false, 14f, 1.5f, 50, 0.8f, DamageKind.BRUISE, armorPen = 0.2f),
    BROAD_BLADE("Broad blade", false, 18f, 1.5f, 42, 0.85f, DamageKind.CUT, armorPen = 0.25f),
    BOW("Reedstring", true, 9f, 19f, 50, 0.72f, DamageKind.STAB, warmup = 10),
    LONGSTAVE("Longstave", true, 15f, 26f, 70, 0.72f, DamageKind.STAB, warmup = 14),
    CYLINDER_GUN("Cylinder gun", true, 12f, 20f, 55, 0.8f, DamageKind.BULLET, warmup = 8),
    SNAP_PISTOL("Quickshot", true, 12f, 20f, 55, 0.78f, DamageKind.BULLET, warmup = 7),
    BOLT_RIFLE("Latch carbine", true, 15f, 30f, 80, 0.78f, DamageKind.BULLET, warmup = 14),
    SHOTGUN("Riot gun", true, 18f, 12f, 70, 0.85f, DamageKind.BULLET, warmup = 10, burst = 1, pellets = 8),
    SMG("Rapid sidearm", true, 10f, 16f, 50, 0.72f, DamageKind.BULLET, burst = 3, warmup = 8),
    RIFLE("Stutter gun", true, 11f, 28f, 55, 0.76f, DamageKind.BULLET, burst = 3, warmup = 10),
    LMG("Bipod hammer", true, 11f, 30f, 70, 0.74f, DamageKind.BULLET, burst = 5, warmup = 20),
    SNIPER("Distance piece", true, 22f, 40f, 110, 0.8f, DamageKind.BULLET, warmup = 22, armorPen = 0.3f),
    STABSWORD("Stabsword", false, 15f, 1.5f, 38, 0.86f, DamageKind.CUT, armorPen = 0.1f),
    HARDALLOY_SWORD("Hardalloy cutter", false, 24f, 1.5f, 36, 0.88f, DamageKind.CUT, armorPen = 0.4f),
    HORNWOUND("Hornwound", true, 11f, 24f, 55, 0.74f, DamageKind.STAB, warmup = 11),
    LEVER_RIFLE("Swing repeater", true, 14f, 24f, 68, 0.78f, DamageKind.BULLET, warmup = 12),
    SCATTER_CANNON("Scatter cannon", true, 16f, 14f, 60, 0.85f, DamageKind.BULLET, burst = 2, warmup = 10, pellets = 8),
    MAG_CARBINE("Mag carbine", true, 11f, 18f, 52, 0.74f, DamageKind.BULLET, burst = 3, warmup = 9),
    SPINNER_RACK("Spinner rack", true, 10f, 26f, 64, 0.68f, DamageKind.BULLET, burst = 8, warmup = 26),
    PULSE_CARBINE("Pulse carbine", true, 18f, 30f, 70, 0.8f, DamageKind.BULLET, burst = 3, armorPen = 0.3f, warmup = 14),
    EMBER_THROWER("Ember thrower", true, 8f, 22f, 160, 0.6f, DamageKind.BURN, warmup = 22, aoe = 2.2f),
    SHRAPNEL_CHARGE("Shrapnel charges", true, 36f, 14f, 220, 0.55f, DamageKind.BLAST, warmup = 14, aoe = 2.4f),
    SKYFALL_BATTERY("Skyfall battery", true, 70f, 30f, 400, 0.55f, DamageKind.BLAST, warmup = 30, aoe = 3.5f),
    PIVOT_CANNON("Pivot cannon", true, 11f, 28f, 50, 0.75f, DamageKind.BULLET, burst = 3),
    LOBBED_CHARGE("Lobbed charge", true, 40f, 55f, 300, 0.5f, DamageKind.BLAST, aoe = 2.6f),
    FIRE_FLASK("Fire flask", true, 5f, 15f, 100, 0.8f, DamageKind.BURN, aoe = 1.8f),
    BLADE("Razor limbs", false, 18f, 1.6f, 30, 0.85f, DamageKind.CUT, armorPen = 0.2f),
    PULSE_PIKE("Pulse pike", true, 28f, 24f, 110, 0.82f, DamageKind.BULLET, warmup = 25, armorPen = 0.45f),
    MECH_GUN("Bolt spitter", true, 12f, 22f, 90, 0.7f, DamageKind.BULLET, burst = 5, warmup = 20, armorPen = 0.1f),
    TEETH("Teeth", false, 8f, 1.5f, 36, 0.85f, DamageKind.BITE),
    CLAWS("Claws", false, 6f, 1.5f, 32, 0.85f, DamageKind.SCRATCH),
    HEAD_BUTT("Horns", false, 12f, 1.5f, 50, 0.8f, DamageKind.BRUISE),
    TRAMPLE("Trample", false, 20f, 1.5f, 60, 0.8f, DamageKind.CRUSH),
    SENTRY_POD_GUN("Sentry pod", true, 8f, 22f, 40, 0.7f, DamageKind.BULLET, burst = 2);

    val meleeSkill get() = !ranged

    /** Warmup and cooldown in current ticks; the stats above are written in old ticks (see [TIME_SCALE]). */
    val warmupTicks get() = tk(warmup)
    val cooldownTicks get() = tk(cooldown)
}

enum class ApparelSlot { HEAD, SHIRT, PANTS, OUTER }

/** Coverage bits: 1 head, 2 torso, 4 arms, 8 legs, 16 neck */
enum class Apparel(
    val label: String, val slot: ApparelSlot, val cover: Int, val insCold: Float, val insHeat: Float,
    val armorSharp: Float, val armorBlunt: Float, val hp: Float, val beauty: Float = 0f,
) {
    TSHIRT("Cotton tee", ApparelSlot.SHIRT, 2 or 4, 2f, 2f, 0.03f, 0.01f, 60f),
    COLLAR_TUNIC("Collar tunic", ApparelSlot.SHIRT, 2 or 4, 4f, 3f, 0.05f, 0.02f, 80f, 0.5f),
    PANTS("Hose", ApparelSlot.PANTS, 8, 3f, 2f, 0.04f, 0.01f, 80f),
    TRIBAL("Woven wrap", ApparelSlot.SHIRT, 2 or 8, 5f, 4f, 0.06f, 0.02f, 90f),
    LONG_COAT("Long coat", ApparelSlot.OUTER, 2 or 4 or 8, 14f, 5f, 0.15f, 0.04f, 150f, 0.5f),
    PADDED_COAT("Padded coat", ApparelSlot.OUTER, 2 or 4 or 8, 40f, -8f, 0.14f, 0.05f, 160f),
    WIDE_BRIM_HAT("Sunbrim", ApparelSlot.HEAD, 1, 3f, 8f, 0.04f, 0.02f, 80f, 0.5f),
    KNIT_CAP("Knit cap", ApparelSlot.HEAD, 1, 12f, -3f, 0.02f, 0.02f, 70f),
    RIBBED_VEST("Quilted tabard", ApparelSlot.OUTER, 2, 5f, -3f, 0.45f, 0.1f, 160f),
    RIBBED_LEGGINGS("Ribbed leggings", ApparelSlot.PANTS, 8, 3f, -1f, 0.4f, 0.09f, 160f),
    RIBBED_JACKET("Ribbed doublet", ApparelSlot.OUTER, 2 or 4, 8f, -4f, 0.52f, 0.14f, 180f),
    RIVETED_HOOD("Riveted hood", ApparelSlot.HEAD, 1, 2f, -4f, 0.4f, 0.25f, 150f),
    BRIGANDINE("Brigandine", ApparelSlot.OUTER, 2 or 4 or 8, 6f, -8f, 0.75f, 0.35f, 350f, -0.5f),
    JACKET("Canvas smock", ApparelSlot.OUTER, 2 or 4, 20f, 3f, 0.08f, 0.03f, 130f, 0.3f),
    TRENCH_ARMOR("Trench mail", ApparelSlot.OUTER, 2 or 4 or 8, 14f, 6f, 0.7f, 0.4f, 380f),
    TRENCH_VISOR("Trench visor", ApparelSlot.HEAD, 1, 4f, 0f, 0.6f, 0.45f, 220f),
    GHOSTWEAVE_HELM("Ghostweave cowl", ApparelSlot.HEAD, 1, 5f, 3f, 0.5f, 0.35f, 200f),
    EXOSUIT("Exosuit", ApparelSlot.OUTER, 2 or 4 or 8, 30f, 18f, 0.8f, 0.55f, 460f, -0.5f),
    EXO_HELM("Exo casque", ApparelSlot.HEAD, 1, 8f, 6f, 0.72f, 0.55f, 260f),
    PLUMED_CROWN("Plumed crown", ApparelSlot.HEAD, 1, 6f, 1f, 0.05f, 0.04f, 80f, 1.2f),
    GHOSTWEAVE_ARMOR("Ghostweave shell", ApparelSlot.OUTER, 2 or 4 or 8, 22f, 12f, 0.58f, 0.35f, 320f),
}

enum class Trait(val label: String, val desc: String) {
    BRISK_HANDS("Brisk hands", "Finishes tasks sooner"), IDLE_STREAK("Idle streak", "Takes tasks slowly"),
    THICK_HIDE("Thick hide", "Blunts incoming harm"), TENDER_FRAME("Tender frame", "Hurts more from injuries"),
    SUNNY_OUTLOOK("Sunny outlook", "+10% mood"), GREY_OUTLOOK("Grey outlook", "-10% mood"),
    LIGHT_STEPPED("Light stepped", "Covers ground quicker"), SLOW_WALKER("Plodder", "Covers ground slowly"),
    NOCTURNAL("Nocturnal", "Works best after dark"), EAGER_PUPIL("Eager pupil", "Gains skill quickly"),
    STREET_SCRAPPER("Street scrapper", "Fights only up close"), QUICK_TRIGGER("Quick trigger", "Fires in quick succession"),
    WARM_HEART("Warm heart", "Gets on well with others"), SHARP_TONGUE("Sharp tongue", "Stirs up quarrels"),
    HEARTY_APPETITE("Hearty appetite", "Eats more and enjoys meals"), GARDEN_SENSE("Garden sense", "Fond of plants"),
    BARE_SKINNED("Bare-skinned", "Dislikes wearing clothes"), FLAME_DRAWN("Flame-drawn", "Enjoys open flames"),
    FLESH_EATER("Flesh-eater", "Finds manflesh tasty"), BUOYANT("Buoyant", "Cheerful"),
    HEAVY_HEARTED("Heavy-hearted", "Gloomy"), BROODING_MUSE("Brooding muse", "Better art, moody"),
    STRIKING_LOOKS("Striking looks", "Admired for appearance"), PLAIN_LOOKS("Plain looks", "Less admired for appearance"),
    UNMOVED("Unmoved", "Feels little at a death"), BATTLE_HUNGRY("Battle-hungry", "Relishes fights"),
    KEEN_MIND("Keen mind", "Fast researcher"), SLOW_STUDY("Late bloomer", "Learns skills slowly"),
    MEAT_FIRST("Meat-first", "Prefers meat dishes"), MACHINE_MINDED("Machine-minded", "Fond of implants"),
    SAME_SEX_DRAWN("Same-sex drawn", "Romantic interest in the same sex"), BOTH_DRAWN("Both-drawn", "Romantic interest in either sex"), UNROMANTIC("Unromantic", "No romantic interest"),
    STEEL_NERVES("Steel nerves", "Rarely has mental breaks"), SHORT_FUSE("Short fuse", "Prone to snapping"),
    EASY_COMPANY("Easy company", "Likes conversation and is good at it"), STEADY_AIM("Steady aim", "Accurate but slower"),
    CREATIVE("Inventive eye", "Crafts finer goods"), ACQUISITIVE("Acquisitive", "Craves riches"), PLAIN_LIVING("Plain living", "Content with little"),
    LONG_STRIDER("Long strider", "Keeps a fast pace"), HARDY_BODY("Hardy body", "Fights illness well"),
}

enum class Research(
    val label: String, val baseCost: Float, val needs: List<Research> = emptyList(), val unlocks: String = "",
    val tier: Int = 1, val bench: Int = 0,
) {
    // Neolithic / basics
    JOINERY_DETAIL("Joinery detail", 400f, emptyList(), "Chairs, stools, dressers, plant pots"),
    RUG_WEAVING("Rug weaving", 300f, listOf(JOINERY_DETAIL), "Carpet floors"),
    ROCK_SPLITTING("Rock splitting", 300f, emptyList(), "Rock dresser"),
    FORGEWORK("Forgework", 400f, emptyList(), "Coal forge: weapons, steel walls, armor"),
    STITCHWORK("Stitchwork", 300f, emptyList(), "Stitch frame"),
    TRAIL_CAKE("Trail cake", 300f, emptyList(), "Trail cake"),
    MASH_CRAFT("Mash craft", 500f, emptyList(), "Beer"),
    TINCTURE_CHEMISTRY("Tincture chemistry", 600f, listOf(MASH_CRAFT), "Tincture rig: rolls, draughts"),
    HERBAL_REMEDIES("Leaf lore", 500f, emptyList(), "Leaf poultice"),
    BASIC_MELEE("Blunt and pole arms", 300f, listOf(FORGEWORK), "Mace, spear, broad blade"),
    STAVE_AND_STRING("Stave and string", 300f, listOf(FORGEWORK), "Reedstring and longstave"),
    // Medieval / industrial
    GALVANICS("Galvanics", 1000f, listOf(FORGEWORK), "Live wires, generators, lamps, heaters, coolers", 2),
    CELL_STORAGE("Cell storage", 600f, listOf(GALVANICS), "Battery, switches", 2),
    SUNLIGHT_HARVESTING("Sunlight harvesting", 800f, listOf(GALVANICS), "Sun arrays", 2),
    BREEZE_HARVESTING("Breeze harvesting", 700f, listOf(GALVANICS), "Breeze mills", 2),
    LATHE_WORK("Lathe work", 700f, listOf(FORGEWORK), "Shaping rig: components, gun parts", 2),
    POWDER_ARMS("Powder arms", 800f, listOf(LATHE_WORK), "Cylinder gun, quickshot, riot gun, rifles", 2),
    SWIVEL_NESTS("Swivel nests", 800f, listOf(POWDER_ARMS), "Auto-firing swivel nests", 2),
    INDIRECT_FIRE("Indirect fire", 900f, listOf(SWIVEL_NESTS), "Tubes and lobbed bombs", 2),
    RIBBED_ARMOR("Ribbed plating", 800f, listOf(LATHE_WORK, STITCHWORK), "Quilted tabard, ribbed leggings, ribbed doublet, riveted hood", 2),
    BRIGANDINE_WORK("Brigandine work", 1000f, listOf(RIBBED_ARMOR), "Brigandine", 3),
    SOILLESS_GROWING("Soilless growing", 1100f, listOf(GALVANICS), "Grow trays", 2),
    ELECTRIC_COOKING("Powered griddle", 600f, listOf(GALVANICS), "Hotplate", 2),
    PHARMACOLOGY("Pharmacology", 1000f, listOf(HERBAL_REMEDIES, LATHE_WORK), "Compound doses", 2),
    LIMB_CRAFTING("Limb crafting", 700f, listOf(FORGEWORK), "Wooden shanks, gaff grips", 2),
    SURGERY("Clean cutting", 700f, listOf(HERBAL_REMEDIES), "Infirmary cots", 2),
    PART_ASSEMBLY("Part assembly", 2000f, listOf(LATHE_WORK, GALVANICS), "Gearworks: hardalloy gear, advanced components", 3),
    ANALYSIS_ARRAY("Analysis array", 1600f, listOf(GALVANICS), "Precision lab", 3),
    AUGMENTATION("Augmentation", 2200f, listOf(PART_ASSEMBLY, LIMB_CRAFTING), "Engineered limbs and organs", 3),
    ADV_ARMOR("Ghostweave loom", 2500f, listOf(PART_ASSEMBLY, BRIGANDINE_WORK), "Ghostweave shell", 3),
    // More industry, weapons and comforts
    WISPSILK_RESEARCH("Wispsilk", 700f, listOf(STITCHWORK), "Wispsilk fabric crop", 2),
    LAYERED_APPAREL("Layered apparel", 600f, listOf(STITCHWORK), "Canvas smocks, plumed crowns", 1),
    HORN_BACKING("Horn backing", 500f, listOf(STAVE_AND_STRING), "Hornwound", 2),
    HARDALLOY_BLADES("Alloy edging", 1500f, listOf(PART_ASSEMBLY, BASIC_MELEE), "Hardalloy cutter", 3),
    RIFLED_BARRELS("Rifled barrels", 1000f, listOf(POWDER_ARMS), "Swing repeater, scatter cannon, mag carbine", 2),
    SIEGE_ASSEMBLY("Siege assembly", 1800f, listOf(RIFLED_BARRELS, PART_ASSEMBLY), "Spinner rack", 3),
    PULSE_ARMS("Pulse arms", 2200f, listOf(PART_ASSEMBLY, ANALYSIS_ARRAY), "Pulse carbine", 3),
    BLAST_CHEMISTRY("Blast chemistry", 900f, listOf(POWDER_ARMS), "Shrapnel charges, ember thrower", 2),
    PROPELLANT_LIFT("Propellant lift", 1800f, listOf(BLAST_CHEMISTRY, PART_ASSEMBLY), "Skyfall battery", 3),
    TRENCH_ARMOR("Trench weaving", 2000f, listOf(RIBBED_ARMOR, PART_ASSEMBLY), "Trench mail and visor", 3),
    EXOSUIT("Exosuit", 3200f, listOf(ADV_ARMOR, ANALYSIS_ARRAY), "Exosuit and casque", 3),
    COMMS("Radio relay", 800f, listOf(GALVANICS), "Signal desk, summons flare", 2),
    SHUTTER_DOORS("Hatch mechanics", 600f, listOf(GALVANICS), "Slide hatch", 2),
    VENTILATION("Air exchange", 500f, listOf(GALVANICS), "Vents", 2),
    FROST_VENTING("Frost venting", 500f, listOf(FORGEWORK), "Frost vault", 2),
    GLOW_PANELS("Glow panels", 1100f, listOf(SUNLIGHT_HARVESTING, SOILLESS_GROWING), "Glow panel", 3),
    STIMULANT_BLENDS("Stimulant blends", 900f, listOf(TINCTURE_CHEMISTRY), "Pale dust, white rush, dawn tablet", 2),
    SURGE_TONIC("Surge tonic", 1100f, listOf(STIMULANT_BLENDS, PHARMACOLOGY), "Surge tonic", 3),
    BANQUET_COOKERY("Banquet cookery", 700f, listOf(TRAIL_CAKE), "Banquet cookery", 2),
    // Ship
    STASIS("Stasis", 3000f, listOf(ANALYSIS_ARRAY), "Cold pods", 3),
    THRUST_DRIVE("Thrust drive", 3500f, listOf(ANALYSIS_ARRAY, PART_ASSEMBLY), "Thrust drive", 3),
    CORE_FURNACE("Core furnace", 4000f, listOf(THRUST_DRIVE), "Core furnace", 3),
    NAVIGATION_BRAIN("Navigation brain", 3500f, listOf(ANALYSIS_ARRAY), "Navigation brain", 3),
    // Production and utility
    SUBSOIL_BORING("Subsoil boring", 1200f, listOf(ANALYSIS_ARRAY), "Subsoil auger", 3),
    VAT_CULTURING("Vat culturing", 500f, listOf(SOILLESS_GROWING), "Mash tap", 2),
    STONE_CARVING("Stone carving", 300f, listOf(ROCK_SPLITTING), "Chisel bench", 1),
    PYRE_RITES("Pyre rites", 400f, listOf(PHARMACOLOGY), "Pyre house", 2),
    ;

    val cost get() = baseCost * 16f
}
