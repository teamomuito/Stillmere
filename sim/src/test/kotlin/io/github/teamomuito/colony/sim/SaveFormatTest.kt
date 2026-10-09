package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.CRC32

private fun game(seed: Long, ticks: Int = 0): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    repeat(ticks) { g.step() }
    return g
}

private fun newDir(): File = Files.createTempDirectory("savefmt").toFile()

private fun colonies(n: Int, ticks: Int = 0): List<SavedArchive> =
    (1..n).map { SavedArchive(it, "Colony $it", SaveGame.write(game(500L + it, ticks))) }

private fun blobCount(dir: File) = File(dir, "archives").listFiles { f -> f.name.endsWith(".arc") }?.size ?: 0

/** Writes a slot in the old container 1 layout: colonies inside the save, uncompressed. */
private fun writeContainerOne(file: File, g: Game, archives: List<SavedArchive>) {
    val payload = SaveGame.write(g)
    val body = ByteArrayOutputStream().also { b ->
        DataOutputStream(b).apply {
            writeInt(payload.size); write(payload)
            writeInt(archives.size)
            for (a in archives) { writeInt(a.tile); writeUTF(a.name); writeInt(a.bytes.size); write(a.bytes) }
            flush()
        }
    }.toByteArray()
    val crc = CRC32().apply { update(body) }.value
    DataOutputStream(FileOutputStream(file)).use { o ->
        o.writeInt(0x434F4C53); o.writeInt(1)
        o.writeUTF("Old save"); o.writeLong(1_000L); o.writeLong(5_000L)
        o.writeUTF(g.colonyName); o.writeInt(g.day); o.writeInt(g.colonists.size); o.writeLong(g.tick)
        o.writeInt(body.size); o.writeLong(crc)
        o.write(body)
    }
}

class SaveFormatTest {
    @Test fun theAutosaveDoesNotGrowWithTheNumberOfSettledColonies() {
        val dir = newDir()
        val store = SaveStore(dir)
        val g = game(601, ticks = 2000)
        store.write(SaveStore.AUTOSAVE, "Autosave", g)
        val alone = store.file(SaveStore.AUTOSAVE).length()
        store.write(SaveStore.AUTOSAVE, "Autosave", g, colonies(5), replace = true)
        val withFive = store.file(SaveStore.AUTOSAVE).length()
        assertTrue("five colonies add only their references ($alone -> $withFive bytes)", withFive - alone < 1_000)
    }

    @Test fun aColonyInSeveralSavesIsStoredOnce() {
        val dir = newDir()
        val store = SaveStore(dir)
        val shared = colonies(3)
        store.write(SaveStore.AUTOSAVE, "Autosave", game(602), shared)
        store.write("save-one", "One", game(603), shared)
        store.write("save-two", "Two", game(604), shared)
        assertEquals("three colonies, three blobs, whatever the number of saves", 3, blobCount(dir))
        assertEquals(shared.map { it.name }, store.read("save-two").archives.map { it.name })
    }

    @Test fun blobsNoSaveRefersToAnyMoreAreRemovedButSharedOnesStay() {
        val dir = newDir()
        val store = SaveStore(dir)
        val all = colonies(2)
        store.write("save-a", "A", game(605), all)
        store.write(SaveStore.AUTOSAVE, "Autosave", game(606), listOf(all[0]))
        assertEquals(2, blobCount(dir))
        store.delete("save-a")
        assertEquals("the colony the autosave still uses is kept", 1, blobCount(dir))
        assertEquals(1, store.read(SaveStore.AUTOSAVE).archives.size)
        store.delete(SaveStore.AUTOSAVE)
        assertEquals(0, blobCount(dir))
    }

    @Test fun aDamagedColonyIsReportedAndTheSaveIsNotLost() {
        val dir = newDir()
        val store = SaveStore(dir)
        store.write(SaveStore.AUTOSAVE, "Autosave", game(607), colonies(1))
        val blob = File(dir, "archives").listFiles()!!.first { it.name.endsWith(".arc") }
        blob.writeBytes(ByteArray(40) { 7 })
        try { store.read(SaveStore.AUTOSAVE); fail("a damaged colony loaded") } catch (e: SaveException) {
            assertTrue(e.message!!, e.message!!.contains("settled colony"))
        }
        assertTrue("the save file itself is still there", store.exists(SaveStore.AUTOSAVE))
    }

    @Test fun aDamagedAutosaveFallsBackToThePreviousOne() {
        val dir = newDir()
        val store = SaveStore(dir)
        val first = game(608, ticks = 500)
        store.write(SaveStore.AUTOSAVE, "Autosave", first)
        val second = game(608, ticks = 1500)
        store.write(SaveStore.AUTOSAVE, "Autosave", second, replace = true)
        val f = store.file(SaveStore.AUTOSAVE)
        f.writeBytes(f.readBytes().copyOf(f.length().toInt() / 3))   // a partly written autosave
        val recovered = store.readAutosave()!!
        assertTrue(recovered.recoveredFromBackup)
        assertEquals("the last good autosave", first.tick, recovered.game.tick)
        assertFalse("the backup never shows in the save list", store.list().any { it.id == SaveStore.AUTOSAVE_BACKUP })
    }

    @Test fun anInterruptedAutosaveReplacementStillRecoversTheLatestGoodCopy() {
        val dir = newDir()
        val store = SaveStore(dir)
        val first = game(609, ticks = 300)
        store.write(SaveStore.AUTOSAVE, "Autosave", first)
        store.write(SaveStore.AUTOSAVE, "Autosave", game(609, ticks = 900), replace = true)
        // The process died after moving the old autosave aside and before the new one was in place.
        store.file(SaveStore.AUTOSAVE).delete()
        assertEquals(first.tick, store.readAutosave()!!.game.tick)
    }

    @Test fun noAutosaveAtAllIsNotAnError() {
        assertNull(SaveStore(newDir()).readAutosave())
    }

    @Test fun aSaveWithDamagedAndMissingBothCopiesReportsRatherThanInventing() {
        val dir = newDir()
        val store = SaveStore(dir)
        store.write(SaveStore.AUTOSAVE, "Autosave", game(610))
        store.write(SaveStore.AUTOSAVE, "Autosave", game(610, 100), replace = true)
        store.file(SaveStore.AUTOSAVE).writeBytes(ByteArray(10))
        store.file(SaveStore.AUTOSAVE_BACKUP).writeBytes(ByteArray(10))
        try { store.readAutosave(); fail("invented an autosave") } catch (_: SaveException) {}
    }

    @Test fun aVersionOneSaveStillLoadsAndIsUpgradedOnTheNextWrite() {
        val dir = newDir()
        val store = SaveStore(dir)
        val old = game(611, ticks = 400)
        val cols = colonies(2)
        writeContainerOne(store.file("save-old"), old, cols)
        val loaded = store.read("save-old")
        assertEquals(old.tick, loaded.game.tick)
        assertEquals(cols.map { it.name }, loaded.archives.map { it.name })
        assertTrue(cols.zip(loaded.archives).all { (a, b) -> a.bytes.contentEquals(b.bytes) })
        assertEquals(5_000L, loaded.info.playtimeMs)

        store.write("save-old", "Old save", loaded.game, loaded.archives, replace = true)
        val container = java.io.DataInputStream(store.file("save-old").inputStream()).use { it.readInt(); it.readInt() }
        assertEquals("the next write uses the current layout", 2, container)
        assertEquals(cols.map { it.name }, store.read("save-old").archives.map { it.name })
    }

    @Test fun playTimeIsWrittenWithTheSaveAndListed() {
        val dir = newDir()
        val store = SaveStore(dir)
        val g = game(612)
        g.playMs = 3_600_000L
        store.write("save-time", "Timed", g)
        assertEquals(3_600_000L, store.list().first { it.id == "save-time" }.playtimeMs)
    }
}
