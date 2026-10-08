package io.github.teamomuito.colony.sim

private fun c(t: ItemType, n: Int) = t to n

enum class BuildDef(
    val label: String, val cost: List<Pair<ItemType, Int>>, val work: Int, val hp: Float, val category: String,
    val blocksMove: Boolean = false, val blocksSight: Boolean = false, val isFloor: Boolean = false,
    val isDoor: Boolean = false, val research: Research? = null, val power: Float = 0f,
    val beauty: Float = 0f, val comfort: Float = 0f, val heat: Float = 0f, val light: Float = 0f,
    val workbench: Boolean = false, val fuelCap: Float = 0f, val joy: Float = 0f, val buildWork: WorkType = WorkType.CONSTRUCT,
    val flam: Float = 0.4f, val isWall: Boolean = false, val cover: Float = 0f, val sleeps: Boolean = false,
    val medical: Boolean = false, val skillRequirement: Int = 0, val trap: Boolean = false, val art: Boolean = false,
    val desc: String = "",
) {
    // Structure
    WOOD_WALL("Wooden wall", listOf(c(ItemType.WOOD, 5)), 160, 150f, "Structure", blocksMove = true, blocksSight = true, isWall = true, flam = 1f, beauty = -1f),
    STONE_WALL("Stone wall", listOf(c(ItemType.STONE, 5)), 280, 300f, "Structure", blocksMove = true, blocksSight = true, isWall = true, flam = 0f, research = null),
    STEEL_WALL("Steel wall", listOf(c(ItemType.STEEL, 5)), 200, 400f, "Structure", blocksMove = true, blocksSight = true, isWall = true, flam = 0f, research = Research.SMITHING),
    DOOR("Wooden door", listOf(c(ItemType.WOOD, 15)), 220, 120f, "Structure", isDoor = true, flam = 1f),
    SANDBAGS("Sandbags", listOf(c(ItemType.CLOTH, 5)), 80, 60f, "Security", cover = 0.55f, flam = 0.3f),
    WOOD_FLOOR("Wood floor", listOf(c(ItemType.WOOD, 3)), 70, 1f, "Floors", isFloor = true, beauty = 0.3f, flam = 1f),
    STONE_FLOOR("Stone tiles", listOf(c(ItemType.STONE, 2)), 100, 1f, "Floors", isFloor = true, beauty = 0.6f, flam = 0f, research = Research.STONECUTTING),
    STEEL_FLOOR("Steel tiles", listOf(c(ItemType.STEEL, 2)), 90, 1f, "Floors", isFloor = true, beauty = 0.3f, flam = 0f, research = Research.SMITHING),
    CARPET("Carpet", listOf(c(ItemType.CLOTH, 5)), 80, 1f, "Floors", isFloor = true, beauty = 1.1f, comfort = 0f, flam = 1f, research = Research.CARPETS),

    // Furniture
    SLEEPING_SPOT("Sleeping spot", emptyList(), 30, 10f, "Furniture", sleeps = true, comfort = 0.0f, beauty = -0.5f),
    BED("Bed", listOf(c(ItemType.WOOD, 40)), 500, 100f, "Furniture", sleeps = true, comfort = 0.7f, beauty = 0.5f, flam = 1f),
    HOSPITAL_BED("Hospital bed", listOf(c(ItemType.STEEL, 70), c(ItemType.COMPONENT, 2)), 700, 120f, "Furniture", sleeps = true, comfort = 0.75f, medical = true, research = Research.SURGERY, flam = 0.2f, beauty = 0.3f),
    TABLE("Table", listOf(c(ItemType.WOOD, 30)), 350, 100f, "Furniture", blocksMove = true, comfort = 0.0f, beauty = 0.4f, flam = 1f),
    STOOL("Stool", listOf(c(ItemType.WOOD, 8)), 120, 40f, "Furniture", comfort = 0.45f, research = Research.COMPLEX_FURNITURE, flam = 1f),
    CHAIR("Chair", listOf(c(ItemType.WOOD, 14)), 200, 60f, "Furniture", comfort = 0.7f, beauty = 0.3f, research = Research.COMPLEX_FURNITURE, flam = 1f),
    PLANT_POT("Plant pot", listOf(c(ItemType.WOOD, 14)), 150, 30f, "Furniture", beauty = 2.2f, research = Research.COMPLEX_FURNITURE, flam = 0.8f),
    SCULPTURE_SMALL("Small sculpture", listOf(c(ItemType.STONE, 50)), 3000, 80f, "Furniture", beauty = 6f, buildWork = WorkType.ART, art = true, blocksMove = true, research = Research.STONECUTTING, flam = 0f),
    SCULPTURE_LARGE("Large sculpture", listOf(c(ItemType.STONE, 140)), 7500, 160f, "Furniture", beauty = 20f, buildWork = WorkType.ART, art = true, blocksMove = true, research = Research.STONECUTTING, flam = 0f),
    TORCH_LAMP("Torch lamp", listOf(c(ItemType.WOOD, 12)), 80, 20f, "Furniture", light = 8f, fuelCap = 24f, heat = 1.5f, flam = 1f, beauty = 0.3f),
    STANDING_LAMP("Standing lamp", listOf(c(ItemType.STEEL, 10), c(ItemType.COMPONENT, 1)), 120, 40f, "Furniture", light = 10f, power = -30f, research = Research.ELECTRICITY, beauty = 0.5f),
    HORSESHOES("Horseshoes pin", listOf(c(ItemType.WOOD, 10)), 100, 20f, "Joy", joy = 0.35f, flam = 1f),
    CHESS_TABLE("Chess table", listOf(c(ItemType.WOOD, 40)), 300, 60f, "Joy", joy = 0.4f, blocksMove = true, research = Research.COMPLEX_FURNITURE, beauty = 0.4f, flam = 1f),
    TELEVISION("Television", listOf(c(ItemType.STEEL, 80), c(ItemType.COMPONENT, 2)), 400, 60f, "Joy", joy = 0.55f, power = -200f, research = Research.ELECTRICITY, blocksMove = true, beauty = 0.5f, light = 3f),
    BILLIARDS("Billiards table", listOf(c(ItemType.WOOD, 120), c(ItemType.STEEL, 25)), 900, 90f, "Joy", joy = 0.55f, blocksMove = true, research = Research.COMPLEX_FURNITURE, beauty = 1f, flam = 0.8f),

    // Production
    CRAFTING_SPOT("Crafting spot", emptyList(), 30, 10f, "Production", workbench = true),
    CAMPFIRE("Campfire", listOf(c(ItemType.WOOD, 10)), 100, 40f, "Production", workbench = true, fuelCap = 24f, heat = 10f, light = 7f, flam = 1f),
    STOVE_FUEL("Fueled stove", listOf(c(ItemType.STEEL, 40)), 450, 90f, "Production", blocksMove = true, workbench = true, fuelCap = 12f, heat = 3f, light = 3f),
    STOVE_ELEC("Electric stove", listOf(c(ItemType.STEEL, 80), c(ItemType.COMPONENT, 2)), 600, 90f, "Production", blocksMove = true, workbench = true, power = -350f, research = Research.ELECTRIC_COOKING),
    BUTCHER_TABLE("Butcher table", listOf(c(ItemType.WOOD, 40)), 300, 80f, "Production", blocksMove = true, workbench = true, flam = 0.8f),
    TAILOR_BENCH("Tailor bench", listOf(c(ItemType.WOOD, 40), c(ItemType.STEEL, 20)), 450, 80f, "Production", blocksMove = true, workbench = true, research = Research.TAILORING),
    SMITHY("Fueled smithy", listOf(c(ItemType.STEEL, 100), c(ItemType.STONE, 20)), 700, 120f, "Production", blocksMove = true, workbench = true, fuelCap = 12f, heat = 4f, research = Research.SMITHING, light = 3f),
    MACHINING("Machining table", listOf(c(ItemType.STEEL, 100), c(ItemType.COMPONENT, 2)), 800, 120f, "Production", blocksMove = true, workbench = true, power = -350f, research = Research.MACHINING),
    STONECUTTER("Stonecutter table", listOf(c(ItemType.STEEL, 25), c(ItemType.STONE_CHUNK, 1)), 400, 90f, "Production", blocksMove = true, workbench = true, research = Research.STONECUTTING),
    DRUG_LAB("Drug lab", listOf(c(ItemType.STEEL, 70), c(ItemType.COMPONENT, 2)), 600, 90f, "Production", blocksMove = true, workbench = true, power = -120f, research = Research.HERBAL_MEDICINE),
    FAB_BENCH("Fabrication bench", listOf(c(ItemType.STEEL, 140), c(ItemType.COMPONENT, 6)), 1100, 140f, "Production", blocksMove = true, workbench = true, power = -420f, research = Research.FABRICATION),
    RESEARCH_BENCH("Research bench", listOf(c(ItemType.STEEL, 40), c(ItemType.WOOD, 60)), 600, 100f, "Production", blocksMove = true, workbench = true),
    HI_TECH_BENCH("Hi-tech research bench", listOf(c(ItemType.STEEL, 100), c(ItemType.COMPONENT, 6)), 1000, 120f, "Production", blocksMove = true, workbench = true, power = -100f, research = Research.MULTIANALYZER),
    HYDROPONICS("Hydroponics basin", listOf(c(ItemType.STEEL, 40), c(ItemType.COMPONENT, 1)), 300, 70f, "Production", power = -70f, research = Research.HYDROPONICS),

    // Power
    CONDUIT("Power conduit", listOf(c(ItemType.STEEL, 1)), 25, 20f, "Power", research = Research.ELECTRICITY, flam = 0f),
    WOOD_GENERATOR("Wood-fired generator", listOf(c(ItemType.STEEL, 60), c(ItemType.COMPONENT, 1)), 500, 100f, "Power", blocksMove = true, power = 1000f, fuelCap = 12f, heat = 3f, research = Research.ELECTRICITY),
    SOLAR_PANEL("Solar generator", listOf(c(ItemType.STEEL, 100), c(ItemType.COMPONENT, 3)), 600, 80f, "Power", blocksMove = true, power = 1700f, research = Research.SOLAR_POWER),
    WIND_TURBINE("Wind turbine", listOf(c(ItemType.STEEL, 80), c(ItemType.COMPONENT, 2)), 600, 100f, "Power", blocksMove = true, power = 1500f, research = Research.WIND_POWER),
    BATTERY("Battery", listOf(c(ItemType.STEEL, 40), c(ItemType.COMPONENT, 2)), 300, 70f, "Power", blocksMove = true, research = Research.BATTERIES),

    // Temperature
    HEATER("Heater", listOf(c(ItemType.STEEL, 30), c(ItemType.COMPONENT, 1)), 250, 50f, "Temperature", blocksMove = true, power = -200f, heat = 14f, research = Research.ELECTRICITY),
    COOLER("Cooler", listOf(c(ItemType.STEEL, 40), c(ItemType.COMPONENT, 2)), 300, 50f, "Temperature", blocksMove = true, power = -200f, heat = -14f, research = Research.ELECTRICITY),

    // Security
    TURRET("Gun turret", listOf(c(ItemType.STEEL, 80), c(ItemType.COMPONENT, 2)), 450, 200f, "Security", blocksMove = true, research = Research.GUN_TURRETS),
    MORTAR("Mortar", listOf(c(ItemType.STEEL, 100), c(ItemType.COMPONENT, 2)), 600, 150f, "Security", blocksMove = true, research = Research.MORTARS),
    TRAP_SPIKE("Spike trap", listOf(c(ItemType.STEEL, 20)), 150, 40f, "Security", trap = true, research = Research.SMITHING),
    TRAP_DEADFALL("Deadfall trap", listOf(c(ItemType.STONE, 30), c(ItemType.STEEL, 10)), 200, 60f, "Security", trap = true),

    // Misc
    GRAVE("Grave", listOf(c(ItemType.STONE, 20)), 300, 50f, "Misc", beauty = 0.3f),

    // Ship
    SHIP_COMPUTER("Ship computer core", listOf(c(ItemType.STEEL, 100), c(ItemType.PLASTEEL, 60), c(ItemType.COMPONENT, 8)), 2500, 300f, "Ship", blocksMove = true, research = Research.SHIP_COMPUTER, power = -100f),
    SHIP_ENGINE("Ship engine", listOf(c(ItemType.STEEL, 160), c(ItemType.PLASTEEL, 80), c(ItemType.COMPONENT, 6)), 3000, 300f, "Ship", blocksMove = true, research = Research.SHIP_ENGINE),
    SHIP_REACTOR("Ship reactor", listOf(c(ItemType.STEEL, 160), c(ItemType.PLASTEEL, 120), c(ItemType.GOLD, 40), c(ItemType.COMPONENT, 8)), 3500, 300f, "Ship", blocksMove = true, research = Research.SHIP_REACTOR),
    SHIP_CASKET("Cryptosleep casket", listOf(c(ItemType.STEEL, 100), c(ItemType.PLASTEEL, 30), c(ItemType.COMPONENT, 4)), 1800, 200f, "Ship", blocksMove = true, research = Research.CRYPTOSLEEP, power = -30f);

    val isPowered get() = power != 0f
    val producesPower get() = power > 0f
    val consumesPower get() = power < 0f
    val isShip get() = category == "Ship"
    val totalCost get() = cost.sumOf { (t, n) -> (t.value * n).toDouble() }.toFloat()
}
