package io.github.teamomuito.colony.sim

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

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

/**
 * A save that was read back: the game, plus the settled colonies that belong with it. [recoveredFromBackup] is set when
 * the autosave itself could not be read and the previous autosave was used instead.
 */
class LoadedSave(val info: SaveInfo, val game: Game, val archives: List<SavedArchive>, val recoveredFromBackup: Boolean = false)

/**
 * Save slots on disk.
 *
 * A slot is one file: a header the list reads without loading the game, then a checksummed, compressed body holding the
 * game. Settled colonies are not copied into every save. Each one is stored once, as a content-addressed blob named by
 * the hash of its bytes, and a save only refers to them by hash. So an autosave writes the same size no matter how many
 * colonies were settled, and a colony that is in several saves is stored once. Blobs no save refers to are removed after
 * a successful write or delete.
 *
 * Writes go to a temporary file that is read back and checked before it replaces the slot, and the replacement is a
 * single rename. Replacing the autosave first moves the previous autosave aside, so there is always a last good copy:
 * if the current autosave can't be read, [readAutosave] uses that one. Slots are never overwritten unless the caller
 * names them: [write] refuses a new id that is already taken.
 */
class SaveStore(private val dir: File) {
    init { dir.mkdirs() }

    companion object {
        const val AUTOSAVE = "autosave"
        const val QUICKSAVE = "quicksave"
        /** The autosave before the current one. Kept for recovery; it never shows in the save list. */
        const val AUTOSAVE_BACKUP = "autosave.prev"
        /** Named saves get ids with this prefix, so they can never collide with the fixed slots. */
        const val NAMED_PREFIX = "save-"
        const val MAX_NAME = 40
        private const val MAGIC = 0x434F4C53      // "COLS"
        /** 1: settled colonies inside every save, uncompressed. 2: colonies as shared blobs, compressed bodies. */
        private const val CONTAINER = 2
        private val ID = Regex("[a-z0-9-]{1,64}")
        private val RESERVED_NAMES = setOf("autosave", "quicksave")
    }

    private val archiveDir = File(dir, "archives")

    /** The file for a slot id. Ids are checked, so a caller can't reach outside the save folder. */
    fun file(id: String): File {
        require(ID.matches(id) || id == AUTOSAVE_BACKUP) { "bad save id" }
        return File(dir, "$id.sav")
    }

    fun exists(id: String) = file(id).exists()

    /**
     * A new, unused id for a named save: the lowest free number after [NAMED_PREFIX]. Counting up instead of drawing a
     * random id keeps all randomness out of the sim module; ids only need to be unique among the files on disk.
     */
    fun newNamedId(): String {
        var n = 1
        while (exists(NAMED_PREFIX + n)) n++
        return NAMED_PREFIX + n
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
        playtimeMs: Long = game.playMs, replace: Boolean = false,
        /** The caller's clock (SaveSession passes its monotonic time). The sim reads no clock itself; 0 when none is given. */
        nowMillis: Long = 0L,
    ) {
        val target = file(id)
        if (target.exists() && !replace) throw SaveException("That save already exists.")
        val refs = archives.map { a -> ArchiveRef(a.tile, a.name, hash(a.bytes)) }
        val bytes = try {
            archives.forEachIndexed { i, a -> ensureBlob(refs[i].hash, a.bytes) }
            encode(game, name, refs, playtimeMs, nowMillis)
        } catch (e: SaveException) {
            throw e
        } catch (e: Exception) {
            throw SaveException("The game couldn't be saved: ${e.message ?: e.javaClass.simpleName}")
        }
        val tmp = File(dir, "$id.tmp")
        try {
            FileOutputStream(tmp).use { out -> out.write(bytes); out.flush(); out.fd.sync() }
            // Read back what was written before it replaces the save, so a bad write is never the only copy.
            decode(id, tmp.readBytes())
            if (id == AUTOSAVE && target.exists()) {
                // Keep the previous autosave as the recovery copy. If the process dies between the two renames, the
                // backup is what readAutosave falls back to.
                val backup = file(AUTOSAVE_BACKUP)
                if (backup.exists() && !backup.delete()) throw SaveException("The save couldn't be written to storage.")
                if (!target.renameTo(backup)) throw SaveException("The save couldn't be written to storage.")
            }
            if (!tmp.renameTo(target)) throw SaveException("The save couldn't be written to storage.")
        } catch (e: SaveException) {
            tmp.delete(); throw e
        } catch (e: Exception) {
            tmp.delete(); throw SaveException("The save couldn't be written to storage.")
        }
        collectArchives()
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

    /**
     * The autosave, or the previous one if the autosave can't be read. Null when there is no autosave at all. Throws if
     * there is one but neither copy can be read; nothing is deleted in that case.
     */
    fun readAutosave(): LoadedSave? {
        if (!exists(AUTOSAVE) && !exists(AUTOSAVE_BACKUP)) return null
        var failure: SaveException? = null
        if (exists(AUTOSAVE)) {
            try { return read(AUTOSAVE) } catch (e: SaveException) { failure = e }
        }
        if (exists(AUTOSAVE_BACKUP)) {
            try {
                val back = decode(AUTOSAVE, file(AUTOSAVE_BACKUP).readBytes())
                return LoadedSave(back.info, back.game, back.archives, recoveredFromBackup = true)
            } catch (e: SaveException) {
                if (failure == null) failure = e
            }
        }
        throw failure ?: SaveException("There is no autosave.")
    }

    /** Deletes one slot. Callers ask the player first; this never touches any other slot. */
    fun delete(id: String) {
        val f = file(id)
        if (f.exists() && !f.delete()) throw SaveException("The save couldn't be deleted.")
        collectArchives()
    }

    // ------------------------------------------------------------------ archive blobs

    private class ArchiveRef(val tile: Int, val name: String, val hash: String)

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun blobFile(hash: String) = File(archiveDir, "$hash.arc")

    /** Stores a colony once. An existing blob is already the same bytes, so it is left alone. */
    private fun ensureBlob(hash: String, bytes: ByteArray) {
        val f = blobFile(hash)
        if (f.exists()) return
        archiveDir.mkdirs()
        val tmp = File(archiveDir, "$hash.tmp")
        val packed = ByteArrayOutputStream().also { raw -> DeflaterOutputStream(raw).use { it.write(bytes) } }.toByteArray()
        FileOutputStream(tmp).use { out -> out.write(packed); out.flush(); out.fd.sync() }
        if (!tmp.renameTo(f)) { tmp.delete(); throw SaveException("The save couldn't be written to storage.") }
    }

    private fun readBlob(ref: ArchiveRef): ByteArray {
        val f = blobFile(ref.hash)
        val bytes = try { InflaterInputStream(f.inputStream()).use { it.readBytes() } } catch (e: Exception) {
            throw SaveException("A settled colony in this save is missing or damaged.")
        }
        if (hash(bytes) != ref.hash) throw SaveException("A settled colony in this save is damaged.")
        return bytes
    }

    /** Removes colony blobs that no save refers to any more. Runs after every write and delete. */
    private fun collectArchives() {
        if (!archiveDir.isDirectory) return
        val used = HashSet<String>()
        for (f in dir.listFiles { f -> f.name.endsWith(".sav") } ?: emptyArray()) {
            try { headerOf(f.readBytes())?.refs?.forEach { used += it.hash } } catch (_: Exception) {}
        }
        for (f in archiveDir.listFiles { f -> f.name.endsWith(".arc") } ?: emptyArray()) {
            if (f.name.removeSuffix(".arc") !in used) f.delete()
        }
        archiveDir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    // ------------------------------------------------------------------ format

    private class Header(
        val info: SaveInfo, val container: Int, val bodyLen: Int, val crc: Long,
        val refs: List<ArchiveRef>, val bodyStart: Int,
    )

    /** Reads only the header, so a save can be listed and its colonies found without loading the game. */
    private fun headerOf(bytes: ByteArray): Header? = try {
        parseHeader("", bytes)
    } catch (e: Exception) { null }

    private fun encode(game: Game, name: String, refs: List<ArchiveRef>, playtimeMs: Long, nowMillis: Long): ByteArray {
        val payload = SaveGame.write(game)
        val compressed = ByteArrayOutputStream().also { raw ->
            DeflaterOutputStream(raw).use { z -> DataOutputStream(z).apply { writeInt(payload.size); write(payload); flush() } }
        }.toByteArray()
        val crc = CRC32().apply { update(compressed) }.value
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(MAGIC); writeInt(CONTAINER)
            writeUTF(name); writeLong(nowMillis); writeLong(playtimeMs)
            writeUTF(game.colonyName); writeInt(game.day); writeInt(game.colonists.size); writeLong(game.tick)
            writeInt(compressed.size); writeLong(crc)
            writeInt(refs.size)
            for (r in refs) { writeInt(r.tile); writeUTF(r.name); writeUTF(r.hash) }
            write(compressed)
            flush()
        }
        return out.toByteArray()
    }

    /** Checks the header, length and checksum, then reads the game and its colonies. Throws [SaveException] with the reason. */
    private fun decode(id: String, bytes: ByteArray): LoadedSave {
        val h = try { parseHeader(id, bytes) } catch (e: SaveException) { throw e } catch (e: Exception) {
            throw SaveException("This save is damaged or was only partly written.")
        }
        if (bytes.size - h.bodyStart != h.bodyLen) throw SaveException("This save was only partly written.")
        val body = bytes.copyOfRange(h.bodyStart, bytes.size)
        if (CRC32().apply { update(body) }.value != h.crc) throw SaveException("This save is damaged (its checksum doesn't match).")

        val payload: ByteArray
        val archives = ArrayList<SavedArchive>()
        try {
            if (h.container == 1) {
                val b = DataInputStream(ByteArrayInputStream(body))
                payload = ByteArray(b.readInt()).also { b.readFully(it) }
                repeat(b.readInt()) { archives += SavedArchive(b.readInt(), b.readUTF(), ByteArray(b.readInt()).also { a -> b.readFully(a) }) }
            } else {
                val raw = InflaterInputStream(ByteArrayInputStream(body)).use { it.readBytes() }
                val b = DataInputStream(ByteArrayInputStream(raw))
                payload = ByteArray(b.readInt()).also { b.readFully(it) }
                for (r in h.refs) archives += SavedArchive(r.tile, r.name, readBlob(r))
            }
        } catch (e: SaveException) {
            throw e
        } catch (e: Exception) {
            throw SaveException("This save is damaged.")
        }
        val game = try { SaveGame.read(payload) } catch (e: IllegalArgumentException) {
            if (e.message?.contains("version") == true) throw SaveException("This save is from an incompatible version of the game.")
            throw SaveException("This save is damaged.")
        } catch (e: Exception) {
            throw SaveException("This save is damaged.")
        }
        return LoadedSave(h.info, game, archives)
    }

    private fun parseHeader(id: String, bytes: ByteArray): Header {
        val h = DataInputStream(ByteArrayInputStream(bytes))
        if (h.readInt() != MAGIC) throw SaveException("This file isn't a save.")
        val container = h.readInt()
        if (container > CONTAINER) throw SaveException("This save is from a newer version of the game.")
        val name = h.readUTF(); val savedAt = h.readLong(); val playtime = h.readLong()
        val colony = h.readUTF(); val day = h.readInt(); val colonists = h.readInt(); val tick = h.readLong()
        val bodyLen = h.readInt(); val crc = h.readLong()
        val refs = if (container >= 2) List(h.readInt()) { ArchiveRef(h.readInt(), h.readUTF(), h.readUTF()) } else emptyList()
        val bodyStart = bytes.size - h.available()
        return Header(SaveInfo(id, name, savedAt, playtime, colony, day, colonists, tick), container, bodyLen, crc, refs, bodyStart)
    }

    private fun damagedInfo(id: String, reason: String) =
        SaveInfo(id, if (id == AUTOSAVE) "Autosave" else if (id == QUICKSAVE) "Quicksave" else id, 0L, 0L, "", 0, 0, 0L, reason)
}
