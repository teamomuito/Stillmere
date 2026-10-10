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
    PLASTEEL("Plasteel", ItemType.PLASTEEL, 10, 20, 2200f),
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
    SHOOTING("Shooting"), MELEE("Melee"), CONSTRUCTION("Construction"), MINING("Mining"),
    COOKING("Cooking"), PLANTS("Plants"), ANIMALS("Animals"), CRAFTING("Crafting"),
    ARTISTIC("Artistic"), MEDICINE("Medicine"), SOCIAL("Social"), INTELLECTUAL("Intellectual"),
}

fun WorkType.skill(): SkillType? = when (this) {
    WorkType.DOCTOR -> SkillType.MEDICINE
    WorkType.WARDEN -> SkillType.SOCIAL
    WorkType.HANDLE -> SkillType.ANIMALS
    WorkType.COOK -> SkillType.COOKING
    WorkType.HUNT -> SkillType.SHOOTING
    WorkType.CONSTRUCT -> SkillType.CONSTRUCTION
    WorkType.GROW, WorkType.PLANT_CUT -> SkillType.PLANTS
    WorkType.MINE -> SkillType.MINING
    WorkType.SMITH, WorkType.TAILOR, WorkType.CRAFT -> SkillType.CRAFTING
    WorkType.ART -> SkillType.ARTISTIC
    WorkType.RESEARCH -> SkillType.INTELLECTUAL
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
    WILD_HEALROOT("Wild healroot", 6f, ItemType.HEALROOT, 3, 120, 0, false),
    RICE("Rice", 5.8f, ItemType.RICE, 12, 110, 170, true),
    POTATO("Potatoes", 5.8f, ItemType.POTATOES, 11, 110, 170, true),
    CORN("Corn", 11.5f, ItemType.CORN, 40, 110, 170, true),
    STRAWBERRY("Strawberries", 6.5f, ItemType.STRAWBERRIES, 10, 110, 170, true),
    COTTON("Cotton", 5.5f, ItemType.CLOTH, 14, 110, 170, true),
    HEALROOT("Healroot", 8f, ItemType.HEALROOT, 5, 110, 170, true),
    SMOKELEAF("Smokeleaf", 7f, ItemType.SMOKELEAF, 10, 110, 170, true),
    PSYCHOID("Psychoid", 7f, ItemType.PSYCHOID, 8, 110, 170, true),
    DEVILSTRAND_CROP("Devilstrand", 15f, ItemType.DEVILSTRAND, 8, 110, 170, true, minTemp = 10f, flammable = 0.1f, research = Research.DEVILSTRAND_RESEARCH),
    HAYGRASS("Hay grass", 3.5f, ItemType.HAY, 20, 90, 120, true, minTemp = 3f);

    val isTree get() = wood
    val regrows get() = this == BERRY || this == WILD_HEALROOT
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
    STONE_CHUNK("Stone chunk", ItemCat.RESOURCE, 1, 0.1f),
    STONE("Stone blocks", ItemCat.RESOURCE, 75, 0.6f),
    STEEL("Steel", ItemCat.RESOURCE, 75, 1.9f),
    PLASTEEL("Plasteel", ItemCat.RESOURCE, 75, 9f),
    SILVER("Silver", ItemCat.RESOURCE, 500, 1f),
    GOLD("Gold", ItemCat.RESOURCE, 500, 10f),
    COMPONENT("Components", ItemCat.RESOURCE, 50, 32f),
    DEVILSTRAND("Devilstrand", ItemCat.RESOURCE, 75, 2.4f, flammable = 0.4f),
    CLOTH("Cloth", ItemCat.RESOURCE, 75, 1.3f, flammable = 1f),
    LEATHER("Leather", ItemCat.RESOURCE, 75, 2.1f, flammable = 0.6f),
    WOOL("Wool", ItemCat.RESOURCE, 75, 1.4f, flammable = 1f),

    RICE("Rice", ItemCat.FOOD_PLANT, 75, 1.1f, 0.05f, 40f),
    POTATOES("Potatoes", ItemCat.FOOD_PLANT, 75, 1.1f, 0.05f, 40f),
    CORN("Corn", ItemCat.FOOD_PLANT, 75, 1.1f, 0.05f, 40f),
    STRAWBERRIES("Strawberries", ItemCat.FOOD_PLANT, 75, 1.2f, 0.05f, 10f),
    MEAT("Meat", ItemCat.FOOD_MEAT, 75, 1.8f, 0.05f, 4f),
    HUMAN_MEAT("Human meat", ItemCat.FOOD_MEAT, 75, 1.8f, 0.05f, 4f),
    INSECT_MEAT("Insect meat", ItemCat.FOOD_MEAT, 75, 1.2f, 0.05f, 4f),
    EGGS("Eggs", ItemCat.FOOD_MEAT, 75, 2f, 0.05f, 8f),
    MILK("Milk", ItemCat.FOOD_MEAT, 75, 2f, 0.05f, 4f),
    MEAL_SIMPLE("Simple meal", ItemCat.FOOD_MEAL, 10, 10f, 0.9f, 4f),
    MEAL_FINE("Fine meal", ItemCat.FOOD_MEAL, 10, 15f, 0.9f, 4f),
    MEAL_LAVISH("Lavish meal", ItemCat.FOOD_MEAL, 10, 28f, 0.9f, 4f),
    MEAL_PACKAGED("Packaged survival meal", ItemCat.FOOD_MEAL, 10, 12f, 0.9f, 0f),
    PEMMICAN("Pemmican", ItemCat.FOOD_MEAL, 50, 3f, 0.05f, 0f),
    KIBBLE("Kibble", ItemCat.FOOD_ANIMAL, 75, 0.9f, 0.05f, 0f, humanFood = false),
    HAY("Hay", ItemCat.FOOD_ANIMAL, 75, 0.4f, 0.05f, 40f, flammable = 1f, humanFood = false),

    HEALROOT("Healroot", ItemCat.MEDICINE, 75, 1f, flammable = 0.8f),
    MEDS_HERBAL("Herbal medicine", ItemCat.MEDICINE, 25, 10f, potency = 0.6f, maxTendQuality = 0.7f),
    MEDS_INDUSTRIAL("Medicine", ItemCat.MEDICINE, 25, 18f, potency = 1.0f),

    BEER("Beer", ItemCat.DRUG, 25, 12f),
    SMOKELEAF("Smokeleaf leaves", ItemCat.DRUG, 75, 2f, flammable = 1f),
    JOINT("Smokeleaf joint", ItemCat.DRUG, 25, 14f, flammable = 1f),
    PSYCHOID("Psychoid leaves", ItemCat.DRUG, 75, 3f, flammable = 1f),
    FLAKE("Flake", ItemCat.DRUG, 25, 14f),
    YAYO("Yayo", ItemCat.DRUG, 25, 20f),
    GO_JUICE("Go-juice", ItemCat.DRUG, 25, 32f),
    WAKE_UP("Wake-up", ItemCat.DRUG, 25, 24f),
    PSYCHITE_TEA("Psychite tea", ItemCat.DRUG, 25, 14f),

    SCULPTURE_SMALL("Small sculpture", ItemCat.ART, 1, 80f),
    SCULPTURE_LARGE("Large sculpture", ItemCat.ART, 1, 220f),

    // Weapons
    W_KNIFE("Knife", ItemCat.WEAPON, 1, 20f, weapon = Weapon.KNIFE),
    W_CLUB("Club", ItemCat.WEAPON, 1, 9f, weapon = Weapon.CLUB, flammable = 1f),
    W_SPEAR("Spear", ItemCat.WEAPON, 1, 26f, weapon = Weapon.SPEAR, flammable = 1f),
    W_MACE("Mace", ItemCat.WEAPON, 1, 40f, weapon = Weapon.MACE),
    W_LONGSWORD("Longsword", ItemCat.WEAPON, 1, 90f, weapon = Weapon.LONGSWORD),
    W_BOW("Short bow", ItemCat.WEAPON, 1, 22f, weapon = Weapon.BOW, flammable = 1f),
    W_GREATBOW("Greatbow", ItemCat.WEAPON, 1, 40f, weapon = Weapon.GREATBOW, flammable = 1f),
    W_REVOLVER("Revolver", ItemCat.WEAPON, 1, 90f, weapon = Weapon.REVOLVER),
    W_AUTOPISTOL("Autopistol", ItemCat.WEAPON, 1, 95f, weapon = Weapon.AUTOPISTOL),
    W_BOLT("Bolt-action rifle", ItemCat.WEAPON, 1, 125f, weapon = Weapon.BOLT_RIFLE),
    W_SHOTGUN("Pump shotgun", ItemCat.WEAPON, 1, 140f, weapon = Weapon.SHOTGUN),
    W_SMG("Machine pistol", ItemCat.WEAPON, 1, 130f, weapon = Weapon.SMG),
    W_RIFLE("Assault rifle", ItemCat.WEAPON, 1, 190f, weapon = Weapon.RIFLE),
    W_LMG("Light machine gun", ItemCat.WEAPON, 1, 260f, weapon = Weapon.LMG),
    W_GLADIUS("Gladius", ItemCat.WEAPON, 1, 70f, weapon = Weapon.GLADIUS),
    W_PLASTEEL_SWORD("Plasteel sword", ItemCat.WEAPON, 1, 300f, weapon = Weapon.PLASTEEL_SWORD),
    W_RECURVE("Recurve bow", ItemCat.WEAPON, 1, 55f, weapon = Weapon.RECURVE_BOW, flammable = 1f),
    W_LEVER("Lever-action rifle", ItemCat.WEAPON, 1, 150f, weapon = Weapon.LEVER_RIFLE),
    W_CHAIN_SHOTGUN("Chain shotgun", ItemCat.WEAPON, 1, 210f, weapon = Weapon.CHAIN_SHOTGUN),
    W_HEAVY_SMG("Heavy SMG", ItemCat.WEAPON, 1, 210f, weapon = Weapon.HEAVY_SMG),
    W_MINIGUN("Minigun", ItemCat.WEAPON, 1, 550f, weapon = Weapon.MINIGUN),
    W_CHARGE_RIFLE("Charge rifle", ItemCat.WEAPON, 1, 480f, weapon = Weapon.CHARGE_RIFLE),
    W_INCENDIARY("Incendiary launcher", ItemCat.WEAPON, 1, 220f, weapon = Weapon.INCENDIARY_LAUNCHER),
    W_FRAG("Frag grenades", ItemCat.WEAPON, 1, 120f, weapon = Weapon.FRAG_GRENADE),
    W_DOOMSDAY("Doomsday rocket launcher", ItemCat.WEAPON, 1, 650f, weapon = Weapon.DOOMSDAY),
    W_SNIPER("Sniper rifle", ItemCat.WEAPON, 1, 340f, weapon = Weapon.SNIPER),

    // Apparel
    A_TSHIRT("T-shirt", ItemCat.APPAREL, 1, 15f, apparel = Apparel.TSHIRT, flammable = 1f),
    A_BUTTONDOWN("Button-down shirt", ItemCat.APPAREL, 1, 25f, apparel = Apparel.BUTTONDOWN, flammable = 1f),
    A_PANTS("Pants", ItemCat.APPAREL, 1, 18f, apparel = Apparel.PANTS, flammable = 1f),
    A_TRIBAL("Tribalwear", ItemCat.APPAREL, 1, 25f, apparel = Apparel.TRIBAL, flammable = 0.7f),
    A_DUSTER("Duster", ItemCat.APPAREL, 1, 55f, apparel = Apparel.DUSTER, flammable = 0.8f),
    A_PARKA("Parka", ItemCat.APPAREL, 1, 70f, apparel = Apparel.PARKA, flammable = 1f),
    A_HAT("Cowboy hat", ItemCat.APPAREL, 1, 25f, apparel = Apparel.COWBOY_HAT, flammable = 0.8f),
    A_BEANIE("Tuque", ItemCat.APPAREL, 1, 25f, apparel = Apparel.TUQUE, flammable = 1f),
    A_FLAK_VEST("Flak vest", ItemCat.APPAREL, 1, 90f, apparel = Apparel.FLAK_VEST),
    A_FLAK_PANTS("Flak pants", ItemCat.APPAREL, 1, 70f, apparel = Apparel.FLAK_PANTS),
    A_FLAK_JACKET("Flak jacket", ItemCat.APPAREL, 1, 120f, apparel = Apparel.FLAK_JACKET),
    A_HELMET("Simple helmet", ItemCat.APPAREL, 1, 55f, apparel = Apparel.HELMET),
    A_ARMOR("Plate armor", ItemCat.APPAREL, 1, 250f, apparel = Apparel.PLATE_ARMOR),
    A_JACKET("Jacket", ItemCat.APPAREL, 1, 60f, apparel = Apparel.JACKET, flammable = 1f),
    A_MARINE("Marine armor", ItemCat.APPAREL, 1, 520f, apparel = Apparel.MARINE_ARMOR),
    A_MARINE_HELM("Marine helmet", ItemCat.APPAREL, 1, 240f, apparel = Apparel.MARINE_HELM),
    A_RECON_HELM("Recon helmet", ItemCat.APPAREL, 1, 200f, apparel = Apparel.RECON_HELM),
    A_POWER_ARMOR("Powered armor", ItemCat.APPAREL, 1, 900f, apparel = Apparel.POWER_ARMOR),
    A_POWER_HELM("Powered helmet", ItemCat.APPAREL, 1, 380f, apparel = Apparel.POWER_HELM),
    A_HEADDRESS("Tribal headdress", ItemCat.APPAREL, 1, 45f, apparel = Apparel.HEADDRESS, flammable = 0.8f),
    A_RECON("Recon armor", ItemCat.APPAREL, 1, 480f, apparel = Apparel.RECON_ARMOR),

    // Misc
    CORPSE_HUMAN("Corpse", ItemCat.MISC, 1, 0f, flammable = 0.3f),
    SHELL("Mortar shell", ItemCat.MISC, 25, 15f),
    BERRIES("Berries", ItemCat.FOOD_PLANT, 75, 1.2f, 0.05f, 10f),
    MEAL_NUTRIENT("Nutrient paste meal", ItemCat.FOOD_MEAL, 25, 3f, 0.9f, 0f),
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
    CRUSH("Crush", false, 0.2f, 1.1f, 0f), ACID("Toxic burn", false, 0f, 1f, 0.1f),
}

enum class Weapon(
    val label: String, val ranged: Boolean, val damage: Float, val range: Float, val cooldown: Int, val accuracy: Float,
    val kind: DamageKind, val burst: Int = 1, val armorPen: Float = 0f, val warmup: Int = 0, val aoe: Float = 0f,
    /** Projectiles per burst that each roll to hit; [damage] is shared between them. */
    val pellets: Int = 1,
) {
    FISTS("Fists", false, 5f, 1.5f, 38, 0.82f, DamageKind.BRUISE),
    KNIFE("Knife", false, 9f, 1.5f, 34, 0.86f, DamageKind.CUT),
    CLUB("Club", false, 11f, 1.5f, 46, 0.8f, DamageKind.BRUISE),
    SPEAR("Spear", false, 13f, 1.9f, 48, 0.82f, DamageKind.STAB),
    MACE("Mace", false, 14f, 1.5f, 50, 0.8f, DamageKind.BRUISE, armorPen = 0.2f),
    LONGSWORD("Longsword", false, 18f, 1.5f, 42, 0.85f, DamageKind.CUT, armorPen = 0.25f),
    BOW("Short bow", true, 9f, 19f, 50, 0.72f, DamageKind.STAB, warmup = 10),
    GREATBOW("Greatbow", true, 15f, 26f, 70, 0.72f, DamageKind.STAB, warmup = 14),
    REVOLVER("Revolver", true, 12f, 20f, 55, 0.8f, DamageKind.BULLET, warmup = 8),
    AUTOPISTOL("Autopistol", true, 12f, 20f, 55, 0.78f, DamageKind.BULLET, warmup = 7),
    BOLT_RIFLE("Bolt-action rifle", true, 15f, 30f, 80, 0.78f, DamageKind.BULLET, warmup = 14),
    SHOTGUN("Pump shotgun", true, 18f, 12f, 70, 0.85f, DamageKind.BULLET, warmup = 10, burst = 1, pellets = 8),
    SMG("Machine pistol", true, 10f, 16f, 50, 0.72f, DamageKind.BULLET, burst = 3, warmup = 8),
    RIFLE("Assault rifle", true, 11f, 28f, 55, 0.76f, DamageKind.BULLET, burst = 3, warmup = 10),
    LMG("Light machine gun", true, 11f, 30f, 70, 0.74f, DamageKind.BULLET, burst = 5, warmup = 20),
    SNIPER("Sniper rifle", true, 22f, 40f, 110, 0.8f, DamageKind.BULLET, warmup = 22, armorPen = 0.3f),
    GLADIUS("Gladius", false, 15f, 1.5f, 38, 0.86f, DamageKind.CUT, armorPen = 0.1f),
    PLASTEEL_SWORD("Plasteel sword", false, 24f, 1.5f, 36, 0.88f, DamageKind.CUT, armorPen = 0.4f),
    RECURVE_BOW("Recurve bow", true, 11f, 24f, 55, 0.74f, DamageKind.STAB, warmup = 11),
    LEVER_RIFLE("Lever-action rifle", true, 14f, 24f, 68, 0.78f, DamageKind.BULLET, warmup = 12),
    CHAIN_SHOTGUN("Chain shotgun", true, 16f, 14f, 60, 0.85f, DamageKind.BULLET, burst = 2, warmup = 10, pellets = 8),
    HEAVY_SMG("Heavy SMG", true, 11f, 18f, 52, 0.74f, DamageKind.BULLET, burst = 3, warmup = 9),
    MINIGUN("Minigun", true, 10f, 26f, 64, 0.68f, DamageKind.BULLET, burst = 8, warmup = 26),
    CHARGE_RIFLE("Charge rifle", true, 18f, 30f, 70, 0.8f, DamageKind.BULLET, burst = 3, armorPen = 0.3f, warmup = 14),
    INCENDIARY_LAUNCHER("Incendiary launcher", true, 8f, 22f, 160, 0.6f, DamageKind.BURN, warmup = 22, aoe = 2.2f),
    FRAG_GRENADE("Frag grenades", true, 36f, 14f, 220, 0.55f, DamageKind.BLAST, warmup = 14, aoe = 2.4f),
    DOOMSDAY("Doomsday rocket launcher", true, 70f, 30f, 400, 0.55f, DamageKind.BLAST, warmup = 30, aoe = 3.5f),
    TURRET_GUN("Turret", true, 11f, 28f, 50, 0.75f, DamageKind.BULLET, burst = 3),
    MORTAR_SHELL("Mortar", true, 40f, 55f, 300, 0.5f, DamageKind.BLAST, aoe = 2.6f),
    MOLOTOV("Molotov", true, 5f, 15f, 100, 0.8f, DamageKind.BURN, aoe = 1.8f),
    BLADE("Mechanoid blades", false, 18f, 1.6f, 30, 0.85f, DamageKind.CUT, armorPen = 0.2f),
    LANCE("Charge lance", true, 28f, 24f, 110, 0.82f, DamageKind.BULLET, warmup = 25, armorPen = 0.45f),
    MECH_GUN("Mechanoid cannon", true, 12f, 22f, 90, 0.7f, DamageKind.BULLET, burst = 5, warmup = 20, armorPen = 0.1f),
    TEETH("Teeth", false, 8f, 1.5f, 36, 0.85f, DamageKind.BITE),
    CLAWS("Claws", false, 6f, 1.5f, 32, 0.85f, DamageKind.SCRATCH),
    HEAD_BUTT("Horns", false, 12f, 1.5f, 50, 0.8f, DamageKind.BRUISE),
    TRAMPLE("Trample", false, 20f, 1.5f, 60, 0.8f, DamageKind.CRUSH),
    MINI_TURRET_GUN("Mini-turret", true, 8f, 22f, 40, 0.7f, DamageKind.BULLET, burst = 2);

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
    TSHIRT("T-shirt", ApparelSlot.SHIRT, 2 or 4, 2f, 2f, 0.03f, 0.01f, 60f),
    BUTTONDOWN("Button-down shirt", ApparelSlot.SHIRT, 2 or 4, 4f, 3f, 0.05f, 0.02f, 80f, 0.5f),
    PANTS("Pants", ApparelSlot.PANTS, 8, 3f, 2f, 0.04f, 0.01f, 80f),
    TRIBAL("Tribalwear", ApparelSlot.SHIRT, 2 or 8, 5f, 4f, 0.06f, 0.02f, 90f),
    DUSTER("Duster", ApparelSlot.OUTER, 2 or 4 or 8, 14f, 5f, 0.15f, 0.04f, 150f, 0.5f),
    PARKA("Parka", ApparelSlot.OUTER, 2 or 4 or 8, 40f, -8f, 0.14f, 0.05f, 160f),
    COWBOY_HAT("Cowboy hat", ApparelSlot.HEAD, 1, 3f, 8f, 0.04f, 0.02f, 80f, 0.5f),
    TUQUE("Tuque", ApparelSlot.HEAD, 1, 12f, -3f, 0.02f, 0.02f, 70f),
    FLAK_VEST("Flak vest", ApparelSlot.OUTER, 2, 5f, -3f, 0.45f, 0.1f, 160f),
    FLAK_PANTS("Flak pants", ApparelSlot.PANTS, 8, 3f, -1f, 0.4f, 0.09f, 160f),
    FLAK_JACKET("Flak jacket", ApparelSlot.OUTER, 2 or 4, 8f, -4f, 0.52f, 0.14f, 180f),
    HELMET("Simple helmet", ApparelSlot.HEAD, 1, 2f, -4f, 0.4f, 0.25f, 150f),
    PLATE_ARMOR("Plate armor", ApparelSlot.OUTER, 2 or 4 or 8, 6f, -8f, 0.75f, 0.35f, 350f, -0.5f),
    JACKET("Jacket", ApparelSlot.OUTER, 2 or 4, 20f, 3f, 0.08f, 0.03f, 130f, 0.3f),
    MARINE_ARMOR("Marine armor", ApparelSlot.OUTER, 2 or 4 or 8, 14f, 6f, 0.7f, 0.4f, 380f),
    MARINE_HELM("Marine helmet", ApparelSlot.HEAD, 1, 4f, 0f, 0.6f, 0.45f, 220f),
    RECON_HELM("Recon helmet", ApparelSlot.HEAD, 1, 5f, 3f, 0.5f, 0.35f, 200f),
    POWER_ARMOR("Powered armor", ApparelSlot.OUTER, 2 or 4 or 8, 30f, 18f, 0.8f, 0.55f, 460f, -0.5f),
    POWER_HELM("Powered helmet", ApparelSlot.HEAD, 1, 8f, 6f, 0.72f, 0.55f, 260f),
    HEADDRESS("Tribal headdress", ApparelSlot.HEAD, 1, 6f, 1f, 0.05f, 0.04f, 80f, 1.2f),
    RECON_ARMOR("Recon armor", ApparelSlot.OUTER, 2 or 4 or 8, 22f, 12f, 0.58f, 0.35f, 320f),
}

enum class Trait(val label: String, val desc: String) {
    HARD_WORKER("Industrious", "Works faster"), LAZY("Lazy", "Works slower"),
    TOUGH("Tough", "Takes less damage"), WIMP("Wimp", "Feels pain more"),
    OPTIMIST("Optimist", "+10% mood"), PESSIMIST("Pessimist", "-10% mood"),
    NIMBLE("Nimble", "Moves faster"), SLOW_WALKER("Slowpoke", "Moves slower"),
    NIGHT_OWL("Night owl", "Prefers working at night"), FAST_LEARNER("Fast learner", "Learns skills faster"),
    BRAWLER("Brawler", "Melee only"), TRIGGER_HAPPY("Trigger-happy", "Shoots faster"),
    KIND("Kind", "Gets along with everyone"), ABRASIVE("Abrasive", "Insults others"),
    GOURMAND("Gourmand", "Needs more food, loves eating"), GREEN_THUMB("Green thumb", "Loves plants"),
    NUDIST("Nudist", "Hates clothes"), PYROMANIAC("Pyromaniac", "Loves fire"),
    CANNIBAL("Cannibal", "Enjoys human flesh"), SANGUINE("Sanguine", "Cheerful"),
    DEPRESSIVE("Depressive", "Gloomy"), TORTURED_ARTIST("Tortured artist", "Better art, moody"),
    BEAUTIFUL("Beautiful", "Pleasant to look at"), UGLY("Ugly", "Unpleasant to look at"),
    PSYCHOPATH("Psychopath", "Unbothered by death"), BLOODLUST("Bloodlust", "Enjoys violence"),
    TOO_SMART("Too smart", "Fast researcher"), SLOW_LEARNER("Slow learner", "Learns skills slowly"),
    CARNIVORE("Carnivore", "Loves meat"), TRANSHUMANIST("Transhumanist", "Loves bionics"),
    GAY("Gay", "Attracted to the same gender"), BISEXUAL("Bisexual", "Attracted to anyone"), ASEXUAL("Asexual", "No romantic interest"),
    IRON_WILLED("Iron-willed", "Rarely has mental breaks"), VOLATILE("Volatile", "Breaks easily"),
    SOCIABLE("Sociable", "Enjoys talking, better at it"), CAREFUL_SHOOTER("Careful shooter", "Accurate but slower"),
    CREATIVE("Creative", "Makes finer things"), GREEDY("Greedy", "Wants wealth"), ASCETIC("Ascetic", "Content with little"),
    JOGGER("Jogger", "Moves faster"), SUPER_IMMUNE("Super-immune", "Fights illness well"),
}

enum class Research(
    val label: String, val baseCost: Float, val needs: List<Research> = emptyList(), val unlocks: String = "",
    val tier: Int = 1, val bench: Int = 0,
) {
    // Neolithic / basics
    COMPLEX_FURNITURE("Complex furniture", 400f, emptyList(), "Chairs, stools, dressers, plant pots"),
    CARPETS("Carpets", 300f, listOf(COMPLEX_FURNITURE), "Carpet floors"),
    STONECUTTING("Stonecutting", 300f, emptyList(), "Stonecutter table"),
    SMITHING("Smithing", 400f, emptyList(), "Smithy: weapons, steel walls, armor"),
    TAILORING("Tailoring", 300f, emptyList(), "Tailor bench"),
    PEMMICAN("Pemmican", 300f, emptyList(), "Pemmican"),
    BREWING("Brewing", 500f, emptyList(), "Beer"),
    DRUGS("Drug production", 600f, listOf(BREWING), "Drug lab: joints, tea"),
    HERBAL_MEDICINE("Herbal medicine", 500f, emptyList(), "Herbal medicine"),
    BASIC_MELEE("Mace and spear", 300f, listOf(SMITHING), "Mace, spear, longsword"),
    BOWS("Bows", 300f, listOf(SMITHING), "Short bow and greatbow"),
    // Medieval / industrial
    ELECTRICITY("Electricity", 1000f, listOf(SMITHING), "Conduits, generators, lamps, heaters, coolers", 2),
    BATTERIES("Batteries", 600f, listOf(ELECTRICITY), "Batteries, switches", 2),
    SOLAR_POWER("Solar power", 800f, listOf(ELECTRICITY), "Solar panels", 2),
    WIND_POWER("Wind power", 700f, listOf(ELECTRICITY), "Wind turbines", 2),
    MACHINING("Machining", 700f, listOf(SMITHING), "Machining table: components, firearms", 2),
    FIREARMS("Firearms", 800f, listOf(MACHINING), "Revolver, autopistol, shotgun, rifles", 2),
    GUN_TURRETS("Gun turrets", 800f, listOf(FIREARMS), "Auto-firing turrets", 2),
    MORTARS("Mortars", 900f, listOf(GUN_TURRETS), "Mortars and shells", 2),
    FLAK_ARMOR("Flak armor", 800f, listOf(MACHINING, TAILORING), "Flak vest, pants, jacket, helmet", 2),
    PLATE_ARMOR("Plate armor", 1000f, listOf(FLAK_ARMOR), "Plate armor", 3),
    HYDROPONICS("Hydroponics", 1100f, listOf(ELECTRICITY), "Hydroponics basins", 2),
    ELECTRIC_COOKING("Electric stove", 600f, listOf(ELECTRICITY), "Electric stove", 2),
    MEDICINE("Medicine production", 1000f, listOf(HERBAL_MEDICINE, MACHINING), "Industrial medicine", 2),
    BIONICS_BASIC("Prosthetics", 700f, listOf(SMITHING), "Peg legs, wooden hands", 2),
    SURGERY("Sterile surgery", 700f, listOf(HERBAL_MEDICINE), "Hospital beds", 2),
    FABRICATION("Fabrication", 2000f, listOf(MACHINING, ELECTRICITY), "Fabrication bench: plasteel gear, advanced components", 3),
    MULTIANALYZER("Multi-analyzer", 1600f, listOf(ELECTRICITY), "Hi-tech research bench", 3),
    BIONICS("Bionics", 2200f, listOf(FABRICATION, BIONICS_BASIC), "Bionic limbs and organs", 3),
    ADV_ARMOR("Recon armor", 2500f, listOf(FABRICATION, PLATE_ARMOR), "Recon armor", 3),
    // More industry, weapons and comforts
    DEVILSTRAND_RESEARCH("Devilstrand", 700f, listOf(TAILORING), "Devilstrand fabric crop", 2),
    COMPLEX_CLOTHING("Complex clothing", 600f, listOf(TAILORING), "Jackets, tribal headdresses", 1),
    RECURVE("Recurve bow", 500f, listOf(BOWS), "Recurve bow", 2),
    PLASTEEL_MELEE("Plasteel blades", 1500f, listOf(FABRICATION, BASIC_MELEE), "Plasteel sword", 3),
    ADV_FIREARMS("Advanced firearms", 1000f, listOf(FIREARMS), "Lever rifle, chain shotgun, heavy SMG", 2),
    HEAVY_WEAPONS("Heavy weapons", 1800f, listOf(ADV_FIREARMS, FABRICATION), "Minigun", 3),
    CHARGE_WEAPONS("Charge weapons", 2200f, listOf(FABRICATION, MULTIANALYZER), "Charge rifle", 3),
    EXPLOSIVES("Explosives", 900f, listOf(FIREARMS), "Frag grenades, incendiary launcher", 2),
    ROCKETS("Rockets", 1800f, listOf(EXPLOSIVES, FABRICATION), "Doomsday rocket launcher", 3),
    MARINE_ARMOR("Marine armor", 2000f, listOf(FLAK_ARMOR, FABRICATION), "Marine armor and helmet", 3),
    POWER_ARMOR("Powered armor", 3200f, listOf(ADV_ARMOR, MULTIANALYZER), "Powered armor and helmet", 3),
    COMMS("Long-range communication", 800f, listOf(ELECTRICITY), "Comms console, trade beacon", 2),
    AUTODOORS("Autodoors", 600f, listOf(ELECTRICITY), "Autodoor", 2),
    VENTILATION("Ventilation", 500f, listOf(ELECTRICITY), "Vents", 2),
    PASSIVE_COOLING("Passive cooling", 500f, listOf(SMITHING), "Passive cooler", 2),
    SUN_LAMPS("Sun lamps", 1100f, listOf(SOLAR_POWER, HYDROPONICS), "Sun lamp", 3),
    FLAKE_YAYO("Flake and yayo", 900f, listOf(DRUGS), "Flake, yayo, wake-up", 2),
    GO_JUICE("Go-juice", 1100f, listOf(FLAKE_YAYO, MEDICINE), "Go-juice", 3),
    LAVISH_COOKING("Lavish meals", 700f, listOf(PEMMICAN), "Lavish meals", 2),
    // Ship
    CRYPTOSLEEP("Cryptosleep", 3000f, listOf(MULTIANALYZER), "Cryptosleep caskets", 3),
    SHIP_ENGINE("Ship engine", 3500f, listOf(MULTIANALYZER, FABRICATION), "Ship engine", 3),
    SHIP_REACTOR("Ship reactor", 4000f, listOf(SHIP_ENGINE), "Ship reactor", 3),
    SHIP_COMPUTER("Ship computer core", 3500f, listOf(MULTIANALYZER), "Ship computer core", 3),
    // Production and utility
    DEEP_DRILLING("Deep drilling", 1200f, listOf(MULTIANALYZER), "Deep drill", 3),
    NUTRIENT_PASTE("Nutrient paste", 500f, listOf(HYDROPONICS), "Nutrient paste dispenser", 2),
    SCULPTING("Sculpting", 300f, listOf(STONECUTTING), "Sculptor's table", 1),
    CREMATION("Cremation", 400f, listOf(MEDICINE), "Crematorium", 2),
    ;

    val cost get() = baseCost * 16f
}
