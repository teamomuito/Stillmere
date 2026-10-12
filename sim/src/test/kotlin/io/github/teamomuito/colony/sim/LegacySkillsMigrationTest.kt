package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * colony_v32.sav is a version-32 save, written by the version-32 code before the daily skill counter of version 33
 * (temporary generator: Game(4242L), startNewColony(QUIET_ENCLAVE), 20000 steps). Its expectation file lists the
 * skills, XP, passions and traits it held. It must still load with them, its counter must read as zero, and version 33
 * must keep the counter it is given. Every XP value in it is zero at the save point (its cause is not checked here).
 *
 * colony_v32_xp.sav is the same colony with XP on the skills each colonist can learn. It was made by a temporary
 * generator that loaded colony_v32.sav, called gainXp on each skill, and wrote the result with SaveGame.write(g, 32).
 * The version-32 layout is unchanged by version 33, which is why that write is a version-32 save: writing the loaded
 * colony_v32.sav back at version 32 gives the same bytes as the original file.
 */
class LegacySkillsMigrationTest {
    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/legacy/$name")?.use { it.readBytes() } ?: error("missing test resource legacy/$name")

    private fun expectations(name: String): Map<String, String> =
        resource(name).decodeToString().lines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private val fixture = resource("colony_v32.sav")
    private val expect = expectations("colony_v32_expect.txt")
    private val xpFixture = resource("colony_v32_xp.sav")
    private val xpExpect = expectations("colony_v32_xp_expect.txt")

    private fun versionOf(bytes: ByteArray): Int = DataInputStream(ByteArrayInputStream(bytes)).readInt()
    private fun ints(s: String): List<Int> = s.split(',').filter { it.isNotEmpty() }.map { it.toInt() }
    private fun floats(s: String): List<Float> = s.split(',').filter { it.isNotEmpty() }.map { it.toFloat() }

    @Test fun theFixtureIsReallyVersionThirtyTwo() {
        assertEquals(32, versionOf(fixture))
    }

    @Test fun aVersionThirtyTwoSaveLoadsItsSkillsXpPassionsAndTraits() {
        val g = SaveGame.read(fixture)
        assertEquals(expect.getValue("colonists").toInt(), g.colonists.size)
        g.colonists.forEachIndexed { k, p ->
            assertEquals("name $k", expect.getValue("name.$k"), p.name)
            assertEquals("skills $k", ints(expect.getValue("skill.$k")), p.skill.toList())
            assertEquals("xp $k", floats(expect.getValue("xp.$k")), p.xp.toList())
            assertEquals("passions $k", ints(expect.getValue("passion.$k")), p.passion.toList())
            assertEquals("traits $k", ints(expect.getValue("traits.$k")), p.traits.map { it.ordinal })
        }
    }

    @Test fun theDailyCounterReadsAsZeroFromAVersionThirtyTwoSave() {
        val g = SaveGame.read(fixture)
        for (p in g.colonists) assertTrue("${p.name}", p.xpToday.all { it == 0f })
    }

    @Test fun aVersionThirtyTwoSaveWrittenAgainIsVersionThirtyFour() {
        assertEquals(35, versionOf(SaveGame.write(SaveGame.read(fixture))))
    }

    @Test fun theDailyCounterSurvivesASaveAtVersionThirtyFour() {
        val g = SaveGame.read(fixture)
        g.colonists.first().xpToday[SkillType.ARTISTRY.ordinal] = 1234.5f
        val again = SaveGame.read(SaveGame.write(g)).colonists.first()
        assertEquals(1234.5f, again.xpToday[SkillType.ARTISTRY.ordinal], 0f)
    }

    @Test fun aVersionThirtyTwoWriteLeavesTheCounterOut() {
        val g = SaveGame.read(fixture)
        g.colonists.first().xpToday[SkillType.ARTISTRY.ordinal] = 99f
        val bytes = SaveGame.write(g, 32)
        assertEquals(32, versionOf(bytes))
        assertTrue("version 32 holds no counter", SaveGame.read(bytes).colonists.all { p -> p.xpToday.all { it == 0f } })
    }

    @Test fun aVersionThirtyTwoSaveWithXpLoadsEverySkillAndXpValue() {
        assertEquals(32, versionOf(xpFixture))
        val g = SaveGame.read(xpFixture)
        assertEquals(xpExpect.getValue("colonists").toInt(), g.colonists.size)
        g.colonists.forEachIndexed { k, p ->
            assertEquals("name $k", xpExpect.getValue("name.$k"), p.name)
            assertEquals("skills $k", ints(xpExpect.getValue("skill.$k")), p.skill.toList())
            assertEquals("xp $k", floats(xpExpect.getValue("xp.$k")), p.xp.toList())
            assertEquals("passions $k", ints(xpExpect.getValue("passion.$k")), p.passion.toList())
            assertEquals("traits $k", ints(xpExpect.getValue("traits.$k")), p.traits.map { it.ordinal })
        }
    }

    @Test fun theXpInTheFixtureIsNonZeroForEveryColonist() {
        val g = SaveGame.read(xpFixture)
        for (p in g.colonists) assertTrue("${p.name} has XP", p.xp.any { it > 0f })
        // The XP is spread over several skills, so the read is not one value copied everywhere.
        assertTrue("several distinct XP values", g.colonists.flatMap { p -> p.xp.toList() }.distinct().size > 10)
    }

    @Test fun theDailyCounterReadsAsZeroFromAVersionThirtyTwoXpSave() {
        val g = SaveGame.read(xpFixture)
        for (p in g.colonists) assertTrue("${p.name}", p.xpToday.all { it == 0f })
    }

    @Test fun theXpSurvivesASaveAtVersionThirtyFour() {
        val g = SaveGame.read(xpFixture)
        val bytes = SaveGame.write(g)
        assertEquals(35, versionOf(bytes))
        val again = SaveGame.read(bytes)
        for (k in g.colonists.indices) {
            assertEquals("xp $k", g.colonists[k].xp.toList(), again.colonists[k].xp.toList())
            assertEquals("skills $k", g.colonists[k].skill.toList(), again.colonists[k].skill.toList())
        }
    }
}
