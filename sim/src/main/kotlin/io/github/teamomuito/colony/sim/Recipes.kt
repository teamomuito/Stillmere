package io.github.teamomuito.colony.sim

class Ing(val types: List<ItemType>, val count: Int, val label: String) {
    fun accepts(t: ItemType) = t in types
}

private fun ing(t: ItemType, n: Int) = Ing(listOf(t), n, t.label)
private fun anyOf(label: String, n: Int, vararg t: ItemType) = Ing(t.toList(), n, label)

private val RAW_VEG = arrayOf(ItemType.RICE, ItemType.POTATOES, ItemType.CORN, ItemType.STRAWBERRIES, ItemType.BERRIES)
private val RAW_MEAT = arrayOf(ItemType.MEAT, ItemType.INSECT_MEAT, ItemType.HUMAN_MEAT, ItemType.EGGS)

enum class Recipe(
    val label: String, val benches: List<BuildDef>, val inputs: List<Ing>, val out: ItemType, val outCount: Int,
    val work: Int, val workType: WorkType, val minSkill: Int = 0, val research: Research? = null,
) {
    COOK_SIMPLE("Cook simple meal", listOf(BuildDef.CAMPFIRE, BuildDef.STOVE_FUEL, BuildDef.STOVE_ELEC),
        listOf(anyOf("Raw food", 10, *RAW_VEG, *RAW_MEAT)), ItemType.MEAL_SIMPLE, 1, 300, WorkType.COOK),
    COOK_FINE("Cook fine meal", listOf(BuildDef.STOVE_FUEL, BuildDef.STOVE_ELEC),
        listOf(anyOf("Meat", 5, *RAW_MEAT), anyOf("Vegetables", 5, *RAW_VEG)), ItemType.MEAL_FINE, 1, 450, WorkType.COOK, 6),
    COOK_PEMMICAN("Make pemmican", listOf(BuildDef.STOVE_FUEL, BuildDef.STOVE_ELEC, BuildDef.CAMPFIRE),
        listOf(anyOf("Meat", 12, *RAW_MEAT), anyOf("Vegetables", 12, *RAW_VEG)), ItemType.PEMMICAN, 16, 700, WorkType.COOK, 3, Research.PEMMICAN),
    COOK_KIBBLE("Make kibble", listOf(BuildDef.STOVE_FUEL, BuildDef.STOVE_ELEC),
        listOf(anyOf("Meat", 5, *RAW_MEAT), anyOf("Vegetables", 6, *RAW_VEG)), ItemType.KIBBLE, 40, 450, WorkType.COOK, 2),

    CUT_BLOCKS("Cut stone blocks", listOf(BuildDef.STONECUTTER), listOf(ing(ItemType.STONE_CHUNK, 1)), ItemType.STONE, 20, 400, WorkType.CRAFT),

    BREW_BEER("Brew beer", listOf(BuildDef.DRUG_LAB, BuildDef.BREWERY, BuildDef.FERMENTING_BARREL), listOf(anyOf("Crops", 20, ItemType.CORN, ItemType.RICE, ItemType.POTATOES)), ItemType.BEER, 5, 900, WorkType.CRAFT, 0, Research.BREWING),
    ROLL_JOINT("Roll smokeleaf joint", listOf(BuildDef.DRUG_LAB), listOf(ing(ItemType.SMOKELEAF, 3)), ItemType.JOINT, 1, 250, WorkType.CRAFT, 0, Research.DRUGS),
    BREW_TEA("Brew psychite tea", listOf(BuildDef.DRUG_LAB), listOf(ing(ItemType.PSYCHOID, 1)), ItemType.PSYCHITE_TEA, 1, 300, WorkType.CRAFT, 0, Research.DRUGS),
    HERBAL_MEDS("Make herbal medicine", listOf(BuildDef.DRUG_LAB, BuildDef.CRAFTING_SPOT), listOf(ing(ItemType.HEALROOT, 3)), ItemType.MEDS_HERBAL, 1, 600, WorkType.CRAFT, 0, Research.HERBAL_MEDICINE),
    MAKE_MEDS("Make industrial medicine", listOf(BuildDef.DRUG_LAB, BuildDef.FAB_BENCH), listOf(ing(ItemType.MEDS_HERBAL, 2), ing(ItemType.COMPONENT, 1)), ItemType.MEDS_INDUSTRIAL, 1, 800, WorkType.CRAFT, 5, Research.MEDICINE),

    COMPONENTS("Make components", listOf(BuildDef.MACHINING, BuildDef.FAB_BENCH), listOf(ing(ItemType.STEEL, 20)), ItemType.COMPONENT, 1, 1100, WorkType.SMITH, 6, Research.MACHINING),

    COOK_LAVISH("Cook lavish meal", listOf(BuildDef.STOVE_FUEL, BuildDef.STOVE_ELEC),
        listOf(anyOf("Meat", 5, *RAW_MEAT), anyOf("Vegetables", 5, *RAW_VEG), anyOf("Milk or eggs", 2, ItemType.MILK, ItemType.EGGS)), ItemType.MEAL_LAVISH, 1, 700, WorkType.COOK, 8, Research.LAVISH_COOKING),
    MAKE_FLAKE("Make flake", listOf(BuildDef.DRUG_LAB), listOf(ing(ItemType.PSYCHOID, 4)), ItemType.FLAKE, 1, 400, WorkType.CRAFT, 3, Research.FLAKE_YAYO),
    MAKE_YAYO("Make yayo", listOf(BuildDef.DRUG_LAB), listOf(ing(ItemType.PSYCHOID, 6)), ItemType.YAYO, 1, 500, WorkType.CRAFT, 4, Research.FLAKE_YAYO),
    MAKE_WAKEUP("Make wake-up", listOf(BuildDef.DRUG_LAB), listOf(ing(ItemType.PSYCHOID, 2), ing(ItemType.COMPONENT, 1)), ItemType.WAKE_UP, 1, 450, WorkType.CRAFT, 3, Research.FLAKE_YAYO),
    MAKE_GOJUICE("Make go-juice", listOf(BuildDef.DRUG_LAB), listOf(ing(ItemType.PSYCHOID, 5), ing(ItemType.MEDS_HERBAL, 1)), ItemType.GO_JUICE, 1, 700, WorkType.CRAFT, 6, Research.GO_JUICE),

    // Tailor
    MAKE_JACKET("Make jacket", listOf(BuildDef.TAILOR_BENCH), listOf(anyOf("Cloth or leather", 60, ItemType.CLOTH, ItemType.LEATHER, ItemType.DEVILSTRAND)), ItemType.A_JACKET, 1, 1100, WorkType.TAILOR, 3, Research.COMPLEX_CLOTHING),
    MAKE_HEADDRESS("Make tribal headdress", listOf(BuildDef.TAILOR_BENCH), listOf(ing(ItemType.LEATHER, 30)), ItemType.A_HEADDRESS, 1, 700, WorkType.TAILOR, 4, Research.COMPLEX_CLOTHING),
    MAKE_TSHIRT("Make T-shirt", listOf(BuildDef.TAILOR_BENCH), listOf(ing(ItemType.CLOTH, 40)), ItemType.A_TSHIRT, 1, 500, WorkType.TAILOR),
    MAKE_BUTTONDOWN("Make button-down shirt", listOf(BuildDef.TAILOR_BENCH), listOf(ing(ItemType.CLOTH, 60)), ItemType.A_BUTTONDOWN, 1, 700, WorkType.TAILOR, 3),
    MAKE_PANTS("Make pants", listOf(BuildDef.TAILOR_BENCH), listOf(ing(ItemType.CLOTH, 40)), ItemType.A_PANTS, 1, 500, WorkType.TAILOR),
    MAKE_TRIBAL("Make tribalwear", listOf(BuildDef.TAILOR_BENCH), listOf(ing(ItemType.LEATHER, 50)), ItemType.A_TRIBAL, 1, 600, WorkType.TAILOR),
    MAKE_DUSTER("Make duster", listOf(BuildDef.TAILOR_BENCH), listOf(ing(ItemType.LEATHER, 80)), ItemType.A_DUSTER, 1, 1000, WorkType.TAILOR, 4),
    MAKE_PARKA("Make parka", listOf(BuildDef.TAILOR_BENCH), listOf(anyOf("Cloth or wool", 80, ItemType.CLOTH, ItemType.WOOL)), ItemType.A_PARKA, 1, 1300, WorkType.TAILOR, 5),
    MAKE_HAT("Make cowboy hat", listOf(BuildDef.TAILOR_BENCH), listOf(ing(ItemType.CLOTH, 20)), ItemType.A_HAT, 1, 350, WorkType.TAILOR),
    MAKE_TUQUE("Make tuque", listOf(BuildDef.TAILOR_BENCH), listOf(anyOf("Cloth or wool", 20, ItemType.CLOTH, ItemType.WOOL)), ItemType.A_BEANIE, 1, 300, WorkType.TAILOR),
    MAKE_FLAK_VEST("Make flak vest", listOf(BuildDef.TAILOR_BENCH), listOf(ing(ItemType.CLOTH, 40), ing(ItemType.STEEL, 20)), ItemType.A_FLAK_VEST, 1, 1600, WorkType.TAILOR, 4, Research.FLAK_ARMOR),
    MAKE_FLAK_PANTS("Make flak pants", listOf(BuildDef.TAILOR_BENCH), listOf(ing(ItemType.CLOTH, 40), ing(ItemType.STEEL, 14)), ItemType.A_FLAK_PANTS, 1, 1400, WorkType.TAILOR, 4, Research.FLAK_ARMOR),
    MAKE_FLAK_JACKET("Make flak jacket", listOf(BuildDef.TAILOR_BENCH), listOf(ing(ItemType.CLOTH, 60), ing(ItemType.STEEL, 30)), ItemType.A_FLAK_JACKET, 1, 2200, WorkType.TAILOR, 5, Research.FLAK_ARMOR),

    // Smith
    MAKE_KNIFE("Make knife", listOf(BuildDef.SMITHY), listOf(ing(ItemType.STEEL, 20)), ItemType.W_KNIFE, 1, 800, WorkType.SMITH),
    MAKE_CLUB("Make club", listOf(BuildDef.SMITHY, BuildDef.CRAFTING_SPOT), listOf(ing(ItemType.WOOD, 20)), ItemType.W_CLUB, 1, 450, WorkType.CRAFT),
    MAKE_SPEAR("Make spear", listOf(BuildDef.SMITHY, BuildDef.CRAFTING_SPOT), listOf(ing(ItemType.WOOD, 30), ing(ItemType.STEEL, 8)), ItemType.W_SPEAR, 1, 700, WorkType.SMITH, 0, Research.BASIC_MELEE),
    MAKE_MACE("Make mace", listOf(BuildDef.SMITHY), listOf(ing(ItemType.STEEL, 40)), ItemType.W_MACE, 1, 1000, WorkType.SMITH, 3, Research.BASIC_MELEE),
    MAKE_LONGSWORD("Make longsword", listOf(BuildDef.SMITHY), listOf(ing(ItemType.STEEL, 70)), ItemType.W_LONGSWORD, 1, 1800, WorkType.SMITH, 6, Research.BASIC_MELEE),
    MAKE_BOW("Make short bow", listOf(BuildDef.CRAFTING_SPOT, BuildDef.SMITHY), listOf(ing(ItemType.WOOD, 40)), ItemType.W_BOW, 1, 800, WorkType.CRAFT, 0, Research.BOWS),
    MAKE_GREATBOW("Make greatbow", listOf(BuildDef.SMITHY), listOf(ing(ItemType.WOOD, 80), ing(ItemType.STEEL, 10)), ItemType.W_GREATBOW, 1, 1300, WorkType.SMITH, 4, Research.BOWS),
    MAKE_HELMET("Make simple helmet", listOf(BuildDef.SMITHY), listOf(ing(ItemType.STEEL, 30)), ItemType.A_HELMET, 1, 1300, WorkType.SMITH, 4, Research.FLAK_ARMOR),
    MAKE_PLATE("Make plate armor", listOf(BuildDef.SMITHY), listOf(ing(ItemType.STEEL, 160)), ItemType.A_ARMOR, 1, 4000, WorkType.SMITH, 8, Research.PLATE_ARMOR),

    MAKE_GLADIUS("Make gladius", listOf(BuildDef.SMITHY), listOf(ing(ItemType.STEEL, 50)), ItemType.W_GLADIUS, 1, 1300, WorkType.SMITH, 5, Research.BASIC_MELEE),
    MAKE_RECURVE("Make recurve bow", listOf(BuildDef.SMITHY, BuildDef.CRAFTING_SPOT), listOf(ing(ItemType.WOOD, 60), ing(ItemType.STEEL, 10)), ItemType.W_RECURVE, 1, 1100, WorkType.CRAFT, 4, Research.RECURVE),
    MAKE_PLASTEEL_SWORD("Make plasteel sword", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.PLASTEEL, 40), ing(ItemType.STEEL, 20)), ItemType.W_PLASTEEL_SWORD, 1, 3000, WorkType.SMITH, 9, Research.PLASTEEL_MELEE),
    MAKE_LEVER("Make lever-action rifle", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 55), ing(ItemType.COMPONENT, 2)), ItemType.W_LEVER, 1, 2400, WorkType.SMITH, 5, Research.ADV_FIREARMS),
    MAKE_CHAIN_SHOTGUN("Make chain shotgun", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 70), ing(ItemType.COMPONENT, 3)), ItemType.W_CHAIN_SHOTGUN, 1, 3000, WorkType.SMITH, 7, Research.ADV_FIREARMS),
    MAKE_HEAVY_SMG("Make heavy SMG", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 65), ing(ItemType.COMPONENT, 4)), ItemType.W_HEAVY_SMG, 1, 3000, WorkType.SMITH, 7, Research.ADV_FIREARMS),
    MAKE_MINIGUN("Make minigun", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.STEEL, 150), ing(ItemType.COMPONENT, 10)), ItemType.W_MINIGUN, 1, 5000, WorkType.SMITH, 10, Research.HEAVY_WEAPONS),
    MAKE_CHARGE_RIFLE("Make charge rifle", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.STEEL, 80), ing(ItemType.PLASTEEL, 20), ing(ItemType.COMPONENT, 8)), ItemType.W_CHARGE_RIFLE, 1, 4500, WorkType.SMITH, 10, Research.CHARGE_WEAPONS),
    MAKE_FRAG("Make frag grenades", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 25), ing(ItemType.COMPONENT, 1)), ItemType.W_FRAG, 1, 1400, WorkType.SMITH, 4, Research.EXPLOSIVES),
    MAKE_INCENDIARY("Make incendiary launcher", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 60), ing(ItemType.COMPONENT, 3)), ItemType.W_INCENDIARY, 1, 2600, WorkType.SMITH, 6, Research.EXPLOSIVES),
    MAKE_DOOMSDAY("Make doomsday launcher", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.STEEL, 120), ing(ItemType.PLASTEEL, 40), ing(ItemType.COMPONENT, 12)), ItemType.W_DOOMSDAY, 1, 6000, WorkType.SMITH, 11, Research.ROCKETS),
    MAKE_MARINE("Make marine armor", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.PLASTEEL, 50), ing(ItemType.STEEL, 40), ing(ItemType.COMPONENT, 4)), ItemType.A_MARINE, 1, 5000, WorkType.SMITH, 9, Research.MARINE_ARMOR),
    MAKE_MARINE_HELM("Make marine helmet", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.PLASTEEL, 25), ing(ItemType.COMPONENT, 2)), ItemType.A_MARINE_HELM, 1, 2400, WorkType.SMITH, 8, Research.MARINE_ARMOR),
    MAKE_RECON_HELM("Make recon helmet", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.PLASTEEL, 25), ing(ItemType.COMPONENT, 2)), ItemType.A_RECON_HELM, 1, 2200, WorkType.SMITH, 8, Research.ADV_ARMOR),
    MAKE_POWER_ARMOR("Make powered armor", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.PLASTEEL, 120), ing(ItemType.GOLD, 10), ing(ItemType.COMPONENT, 12)), ItemType.A_POWER_ARMOR, 1, 8000, WorkType.SMITH, 11, Research.POWER_ARMOR),
    MAKE_POWER_HELM("Make powered helmet", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.PLASTEEL, 50), ing(ItemType.COMPONENT, 5)), ItemType.A_POWER_HELM, 1, 4000, WorkType.SMITH, 10, Research.POWER_ARMOR),

    // Machining
    MAKE_REVOLVER("Make revolver", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 30), ing(ItemType.COMPONENT, 1)), ItemType.W_REVOLVER, 1, 1800, WorkType.SMITH, 4, Research.FIREARMS),
    MAKE_AUTOPISTOL("Make autopistol", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 35), ing(ItemType.COMPONENT, 1)), ItemType.W_AUTOPISTOL, 1, 1800, WorkType.SMITH, 4, Research.FIREARMS),
    MAKE_BOLT("Make bolt-action rifle", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 60), ing(ItemType.COMPONENT, 2)), ItemType.W_BOLT, 1, 2400, WorkType.SMITH, 5, Research.FIREARMS),
    MAKE_SHOTGUN("Make pump shotgun", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 50), ing(ItemType.COMPONENT, 2)), ItemType.W_SHOTGUN, 1, 2400, WorkType.SMITH, 5, Research.FIREARMS),
    MAKE_SMG("Make machine pistol", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 40), ing(ItemType.COMPONENT, 3)), ItemType.W_SMG, 1, 2400, WorkType.SMITH, 6, Research.FIREARMS),
    MAKE_RIFLE("Make assault rifle", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 80), ing(ItemType.COMPONENT, 6)), ItemType.W_RIFLE, 1, 3200, WorkType.SMITH, 8, Research.FIREARMS),
    MAKE_LMG("Make light machine gun", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 100), ing(ItemType.COMPONENT, 8)), ItemType.W_LMG, 1, 3800, WorkType.SMITH, 9, Research.FIREARMS),
    MAKE_SNIPER("Make sniper rifle", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 100), ing(ItemType.COMPONENT, 8)), ItemType.W_SNIPER, 1, 3800, WorkType.SMITH, 9, Research.FIREARMS),
    MAKE_SHELLS("Make mortar shells", listOf(BuildDef.MACHINING), listOf(ing(ItemType.STEEL, 15)), ItemType.SHELL, 5, 800, WorkType.SMITH, 3, Research.MORTARS),
    MAKE_NUTRIENT("Make nutrient paste meal", listOf(BuildDef.NUTRIENT_DISPENSER), listOf(anyOf("Food", 10, *RAW_VEG, *RAW_MEAT)), ItemType.MEAL_NUTRIENT, 10, 400, WorkType.COOK, 0, Research.NUTRIENT_PASTE),
    CARVE_SMALL("Carve small sculpture", listOf(BuildDef.SCULPTOR_TABLE), listOf(ing(ItemType.STONE, 50)), ItemType.SCULPTURE_SMALL, 1, 1200, WorkType.ART, 0, Research.SCULPTING),
    CARVE_LARGE("Carve large sculpture", listOf(BuildDef.SCULPTOR_TABLE), listOf(ing(ItemType.STONE, 140)), ItemType.SCULPTURE_LARGE, 1, 3600, WorkType.ART, 4, Research.SCULPTING),
    MAKE_RECON("Make recon armor", listOf(BuildDef.FAB_BENCH), listOf(ing(ItemType.PLASTEEL, 60), ing(ItemType.COMPONENT, 4)), ItemType.A_RECON, 1, 4500, WorkType.SMITH, 8, Research.ADV_ARMOR);

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
