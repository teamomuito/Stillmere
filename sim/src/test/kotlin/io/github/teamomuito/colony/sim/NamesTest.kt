package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Enums are saved by ordinal, so a renamed constant must keep the ordinal it had before the rename. The expected numbers are
 * the ordinals from version 25. The old-to-new mapping is in docs/fidelity/names.md.
 */
class NamesTest {
    @Test fun renamedConstantsKeepTheirOrdinals() {
        // Race
        assertEquals("Race.SHAGOX", 4, Race.SHAGOX.ordinal)
        assertEquals("Race.SKYHORN", 11, Race.SKYHORN.ordinal)
        assertEquals("Race.ASHHOUND", 25, Race.ASHHOUND.ordinal)
        assertEquals("Race.PUFFTAIL", 34, Race.PUFFTAIL.ordinal)
        assertEquals("Race.SWELLGRAZER", 35, Race.SWELLGRAZER.ordinal)
        assertEquals("Race.HORNBEETLE", 36, Race.HORNBEETLE.ordinal)
        assertEquals("Race.CAVECRAWLER", 37, Race.CAVECRAWLER.ordinal)
        assertEquals("Race.PITWEAVER", 38, Race.PITWEAVER.ordinal)
        assertEquals("Race.HOOKTALON", 39, Race.HOOKTALON.ordinal)
        assertEquals("Race.PIKEWALKER", 40, Race.PIKEWALKER.ordinal)
        assertEquals("Race.SIEGEFRAME", 41, Race.SIEGEFRAME.ordinal)
        // Scenario
        assertEquals("Scenario.SALVAGE_CREW", 0, Scenario.SALVAGE_CREW.ordinal)
        assertEquals("Scenario.QUIET_ENCLAVE", 1, Scenario.QUIET_ENCLAVE.ordinal)
        assertEquals("Scenario.SURPLUS_START", 2, Scenario.SURPLUS_START.ordinal)
        // ItemType
        assertEquals("ItemType.HARDALLOY", 4, ItemType.HARDALLOY.ordinal)
        assertEquals("ItemType.WISPSILK", 8, ItemType.WISPSILK.ordinal)
        assertEquals("ItemType.MEAL_RATION", 24, ItemType.MEAL_RATION.ordinal)
        assertEquals("ItemType.MIREWORT", 28, ItemType.MIREWORT.ordinal)
        assertEquals("ItemType.HUSHMOSS", 32, ItemType.HUSHMOSS.ordinal)
        assertEquals("ItemType.DREAMWEED", 34, ItemType.DREAMWEED.ordinal)
        assertEquals("ItemType.PALE_DUST", 35, ItemType.PALE_DUST.ordinal)
        assertEquals("ItemType.WHITE_RUSH", 36, ItemType.WHITE_RUSH.ordinal)
        assertEquals("ItemType.SURGE_TONIC", 37, ItemType.SURGE_TONIC.ordinal)
        assertEquals("ItemType.DAWN_TABLET", 38, ItemType.DAWN_TABLET.ordinal)
        assertEquals("ItemType.LUCID_DRAUGHT", 39, ItemType.LUCID_DRAUGHT.ordinal)
        assertEquals("ItemType.TRAIL_CAKE", 25, ItemType.TRAIL_CAKE.ordinal)
        assertEquals("ItemType.FEED_PELLETS", 26, ItemType.FEED_PELLETS.ordinal)
        assertEquals("ItemType.W_HARDALLOY_SWORD", 57, ItemType.W_HARDALLOY_SWORD.ordinal)
        assertEquals("ItemType.W_SKYFALL_BATTERY", 66, ItemType.W_SKYFALL_BATTERY.ordinal)
        // Research
        assertEquals("Research.ANALYSIS_ARRAY", 27, Research.ANALYSIS_ARRAY.ordinal)
        assertEquals("Research.WISPSILK_RESEARCH", 30, Research.WISPSILK_RESEARCH.ordinal)
        assertEquals("Research.HARDALLOY_BLADES", 33, Research.HARDALLOY_BLADES.ordinal)
        assertEquals("Research.STIMULANT_BLENDS", 46, Research.STIMULANT_BLENDS.ordinal)
        assertEquals("Research.SURGE_TONIC", 47, Research.SURGE_TONIC.ordinal)
        assertEquals("Research.STASIS", 49, Research.STASIS.ordinal)
        assertEquals("Research.VAT_CULTURING", 54, Research.VAT_CULTURING.ordinal)
        assertEquals("Research.SWIVEL_NESTS", 17, Research.SWIVEL_NESTS.ordinal)
        assertEquals("Research.BRIGANDINE_WORK", 20, Research.BRIGANDINE_WORK.ordinal)
        assertEquals("Research.HERBAL_REMEDIES", 8, Research.HERBAL_REMEDIES.ordinal)
        // PlantType
        assertEquals("PlantType.FERAL_MIREWORT", 10, PlantType.FERAL_MIREWORT.ordinal)
        assertEquals("PlantType.MIREWORT", 16, PlantType.MIREWORT.ordinal)
        assertEquals("PlantType.HUSHMOSS", 17, PlantType.HUSHMOSS.ordinal)
        assertEquals("PlantType.DREAMWEED", 18, PlantType.DREAMWEED.ordinal)
        assertEquals("PlantType.WISPSILK_CROP", 19, PlantType.WISPSILK_CROP.ordinal)
        assertEquals("PlantType.MEADOW_GRASS", 20, PlantType.MEADOW_GRASS.ordinal)
        // Weapon
        assertEquals("Weapon.HARDALLOY_SWORD", 17, Weapon.HARDALLOY_SWORD.ordinal)
        assertEquals("Weapon.SKYFALL_BATTERY", 26, Weapon.SKYFALL_BATTERY.ordinal)
        // BuildDef
        assertEquals("BuildDef.HARDALLOY_WALL", 5, BuildDef.HARDALLOY_WALL.ordinal)
        assertEquals("BuildDef.PRECISION_BENCH", 42, BuildDef.PRECISION_BENCH.ordinal)
        assertEquals("BuildDef.QUENCH_VENT", 53, BuildDef.QUENCH_VENT.ordinal)
        assertEquals("BuildDef.QUOITS", 26, BuildDef.QUOITS.ordinal)
        // HediffKind
        assertEquals("HediffKind.HUSHMOSS_HIGH", 13, HediffKind.HUSHMOSS_HIGH.ordinal)
        assertEquals("HediffKind.LUCID_HIGH", 14, HediffKind.LUCID_HIGH.ordinal)
        assertEquals("HediffKind.HUSHMOSS_ADDICTION", 17, HediffKind.HUSHMOSS_ADDICTION.ordinal)
        assertEquals("HediffKind.LUCID_ADDICTION", 18, HediffKind.LUCID_ADDICTION.ordinal)
        assertEquals("HediffKind.PALE_DUST_HIGH", 19, HediffKind.PALE_DUST_HIGH.ordinal)
        assertEquals("HediffKind.WHITE_RUSH_HIGH", 20, HediffKind.WHITE_RUSH_HIGH.ordinal)
        assertEquals("HediffKind.SURGE_TONIC_HIGH", 21, HediffKind.SURGE_TONIC_HIGH.ordinal)
        assertEquals("HediffKind.DAWN_TABLET_HIGH", 22, HediffKind.DAWN_TABLET_HIGH.ordinal)
        assertEquals("HediffKind.PALE_DUST_ADDICTION", 23, HediffKind.PALE_DUST_ADDICTION.ordinal)
        assertEquals("HediffKind.WHITE_RUSH_ADDICTION", 24, HediffKind.WHITE_RUSH_ADDICTION.ordinal)
        assertEquals("HediffKind.SURGE_TONIC_ADDICTION", 25, HediffKind.SURGE_TONIC_ADDICTION.ordinal)
        // Recipe
        assertEquals("Recipe.MAKE_PALE_DUST", 12, Recipe.MAKE_PALE_DUST.ordinal)
        assertEquals("Recipe.MAKE_WHITE_RUSH", 13, Recipe.MAKE_WHITE_RUSH.ordinal)
        assertEquals("Recipe.MAKE_DAWN_TABLET", 14, Recipe.MAKE_DAWN_TABLET.ordinal)
        assertEquals("Recipe.MAKE_SURGE_TONIC", 15, Recipe.MAKE_SURGE_TONIC.ordinal)
        assertEquals("Recipe.MAKE_HARDALLOY_SWORD", 40, Recipe.MAKE_HARDALLOY_SWORD.ordinal)
        assertEquals("Recipe.MAKE_SKYFALL_BATTERY", 48, Recipe.MAKE_SKYFALL_BATTERY.ordinal)
        // Apparel
        assertEquals("Apparel.KNIT_CAP", 7, Apparel.KNIT_CAP.ordinal)
        assertEquals("Apparel.BRIGANDINE", 12, Apparel.BRIGANDINE.ordinal)
        // Ore
        assertEquals("Ore.HARDALLOY", 4, Ore.HARDALLOY.ordinal)
    }

    @Test fun theRenamedIncidentHasItsOrdinalAndNewName() {
        // Incidents are saved by name. A version 25 save stores the old name; LegacyNames maps it to this constant (see LegacyNamesMigrationTest).
        assertEquals("Incident.SKYHORN_VISIT", 15, Incident.SKYHORN_VISIT.ordinal)
        assertEquals("SKYHORN_VISIT", Incident.SKYHORN_VISIT.name)
    }

    @Test fun scenarioAndDifficultyLabelsAreTheNewOnes() {
        assertEquals("Salvage crew", Scenario.SALVAGE_CREW.label)
        assertEquals("Quiet enclave", Scenario.QUIET_ENCLAVE.label)
        assertEquals("Surplus start", Scenario.SURPLUS_START.label)
        assertEquals("Bare start", Scenario.SOLO.label)
        assertEquals("Relentless", Difficulty.EXTREME.label)
    }

    @Test fun renamedIdentifiersKeepTheirOrdinals() {
        // Renames keep the position of each constant, so the saved ordinal does not change. These are the positions in the source.
        assertEquals("SkillType.MARKSMANSHIP", 0, SkillType.MARKSMANSHIP.ordinal)
        assertEquals("SkillType.HEALING", 9, SkillType.HEALING.ordinal)
        assertEquals("Trait.SUNNY_OUTLOOK", 4, Trait.SUNNY_OUTLOOK.ordinal)
        assertEquals("Trait.GREY_OUTLOOK", 5, Trait.GREY_OUTLOOK.ordinal)
        assertEquals("Trait.SAME_SEX_DRAWN", 30, Trait.SAME_SEX_DRAWN.ordinal)
        assertEquals("HediffKind.CHEST_COUGH", 0, HediffKind.CHEST_COUGH.ordinal)
        assertEquals("HediffKind.BREW_HAZE", 12, HediffKind.BREW_HAZE.ordinal)
        assertEquals("HediffKind.CRAVING_ACHE", 26, HediffKind.CRAVING_ACHE.ordinal)
        assertEquals("Research.PHARMACOLOGY", 23, Research.PHARMACOLOGY.ordinal)
        assertEquals("Research.LATHE_WORK", 15, Research.LATHE_WORK.ordinal)
        assertEquals("Research.HORN_BACKING", 32, Research.HORN_BACKING.ordinal)
        assertEquals("BuildDef.SHAPING_RIG", 37, BuildDef.SHAPING_RIG.ordinal)
        assertEquals("BuildDef.INFIRMARY_COT", 14, BuildDef.INFIRMARY_COT.ordinal)
        assertEquals("BuildDef.GROW_TRAY", 43, BuildDef.GROW_TRAY.ordinal)
        assertEquals("Weapon.POCKET_BLADE", 1, Weapon.POCKET_BLADE.ordinal)
        assertEquals("Weapon.HORNWOUND", 18, Weapon.HORNWOUND.ordinal)
        assertEquals("ItemType.MANFLESH", 17, ItemType.MANFLESH.ordinal)
        assertEquals("ItemType.W_BROAD_BLADE", 46, ItemType.W_BROAD_BLADE.ordinal)
        assertEquals("Recipe.MAKE_KNIT_CAP", 25, Recipe.MAKE_KNIT_CAP.ordinal)
        assertEquals("Implant.SERVO_LIMB", 2, Implant.SERVO_LIMB.ordinal)
        assertEquals("Implant.PUMP_CORE", 6, Implant.PUMP_CORE.ordinal)
    }

    @Test fun renamedIdentifiersOfTheVersion30RoundKeepTheirOrdinals() {
        // Constants renamed so that no identifier echoes an old label. Each keeps its position, so its saved ordinal is unchanged.
        assertEquals("ItemType.MEAL_HEARTH_STEW", 21, ItemType.MEAL_HEARTH_STEW.ordinal)
        assertEquals("ItemType.MEAL_ROASTED_PLATTER", 22, ItemType.MEAL_ROASTED_PLATTER.ordinal)
        assertEquals("ItemType.MEAL_BANQUET_SPREAD", 23, ItemType.MEAL_BANQUET_SPREAD.ordinal)
        assertEquals("ItemType.MEDS_LEAF_POULTICE", 29, ItemType.MEDS_LEAF_POULTICE.ordinal)
        assertEquals("ItemType.MEDS_STERILE_DRESSING", 30, ItemType.MEDS_STERILE_DRESSING.ordinal)
        assertEquals("ItemType.A_RIVETED_HOOD", 79, ItemType.A_RIVETED_HOOD.ordinal)
        assertEquals("ItemType.A_PLUMED_CROWN", 87, ItemType.A_PLUMED_CROWN.ordinal)
        assertEquals("ItemType.A_COLLAR_TUNIC", 69, ItemType.A_COLLAR_TUNIC.ordinal)
        assertEquals("ItemType.A_TRENCH_VISOR", 83, ItemType.A_TRENCH_VISOR.ordinal)
        assertEquals("Apparel.RIVETED_HOOD", 11, Apparel.RIVETED_HOOD.ordinal)
        assertEquals("Apparel.PLUMED_CROWN", 19, Apparel.PLUMED_CROWN.ordinal)
        assertEquals("Apparel.COLLAR_TUNIC", 1, Apparel.COLLAR_TUNIC.ordinal)
        assertEquals("Apparel.TRENCH_VISOR", 15, Apparel.TRENCH_VISOR.ordinal)
        assertEquals("Research.TINCTURE_CHEMISTRY", 7, Research.TINCTURE_CHEMISTRY.ordinal)
        assertEquals("Weapon.FIRE_FLASK", 29, Weapon.FIRE_FLASK.ordinal)
        assertEquals("Recipe.COOK_HEARTH_STEW", 0, Recipe.COOK_HEARTH_STEW.ordinal)
        assertEquals("Recipe.COOK_ROASTED_PLATTER", 1, Recipe.COOK_ROASTED_PLATTER.ordinal)
        assertEquals("Recipe.COOK_BANQUET_SPREAD", 11, Recipe.COOK_BANQUET_SPREAD.ordinal)
        assertEquals("Recipe.MAKE_RIVETED_HOOD", 36, Recipe.MAKE_RIVETED_HOOD.ordinal)
        assertEquals("Recipe.MAKE_PLUMED_CROWN", 17, Recipe.MAKE_PLUMED_CROWN.ordinal)
        assertEquals("Recipe.MAKE_COLLAR_TUNIC", 19, Recipe.MAKE_COLLAR_TUNIC.ordinal)
        assertEquals("Recipe.MAKE_LEAF_POULTICE", 8, Recipe.MAKE_LEAF_POULTICE.ordinal)
        assertEquals("BuildDef.COAL_FORGE", 36, BuildDef.COAL_FORGE.ordinal)
        assertEquals("BuildDef.EMBER_OVEN", 32, BuildDef.EMBER_OVEN.ordinal)
        assertEquals("BuildDef.HOTPLATE", 33, BuildDef.HOTPLATE.ordinal)
        assertEquals("BuildDef.POCKET_BOARD", 29, BuildDef.POCKET_BOARD.ordinal)
    }

    @Test fun labelsChangedInTheVersion30RoundAreTheNewOnes() {
        assertEquals("Sward", PlantType.MEADOW_GRASS.label)
        assertEquals("Fire flask", Weapon.FIRE_FLASK.label)
        assertEquals("Late bloomer", Trait.SLOW_STUDY.label)
        assertEquals("Tincture chemistry", Research.TINCTURE_CHEMISTRY.label)
    }

    @Test fun renamedLabelsAreTheNewOnesAndSkillLabelsAreUnique() {
        assertEquals("Marksmanship", SkillType.MARKSMANSHIP.label)
        assertEquals("Healing", SkillType.HEALING.label)
        assertEquals("Chest cough", HediffKind.CHEST_COUGH.label)
        assertEquals("Craving ache", HediffKind.CRAVING_ACHE.label)
        assertEquals(SkillType.entries.size, SkillType.entries.map { it.label }.toSet().size)
    }

    @Test fun labelsThatCouldCollideAreUnique() {
        assertEquals(Race.entries.size, Race.entries.map { it.label }.toSet().size)
        assertEquals(Scenario.entries.size, Scenario.entries.map { it.label }.toSet().size)
        assertEquals(Difficulty.entries.size, Difficulty.entries.map { it.label }.toSet().size)
        assertEquals(Backstories.childhood.size, Backstories.childhood.map { it.title }.toSet().size)
        assertEquals(Backstories.adulthood.size, Backstories.adulthood.map { it.title }.toSet().size)
    }
}
