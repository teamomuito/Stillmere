package io.github.teamomuito.colony.sim

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32

/** Something a player can be told about a save: why it can't be loaded, or what it contains. */
class SaveException(message: String) : Exception(message)

/** A save slot as the save list shows it. [problem] is set when the file can't be loaded. */
data class SaveInfo(
    val id: String,
    val name: String,
    val savedAtMillis: Long,
    val playtimeMs: Long,
    val colonyName: String,
    val day: Int,
    val colonists: Int,
    val tick: Long,
    val problem: String? = null,
) {
    val damaged get() = problem != null
    /** Autosave and quicksave are not named by the player, so the list groups them apart from named saves. */
    val isNamed get() = id.startsWith(SaveStore.NAMED_PREFIX)
}

/** A settled colony archived with a save. It is written as it was when it was left. */
class SavedArchive(val tile: Int, val name: String, val bytes: ByteArray)

/** A save that was read back: the game, plus the settled colonies that belong with it. */
class LoadedSave(val info: SaveInfo, val game: Game, val archives: List<SavedArchive>)

/**
 * Save slots on disk. Each slot is one file: a header the list reads without loading the game, then a checksummed body
 * holding the game and any archived colonies.
 *
 * Writes go to a temporary file that is read back and checked before it replaces the slot, and the replacement is a
 * single rename. So an interrupted or failed save leaves the previous save as it was. Slots are never overwritten
 * unless the caller names them: [write] refuses a new id that is already taken.
 */
class SaveStore(private val dir: File) {
    init { dir.mkdirs() }

    companion object {
        const val AUTOSAVE = "autosave"
        const val QUICKSAVE = "quicksave"
        /** Named saves get ids with this prefix, so they can never collide with the fixed slots. */
        const val NAMED_PREFIX = "save-"
        const val MAX_NAME = 40
        private const val MAGIC = 0x434F4C53      // "COLS"
        private const val CONTAINER = 1
        private val ID = Regex("[a-z0-9-]{1,64}")
        private val RESERVED_NAMES = setOf("autosave", "quicksave")
    }

    fun file(id: String): File {
        require(ID.matches(id)) { "bad save id" }
        return File(dir, "$id.sav")
    }

    fun exists(id: String) = file(id).exists()

    /** A new, unused id for a named save. */
    fun newNamedId(): String {
        var id: String
        do id = NAMED_PREFIX + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        while (exists(id))
        return id
    }

    /** The name the player typed, cleaned up. Throws with the reason when it can't be used. */
    fun cleanName(raw: String): String {
        val n = raw.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty()) throw SaveException("Give the save a name.")
        if (n.length > MAX_NAME) throw SaveException("Names can be at most $MAX_NAME characters.")
        if (n.any { it.isISOControl() }) throw SaveException("Names can't contain control characters.")
        if (n.lowercase() in RESERVED_NAMES) throw SaveException("\"$n\" is reserved. Choose another name.")
        return n
    }

    /** Throws if another named save already has this name (ignoring letter case). [exceptId] is the save being renamed or overwritten. */
    fun ensureUniqueName(name: String, exceptId: String? = null) {
        val clash = list().firstOrNull { it.isNamed && it.id != exceptId && it.name.equals(name, ignoreCase = true) }
        if (clash != null) throw SaveException("A save named \"${clash.name}\" already exists. Choose another name.")
    }

    /**
     * Writes [game] to slot [id]. A new id must be free; an existing id is replaced only when [replace] is set, so the
     * caller has to choose to overwrite. Throws [SaveException] and leaves the old file alone on any failure.
     */
    fun write(
        id: String, name: String, game: Game, archives: List<SavedArchive> = emptyList(),
        playtimeMs: Long = 0L, replace: Boolean = false, nowMillis: Long = System.currentTimeMillis(),
    ) {
        val target = file(id)
        if (target.exists() && !replace) throw SaveException("That save already exists.")
        val bytes = try { encode(game, name, archives, playtimeMs, nowMillis) } catch (e: Exception) {
            throw SaveException("The game couldn't be saved: ${e.message ?: e.javaClass.simpleName}")
        }
        val tmp = File(dir, "$id.tmp")
        try {
            FileOutputStream(tmp).use { out -> out.write(bytes); out.flush(); out.fd.sync() }
            // Read back what was written before it replaces the save, so a bad write is never the only copy.
            decode(id, tmp.readBytes())
            if (!tmp.renameTo(target)) throw SaveException("The save couldn't be written to storage.")
        } catch (e: SaveException) {
            tmp.delete(); throw e
        } catch (e: Exception) {
            tmp.delete(); throw SaveException("The save couldn't be written to storage.")
        }
    }

    /** Every save slot, damaged ones included. Also clears temporary files an interrupted write left behind. */
    fun list(): List<SaveInfo> {
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
        val out = ArrayList<SaveInfo>()
        for (f in dir.listFiles { f -> f.name.endsWith(".sav") } ?: emptyArray()) {
            val id = f.name.removeSuffix(".sav")
            if (!ID.matches(id)) continue
            out += try { decode(id, f.readBytes()).info } catch (e: SaveException) { damagedInfo(id, e.message ?: "Damaged") }
        }
        return out.sortedWith(compareBy<SaveInfo> { it.id != AUTOSAVE }.thenBy { it.id != QUICKSAVE }.thenByDescending { it.savedAtMillis })
    }

    /** Loads slot [id]. Throws [SaveException] saying why it can't be loaded. */
    fun read(id: String): LoadedSave {
        val f = file(id)
        if (!f.exists()) throw SaveException("That save doesn't exist anymore.")
        return decode(id, f.readBytes())
    }

    /** Deletes one slot. Callers ask the player first; this never touches any other slot. */
    fun delete(id: String) {
        val f = file(id)
        if (f.exists() && !f.delete()) throw SaveException("The save couldn't be deleted.")
    }

    // ------------------------------------------------------------------ format

    private fun encode(game: Game, name: String, archives: List<SavedArchive>, playtimeMs: Long, nowMillis: Long): ByteArray {
        val payload = SaveGame.write(game)
        val body = ByteArrayOutputStream()
        DataOutputStream(body).apply {
            writeInt(payload.size); write(payload)
            writeInt(archives.size)
            for (a in archives) { writeInt(a.tile); writeUTF(a.name); writeInt(a.bytes.size); write(a.bytes) }
            flush()
        }
        val bodyBytes = body.toByteArray()
        val crc = CRC32().apply { update(bodyBytes) }.value
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(MAGIC); writeInt(CONTAINER)
            writeUTF(name); writeLong(nowMillis); writeLong(playtimeMs)
            writeUTF(game.colonyName); writeInt(game.day); writeInt(game.colonists.size); writeLong(game.tick)
            writeInt(bodyBytes.size); writeLong(crc)
            write(bodyBytes)
            flush()
        }
        return out.toByteArray()
    }

    /** Checks the header, length and checksum, then reads the game. Throws [SaveException] with the reason on failure. */
    private fun decode(id: String, bytes: ByteArray): LoadedSave {
        val info: SaveInfo
        val body: ByteArray
        try {
            val h = DataInputStream(ByteArrayInputStream(bytes))
            if (h.readInt() != MAGIC) throw SaveException("This file isn't a save.")
            val container = h.readInt()
            if (container > CONTAINER) throw SaveException("This save is from a newer version of the game.")
            val name = h.readUTF(); val savedAt = h.readLong(); val playtime = h.readLong()
            val colony = h.readUTF(); val day = h.readInt(); val colonists = h.readInt(); val tick = h.readLong()
            val bodyLen = h.readInt(); val crc = h.readLong()
            val headerEnd = bytes.size - h.available()
            if (bytes.size - headerEnd != bodyLen) throw SaveException("This save was only partly written.")
            body = bytes.copyOfRange(headerEnd, bytes.size)
            if (CRC32().apply { update(body) }.value != crc) throw SaveException("This save is damaged (its checksum doesn't match).")
            info = SaveInfo(id, name, savedAt, playtime, colony, day, colonists, tick)
        } catch (e: SaveException) {
            throw e
        } catch (e: Exception) {
            throw SaveException("This save is damaged or was only partly written.")
        }
        val b = DataInputStream(ByteArrayInputStream(body))
        val payload: ByteArray
        val archives = ArrayList<SavedArchive>()
        try {
            payload = ByteArray(b.readInt()).also { b.readFully(it) }
            repeat(b.readInt()) { archives += SavedArchive(b.readInt(), b.readUTF(), ByteArray(b.readInt()).also { a -> b.readFully(a) }) }
        } catch (e: Exception) {
            throw SaveException("This save is damaged.")
        }
        val game = try { SaveGame.read(payload) } catch (e: IllegalArgumentException) {
            if (e.message?.contains("version") == true) throw SaveException("This save is from an incompatible version of the game.")
            throw SaveException("This save is damaged.")
        } catch (e: Exception) {
            throw SaveException("This save is damaged.")
        }
        return LoadedSave(info, game, archives)
    }

    private fun damagedInfo(id: String, reason: String) =
        SaveInfo(id, if (id == AUTOSAVE) "Autosave" else if (id == QUICKSAVE) "Quicksave" else id, 0L, 0L, "", 0, 0, 0L, reason)
}
