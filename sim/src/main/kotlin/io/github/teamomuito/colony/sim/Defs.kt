package io.github.teamomuito.colony.sim

const val TICKS_PER_HOUR = 1000
const val TICKS_PER_DAY = 24000
const val DAYS_PER_SEASON = 15

enum class Terrain(val label: String, val passable: Boolean, val fertility: Float, val cost: Int) {
    SOIL("Soil", true, 1f, 1),
    RICH_SOIL("Rich soil", true, 1.4f, 1),
    GRAVEL("Gravel", true, 0f, 1),
    SAND("Sand", true, 0.15f, 2),
    MARSH("Marsh", true, 0.9f, 3),
    WATER_SHALLOW("Shallow water", true, 0f, 4),
    WATER_DEEP("Deep water", false, 0f, 0),
    ROCK("Rock", false, 0f, 0),
}

enum class ItemType(val label: String, val nutrition: Float, val stack: Int, val value: Float) {
    WOOD("Wood", 0f, 75, 0.5f),
    STONE("Stone blocks", 0f, 75, 0.6f),
    STEEL("Steel", 0f, 75, 1.9f),
    RAW_FOOD("Raw food", 0.05f, 75, 0.4f),
    MEAL("Simple meal", 0.9f, 10, 10f),
}

enum class Season(val label: String, val baseTemp: Float) {
    SPRING("Spring", 13f), SUMMER("Summer", 25f), FALL("Fall", 10f), WINTER("Winter", -7f)
}

enum class WorkType(val label: String) {
    DOCTOR("Doctor"), COOK("Cook"), CONSTRUCT("Construct"), GROW("Grow"), MINE("Mine"),
    PLANT_CUT("Plant cut"), HAUL("Haul"), RESEARCH("Research")
}

enum class SkillType(val label: String) {
    SHOOTING("Shooting"), MELEE("Melee"), CONSTRUCTION("Construction"), MINING("Mining"),
    COOKING("Cooking"), PLANTS("Plants"), MEDICINE("Medicine"), INTELLECTUAL("Intellectual")
}

fun WorkType.skill(): SkillType = when (this) {
    WorkType.DOCTOR -> SkillType.MEDICINE
    WorkType.COOK -> SkillType.COOKING
    WorkType.CONSTRUCT -> SkillType.CONSTRUCTION
    WorkType.GROW, WorkType.PLANT_CUT -> SkillType.PLANTS
    WorkType.MINE -> SkillType.MINING
    WorkType.HAUL -> SkillType.CONSTRUCTION
    WorkType.RESEARCH -> SkillType.INTELLECTUAL
}

enum class PlantType(
    val label: String, val growDays: Float, val yieldType: ItemType?, val yieldCount: Int,
    val harvestWork: Int, val sowWork: Int, val crop: Boolean,
) {
    TREE("Tree", 0f, ItemType.WOOD, 20, 400, 0, false),
    BERRY("Berry bush", 0f, ItemType.RAW_FOOD, 8, 120, 0, false),
    RICE("Rice", 5.8f, ItemType.RAW_FOOD, 12, 110, 170, true),
    POTATO("Potatoes", 5.8f, ItemType.RAW_FOOD, 11, 110, 170, true),
    CORN("Corn", 11.5f, ItemType.RAW_FOOD, 40, 110, 170, true),
}

enum class Research(val label: String, val cost: Float, val needs: Research?, val unlocks: String) {
    SMITHING("Smithing", 5000f, null, "Steel walls and floors"),
    STONECUTTING("Stonecutting", 6000f, null, "Stone tile floors"),
    GUN_TURRETS("Gun turrets", 18000f, SMITHING, "Auto-firing turrets"),
    SHIP("Escape ship", 90000f, GUN_TURRETS, "Build a ship and leave the planet"),
}

enum class BuildDef(
    val label: String, val item: ItemType, val count: Int, val work: Int, val hp: Float,
    val blocksMove: Boolean = false, val blocksSight: Boolean = false, val isFloor: Boolean = false,
    val isDoor: Boolean = false, val research: Research? = null, val category: String = "Structure",
) {
    WOOD_WALL("Wooden wall", ItemType.WOOD, 5, 160, 150f, true, true),
    STONE_WALL("Stone wall", ItemType.STONE, 5, 280, 300f, true, true),
    STEEL_WALL("Steel wall", ItemType.STEEL, 5, 200, 400f, true, true, research = Research.SMITHING),
    DOOR("Wooden door", ItemType.WOOD, 15, 220, 120f, isDoor = true, blocksSight = false),
    WOOD_FLOOR("Wood floor", ItemType.WOOD, 3, 70, 1f, isFloor = true),
    STONE_FLOOR("Stone tiles", ItemType.STONE, 3, 100, 1f, isFloor = true, research = Research.STONECUTTING),
    STEEL_FLOOR("Steel tiles", ItemType.STEEL, 2, 90, 1f, isFloor = true, research = Research.SMITHING),
    BED("Bed", ItemType.WOOD, 30, 500, 100f, category = "Furniture"),
    TABLE("Table", ItemType.WOOD, 25, 350, 100f, blocksMove = true, category = "Furniture"),
    CAMPFIRE("Campfire", ItemType.WOOD, 10, 120, 40f, category = "Furniture"),
    STOVE("Stove", ItemType.STEEL, 50, 500, 100f, blocksMove = true, category = "Production"),
    RESEARCH_BENCH("Research bench", ItemType.STEEL, 60, 600, 100f, blocksMove = true, category = "Production"),
    TURRET("Gun turret", ItemType.STEEL, 80, 450, 200f, blocksMove = true, research = Research.GUN_TURRETS, category = "Security"),
    SHIP("Escape ship", ItemType.STEEL, 400, 4000, 500f, blocksMove = true, research = Research.SHIP, category = "Ship");

    val isWall get() = this == WOOD_WALL || this == STONE_WALL || this == STEEL_WALL
}

enum class Weapon(
    val label: String, val ranged: Boolean, val damage: Float, val range: Float, val cooldown: Int, val accuracy: Float,
) {
    FISTS("Fists", false, 5f, 1.5f, 40, 0.8f),
    KNIFE("Knife", false, 9f, 1.5f, 36, 0.85f),
    CLUB("Club", false, 11f, 1.5f, 46, 0.8f),
    REVOLVER("Revolver", true, 12f, 20f, 55, 0.8f),
    RIFLE("Assault rifle", true, 11f, 28f, 45, 0.75f),
}

enum class Trait(val label: String, val desc: String) {
    HARD_WORKER("Hard worker", "Works 25% faster"),
    LAZY("Lazy", "Works 25% slower"),
    TOUGH("Tough", "Takes less damage"),
    OPTIMIST("Optimist", "+10% mood"),
    PESSIMIST("Pessimist", "-10% mood"),
    NIMBLE("Nimble", "Moves faster"),
}
