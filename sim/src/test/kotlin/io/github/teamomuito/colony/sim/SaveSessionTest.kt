package io.github.teamomuito.colony.sim

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

private class Settings : SessionSettings {
    override var currentSlot: String? = null
    override var continueOrigin: String? = null
}

private class Fixture {
    val dir: File = Files.createTempDirectory("session").toFile()
    val store = SaveStore(dir)
    val settings = Settings()
    val session = SaveSession(store, settings)
    fun game(seed: Long, ticks: Int = 0): Game {
        val g = Game(seed)
        g.startNewColony(Scenario.LOST_TRIBE)
        repeat(ticks) { g.step() }
        return g
    }
    fun bytes(id: String) = store.file(id).readBytes()
}

private fun noColonies(): List<SavedArchive> = emptyList()

private fun Game.run(ticks: Int) { repeat(ticks) { step() } }

class PlayClockTest {
    @Test fun countsOnlyBetweenResumeAndPause() {
        val c = PlayClock()
        c.resume(0); c.pause(100)
        assertEquals(100L, c.totalMs(500))
        c.resume(200); c.pause(250)
        assertEquals("the time while paused (100..200) is not counted", 150L, c.totalMs(1_000))
    }

    @Test fun repeatedResumesAndPausesNeverCountTwice() {
        val c = PlayClock()
        c.resume(0); c.resume(50)           // a second resume while running changes nothing
        c.pause(100); c.pause(150)          // a second pause changes nothing
        assertEquals(100L, c.totalMs(200))
    }

    @Test fun aRunThatIsStillGoingIsCountedUpToNow() {
        val c = PlayClock(40)
        c.resume(1_000)
        assertEquals(40L + 300L, c.totalMs(1_300))
        assertTrue(c.running)
    }

    @Test fun aBackwardsClockAddsNothing() {
        val c = PlayClock()
        c.resume(500); c.pause(400)
        assertEquals(0L, c.totalMs(1_000))
    }

    @Test fun carriesTheAccruedTimeIntoANewClock() {
        val first = PlayClock(); first.resume(0); first.pause(100)
        val second = PlayClock(first.totalMs(100))
        second.resume(10_000); second.pause(10_050)
        assertEquals(150L, second.totalMs(20_000))
    }
}

class SaveSessionTest {
    @Test fun aNewGameIsContinuedAfterwardsAndTheReplacedAutosaveIsKept() {
        val f = Fixture()
        val old = f.game(1, ticks = 400)
        f.session.startNew(old, 0L, noColonies())
        val fresh = f.game(2, ticks = 50)
        f.session.startNew(fresh, 10L, noColonies())
        assertEquals("Continue is the new game", fresh.tick, f.store.readAutosave()!!.game.tick)
        assertEquals("the replaced game is the backup", old.tick, f.store.read(SaveStore.AUTOSAVE_BACKUP).game.tick)
        assertNull("a new game has no slot yet", f.settings.currentSlot)
    }

    @Test fun saveWritesOnlyTheGamesOwnSlotAndAsksForANameFirst() {
        val f = Fixture()
        val g = f.game(3, ticks = 100)
        f.session.startNew(g, 0L, noColonies())
        assertTrue(f.session.save(0L, noColonies()) is SaveOutcome.NeedsName)
        val alpha = f.session.saveAs("Alpha", 0L, noColonies())
        val beta = f.session.saveAs("Beta", 0L, noColonies())   // the game now belongs to Beta
        assertFalse(alpha == beta)
        val alphaBefore = f.bytes(alpha)
        val autoBefore = f.bytes(SaveStore.AUTOSAVE)
        f.session.game.run(300)
        val outcome = f.session.save(5L, noColonies()) as SaveOutcome.Saved
        assertEquals("Beta", outcome.name)
        assertArrayEquals("the other named save is untouched", alphaBefore, f.bytes(alpha))
        assertArrayEquals("the autosave is untouched by an explicit save", autoBefore, f.bytes(SaveStore.AUTOSAVE))
        assertEquals(f.session.game.tick, f.store.read(beta).game.tick)
    }

    @Test fun saveAsRefusesADuplicateNameAndWritesNothing() {
        val f = Fixture()
        f.session.startNew(f.game(4, 50), 0L, noColonies())
        f.session.saveAs("Alpha", 0L, noColonies())
        val before = f.dir.listFiles()!!.map { it.name to it.length() }.sortedBy { it.first }
        try { f.session.saveAs("ALPHA", 0L, noColonies()); fail("a duplicate name was accepted") } catch (e: SaveException) {
            assertTrue(e.message!!.contains("already exists"))
        }
        assertEquals(before, f.dir.listFiles()!!.map { it.name to it.length() }.sortedBy { it.first })
    }

    @Test fun aDamagedNamedSaveIsNotOverwrittenBySave() {
        val f = Fixture()
        f.session.startNew(f.game(5, 50), 0L, noColonies())
        f.session.saveAs("Alpha", 0L, noColonies())
        val id = f.settings.currentSlot!!
        f.store.file(id).writeBytes(ByteArray(12))
        val damaged = f.bytes(id)
        assertTrue("Save asks for a name rather than overwrite a damaged save", f.session.save(0L, noColonies()) is SaveOutcome.NeedsName)
        assertArrayEquals(damaged, f.bytes(id))
    }

    @Test fun quicksaveNeverTouchesNamedSavesOrTheSlotTheGameBelongsTo() {
        val f = Fixture()
        f.session.startNew(f.game(6, 60), 0L, noColonies())
        f.session.saveAs("Alpha", 0L, noColonies())
        val slot = f.settings.currentSlot
        val alphaBefore = f.bytes(slot!!)
        f.session.game.run(200)
        f.session.quicksave(1L, noColonies())
        f.session.game.run(100)
        f.session.quicksave(2L, noColonies())
        assertEquals("the quicksave holds the latest quicksave only", f.session.game.tick, f.store.read(SaveStore.QUICKSAVE).game.tick)
        assertEquals(slot, f.settings.currentSlot)
        assertArrayEquals(alphaBefore, f.bytes(slot))
    }

    @Test fun aLoadWithUnsavedProgressAsksFirstAndThenContinueFollowsIt() {
        val f = Fixture()
        val saved = f.game(7, 300)
        f.session.startNew(saved, 0L, noColonies())
        f.session.saveAs("Keep", 0L, noColonies())
        val target = f.settings.currentSlot!!
        val keptTick = saved.tick          // the tick the save holds; the game object keeps running below
        // Play on without saving, then start another game (its autosave is written) and play that without saving too.
        f.session.game.run(500)
        f.session.startNew(f.game(8, 100), 1L, noColonies())
        val replacedTick = f.session.game.tick   // the autosave the load will move into the backup
        f.session.game.run(40)
        val plan = f.session.planLoad(target)
        assertTrue("the progress since the last save would be lost", plan.discardsUnsaved)
        assertTrue("Continue would be replaced", plan.replacesContinue)
        assertTrue(plan.needsConfirmation)
        f.session.startLoad(plan, 2L, { noColonies() }, { })
        assertEquals("the loaded game is in play", keptTick, f.session.game.tick)
        assertEquals("Continue now resumes the loaded game", keptTick, f.store.readAutosave()!!.game.tick)
        assertEquals("the previous Continue is kept as the backup", replacedTick, f.store.read(SaveStore.AUTOSAVE_BACKUP).game.tick)
        assertEquals(target, f.settings.continueOrigin)
        assertFalse("loading the same save again does not ask to replace Continue", f.session.planLoad(target).replacesContinue)
    }

    @Test fun aFailedOrDamagedLoadChangesNothing() {
        val f = Fixture()
        f.session.startNew(f.game(9, 200), 0L, noColonies())
        f.session.saveAs("Bad", 0L, noColonies())
        val bad = f.settings.currentSlot!!
        f.session.game.run(100)
        val playing = f.session.game
        val autoBefore = f.bytes(SaveStore.AUTOSAVE)
        f.store.file(bad).writeBytes(ByteArray(20))
        try { f.session.planLoad(bad); fail("a damaged save was planned") } catch (_: SaveException) {}
        try { f.session.planLoad("save-missing"); fail("a missing save was planned") } catch (_: SaveException) {}
        assertSame("the game in play is the same game", playing, f.session.game)
        assertEquals(playing.tick, f.session.game.tick)
        assertArrayEquals("Continue is unchanged", autoBefore, f.bytes(SaveStore.AUTOSAVE))
    }

    @Test fun continueRecoversTheLastGoodAutosaveWhenTheCurrentOneIsDamaged() {
        val f = Fixture()
        val first = f.game(10, 100)
        f.session.startNew(first, 0L, noColonies())
        val second = f.game(10, 900)
        f.session.startNew(second, 1L, noColonies())
        f.store.file(SaveStore.AUTOSAVE).writeBytes(ByteArray(30))
        val loaded = f.session.continueSession(2L)!!
        assertTrue(loaded.recoveredFromBackup)
        assertEquals(first.tick, f.session.game.tick)
    }

    @Test fun aFinishedGameIsNotSavedToASlotAndItsAutosaveIsRemoved() {
        val f = Fixture()
        val g = f.game(11, 200)
        f.session.startNew(g, 0L, noColonies())
        g.gameOver = true
        try { f.session.saveAs("Late", 0L, noColonies()); fail("a finished game was saved") } catch (_: SaveException) {}
        f.session.autosave(1L, noColonies())
        assertFalse("nothing is left to continue", f.store.exists(SaveStore.AUTOSAVE))
    }

    @Test fun returningToTheMenuSavesTheGameAndItsPlayTime() {
        val f = Fixture()
        f.session.begin(f.game(12, 100), 1_000L, slot = null)
        f.session.autosave(1_100L, noColonies())  // while running: 100 ms so far
        f.session.pause(1_300L)
        f.session.autosave(1_300L, noColonies())
        assertEquals(300L, f.store.readAutosave()!!.info.playtimeMs)
    }

    // ---------------------------------------------------------------- play time across saves and loads

    @Test fun playTimeIsNotCountedTwiceAcrossASaveAndALoad() {
        val f = Fixture()
        f.session.begin(f.game(13, 100), 1_000L, slot = null)
        f.session.saveAs("Timed", 1_100L, noColonies())            // 100 ms of play, saved while running
        f.session.game.run(10)
        f.session.pause(1_150L)                                    // 50 more
        assertEquals(150L, f.session.game.playMs)
        val plan = f.session.planLoad(f.settings.currentSlot!!)
        f.session.startLoad(plan, 9_000L, { noColonies() }, { })  // time in the menu is not counted
        assertEquals("the loaded game carries its 100 ms of play, not the 150 ms of the discarded session", 100L, f.session.game.playMs)
        f.session.pause(9_060L)                                    // 60 ms of play after the load
        assertEquals(160L, f.session.game.playMs)
        f.session.autosave(9_060L, noColonies())
        assertEquals(160L, f.store.readAutosave()!!.info.playtimeMs)
    }

    @Test fun backgroundedTimeIsNotCounted() {
        val f = Fixture()
        f.session.begin(f.game(14, 10), 0L, slot = null)
        f.session.pause(100L)            // the app goes to the background
        f.session.pause(5_000L)          // still backgrounded: nothing counts
        f.session.resume(6_000L)         // back on screen
        f.session.pause(6_050L)
        assertEquals(150L, f.session.game.playMs)
    }
}
