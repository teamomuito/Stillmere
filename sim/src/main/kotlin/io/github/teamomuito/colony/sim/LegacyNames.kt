package io.github.teamomuito.colony.sim

import java.util.zip.CRC32

/**
 * Saves written before the renames hold some names as text or as enum names: backstory titles inside [Pawn.backstory],
 * thought labels, and one incident name (incidents are saved by name). These functions map the old text to the current text.
 * The renames come in six groups, each gated on the save version:
 *  - names changed in version 26 are translated when a save is below 26;
 *  - names changed in version 27 are translated when a save is below 27 (this includes version 26 saves);
 *  - names changed in version 28 (thought labels and the pirate camp texts) are translated when a save is below 28 (this includes
 *    versions 26 and 27);
 *  - names changed in version 29 (thought labels and the temporary event name) are translated when a save is below 29;
 *  - names changed in version 30 (thought labels and the temporary event name) are translated when a save is below 30;
 *  - names changed in version 31 (backstory titles) are translated when a save is below 31.
 * Backstory titles are read for every save below 31, and each title table applies below its own version. Incident names are
 * translated for saves below 26: SaveGame calls [incidentName] for saves below 28, and this function returns the saved name from
 * version 26 on, so the effective gate is 26. Thought labels and the temporary event name are read for every save below 30, so saves
 * written by version 30 never go through that translation. Pirate camp texts (site names and pending-fight labels) are translated
 * below version 28.
 * Graveyard causes, log lines and caravan last-event notes hold renamed text too, so they are translated by [renamedText], which
 * replaces each renamed text it finds in them and keeps everything else (names.md section 2). Each renamed text has its own gate.
 * Translating on read does not change the save layout, so it does not change the version number.
 * Each table holds the text as it stands in the current version, so a save that passes through several tables gets the final name.
 * The case of the new text follows the case of the old text: a lower-case old title (an adult title, which Game.kt writes in lower
 * case) maps to a lower-case new title. A migrated adult title is therefore the same text that a new colony writes for that title.
 *
 * Each old name is stored as the CRC-32 of its UTF-8 bytes, so no old name is written in code. A lookup computes the CRC-32 of
 * the saved text and finds the entry with that value. The row ids (T, H, E, I) match the table in docs/fidelity/names.md, which
 * lists each old name next to its CRC-32 value. The test data for these functions is in sim/src/test/resources/legacy/.
 */
object LegacyNames {
    private fun crc(text: String): Long = CRC32().apply { update(text.toByteArray(Charsets.UTF_8)) }.value

    /** Backstory titles changed in version 26, keyed by the CRC-32 of the old title. */
    private val titles26: Map<Long, String> = mapOf(
        0x53C9BDEEL to "Cradle-line recruit", // T01
        0x1FF1D58CL to "Street kid", // T02
        0x0EEBEA00L to "Camp foundling", // T03
        0x8D96AF7FL to "Ringside tyke", // T04
        0x508903A1L to "Hull scrapper", // T05
        0xE4AD5242L to "Hold-born ward", // T06
    )

    /**
     * Backstory titles changed in version 27, keyed by the CRC-32 of the old title. HEAD writes every adult title in lower case
     * (Game.kt), so the lower-case form of each renamed adult title is keyed too: T12 (of T11), T20 (of T13) and T21 (of T14). The
     * new text of a lower-case key is lower case as well, the form that Game.kt writes for an adult.
     */
    private val titles27: Map<Long, String> = mapOf(
        0x9A09B234L to "Furrow-born", // T07
        0x0C94F25FL to "Infirmary helper", // T08
        0x9699E58AL to "Ruin picker", // T09
        0xBC6E2000L to "Pasture-born", // T10
        0x2AB1839EL to "Palisade watch", // T11
        0xE7D5FB46L to "palisade watch", // T12, the lower-case form found in the version 25 fixture
        0xCD672D84L to "Stockman", // T13
        0x02D179CAL to "Sloop hand", // T14
        0x4BFBA253L to "stockman", // T20, the lower-case form of T13 that HEAD writes for an adult
        0x8A144754L to "sloop hand", // T21, the lower-case form of T14 that HEAD writes for an adult
    )

    /**
     * Backstory titles changed in version 31, keyed by the CRC-32 of the old title. HEAD writes an adult title in lower case, so the
     * lower-case form is keyed too (T17, T19), as T12, T20 and T21 are, with a lower-case new title.
     */
    private val titles31: Map<Long, String> = mapOf(
        0x37C98608L to "Unclaimed child", // T15, childhood title
        0xD36D6618L to "Battlefield bandager", // T16
        0x55F1E9CFL to "battlefield bandager", // T17, the lower-case form that versions 25 to 30 wrote for an adult
        0x14D4D043L to "Roadwalker", // T18
        0xDB69E9DFL to "roadwalker", // T19, the lower-case form that versions 25 to 30 wrote for an adult
    )

    /** Thought labels changed in version 26, keyed by the CRC-32 of the old label. Thoughts expire within days. */
    private val thoughts26: Map<Long, String> = mapOf(
        0xE540B066L to "Lucid high", // H01
        0x12066505L to "Pale dust high", // H02
        0xB15D4526L to "White rush high", // H03
    )

    /** Thought labels changed in version 27, keyed by the CRC-32 of the old label. */
    private val thoughts27: Map<Long, String> = mapOf(
        0x9FE4F3DAL to "Ate a roasted platter", // H04
        0xB0F67A6DL to "Ate feed pellets", // H05
        0xE4B88E51L to "Played quoits", // H06
        0x57D1DCE9L to "Mind hum", // H07
        0x14E7C8A1L to "Mind balm", // H08
        0x4FEFA924L to "Hushed calm", // H09
    )

    /** Thought labels changed in version 28, keyed by the CRC-32 of the old label. */
    private val thoughts28: Map<Long, String> = mapOf(
        0xAE9D1F55L to "Ate chitin cuts", // H10
        0xA527E0A2L to "Ate manflesh", // H11
        0xCD2564B8L to "Ate manflesh (loved it)", // H12
        0x5D920822L to "Full belly", // H13
        0x6F84131EL to "Refit pride", // H14
        0xA056C589L to "Sunny outlook", // H15
        0x15741B83L to "Grey outlook", // H16
    )

    /** Incident names (saved by name) changed in version 26, keyed by the CRC-32 of the old name. */
    private val incidents26: Map<Long, String> = mapOf(
        0x4B9396F5L to "SKYHORN_VISIT", // I01
    )

    /**
     * Site names changed in version 28 (round 4), keyed by the CRC-32 of the old name. A pirate camp is saved as "<faction> camp"
     * (S01), so the old pirate faction name is saved inside its camp's name.
     */
    private val siteNames28: Map<Long, String> = mapOf(
        0x3ACB053FL to "Rough-Blade gang camp", // S01
    )

    /**
     * Labels of a pending fight changed in version 28 (round 4), keyed by the CRC-32 of the old label. The label of a camp attack is
     * "the <site name>" (S02). The label of an ambush by a pirate band is "<faction> bandits" (S03). A pending fight is saved with
     * its label, so a save taken during one holds the label.
     */
    private val battleLabels28: Map<Long, String> = mapOf(
        0x243182D6L to "the Rough-Blade gang camp", // S02
        0x5A42498FL to "Rough-Blade gang bandits", // S03
    )

    private fun title(part: String, version: Int): String {
        val key = crc(part)
        if (version < 26) titles26[key]?.let { return it }
        if (version < 27) titles27[key]?.let { return it }
        if (version < 31) titles31[key]?.let { return it }
        return part
    }

    /**
     * A saved backstory is "<childhood title>, <adult title>", or a single title. Each comma-separated part is looked up on its
     * own. A part with no entry is kept as it is.
     */
    fun backstory(saved: String, version: Int): String = saved.split(", ").joinToString(", ") { title(it, version) }

    fun thoughtLabel(saved: String, version: Int): String {
        val key = crc(saved)
        if (version < 26) thoughts26[key]?.let { return it }
        if (version < 27) thoughts27[key]?.let { return it }
        if (version < 28) thoughts28[key]?.let { return it }
        if (version < 29) thoughts29[key]?.let { return it }
        if (version < 30) thoughts30[key]?.let { return it }
        return saved
    }

    /** The temporary event name, saved as text. Saves below version 30 are translated. */
    fun tempEventName(saved: String, version: Int): String {
        val key = crc(saved)
        if (version < 29) tempEvents29[key]?.let { return it }
        if (version < 30) tempEvents30[key]?.let { return it }
        return saved
    }

    /**
     * Thought labels changed in version 30, keyed by the CRC-32 of the label that versions 28 and 29 wrote (H27 and H28), and of
     * two labels that were unchanged from version 25 (H25 and H26). Every save below version 30 is translated by this table.
     */
    private val thoughts30: Map<Long, String> = mapOf(
        0xDAEA0D79L to "Full belly", // H27
        0x64388373L to "Refit pride", // H28
        0x5071A35AL to "Candid exchange", // H25
        0xD5CC079AL to "Friendly ribbing", // H26
    )

    /** The temporary event name changed in version 30, keyed by the CRC-32 of the name version 29 wrote. */
    private val tempEvents30: Map<Long, String> = mapOf(
        0x5D93D34FL to "smoke pall", // E04
    )

    /** Thought labels changed in version 29, keyed by the CRC-32 of the old label. */
    private val thoughts29: Map<Long, String> = mapOf(
        0xCAF9643CL to "Gulped raw fare", // H17
        0x64806904L to "Floor meal", // H18
        0x7F7B9506L to "Warm talk", // H19
        0xF7AFB36FL to "Sky curtain glow", // H20
        0xBD2A1D87L to "Drab rooms", // H21
        0x364B141EL to "Restless mind", // H22
        0xD94335C1L to "Brew haze", // H23
        0x22703895L to "Craving ache", // H24
    )

    /** The temporary event name (saved as text) changed in version 29, keyed by the CRC-32 of the old name. */
    private val tempEvents29: Map<Long, String> = mapOf(
        0x1E4ACCDAL to "scorching spell", // E01
        0x83BE9D83L to "deep chill", // E02
        0x7151D830L to "smoke pall", // E03
    )

    fun incidentName(saved: String, version: Int): String =
        if (version < 26) incidents26[crc(saved)] ?: saved else saved

    /** A site name, saved as text. A pirate camp was renamed in version 28, so saves below 28 are translated. */
    fun siteName(saved: String, version: Int): String =
        if (version < 28) siteNames28[crc(saved)] ?: saved else saved

    /** The label of a pending fight, saved as text. The label of a pirate camp or band was renamed in version 28. */
    fun battleLabel(saved: String, version: Int): String =
        if (version < 28) battleLabels28[crc(saved)] ?: saved else saved

    /**
     * A renamed text that a saved log line, caravan note or death cause can hold, in any position. [crc] and [length] are the CRC-32
     * and the length of the UTF-8 bytes of the old text, so the old text is not written here. The old text is translated in a save
     * below [since], the version whose rename it is, and [new] is its current text.
     */
    private class Renamed(val crc: Long, val length: Int, val since: Int, val new: String)

    /**
     * The renamed texts that saved log lines, caravan notes and death causes can hold. The ids match the S, L and G rows of
     * names.md section 3. S01-S04 are the pirate names, renamed in version 28. The L rows are log wording, raid labels, and the
     * chronic and disease labels that log lines write, renamed in versions 27 to 30. G01-G09 are the death causes, which are also the
     * disease labels that log lines write, renamed in version 31. A text is matched only as a whole word, so a label inside a longer
     * word is not translated.
     */
    private val renamed: List<Renamed> = listOf(
        Renamed(0x3ACB053FL, 21, 28, "Rough-Blade gang camp"), // S01
        Renamed(0x243182D6L, 25, 28, "the Rough-Blade gang camp"), // S02
        Renamed(0x5A42498FL, 24, 28, "Rough-Blade gang bandits"), // S03
        Renamed(0x4063F098L, 16, 28, "Rough-Blade gang"), // S04
        Renamed(0x2CA87CD6L, 33, 27, "The iron swarm has awoken!"), // L01
        Renamed(0x7173095BL, 10, 27, "Iron swarm"), // L02
        Renamed(0xBA006905L, 28, 27, "An iron hulk has crashed"), // L03
        Renamed(0xEEDD3DC6L, 24, 27, "A mind hum assaults"), // L04
        Renamed(0x1FF83583L, 22, 27, "A mind balm calms"), // L05
        Renamed(0x78E10C89L, 19, 27, " has gone rogue"), // L06
        Renamed(0x5487903FL, 34, 28, "The pyre house burned the body of"), // L07
        Renamed(0x18326A61L, 47, 28, "Fume cloud! Stay indoors; crops will wither."), // L08
        Renamed(0x3DCEB71CL, 30, 28, "The fume cloud has settled."), // L09
        Renamed(0x440D82C1L, 7, 29, "Tunnelers"), // L10
        Renamed(0x7EDEB114L, 13, 29, "A shelling party"), // L11
        Renamed(0x3B16A8B2L, 20, 29, "Raiders in landers"), // L12
        Renamed(0x78409214L, 27, 29, "A scorching spell is sweeping in!"), // L13
        Renamed(0x0B5974E1L, 54, 29, "A deep chill has hit. Keep warm and protect your crops!"), // L14
        Renamed(0xDD645A87L, 20, 29, "The shadow passage is over."), // L15
        Renamed(0x9BF1C1A3L, 29, 29, "A shadow passage blots out the sun."), // L16
        Renamed(0xB175C334L, 31, 29, "A supply capsule crashed nearby with"), // L17
        Renamed(0x6A0FF30DL, 15, 29, "A pest bloom!"), // L18
        Renamed(0x5A57779EL, 36, 29, "A grid fault! A battery exploded."), // L19
        Renamed(0x01C82663L, 16, 29, "Crop mildew destroyed"), // L20
        Renamed(0x524D699CL, 61, 29, "A falling stone crashes into the ground nearby, scattering metal."), // L21
        Renamed(0x22FA7E43L, 50, 29, "A sky curtain lights the night sky. Everyone is moved."), // L22
        Renamed(0x19D1DF79L, 32, 29, "A grassland burn has broken out to the"), // L23
        Renamed(0x82B13289L, 15, 29, "A peddler convoy"), // L24
        Renamed(0x34CF9AF4L, 55, 29, "Volcanic ash darkens the sky: a smoke pall begins."), // L25
        Renamed(0xC403439DL, 8, 29, "spine strain"), // L26
        Renamed(0x54135569L, 9, 29, "stiff joints"), // L27
        Renamed(0xB3FD6A0BL, 8, 29, "clouded lens"), // L28
        Renamed(0xAB003025L, 12, 29, "dulled hearing"), // L29
        Renamed(0x0B3E374FL, 8, 29, "memory fade"), // L30
        Renamed(0x0AF06D8DL, 10, 29, "numb sleep"), // L31
        Renamed(0xA1532339L, 19, 30, " got a spoiled stomach"), // L32
        Renamed(0x0C67795EL, 41, 30, "The corona surge has ended; power is back."), // L33
        Renamed(0x4D69701DL, 46, 30, "A corona surge knocks out all electrical power!"), // L34
        Renamed(0x41589C51L, 28, 30, "A violent lightning squall hits."), // L35
        Renamed(0xBC74826AL, 14, 30, "spoiled stomach"), // L36
        Renamed(0xD0452977L, 11, 31, "exposure"), // G01
        Renamed(0x8BA046D5L, 10, 31, "overheating"), // G02
        Renamed(0x553BECCDL, 10, 31, "bleed-out"), // G03
        Renamed(0x5A3CCF98L, 3, 31, "chest cough"), // G04
        Renamed(0x47B06389L, 6, 31, "boil sickness"), // G05
        Renamed(0xB2021990L, 7, 31, "marsh fever"), // G06
        Renamed(0xA18A0D05L, 17, 31, "heavy drowse"), // G07
        Renamed(0xC66372D0L, 12, 31, "starved frame"), // G08
        Renamed(0x35F3E398L, 13, 31, "toxin load"), // G09
    )

    /** The renamed texts grouped by length, the longest first, each group keyed by CRC-32. */
    private val renamedByLength: List<Pair<Int, Map<Long, Renamed>>> =
        renamed.groupBy { it.length }.map { (length, rows) -> length to rows.associateBy { it.crc } }.sortedByDescending { it.first }

    /** From this version on no renamed text applies, so a save at or past it is read as it was saved. */
    private val lastRename: Int = renamed.maxOf { it.since }

    /**
     * Translates every renamed text in [saved] that applies to [version]. The scan runs left to right over the UTF-8 bytes. At each
     * position the longest renamed text that matches (by length and CRC-32, and as a whole word) is replaced by its current text.
     * Everything else is copied unchanged, so names and the rest of the wording stay as saved.
     */
    private fun renamedText(saved: String, version: Int): String {
        if (version >= lastRename) return saved
        val bytes = saved.toByteArray(Charsets.UTF_8)
        val out = java.io.ByteArrayOutputStream(bytes.size)
        val crc = CRC32()
        var changed = false
        var i = 0
        while (i < bytes.size) {
            val hit = renamedAt(bytes, i, version, crc)
            if (hit != null) {
                out.write(hit.new.toByteArray(Charsets.UTF_8))
                i += hit.length
                changed = true
            } else {
                out.write(bytes[i].toInt())
                i++
            }
        }
        return if (changed) String(out.toByteArray(), Charsets.UTF_8) else saved
    }

    /** The longest renamed text that applies to [version] and starts at [start] as a whole word, or null. */
    private fun renamedAt(bytes: ByteArray, start: Int, version: Int, crc: CRC32): Renamed? {
        for ((length, rows) in renamedByLength) {
            val end = start + length
            if (end > bytes.size) continue
            crc.reset()
            crc.update(bytes, start, length)
            val row = rows[crc.value] ?: continue
            if (version >= row.since) continue
            if (isWordByte(bytes[start]) && start > 0 && isWordByte(bytes[start - 1])) continue
            if (isWordByte(bytes[end - 1]) && end < bytes.size && isWordByte(bytes[end])) continue
            return row
        }
        return null
    }

    /** A byte that continues a word: an ASCII letter or digit, or any byte of a multi-byte character. */
    private fun isWordByte(b: Byte): Boolean = b < 0 || Character.isLetterOrDigit(b.toInt())

    /** A saved log line. Its renamed wording is translated; the names and the rest of the line are kept. */
    fun logLine(saved: String, version: Int): String = renamedText(saved, version)

    /** A saved caravan last-event note. A renamed label in it (a pirate camp or band) is translated. */
    fun caravanNote(saved: String, version: Int): String = renamedText(saved, version)

    private val graveyardPattern = Regex("""(.+) - (.+) \(day (\d+)\)""")

    /**
     * A saved graveyard entry, "<name> - <cause> (day <n>)". Only the cause is translated, so a name is never changed. A name may
     * hold " - "; a cause does not, so the last " - " before "(day" divides them.
     */
    fun graveyardEntry(saved: String, version: Int): String {
        if (version >= lastRename) return saved
        val m = graveyardPattern.matchEntire(saved) ?: return saved
        val cause = m.groupValues[2]
        val translated = renamedText(cause, version)
        return if (translated == cause) saved else "${m.groupValues[1]} - $translated (day ${m.groupValues[3]})"
    }
}
