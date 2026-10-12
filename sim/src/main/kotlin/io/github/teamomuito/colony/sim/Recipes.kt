package io.github.teamomuito.colony.sim

class Ing(val types: List<ItemType>, val count: Int, val label: String) {
    fun accepts(t: ItemType) = t in types
}

private fun ing(t: ItemType, n: Int) = Ing(listOf(t), n, t.label)
private fun anyOf(label: String, n: Int, vararg t: ItemType) = Ing(t.toList(), n, label)

private val RAW_VEG = arrayOf(ItemType.RICE, ItemType.POTATOES, ItemType.CORN, ItemType.STRAWBERRIES, ItemType.BERRIES)
private val RAW_MEAT = arrayOf(ItemType.MEAT, ItemType.CHITIN_CUTS, ItemType.MANFLESH, ItemType.EGGS)

enum class Recipe(
    val label: String, val benches: List<BuildDef>, val inputs: List<Ing>, val out: ItemType, val outCount: Int,
    val work: Int, val workType: WorkType, val minSkill: Int = 0, val research: Research? = null,
) {
    COOK_HEARTH_STEW("Cook hearth stew", listOf(BuildDef.CAMPFIRE, BuildDef.EMBER_OVEN, BuildDef.HOTPLATE),
        listOf(anyOf("Raw food", 10, *RAW_VEG, *RAW_MEAT)), ItemType.MEAL_HEARTH_STEW, 1, 300, WorkType.COOK),
    COOK_ROASTED_PLATTER("Cook roasted platter", listOf(BuildDef.EMBER_OVEN, BuildDef.HOTPLATE),
        listOf(anyOf("Meat", 5, *RAW_MEAT), anyOf("Vegetables", 5, *RAW_VEG)), ItemType.MEAL_ROASTED_PLATTER, 1, 450, WorkType.COOK, 6),
    COOK_TRAIL_CAKE("Make trail cake", listOf(BuildDef.EMBER_OVEN, BuildDef.HOTPLATE, BuildDef.CAMPFIRE),
        listOf(anyOf("Meat", 12, *RAW_MEAT), anyOf("Vegetables", 12, *RAW_VEG)), ItemType.TRAIL_CAKE, 16, 700, WorkType.COOK, 3, Research.TRAIL_CAKE),
    COOK_FEED_PELLETS("Make feed pellets", listOf(BuildDef.EMBER_OVEN, BuildDef.HOTPLATE),
        listOf(anyOf("Meat", 5, *RAW_MEAT), anyOf("Vegetables", 6, *RAW_VEG)), ItemType.FEED_PELLETS, 40, 450, WorkType.COOK, 2),

    CUT_BLOCKS("Dress cut stone", listOf(BuildDef.STONECUTTER), listOf(ing(ItemType.STONE_CHUNK, 1)), ItemType.STONE, 20, 400, WorkType.CRAFT),

    BREW_BEER("Brew beer", listOf(BuildDef.TINCTURE_RIG, BuildDef.MASH_WORKS, BuildDef.SOUR_CASK), listOf(anyOf("Crops", 20, ItemType.CORN, ItemType.RICE, ItemType.POTATOES)), ItemType.BEER, 5, 900, WorkType.CRAFT, 0, Research.MASH_CRAFT),
    ROLL_JOINT("Make rolled bundle", listOf(BuildDef.TINCTURE_RIG), listOf(ing(ItemType.HUSHMOSS, 3)), ItemType.JOINT, 1, 250, WorkType.CRAFT, 0, Research.TINCTURE_CHEMISTRY),
    BREW_DRAUGHT("Brew lucid draught", listOf(BuildDef.TINCTURE_RIG), listOf(ing(ItemType.DREAMWEED, 1)), ItemType.LUCID_DRAUGHT, 1, 300, WorkType.CRAFT, 0, Research.TINCTURE_CHEMISTRY),
    MAKE_LEAF_POULTICE("Make leaf poultice", listOf(BuildDef.TINCTURE_RIG, BuildDef.WORKSHOP_MAT), listOf(ing(ItemType.MIREWORT, 3)), ItemType.MEDS_LEAF_POULTICE, 1, 600, WorkType.CRAFT, 0, Research.HERBAL_REMEDIES),
    MAKE_MEDS("Compound a dose", listOf(BuildDef.TINCTURE_RIG, BuildDef.FAB_BENCH), listOf(ing(ItemType.MEDS_LEAF_POULTICE, 2), ing(ItemType.COMPONENT, 1)), ItemType.MEDS_STERILE_DRESSING, 1, 800, WorkType.CRAFT, 5, Research.PHARMACOLOGY),

    COMPONENTS("Make components", listOf(BuildDef.SHAPING_RIG, BuildDef.FAB_BENCH), listOf(ing(ItemType.STEEL, 20)), ItemType.COMPONENT, 1, 1100, WorkType.SMITH, 6, Research.LATHE_WORK),

    COOK_BANQUET_SPREAD("Cook banquet spread", listOf(BuildDef.EMBER_OVEN, BuildDef.HOTPLATE),
        listOf(anyOf("Meat", 5, *RAW_MEAT), anyOf("Vegetables", 5, *RAW_VEG), anyOf("Milk or eggs", 2, ItemType.MILK, ItemType.EGGS)), ItemType.MEAL_BANQUET_SPREAD, 1, 700, WorkType.COOK, 8, Research.BANQUET_COOKERY),
    MAKE_PALE_DUST("Make pale dust", listOf(BuildDef.TINCTURE_RIG), listOf(ing(ItemType.DREAMWEED, 4)), ItemType.PALE_DUST, 1, 400, WorkType.CRAFT, 3, Research.STIMULANT_BLENDS),
    MAKE_WHITE_RUSH("Make white rush", listOf(BuildDef.TINCTURE_RIG), listOf(ing(ItemType.DREAMWEED, 6)), ItemType.WHITE_RUSH, 1, 500, WorkType.CRAFT, 4, Research.STIMULANT_BLENDS),
    MAKE_DAWN_TABLET("Make dawn tablet", listOf(BuildDef.TINCTURE_RIG), listOf(ing(ItemType.DREAMWEED, 2), ing(ItemType.COMPONENT, 1)), ItemType.DAWN_TABLET, 1, 450, WorkType.CRAFT, 3, Research.STIMULANT_BLENDS),
    MAKE_SURGE_TONIC("Make surge tonic", listOf(BuildDef.TINCTURE_RIG), listOf(ing(ItemType.DREAMWEED, 5), ing(ItemType.MEDS_LEAF_POULTICE, 1)), ItemType.SURGE_TONIC, 1, 700, WorkType.CRAFT, 6, Research.SURGE_TONIC),

    // Tailor
    MAKE_JACKET("Make canvas smock", listOf(BuildDef.STITCH_FRAME), listOf(anyOf("Cloth or leather", 60, ItemType.CLOTH, ItemType.LEATHER, ItemType.WISPSILK)), ItemType.A_JACKET, 1, 1100, WorkType.TAILOR, 3, Research.LAYERED_APPAREL),
    MAKE_PLUMED_CROWN("Make plumed crown", listOf(BuildDef.STITCH_FRAME), listOf(ing(ItemType.LEATHER, 30)), ItemType.A_PLUMED_CROWN, 1, 700, WorkType.TAILOR, 4, Research.LAYERED_APPAREL),
    MAKE_TSHIRT("Make cotton tee", listOf(BuildDef.STITCH_FRAME), listOf(ing(ItemType.CLOTH, 40)), ItemType.A_TSHIRT, 1, 500, WorkType.TAILOR),
    MAKE_COLLAR_TUNIC("Make collar tunic", listOf(BuildDef.STITCH_FRAME), listOf(ing(ItemType.CLOTH, 60)), ItemType.A_COLLAR_TUNIC, 1, 700, WorkType.TAILOR, 3),
    MAKE_PANTS("Make hose", listOf(BuildDef.STITCH_FRAME), listOf(ing(ItemType.CLOTH, 40)), ItemType.A_PANTS, 1, 500, WorkType.TAILOR),
    MAKE_TRIBAL("Make woven wrap", listOf(BuildDef.STITCH_FRAME), listOf(ing(ItemType.LEATHER, 50)), ItemType.A_TRIBAL, 1, 600, WorkType.TAILOR),
    MAKE_LONG_COAT("Make long coat", listOf(BuildDef.STITCH_FRAME), listOf(ing(ItemType.LEATHER, 80)), ItemType.A_LONG_COAT, 1, 1000, WorkType.TAILOR, 4),
    MAKE_PADDED_COAT("Make padded coat", listOf(BuildDef.STITCH_FRAME), listOf(anyOf("Cloth or wool", 80, ItemType.CLOTH, ItemType.WOOL)), ItemType.A_PADDED_COAT, 1, 1300, WorkType.TAILOR, 5),
    MAKE_WIDE_BRIM_HAT("Make sunbrim", listOf(BuildDef.STITCH_FRAME), listOf(ing(ItemType.CLOTH, 20)), ItemType.A_HAT, 1, 350, WorkType.TAILOR),
    MAKE_KNIT_CAP("Make knit cap", listOf(BuildDef.STITCH_FRAME), listOf(anyOf("Cloth or wool", 20, ItemType.CLOTH, ItemType.WOOL)), ItemType.A_BEANIE, 1, 300, WorkType.TAILOR),
    MAKE_RIBBED_VEST("Make quilted tabard", listOf(BuildDef.STITCH_FRAME), listOf(ing(ItemType.CLOTH, 40), ing(ItemType.STEEL, 20)), ItemType.A_RIBBED_VEST, 1, 1600, WorkType.TAILOR, 4, Research.RIBBED_ARMOR),
    MAKE_RIBBED_LEGGINGS("Make ribbed leggings", listOf(BuildDef.STITCH_FRAME), listOf(ing(ItemType.CLOTH, 40), ing(ItemType.STEEL, 14)), ItemType.A_RIBBED_LEGGINGS, 1, 1400, WorkType.TAILOR, 4, Research.RIBBED_ARMOR),
    MAKE_RIBBED_JACKET("Make ribbed doublet", listOf(BuildDef.STITCH_FRAME), listOf(ing(ItemType.CLOTH, 60), ing(ItemType.STEEL, 30)), ItemType.A_RIBBED_JACKET, 1, 2200, WorkType.TAILOR, 5, Research.RIBBED_ARMOR),

    // Smith
    MAKE_POCKET_BLADE("Make pocket blade", listOf(BuildDef.COAL_FORGE), listOf(ing(ItemType.STEEL, 20)), ItemType.W_POCKET_BLADE, 1, 800, WorkType.SMITH),
    MAKE_CLUB("Make club", listOf(BuildDef.COAL_FORGE, BuildDef.WORKSHOP_MAT), listOf(ing(ItemType.WOOD, 20)), ItemType.W_CLUB, 1, 450, WorkType.CRAFT),
    MAKE_SPEAR("Make spear", listOf(BuildDef.COAL_FORGE, BuildDef.WORKSHOP_MAT), listOf(ing(ItemType.WOOD, 30), ing(ItemType.STEEL, 8)), ItemType.W_SPEAR, 1, 700, WorkType.SMITH, 0, Research.BASIC_MELEE),
    MAKE_MACE("Make mace", listOf(BuildDef.COAL_FORGE), listOf(ing(ItemType.STEEL, 40)), ItemType.W_MACE, 1, 1000, WorkType.SMITH, 3, Research.BASIC_MELEE),
    MAKE_BROAD_BLADE("Make broad blade", listOf(BuildDef.COAL_FORGE), listOf(ing(ItemType.STEEL, 70)), ItemType.W_BROAD_BLADE, 1, 1800, WorkType.SMITH, 6, Research.BASIC_MELEE),
    MAKE_BOW("Make reedstring", listOf(BuildDef.WORKSHOP_MAT, BuildDef.COAL_FORGE), listOf(ing(ItemType.WOOD, 40)), ItemType.W_BOW, 1, 800, WorkType.CRAFT, 0, Research.STAVE_AND_STRING),
    MAKE_LONGSTAVE("Make longstave", listOf(BuildDef.COAL_FORGE), listOf(ing(ItemType.WOOD, 80), ing(ItemType.STEEL, 10)), ItemType.W_LONGSTAVE, 1, 1300, WorkType.SMITH, 4, Research.STAVE_AND_STRING),
    MAKE_RIVETED_HOOD("Make riveted hood", listOf(BuildDef.COAL_FORGE), listOf(ing(ItemType.STEEL, 30)), ItemType.A_RIVETED_HOOD, 1, 1300, WorkType.SMITH, 4, Research.RIBBED_ARMOR),
    MAKE_PLATE("Make brigandine", listOf(BuildDef.COAL_FORGE), listOf(ing(ItemType.STEEL, 160)), ItemType.A_ARMOR, 1, 4000, WorkType.SMITH, 8, Research.BRIGANDINE_WORK),

    MAKE_STABSWORD("Make stabsword", listOf(BuildDef.COAL_FORGE), listOf(ing(ItemType.STEEL, 50)), ItemType.W_STABSWORD, 1, 1300, WorkType.SMITH, 5, Research.BASIC_MELEE),
    MAKE_HORNWOUND("Make hornwound", listOf(BuildDef.COAL_FORGE, BuildDef.WORKSHOP_MAT), listOf(ing(ItemType.WOOD, 60), ing(ItemType.STEEL, 10)), ItemType.W_RECURVE, 1, 1100, WorkType.CRAFT, 4, Research.HORN_BACKING),
    MAKE_HARDALLOY_SWORD("Make hardalloy cutter", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.HARDALLOY, 40), ing(ItemType.STEEL, 20)), ItemType.W_HARDALLOY_SWORD, 1, 3000, WorkType.SMITH, 9, Research.HARDALLOY_BLADES),
    MAKE_LEVER("Make swing repeater", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 55), ing(ItemType.COMPONENT, 2)), ItemType.W_LEVER, 1, 2400, WorkType.SMITH, 5, Research.RIFLED_BARRELS),
    MAKE_SCATTER_CANNON("Make scatter cannon", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 70), ing(ItemType.COMPONENT, 3)), ItemType.W_SCATTER_CANNON, 1, 3000, WorkType.SMITH, 7, Research.RIFLED_BARRELS),
    MAKE_MAG_CARBINE("Make mag carbine", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 65), ing(ItemType.COMPONENT, 4)), ItemType.W_MAG_CARBINE, 1, 3000, WorkType.SMITH, 7, Research.RIFLED_BARRELS),
    MAKE_SPINNER_RACK("Make spinner rack", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.STEEL, 150), ing(ItemType.COMPONENT, 10)), ItemType.W_SPINNER_RACK, 1, 5000, WorkType.SMITH, 10, Research.SIEGE_ASSEMBLY),
    MAKE_PULSE_CARBINE("Make pulse carbine", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.STEEL, 80), ing(ItemType.HARDALLOY, 20), ing(ItemType.COMPONENT, 8)), ItemType.W_PULSE_CARBINE, 1, 4500, WorkType.SMITH, 10, Research.PULSE_ARMS),
    MAKE_SHRAPNEL_CHARGE("Make shrapnel charges", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 25), ing(ItemType.COMPONENT, 1)), ItemType.W_SHRAPNEL_CHARGE, 1, 1400, WorkType.SMITH, 4, Research.BLAST_CHEMISTRY),
    MAKE_EMBER_THROWER("Make ember thrower", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 60), ing(ItemType.COMPONENT, 3)), ItemType.W_EMBER_THROWER, 1, 2600, WorkType.SMITH, 6, Research.BLAST_CHEMISTRY),
    MAKE_SKYFALL_BATTERY("Make skyfall battery", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.STEEL, 120), ing(ItemType.HARDALLOY, 40), ing(ItemType.COMPONENT, 12)), ItemType.W_SKYFALL_BATTERY, 1, 6000, WorkType.SMITH, 11, Research.PROPELLANT_LIFT),
    MAKE_TRENCH_ARMOR("Make trench mail", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.HARDALLOY, 50), ing(ItemType.STEEL, 40), ing(ItemType.COMPONENT, 4)), ItemType.A_TRENCH_ARMOR, 1, 5000, WorkType.SMITH, 9, Research.TRENCH_ARMOR),
    MAKE_TRENCH_HELMET("Make trench visor", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.HARDALLOY, 25), ing(ItemType.COMPONENT, 2)), ItemType.A_TRENCH_VISOR, 1, 2400, WorkType.SMITH, 8, Research.TRENCH_ARMOR),
    MAKE_GHOSTWEAVE_HELMET("Make ghostweave cowl", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.HARDALLOY, 25), ing(ItemType.COMPONENT, 2)), ItemType.A_GHOSTWEAVE_HELM, 1, 2200, WorkType.SMITH, 8, Research.ADV_ARMOR),
    MAKE_EXOSUIT("Make exosuit", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.HARDALLOY, 120), ing(ItemType.GOLD, 10), ing(ItemType.COMPONENT, 12)), ItemType.A_EXOSUIT, 1, 8000, WorkType.SMITH, 11, Research.EXOSUIT),
    MAKE_EXO_HELMET("Make exo casque", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.HARDALLOY, 50), ing(ItemType.COMPONENT, 5)), ItemType.A_EXO_HELM, 1, 4000, WorkType.SMITH, 10, Research.EXOSUIT),

    // Lathe work
    MAKE_CYLINDER_GUN("Make cylinder gun", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 30), ing(ItemType.COMPONENT, 1)), ItemType.W_CYLINDER_GUN, 1, 1800, WorkType.SMITH, 4, Research.POWDER_ARMS),
    MAKE_SNAP_PISTOL("Make quickshot", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 35), ing(ItemType.COMPONENT, 1)), ItemType.W_SNAP_PISTOL, 1, 1800, WorkType.SMITH, 4, Research.POWDER_ARMS),
    MAKE_BOLT("Make latch carbine", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 60), ing(ItemType.COMPONENT, 2)), ItemType.W_BOLT, 1, 2400, WorkType.SMITH, 5, Research.POWDER_ARMS),
    MAKE_SHOTGUN("Make riot gun", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 50), ing(ItemType.COMPONENT, 2)), ItemType.W_SHOTGUN, 1, 2400, WorkType.SMITH, 5, Research.POWDER_ARMS),
    MAKE_SMG("Make rapid sidearm", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 40), ing(ItemType.COMPONENT, 3)), ItemType.W_SMG, 1, 2400, WorkType.SMITH, 6, Research.POWDER_ARMS),
    MAKE_RIFLE("Make stutter gun", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 80), ing(ItemType.COMPONENT, 6)), ItemType.W_RIFLE, 1, 3200, WorkType.SMITH, 8, Research.POWDER_ARMS),
    MAKE_LMG("Make bipod hammer", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 100), ing(ItemType.COMPONENT, 8)), ItemType.W_LMG, 1, 3800, WorkType.SMITH, 9, Research.POWDER_ARMS),
    MAKE_SNIPER("Make distance piece", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 100), ing(ItemType.COMPONENT, 8)), ItemType.W_SNIPER, 1, 3800, WorkType.SMITH, 9, Research.POWDER_ARMS),
    MAKE_SHELLS("Make lobbed bombs", listOf(BuildDef.SHAPING_RIG), listOf(ing(ItemType.STEEL, 15)), ItemType.SHELL, 5, 800, WorkType.SMITH, 3, Research.INDIRECT_FIRE),
    MAKE_VAT_MASH("Make vat mash", listOf(BuildDef.MASH_TAP), listOf(anyOf("Food", 10, *RAW_VEG, *RAW_MEAT)), ItemType.MEAL_VAT_MASH, 10, 400, WorkType.COOK, 0, Research.VAT_CULTURING),
    CARVE_SMALL("Carve small sculpture", listOf(BuildDef.SCULPTOR_TABLE), listOf(ing(ItemType.STONE, 50)), ItemType.SCULPTURE_SMALL, 1, 1200, WorkType.ART, 0, Research.STONE_CARVING),
    CARVE_LARGE("Carve large sculpture", listOf(BuildDef.SCULPTOR_TABLE), listOf(ing(ItemType.STONE, 140)), ItemType.SCULPTURE_LARGE, 1, 3600, WorkType.ART, 4, Research.STONE_CARVING),
    MAKE_GHOSTWEAVE_ARMOR("Make ghostweave shell", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.HARDALLOY, 60), ing(ItemType.COMPONENT, 4)), ItemType.A_GHOSTWEAVE_ARMOR, 1, 4500, WorkType.SMITH, 8, Research.ADV_ARMOR);

    fun available(done: Set<Research>) = research == null || research in done
}

enum class BillMode(val label: String) { DO_X("Do X times"), UNTIL_HAVE("Until you have X"), FOREVER("Forever") }

class Bill(val recipe: Recipe) {
    var mode = BillMode.DO_X
    var target = 1
    var done = 0
    var paused = false
    var minSkill = 0
    var allowedItems: MutableSet<ItemType>? = null // null = every ingredient the recipe accepts
}
