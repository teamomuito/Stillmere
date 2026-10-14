package io.github.teamomuito.colony.sim

/**
 * Play time for a session. It counts only between [resume] and [pause], so time spent backgrounded or paused is not
 * counted. Times are on a monotonic clock supplied by the caller (milliseconds since boot), so a changed wall clock
 * does not change play time. Repeated resumes or pauses do nothing extra, so no time is counted twice.
 */
class PlayClock(accruedMs: Long = 0L) {
    private var accrued = accruedMs.coerceAtLeast(0L)
    private var since = NOT_RUNNING

    val running get() = since != NOT_RUNNING

    fun resume(nowMs: Long) { if (!running) since = nowMs }

    fun pause(nowMs: Long) {
        if (!running) return
        accrued += (nowMs - since).coerceAtLeast(0L)
        since = NOT_RUNNING
    }

    /** Everything counted so far, including the part of the current run that has not been paused yet. */
    fun totalMs(nowMs: Long): Long = accrued + if (running) (nowMs - since).coerceAtLeast(0L) else 0L

    private companion object { const val NOT_RUNNING = Long.MIN_VALUE }
}

/** What the session has to remember between launches. The app keeps these in its preferences. */
interface SessionSettings {
    /** The named save the game in play belongs to: what Save writes to. Null until it has one. */
    var currentSlot: String?
    /** The save the autosave was loaded from, so loading the same save again does not ask to replace Continue. */
    var continueOrigin: String?
}

/**
 * A load that has been checked and is ready. [discardsUnsaved] is set when the game in play has progress that no save
 * holds. [replacesContinue] is set when the autosave, which Continue resumes, is a different game from the one loaded.
 */
class LoadPlan(val target: String, val loaded: LoadedSave, val discardsUnsaved: Boolean, val replacesContinue: Boolean) {
    val needsConfirmation get() = discardsUnsaved || replacesContinue
}

/** The result of Save: either written to the game's own save, or the player must choose a name first. */
sealed class SaveOutcome {
    class Saved(val name: String) : SaveOutcome()
    object NeedsName : SaveOutcome()
}

/**
 * The save flows, with the game in play: which game is running, which slot it belongs to, where Continue points, what
 * progress is unsaved, and how play time is counted. The app asks, confirms with the player, and calls these; every
 * decision that changes what is saved is made here.
 *
 * Rules the flows keep:
 *  - Only an explicit save writes a named slot or the quicksave. The autosave is written by the session's own
 *    transitions and on leaving the game; it never replaces a named save.
 *  - Writing the autosave keeps the previous one as the recovery copy (see [SaveStore.readAutosave]).
 *  - Before a load replaces the game in play, the target is read and checked. A failed or damaged load changes nothing.
 */
class SaveSession(private val store: SaveStore, private val settings: SessionSettings) {
    /** The game in play. Set by [begin]. */
    lateinit var game: Game
        private set
    private var clock = PlayClock()
    private var savedTick = -1L

    /** Progress since the last explicit save or load. */
    fun hasUnsavedProgress(): Boolean = this::game.isInitialized && game.tick != savedTick

    /** Play time for this game so far, in milliseconds. */
    fun playTimeMs(nowMs: Long): Long = clock.totalMs(nowMs)

    /** Starts playing [g], which belongs to [slot] (null for a new game). Counting starts from the time the game has. */
    fun begin(g: Game, nowMs: Long, slot: String?) {
        game = g
        clock = PlayClock(g.playMs)
        clock.resume(nowMs)
        savedTick = g.tick
        settings.currentSlot = slot
    }

    /**
     * Moves play to another game in the same session (a battle map, a colony switch, a settled colony). Play time carries
     * on from this session, the named save stays linked, and the change counts as unsaved until the player saves.
     */
    fun swapInPlay(g: Game, nowMs: Long) {
        val total = clock.totalMs(nowMs)
        clock.pause(nowMs)
        game = g
        g.playMs = total
        clock = PlayClock(total)
        clock.resume(nowMs)
        savedTick = -1L
    }

    /** The game is leaving the screen: stop counting and record the time in the game. */
    fun pause(nowMs: Long) {
        clock.pause(nowMs)
        game.playMs = clock.totalMs(nowMs)
    }

    fun resume(nowMs: Long) = clock.resume(nowMs)

    /** Writes the autosave: the Continue game. A finished game has nothing to continue, so its autosave is removed. */
    fun autosave(nowMs: Long, archives: List<SavedArchive>) {
        game.playMs = clock.totalMs(nowMs)
        if (game.gameOver) { store.delete(SaveStore.AUTOSAVE); return }
        store.write(SaveStore.AUTOSAVE, "Autosave", game, archives, game.playMs, replace = true, nowMillis = nowMs)
    }

    /** Save: writes to the game's own named save. Without one, the player has to choose a name. */
    fun save(nowMs: Long, archives: List<SavedArchive>): SaveOutcome {
        requireNotFinished()
        val id = settings.currentSlot?.takeIf { store.exists(it) && !damaged(it) } ?: return SaveOutcome.NeedsName
        val name = store.list().first { it.id == id }.name
        writeNamed(id, name, nowMs, archives, replace = true)
        return SaveOutcome.Saved(name)
    }

    /** Save as: a new named save with a name that is not used by another save. Returns the new slot's id. */
    fun saveAs(rawName: String, nowMs: Long, archives: List<SavedArchive>): String {
        requireNotFinished()
        val name = store.cleanName(rawName)
        store.ensureUniqueName(name)
        val id = store.newNamedId()
        writeNamed(id, name, nowMs, archives, replace = false)
        settings.currentSlot = id
        return id
    }

    /** Quicksave: replaces only the previous quicksave. Named saves and the autosave are not touched. */
    fun quicksave(nowMs: Long, archives: List<SavedArchive>) {
        requireNotFinished()
        writeNamed(SaveStore.QUICKSAVE, "Quicksave", nowMs, archives, replace = true)
    }

    /**
     * Checks a load before anything changes. Throws [SaveException] if the target can't be read, and leaves the game
     * in play and the autosave exactly as they were.
     */
    fun planLoad(target: String): LoadPlan {
        val loaded = store.read(target)
        val replacesContinue = store.exists(SaveStore.AUTOSAVE) && settings.continueOrigin != target
        return LoadPlan(target, loaded, discardsUnsaved = hasUnsavedProgress(), replacesContinue = replacesContinue)
    }

    /**
     * Starts the planned load. The previous autosave is kept as the recovery copy and the loaded game becomes the new
     * autosave straight away, so Continue points at it from now on.
     */
    fun startLoad(plan: LoadPlan, nowMs: Long, archives: () -> List<SavedArchive>, restoreArchives: (List<SavedArchive>) -> Unit) {
        restoreArchives(plan.loaded.archives)
        begin(plan.loaded.game, nowMs, plan.target)
        settings.continueOrigin = plan.target
        autosave(nowMs, archives())
    }

    /** Continue: the autosave, or the previous one if the autosave can't be read. Null when there is no autosave. */
    fun continueSession(nowMs: Long): LoadedSave? {
        val loaded = store.readAutosave() ?: return null
        begin(loaded.game, nowMs, slot = settings.currentSlot?.takeIf { store.exists(it) })
        settings.continueOrigin = null
        return loaded
    }

    /** A new game. Its autosave is written at once, so Continue points at it and the replaced autosave becomes the backup. */
    fun startNew(g: Game, nowMs: Long, archives: List<SavedArchive>) {
        begin(g, nowMs, slot = null)
        settings.continueOrigin = null
        autosave(nowMs, archives)
    }

    private fun requireNotFinished() {
        if (game.gameOver) throw SaveException("A finished game can't be saved to a slot.")
    }

    private fun damaged(id: String) = store.list().firstOrNull { it.id == id }?.damaged ?: true

    private fun writeNamed(id: String, name: String, nowMs: Long, archives: List<SavedArchive>, replace: Boolean) {
        game.playMs = clock.totalMs(nowMs)
        store.write(id, name, game, archives, game.playMs, replace = replace, nowMillis = nowMs)
        savedTick = game.tick
        if (id != SaveStore.QUICKSAVE) settings.currentSlot = id
    }
}
