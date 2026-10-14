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
    val desc: String = "", val w: Int = 1, val h: Int = 1,
    /** Materials this structure can be built from. The first one is the default and is the item in [cost]'s first entry. */
    val stuff: List<ItemType>? = null,
    /** Kept so saves from before a change still load; hidden from the build menu. */
    val legacy: Boolean = false,
) {
    // Structure
    WOOD_WALL("Wall", listOf(c(ItemType.WOOD, 5)), 160, 150f, "Structure", blocksMove = true, blocksSight = true, isWall = true, flam = 1f, beauty = -1f, stuff = listOf(ItemType.WOOD, ItemType.STONE, ItemType.STEEL, ItemType.HARDALLOY)),
    STONE_WALL("Stone wall", listOf(c(ItemType.STONE, 5)), 280, 300f, "Structure", blocksMove = true, blocksSight = true, isWall = true, flam = 0f, research = null, legacy = true),
    STEEL_WALL("Steel wall", listOf(c(ItemType.STEEL, 5)), 200, 400f, "Structure", blocksMove = true, blocksSight = true, isWall = true, flam = 0f, research = Research.FORGEWORK, legacy = true),
    STEEL_DOOR("Steel door", listOf(c(ItemType.STEEL, 25)), 260, 250f, "Structure", isDoor = true, flam = 0f, research = Research.FORGEWORK, legacy = true),
    SHUTTER_DOOR("Slide hatch", listOf(c(ItemType.STEEL, 40), c(ItemType.COMPONENT, 1)), 320, 220f, "Structure", isDoor = true, flam = 0f, power = -50f, research = Research.SHUTTER_DOORS),
    HARDALLOY_WALL("Hardalloy wall", listOf(c(ItemType.HARDALLOY, 5)), 320, 500f, "Structure", blocksMove = true, blocksSight = true, isWall = true, flam = 0f, research = Research.PART_ASSEMBLY, legacy = true),
    DOOR("Door", listOf(c(ItemType.WOOD, 15)), 220, 120f, "Structure", isDoor = true, flam = 1f, stuff = listOf(ItemType.WOOD, ItemType.STEEL)),
    SANDBAGS("Sandbags", listOf(c(ItemType.CLOTH, 5)), 80, 60f, "Security", cover = 0.55f, flam = 0.3f),
    WOOD_FLOOR("Wood floor", listOf(c(ItemType.WOOD, 3)), 70, 1f, "Floors", isFloor = true, beauty = 0.3f, flam = 1f),
    STONE_FLOOR("Stone tiles", listOf(c(ItemType.STONE, 2)), 100, 1f, "Floors", isFloor = true, beauty = 0.6f, flam = 0f, research = Research.ROCK_SPLITTING),
    STEEL_FLOOR("Steel tiles", listOf(c(ItemType.STEEL, 2)), 90, 1f, "Floors", isFloor = true, beauty = 0.3f, flam = 0f, research = Research.FORGEWORK),
    CARPET("Carpet", listOf(c(ItemType.CLOTH, 5)), 80, 1f, "Floors", isFloor = true, beauty = 1.1f, comfort = 0f, flam = 1f, research = Research.RUG_WEAVING),

    // Furniture
    BEDROLL("Bedroll", emptyList(), 30, 10f, "Furniture", sleeps = true, comfort = 0.0f, beauty = -0.5f),
    BED("Bed", listOf(c(ItemType.WOOD, 40)), 500, 100f, "Furniture", sleeps = true, comfort = 0.7f, beauty = 0.5f, flam = 1f, w = 1, h = 2),
    INFIRMARY_COT("Infirmary cot", listOf(c(ItemType.STEEL, 70), c(ItemType.COMPONENT, 2)), 700, 120f, "Furniture", sleeps = true, comfort = 0.75f, medical = true, research = Research.SURGERY, flam = 0.2f, beauty = 0.3f, w = 1, h = 2),
    TABLE("Table", listOf(c(ItemType.WOOD, 60)), 350, 100f, "Furniture", blocksMove = true, comfort = 0.0f, beauty = 0.4f, flam = 1f, w = 2, h = 2),
    ARMCHAIR("Armchair", listOf(c(ItemType.WOOD, 30), c(ItemType.CLOTH, 30)), 320, 70f, "Furniture", comfort = 0.8f, beauty = 0.7f, research = Research.JOINERY_DETAIL, flam = 1f),
    DRESSER("Dresser", listOf(c(ItemType.WOOD, 50)), 300, 80f, "Furniture", blocksMove = true, beauty = 0.9f, research = Research.JOINERY_DETAIL, flam = 1f, w = 2, h = 1),
    END_TABLE("End table", listOf(c(ItemType.WOOD, 20)), 150, 40f, "Furniture", beauty = 0.5f, research = Research.JOINERY_DETAIL, flam = 1f),
    STOOL("Stool", listOf(c(ItemType.WOOD, 8)), 120, 40f, "Furniture", comfort = 0.45f, research = Research.JOINERY_DETAIL, flam = 1f),
    CHAIR("Chair", listOf(c(ItemType.WOOD, 14)), 200, 60f, "Furniture", comfort = 0.7f, beauty = 0.3f, research = Research.JOINERY_DETAIL, flam = 1f),
    PLANT_POT("Plant pot", listOf(c(ItemType.WOOD, 14)), 150, 30f, "Furniture", beauty = 2.2f, research = Research.JOINERY_DETAIL, flam = 0.8f),
    SCULPTURE_SMALL("Small sculpture", listOf(c(ItemType.SCULPTURE_SMALL, 1)), 3000, 80f, "Furniture", beauty = 6f, buildWork = WorkType.ART, art = true, blocksMove = true, research = Research.ROCK_SPLITTING, flam = 0f),
    SCULPTURE_LARGE("Large sculpture", listOf(c(ItemType.SCULPTURE_LARGE, 1)), 7500, 160f, "Furniture", beauty = 20f, buildWork = WorkType.ART, art = true, blocksMove = true, research = Research.ROCK_SPLITTING, flam = 0f, w = 2, h = 2),
    PITCH_POST("Pitch post", listOf(c(ItemType.WOOD, 12)), 80, 20f, "Furniture", light = 8f, fuelCap = 24f, heat = 1.5f, flam = 1f, beauty = 0.3f),
    POLE_LANTERN("Pole lantern", listOf(c(ItemType.STEEL, 10), c(ItemType.COMPONENT, 1)), 120, 40f, "Furniture", light = 10f, power = -30f, research = Research.GALVANICS, beauty = 0.5f),
    QUOITS("Quoits pitch", listOf(c(ItemType.WOOD, 10)), 100, 20f, "Joy", joy = 0.35f, flam = 1f),
    TACTICS_BOARD("Tactics board", listOf(c(ItemType.WOOD, 40)), 300, 60f, "Joy", joy = 0.4f, blocksMove = true, research = Research.JOINERY_DETAIL, beauty = 0.4f, flam = 1f, w = 2, h = 1),
    PICTURE_BOX("Picture box", listOf(c(ItemType.STEEL, 80), c(ItemType.COMPONENT, 2)), 400, 60f, "Joy", joy = 0.55f, power = -200f, research = Research.GALVANICS, blocksMove = true, beauty = 0.5f, light = 3f, w = 3, h = 1),
    POCKET_BOARD("Pocket board", listOf(c(ItemType.WOOD, 120), c(ItemType.STEEL, 25)), 900, 90f, "Joy", joy = 0.55f, blocksMove = true, research = Research.JOINERY_DETAIL, beauty = 1f, flam = 0.8f, w = 3, h = 2),

    // Production
    WORKSHOP_MAT("Workshop mat", emptyList(), 30, 10f, "Production", workbench = true),
    CAMPFIRE("Campfire", listOf(c(ItemType.WOOD, 10)), 100, 40f, "Production", workbench = true, fuelCap = 24f, heat = 10f, light = 7f, flam = 1f),
    EMBER_OVEN("Ember oven", listOf(c(ItemType.STEEL, 40)), 450, 90f, "Production", blocksMove = true, workbench = true, fuelCap = 12f, heat = 3f, light = 3f, w = 3, h = 1),
    HOTPLATE("Hotplate", listOf(c(ItemType.STEEL, 80), c(ItemType.COMPONENT, 2)), 600, 90f, "Production", blocksMove = true, workbench = true, power = -350f, research = Research.ELECTRIC_COOKING, w = 3, h = 1),
    CARVING_SLAB("Carving slab", listOf(c(ItemType.WOOD, 40)), 300, 80f, "Production", blocksMove = true, workbench = true, flam = 0.8f, w = 3, h = 1),
    STITCH_FRAME("Stitch frame", listOf(c(ItemType.WOOD, 40), c(ItemType.STEEL, 20)), 450, 80f, "Production", blocksMove = true, workbench = true, research = Research.STITCHWORK, w = 3, h = 1),
    COAL_FORGE("Coal forge", listOf(c(ItemType.STEEL, 100), c(ItemType.STONE, 20)), 700, 120f, "Production", blocksMove = true, workbench = true, fuelCap = 12f, heat = 4f, research = Research.FORGEWORK, light = 3f, w = 3, h = 1),
    SHAPING_RIG("Shaping rig", listOf(c(ItemType.STEEL, 100), c(ItemType.COMPONENT, 2)), 800, 120f, "Production", blocksMove = true, workbench = true, power = -350f, research = Research.LATHE_WORK, w = 3, h = 1),
    STONECUTTER("Rock dresser", listOf(c(ItemType.STEEL, 25), c(ItemType.STONE_CHUNK, 1)), 400, 90f, "Production", blocksMove = true, workbench = true, research = Research.ROCK_SPLITTING, w = 3, h = 1),
    TINCTURE_RIG("Tincture rig", listOf(c(ItemType.STEEL, 70), c(ItemType.COMPONENT, 2)), 600, 90f, "Production", blocksMove = true, workbench = true, power = -120f, research = Research.HERBAL_REMEDIES, w = 3, h = 1),
    FAB_BENCH("Gearworks", listOf(c(ItemType.STEEL, 140), c(ItemType.COMPONENT, 6)), 1100, 140f, "Production", blocksMove = true, workbench = true, power = -420f, research = Research.PART_ASSEMBLY, w = 3, h = 1),
    STUDY_DESK("Study desk", listOf(c(ItemType.STEEL, 40), c(ItemType.WOOD, 60)), 600, 100f, "Production", blocksMove = true, workbench = true, w = 3, h = 1),
    PRECISION_BENCH("Precision lab", listOf(c(ItemType.STEEL, 100), c(ItemType.COMPONENT, 6)), 1000, 120f, "Production", blocksMove = true, workbench = true, power = -100f, research = Research.ANALYSIS_ARRAY, w = 3, h = 2),
    GROW_TRAY("Grow tray", listOf(c(ItemType.STEEL, 40), c(ItemType.COMPONENT, 1)), 300, 70f, "Production", power = -70f, research = Research.SOILLESS_GROWING),

    // Power
    CONDUIT("Live wire", listOf(c(ItemType.STEEL, 1)), 25, 20f, "Power", research = Research.GALVANICS, flam = 0f),
    WOOD_GENERATOR("Log-burning engine", listOf(c(ItemType.STEEL, 60), c(ItemType.COMPONENT, 1)), 500, 100f, "Power", blocksMove = true, power = 1000f, fuelCap = 12f, heat = 3f, research = Research.GALVANICS, w = 2, h = 2),
    SOLAR_PANEL("Sun array", listOf(c(ItemType.STEEL, 100), c(ItemType.COMPONENT, 3)), 600, 80f, "Power", blocksMove = true, power = 1700f, research = Research.SUNLIGHT_HARVESTING, w = 3, h = 3),
    BREEZE_MILL("Breeze mill", listOf(c(ItemType.STEEL, 80), c(ItemType.COMPONENT, 2)), 600, 100f, "Power", blocksMove = true, power = 1500f, research = Research.BREEZE_HARVESTING, w = 2, h = 2),
    BATTERY("Battery", listOf(c(ItemType.STEEL, 40), c(ItemType.COMPONENT, 2)), 300, 70f, "Power", blocksMove = true, research = Research.CELL_STORAGE, w = 1, h = 2),

    // Communications and trade
    SIGNAL_DESK("Signal desk", listOf(c(ItemType.STEEL, 70), c(ItemType.COMPONENT, 3)), 500, 90f, "Misc", blocksMove = true, power = -200f, research = Research.COMMS, w = 2, h = 1),
    SUMMONS_FLARE("Summons flare", listOf(c(ItemType.STEEL, 20), c(ItemType.COMPONENT, 1)), 200, 60f, "Misc", research = Research.COMMS),
    FROST_VAULT("Frost vault", listOf(c(ItemType.STEEL, 30), c(ItemType.STONE, 20)), 250, 80f, "Temperature", blocksMove = true, heat = -9f, research = Research.FROST_VENTING, flam = 0f),
    GLOW_PANEL("Glow panel", listOf(c(ItemType.STEEL, 40), c(ItemType.COMPONENT, 2)), 400, 70f, "Production", blocksMove = true, power = -400f, light = 13f, research = Research.GLOW_PANELS, w = 2, h = 2),
    QUENCH_VENT("Quench vent", listOf(c(ItemType.STEEL, 20), c(ItemType.COMPONENT, 1)), 200, 30f, "Security", research = Research.VENTILATION, flam = 0f),

    // Temperature
    // Heater heat 35.62 is Stillmere's choice (C), not a vanilla value: the smallest value to two decimals that reaches 21 C at 0 C outdoors
    // in a sealed 3 by 3 room (threshold 35.618). The cooler (-14) is unchanged. See docs/fidelity/heat-exchange.md, "Heater output".
    HEATER("Heater", listOf(c(ItemType.STEEL, 30), c(ItemType.COMPONENT, 1)), 250, 50f, "Temperature", blocksMove = true, power = -200f, heat = 35.62f, research = Research.GALVANICS),
    COOLER("Cooler", listOf(c(ItemType.STEEL, 40), c(ItemType.COMPONENT, 2)), 300, 50f, "Temperature", blocksMove = true, power = -200f, heat = -14f, research = Research.GALVANICS, w = 2, h = 1),

    // Security
    TURRET("Swivel nest", listOf(c(ItemType.STEEL, 80), c(ItemType.COMPONENT, 2)), 450, 200f, "Security", blocksMove = true, research = Research.SWIVEL_NESTS),
    LOBBING_TUBE("Lobbing tube", listOf(c(ItemType.STEEL, 100), c(ItemType.COMPONENT, 2)), 600, 150f, "Security", blocksMove = true, research = Research.INDIRECT_FIRE, w = 2, h = 2),
    TRAP_SPIKE("Pitfall stakes", listOf(c(ItemType.STEEL, 20)), 150, 40f, "Security", trap = true, research = Research.FORGEWORK),
    TRAP_DEADFALL("Log snare", listOf(c(ItemType.STONE, 30), c(ItemType.STEEL, 10)), 200, 60f, "Security", trap = true),

    // Misc
    GRAVE("Grave", listOf(c(ItemType.STONE, 20)), 300, 50f, "Misc", beauty = 0.3f, w = 1, h = 2),

    // Ship
    NAVIGATION_BRAIN("Navigation brain", listOf(c(ItemType.STEEL, 100), c(ItemType.HARDALLOY, 60), c(ItemType.COMPONENT, 8)), 2500, 300f, "Ship", blocksMove = true, research = Research.NAVIGATION_BRAIN, power = -100f, w = 2, h = 2),
    THRUST_DRIVE("Thrust drive", listOf(c(ItemType.STEEL, 160), c(ItemType.HARDALLOY, 80), c(ItemType.COMPONENT, 6)), 3000, 300f, "Ship", blocksMove = true, research = Research.THRUST_DRIVE, w = 3, h = 2),
    CORE_FURNACE("Core furnace", listOf(c(ItemType.STEEL, 160), c(ItemType.HARDALLOY, 120), c(ItemType.GOLD, 40), c(ItemType.COMPONENT, 8)), 3500, 300f, "Ship", blocksMove = true, research = Research.CORE_FURNACE, w = 2, h = 2),
    COLD_POD("Cold pod", listOf(c(ItemType.STEEL, 100), c(ItemType.HARDALLOY, 30), c(ItemType.COMPONENT, 4)), 1800, 200f, "Ship", blocksMove = true, research = Research.STASIS, power = -30f, w = 1, h = 2),
    // Production and utility, from the base game's production list.
    SUBSOIL_AUGER("Subsoil auger", listOf(c(ItemType.STEEL, 100), c(ItemType.COMPONENT, 2)), 600, 150f, "Production", blocksMove = true, power = -300f, research = Research.SUBSOIL_BORING),
    MASH_TAP("Mash tap", listOf(c(ItemType.STEEL, 125), c(ItemType.COMPONENT, 3)), 600, 120f, "Production", blocksMove = true, power = -200f, workbench = true, research = Research.VAT_CULTURING, skillRequirement = 5),
    SCULPTOR_TABLE("Chisel bench", listOf(c(ItemType.STONE, 40)), 400, 100f, "Production", blocksMove = true, workbench = true, research = Research.STONE_CARVING),
    MASH_WORKS("Mash works", listOf(c(ItemType.STEEL, 60), c(ItemType.WOOD, 40)), 450, 110f, "Production", blocksMove = true, workbench = true, research = Research.MASH_CRAFT),
    SOUR_CASK("Sour cask", listOf(c(ItemType.WOOD, 30)), 200, 60f, "Production", blocksMove = true, workbench = true, research = Research.MASH_CRAFT),
    PYRE_HOUSE("Pyre house", listOf(c(ItemType.STEEL, 80), c(ItemType.STONE, 60)), 800, 200f, "Production", blocksMove = true, power = -250f, research = Research.PYRE_RITES, w = 2, h = 2),
    SENTRY_POD("Sentry pod", listOf(c(ItemType.STEEL, 45), c(ItemType.COMPONENT, 1)), 300, 120f, "Security", blocksMove = true, research = Research.SWIVEL_NESTS),
    // Appended after the saved entries: saves store building definitions by ordinal, so new entries must go last.
    LOUNGE_SEAT("Lounge seat", listOf(c(ItemType.WOOD, 25), c(ItemType.CLOTH, 40)), 380, 80f, "Furniture", comfort = 0.85f, beauty = 0.8f, research = Research.JOINERY_DETAIL, flam = 1f, w = 2, h = 1),
    BOOKSHELF("Bookshelf", listOf(c(ItemType.WOOD, 40)), 260, 70f, "Furniture", blocksMove = true, beauty = 0.8f, research = Research.JOINERY_DETAIL, flam = 1f),
    WARDROBE("Wardrobe", listOf(c(ItemType.WOOD, 60)), 340, 90f, "Furniture", blocksMove = true, beauty = 0.5f, research = Research.JOINERY_DETAIL, flam = 1f, w = 2, h = 1),
    COFFEE_TABLE("Coffee table", listOf(c(ItemType.WOOD, 15)), 120, 35f, "Furniture", beauty = 0.3f, research = Research.JOINERY_DETAIL, flam = 1f);

    val isPowered get() = power != 0f
    val producesPower get() = power > 0f
    val consumesPower get() = power < 0f
    val isShip get() = category == "Ship"
    val totalCost get() = cost.sumOf { (t, n) -> (t.value * n).toDouble() }.toFloat()
}

/**
 * What a building material changes compared with plain wood: hit points, flammability and beauty. Hit points are a
 * multiplier on the structure's own base HP, which is tuned for wood. Flammability is absolute; beauty is a difference.
 */
class Material(val item: ItemType, val hpMult: Float, val flam: Float, val beauty: Float, val research: Research?)

object Materials {
    private val table = listOf(
        Material(ItemType.WOOD, 1f, 1f, 0f, null),
        Material(ItemType.STONE, 2f, 0f, 1f, null),
        Material(ItemType.STEEL, 8f / 3f, 0f, 1f, Research.FORGEWORK),
        Material(ItemType.HARDALLOY, 3.5f, 0f, 1.5f, Research.PART_ASSEMBLY),
    ).associateBy { it.item }

    fun of(t: ItemType?): Material? = t?.let { table[it] }
}
