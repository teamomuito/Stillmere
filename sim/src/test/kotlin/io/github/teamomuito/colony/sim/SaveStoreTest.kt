package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32

private fun newGame(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    g.mentalBreaksEnabled = false
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    g.nextRaid = Long.MAX_VALUE; g.nextMisc = Long.MAX_VALUE; g.nextWanderer = Long.MAX_VALUE; g.nextTrader = Long.MAX_VALUE
    return g
}

private fun Game.run(ticks: Int) { repeat(ticks) { step() } }

private fun newStore(): SaveStore = SaveStore(Files.createTempDirectory("saves").toFile())

/**
 * Changes a slot's body after [edit]. With [fixChecksum] the checksum is recomputed, so the change looks like a valid
 * file; without it the change is damage the checksum must catch.
 */
private fun SaveStore.editBody(id: String, fixChecksum: Boolean, edit: (ByteArray) -> Unit) {
    val f = file(id)
    val bytes = f.readBytes()
    val h = DataInputStream(bytes.inputStream())
    h.readInt(); h.readInt(); h.readUTF(); h.readLong(); h.readLong(); h.readUTF(); h.readInt(); h.readInt(); h.readLong()
    val bodyLen = h.readInt(); h.readLong()
    val headerEnd = bytes.size - h.available()
    val body = bytes.copyOfRange(headerEnd, bytes.size)
    edit(body)
    val crc = CRC32().apply { update(body) }.value
    val out = bytes.copyOf(headerEnd)
    // The checksum is the last 8 bytes of the header; the body length sits just before it.
    if (fixChecksum) java.nio.ByteBuffer.wrap(out, headerEnd - 8, 8).putLong(crc)
    java.nio.ByteBuffer.wrap(out, headerEnd - 12, 4).putInt(bodyLen)
    f.writeBytes(out + body)
}

class RngTest {
    @Test fun matchesJavaUtilRandomSoExistingSeededResultsDoNotChange() {
        for (seed in listOf(0L, 1L, 42L, -7L, 123_456_789L)) {
            val mine = Rng(seed)
            val theirs = java.util.Random(seed)
            repeat(200) {
                for (n in listOf(1, 2, 7, 8, 10, 64, 100, 1000, 65536)) assertEquals(theirs.nextInt(n), mine.int(n))
                assertEquals(theirs.nextFloat(), mine.float(), 0f)
            }
        }
    }

    @Test fun aRestoredStateContinuesTheSameSequence() {
        val a = Rng(99)
        repeat(37) { a.int(100) }
        val b = Rng(0).also { it.restore(a.state) }
        repeat(50) { assertEquals(a.int(1000), b.int(1000)) }
    }
}

class SaveStoreTest {
    @Test fun aRoundTripKeepsTheGameTimeColonistsAndGeneratorState() {
        val store = newStore()
        val g = newGame(11)
        g.run(2000)
        store.write("save-round", "Round", g, playtimeMs = 5_000)
        val loaded = store.read("save-round")
        assertEquals(g.tick, loaded.game.tick)
        assertEquals(g.colonyName, loaded.game.colonyName)
        assertEquals(g.colonists.size, loaded.game.colonists.size)
        assertEquals(g.rng.state, loaded.game.rng.state)
        assertEquals("Round", loaded.info.name)
        assertEquals(5_000L, loaded.info.playtimeMs)
    }

    @Test fun savedCarriedLoadAndSurgeryQueueAreRestored() {
        val store = newStore()
        val g = newGame(12)
        val p = g.colonists[0]
        p.carryType = ItemType.STEEL; p.carryCount = 7; p.carryQuality = Quality.GOOD
        p.surgeries.add(SurgeryOrder(SurgeryKind.AMPUTATE, 0, null))
        store.write("save-carry", "Carry", g)
        val lp = store.read("save-carry").game.pawns.first { it.id == p.id }
        assertEquals(ItemType.STEEL, lp.carryType)
        assertEquals(7, lp.carryCount)
        assertEquals(Quality.GOOD, lp.carryQuality)
        assertEquals(1, lp.surgeries.size)
        assertEquals(SurgeryKind.AMPUTATE, lp.surgeries[0].kind)
    }

    @Test fun endOfGameFlagsAndTheRaidTimerSurvive() {
        val store = newStore()
        val g = newGame(13)
        g.raidStartedAt = 777L
        g.gameOver = true; g.won = true
        store.write("save-flags", "Flags", g)
        val l = store.read("save-flags").game
        assertEquals(777L, l.raidStartedAt)
        assertTrue(l.gameOver); assertTrue(l.won)
    }

    @Test fun aNewGameDoesNotInheritAnotherGamesRaidTimer() {
        val old = newGame(14)
        old.raidStartedAt = 12_345L
        val fresh = newGame(15)
        assertEquals(0L, fresh.raidStartedAt)
    }

    @Test fun severalSavesCoexistAndAreListedWithTheirMetadata() {
        val store = newStore()
        store.write(SaveStore.AUTOSAVE, "Autosave", newGame(21))
        store.write(SaveStore.QUICKSAVE, "Quicksave", newGame(22))
        store.write("save-a", "Alpha", newGame(23), playtimeMs = 1_000)
        store.write("save-b", "Beta", newGame(24), playtimeMs = 2_000)
        val list = store.list()
        assertEquals(4, list.size)
        assertEquals(setOf("Alpha", "Beta"), list.filter { it.isNamed }.map { it.name }.toSet())
        assertTrue(list.none { it.damaged })
        assertTrue(list.all { it.colonists > 0 && it.colonyName.isNotEmpty() })
        // Autosave and quicksave come first, then named saves, newest first.
        assertEquals(SaveStore.AUTOSAVE, list[0].id)
        assertEquals(SaveStore.QUICKSAVE, list[1].id)
    }

    @Test fun overwritingTheSelectedSaveLeavesEveryOtherSaveAlone() {
        val store = newStore()
        store.write("save-a", "Alpha", newGame(31))
        store.write("save-b", "Beta", newGame(32))
        val betaBefore = store.file("save-b").readBytes()
        val fresh = newGame(33)
        fresh.run(500)
        store.write("save-a", "Alpha", fresh, replace = true)
        assertEquals(fresh.tick, store.read("save-a").game.tick)
        assertTrue("Beta is byte-for-byte unchanged", betaBefore.contentEquals(store.file("save-b").readBytes()))
    }

    @Test fun aNewIdIsNeverWrittenOverAnExistingSave() {
        val store = newStore()
        store.write("save-a", "Alpha", newGame(41))
        val before = store.file("save-a").readBytes()
        val other = newGame(42); other.run(300)
        try {
            store.write("save-a", "Stolen", other)
            fail("an existing save was overwritten without asking")
        } catch (e: SaveException) {
            assertTrue(e.message!!.contains("already exists"))
        }
        assertTrue(before.contentEquals(store.file("save-a").readBytes()))
    }

    @Test fun quicksaveReplacesOnlyThePreviousQuicksave() {
        val store = newStore()
        store.write(SaveStore.AUTOSAVE, "Autosave", newGame(51))
        store.write("save-named", "Named", newGame(52))
        val autoBefore = store.file(SaveStore.AUTOSAVE).readBytes()
        val namedBefore = store.file("save-named").readBytes()
        val first = newGame(53); first.run(100)
        store.write(SaveStore.QUICKSAVE, "Quicksave", first, replace = true)
        val second = newGame(54); second.run(900)
        store.write(SaveStore.QUICKSAVE, "Quicksave", second, replace = true)
        assertEquals(second.tick, store.read(SaveStore.QUICKSAVE).game.tick)
        assertTrue(autoBefore.contentEquals(store.file(SaveStore.AUTOSAVE).readBytes()))
        assertTrue(namedBefore.contentEquals(store.file("save-named").readBytes()))
    }

    @Test fun namesAreCleanedAndDuplicatesAreRefused() {
        val store = newStore()
        assertEquals("My Base", store.cleanName("  My   Base  "))
        for (bad in listOf("", "   ", "x".repeat(41), "autosave", "Quicksave", "bad\u0007name")) {
            try { store.cleanName(bad); fail("accepted '$bad'") } catch (_: SaveException) {}
        }
        store.write("save-alpha", "Alpha", newGame(61))
        try { store.ensureUniqueName("alpha"); fail("duplicate allowed") } catch (e: SaveException) {
            assertTrue(e.message!!.contains("Alpha"))
        }
        store.ensureUniqueName("alpha", exceptId = "save-alpha")   // renaming a save to its own name is fine
        store.ensureUniqueName("Gamma")
    }

    @Test fun corruptedSavesAreReportedNotLoaded() {
        val store = newStore()
        store.write("save-a", "Alpha", newGame(71))
        store.write("save-b", "Beta", newGame(72))
        store.editBody("save-a", fixChecksum = false) { body -> body[body.size / 2] = (body[body.size / 2] + 1).toByte() }
        val alpha = store.list().first { it.id == "save-a" }
        assertTrue(alpha.damaged)
        assertTrue(alpha.problem!!.contains("checksum"))
        try { store.read("save-a"); fail("a corrupted save loaded") } catch (e: SaveException) {
            assertTrue(e.message!!.contains("damaged"))
        }
        assertEquals("Beta loads fine", "Beta", store.read("save-b").info.name)
    }

    @Test fun aPartlyWrittenFileIsReportedNotLoaded() {
        val store = newStore()
        store.write("save-a", "Alpha", newGame(81))
        val f = store.file("save-a")
        f.writeBytes(f.readBytes().copyOf(f.length().toInt() / 2))
        val info = store.list().first { it.id == "save-a" }
        assertTrue(info.damaged)
        assertTrue(info.problem!!.contains("partly written"))
        try { store.read("save-a"); fail("a partial save loaded") } catch (_: SaveException) {}
    }

    @Test fun aFutureOrIncompatibleSaveSaysSo() {
        val store = newStore()
        val g = newGame(91)
        store.write("save-a", "Alpha", g)
        // The game payload's version number is its first four bytes, after the payload length.
        store.editBody("save-a", fixChecksum = true) { body -> java.nio.ByteBuffer.wrap(body, 4, 4).putInt(99) }
        try { store.read("save-a"); fail("an incompatible save loaded") } catch (e: SaveException) {
            assertTrue(e.message!!, e.message!!.contains("incompatible"))
        }
    }

    @Test fun junkFilesAreListedAsDamagedAndMissingSavesAreReported() {
        val store = newStore()
        store.file("save-junk").writeText("not a save at all")
        val junk = store.list().first { it.id == "save-junk" }
        assertTrue(junk.damaged)
        try { store.read("save-missing"); fail("a missing save loaded") } catch (e: SaveException) {
            assertTrue(e.message!!.contains("doesn't exist"))
        }
    }

    @Test fun deletingOneSaveLeavesTheOthersAlone() {
        val store = newStore()
        store.write(SaveStore.AUTOSAVE, "Autosave", newGame(101))
        store.write(SaveStore.QUICKSAVE, "Quicksave", newGame(102))
        store.write("save-a", "Alpha", newGame(103))
        store.write("save-b", "Beta", newGame(104))
        store.delete("save-a")
        assertFalse(store.exists("save-a"))
        assertTrue(store.exists("save-b"))
        assertTrue(store.exists(SaveStore.QUICKSAVE))
        assertTrue(store.exists(SaveStore.AUTOSAVE))
    }

    @Test fun anInterruptedWriteLeavesThePreviousSaveIntact() {
        val store = newStore()
        store.write("save-a", "Alpha", newGame(111))
        val before = store.file("save-a").readBytes()
        // A write that died halfway leaves only its temporary file.
        File(store.file("save-a").parentFile, "save-a.tmp").writeBytes(ByteArray(64) { 1 })
        assertEquals("Alpha", store.read("save-a").info.name)
        store.list()   // clears the leftover temporary file
        assertFalse(File(store.file("save-a").parentFile, "save-a.tmp").exists())
        assertTrue(before.contentEquals(store.file("save-a").readBytes()))
    }

    @Test fun archivedColoniesTravelWithTheirSave() {
        val store = newStore()
        val archive = SavedArchive(tile = 9, name = "Old Colony", bytes = newGame(121).let { SaveGame.write(it) })
        store.write("save-a", "Alpha", newGame(122), archives = listOf(archive))
        val back = store.read("save-a").archives
        assertEquals(1, back.size)
        assertEquals("Old Colony", back[0].name)
        assertEquals(9, back[0].tile)
        assertTrue(archive.bytes.contentEquals(back[0].bytes))
    }

    @Test fun aLoadedGameResumesTheGeneratorWhereItStopped() {
        val store = newStore()
        val g = newGame(131)
        g.run(1000)
        store.write("save-a", "Alpha", g)
        val loaded = store.read("save-a").game
        assertEquals(g.rng.state, loaded.rng.state)
    }
}
