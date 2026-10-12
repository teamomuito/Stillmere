# Names and labels: removing RimWorld names and text

Scope: gap report section 2.2 (`gap-report.md`), plus every label that the author recognises as a RimWorld item, building, research topic, trait, backstory title, event, creature, implant, skill, hediff or thought name. This file records names only. It holds no RimWorld text, descriptions or art. Old Stillmere labels that were RimWorld names appear here, in the test data under `sim/src/test/resources/legacy/`, and in code only as CRC-32 values (section 4). Vanilla names recalled for the audit are not Stillmere labels (open item 11).

Status: **rounds 12, 13 and 14 changes are in the working tree on branch `ccr-41984ede-stlmp4`. Not committed.** Round 14 (section 18) checks the
verdict in `names-verdict.md` against the tree, finds findings A and B already fixed, and changes the new text of the five lower-case adult titles
to lower case. Round 13 checks the verdict on round 12 against the tree (section 17): the code, the tables and the tests already hold the fixes the verdict asked for, and round 13 corrects the wording of section 2 and section 6.1 about the bare pirate name and strengthens one test. Round 12 answers the review of round 11, findings A and B (section 16): the lower-case adult titles of T13 and T14 have keys, and the pirate camp texts saved in site names and pending-fight labels are translated. Round 11 answered the verdict on round 10 (section 15), round 10 the review of round 9 (section 14), and round 9 the review of round 8 (section 13). The save version is still 31. Graveyard entries, log lines and caravan last-event notes are kept as saved (section 2). The save fixtures under `sim/src/test/resources/legacy/` and the new files are untracked. The commit step adds them with `git add -A` (section 9, item 1).

Rounds 3 to 7 are marked `r3` to `r7` in the tables. Older rounds have no mark.

## 0. Goal and migration scope (round 11)

**Goal.** Remove the RimWorld names from Stillmere's labels, code and text, and keep every old save readable. A renamed label that a save
file holds must still load under its new name, so the migration in `LegacyNames` covers each renamed label that is saved.

**Exception (decision, rounds 11 and 12).** Three kinds of saved text are kept as saved and are not translated, because each one is a record
of a past event: **graveyard entries** (the death causes, rows G01-G09 of section 3), **log lines** and **caravan last-event notes** (round
12). An old save shows the wording it was written with. The reasons are in section 2. The owner has not yet confirmed this exception (section
9, items 23, 24 and 27).

**Scope.** The goal named three thought labels, and the migration covers more. Each renamed label below is saved and must still load from an
old save, so each one has a translation. The scope is:

* **28 thought labels**, H01-H28 (section 3), renamed in versions 26 to 30.
* **21 backstory-title rows**, T01-T21 (section 3), which are **16 titles** when case is ignored. T01-T14 are the rows renamed in versions 26
  and 27 (13 titles, because T12 is the lower-case form of T11). T15-T19 are the rows renamed in version 31 (three titles, with their
  lower-case adult forms T17 and T19). T20 and T21 are the lower-case adult forms of T13 and T14, renamed in version 27 (round 12), so
  they add no title. The review's count of "14 backstory titles" covers T01-T14 only.
* **4 temporary event names**, E01-E04 (`heat wave`, `cold snap`, `volcanic winter`, `ash-veil winter`), renamed in versions 29 and 30.
* **1 incident name**, I01 (`THRUMBO`, saved by name, read as `SKYHORN_VISIT` below version 26).
* **3 pirate camp texts**, S01-S03 (section 3), renamed in version 28 (round 4, section 6.1): the camp's site name (`<faction> camp`), the
  label of a pending fight against a camp, and the label of an ambush by a pirate band. Round 12 adds them (section 16).

The scope is a decision (round 9, restated in rounds 10, 11 and 12). The owner has not yet confirmed it (section 9, item 18).

## 1. Rules used

* **Renamed:** a label that RimWorld coined or uses in a distinctive form: a designation for a trait, research topic, implant, drug, plant, creature, scenario, thought, event, backstory title, skill, hediff, item, building or weapon whose RimWorld name is not an ordinary word. A label is also renamed when it fails the near-copy rule below.
* **Kept (generic):** an ordinary English word, or a real-world object, food, material, biome, animal, disease or phenomenon, used in its ordinary sense (section 7). A kept label is UNVERIFIED as RimWorld wording unless its row says otherwise.
* **Near-copy rule.** A new label must share neither its head word (its last word) nor its first word with the RimWorld label it replaces. Examples that fail: "Greatbow" to "Tall bow" (same head word), and "Electric stove" to "Electric range" (same first word, head swapped for a synonym). Round 7 checked every (old, new) pair of sections 3 and 6 by script (section 8). Two template forms do not count as a shared first word: the verb of a thought label ("Ate ...", "Played ...") and the drug template ("<drug> high", "<drug> addiction"). This exception is an owner decision (open item 16).
* **Saved text** is translated on load when it is one of the saved labels: thought labels, backstory titles, the temporary event name, one incident name and the pirate camp texts (section 2). Display text that is not saved, such as building, item, research and trait names and their descriptions, needs no translation when it is renamed. Graveyard entries, log lines and caravan last-event notes are saved and are kept as saved (section 2).
* **Enums are saved by ordinal.** A renamed constant keeps its position. Round 7 renamed constants again (section 5.2); each keeps its position, and `NamesTest` pins the ordinals.
* **Incident constants are saved by name.** Only `Incident.THRUMBO` was renamed (`SKYHORN_VISIT`), with a translation (section 4). The other incident constants keep their names (open item 6).
* **Identifiers.** A constant that echoes a RimWorld label phrase is renamed (sections 5.1 and 5.2). Generic words stay in identifiers, for example `TURRET`, `TRAP_SPIKE`, `CONDUIT` and `BATTERY` (open item 9). Player-visible text and comments use the new words.

## 2. Save compatibility

* **Current version: 31** (`SaveGame.VERSION`). Round 8 renamed three backstory titles (`Orphan`, `Field medic`, `Drifter`) and, for new deaths, the causes that versions 25 to 30 wrote into the graveyard. The version went from 30 to 31. Round 9 stopped translating graveyard entries: an old entry is kept as saved (decision below). The version stays 31, because the three titles still need it. The field layout did not change. Round 7 (version 29 to 30) renamed four thought labels and one temporary event name (section 6.4).
* **Gates.** Backstory titles are read for every save below 31; each title table applies below its own version (titles renamed in versions 26 and 27, and the three titles renamed in version 31). Incident names are translated for saves below 26 (`LegacyNames.incidentName`). `SaveGame` calls that function for saves below 28, and the function returns the saved name from version 26 on, so the effective gate is 26. Thought labels are read through the table of each version group: below 26, 27, 28, 29 and 30. The temporary event name is read below 29 (E01-E03) and below 30 (E04). Graveyard entries, log lines and caravan last-event notes are never translated (rounds 9, 11 and 12, section 2). Site names and pending-fight labels (S01-S03) are read below 28, the version of round 4 (`LegacyNames.siteName` and `LegacyNames.battleLabel`, called from `SaveGame` for every site and both pending plans). Thought labels and the temporary event name are not translated in saves written by version 30, because they are read only below 30. Each table holds the final name, so a save that passes several gates ends at the current name.
* **What is saved as text and was renamed:** backstory titles (`Pawn.backstory`), thought labels (`Thought.label`), the temporary event name (`tempEventName`), one incident name (saved by name), the pirate camp texts (the site names in `World.sites`, `<faction> camp`, and the labels of pending fights in `BattlePlan.label`, round 12), and the death causes written into the graveyard (`Game.graveyard`, one string per death: `<name> - <cause> (day <n>)`). Only new deaths write the renamed causes. A saved graveyard entry is never translated (decision below). Enum labels are not saved. Log lines are saved as text and are kept as saved, as graveyard entries are (decision of rounds 10 and 11, below; open item 23). Caravan last-event notes (`Caravan.lastEvent`) are saved as text and are kept as saved, as a record of a past battle (decision of round 12, below; open item 27). The bare pirate faction name (`Rough-Knife gang`) is never saved on its own: factions are not written to a save, and `World.generate` rebuilds them from the seed on load (`SaveGame.kt`, the read site of `World.generate`). The old name reaches a save only inside the camp texts (S01-S03, keyed), the caravan note and the log lines, and the last two are kept as saved. So the key for the site name is for `<faction> camp`, not for the bare name (section 16, finding B).
* **Fixtures.** Round 7 generated `colony_v29.sav` from the round-6 code (version 29) before any round 7 change, with a temporary test that was deleted afterwards. `LegacyNamesMigrationTest` reads it.
* **Fixtures (round 8).** `colony_v30.sav` was generated from the round-7 working tree (version 30) before the version 31 change, by a temporary test (`Game(4242L)`, `startNewColony(QUIET_ENCLAVE)`), and the test was deleted. `colony_v25_graves.sav` was generated from the code at git `HEAD` (version 25), in a `git archive` copy, by a temporary test (`Game(4242L)`, `startNewColony(LOST_TRIBE)`) that added the version-25 death causes to the graveyard. Both are untracked.

**Decision: graveyard entries are kept as saved (round 9; restated in rounds 10 and 11; not yet confirmed by the owner, open item 24).**
`Game.graveyard` holds one string per death. Each entry is a record of a past death, so `SaveGame.read` reads every entry exactly as it was
saved, in every version. Round 8 had translated the causes below version 31 (rows G01-G09). Round 9 reverses that: the translation and its
nine keys are removed from `LegacyNames.kt`. The read site in `SaveGame.kt` carries a comment that records the decision. The death causes
that the current code writes are `exposure` and `overheating` (`Mood.kt`) and `bleed-out` and the lowercased lethal hediff labels
(`chest cough`, `boil sickness`, `marsh fever`, `heavy drowse`, `starved frame`, `toxin load`, from `Health.kt`). They have no key in
`LegacyNames`, and they need none: graveyard text is never translated, in either direction. Consequences:

* A save written by versions 25 to 30 shows its old causes as they were written (`hypothermia`, `heatstroke`, `blood loss`, `flu`, `plague`,
  `malaria`, `sleeping sickness`, `malnutrition`, `toxic buildup`).
* A new death writes the current causes (`exposure`, `overheating`, `bleed-out`, `chest cough`, `boil sickness`, `marsh fever`, `heavy drowse`,
  `starved frame`, `toxin load`), so one graveyard can hold both kinds of entry.
* The version number is not changed by this decision. Version 31 stays, because the three backstory titles of round 8 still need it.
* Tests: `LegacyNamesMigrationTest.aVersion25GraveyardKeepsEveryDeathCauseAsItWasSaved` reads `colony_v25_graves.sav` and checks each entry
  against its expectation line. `everyOldDeathCauseInTheCaseFileIsInTheVersion25GraveyardAsSaved` checks all nine G rows of the case file.
  `anOldGraveyardEntryInAVersion30SaveLoadsUnchanged` writes and reads them through a version 30 save.
  `anOldGraveyardStringStillLoadsUnchangedWhenItIsSavedNow` (round 11) writes an entry that holds an old cause through the current version
  and reads it back unchanged. `aVersion30GraveyardIsReadWithItsCurrentCausesUnchanged` checks the current causes of `colony_v30.sav`.

**Decision: log lines are kept as saved (round 10; restated in round 11).** `g.log` holds one text per line, and each line is a record of a
past event, as a graveyard entry is. `SaveGame.read` reads every log line exactly as it was saved, in every version, and `LegacyNames` has no
key for a log line. An old save therefore keeps the wording of its event lines, including any line that names a renamed label. Those lines
are the temporary-event messages, the spoiled-stomach lines, the pyre-house line, the fume-cloud line and the corona-surge line. The read
site in `SaveGame.kt` carries a comment that records the decision. Test: `LegacyNamesMigrationTest.anOldLogLineIsKeptAsSavedAndNotTranslated`
writes the four old temporary-event labels of the case file into a version 30 save as log lines, and reads them back unchanged. The
statement "log lines are not saved" was wrong and is withdrawn: they are saved, and they are kept as saved. This extends the graveyard
decision to log lines, so the owner should confirm it (section 9, item 23).

The rows G01-G09 stay in section 3 as the record of those old texts. Their status is "not translated".

**Decision: pirate camp texts are translated (round 12; not yet confirmed by the owner, open item 26).** A pirate camp's name is saved as
text. A site's name is `<faction> camp` (`Site.name`, written by `SaveGame`), because `Factions.kt` and `Quests.kt` build it from the faction
name. A fight against a camp is saved with its label, `the <site name>` (`Caravans.kt`, `BattlePlan.label`), and an ambush by a pirate band is
saved with `<faction> bandits`. The old pirate faction name therefore sits in saved text, and the rename of round 4 reaches it. These are
live labels: the camp stays on the world map, and the pending fight is named in the battle prompt. They are translated like backstory titles,
not kept like a record. The keys are S01 (site name), S02 (camp label) and S03 (ambush label), read below version 28. The faction name itself
is not saved: `World.generate` rebuilds the factions from the seed on load, so it always shows the current name, and no key is needed for it.

**Decision: caravan last-event notes are kept as saved (round 12; open item 27).** `Caravan.lastEvent` holds a sentence about the last battle,
such as a retreat from a camp. It records a past event, as a log line does, so `SaveGame.read` reads it as saved, and `LegacyNames` has no key
for it. A key would have to cover each sentence template as well as each label, so the record decision is the simpler one. Tests:
`LegacyNamesMigrationTest.aVersion27SaveWithAPirateCampReadsTheCampAndTheFightUnderTheNewNames` checks that the fixture's last event is unchanged,
and `aVersion27SaveWrittenAgainKeepsTheCampTextsAndTheLastEvent` checks it again after a round trip.

### Fixtures (`sim/src/test/resources/legacy/`)

| File | What it holds | Provenance |
|---|---|---|
| `colony_v24.sav`, `colony_v24_expect.txt` | A version 24 save (tracked, read by `LegacySaveMigrationTest`). Round 12 also reads it in `LegacyNamesMigrationTest` for its lower-case adult title (T20). | Written before the time base changed. |
| `colony_v25.sav`, `colony_v25_expect.txt` | Six colonists with version 25 backstory titles. | Written by the version 25 code (git `HEAD`) before the renames. Colonist 0's adult title is lower case, which is what HEAD writes (open item 3). |
| `colony_v25_head.sav`, `colony_v25_head_expect.txt` (r4) | Five colonists with version 25 thought labels (one per rename group), and two incidents: `THRUMBO` and `ANIMAL_JOINS`. | Written by the code at git `HEAD`. Procedure: `git archive HEAD` into a scratch folder. In that copy a temporary test starts the first scenario that gives five colonists, adds thoughts from `old_text_cases.tsv`, sets the two incidents, and writes `SaveGame.write(g)`. This file can be regenerated from the repository. |
| `colony_v25_thoughts.sav`, `colony_v25_thoughts_expect.txt` | Five colonists with old thought labels, and two incidents. | **Came from a generator outside this repository. It cannot be regenerated from the repo.** Kept because `LegacyNamesMigrationTest` reads it (open item 4). |
| `colony_v26.sav`, `colony_v26_expect.txt` (r4) | Five colonists with the names version 26 held. | Written with the current layout and the header set to 26 (`SaveGame.write(g, 26)`), by a temporary test whose names come from `old_text_cases.tsv`. The code that wrote version 26 was never committed (open item 2). |
| `colony_v27.sav`, `colony_v27_expect.txt` (r4) | Five colonists with version 27 text. | Written by the version 27 code (before the round 4 renames), by a temporary test. |
| `colony_v28.sav`, `colony_v28_expect.txt` (r6) | Five colonists with the version 28 thought labels and two incidents. | Written by a temporary generator (`Game(4242L)`, `startNewColony(QUIET_ENCLAVE)`, `SaveGame.write`) from the version-28 working tree, which had 100 by 100 maps. The generator was deleted. |
| `colony_v29.sav`, `colony_v29_expect.txt` (r7) | Five colonists with version 29 labels (`Hearty feast`, `Engineered upgrades`, `Deep talk` and `Shared a joke` among them), the temporary event `ash-veil winter`, and two incidents (`SKYHORN_VISIT`, `ANIMAL_JOINS`). | Written by the version 29 code before any round 7 change, by a temporary test (`Game(4242L)`, `startNewColony(QUIET_ENCLAVE)`, thoughts added with `addThought`, `SaveGame.write`). The test was deleted. The map is 250 by 250 (the version 29 default). |
| `colony_v30.sav`, `colony_v30_expect.txt` (r8) | Five colonists with the version 30 labels, the temporary event `smoke pall`, two incidents, and a graveyard with the version 30 causes (`exposure`, `overheating`, `bleed-out`, and a `lost in captivity` entry). The lower-case adult titles that HEAD writes are in it, so the version 31 translation applies to them. | Written by the version 30 working tree before the version 31 change, by a temporary generator. The generator was deleted. |
| `colony_v25_graves.sav`, `colony_v25_graves_expect.txt` (r8) | A graveyard with the version 25 death causes (read unchanged since round 9, section 2) (`hypothermia`, `heatstroke`, `blood loss`, `flu`, `plague`, `malaria`, `sleeping sickness`, `malnutrition`, `toxic buildup`), and two that were not renamed (`starvation`, `lost in captivity`). Five colonists. | Written by the code at git `HEAD` (version 25), by a temporary generator in a `git archive` copy. The generator was deleted. |
| `colony_v25_adults.sav`, `colony_v25_adults_expect.txt` (r12) | Five colonists. Colonists 0 and 1 hold old childhood titles and the lower-case adult titles that HEAD writes (T21 and T20, from T14 and T13). | Written with the current layout and the header set to 25 (`SaveGame.write(g, 25)`), by a temporary generator (`Game(4242L)`, `startNewColony(QUIET_ENCLAVE)`). The generator was deleted. The layout did not change, so only the header differs from a version 25 save. |
| `colony_v27_sites.sav`, `colony_v27_sites_expect.txt` (r12) | One pirate camp (its site name), a pending fight against it (its label), and a caravan whose last event names the camp. | Written with the current layout and the header set to 27 (`SaveGame.write(g, 27)`), by a temporary generator, as above. The generator was deleted. |
| `old_text_cases.tsv` | Each old name: id, version of the rename (`since`), kind, old text, new text. Rows T01-T21, H01-H28, I01, S01-S03, P01-P03, U01-U02, E01-E04 and G01-G09. | Written for the tests. Old names are allowed here. |

The temporary generators were not kept in the repository. Every file in this folder is untracked, so the commit step must add all of them (section 9, item 1).

## 3. Save translation table

Each old name is stored in code as the CRC-32 of its UTF-8 bytes (`java.util.zip.CRC32`), so no old name is written in code.
`LegacyNames` computes the CRC-32 of the saved text and looks it up. The table below is the source of those values.

| ID | Kind | Changed in | Old text | CRC-32 (hex) | New text |
|---|---|---|---|---|---|
| T01 | title | 26 | Vatgrown soldier | `53C9BDEE` | Cradle-line recruit |
| T02 | title | 26 | Urchin | `1FF1D58C` | Street kid |
| T03 | title | 26 | Tribal child | `0EEBEA00` | Camp foundling |
| T04 | title | 26 | Pit fighter | `8D96AF7F` | Ringside tyke |
| T05 | title | 26 | Space-born tinker | `508903A1` | Hull scrapper |
| T06 | title | 26 | Cargo ship brat | `E4AD5242` | Hold-born ward |
| T07 | title | 27 | Farm kid | `9A09B234` | Furrow-born |
| T08 | title | 27 | Medical student | `0C94F25F` | Infirmary helper |
| T09 | title | 27 | Wasteland scavenger | `9699E58A` | Ruin picker |
| T10 | title | 27 | Herder's child | `BC6E2000` | Pasture-born |
| T11 | title | 27 | Colonial guard | `2AB1839E` | Palisade watch |
| T12 | title | 27 | colonial guard | `E7D5FB46` | palisade watch |
| T13 | title | 27 | Beast tamer | `CD672D84` | Stockman |
| T14 | title | 27 | Pirate deckhand | `02D179CA` | Sloop hand |
| H01 | thought | 26 | Psychite buzz | `E540B066` | Lucid high |
| H02 | thought | 26 | Flake rush | `12066505` | Pale dust high |
| H03 | thought | 26 | Yayo rush | `B15D4526` | White rush high |
| H04 | thought | 27 | Ate a fine meal | `9FE4F3DA` | Ate a roasted platter |
| H05 | thought | 27 | Ate kibble | `B0F67A6D` | Ate feed pellets |
| H06 | thought | 27 | Played horseshoes | `E4B88E51` | Played quoits |
| H07 | thought | 27 | Psychic drone | `57D1DCE9` | Mind hum |
| H08 | thought | 27 | Psychic soothe | `14E7C8A1` | Mind balm |
| H09 | thought | 27 | Mellow | `4FEFA924` | Hushed calm |
| I01 | incident | 26 | THRUMBO | `4B9396F5` | SKYHORN_VISIT |
| H10 | thought | 28 (r4) | Ate insect meat | `AE9D1F55` | Ate chitin cuts |
| H11 | thought | 28 (r4) | Ate human flesh | `A527E0A2` | Ate manflesh |
| H12 | thought | 28 (r4) | Ate human flesh (loved it) | `CD2564B8` | Ate manflesh (loved it) |
| H13 | thought | 28 (r4; new text changed in r7) | Gourmand's feast | `5D920822` | Full belly |
| H14 | thought | 28 (r4; new text changed in r7) | Bionic upgrades | `6F84131E` | Refit pride |
| H15 | thought | 28 (r4) | Optimist | `A056C589` | Sunny outlook |
| H16 | thought | 28 (r4) | Pessimist | `15741B83` | Grey outlook |
| S01 | site | 28 (r4) | Rough-Knife gang camp | `3ACB053F` | Rough-Blade gang camp |
| S02 | battle | 28 (r4) | the Rough-Knife gang camp | `243182D6` | the Rough-Blade gang camp |
| S03 | battle | 28 (r4) | Rough-Knife gang bandits | `5A42498F` | Rough-Blade gang bandits |
| H17 | thought | 29 (r6) | Ate raw food | `CAF9643C` | Gulped raw fare |
| H18 | thought | 29 (r6) | Ate without a table | `64806904` | Floor meal |
| H19 | thought | 29 (r6) | Kind words | `7F7B9506` | Warm talk |
| H20 | thought | 29 (r6) | Beautiful aurora | `F7AFB36F` | Sky curtain glow |
| H21 | thought | 29 (r6) | Ugly surroundings | `BD2A1D87` | Drab rooms |
| H22 | thought | 29 (r6) | Tortured soul | `364B141E` | Restless mind |
| H23 | thought | 29 (r6) | Drunk | `D94335C1` | Brew haze |
| H24 | thought | 29 (r6) | Withdrawal | `22703895` | Craving ache |
| E01 | temp | 29 (r6) | heat wave | `1E4ACCDA` | scorching spell |
| E02 | temp | 29 (r6) | cold snap | `83BE9D83` | deep chill |
| E03 | temp | 29 (r6; new text changed in r7) | volcanic winter | `7151D830` | smoke pall |
| E04 | temp | 30 (r7) | ash-veil winter | `5D93D34F` | smoke pall |
| H25 | thought | 30 (r7) | Deep talk | `5071A35A` | Candid exchange |
| H26 | thought | 30 (r7) | Shared a joke | `D5CC079A` | Friendly ribbing |
| H27 | thought | 30 (r7) | Hearty feast | `DAEA0D79` | Full belly |
| H28 | thought | 30 (r7) | Engineered upgrades | `64388373` | Refit pride |
| T15 | title | 31 (r8) | Orphan | `37C98608` | Unclaimed child |
| T16 | title | 31 (r8) | Field medic | `D36D6618` | Battlefield bandager |
| T17 | title | 31 (r8) | field medic | `55F1E9CF` | battlefield bandager |
| T18 | title | 31 (r8) | Drifter | `14D4D043` | Roadwalker |
| T19 | title | 31 (r8) | drifter | `DB69E9DF` | roadwalker |
| T20 | title | 27 (r12) | beast tamer | `4BFBA253` | stockman |
| T21 | title | 27 (r12) | pirate deckhand | `8A144754` | sloop hand |
| G01 | death cause | 31 (r8); kept as saved from r9 | hypothermia | `D0452977` | exposure |
| G02 | death cause | 31 (r8); kept as saved from r9 | heatstroke | `8BA046D5` | overheating |
| G03 | death cause | 31 (r8); kept as saved from r9 | blood loss | `553BECCD` | bleed-out |
| G04 | death cause | 31 (r8); kept as saved from r9 | flu | `5A3CCF98` | chest cough |
| G05 | death cause | 31 (r8); kept as saved from r9 | plague | `47B06389` | boil sickness |
| G06 | death cause | 31 (r8); kept as saved from r9 | malaria | `B2021990` | marsh fever |
| G07 | death cause | 31 (r8); kept as saved from r9 | sleeping sickness | `A18A0D05` | heavy drowse |
| G08 | death cause | 31 (r8); kept as saved from r9 | malnutrition | `C66372D0` | starved frame |
| G09 | death cause | 31 (r8); kept as saved from r9 | toxic buildup | `35F3E398` | toxin load |

T17 and T19 are the lower-case forms of T16 and T18. HEAD writes an adult colonist's title in lower case (open item 3), so saves from
versions 25 to 30 hold these forms, and each has its own CRC-32 key. The old-text column holds only the title text (round 10).
T12 is the lower-case form of T11. T20 and T21 (round 12) are the lower-case forms of T13 and T14. Before round 12 they had no key, so an
old save that held a lower-case T13 or T14 kept the old adult title on load (review finding A). The rows are numbered after T19 and keep
their version 27 `since` value, so the table is not in version order at this point.

**Case of the new text (round 14).** The case of the new text follows the case of the old text. Each lower-case row (T12, T17, T19, T20
and T21) maps to the lower-case new title: `palisade watch`, `battlefield bandager`, `roadwalker`, `stockman` and `sloop hand`. Game.kt writes
an adult title in lower case, so a translated adult title is now the text a new colony writes for that title. Before round 14 these five
new texts were capitalised, which no new colony writes, so a colonist from an old save showed a title that a new colonist never had. The
capitalised rows (T01-T11, T13-T16, T18) are unchanged. `LegacyNamesMigrationTest.aMigratedLowerCaseAdultTitleIsTheTextTheCurrentCodeWritesForAnAdult`
checks each lower-case row against the adult titles of `Backstories`, lower-cased.

The G rows are the death causes of the graveyard. They are not translated: the column "Changed in" gives the version that wrote the new
cause, and the old cause is kept as saved (section 2).

Rows `T07`-`T14`, `H04`-`H09` were renamed in version 27. Rows `H10`-`H16` and `S01`-`S03` were renamed in version 28 (round 4). Rows `T20` and `T21` (round 12) are the lower-case forms of `T13` and `T14`, renamed in version 27. Rows `H17`-`H24` and
`E01`-`E03` were renamed in version 29 (round 6). Rows `H25`-`H28` and `E04` were renamed in version 30 (round 7). Row `H13` and `H14`
were renamed again in version 30 (their version-28 names were `Hearty feast` and `Engineered upgrades`; the table keeps their
current names). The others were renamed in version 26. Rows `T15`-`T19` (version 31) and `G01`-`G09` (version 31, graveyard) were renamed in round 8. Round 9 keeps the `T` rows translated and stops translating the `G` rows: graveyard entries are kept as saved (section 2).

A label is listed under the version whose rename changed it. A save below that version is translated by that row's table, and the
table gives the final name, so a save that is several versions old still reaches the current name.

Round 5 checked the 31 keys in `LegacyNames.kt` with a script. Round 6 added 11 keys (H17-H24 and E01-E03). Round 7 added 5 keys
(H25-H28 in `thoughts30`, E04 in `tempEvents30`) and changed the new text of H13, H14 and E03, so `LegacyNames.kt` holds 47 keys.
A script (round 7) checked all 47: each key is the CRC-32 of the UTF-8 bytes of the old text in `old_text_cases.tsv` (rows T01-T14,
H01-H28, E01-E04, I01), and each maps to the new text in the same row. 47 of 47 match, and no key is unused. The CRC-32 values were
computed with `zlib.crc32`, which computes the same CRC-32 as `java.util.zip.CRC32`.

Round 8 added 14 keys: T15-T19 in `titles31` and G01-G09 in `causes31`, which made 61. Round 9 removed the nine `causes31` keys, because graveyard entries are no longer translated (section 2). `LegacyNames.kt` now holds 52 keys: the 47 from rounds 5 to 7 and the five keys T15-T19 of round 8. A round 9 count of the `0x` keys in `LegacyNames.kt` gives 52. A script (round 8) read the keys and compared them with `old_text_cases.tsv`: 61 of 61 keys match a row, and no key is unused. Rows P01-P03 and U01-U02 hold no key. They are test-only rows, listed in the table after this section (round 10). The round 8 keys were computed with `zlib.crc32`.

Round 12 added 5 keys: T20 and T21 in `titles27` (the lower-case forms of T13 and T14), and S01 in `siteNames28`, with S02 and S03 in
`battleLabels28` (the pirate camp texts). `LegacyNames.kt` now holds 57 keys: the 52 of round 9 and these five. A script (round 12) read the
keys, computed the CRC-32 of each row's old text with `zlib.crc32`, and checked the new text: 57 of 57 keys match a row, no key is unused,
and the only rows with no key are G01-G09 (kept as saved) and the test-only rows P01-P03 and U01-U02.

**Test-only rows (no CRC-32 key, round 10).** These rows are in `old_text_cases.tsv` and are read by `LegacyNamesMigrationTest`. No
table above lists them, so they are listed here (verdict item 7).

| ID | Kind | Changed in | Old text | New text | What the row tests |
|---|---|---|---|---|---|
| P01 | backstory list | 26 | Urchin, cook | Street kid, cook | a saved list of two titles: the first is translated (T02), the second is in no table and is kept |
| P02 | backstory list | 27 | Farm kid, colonial guard | Furrow-born, palisade watch | a list of two titles, both translated (T07 and T12) |
| P03 | backstory list | 27 | Colony child | Colony child | a title that is in no table is kept as it is |
| U01 | thought | 27 | Drug use | Drug use | an ordinary label that was not renamed is kept |
| U02 | incident | 27 | ANIMAL_JOINS | ANIMAL_JOINS | an incident name that was not renamed is kept |

## 4. Explicit exception: where old names may appear

Old Stillmere labels that were RimWorld names, and the old names of constants that echoed RimWorld labels, may appear only in:

* this file (`docs/fidelity/names.md`);
* `sim/src/test/resources/legacy/` (the fixtures, their expectation files and `old_text_cases.tsv`). That folder is exempt from the name check;
* code, but only as CRC-32 values. `LegacyNames.kt` holds 57 values (round 12), and `LegacyNamesMigrationTest.kt` reads the old text from the legacy folder.

**Explicit exception list (owner decision, recorded):**

* **`Incident.THRUMBO` is renamed to `Incident.SKYHORN_VISIT`.** The owner chose to migrate old saves, so the rule "do not rename Incident constants" is waived for this one constant. `LegacyNames.incidentName` is the migration: the saved incident name (row I01, CRC-32 `4B9396F5`) is read as `SKYHORN_VISIT` for saves below version 26, with its tick kept. The name is also in section 3 (row I01), and the creature rename Thrumbo to Skyhorn is in section 6.
  * `LegacyNamesMigrationTest.theOldIncidentIsReadUnderItsNewNameWithItsTickAndTheOtherIncidentIsUnchanged` loads `colony_v25_thoughts.sav`, whose expectation file holds the old incident name, and checks the tick under the new name.
  * `LegacyNamesMigrationTest.aVersion25SaveWrittenByTheHeadCodeReadsEveryRenameSinceVersion25` loads `colony_v25_head.sav`, which holds the old incident name, and checks `Incident.SKYHORN_VISIT`.
  * `NamesTest.theRenamedIncidentHasItsOrdinalAndNewName` pins the ordinal and the name.

The name check in verify covers string literals in code. Identifiers are outside it (open item 9). Round 7 searched the code and the docs (section 8): every literal hit on an old label was changed, except the CRC-32 table and the legacy folder.

Vanilla reference names recalled for the gap audit, for example in the vanilla columns of `gap-report.md`, are not Stillmere labels and are not in code (open item 11).

## 5. Renamed constants (enum name changes, in place)

Rounds 4 and 5 renamed no constant; this list is unchanged. Round 6 is in section 5.1 and round 7 in section 5.2.

Generated from a positional comparison of the enum bodies in git `HEAD` (version 25) and the working tree. The comparison removes
comments first, then checks that each enum has the same number of entries. Each entry below keeps its position, so its ordinal is
unchanged. The constants that were renamed in earlier rounds are included.

* **HediffKind** (11): `SMOKELEAF_HIGH` → `HUSHMOSS_HIGH`, `PSYCHITE_HIGH` → `LUCID_HIGH`, `SMOKELEAF_ADDICTION` → `HUSHMOSS_ADDICTION`, `PSYCHITE_ADDICTION` → `LUCID_ADDICTION`, `FLAKE_HIGH` → `PALE_DUST_HIGH`, `YAYO_HIGH` → `WHITE_RUSH_HIGH`, `GOJUICE_HIGH` → `SURGE_TONIC_HIGH`, `WAKEUP_HIGH` → `DAWN_TABLET_HIGH`, `FLAKE_ADDICTION` → `PALE_DUST_ADDICTION`, `YAYO_ADDICTION` → `WHITE_RUSH_ADDICTION`, `GOJUICE_ADDICTION` → `SURGE_TONIC_ADDICTION`.
* **BuildDef** (12): `AUTODOOR` → `SHUTTER_DOOR`, `PLASTEEL_WALL` → `HARDALLOY_WALL`, `HORSESHOES` → `QUOITS`, `DRUG_LAB` → `TINCTURE_RIG`, `HI_TECH_BENCH` → `PRECISION_BENCH`, `COMMS_CONSOLE` → `SIGNAL_DESK`, `PASSIVE_COOLER` → `FROST_VAULT`, `SUN_LAMP` → `GLOW_PANEL`, `FOAM_POPPER` → `QUENCH_VENT`, `DEEP_DRILL` → `SUBSOIL_AUGER`, `NUTRIENT_DISPENSER` → `MASH_TAP`, `MINI_TURRET` → `SENTRY_POD`.
* **Ore** (1): `PLASTEEL` → `HARDALLOY`.
* **PlantType** (6): `WILD_HEALROOT` → `FERAL_MIREWORT`, `HEALROOT` → `MIREWORT`, `SMOKELEAF` → `HUSHMOSS`, `PSYCHOID` → `DREAMWEED`, `DEVILSTRAND_CROP` → `WISPSILK_CROP`, `HAYGRASS` → `MEADOW_GRASS`.
* **ItemType** (35): `PLASTEEL` → `HARDALLOY`, `DEVILSTRAND` → `WISPSILK`, `MEAL_PACKAGED` → `MEAL_RATION`, `PEMMICAN` → `TRAIL_CAKE`, `KIBBLE` → `FEED_PELLETS`, `HEALROOT` → `MIREWORT`, `SMOKELEAF` → `HUSHMOSS`, `PSYCHOID` → `DREAMWEED`, `FLAKE` → `PALE_DUST`, `YAYO` → `WHITE_RUSH`, `GO_JUICE` → `SURGE_TONIC`, `WAKE_UP` → `DAWN_TABLET`, `PSYCHITE_TEA` → `LUCID_DRAUGHT`, `W_GREATBOW` → `W_LONGSTAVE`, `W_AUTOPISTOL` → `W_SNAP_PISTOL`, `W_GLADIUS` → `W_STABSWORD`, `W_PLASTEEL_SWORD` → `W_HARDALLOY_SWORD`, `W_CHAIN_SHOTGUN` → `W_SCATTER_CANNON`, `W_HEAVY_SMG` → `W_MAG_CARBINE`, `W_MINIGUN` → `W_SPINNER_RACK`, `W_CHARGE_RIFLE` → `W_PULSE_CARBINE`, `W_INCENDIARY` → `W_EMBER_THROWER`, `W_FRAG` → `W_SHRAPNEL_CHARGE`, `W_DOOMSDAY` → `W_SKYFALL_BATTERY`, `A_DUSTER` → `A_LONG_COAT`, `A_PARKA` → `A_PADDED_COAT`, `A_FLAK_VEST` → `A_RIBBED_VEST`, `A_FLAK_PANTS` → `A_RIBBED_LEGGINGS`, `A_FLAK_JACKET` → `A_RIBBED_JACKET`, `A_MARINE` → `A_TRENCH_ARMOR`, `A_RECON_HELM` → `A_GHOSTWEAVE_HELM`, `A_POWER_ARMOR` → `A_EXOSUIT`, `A_POWER_HELM` → `A_EXO_HELM`, `A_RECON` → `A_GHOSTWEAVE_ARMOR`, `MEAL_NUTRIENT` → `MEAL_VAT_MASH`.
* **Weapon** (13): `GREATBOW` → `LONGSTAVE`, `AUTOPISTOL` → `SNAP_PISTOL`, `GLADIUS` → `STABSWORD`, `PLASTEEL_SWORD` → `HARDALLOY_SWORD`, `CHAIN_SHOTGUN` → `SCATTER_CANNON`, `HEAVY_SMG` → `MAG_CARBINE`, `MINIGUN` → `SPINNER_RACK`, `CHARGE_RIFLE` → `PULSE_CARBINE`, `INCENDIARY_LAUNCHER` → `EMBER_THROWER`, `FRAG_GRENADE` → `SHRAPNEL_CHARGE`, `DOOMSDAY` → `SKYFALL_BATTERY`, `LANCE` → `PULSE_PIKE`, `MINI_TURRET_GUN` → `SENTRY_POD_GUN`.
* **Apparel** (13): `DUSTER` → `LONG_COAT`, `PARKA` → `PADDED_COAT`, `COWBOY_HAT` → `WIDE_BRIM_HAT`, `TUQUE` → `KNIT_CAP`, `FLAK_VEST` → `RIBBED_VEST`, `FLAK_PANTS` → `RIBBED_LEGGINGS`, `FLAK_JACKET` → `RIBBED_JACKET`, `PLATE_ARMOR` → `BRIGANDINE`, `MARINE_ARMOR` → `TRENCH_ARMOR`, `RECON_HELM` → `GHOSTWEAVE_HELM`, `POWER_ARMOR` → `EXOSUIT`, `POWER_HELM` → `EXO_HELM`, `RECON_ARMOR` → `GHOSTWEAVE_ARMOR`.
* **Trait** (10): `NIGHT_OWL` → `NOCTURNAL`, `FAST_LEARNER` → `EAGER_PUPIL`, `TRIGGER_HAPPY` → `QUICK_TRIGGER`, `GREEN_THUMB` → `GARDEN_SENSE`, `TORTURED_ARTIST` → `BROODING_MUSE`, `TOO_SMART` → `KEEN_MIND`, `SLOW_LEARNER` → `SLOW_STUDY`, `IRON_WILLED` → `STEEL_NERVES`, `CAREFUL_SHOOTER` → `STEADY_AIM`, `SUPER_IMMUNE` → `HARDY_BODY`.
* **Research** (19): `PEMMICAN` → `TRAIL_CAKE`, `HERBAL_MEDICINE` → `HERBAL_REMEDIES`, `GUN_TURRETS` → `SWIVEL_NESTS`, `FLAK_ARMOR` → `RIBBED_ARMOR`, `PLATE_ARMOR` → `BRIGANDINE_WORK`, `MULTIANALYZER` → `ANALYSIS_ARRAY`, `DEVILSTRAND_RESEARCH` → `WISPSILK_RESEARCH`, `PLASTEEL_MELEE` → `HARDALLOY_BLADES`, `CHARGE_WEAPONS` → `PULSE_ARMS`, `MARINE_ARMOR` → `TRENCH_ARMOR`, `POWER_ARMOR` → `EXOSUIT`, `AUTODOORS` → `SHUTTER_DOORS`, `PASSIVE_COOLING` → `FROST_VENTING`, `SUN_LAMPS` → `GLOW_PANELS`, `FLAKE_YAYO` → `STIMULANT_BLENDS`, `GO_JUICE` → `SURGE_TONIC`, `CRYPTOSLEEP` → `STASIS`, `DEEP_DRILLING` → `SUBSOIL_BORING`, `NUTRIENT_PASTE` → `VAT_CULTURING`.
* **Difficulty** (1): `RANDY` → `NORMAL`.
* **Scenario** (3): `CRASHLANDED` → `SALVAGE_CREW`, `LOST_TRIBE` → `QUIET_ENCLAVE`, `RICH_EXPLORER` → `SURPLUS_START`.
* **Incident** (1): `THRUMBO` → `SKYHORN_VISIT`.
* **Race** (11): `MUFFALO` → `SHAGOX`, `THRUMBO` → `SKYHORN`, `WARG` → `ASHHOUND`, `BOOMRAT` → `PUFFTAIL`, `BOOMALOPE` → `SWELLGRAZER`, `MEGASCARAB` → `HORNBEETLE`, `SPELOPEDE` → `CAVECRAWLER`, `MEGASPIDER` → `PITWEAVER`, `SCYTHER` → `HOOKTALON`, `LANCER` → `PIKEWALKER`, `CENTIPEDE` → `SIEGEFRAME`.
* **Recipe** (31): `COOK_PEMMICAN` → `COOK_TRAIL_CAKE`, `COOK_KIBBLE` → `COOK_FEED_PELLETS`, `BREW_TEA` → `BREW_DRAUGHT`, `MAKE_FLAKE` → `MAKE_PALE_DUST`, `MAKE_YAYO` → `MAKE_WHITE_RUSH`, `MAKE_WAKEUP` → `MAKE_DAWN_TABLET`, `MAKE_GOJUICE` → `MAKE_SURGE_TONIC`, `MAKE_DUSTER` → `MAKE_LONG_COAT`, `MAKE_PARKA` → `MAKE_PADDED_COAT`, `MAKE_HAT` → `MAKE_WIDE_BRIM_HAT`, `MAKE_FLAK_VEST` → `MAKE_RIBBED_VEST`, `MAKE_FLAK_PANTS` → `MAKE_RIBBED_LEGGINGS`, `MAKE_FLAK_JACKET` → `MAKE_RIBBED_JACKET`, `MAKE_GREATBOW` → `MAKE_LONGSTAVE`, `MAKE_GLADIUS` → `MAKE_STABSWORD`, `MAKE_PLASTEEL_SWORD` → `MAKE_HARDALLOY_SWORD`, `MAKE_CHAIN_SHOTGUN` → `MAKE_SCATTER_CANNON`, `MAKE_HEAVY_SMG` → `MAKE_MAG_CARBINE`, `MAKE_MINIGUN` → `MAKE_SPINNER_RACK`, `MAKE_CHARGE_RIFLE` → `MAKE_PULSE_CARBINE`, `MAKE_FRAG` → `MAKE_SHRAPNEL_CHARGE`, `MAKE_INCENDIARY` → `MAKE_EMBER_THROWER`, `MAKE_DOOMSDAY` → `MAKE_SKYFALL_BATTERY`, `MAKE_MARINE` → `MAKE_TRENCH_ARMOR`, `MAKE_MARINE_HELM` → `MAKE_TRENCH_HELMET`, `MAKE_RECON_HELM` → `MAKE_GHOSTWEAVE_HELMET`, `MAKE_POWER_ARMOR` → `MAKE_EXOSUIT`, `MAKE_POWER_HELM` → `MAKE_EXO_HELMET`, `MAKE_AUTOPISTOL` → `MAKE_SNAP_PISTOL`, `MAKE_NUTRIENT` → `MAKE_VAT_MASH`, `MAKE_RECON` → `MAKE_GHOSTWEAVE_ARMOR`.

### 5.1 Round 6 renamed constants (identifiers, in place)

Round 6 renamed these identifiers so that each follows the Stillmere label of its constant (round 5 kept them). Each keeps its position in its enum, so its ordinal is unchanged; `NamesTest.renamedIdentifiersKeepTheirOrdinals` pins 21 of them. Enums are saved by ordinal and skill indices are saved too, so no save needs a translation for these. Qualified references were changed in `sim/`, `app/` and the tests. `app/` was edited by the same rule but is not compiled here. Two offset constants outside any enum were renamed too: `WIMP_SHOCK_OFFSET` to `TENDER_FRAME_SHOCK_OFFSET`, `HOSPITAL_BED_TEND_OFFSET` to `INFIRMARY_COT_TEND_OFFSET`. Labels that changed are in section 6.3.

* **SkillType** (12): SHOOTING → MARKSMANSHIP, MELEE → CLOSE_COMBAT, CONSTRUCTION → ARCHITECTURE, MINING → QUARRYING, COOKING → GALLEY, PLANTS → HORTICULTURE, ANIMALS → HUSBANDRY, CRAFTING → WORKMANSHIP, ARTISTIC → ARTISTRY, MEDICINE → HEALING, SOCIAL → RAPPORT, INTELLECTUAL → SCHOLARSHIP.
* **HediffKind** (22): FLU → CHEST_COUGH, PLAGUE → BOIL_SICKNESS, MALARIA → MARSH_FEVER, SLEEPING_SICKNESS → HEAVY_DROWSE, FOOD_POISONING → SPOILED_STOMACH, GUT_WORMS → GUT_FLUKES, MUSCLE_PARASITES → TISSUE_LARVAE, HYPOTHERMIA → COLD_CORE, HEATSTROKE → SUNSTRUCK, MALNUTRITION → STARVED_FRAME, TOXIC_BUILDUP → TOXIN_LOAD, BLOOD_LOSS → BLEED_OUT, ALCOHOL_HIGH → BREW_HAZE, ALCOHOL_TOLERANCE → BREW_RESISTANCE, ALCOHOL_ADDICTION → BREW_DEPENDENCE, WITHDRAWAL → CRAVING_ACHE, BAD_BACK → SPINE_STRAIN, ARTHRITIS → STIFF_JOINTS, CATARACT → CLOUDED_LENS, HEARING_LOSS → DULLED_HEARING, DEMENTIA → MEMORY_FADE, ANESTHESIA → NUMB_SLEEP.
* **Trait** (30): HARD_WORKER → BRISK_HANDS, LAZY → IDLE_STREAK, TOUGH → THICK_HIDE, WIMP → TENDER_FRAME, OPTIMIST → SUNNY_OUTLOOK, PESSIMIST → GREY_OUTLOOK, NIMBLE → LIGHT_STEPPED, BRAWLER → STREET_SCRAPPER, KIND → WARM_HEART, ABRASIVE → SHARP_TONGUE, GOURMAND → HEARTY_APPETITE, NUDIST → BARE_SKINNED, PYROMANIAC → FLAME_DRAWN, CANNIBAL → FLESH_EATER, SANGUINE → BUOYANT, DEPRESSIVE → HEAVY_HEARTED, BEAUTIFUL → STRIKING_LOOKS, UGLY → PLAIN_LOOKS, PSYCHOPATH → UNMOVED, BLOODLUST → BATTLE_HUNGRY, CARNIVORE → MEAT_FIRST, TRANSHUMANIST → MACHINE_MINDED, GAY → SAME_SEX_DRAWN, BISEXUAL → BOTH_DRAWN, ASEXUAL → UNROMANTIC, VOLATILE → SHORT_FUSE, SOCIABLE → EASY_COMPANY, GREEDY → ACQUISITIVE, ASCETIC → PLAIN_LIVING, JOGGER → LONG_STRIDER.
* **Research** (31): SMITHING → FORGEWORK, TAILORING → STITCHWORK, ELECTRICITY → GALVANICS, FIREARMS → POWDER_ARMS, ADV_FIREARMS → RIFLED_BARRELS, MACHINING → LATHE_WORK, MORTARS → INDIRECT_FIRE, HYDROPONICS → SOILLESS_GROWING, BIONICS → AUGMENTATION, BIONICS_BASIC → LIMB_CRAFTING, BREWING → MASH_CRAFT, STONECUTTING → ROCK_SPLITTING, MEDICINE → PHARMACOLOGY, FABRICATION → PART_ASSEMBLY, EXPLOSIVES → BLAST_CHEMISTRY, ROCKETS → PROPELLANT_LIFT, SCULPTING → STONE_CARVING, CARPETS → RUG_WEAVING, BOWS → STAVE_AND_STRING, CREMATION → PYRE_RITES, COMPLEX_FURNITURE → JOINERY_DETAIL, COMPLEX_CLOTHING → LAYERED_APPAREL, SOLAR_POWER → SUNLIGHT_HARVESTING, WIND_POWER → BREEZE_HARVESTING, BATTERIES → CELL_STORAGE, HEAVY_WEAPONS → SIEGE_ASSEMBLY, SHIP_ENGINE → THRUST_DRIVE, SHIP_REACTOR → CORE_FURNACE, SHIP_COMPUTER → NAVIGATION_BRAIN, LAVISH_COOKING → BANQUET_COOKERY, RECURVE → HORN_BACKING.
* **BuildDef** (23): BREWERY → MASH_WORKS, TELEVISION → PICTURE_BOX, MORTAR → LOBBING_TUBE, SOFA → LOUNGE_SEAT, CREMATORIUM → PYRE_HOUSE, BUTCHER_TABLE → CARVING_SLAB, CHESS_TABLE → TACTICS_BOARD, TORCH_LAMP → PITCH_POST, STANDING_LAMP → POLE_LANTERN, SLEEPING_SPOT → BEDROLL, HOSPITAL_BED → INFIRMARY_COT, TAILOR_BENCH → STITCH_FRAME, TRADE_BEACON → SUMMONS_FLARE, FERMENTING_BARREL → SOUR_CASK, RESEARCH_BENCH → STUDY_DESK, WIND_TURBINE → BREEZE_MILL, SHIP_CASKET → COLD_POD, SHIP_COMPUTER → NAVIGATION_BRAIN, SHIP_ENGINE → THRUST_DRIVE, SHIP_REACTOR → CORE_FURNACE, MACHINING → SHAPING_RIG, HYDROPONICS → GROW_TRAY, CRAFTING_SPOT → WORKSHOP_MAT.
* **Weapon** (6): KNIFE → POCKET_BLADE, REVOLVER → CYLINDER_GUN, LONGSWORD → BROAD_BLADE, RECURVE_BOW → HORNWOUND, MORTAR_SHELL → LOBBED_CHARGE, TURRET_GUN → PIVOT_CANNON.
* **ItemType** (5): W_KNIFE → W_POCKET_BLADE, W_REVOLVER → W_CYLINDER_GUN, W_LONGSWORD → W_BROAD_BLADE, HUMAN_MEAT → MANFLESH, INSECT_MEAT → CHITIN_CUTS.
* **Recipe** (5): MAKE_KNIFE → MAKE_POCKET_BLADE, MAKE_REVOLVER → MAKE_CYLINDER_GUN, MAKE_LONGSWORD → MAKE_BROAD_BLADE, MAKE_RECURVE → MAKE_HORNWOUND, MAKE_TUQUE → MAKE_KNIT_CAP.
* **Implant** (7): PEG_LEG → WOODEN_SHANK, HOOK_HAND → GAFF_GRIP, BIONIC_ARM → SERVO_LIMB, BIONIC_LEG → SERVO_STRIDER, BIONIC_EYE → OPTIC_LENS, BIONIC_EAR → COIL_LISTENER, BIONIC_HEART → PUMP_CORE.

### 5.2 Round 7 renamed constants (identifiers, in place)

Round 7 renamed these constants because each still echoed a RimWorld label phrase (round 6 had left them). Each keeps its position in its enum, so its ordinal is unchanged. `NamesTest.renamedIdentifiersOfTheVersion30RoundKeepTheirOrdinals` pins 26 of them, and `NamesTest.labelsChangedInTheVersion30RoundAreTheNewOnes` pins four labels. Qualified references were changed in `sim/`, `app/` and the tests with a word-boundary replacement. `app/` is not compiled here.

* **ItemType** (9): `MEAL_SIMPLE` → `MEAL_HEARTH_STEW`, `MEAL_FINE` → `MEAL_ROASTED_PLATTER`, `MEAL_LAVISH` → `MEAL_BANQUET_SPREAD`, `MEDS_HERBAL` → `MEDS_LEAF_POULTICE`, `MEDS_INDUSTRIAL` → `MEDS_STERILE_DRESSING`, `A_HELMET` → `A_RIVETED_HOOD`, `A_HEADDRESS` → `A_PLUMED_CROWN`, `A_BUTTONDOWN` → `A_COLLAR_TUNIC`, `A_MARINE_HELM` → `A_TRENCH_VISOR`.
* **Apparel** (4): `HELMET` → `RIVETED_HOOD`, `HEADDRESS` → `PLUMED_CROWN`, `BUTTONDOWN` → `COLLAR_TUNIC`, `MARINE_HELM` → `TRENCH_VISOR`.
* **Recipe** (7): `COOK_SIMPLE` → `COOK_HEARTH_STEW`, `COOK_FINE` → `COOK_ROASTED_PLATTER`, `COOK_LAVISH` → `COOK_BANQUET_SPREAD`, `MAKE_HELMET` → `MAKE_RIVETED_HOOD`, `MAKE_HEADDRESS` → `MAKE_PLUMED_CROWN`, `MAKE_BUTTONDOWN` → `MAKE_COLLAR_TUNIC`, `HERBAL_MEDS` → `MAKE_LEAF_POULTICE`.
* **BuildDef** (4): `SMITHY` → `COAL_FORGE`, `STOVE_FUEL` → `EMBER_OVEN`, `STOVE_ELEC` → `HOTPLATE`, `BILLIARDS` → `POCKET_BOARD`.
* **Research** (1): `DRUGS` → `TINCTURE_CHEMISTRY`.
* **Weapon** (1): `MOLOTOV` → `FIRE_FLASK`.
* **Found in the round 7 audit, renamed in an earlier round and not recorded in section 5:** **BuildDef** `TAILOR_BENCH` → `STITCH_FRAME` (the round 5 row still said "kept").

Labels changed in round 7 are in section 6.4. Some rows in sections 6.1 to 6.3 still say "kept" for a constant that a later round renamed; those rows now point to section 5 (round 7 check, open item 17).

### 5.3 Round 8 renamed identifiers

Round 8 renamed one property and three test names. No enum constant changed, so no ordinal moved.

* **Implant property:** `Implant.bionic` is now `Implant.augmented` (the flag is `true` for the five augmented implants: servo limb, servo strider, optic lens, coil listener and pump core, as set in `Surgery.kt`). It is a property, not an enum constant, and it is not saved.
* **Test names** (`HealthFidelityTest`, `GameTest`): `aPegLegRestoresPartOfWhatAmputationTook` is now `aWoodenShankRestoresPartOfWhatAmputationTook`; `bionicPartsBeatNaturalOnes` is now `augmentedPartsBeatNaturalOnes`; `surgeryInstallsABionicLeg` is now `surgeryInstallsAServoLeg`. `docs/fidelity/health.md` cites the new names. Round 9 renamed `aWoodenShankRestoresPartOfWhatAmputationTook` to `aReplacementLegRestoresPartOfWhatAmputationTook` (`HealthFidelityTest`), because the round 8 name still echoed a Stillmere label. `augmentedPartsBeatNaturalOnes` is kept: "augmented" is an ordinary word and is the name of `Implant.augmented`.

## 6. Renamed labels

Every label below was renamed. Old text is shown as the label or unlock text it was. Rows marked `r3` are in this section. Round 4
(`r4`) changes are in 6.1, round 5 (`r5`) in 6.2, round 6 (`r6`) in 6.3 and round 7 (`r7`) in 6.4. Where a later round renamed a label,
the later table gives the current label, and some rows of this section are the history of an earlier name. The **Origin** column gives
the tag for the claim about the old name: `V` (a web search snippet, URL inline), `M` (from memory, **UNVERIFIED**), or a note. The
**Tag** column is `C` (Stillmere's new name).

### Scenarios and difficulty

| Kind | Old label | New label | Constant (old to new) | Origin | Tag |
|---|---|---|---|---|---|
| Scenario | Crashlanded | Salvage crew | `CRASHLANDED` to `SALVAGE_CREW` | RimWorld scenario name. M, UNVERIFIED | C |
| Scenario | The lost tribe | Quiet enclave | `LOST_TRIBE` to `QUIET_ENCLAVE` (earlier intermediate names are not kept) | RimWorld scenario name. M, UNVERIFIED | C |
| Scenario | Rich explorer | Surplus start | `RICH_EXPLORER` to `SURPLUS_START` | RimWorld scenario name. M, UNVERIFIED | C |
| Scenario | Naked brutality | Bare start | `SOLO` kept | RimWorld scenario name. M, UNVERIFIED | C |
| Difficulty | Randy (label "Normal") | Normal | `RANDY` to `NORMAL` | The old constant echoed the RimWorld storyteller name. M, UNVERIFIED | C |
| Difficulty | Losing is fun | Relentless | `EXTREME` kept | RimWorld difficulty preset. M, UNVERIFIED. Search returned third-party pages only, no wiki URL | C |

Scenario descriptions were rewritten from scratch. Difficulty labels Peaceful, Easy, Normal and Challenge are generic words.

### Creatures (`Race`)

| Old label | New label | Constant (old to new) | Origin | Tag |
|---|---|---|---|---|
| Muffalo | Shagox | `MUFFALO` to `SHAGOX` | RimWorld animal. V https://rimworldwiki.com/wiki/Muffalo | C |
| Thrumbo | Skyhorn | `THRUMBO` to `SKYHORN` | RimWorld animal. V https://rimworldwiki.com/wiki/Thrumbo | C |
| Boomrat | Pufftail | `BOOMRAT` to `PUFFTAIL` | RimWorld animal. M, UNVERIFIED | C |
| Boomalope | Swellgrazer | `BOOMALOPE` to `SWELLGRAZER` | RimWorld animal. M, UNVERIFIED. Not confirmed by search | C |
| Megascarab | Hornbeetle | `MEGASCARAB` to `HORNBEETLE` | RimWorld insect. M, UNVERIFIED | C |
| Spelopede | Cavecrawler | `SPELOPEDE` to `CAVECRAWLER` | RimWorld insect. M, UNVERIFIED | C |
| Megaspider | Pitweaver | `MEGASPIDER` to `PITWEAVER` | RimWorld insect. V https://rimworldwiki.com/wiki/Megaspider | C |
| Scyther | Hooktalon | `SCYTHER` to `HOOKTALON` | RimWorld mechanoid. M, UNVERIFIED | C |
| Lancer | Pikewalker | `LANCER` to `PIKEWALKER` | RimWorld mechanoid. M, UNVERIFIED | C |
| Centipede | Siegeframe | `CENTIPEDE` to `SIEGEFRAME` | RimWorld mechanoid. M, UNVERIFIED | C |
| Warg | Ashhound | `WARG` to `ASHHOUND` | RimWorld animal. M, UNVERIFIED | C |

Real-world animal names were kept (wild boar, timber wolf, grizzly bear, capuchin, labrador retriever, husky, cougar, elk and the
other ordinary animals). They are section 7 entries, marked UNVERIFIED.

### Drugs, plants, materials and food

| Kind | Old label | New label | Constant (old to new) | Origin | Tag |
|---|---|---|---|---|---|
| Drug item | Flake | Pale dust | `FLAKE` to `PALE_DUST` | RimWorld drug. V https://rimworldwiki.com/wiki/Flake | C |
| Drug item | Yayo | White rush | `YAYO` to `WHITE_RUSH` | RimWorld drug. V https://rimworldwiki.com/wiki/Yayo | C |
| Drug item | Go-juice | Surge tonic | `GO_JUICE` to `SURGE_TONIC` | RimWorld drug, listed on the wiki Drugs page. V https://rimworldwiki.com/wiki/Drugs | C |
| Drug item | Wake-up | Dawn tablet | `WAKE_UP` to `DAWN_TABLET` | RimWorld drug. M, UNVERIFIED (the snippet did not describe it) | C |
| Drug item | Psychite tea | Lucid draught | `PSYCHITE_TEA` to `LUCID_DRAUGHT` | RimWorld drug. V https://rimworldwiki.com/wiki/Drugs | C |
| Drug item | Smokeleaf leaves | Dried hushmoss | `SMOKELEAF` to `HUSHMOSS` (item) | RimWorld plant. M, UNVERIFIED | C |
| Drug item | Smokeleaf joint | Rolled bundle | `JOINT` kept; recipe `ROLL_JOINT` | RimWorld item. M, UNVERIFIED | C |
| Plant | Smokeleaf | Hushmoss | `PlantType.SMOKELEAF` to `HUSHMOSS` | RimWorld plant. M, UNVERIFIED | C |
| Plant | Psychoid | Dreamweed | `PSYCHOID` to `DREAMWEED` (plant) | RimWorld plant. M, UNVERIFIED (not in the Drugs snippet) | C |
| Item | Psychoid leaves | Dreamweed sprigs | `PSYCHOID` to `DREAMWEED` (item) | RimWorld item. M, UNVERIFIED | C |
| Plant and item | Healroot | Mirewort | `HEALROOT` to `MIREWORT` (item and plant) | RimWorld plant. M, UNVERIFIED | C |
| Plant | Wild healroot | Feral mirewort | `WILD_HEALROOT` to `FERAL_MIREWORT` | RimWorld plant. M, UNVERIFIED | C |
| Fibre | Devilstrand | Wispsilk | `DEVILSTRAND` to `WISPSILK` (item), `DEVILSTRAND_CROP` to `WISPSILK_CROP`, `DEVILSTRAND_RESEARCH` to `WISPSILK_RESEARCH` | RimWorld fibre. M, UNVERIFIED | C |
| Material | Plasteel | Hardalloy | `PLASTEEL` to `HARDALLOY` (item and ore), `PLASTEEL_WALL` to `HARDALLOY_WALL` | RimWorld material. M, UNVERIFIED | C |
| Food | Packaged survival meal | Sealed ration | `MEAL_PACKAGED` to `MEAL_RATION` | RimWorld meal. M, UNVERIFIED | C |
| Food | Nutrient paste meal | Vat mash | `MEAL_NUTRIENT` to `MEAL_VAT_MASH` | RimWorld meal. M, UNVERIFIED | C |
| Food (r3) | Simple meal | Hearth stew | `MEAL_SIMPLE` to `MEAL_HEARTH_STEW` (r7) | RimWorld meal label. M, UNVERIFIED | C |
| Food (r3) | Fine meal | Roasted platter | `MEAL_FINE` to `MEAL_ROASTED_PLATTER` (r7) | RimWorld meal label. M, UNVERIFIED | C |
| Food (r3) | Lavish meal | Banquet spread | `MEAL_LAVISH` to `MEAL_BANQUET_SPREAD` (r7) | RimWorld meal label. M, UNVERIFIED | C |
| Food (r3) | Pemmican | Trail cake | `PEMMICAN` to `TRAIL_CAKE` | RimWorld item. M, UNVERIFIED | C |
| Food (r3) | Kibble | Feed pellets | `KIBBLE` to `FEED_PELLETS` | RimWorld item. M, UNVERIFIED | C |
| Food (r3) | Hay | Fodder | `HAY` kept | RimWorld item. M, UNVERIFIED. The word is common, the label is changed | C |
| Plant (r3) | Hay grass | Meadow grass | `HAYGRASS` to `MEADOW_GRASS` | RimWorld plant. M, UNVERIFIED | C |
| Medicine (r3) | Herbal medicine (item) | Leaf poultice | `MEDS_HERBAL` to `MEDS_LEAF_POULTICE` (r7) | RimWorld item. M, UNVERIFIED | C |

Recipes follow their items. Recipe labels changed as well: `Make pemmican` to `Make trail cake` (`COOK_PEMMICAN` to
`COOK_TRAIL_CAKE`), `Make kibble` to `Make feed pellets` (`COOK_KIBBLE` to `COOK_FEED_PELLETS`), `Cook simple meal` to `Cook hearth
stew`, `Cook fine meal` to `Cook roasted platter`, `Cook lavish meal` to `Cook banquet spread`, `Make herbal medicine` to `Make leaf
poultice`, `Make industrial medicine` to `Compound a dose`, `Make tuque` to `Make knit cap`, `Make tribal headdress` to `Make plumed
crown`, `Make simple helmet` to `Make riveted hood`, `Make plate armor` to `Make brigandine`, and the weapon, apparel and drug recipes
listed in the tables below.

### Research (r3 rows and earlier rows)

| Old label | New label | Constant (old to new) | Origin | Tag |
|---|---|---|---|---|
| Flake and yayo | Stimulant blends | `FLAKE_YAYO` to `STIMULANT_BLENDS` | RimWorld research. M, UNVERIFIED | C |
| Go-juice | Surge tonic | `GO_JUICE` to `SURGE_TONIC` | RimWorld research. M, UNVERIFIED | C |
| Plasteel blades | Hardalloy blades | `PLASTEEL_MELEE` to `HARDALLOY_BLADES` | RimWorld research. M, UNVERIFIED | C |
| Multi-analyzer | Analysis array | `MULTIANALYZER` to `ANALYSIS_ARRAY` | RimWorld research. M, UNVERIFIED | C |
| Cryptosleep | Stasis | `CRYPTOSLEEP` to `STASIS` | RimWorld research. M, UNVERIFIED | C |
| Nutrient paste | Vat culturing | `NUTRIENT_PASTE` to `VAT_CULTURING` | RimWorld research. M, UNVERIFIED | C |
| Charge weapons | Pulse arms | `CHARGE_WEAPONS` to `PULSE_ARMS` | RimWorld research. M, UNVERIFIED | C |
| Passive cooling | Frost venting | `PASSIVE_COOLING` to `FROST_VENTING` | RimWorld research. M, UNVERIFIED | C |
| Sun lamps | Glow panels | `SUN_LAMPS` to `GLOW_PANELS` | RimWorld research. M, UNVERIFIED | C |
| Deep drilling | Subsoil boring | `DEEP_DRILLING` to `SUBSOIL_BORING` | RimWorld research. M, UNVERIFIED | C |
| Autodoors | Shutter doors | `AUTODOORS` to `SHUTTER_DOORS` | RimWorld research. M, UNVERIFIED | C |
| Flak armor | Ribbed armor | `FLAK_ARMOR` to `RIBBED_ARMOR` | RimWorld research. M, UNVERIFIED | C |
| Marine armor (research) | Trench armor | `MARINE_ARMOR` to `TRENCH_ARMOR` | RimWorld research. M, UNVERIFIED | C |
| Powered armor (research) | Exosuit | `POWER_ARMOR` to `EXOSUIT` | RimWorld research. M, UNVERIFIED | C |
| Recon armor (tech) | Ghostweave armor | label only (`ADV_ARMOR` kept) | RimWorld research. M, UNVERIFIED | C |
| Ship computer core | Flight core | label only (`SHIP_COMPUTER` (renamed in a later round; section 5)) | RimWorld ship part. M, UNVERIFIED | C |
| Ship engine | Thrust drive | label only (`SHIP_ENGINE` (renamed in a later round; section 5)) | RimWorld ship part. M, UNVERIFIED | C |
| Ship reactor | Power reactor | label only (`SHIP_REACTOR` (renamed in a later round; section 5)) | RimWorld ship part. M, UNVERIFIED | C |
| Plate armor (research, r3) | Brigandine work | `PLATE_ARMOR` to `BRIGANDINE_WORK` | RimWorld research. M, UNVERIFIED | C |
| Pemmican (research, r3) | Trail cake | `PEMMICAN` to `TRAIL_CAKE` | RimWorld research. M, UNVERIFIED | C |
| Herbal medicine (research, r3) | Herbal remedies | `HERBAL_MEDICINE` to `HERBAL_REMEDIES` | RimWorld research. M, UNVERIFIED | C |
| Gun turrets (r3) | Swivel nests | `GUN_TURRETS` to `SWIVEL_NESTS` | RimWorld research. M, UNVERIFIED | C |
| Advanced firearms (r3) | Rifled barrels | `ADV_FIREARMS` to `RIFLED_BARRELS` (r6) | RimWorld research. M, UNVERIFIED | C |
| Drug production (r3) | Tincture chemistry | `DRUGS` to `TINCTURE_CHEMISTRY` (r7) | RimWorld research. M, UNVERIFIED | C |
| Medicine production (r3) | Pharmacology | `MEDICINE` kept | RimWorld research. M, UNVERIFIED | C |
| Electric stove (research, r3) | Powered griddle | `ELECTRIC_COOKING` kept | RimWorld research. M, UNVERIFIED | C |
| Lavish meals (r3) | Banquet cookery | `LAVISH_COOKING` (renamed in a later round; section 5) | RimWorld research. M, UNVERIFIED | C |
| Solar power (r3) | Sunlight harvesting | `SOLAR_POWER` (renamed in a later round; section 5) | RimWorld research. M, UNVERIFIED | C |
| Wind power (r3) | Breeze harvesting | `WIND_POWER` (renamed in a later round; section 5) | RimWorld research. M, UNVERIFIED | C |
| Heavy weapons (r3) | Heavy ordnance | `HEAVY_WEAPONS` (renamed in a later round; section 5) | RimWorld research. M, UNVERIFIED | C |
| Mace and spear (r3) | Blunt and pole arms | `BASIC_MELEE` kept | RimWorld research. M, UNVERIFIED | C |
| Complex furniture (r3) | Joinery detail | `COMPLEX_FURNITURE` (renamed in a later round; section 5) | RimWorld research. M, UNVERIFIED | C |
| Complex clothing (r3) | Layered apparel | `COMPLEX_CLOTHING` (renamed in a later round; section 5) | RimWorld research. M, UNVERIFIED | C |
| Long-range communication (r3) | Radio relay | `COMMS` kept | RimWorld research. M, UNVERIFIED | C |

The research unlock texts that name these items were rewritten with the new names (for example "Lever repeater, scatter cannon,
mag carbine", "Signal desk, trade signal", "Power cables, generators, lamps, heaters, coolers").

The earlier table row "Multi-analyzer" is kept for history. The earlier row for "ADVANCED_BENCH" is removed: the constant
`ADVANCED_BENCH` never existed in git `HEAD`. The only bench renamed was `HI_TECH_BENCH` (see the buildings table).

### Buildings

| Old label | New label | Constant (old to new) | Origin | Tag |
|---|---|---|---|---|
| Hi-tech research bench | Precision lab bench | `HI_TECH_BENCH` to `PRECISION_BENCH` | RimWorld building. M, UNVERIFIED | C |
| Firefoam popper | Quench vent | `FOAM_POPPER` to `QUENCH_VENT` | RimWorld building. M, UNVERIFIED | C |
| Nutrient paste dispenser | Mash tap | `NUTRIENT_DISPENSER` to `MASH_TAP` | RimWorld building. M, UNVERIFIED | C |
| Passive cooler | Frost vault | `PASSIVE_COOLER` to `FROST_VAULT` | RimWorld building. M, UNVERIFIED | C |
| Sun lamp | Glow panel | `SUN_LAMP` to `GLOW_PANEL` | RimWorld building. M, UNVERIFIED | C |
| Deep drill | Subsoil auger | `DEEP_DRILL` to `SUBSOIL_AUGER` | RimWorld building. M, UNVERIFIED | C |
| Autodoor | Shutter door | `AUTODOOR` to `SHUTTER_DOOR` | RimWorld building. M, UNVERIFIED | C |
| Comms console | Signal desk | `COMMS_CONSOLE` to `SIGNAL_DESK` | RimWorld building. M, UNVERIFIED | C |
| Drug lab | Tincture rig | `DRUG_LAB` to `TINCTURE_RIG` | RimWorld building. M, UNVERIFIED | C |
| Hydroponics basin | Grow tray | label only (`HYDROPONICS` (renamed in a later round; section 5), shared with research) | RimWorld building. M, UNVERIFIED | C |
| Machining table | Shaping rig | label only (`MACHINING` (renamed in a later round; section 5), shared with research) | RimWorld building. M, UNVERIFIED | C |
| Fabrication bench | Gearworks | label only (`FAB_BENCH` kept) | RimWorld building. M, UNVERIFIED | C |
| Mini-turret | Sentry pod | `MINI_TURRET` to `SENTRY_POD`, `MINI_TURRET_GUN` to `SENTRY_POD_GUN` | RimWorld building and weapon. M, UNVERIFIED | C |
| Cryptosleep casket | Cold pod | label only (`SHIP_CASKET` (renamed in a later round; section 5)) | RimWorld building. M, UNVERIFIED | C |
| Gun turret (r3) | Swivel nest | label only (`TURRET` kept) | RimWorld building. M, UNVERIFIED | C |
| Horseshoes pin (r3) | Quoits pitch | `HORSESHOES` to `QUOITS` | RimWorld building. M, UNVERIFIED | C |
| Fueled smithy (r3) | Coal forge | label only (`SMITHY` to `COAL_FORGE` (r7)) | RimWorld building. M, UNVERIFIED | C |
| Fueled stove (r3) | Fired range | label only (`STOVE_FUEL` to `EMBER_OVEN` (r7)) | RimWorld building. M, UNVERIFIED | C |
| Electric stove (building, r3) | Electric range | label only (`STOVE_ELEC` to `HOTPLATE` (r7)) | RimWorld building. M, UNVERIFIED | C |
| Sleeping spot (r3) | Bedroll | label only (`SLEEPING_SPOT` (renamed in a later round; section 5)) | RimWorld building. M, UNVERIFIED | C |
| Crafting spot (r3) | Workshop mat | label only (`CRAFTING_SPOT` (renamed in a later round; section 5)) | RimWorld building. M, UNVERIFIED | C |
| Torch lamp (r3) | Torch sconce | label only (`TORCH_LAMP` (renamed in a later round; section 5)) | RimWorld building. M, UNVERIFIED | C |
| Standing lamp (r3) | Pole lantern | label only (`STANDING_LAMP` (renamed in a later round; section 5)) | RimWorld building. M, UNVERIFIED | C |
| Chess table (r3) | Strategy table | label only (`CHESS_TABLE` (renamed in a later round; section 5)) | RimWorld building. M, UNVERIFIED | C |
| Billiards table (r3) | Cue table | label only (`BILLIARDS` to `POCKET_BOARD` (r7)) | RimWorld building. M, UNVERIFIED | C |
| Butcher table (r3) | Carving slab | label only (`BUTCHER_TABLE` (renamed in a later round; section 5)) | RimWorld building. M, UNVERIFIED | C |
| Stonecutter table (r3) | Rock dresser | label only (`STONECUTTER` kept) | RimWorld building. M, UNVERIFIED | C |
| Sculptor's table (r3) | Chisel bench | `SCULPTOR_TABLE` kept | RimWorld building. M, UNVERIFIED | C |
| Trade beacon (r3) | Trade signal | label only (`TRADE_BEACON` (renamed in a later round; section 5)) | RimWorld building. M, UNVERIFIED | C |
| Power conduit (r3) | Power cable | label only (`CONDUIT` kept) | RimWorld building. M, UNVERIFIED | C |
| Solar generator (r3) | Sun array | label only (`SOLAR_PANEL` kept) | RimWorld building. M, UNVERIFIED | C |
| Wind turbine (r3) | Breeze mill | label only (`WIND_TURBINE` (renamed in a later round; section 5)) | RimWorld building. M, UNVERIFIED | C |
| Wood-fired generator (r3) | Log-burning engine | label only (`WOOD_GENERATOR` kept) | RimWorld building. M, UNVERIFIED | C |
| Spike trap (r3) | Pitfall stakes | label only (`TRAP_SPIKE` kept) | RimWorld building. M, UNVERIFIED | C |
| Deadfall trap (r3) | Log snare | label only (`TRAP_DEADFALL` kept) | RimWorld building. M, UNVERIFIED | C |
| Fermenting barrel (r3) | Fermenting vat | label only (`FERMENTING_BARREL` (renamed in a later round; section 5)) | RimWorld building. M, UNVERIFIED | C |

### Weapons and apparel

| Kind | Old label | New label | Constant (old to new) | Origin | Tag |
|---|---|---|---|---|---|
| Weapon | Greatbow | Longstave | `GREATBOW` to `LONGSTAVE` (`W_GREATBOW` to `W_LONGSTAVE`) | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Autopistol | Snap pistol | `AUTOPISTOL` to `SNAP_PISTOL` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Gladius | Stabsword | `GLADIUS` to `STABSWORD` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Plasteel sword | Hardalloy sword | `PLASTEEL_SWORD` to `HARDALLOY_SWORD` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Chain shotgun | Scatter cannon | `CHAIN_SHOTGUN` to `SCATTER_CANNON` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Heavy SMG | Mag carbine | `HEAVY_SMG` to `MAG_CARBINE` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Minigun | Spinner rack | `MINIGUN` to `SPINNER_RACK` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Charge rifle | Pulse carbine | `CHARGE_RIFLE` to `PULSE_CARBINE` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Incendiary launcher | Ember thrower | `INCENDIARY_LAUNCHER` to `EMBER_THROWER` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Frag grenades | Shrapnel charges | `FRAG_GRENADE` to `SHRAPNEL_CHARGE` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Doomsday rocket launcher | Skyfall battery | `DOOMSDAY` to `SKYFALL_BATTERY` | RimWorld weapon. Gap report 2.2. M, UNVERIFIED | C |
| Weapon | Siege rocket launcher | Skyfall battery | `SIEGE_ROCKET` to `SKYFALL_BATTERY` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Charge lance | Pulse pike | `LANCE` to `PULSE_PIKE` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon | Mechanoid blades | Razor limbs | label only (`BLADE` kept) | RimWorld term. M, UNVERIFIED | C |
| Weapon | Mechanoid cannon | Bolt spitter | label only (`MECH_GUN` kept) | RimWorld term. M, UNVERIFIED | C |
| Weapon (r3) | Pump shotgun | Riot gun | `W_SHOTGUN`, `SHOTGUN` labels | RimWorld weapon. M, UNVERIFIED | C |
| Weapon (r3) | Machine pistol | Spray pistol | `W_SMG`, `SMG` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon (r3) | Assault rifle | Burst rifle | `W_RIFLE`, `RIFLE` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon (r3) | Light machine gun | Bipod gun | `W_LMG`, `LMG` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon (r3) | Sniper rifle | Scoped rifle | `W_SNIPER`, `SNIPER` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon (r3) | Bolt-action rifle | Bolt carbine | `W_BOLT`, `BOLT_RIFLE` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon (r3) | Lever-action rifle | Lever repeater | `W_LEVER`, `LEVER_RIFLE` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon (r3) | Short bow | Hand bow | `W_BOW`, `BOW` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon (r3) | Recurve bow | Laminated bow | `W_RECURVE`, `RECURVE_BOW` | RimWorld weapon. M, UNVERIFIED | C |
| Weapon (r3) | Turret (weapon) | Swivel gun | `TURRET_GUN` (renamed in a later round; section 5) | RimWorld turret weapon. M, UNVERIFIED | C |
| Apparel | Tribalwear | Woven wrap | label only (`TRIBAL` kept) | RimWorld apparel. M, UNVERIFIED | C |
| Apparel | Parka | Padded coat | `PARKA` to `PADDED_COAT` | RimWorld apparel. M, UNVERIFIED | C |
| Apparel | Duster | Long coat | `DUSTER` to `LONG_COAT` | RimWorld apparel. M, UNVERIFIED | C |
| Apparel | Cowboy hat | Wide-brim hat | `COWBOY_HAT` to `WIDE_BRIM_HAT` | RimWorld apparel. M, UNVERIFIED | C |
| Apparel | Flak vest, pants, jacket | Ribbed vest, leggings, jacket | `FLAK_VEST` to `RIBBED_VEST`, `FLAK_PANTS` to `RIBBED_LEGGINGS`, `FLAK_JACKET` to `RIBBED_JACKET` | RimWorld apparel. M, UNVERIFIED | C |
| Apparel | Marine armor and helmet | Trench armor, Trench helmet | `MARINE_ARMOR` to `TRENCH_ARMOR`; helmet constant `MARINE_HELM` to `TRENCH_VISOR` (r7) | RimWorld apparel. M, UNVERIFIED | C |
| Apparel | Recon armor and helmet | Ghostweave armor, helmet | `RECON_ARMOR` to `GHOSTWEAVE_ARMOR`, `RECON_HELM` to `GHOSTWEAVE_HELM` | RimWorld apparel. M, UNVERIFIED | C |
| Apparel | Powered armor and helmet | Exosuit, exo helmet | `POWER_ARMOR` to `EXOSUIT`, `POWER_HELM` to `EXO_HELM` | RimWorld apparel. M, UNVERIFIED | C |
| Apparel (r3) | Tuque | Knit cap | `TUQUE` to `KNIT_CAP` | RimWorld apparel. M, UNVERIFIED | C |
| Apparel (r3) | Simple helmet | Riveted hood | label only (`HELMET` to `RIVETED_HOOD` (r7)) | RimWorld apparel. M, UNVERIFIED | C |
| Apparel (r3) | Plate armor | Brigandine | `PLATE_ARMOR` to `BRIGANDINE` | RimWorld apparel. M, UNVERIFIED | C |
| Apparel (r3) | Tribal headdress | Plumed crown | label only (`HEADDRESS` to `PLUMED_CROWN` (r7)) | RimWorld apparel. M, UNVERIFIED | C |
| Apparel (r3) | Button-down shirt | Oxford shirt | label only (`BUTTONDOWN` to `COLLAR_TUNIC` (r7)) | RimWorld apparel. M, UNVERIFIED | C |

Club, Spear and Mace are kept as generic words (section 7). The other single-word clothing and weapon names were renamed in round 4 (section 6.1).

### Traits

| Old label | New label | Constant (old to new) | Origin | Tag |
|---|---|---|---|---|
| Slowpoke | Plodder | `SLOW_WALKER` kept | RimWorld trait. M, UNVERIFIED | C |
| Tortured artist | Brooding muse | `TORTURED_ARTIST` to `BROODING_MUSE` | RimWorld trait. M, UNVERIFIED | C |
| Iron-willed | Steel nerves | `IRON_WILLED` to `STEEL_NERVES` | RimWorld trait. M, UNVERIFIED | C |
| Careful shooter | Steady aim | `CAREFUL_SHOOTER` to `STEADY_AIM` | RimWorld trait. M, UNVERIFIED | C |
| Too smart | Keen mind | `TOO_SMART` to `KEEN_MIND` | RimWorld trait. M, UNVERIFIED | C |
| Night owl | Nocturnal | `NIGHT_OWL` to `NOCTURNAL` | RimWorld trait. M, UNVERIFIED | C |
| Super-immune | Hardy body | `SUPER_IMMUNE` to `HARDY_BODY` | RimWorld trait. M, UNVERIFIED | C |
| Green thumb | Garden sense | `GREEN_THUMB` to `GARDEN_SENSE` | RimWorld trait. M, UNVERIFIED | C |
| Trigger-happy | Quick trigger | `TRIGGER_HAPPY` to `QUICK_TRIGGER` | RimWorld trait. M, UNVERIFIED | C |
| Fast learner | Eager pupil | `FAST_LEARNER` to `EAGER_PUPIL` | RimWorld trait. M, UNVERIFIED | C |
| Slow learner | Slow study | `SLOW_LEARNER` to `SLOW_STUDY` | RimWorld trait. M, UNVERIFIED | C |

Trait descriptions were rewritten in round 4 (section 9, item 7).

### Backstory titles (`Pawn.kt`, `Backstories`)

Only the titles below changed. Each is saved as text, so each has a translation entry in section 3.

| Old title | New title | Changed in | Origin | Tag |
|---|---|---|---|---|
| Vatgrown soldier | Cradle-line recruit | 26 | RimWorld childhood backstory. M, UNVERIFIED (the search snippet did not list this title) | C |
| Urchin | Street kid | 26 | RimWorld childhood backstory. V https://rimworldwiki.com/wiki/List_of_childhood_backstories | C |
| Tribal child | Camp foundling | 26 | RimWorld childhood backstory. M, UNVERIFIED | C |
| Pit fighter | Ringside tyke | 26 | M, UNVERIFIED. Not confirmed by search | C |
| Space-born tinker | Hull scrapper | 26 | M, UNVERIFIED. Not confirmed by search | C |
| Cargo ship brat | Hold-born ward | 26 | M, UNVERIFIED | C |
| Farm kid (r3) | Furrow-born | 27 | RimWorld childhood backstory. M, UNVERIFIED | C |
| Medical student (r3) | Infirmary helper | 27 | RimWorld backstory. M, UNVERIFIED | C |
| Wasteland scavenger (r3) | Ruin picker | 27 | RimWorld backstory. M, UNVERIFIED | C |
| Herder's child (r3) | Pasture-born | 27 | RimWorld backstory. M, UNVERIFIED | C |
| Colonial guard (r3) | Palisade watch | 27 | RimWorld backstory. M, UNVERIFIED. The lower-case form (entry T12) is in the version 25 fixture | C |
| Beast tamer (r3) | Stockman | 27 | RimWorld backstory. M, UNVERIFIED | C |
| Pirate deckhand (r3) | Sloop hand | 27 | RimWorld backstory. M, UNVERIFIED | C |

Adult titles not listed here were kept (section 7).

### Thought labels, events and alerts

| Kind | Old text | New text | Changed in | Origin | Tag |
|---|---|---|---|---|---|
| Thought (saved) | Psychite buzz | Lucid high | 26 | RimWorld drug high. M, UNVERIFIED | C |
| Thought (saved) | Flake rush | Pale dust high | 26 | RimWorld drug high. M, UNVERIFIED | C |
| Thought (saved) | Yayo rush | White rush high | 26 | RimWorld drug high. M, UNVERIFIED | C |
| Thought (saved, r3) | Ate a fine meal | Ate a roasted platter | 27 | RimWorld meal wording. M, UNVERIFIED | C |
| Thought (saved, r3) | Ate kibble | Ate feed pellets | 27 | RimWorld item. M, UNVERIFIED | C |
| Thought (saved, r3) | Played horseshoes | Played quoits | 27 | RimWorld recreation. M, UNVERIFIED | C |
| Thought (saved, r3) | Psychic drone | Mind hum | 27 | RimWorld event and thought term. M, UNVERIFIED | C |
| Thought (saved, r3) | Psychic soothe | Mind balm | 27 | RimWorld event and thought term. M, UNVERIFIED | C |
| Thought (saved, r3) | Mellow | Hushed calm | 27 | RimWorld drug thought. M, UNVERIFIED | C |
| Drug (r3) | Toxic buildup (hediff) | Toxin load | not saved as text; its death text is G09 (round 8) | RimWorld hediff. M, UNVERIFIED | C |
| Damage (r3) | Toxic burn | Acid scald | not saved as text | RimWorld damage name. M, UNVERIFIED | C |
| Disease (r3) | Gut worms | Intestinal flukes (r7; "Gut flukes" was shared first word) | not saved as text | RimWorld disease. M, UNVERIFIED | C |
| Disease (r3) | Muscle parasites | Tissue larvae | not saved as text | RimWorld disease. M, UNVERIFIED | C |
| Raid (r3) | Mechanoids (raid label) | Iron swarm | not saved | RimWorld term. M, UNVERIFIED | C |
| Event (r3) | The mechanoid cluster has awoken | The iron swarm has awoken | not saved | RimWorld term. M, UNVERIFIED | C |
| Event (r3) | A mechanoid ship has crashed | An iron hulk has crashed | not saved | RimWorld term. M, UNVERIFIED | C |
| Alert (r3) | (manhunter!) | (rogue!) | not saved | RimWorld mechanic. M, UNVERIFIED | C |
| Event text (r3) | has gone manhunter | has gone rogue | not saved | RimWorld mechanic. M, UNVERIFIED | C |

Hediffs for the drug highs keep the drug word and the template from section 1 (`HUSHMOSS_HIGH`, `LUCID_HIGH`, `PALE_DUST_HIGH`,
`WHITE_RUSH_HIGH`, `SURGE_TONIC_HIGH`, `DAWN_TABLET_HIGH`, and their `_ADDICTION` forms).

### 6.1 Round 4 (`r4`): labels changed in this round

Each row gives the label as it stood before round 4, the new label, and the constant. No constant changed in round 4. Every new label
is tag `C` (Stillmere's own name). Where an earlier table below lists one of these old labels, the table keeps its own round's label
and this table gives the current one.

**Traits** (`Trait`, in `Defs.kt`; descriptions were also rewritten, in original wording, for every trait):

| Before r4 | New label | Constant |
|---|---|---|
| Industrious | Brisk hands | `HARD_WORKER` |
| Lazy | Idle streak | `LAZY` |
| Tough | Thick hide | `TOUGH` |
| Wimp | Tender frame | `WIMP` |
| Optimist | Sunny outlook | `OPTIMIST` |
| Pessimist | Grey outlook | `PESSIMIST` |
| Nimble | Light stepped | `NIMBLE` |
| Brawler | Street scrapper | `BRAWLER` |
| Kind | Warm heart | `KIND` |
| Abrasive | Sharp tongue | `ABRASIVE` |
| Gourmand | Hearty appetite | `GOURMAND` |
| Nudist | Bare-skinned | `NUDIST` |
| Pyromaniac | Flame-drawn | `PYROMANIAC` |
| Cannibal | Flesh-eater | `CANNIBAL` |
| Sanguine | Buoyant | `SANGUINE` |
| Depressive | Heavy-hearted | `DEPRESSIVE` |
| Beautiful | Striking looks | `BEAUTIFUL` |
| Ugly | Plain looks | `UGLY` |
| Psychopath | Unmoved | `PSYCHOPATH` |
| Bloodlust | Battle-hungry | `BLOODLUST` |
| Carnivore | Meat-first | `CARNIVORE` |
| Transhumanist | Machine-minded | `TRANSHUMANIST` |
| Gay | Same-sex drawn | `GAY` |
| Bisexual | Both-drawn | `BISEXUAL` |
| Asexual | Unromantic | `ASEXUAL` |
| Volatile | Short fuse | `VOLATILE` |
| Sociable | Easy company | `SOCIABLE` |
| Creative | Inventive eye | `CREATIVE` |
| Greedy | Acquisitive | `GREEDY` |
| Ascetic | Plain living | `ASCETIC` |
| Jogger | Long strider | `JOGGER` |

**Research** (`Research`, in `Defs.kt`; the unlock texts that name these items were rewritten too):

| Before r4 | New label | Constant |
|---|---|---|
| Smithing | Forgework | `SMITHING` |
| Tailoring | Stitchwork | `TAILORING` |
| Brewing | Mash craft | `BREWING` |
| Electricity | Galvanics | `ELECTRICITY` |
| Machining | Lathe work | `MACHINING` |
| Firearms | Powder arms | `FIREARMS` |
| Mortars | Indirect fire | `MORTARS` |
| Hydroponics | Soilless growing | `HYDROPONICS` |
| Prosthetics | Limb crafting | `BIONICS_BASIC` |
| Bionics | Augmentation | `BIONICS` |
| Sterile surgery | Clean cutting | `SURGERY` |
| Fabrication | Part assembly | `FABRICATION` |
| Explosives | Blast chemistry | `EXPLOSIVES` |
| Rockets | Propellant lift | `ROCKETS` |
| Cremation | Pyre rites | `CREMATION` |
| Stonecutting | Rock splitting | `STONECUTTING` |
| Bows | Stave and string | `BOWS` |
| Batteries | Cell storage | `BATTERIES` |
| Carpets | Rug weaving | `CARPETS` |
| Sculpting | Stone carving | `SCULPTING` |
| Ventilation | Air exchange | `VENTILATION` |
| Herbal remedies | Leaf lore | `HERBAL_REMEDIES` |
| Heavy ordnance | Siege assembly | `HEAVY_WEAPONS` |
| Ribbed armor (r3) | Ribbed plating | `RIBBED_ARMOR` |
| Ghostweave armor (r3, research) | Ghostweave loom | `ADV_ARMOR` |
| Laminated bow (r3, research) | Horn backing | `RECURVE` |
| Hardalloy blades (r3) | Alloy edging | `HARDALLOY_BLADES` |
| Trench armor (r3, research) | Trench weaving | `TRENCH_ARMOR` |
| Shutter doors (r3) | Hatch mechanics | `SHUTTER_DOORS` |
| Flight core (r3, research) | Navigation brain | `SHIP_COMPUTER` |
| Power reactor (r3, research) | Core furnace | `SHIP_REACTOR` |

**Buildings** (`BuildDef`, in `Buildings.kt`):

| Before r4 | New label | Constant |
|---|---|---|
| Shutter door | Slide hatch | `SHUTTER_DOOR` |
| Hospital bed | Infirmary cot | `HOSPITAL_BED` |
| Torch sconce | Pitch post | `TORCH_LAMP` |
| Strategy table | Tactics board | `CHESS_TABLE` |
| Television | Picture box | `TELEVISION` |
| Cue table | Pocket board | `BILLIARDS` |
| Fired range | Ember oven | `STOVE_FUEL` |
| Electric range | Hotplate | `STOVE_ELEC` |
| Research bench | Study desk | `RESEARCH_BENCH` |
| Precision lab bench | Precision lab | `PRECISION_BENCH` |
| Power cable | Live wire | `CONDUIT` |
| Trade signal (r3) | Summons flare | `TRADE_BEACON` |
| Mortar | Lobbing tube | `MORTAR` |
| Flight core (building) | Navigation brain | `SHIP_COMPUTER` |
| Power reactor (building) | Core furnace | `SHIP_REACTOR` |
| Brewery | Mash works | `BREWERY` |
| Fermenting vat (r3) | Sour cask | `FERMENTING_BARREL` |
| Crematorium | Pyre house | `CREMATORIUM` |
| Sofa | Lounge seat | `SOFA` |

**Implants** (`Implant`, in `Surgery.kt`; the operation names follow):

| Before r4 | New label | Constant |
|---|---|---|
| Peg leg | Wooden shank | `PEG_LEG` |
| Hook hand | Gaff grip | `HOOK_HAND` |
| Bionic arm | Servo limb | `BIONIC_ARM` |
| Bionic leg | Servo strider | `BIONIC_LEG` |
| Bionic eye | Optic lens | `BIONIC_EYE` |
| Bionic ear | Coil listener | `BIONIC_EAR` |
| Bionic heart | Pump core | `BIONIC_HEART` |

**Weapons** (`Weapon`, and the `ItemType` entries that carry them):

| Before r4 | New label | Constant (`Weapon`; `ItemType` is `W_` + the same name) |
|---|---|---|
| Knife | Pocket blade | `KNIFE` |
| Longsword | Broad blade | `LONGSWORD` |
| Hand bow | Reedstring | `BOW` |
| Revolver | Cylinder gun | `REVOLVER` |
| Snap pistol | Quickshot | `SNAP_PISTOL` |
| Bolt carbine | Latch carbine | `BOLT_RIFLE` |
| Spray pistol (r3) | Rapid sidearm | `SMG` |
| Burst rifle (r3) | Stutter gun | `RIFLE` |
| Bipod gun (r3) | Bipod hammer | `LMG` |
| Scoped rifle (r3) | Distance piece | `SNIPER` |
| Hardalloy sword | Hardalloy cutter | `HARDALLOY_SWORD` |
| Laminated bow (r3) | Hornwound | `RECURVE_BOW` |
| Lever repeater (r3) | Swing repeater | `LEVER_RIFLE` |
| Swivel gun (r3) | Pivot cannon | `TURRET_GUN` |
| Mortar (weapon) | Lobbed charge | `MORTAR_SHELL` |
| Mortar shell (item) | Lobbed bomb | `ItemType.SHELL` |

**Apparel** (`Apparel`, and the `ItemType` entries that carry them):

| Before r4 | New label | Constant (`Apparel`; `ItemType` is `A_` + the same name) |
|---|---|---|
| T-shirt | Cotton tee | `TSHIRT` |
| Oxford shirt (r3) | Collar tunic | `BUTTONDOWN` |
| Pants | Hose | `PANTS` |
| Wide-brim hat (r3) | Sunbrim | `WIDE_BRIM_HAT` |
| Ribbed vest (r3) | Quilted tabard | `RIBBED_VEST` |
| Ribbed jacket (r3) | Ribbed doublet | `RIBBED_JACKET` |
| Jacket | Canvas smock | `JACKET` |
| Trench armor (r3) | Trench mail | `TRENCH_ARMOR` |
| Trench helmet (r3) | Trench visor | `MARINE_HELM` |
| Ghostweave helmet (r3) | Ghostweave cowl | `GHOSTWEAVE_HELM` |
| Exo helmet (r3) | Exo casque | `EXO_HELM` |
| Ghostweave armor (r3) | Ghostweave shell | `GHOSTWEAVE_ARMOR` |

**Materials and food:** Stone chunk to Rough rock (`STONE_CHUNK`); Stone blocks to Cut stone (`STONE`); Human meat to Manflesh
(`HUMAN_MEAT`); Insect meat to Chitin cuts (`INSECT_MEAT`).

**Recipes** (labels only; constants unchanged): Make cut stone blocks to Dress cut stone; Make jacket to Make canvas smock; Make oxford
shirt to Make collar tunic; Make pants to Make hose; Make wide-brim hat to Make sunbrim; Make ribbed vest to Make quilted tabard; Make
ribbed jacket to Make ribbed doublet; Make T-shirt to Make cotton tee; Make knife to Make pocket blade; Make longsword to Make broad
blade; Make hand bow to Make reedstring; Make laminated bow to Make hornwound; Make lever repeater to Make swing repeater; Make revolver
to Make cylinder gun; Make snap pistol to Make quickshot; Make bolt carbine to Make latch carbine; Make spray pistol to Make rapid
sidearm; Make burst rifle to Make stutter gun; Make bipod gun to Make bipod hammer; Make scoped rifle to Make distance piece; Make
hardalloy sword to Make hardalloy cutter; Make trench armor to Make trench mail; Make trench helmet to Make trench visor; Make
ghostweave helmet to Make ghostweave cowl; Make exo helmet to Make exo casque; Make ghostweave armor to Make ghostweave shell; Make
mortar shells to Make lobbed bombs.

**Events, alerts, hints and texts:** the fallout event becomes a fume cloud ("Fume cloud! Stay indoors; crops will wither.", "The fume
cloud has settled.", and the alert "Fume cloud"); the hint about electricity names Galvanics; "Your research bench is idle" becomes "Your
study desk is idle"; "Build a research bench to start researching" becomes "Build a study desk to start researching"; "needs a precision
lab bench" becomes "needs a precision lab"; the crematorium log line becomes "The pyre house burned the body of ..."; the faction line
"Build a trade beacon first." becomes "Build a summons flare first."; the implant help text names Research "Limb crafting or Augmentation";
the app's help and dialogs say "infirmary cots"; the pirate gang "Rough-Knife gang" becomes "Rough-Blade gang". The saved texts that hold the old gang name are the camp texts S01-S03 (section 2), which are translated below version 28; the bare name is never saved (section 2).

**Saved thought labels** (r4, versions below 28 are translated; rows H10-H16 in section 3): Ate insect meat, Ate human flesh, Ate human
flesh (loved it), Gourmand's feast, Bionic upgrades, Optimist, Pessimist.

**Near-copy corrections (from the review, all made in r4).** Sniper rifle to Scoped rifle, Assault rifle to Burst rifle, Machine pistol
to Spray pistol, Short bow to Hand bow, Recurve bow to Laminated bow, Cowboy hat to Wide-brim hat, Flak vest and jacket to Ribbed vest and
jacket, Marine armor to Trench armor, Recon armor to Ghostweave armor, Flak armor to Ribbed armor, Plasteel sword to Hardalloy sword,
Light machine gun to Bipod gun, Chess table to Strategy table, Billiards table to Cue table, Electric stove to Electric range: each of
these is now renamed again, as the 6.1 tables show. Round 4 also renamed labels that failed the same rule in earlier rounds: Autopistol
(Snap pistol to Quickshot), Bolt-action rifle (Bolt carbine to Latch carbine), Lever-action rifle (Lever repeater to Swing repeater),
Torch lamp (Torch sconce to Pitch post), Power conduit (Power cable to Live wire), Fermenting barrel (Fermenting vat to Sour cask), Trade
beacon (Trade signal to Summons flare), Heavy weapons (Heavy ordnance to Siege assembly), Herbal medicine research (Herbal remedies to Leaf
lore), Autodoor (Shutter door to Slide hatch), Ship computer core (Flight core to Navigation brain), Ship reactor (Power reactor to Core
furnace), Hi-tech research bench (Precision lab bench to Precision lab), Research bench (Study bench to Study desk), Mortar shell (Lobbed
shell to Lobbed bomb), Hook hand (Gaff hand to Gaff grip), the bionic parts (to Servo limb, Servo strider, Optic lens, Coil listener, Pump
core), Turret gun (Swivel gun to Pivot cannon), Button-down shirt (Oxford shirt to Collar tunic), Fueled stove (Fired range to Ember oven).

### 6.2 Round 5 (`r5`): labels changed in this round

| Kind | Before r5 | New label | Constant | Origin | Tag |
|---|---|---|---|---|---|
| Building | Tailor bench | Stitch frame | `TAILOR_BENCH` now `STITCH_FRAME` (rename not recorded in round 5; listed in section 5.2) | RimWorld building. M, UNVERIFIED | C |
| Item | Industrial medicine | Sterile dressing | `MEDS_INDUSTRIAL` to `MEDS_STERILE_DRESSING` (r7) | RimWorld item. M, UNVERIFIED | C |

Buildings and items are saved by ordinal, so these two renames need no translation and no version change. The unlock text of the
Stitchwork research and a hint about padded coats now name the stitch frame. Near-copy check: "Stitch frame" shares neither the head
word nor the modifier of "Tailor bench"; "Sterile dressing" shares neither the head word nor the modifier of "Industrial medicine".

Round 5 also changed text that named an old label, without changing any label: the help text (a "mortars" line now says lobbing tubes,
and the research line names powder arms, soilless growing and augmentation), a comment that said "hay" (now fodder), test messages
that named the peg leg and prosthetics (now the wooden shank and limb crafting), a comment and a test message that used the trait word
"sociable" or the building words "hospital bed" (now gregarious or infirmary cot). The docs listed in section 8 use the new names too.

### 6.3 Round 6 renamed labels

Round 6 (`r6`) renamed the labels below. The skill names are the RimWorld skill list, which a WebSearch snippet lists (https://rimworldwiki.com/wiki/Skills, V, confirmed by a WebSearch snippet in round 7). The hediff, thought and event names are the author's recollection of RimWorld wording, so their origin is M, UNVERIFIED; the review did not check them against a source, and this round did not either. The new names are C. Thought labels and the temporary event name are saved text with a version 29 translation (H17-H24 and E01-E03 in section 3). Skill and hediff labels are not saved.

| Kind | Old label | New label | Saved? | Origin | Tag |
|---|---|---|---|---|---|
| Skill | Shooting | Marksmanship | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Skill | Melee | Close combat | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Skill | Construction | Architecture | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Skill | Mining | Quarrying | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Skill | Cooking | Galley | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Skill | Plants | Horticulture | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Skill | Animals | Husbandry | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Skill | Crafting | Workmanship | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Skill | Artistic | Artistry | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Skill | Medicine | Healing | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Skill | Social | Rapport | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Skill | Intellectual | Scholarship | no (index) | V, https://rimworldwiki.com/wiki/Skills | C |
| Hediff | Flu | Chest cough | not translated, G04 (graveyard, round 8) | M, UNVERIFIED | C |
| Hediff | Plague | Boil sickness | not translated, G05 (graveyard, round 8) | M, UNVERIFIED | C |
| Hediff | Malaria | Marsh fever | not translated, G06 (graveyard, round 8) | M, UNVERIFIED | C |
| Hediff | Sleeping sickness | Heavy drowse | not translated, G07 (graveyard, round 8) | M, UNVERIFIED | C |
| Hediff | Food poisoning | Spoiled stomach | no | M, UNVERIFIED | C |
| Hediff | Hypothermia | Cold core | no (the hediff label); its death text is G01 | M, UNVERIFIED | C |
| Hediff | Heatstroke | Sunstruck | no (the hediff label); its death text is G02 | M, UNVERIFIED | C |
| Hediff | Malnutrition | Starved frame | not translated, G08 (graveyard, round 8) | M, UNVERIFIED | C |
| Hediff | Blood loss | Bleed-out | no (the hediff label); its death text is G03 | M, UNVERIFIED | C |
| Hediff | Drunk | Brew haze | no | M, UNVERIFIED | C |
| Hediff | Alcohol tolerance | Brew resistance | no | M, UNVERIFIED | C |
| Hediff | Alcohol addiction | Brew dependence | no | M, UNVERIFIED | C |
| Hediff | Withdrawal | Craving ache | no | M, UNVERIFIED | C |
| Hediff | Bad back | Spine strain | no | M, UNVERIFIED | C |
| Hediff | Arthritis | Stiff joints | no | M, UNVERIFIED | C |
| Hediff | Cataract | Clouded lens | no | M, UNVERIFIED | C |
| Hediff | Hearing loss | Dulled hearing | no | M, UNVERIFIED | C |
| Hediff | Dementia | Memory fade | no | M, UNVERIFIED | C |
| Hediff | Anesthetic | Numb sleep | no | M, UNVERIFIED | C |
| Thought | Ate raw food | Gulped raw fare | yes, H17-H24 | M, UNVERIFIED | C |
| Thought | Ate without a table | Floor meal | yes, H17-H24 | M, UNVERIFIED | C |
| Thought | Kind words | Warm talk | yes, H17-H24 | M, UNVERIFIED | C |
| Thought | Beautiful aurora | Sky curtain glow | yes, H17-H24 | M, UNVERIFIED | C |
| Thought | Ugly surroundings | Drab rooms | yes, H17-H24 | M, UNVERIFIED | C |
| Thought | Tortured soul | Restless mind | yes, H17-H24 | M, UNVERIFIED | C |
| Thought | Drunk (thought) | Brew haze | yes, H17-H24 | M, UNVERIFIED | C |
| Thought | Withdrawal (thought) | Craving ache | yes, H17-H24 | M, UNVERIFIED | C |
| Temporary event | heat wave | scorching spell | yes, E01-E03 | M, UNVERIFIED | C |
| Temporary event | cold snap | deep chill | yes, E01-E03 | M, UNVERIFIED | C |
| Temporary event | volcanic winter | ash-veil winter, then smoke pall (r7) | yes, E03 (v29), E04 (v30) | M, UNVERIFIED | C |
| Event (alert) | Solar flare | Corona surge | no | M, UNVERIFIED | C |
| Event (weather label) | Thunderstorm | Lightning squall | no | M, UNVERIFIED | C |
| Event (log text) | Heat wave | Scorching spell | no | M, UNVERIFIED | C |
| Event (log text) | Cold snap | Deep chill | no | M, UNVERIFIED | C |
| Event (log text) | Volcanic winter | Ash-veil winter | no | M, UNVERIFIED | C |
| Event (log text) | Infestation | Pest bloom | no | M, UNVERIFIED | C |
| Event (log text) | Short circuit | Grid fault | no | M, UNVERIFIED | C |
| Event (log text) | Blight | Crop mildew | no | M, UNVERIFIED | C |
| Event (log text) | Wildfire | Grassland burn | no | M, UNVERIFIED | C |
| Event (log text) | Eclipse | Shadow passage | no | M, UNVERIFIED | C |
| Event (log text) | Trade caravan | Peddler convoy | no | M, UNVERIFIED | C |
| Event (log text) | Cargo pod | Supply capsule | no | M, UNVERIFIED | C |
| Event (log text) | Aurora | Sky curtain | no | M, UNVERIFIED | C |
| Event (log text) | Meteorite | Falling stone | no | M, UNVERIFIED | C |
| Raid kind (comment) | Assault (raid kind 0) | Rush | no | M, UNVERIFIED | C |
| Raid kind (raid text) | Sappers | Tunnelers | no | M, UNVERIFIED | C |
| Raid kind (comment; raid text 'A shelling party') | Siege (raid kind 2) | Shelling camp | no | M, UNVERIFIED | C |
| Raid kind (raid text 'Raiders in landers') | Drop pods (raid kind 3) | Landers | no | M, UNVERIFIED | C |
| Death cause | hypothermia | exposure | not translated, G01 (round 8) | M, UNVERIFIED | C |
| Death cause | heatstroke | overheating | not translated, G02 (round 8) | M, UNVERIFIED | C |

### 6.4 Round 7 renamed labels

Round 7 renamed the labels below. The thought labels and the temporary event names are saved text (section 3, rows H13, H14, H25-H28, E03 and E04). The others are not saved. The **Why** column gives the near-copy failure, or the echo that made the label a RimWorld name.

| Kind | Old label | New label | Saved? | Origin | Tag | Why |
|---|---|---|---|---|---|---|
| Thought | Hearty feast (the r4 label of `Gourmand's feast`) | Full belly | yes, H27 (version 29 text) and H13 | M, UNVERIFIED | C | Head word "feast" matched the RimWorld thought "Gourmand's feast" with a near-synonym modifier. |
| Thought | Engineered upgrades (the r4 label of `Bionic upgrades`) | Refit pride | yes, H28 and H14 | M, UNVERIFIED | C | Head word "upgrades" matched "Bionic upgrades". |
| Thought | Deep talk | Candid exchange | yes, H25 | M, UNVERIFIED | C | RimWorld social interaction name, recalled. |
| Thought | Shared a joke | Friendly ribbing | yes, H26 | M, UNVERIFIED | C | RimWorld social thought, recalled. |
| Temporary event | ash-veil winter (the r6 name of `volcanic winter`) | smoke pall | yes, E03 and E04 | M, UNVERIFIED (RimWorld event `volcanic winter`) | C | Head word "winter" matched "volcanic winter". |
| Event (alert and log text) | Solar flare | Corona surge | no | M, UNVERIFIED | C | Alert and log text in `Events.kt`. |
| Event (weather log text) | Thunderstorm | Lightning squall | no | M, UNVERIFIED | C | Log text in `Events.kt`. |
| Hediff (log text) | Food poisoning | Spoiled stomach | no | M, UNVERIFIED | C | Log text ("got a spoiled stomach") in `JobDrivers.kt` and `Caravans.kt`. The hediff is `SPOILED_STOMACH` (section 6.3). |
| Death cause | Blood loss | Bleed-out | not translated, G03 (round 8) | M, UNVERIFIED | C | Graveyard text in `Health.kt`. The hediff is `BLEED_OUT` (section 6.3). |
| Disease | Gut flukes (r3 label of `Gut worms`) | Intestinal flukes | no | M, UNVERIFIED | C | Shared first word "Gut" with "Gut worms". |
| Trait | Slow study (the r4 label of `Slow learner`) | Late bloomer | no | M, UNVERIFIED | C | Shared first word "Slow" with "Slow learner". |
| Plant | Meadow grass (the r3 label of `Hay grass`) | Sward | no | M, UNVERIFIED | C | Shared head word "grass" with "Hay grass". |
| Weapon | Molotov (kept in round 6) | Fire flask | no | M, UNVERIFIED (RimWorld `Molotov cocktail`) | C | Shared first word "Molotov". |

Test messages and comments that used the old words were changed too, in the same round: "malaria" to "marsh fever" (`IncidentTest`), "anesthetic" to "numbing" (`CombatPipelineTest`), "shutter door" to "slide hatch" (`RegionTest`), "ribbed vest" to "quilted tabard" (`CombatPipelineTest`), "blood loss" to "bleed-out" (`GameTest`, `HealthFidelityTest`), and a "solar flare" comment (`GameTest`).

### 6.5 Round 8 renamed labels

Round 8 renamed the labels below, which the review found were kept as possible RimWorld names (open item 10), and the death causes that were saved as text. The **Why** column gives the reason.

| Kind | Old label | New label | Saved? | Origin | Tag | Why |
|---|---|---|---|---|---|---|
| Backstory title (childhood) | Orphan | Unclaimed child | yes, T15 (version 31) | M, UNVERIFIED | C | Possible RimWorld title (open item 10). Renamed on the precaution of section 1; no search confirmed or ruled it out. |
| Backstory title (adult) | Field medic | Battlefield bandager | yes, T16 and T17 (version 31) | M, UNVERIFIED | C | Possible RimWorld title (open item 10). Shares no head or first word with the old title. |
| Backstory title (adult) | Drifter | Roadwalker | yes, T18 and T19 (version 31) | M, UNVERIFIED | C | Possible RimWorld title (open item 10). |
| Death cause | hypothermia, heatstroke, blood loss | exposure, overheating, bleed-out | not translated, G01-G03 (version 31) | M, UNVERIFIED | C | Death texts from `Mood.kt` and `Health.kt` (section 6.3). The lethal hediff path can also write another name for the same hediff; a graveyard entry holds one text, so the `Mood.kt` name is used for the old text. |
| Death cause (lethal illness label, lower-cased) | flu, plague, malaria, sleeping sickness, malnutrition, toxic buildup | chest cough, boil sickness, marsh fever, heavy drowse, starved frame, toxin load | not translated, G04-G09 (version 31) | M, UNVERIFIED | C | `Health.kt` uses the lethal hediff label, lower-cased, as the cause of death. The labels are the section 6.3 names. |

**Round 9 decisions on this section (review items 6 and 8).** The graveyard causes are no longer translated (section 2), so the rows
G01-G09 read "not translated". The three backstory titles stay renamed. The review rule keeps a generic title unless it is recalled as
RimWorld wording. This round `Orphan`, `Field medic` and `Drifter` could not be recalled as RimWorld backstory titles with confidence, and a
search of the wiki's childhood-backstory list did not confirm any of them. Its nearest hit was `Abandoned Child`, a RimWorld childhood title,
which shares the head word "child" with the new title `Unclaimed child`. Reverting the three titles would undo the version 31 rename and its
tested translation rows T15-T19, so round 9 keeps the round 8 renames. That conflicts with the rule as written, and the owner decides (section
9, item 10). A different new title would cost no save change, because T15-T19 translate the old saved text to whatever the table holds.

**Round 11 (task item 8): reasoning for the three titles.** `Pawn.kt` holds none of `Orphan`, `Field medic` or `Drifter`. Its backstories are
`Unclaimed child` (childhood), `Battlefield bandager` and `Roadwalker` (adult). The task's rule is that a generic English word stays unless it
is recalled as a RimWorld backstory title. Each old title is a generic English word for a person. Recall check: this round cannot recall
any of the three as a RimWorld backstory title with confidence, and the search in round 9 did not confirm one. On the rule, the three would
stay. The renames are kept anyway, because reverting them would undo the version 31 change and its tested rows T15-T19 (the save version,
the three translation rows and their tests). This conflicts with the rule as written, so the owner decides (section 9, item 10).

## 7. Generic labels kept (UNVERIFIED)

Each label in this section is an ordinary English word, or a real-world object, food, material, animal, biome, disease or weather term, used in its ordinary sense. Round 7 removed from this list every label that is a RimWorld name and not an ordinary word; the renamed ones are in sections 6.3 and 6.4. Each label here is UNVERIFIED as RimWorld wording: RimWorld may use the same words, and whether they are acceptable is an owner decision (open item 5).

* **Foods and materials:** Meat, Eggs, Milk, Beer, Rice, Corn, Potatoes, Strawberries, Cotton, Berries, Wood, Steel, Silver, Gold, Components, Cloth, Leather, Wool, Fodder (the hay item).
* **Weapons and armor (generic):** Club, Spear, Mace, Teeth, Claws, Horns, Trample. Turret is kept as a word (the building label is Swivel nest).
* **Furniture and building parts (generic):** Table, Chair, Stool, Bed, Armchair, Dresser, End table, Coffee table, Bookshelf, Wardrobe, Plant pot, Carpet, Door, Wall, Steel door, Steel wall, Stone wall, Wood floor, Stone tiles, Steel tiles, Campfire, Sandbags, Heater, Cooler, Battery, Grave, Small and large sculpture. Several are also RimWorld building names, but each is an ordinary word. Open item 5.
* **Biomes (real-world ecology terms):** Temperate forest, Boreal forest, Tropical rainforest, Arid shrubland, Tundra, Desert.
* **Terrain:** Flat, Small hills, Large hills, Impassable mountains.
* **Animals (real-world):** Wild boar, Timber wolf, Grizzly bear, Capuchin, Labrador retriever, Husky, Cougar, Elk, Caribou, Bison, Alpaca, Dromedary, Rhinoceros, and the other ordinary animals. The creatures that were RimWorld names are in section 6.
* **Diseases and body (real-world terms or Stillmere's own):** Frostbite, Intestinal flukes, Toxin load, Tissue larvae, and the other body names of `Body.kt`. The hediffs renamed in round 6 are in section 6.3.
* **Event and raid wording:** Raid is an ordinary word. The phenomenon words Aurora, Eclipse and Meteorite are kept in identifiers only (incident constants, open item 6); their player-visible text uses the new names (section 6.3).
* **Backstory titles (occupations, saved text):** Farmhand, Cook, Miner, Artist, Engineer, Hunter, Scholar, Smith, Trader, Surgeon, Bartender, Carpenter, Mercenary, Prospector, Gardener, Tailor, Warden, and the other adult titles in `Pawn.kt`. The three titles that may have been RimWorld titles were renamed in round 8 (section 6.5).
* **Thoughts (ordinary words, saved text):** Drug use, Cheerful, Gloomy, Lonely, Looks bad, Insulted by, and the other thoughts in `Mood.kt` and `Social.kt` that are not in section 6.4. UNVERIFIED (open item 10).
* **Category and job labels (ordinary words):** Medicine, Drugs, Weapons, Apparel, Production, Security, Ship, Joy, Art, Other, Mining, Crafting, Cooking, Plants, Animals, Social.
* **Skill names:** renamed in round 6 (section 6.3). Not kept.
* **Storytellers:** Marlowe, Juniper, Orrin. Original names (gap report 2.2).
* **Words kept in identifiers:** `TURRET`, `TRAP_SPIKE`, `TRAP_DEADFALL`, `FAB_BENCH`, `SCULPTOR_TABLE`, `STONECUTTER`, `SHAPING_RIG`, `CONDUIT`, `BATTERY`, `SOLAR_PANEL`, `ADV_ARMOR`, `MAKE_MEDS`, `TSHIRT`, `PANTS`, `JACKET`, `TRIBAL`, the `SURGERY` and `VENTILATION` research constants, and the others listed in open item 9. Identifiers are not literals, so the verify rule does not check them.

## 8. Verification (round 7)

* **Enum positions.** `NamesTest.renamedIdentifiersOfTheVersion30RoundKeepTheirOrdinals` pins the 26 constants of section 5.2; `renamedIdentifiersKeepTheirOrdinals` pins 21 from round 6, and `renamedConstantsKeepTheirOrdinals` the earlier ones. The positions were counted from the source with a script that reads the entries of each enum. The script gives the same position for the old and the new name of the renamed constants it was run on: `ItemType.MEAL_SIMPLE` is 21 in git `HEAD` and `MEAL_HEARTH_STEW` is 21 now; `MEDS_HERBAL` is 29 and `MEDS_LEAF_POULTICE` is 29; `Apparel.HELMET` is 11 and `RIVETED_HOOD` is 11.
* **Literal scan (round 7).** A script read every string literal in `sim/src/main`, `sim/src/test/kotlin` and `app/src/main`, and searched it for the old labels of sections 3 and 6 (368 phrases, the old column of every table) and for a second list of further RimWorld-style terms (103 terms). Before round 7 it found the old words of section 6.4 in literals (`blood loss`, `solar flare`, `thunderstorm`, `food poisoning`, `malaria`, `anesthetic`, `shutter door`, `ribbed vest`, `Deep talk`, `Shared a joke`, `Hearty feast`, `Engineered upgrades`, `Molotov`, `Meadow grass`, `Slow study`, and others). All were changed. After round 7 the hits are generic words only (for example `Kind`, `Animals`, `Plants`, `Medicine`, `Crafting`, `Cooking`, `Social`, `Mining`, `Melee`, `Shooting`, `Raid`, `Volcanic`, `Frostbite`, `Cheerful`, `Gloomy`, `Looks bad`, `Insulted by`, `Drug use`) and the occupations of section 7. The CRC-32 keys in `LegacyNames.kt` are the only old Stillmere text in code, and they are not literals.
* **Comment scan.** The same words in comments were searched in round 7. The hits were generic words (for example `withdrawal` in a `Mood.kt` comment, `construction` and `mining` in comments), and one comment about a solar flare, changed.
* **Old identifiers (round 7).** A grep for every old constant of sections 5 to 5.2 finds none in `sim/` or `app/`. The new names of section 5.2 are used in `app/` (10 references).
* **Key check (round 7).** A script compared the 47 keys in `LegacyNames.kt` with `old_text_cases.tsv`. Each key is the CRC-32 of the UTF-8 bytes of its old text, and each maps to the new text in the same row: 47 of 47 match, and no key is unused (section 3).
* **Near-copy check (round 7).** A script compared each (old, new) pair of sections 3, 6, 6.1, 6.2, 6.3 and 6.4 by head word and first word. It flagged 59 pairs. Most of them are intermediate names: the table rows give the name the label had in its round, and a later round changed it again (sections 6.1 and 6.2). A second check compared the current labels (the new labels that are still string literals in `sim/src/main`) with the RimWorld names. It flagged only the thought templates ("Ate ...", "Played ..."), which section 1 excludes, and `Gut flukes`, which was renamed. The other round 7 renames (section 6.4: `Hearty feast`, `Engineered upgrades`, `Deep talk`, `Shared a joke`, `ash-veil winter`, `Slow study`, `Meadow grass`, `Molotov`) were found by applying the same rule by hand. Close synonym pairs that pass the rule are listed in open item 16.
* **Saves.** `LegacyNamesMigrationTest` reads the version 25 (outside and HEAD-generated), 26, 27, 28 and 29 fixtures, the version 30 fixture and the version 25 graveyard fixture, and checks the translated titles, thoughts, temporary events, incidents and graveyard causes. Saving writes version 31. `ColonyMapSizeTest` is new (untracked, not in git `HEAD`). It checks that a save keeps the map size it stores.
* **Docs scan (round 7).** `README.md` and `docs/fidelity/*.md` (except this file) were searched for the old labels. The old names were changed where they named a RimWorld label: the gap report (the temperature event names, while the offsets +16, -17 and -13 are unchanged, the food hediff row, the raid kinds, the README quote in section 4), `health.md` (`anesthetic`, and the `blood loss` hediff, now `bleed-out`) and `README.md` (`Manhunter packs`, `ash-veil winters`, `crashed mechanoid ships`). Generic words were kept (for example `Medicine`, `Social`, `Animals`, `Plants`, `Mining`, and `withdrawal` as a drug effect).
* **App.** `app/` is not compiled (no Android SDK). Its references to the renamed constants were changed by the same word-boundary rule.
* **Tests.** `./gradlew :sim:test --console=plain` on the round 7 tree: 490 tests, 0 failures, 0 errors (`BUILD SUCCESSFUL`). The count includes the new `NamesTest` and `LegacyNamesMigrationTest` cases.
* **Tags.** Every M-tagged row is marked UNVERIFIED (grep check). Every V-tagged row has its URL inline (grep check, round 7). Section 10 gives the source of the V tags.

* **Round 9 checks.**
  * **Graveyard decision.** `SaveGame.read` reads graveyard entries without translation, and `LegacyNames` holds 52 `0x` keys (a grep count). A grep finds no `graveyardEntry` or `causes31` in `sim/`, `app/` or `docs/`.
  * **Web checks.** Round 9 ran three searches. The `Simple_meal` page appeared in a search result that gives 0.9 nutrition for a simple meal and 0.5 units of raw nutrition as its cost. The `Temperature` page appeared in a result that lists latitude, time of day, day of year and biome as the drivers of outdoor temperature. Neither page was read directly. The map size search found only third-party guides about Odyssey, so that row is M, UNVERIFIED. A search for `Orphan`, `Field medic` and `Drifter` found no confirmation.
  * **Docs changed in round 9.** `gap-report.md`: the map size vanilla values are marked M, UNVERIFIED (section 3.2); the hearth stew row cites the search result (3.5); the saved-text status in 2.2 names version 31 and the graveyard rule; section 11 has a round 9 bullet. `needs-mood.md`: the hearth stew note. `temperature-rooms.md`: the outdoor-temperature row's URL note, and its event labels (`smoke pall`, `deep chill`, `scorching spell`) with no RimWorld event names. `health.md`: the test name. `README.md`: line 12 restored to the committed sentence; the other round 8 README changes are kept. `names.md` itself.
  * **Labels in the docs.** The round 9 literal check of `gap-report.md`, `temperature-rooms.md`, `needs-mood.md` and `README.md` found no `ash-veil`, `cold snap`, `volcanic winter`, `heat wave`, `deep talk`, `kind words` or `shared a joke`. The one hit outside this file is the `joke` kind in `Social.kt`, which is an internal name (section 9, item 13).
  * **Pawn.kt.** `Pawn.kt` already holds `Unclaimed child`, `Battlefield bandager` and `Roadwalker` (round 8). It holds none of `Orphan`, `Field medic` and `Drifter`, so no code change was made there.

* **Round 10 checks.**
  * **V tags.** Every V cell in `gap-report.md`, `time.md`, `temperature-rooms.md` and `needs-mood.md` gives a URL in the same cell, or is
    M, UNVERIFIED (grep check). Section 10 gives the sources.
  * **Labels.** A grep of `docs/`, `README.md`, `app/` and `sim/src` for `ash-veil`, `cold snap`, `volcanic winter`, `heat wave`,
    `deep talk`, `kind words` and `shared a joke` finds none outside this file, the legacy folder and `LegacyNames.kt`. `temperature-rooms.md`
    uses `deep chill`, `smoke pall` and `scorching spell`. The one `joke` hit is the internal social kind in `Social.kt` (section 9, item 13).
  * **Attribution.** README line 12 keeps the sentence that names Claude and Anthropic (decision 7, section 9, item 14). Not yet confirmed
    by the owner.
  * **Test names.** `HealthFidelityTest` has `aReplacementLegRestoresPartOfWhatAmputationTook` and `augmentedPartsBeatNaturalOnes`. Neither
    `aPegLegRestoresPartOfWhatAmputationTook` nor `bionicPartsBeatNaturalOnes` is in the code.
  * **Pawn.kt.** The backstory titles there are `Unclaimed child`, `Battlefield bandager` and `Roadwalker`. `Orphan`, `Field medic` and
    `Drifter` are not in the code.
  * **Save tests.** The graveyard and log tests of section 2 pass. The test count is in section 14.

* **Round 11 checks (task and verdict on round 10).**
  * **Attribution (task item 7): README line 12 is kept.** Line 12 of `README.md` says the project is "built with Claude, an AI assistant by
    Anthropic". It is the project's own disclosure of how it was made. It is not attribution on a commit or a pull request, which is what the
    commit-identity rule in `CLAUDE.md` covers, and no commit in this round carries attribution. No other file names Claude or Anthropic as an
    author. The owner should confirm the narrower reading (section 9, item 14).
  * **V tags.** A script read every table row of `gap-report.md`, `temperature-rooms.md`, `time.md`, `needs-mood.md`, `health.md` and this file.
    Each row with a V cell has a URL in the same row. The V cells without a URL are in prose and in the legend lines, which describe the tag.
    The verdict's line numbers do not match the file, and the rows it names already carried their URL when this round started.
  * **Old labels.** A grep of `docs/`, `README.md`, `app/` and `sim/src` for `ash-veil`, `cold snap`, `volcanic winter`, `heat wave`, `deep
    talk`, `kind words`, `shared a joke`, `hypothermia`, `heatstroke` and the other old death causes finds no hit outside this file, the legacy
    folder and `LegacyNames.kt`. The code hits are identifiers, which are kept: `Events.kt` `thunderstorm()`, and the incident constants
    `THUNDERSTORM` and `VOLCANIC_WINTER` (section 4 and open item 6).
  * **Gates.** One gate for incident names, below 26 (`LegacyNames.incidentName`). `SaveGame` calls it for saves below 28. Section 2, the KDoc of
    `LegacyNames.kt` and the KDoc of `SaveGame.kt` state the same gate.
  * **Augmented flag.** `Implant.augmented` is true for the five augmented implants: servo limb, servo strider, optic lens, coil listener and
    pump core (`Surgery.kt`).
  * **Test sources.** No old scenario or label is written in `LegacyNamesMigrationTest.kt` (the old scenario name `LOST_TRIBE` is gone from its
    KDoc). The test names in `HealthFidelityTest.kt` are `aReplacementLegRestoresPartOfWhatAmputationTook` and `augmentedPartsBeatNaturalOnes`.
  * **New tests (round 11).** `LegacyNamesMigrationTest.anOldGraveyardStringStillLoadsUnchangedWhenItIsSavedNow`.
  * **Untracked files.** `git check-ignore` reports no rule for `docs/fidelity/names.md`, the five new Kotlin files or the 19 files of
    `sim/src/test/resources/legacy/`. The commit step's `git add -A` takes all of them.

## 9. Open items

1. **Not committed; untracked fixtures.** The change set is not committed (the request said not to commit). Every file in `sim/src/test/resources/legacy/` is untracked and must be added with it: `colony_v25.sav`, `colony_v25_expect.txt`, `colony_v25_head.sav`, `colony_v25_head_expect.txt`, `colony_v25_thoughts.sav`, `colony_v25_thoughts_expect.txt`, `colony_v26.sav`, `colony_v26_expect.txt`, `colony_v27.sav`, `colony_v27_expect.txt`, `colony_v28.sav`, `colony_v28_expect.txt`, `colony_v29.sav`, `colony_v29_expect.txt`, `colony_v30.sav`, `colony_v30_expect.txt`, `colony_v25_graves.sav`, `colony_v25_graves_expect.txt`, `colony_v25_adults.sav`, `colony_v25_adults_expect.txt`, `colony_v27_sites.sav`, `colony_v27_sites_expect.txt` and `old_text_cases.tsv`. Round 11 was asked not to commit, so these stay untracked. The commit step runs `git add -A`, which takes them together with `docs/fidelity/names.md`, `LegacyNames.kt`, `LegacyNamesMigrationTest.kt`, `NamesTest.kt` and `ColonyMapSizeTest.kt`. No `.gitignore` rule excludes any of them (`git check-ignore` finds none).
2. **Version 26 fixture.** Round 4 wrote `colony_v26.sav` with the current layout and the header set to 26 (section 2). The code that wrote version 26 was never committed, so this is not a file that the version 26 code wrote. The layout did not change, so the file has the same shape.
3. **Lower-case adult titles.** The cause is known. HEAD's `Game.kt:190` writes `adult.title.lowercase()` for every randomly made colonist, so new saves hold lower-case adult titles. Round 12 keyed the lower-case forms of T13 and T14 (T20 and T21), so every renamed adult title loads under its new name in either case. Round 14 maps each lower-case key to a lower-case new title (section 3), so a migrated adult title has the same form as a new colonist's (before round 14 the migrated form was capitalised). Owner decision: keep the lower case, or capitalise new colonists' titles (this changes new saves' text only). If the owner capitalises new colonists' titles, the five lower-case rows (T12, T17, T19, T20 and T21) must also change to the capitalised new text, and the tests that expect the lower-case forms must change with them.
4. **`colony_v25_thoughts.sav` provenance.** It came from a generator outside this repository and **cannot be regenerated from the repo**. Its rows are also covered by `colony_v25_head.sav`, which the HEAD procedure regenerates. The owner may delete the outside file once that is accepted; the existing tests read it.
5. **Kept generic labels.** Section 7 keeps ordinary words, real-world names, furniture words and occupations. The owner must decide whether that is acceptable under the goal "any other RimWorld proper name". Renaming them would change many player-visible labels.
6. **Incident constants saved by name.** `PSYCHIC_WAVE`, `MECH_CRASH`, `MANHUNTER_PACK` and the other incident constants keep their names. Renaming any of them needs a translation entry, as `THRUMBO` has (section 4). The ones that still match RimWorld event names are CARGO_POD, TRADE_CARAVAN, HEAT_WAVE, COLD_SNAP, SOLAR_FLARE, ECLIPSE, SHORT_CIRCUIT, BLIGHT, INFESTATION, VOLCANIC_WINTER, WILDFIRE, THUNDERSTORM, METEORITE, AURORA, TOXIC_FALLOUT, PSYCHIC_WAVE and MECH_CRASH. Their player-visible text was renamed (section 6.3 and 6.4).
7. **Trait descriptions and effect texts.** Round 4 rewrote every trait description in original wording. The numbers in the effects are unchanged. The descriptions were not compared with RimWorld's wording.
8. **Origins not confirmed.** Boomalope, Pit fighter, Space-born tinker, Wake-up, Psychoid, Psychic drone, Psychic soothe, Deep talk, Shared a joke, Hearty feast, Engineered upgrades, volcanic winter, Molotov, Slow learner and the thought labels of section 6 are M, UNVERIFIED. The difficulty origin has no wiki URL. The rimworldwiki.com host does not resolve from this sandbox, so only search snippets are cited (section 10).
9. **Identifiers.** Round 7 renamed the constants of section 5.2. Constants that still echo a RimWorld label phrase, but whose words are ordinary, are kept: `TURRET`, `TRAP_SPIKE`, `TRAP_DEADFALL`, `FAB_BENCH`, `SCULPTOR_TABLE`, `STONECUTTER`, `SHAPING_RIG`, `CONDUIT`, `BATTERY`, `SOLAR_PANEL`, `ADV_ARMOR`, `MAKE_MEDS` (the "Compound a dose" recipe), `TSHIRT`, `PANTS`, `JACKET`, `TRIBAL`, `SURGERY`, `VENTILATION`, `BASIC_MELEE`, `SMG`, `LMG`, `SNIPER`, `BOLT_RIFLE`, `HAY`, `FLAT`, `STONE_CHUNK`, `ELECTRIC_COOKING`, `Diet.CARNIVORE`, `ItemCat.MEDICINE`, the `NO_MEDICINE_*` constants, and Stillmere's own names (`GHOSTWEAVE_*`, `RIBBED_*`, `HARDALLOY_BLADES`, `SHUTTER_DOORS`, `TRENCH_ARMOR`, `BRIGANDINE`, `EXOSUIT`, `SKYFALL_BATTERY`). Whether generic words stay is an owner decision.
10. **Saved labels: the three titles renamed in round 8 (decision, not yet confirmed by the owner).** `Orphan`, `Field medic` and `Drifter` were renamed in round 8 (section 6.5), as a precaution. Round 9 could not recall them as RimWorld backstory titles with confidence, and a search did not confirm them. The review rule keeps a generic title unless it is recalled as RimWorld wording, and on that rule the three names would stay. The renames are kept instead, because reverting them would undo the version 31 rename and its tested translation rows T15-T19. Rounds 10 and 11 (task item 8) restate the same decision and the same reasons (section 6.5). The owner should confirm that choice. The thoughts Drug use, Insulted by, Cheerful, Gloomy, Looks bad and Lonely are kept as ordinary words (UNVERIFIED). Log lines are kept as saved (section 2, item 23).
11. **Vanilla reference names.** The gap report's vanilla columns and fidelity notes name RimWorld items, raids and events for comparison. They are recalled benchmarks, not Stillmere labels, and they are not in code. Where the gap report named a RimWorld label that the code no longer uses, it now refers to this file (round 7). The owner may ask for the rest to be removed.
12. **Map size.** `Game.MAP_SIZE` is 250, so a new colony is 250 by 250. Saves keep the width and height they store, so no save is resized (section 8). The colony test helpers build `Game.MAP_SIZE` colonies (`GameTest.newGame`, `JobAiTest.newGame`, `CombatModelTest.arena`, `CombatPipelineTest.arena` and `SkillsTraitsTest.arena`). Four seed-dependent tests were given setups that do not depend on the layout near home, and keep their seeds (map.md, Tests). The explicit-size generator tests keep the sizes they test (100, 120 by 90 and 150). The owner may want those seeds re-checked at 250 by 250.
13. **Social kinds.** The social kinds (`chitchat`, `insult`, `argument`, `joke`, `compliment`, `kind`, `deep`) in `Social.kt` are internal names. `kind` and `deep` may echo RimWorld interaction names (UNVERIFIED). Owner decision. The two thought labels of those interactions that were RimWorld-like were renamed in round 7 (section 6.4).
14. **Attribution in `README.md` (decision, rounds 9, 10 and 11; not yet confirmed by the owner).** Line 12 keeps the sentence that says the project was built with Claude, an AI assistant by Anthropic. Round 8 had removed it, and round 9 restores the committed sentence (review item 7). Round 11 keeps it again (task item 7; section 8). The reason is that the sentence is the project's own disclosure of how it was made. It is not attribution on a commit or a pull request, which is all the commit-identity rule covers. Open point for the owner: `CLAUDE.md` says "Never write Claude, Anthropic, or AI attribution anywhere" and then names commits and pull request descriptions. This round reads the rule as the narrower of the two. If the owner reads "anywhere" literally, the sentence must go.
15. **Commit.** Round 7 did not commit. The fixtures (item 1) and the change set are untracked; they must be committed on `ccr-41984ede-stlmp4` by the owner or the next step.
16. **Near-copy borderline pairs.** These pass the rule (no shared head word or first word), but both words are near-synonyms, so a reviewer may still call them close copies: `Log-burning engine` (Wood-fired generator), `Infirmary cot` (Hospital bed), `Pole lantern` (Standing lamp), `Palisade watch` (Colonial guard), `Sour cask` (Fermenting barrel), `Exo casque` (Powered helmet), `Pulse pike` (Charge lance), `Servo limb` (Bionic arm), `Gulped raw fare` (Ate raw food), `Mind hum` (Psychic drone) and `Mind balm` (Psychic soothe). Also the thought-template exception of section 1: the labels that begin "Ate" or "Played" share that verb with their RimWorld label. Owner decision: keep, or change.
17. **Stale rows in the history tables.** Sections 6 to 6.2 keep the rows of earlier rounds. A row that says a constant is "kept" but names a constant that a later round renamed now says so ("renamed in a later round; section 5"). Round 7 found 23 such rows and annotated them.
18. **Scope (decided in round 9; restated in rounds 10 to 12).** The goal named three thought labels. Section 0 states the migration scope and its list: 28 thought labels (H01-H28), 21 backstory-title rows (T01-T21, 16 titles ignoring case), the temporary event names E01-E04, one incident name and three pirate camp texts (S01-S03). Each of them is saved and must load from an old save. The death causes (G01-G09), the log lines and the caravan last-event notes are not in the migration, because they are kept as saved (section 2, and items 23, 24 and 27). The scope is not yet confirmed by the owner. The review's count of 14 backstory titles covers T01-T14 only.
19. **V tags without a URL (closed in round 10).** Forty table rows in `gap-report.md` (16), `temperature-rooms.md` (10), `time.md` (10) and `needs-mood.md` (4) carry a V tag with no inline URL. They predate round 8. Round 8 re-sourced only the rows the review named (map size, hearth stew nutrition, outdoor temperature). Round 10 gave each of these rows a URL in the same cell, or changed it to M, UNVERIFIED (section 10). Round 11 re-checked every table row of the four docs by script: no V cell lacks a URL in its row. The weakest V sources are the legend-page URLs of section 10, which were not searched in round 11 and were not read. A reviewer may ask for them to become M, UNVERIFIED.
20. **Map size source.** The search for the default map size (250 by 250) returned only third-party guides about RimWorld Odyssey, which is DLC. Round 9 searched again and found the same kind of result: one guide and one forum comment, with no Ludeon or wiki page. The vanilla map sizes are therefore M, UNVERIFIED for base game.
21. **Near-copy observation (round 9).** `Unclaimed child` shares the head word "child" with `Abandoned Child`, a RimWorld childhood title that a round 9 search returned. The near-copy rule (section 1) checks the label that is replaced, so the rule is met, but a reviewer may judge the two as close. Decide together with item 10. A different new title costs no save change (T15 translates the old saved text to whatever the table holds).
22. **New titles not searched.** `Battlefield bandager` and `Roadwalker`, the round 8 replacements, were not searched in round 9. Their origin is M, UNVERIFIED, like the other new titles.
23. **Log lines (decision, rounds 10 and 11).** Kept as saved, for the same reason as graveyard entries (section 2). The review did not name log
    lines. This extends the graveyard decision to them, so the owner should confirm the extension. The verdict on round 10 asked for either
    a migration or a withdrawal of the claim "log lines are not saved". Round 11 withdraws the claim and keeps the decision.
24. **Decisions given in the round 11 task.** The graveyard decision (section 2), the log decision (item 23), the scope list (item 18), the
    README decision (item 14) and the title reasoning (item 10) came from the task, not from the owner. They are recorded as decisions,
    pending owner confirmation.
25. **Commit.** Round 11 was asked not to commit, so the untracked files are still untracked (items 1 and 15). The commit step must add them,
    for example with `git add -A`.
26. **Pirate camp texts (decision, round 12).** The site names of pirate camps and the labels of fights against them, or against a pirate
    band, are saved with the old pirate faction name. They are live labels, so they are translated below version 28 (S01-S03, section 2).
    This is the same kind of text as a backstory title, not a record. The owner should confirm it, given that log lines and graveyard
    entries are kept as saved (items 23 and 24).
27. **Caravan last-event notes (decision, round 12).** `Caravan.lastEvent` is a sentence about the last battle, and it can hold the old
    pirate faction name. It is kept as saved, as a record of a past event (section 2). The owner should confirm this. If the owner prefers a
    translation, each sentence template needs a key, not only each label.

## 10. Source of the V tags

Each V tag gives its URL inline in its table row. Round 10 added the URLs of the other four docs (below). The sources are the search snippets of earlier rounds and of round 7:

* **Creatures:** Thrumbo (`https://rimworldwiki.com/wiki/Thrumbo`), Muffalo (`https://rimworldwiki.com/wiki/Muffalo`), Megaspider (`https://rimworldwiki.com/wiki/Megaspider`).
* **Drugs:** the Drugs page (`https://rimworldwiki.com/wiki/Drugs`) lists psychite tea, go-juice, flake and yayo. Flake (`https://rimworldwiki.com/wiki/Flake`) and Yayo (`https://rimworldwiki.com/wiki/Yayo`) have their own pages. No snippet gave a page for go-juice, so the Drugs page is cited for it.
* **Urchin (backstory):** `https://rimworldwiki.com/wiki/List_of_childhood_backstories`. A round 7 search snippet lists "Urbworld Urchin" with the short title "Urchin" on that page. The scraped skill numbers in the snippet are jumbled, so only the existence of the backstory is claimed.
* **Skill names (section 6.3):** `https://rimworldwiki.com/wiki/Skills`. A round 7 search snippet lists the twelve skills: Shooting, Melee, Construction, Mining, Cooking, Plants, Animals, Crafting, Artistic, Medical, Social and Intellectual. The wiki spells the medicine skill "Medical"; the Stillmere label for it is Healing.
* **No V source:** the difficulty preset (no URL found; M, UNVERIFIED).

Round 7 checked that every V row in this file has its URL inline. Round 10 applied the same rule to the other four docs:

* **`gap-report.md` and `time.md`.** Each V row gives the URL of the legend page on its topic (Ticks, Time, Hunger rate, Food, Mental break
  threshold, Passion, Skill, Raid points). `time.md` names the Ticks and Time pages as its source. The pages were not read. Round 10 gave
  these URLs by topic. Round 11 did not search for them, so a search result does not stand behind them in this file (section 9, item 19).
* **`temperature-rooms.md`.** Row 21 keeps the Temperature page (a search result). Row 55 gives the Ticks and Time pages. The other V rows
  became M, UNVERIFIED, because no page URL was recorded for them.
* **`needs-mood.md`.** S1 and S2 name articles with no recorded URL, so their rows are M, UNVERIFIED. The hearth stew row keeps its
  `Simple_meal` URL.
* **Map size, hearth stew and outdoor temperature.** Unchanged from round 9 (section 13).

The rimworldwiki.com host does not resolve from this sandbox. The URLs are the ones the search results returned, or the legend pages that
the docs name.

## 11. Round 7 summary (review of this area)

Nothing is committed. The review's issues, and what round 7 did with each:

* **THRUMBO.** Kept as `SKYHORN_VISIT` (owner decision). Recorded in the explicit-exception list (section 4). `LegacyNamesMigrationTest` checks the old key reads as `SKYHORN_VISIT`.
* **RimWorld names in code.** `LegacyNames.kt` holds 47 CRC-32 keys and no old text. `LegacyNamesMigrationTest.kt` reads the old text from `old_text_cases.tsv` and uses the keys through the functions, so it writes no old name. The literals that still named old labels were changed (section 6.4 lists them, and section 8 gives the scan).
* **The labels the review listed.** Simple meal, Fine meal, Lavish meal, Simple helmet, Tribal headdress, Fueled smithy and Drug production: the labels were renamed earlier (Hearth stew, Roasted platter, Banquet spread, Riveted hood, Plumed crown, Coal forge, Tincture chemistry), and round 7 renamed their constants (section 5.2). Tuque, Plate armor, Horseshoes pin, Advanced firearms and Gun turrets were renamed in rounds 4 to 6; their rows now say so. Round 7 also renamed the other labels it recalled as RimWorld names (section 6.4).
* **Labels recalled as RimWorld names.** Renamed in round 7: Deep talk, Shared a joke, Hearty feast, Engineered upgrades, volcanic winter (smoke pall), Solar flare, Thunderstorm, Food poisoning, Blood loss, Gut flukes, Slow study, Meadow grass, Molotov. Not renamed, UNVERIFIED: Orphan, Field medic, Drifter (section 9, item 10), and the generic words of section 7.
* **Near-copy rule.** Round 7 checked the (old, new) pairs by script (section 8). The failures were renamed (section 6.4); the template exception and the borderline pairs are open items 1 and 16.
* **V tags without URL.** Every V row has its URL inline; Urchin's URL is in the backstory table of section 6 and in section 10. Where a snippet gave no URL, the tag is M, UNVERIFIED (the difficulty preset).
* **M without UNVERIFIED.** Every M row is marked UNVERIFIED (grep check).
* **ADVANCED_BENCH.** Checked against git `HEAD` `Buildings.kt`: `ADVANCED_BENCH` never existed. The only bench renamed was `HI_TECH_BENCH` (label Hi-tech research bench), now `PRECISION_BENCH` (label Precision lab). Section 6 has this, and the row for "ADVANCED_BENCH" is removed.
* **Fixtures.** `colony_v25_thoughts.sav` came from a generator outside this repository and cannot be regenerated from the repo (section 2, open item 4). The new `colony_v29.sav` was generated from the version 29 code before any round 7 change. All fixtures are untracked (open item 1).
* **Gap report.** Section 2.2 and section 11 agree: both say resolved for labels and text, with open items. The round 7 status is the one in section 2.2. Old RimWorld names were removed from the gap report's temperature, food, raid and README lines (names.md section 8).
* **Save version.** 29 to 30 (section 2). Tests: 490 pass, 0 failures.
* **Not done.** Committing (the request said not to). Renaming Orphan, Field medic and Drifter (open item 10). The borderline near-copy pairs (open item 16). The attribution line (open item 14).

## 12. Round 8 summary (review of round 7)

Nothing is committed. The review's issues, and what round 8 did with each:

* **V rows without a URL.** Re-sourced: the vanilla simple meal values (0.9 nutrition, 0.5 nutrition of raw food) and the outdoor temperature factors have inline URLs (`https://rimworldwiki.com/wiki/Simple_meal`, `https://rimworldwiki.com/wiki/Temperature`). The map size vanilla values are M, UNVERIFIED (Odyssey DLC guides only; open item 20). The other 40 rows are open (open item 19).
* **Temperature rows.** `temperature-rooms.md` no longer names the RimWorld temperature events. Its event labels are the current names. The offsets (+16, -17, -13) are unchanged from `HEAD` and the row was corrected; section 8 now says so.
* **Social interactions.** `gap-report.md` no longer lists the old thought-derived social names; it uses the current thought labels. The round 7 note in section 11 names no old label.
* **Graveyard text.** Saves below version 31 are translated when read. `LegacyNames.graveyardEntry` translates the cause segment of `<name> - <cause> (day <n>)` through 9 keys (G01-G09). Sections 3, 6.3, 6.4 and 6.5 give the status: `Saved?` is `yes` for these causes. Rows 724, 725 and 741 said `no` and now say `yes`.
* **Backstory titles that may be RimWorld titles.** Renamed to Unclaimed child, Battlefield bandager and Roadwalker (version 31, keys T15-T19, lower-case adult forms included). Open item 10 is answered.
* **Identifiers.** `HealthFidelityTest` and `GameTest` test names no longer echo the old wording; `Implant.bionic` is `Implant.augmented`. `health.md` cites the new names.
* **README.** The sentence naming Claude and Anthropic is removed (open item 14).
* **Save version.** 30 to 31. A fixture of version 30 was generated from the round-7 code before the change; a fixture of version 25 with the old graveyard causes was generated from `HEAD`. `LegacyNamesMigrationTest` reads both.
* **Scope.** The goal text covers three thought labels; the change set covers more (open item 18). Not changed in round 8: the generic kept labels (open item 5).
* **Tests.** `./gradlew :sim:test --console=plain`: 499 tests, 0 failures, 0 errors (`BUILD SUCCESSFUL`). The count includes the round 8 cases in `LegacyNamesMigrationTest`.

## 13. Round 9 summary (review of round 8)

Nothing is committed. The review items, and what round 9 found and did with each. Several items described a state that the working tree
had already changed; the found state is given.

1. **Map size row (gap report 3.2).** The working tree already had no V tag on the row. Its vanilla values are now marked M, UNVERIFIED in
   the vanilla column too. The round 9 search found only third-party guides about Odyssey (DLC). Section 9, item 20.
2. **Hearth stew nutrition (gap report 3.5 and `needs-mood.md`).** Both rows cite `https://rimworldwiki.com/wiki/Simple_meal`. A round 9 search
   returned that page with a 0.9 meal value and a 0.5 ingredient cost. The rows stay V, with the note that the page itself was not read.
3. **`temperature-rooms.md` row at line 21.** The working tree already used the new labels and had no RimWorld event names. The row cites
   `https://rimworldwiki.com/wiki/Temperature`, which a round 9 search returned for the list of drivers. The note now says the page was not read.
4. **`temperature-rooms.md` line 54.** The working tree already reads "smoke pall". A search of the repository (outside this file and the
   legacy folder) finds no `ash-veil` label. No edit was needed.
5. **Social interactions and section 11 of the gap report.** No `deep talk`, `kind words` or `joke` label is left in the gap report. The
   social-interactions row names Candid exchange, Warm talk and Friendly ribbing. Section 11 has a round 9 bullet and no longer describes
   the graveyard or README as they were in round 8.
6. **Graveyard text. Decision: old entries are kept as saved.** The working tree translated graveyard entries when read (below version 31).
   Round 9 removed that translation: `SaveGame.read` reads each entry as it was saved, `LegacyNames` lost `causes31`, `graveyardEntry` and nine
   keys (61 became 52), and the read site and `LegacyNames` carry comments that record the decision. The version stays 31, because the three
   backstory titles still need it. Tests: `aVersion25GraveyardKeepsEveryDeathCauseAsItWasSaved` (the v25 graveyard fixture reads unchanged),
   `everyOldDeathCauseInTheCaseFileIsInTheVersion25GraveyardAsSaved` and `anOldGraveyardEntryInAVersion30SaveLoadsUnchanged` (a version 30 round
   trip). Sections 0, 2, 3, 4, 6.3, 6.5 and 9 give the same decision.
7. **`README.md` line 12. Decision: keep.** Round 8 had removed the sentence that says the project was built with Claude, by Anthropic. Round 9
   restores the committed sentence and keeps the other round 8 README changes. Recorded in section 9, item 14. Open point: `CLAUDE.md` says "anywhere",
   and the owner should confirm the narrower reading used here.
8. **Backstory titles `Orphan`, `Field medic`, `Drifter` (`Pawn.kt`).** `Pawn.kt` already has none of them: round 8 renamed them to `Unclaimed child`,
   `Battlefield bandager` and `Roadwalker`. Decision in this round: keep the round 8 renames, because the review rule would keep the old names
   while reverting them would undo a tested version 31 change. This conflicts with the rule as written. The search did not confirm the old titles
   as RimWorld titles; its nearest hit was `Abandoned Child`. Recorded in section 6.5 and section 9, items 10 and 21. Owner decision.
9. **Test names (`HealthFidelityTest`).** HEAD has `aPegLegRestoresPartOfWhatAmputationTook` and `bionicPartsBeatNaturalOnes`; the working tree
   already renamed them in round 8. Round 9 renamed `aWoodenShankRestoresPartOfWhatAmputationTook` to
   `aReplacementLegRestoresPartOfWhatAmputationTook`, because the name echoed a Stillmere label. `augmentedPartsBeatNaturalOnes` stays. `health.md`
   and section 5.3 cite the new names.
10. **Untracked files.** `git add -A --dry-run` lists `docs/fidelity/names.md`, `LegacyNames.kt`, `LegacyNamesMigrationTest.kt`, `NamesTest.kt`,
    `ColonyMapSizeTest.kt` and all 24 entries under `sim/src/test/resources/legacy/`. No `.gitignore` rule excludes them. Nothing was staged or
    committed in this round.
11. **Scope.** Section 0 gives the goal and the list. The migration covers 28 thought labels (H01-H28), 19 backstory-title rows (T01-T19, which are
    16 titles when case is ignored), four temporary event names (E01-E04) and one incident name (I01). The review's "14 backstory titles" matches
    rows T01-T14 only; T15-T19 were added in round 8. Death causes are not in the migration (item 6).

**Tests.** `./gradlew :sim:test --console=plain`: 499 tests, 0 failures, 0 errors (`BUILD SUCCESSFUL`). The count is the same as round 8: two
tests were removed, one was renamed and two were added. A full run executed the tests; a later run after a comment-only edit in `SaveGame.kt`
reported `:sim:test UP-TO-DATE` (the bytecode did not change), so the 499 result stands.

**Open items for the owner.** (a) The three backstory titles (item 8 above, section 9, item 10). (b) The `CLAUDE.md` reading of "anywhere" (item
7 above, section 9, item 14). (c) `Unclaimed child` against `Abandoned Child` (section 9, item 21).

## 14. Round 10 summary (review of round 9)

The review verdict (FAIL) and the round 10 task. Each item and what changed:

* **Untracked files (verdict 1).** Not committed, because the round 10 task says not to commit. Five new files (`docs/fidelity/names.md`,
  `LegacyNames.kt`, `LegacyNamesMigrationTest.kt`, `NamesTest.kt`, `ColonyMapSizeTest.kt`) and the 19 files of
  `sim/src/test/resources/legacy/` are still untracked. The commit step with `git add -A` takes them (section 9, items 1, 15 and 25).
* **V tags without a URL (verdict 2; task items 1 to 3).** The 40 rows of round 9. `gap-report.md`: 12 rows give the URL of the legend page
  on their topic, and 4 rows (`156`, `160`, `163`, `164`) are M, UNVERIFIED. `time.md`: all 10 rows give the Ticks or Time URL.
  `temperature-rooms.md`: row 55 gives the URLs, and 9 rows are M, UNVERIFIED. `needs-mood.md`: 4 rows are M, UNVERIFIED, because S1 and S2
  have no recorded URL. Section 10 gives the rule. The map size row (task item 1) is M, UNVERIFIED for its vanilla values. The hearth stew
  row (task item 2) keeps its `Simple_meal` URL. The outdoor temperature row (task item 3) keeps its URL, with a note.
* **Old labels in the docs (task items 3 to 5).** `temperature-rooms.md` uses `deep chill`, `smoke pall` and `scorching spell`, with no
  RimWorld event names. `gap-report.md` names `Candid exchange`, `Warm talk` and `Friendly ribbing` for the social thoughts. A grep finds no
  old label outside this file, the legacy folder and `LegacyNames.kt`.
* **Graveyard (verdict 3; task item 6).** Kept as saved (section 2). The read site in `SaveGame.kt` has a comment. The tests
  `aVersion25GraveyardKeepsEveryDeathCauseAsItWasSaved` and `anOldGraveyardEntryInAVersion30SaveLoadsUnchanged` load old entries unchanged.
  Section 0 now names this exception in the goal.
* **Log lines (verdict 4).** Kept as saved, by the same decision applied to log lines (section 2; item 23). The read site in `SaveGame.kt`
  has a comment. The test `anOldLogLineIsKeptAsSavedAndNotTranslated` checks it. The claim that log lines are not saved is withdrawn
  (section 1).
* **Incident gate (verdict 5a).** One gate, stated as 26 (section 2). `LegacyNames.incidentName` checks the version. `SaveGame` calls it for
  saves below 28. The KDoc in `LegacyNames.kt` says the same.
* **Augmented flag (verdict 5b).** Five implants, not three (section 5.3).
* **ColonyMapSizeTest (verdict 5c).** New and untracked (section 8).
* **`LOST_TRIBE` in code (verdict 6).** Removed from the KDoc of `LegacyNamesMigrationTest.kt`. The old scenario name appears only in
  names.md and the legacy folder.
* **T17, T19 and the test-only rows (verdict 7).** The parentheticals are out of the table (section 3). P01 to P03 and U01 to U02 are in a
  table of section 3.
* **Test names (task item 9).** Already neutral (round 9): `aReplacementLegRestoresPartOfWhatAmputationTook` and
  `augmentedPartsBeatNaturalOnes`. No old name is in the code.
* **Backstory titles (task item 8).** `Pawn.kt` has the round 8 titles. The reasoning is in section 9, item 10. The owner decides.
* **README (task item 7).** Line 12 is kept (section 9, item 14). The decision is recorded and not yet confirmed by the owner.
* **Scope (task item 11).** The list in section 0 is kept, with its counts: 28 thought labels, 19 backstory-title rows (16 titles ignoring
  case, of which T01-T14 are the 14 the review counted), four temporary event names and one incident name.
* **Tests.** `./gradlew :sim:test --console=plain`: 500 tests, 0 failures, 0 errors, 0 skipped (`BUILD SUCCESSFUL`). The count is 499 plus the
  new `LegacyNamesMigrationTest.anOldLogLineIsKeptAsSavedAndNotTranslated`.

## 15. Round 11 summary (verdict on round 10, and the names-fix task)

The verdict is FAIL, with issues 1 to 7. Several of its line numbers and claims do not match the file as it stands: round 10 had already
changed the rows, the parentheticals and the KDoc that the verdict names. Each item below gives what the working tree holds now, and what
round 11 changed.

* **Verdict 1 (fixtures and new files untracked).** Still untracked, because the task says not to commit. No `.gitignore` rule excludes them,
  so the commit step's `git add -A` takes them (section 9, items 1 and 25).
* **Verdict 2 (V tags without a URL).** The rows the verdict names (`gap-report.md` lines 101-105, 156 to 164, 232 to 237, 259, 260, 369, 386;
  `temperature-rooms.md` lines 20 to 24; `time.md` and `needs-mood.md`) do not match the file. A script read every table row of the four
  fidelity docs and this file. Each V cell has its URL in its row. The two V rows of `temperature-rooms.md` are row 22, which cites
  `https://rimworldwiki.com/wiki/Temperature`, and row 56, which cites the Ticks and Time pages. Task item 1: the map size row is M, UNVERIFIED for its vanilla values. Task item 2: the hearth
  stew rows (`gap-report.md` line 234 and `needs-mood.md` line 39) cite `https://rimworldwiki.com/wiki/Simple_meal`.
* **Verdict 3 and task item 6 (graveyard).** The goal (section 0) states the exception. Section 2 states the decision, with the current
  death causes that have no key, and the test list. The read site in `SaveGame.kt` has a comment, and so does the `SaveGame` KDoc. The test
  `anOldGraveyardStringStillLoadsUnchangedWhenItIsSavedNow` is new. `aVersion25GraveyardKeepsEveryDeathCauseAsItWasSaved`,
  `anOldGraveyardEntryInAVersion30SaveLoadsUnchanged` and `aVersion30GraveyardIsReadWithItsCurrentCausesUnchanged` already check old and
  current causes.
* **Verdict 4 (log lines).** Decision: log lines are kept as saved, the same as graveyard entries (section 2, item 23). The claim that log
  lines are not saved is withdrawn. The read site has a comment, the `SaveGame` KDoc names them, and `anOldLogLineIsKeptAsSavedAndNotTranslated`
  checks the behaviour. The owner must confirm the extension.
* **Verdict 5a (incident gate).** One gate, below 26, in `LegacyNames.incidentName`. `SaveGame` calls it for saves below 28. Section 2, section
  8 and the KDocs of `LegacyNames.kt` and `SaveGame.kt` say the same.
* **Verdict 5b (augmented flag).** Five implants: servo limb, servo strider, optic lens, coil listener and pump core (section 5.3, `Surgery.kt`).
* **Verdict 5c (`ColonyMapSizeTest`).** Section 8 says it is new and untracked. No "unchanged" claim is left.
* **Verdict 6 (old name in a test KDoc).** `LOST_TRIBE` is not in `LegacyNamesMigrationTest.kt`, in any other source, or in any other doc. It
  appears only in this file (sections 2, 5 and 6, and the fixture description in section 2).
* **Verdict 7 (T17 and T19, test-only rows).** The old-text column of section 3 has no parenthetical. P01 to P03 and U01 to U02 are in the
  test-only table of section 3.
* **Task item 3 (`temperature-rooms.md`, the row the verdict calls line 21).** The row is now line 22. It names the new temperature events
  (`deep chill`, `smoke pall`, `scorching spell`) and gives the URL. No "cold snap", "volcanic winter" or "heat wave" is left in the file.
* **Task item 4 (`temperature-rooms.md`, line 54 in the task).** The offsets row (line 55) uses `smoke pall`. No `ash-veil` label is left.
* **Task item 5 (social kinds in `gap-report.md`).** The social row names Candid exchange, Warm talk and Friendly ribbing. The round 11 note
  and the test-coverage row use the same labels. The test `aSociableColonistSometimesSharesAJoke` was renamed to
  `aSociableColonistSometimesGivesFriendlyRibbing`, so no test name echoes the retired label.
* **Task item 7 (README).** Line 12 is kept, and section 8 records the decision.
* **Task item 8 (`Orphan`, `Field medic`, `Drifter`).** `Pawn.kt` has none of them. The renames stay, and section 6.5 gives the reasoning. The
  owner decides (section 9, item 10).
* **Task item 9 (test names).** `HealthFidelityTest` already has `aReplacementLegRestoresPartOfWhatAmputationTook` and
  `augmentedPartsBeatNaturalOnes`. No old name is left, so nothing was renamed there.
* **Task item 10 (untracked files).** Covered by verdict 1.
* **Task item 11 (scope).** Section 0 states the goal, the exception and the scope list, and gives the counts.
* **Tests.** `./gradlew :sim:cleanTest :sim:test --console=plain`: BUILD SUCCESSFUL, 62 result files, 501 tests, 0 failures, 0 errors,
  0 skipped. The count is 500 (round 10) plus `anOldGraveyardStringStillLoadsUnchangedWhenItIsSavedNow`. The renamed social test keeps its count.
* **Not done.** Committing (the task says not to). Owner confirmation of decisions items 10, 14, 18, 23 and 24.

## 16. Round 12 summary (review of round 11, findings A and B)

* **Finding A (lower-case adult titles of T13 and T14).** Fixed. `LegacyNames.kt` keys the two lower-case forms that `Game.kt:190` writes for
  an adult title: `beast tamer` as T20 (`4BFBA253`, to `Stockman`) and `pirate deckhand` as T21 (`8A144754`, to `Sloop hand`). Both are in
  `titles27`, so they apply below version 27. They are rows T20 and T21 in section 3 and in `old_text_cases.tsv`. Before this round an old
  save that held either form kept the old adult title on load. Tests: `aVersion24SaveHoldingALowerCaseAdultTitleReadsTheNewAdultTitle` loads
  `colony_v24.sav`, which holds the lower-case form of T13 on a visitor, and expects the new title. `aVersion25SaveHoldingTheLowerCaseAdultTitlesReadsTheNewTitles`
  loads `colony_v25_adults.sav` (version 25), which holds both forms. `everyLowerCaseAdultTitleIsTranslatedFromItsVersionAndNotFromLater`
  checks all five lower-case rows (T12, T17, T19, T20, T21): each is translated at the version before its rename and kept from that version on.
* **Finding B (saved site names).** Fixed, and the fix covers more than the site name. The saved camp name is `<faction> camp`
  (`Factions.kt`, `Quests.kt`), so the key is for that text, not for the bare faction name. The bare name is rebuilt from the seed on load and is
  never saved. Two more saved fields hold the same old pirate name: the label of a pending fight (`BattlePlan.label`, `the <site name>` for a
  camp attack and `<faction> bandits` for an ambush), and the caravan last-event note (`Caravan.lastEvent`). Keys: S01 for the site name, S02 and
  S03 for the two fight labels. All three are read below version 28, the version of the round 4 rename. The fight labels are translated, as
  live labels (decision, section 2, open item 26). `Caravan.lastEvent` is kept as saved, as a record of a past battle (decision, section 2,
  open item 27). In `SaveGame.kt` the read sites are the site list, `readPlan` (both pending plans) and the caravan list. Tests:
  `aVersion27SaveWithAPirateCampReadsTheCampAndTheFightUnderTheNewNames` loads `colony_v27_sites.sav`, which holds a camp, a pending fight and a
  caravan event. `aVersion27SaveWrittenAgainKeepsTheCampTextsAndTheLastEvent` round-trips it. `everyPirateCampTextRenamedInVersion28IsTranslatedFromAVersion27SaveAndNotFromAVersion28`
  checks the three rows at version 27 and at version 28.
* **Finding C (untracked files).** Unchanged. The fixtures and the new files stay untracked, and nothing is committed. The four new fixture
  files (`colony_v25_adults.sav`, `colony_v25_adults_expect.txt`, `colony_v27_sites.sav`, `colony_v27_sites_expect.txt`) are listed in section 9,
  item 1. The commit step adds them with `git add -A`.
* **Temporary generators.** Two temporary test classes produced the two new fixtures (`Game(4242L)`, `startNewColony(QUIET_ENCLAVE)`, then
  `SaveGame.write(g, 25)` and `SaveGame.write(g, 27)`). Each was deleted after it ran. A short probe that printed the pawns of `colony_v24.sav`
  was also deleted. The layout did not change, so only the header differs from a save of that version.
* **Checks.** A script matched the 57 keys in `LegacyNames.kt` with the rows of `old_text_cases.tsv`: 57 of 57 matched, with the CRC-32 of
  each old text and its new text. The only rows without a key are G01-G09 (kept as saved). No old pirate name is left in code. The old text
  lives only in the legacy folder and in this file (`LegacyNames.kt` holds CRC-32 values, not the text).
* **Tests.** `./gradlew :sim:test --console=plain`: BUILD SUCCESSFUL. 62 result files, 507 tests, 0 failures, 0 errors, 0 skipped. The round
  11 count was 501. The six new tests are in `LegacyNamesMigrationTest`. A first run after the change had one failure, in
  `aVersion24SaveHoldingALowerCaseAdultTitleReadsTheNewAdultTitle`. The pawn is a visitor, not a colonist, so the test was changed to read every
  pawn. The translation itself was correct.
* **Not done.** Committing (the task says not to). Owner confirmation of decisions items 10, 14, 18, 23, 24, 26 and 27.

## 17. Round 13 summary (verdict on round 12)

The verdict on round 12 gave three items. Each claim was checked against the working tree, not taken as given. The verdict describes a tree with
52 keys, no lower-case keys for T13 and T14, no site-name key and no test for either. The tree holds 57 keys, so those facts were out of date.

* **Item 1 (lower-case adult titles of T13 and T14).** Already fixed in round 12, and checked again. `titles27` keys `beast tamer` as T20 (`4BFBA253`,
  to `Stockman`) and `pirate deckhand` as T21 (`8A144754`, to `Sloop hand`). Both CRC-32 values were recomputed with `zlib.crc32` and match.
  Rows T20 and T21 are in section 3 and in `old_text_cases.tsv`. The fixtures really hold the lower-case bytes: `colony_v24.sav` holds
  `Urchin, beast tamer` and `Pit fighter, colonial guard`, and `colony_v25_adults.sav` holds `beast tamer` and `pirate deckhand`. Tests:
  `aVersion24SaveHoldingALowerCaseAdultTitleReadsTheNewAdultTitle`, `aVersion25SaveHoldingTheLowerCaseAdultTitlesReadsTheNewTitles` and
  `everyLowerCaseAdultTitleIsTranslatedFromItsVersionAndNotFromLater`. Coverage: at HEAD the renamed adult titles are Colonial guard (T11),
  Beast tamer (T13), Pirate deckhand (T14), Field medic (T16) and Drifter (T18). Their lower-case forms are T12, T20, T21, T17 and T19, and
  all five are keyed. No other renamed title was an adult title at HEAD (T01-T10 and T15 were childhood titles).
* **Item 2 (pirate site name).** Already fixed in round 12, and the verdict's premise is wrong for this tree. A site is never named by the bare
  faction name. Both site constructors write `"<faction> camp"` (`Factions.kt`, `Quests.kt`), so the saved text is `Rough-Knife gang camp`, which
  is key S01. The fixture `colony_v27_sites_expect.txt` holds exactly that text. The bare name is never saved: factions are not written, and
  `World.generate` rebuilds them from the seed on load (section 2). The pending-fight labels are S02 (`the <site name>`) and S03
  (`<faction> bandits`), and these are the only battle labels that carry a pirate name (`Caravans.kt`). The caravan note from `Battles.kt`
  (`Retreated from ...`, `Defeated ...`) carries the same names and is kept as saved (open item 27). Round 13 corrects section 2 and section 6.1
  so that they say this.
* **Item 3 (untracked files).** Unchanged. The fixtures and the new files are untracked, and the commit step adds them with `git add -A`.
  Nothing was committed.

**Changes in round 13.**

* `LegacyNamesMigrationTest.aVersion24SaveHoldingALowerCaseAdultTitleReadsTheNewAdultTitle` checks every pawn the save holds, through the new helper
  `everyPawnIn`: the pawns on the map, the battle members, the captives, the caravan members and the pending battle's snapshot. The first version
  of the check looked only at `g.pawns`, and that failed: the pawn that holds `Pit fighter, colonial guard` is not on the map list. The fix
  changed the lookup, not the translation. The test now also asserts `Ringside tyke, Palisade watch`, which is the lower-case T12 form in a real
  save, and that no lower-case form of T13 or T12 remains.
* Section 0 (status), section 2 (the bare faction name is never saved, and the camp texts are S01-S03) and section 6.1 (a pointer to the keyed
  camp texts) are corrected to match this round.

**Checks.**

* A script (round 13) matched the 57 keys in `LegacyNames.kt` with the rows of `old_text_cases.tsv`: 57 of 57 keys match their CRC-32 and their new
  text, and no T, H, I, E or S row is without a key. The only rows without a key are G01-G09 (kept as saved) and the test-only rows P01-P03 and
  U01-U02.
* `./gradlew :sim:cleanTest :sim:test --console=plain`: BUILD SUCCESSFUL. 62 result files, 507 tests, 0 failures, 0 errors, 0 skipped.

**Not done.** Committing (the task says not to). Owner confirmation of decisions items 10, 14, 18, 23, 24, 26 and 27 remains open. Item 27 is
the one the verdict's rule could still reach: a caravan note with an old pirate name is kept as saved.

## 18. Round 14 summary (verdict in `names-verdict.md`)

The verdict in `names-verdict.md` rejects the tree on two saved-name items and an untracked-file rule. Its premise is an older tree. The verdict
file is dated before the round 12 edits to `LegacyNames.kt`, its line numbers do not match the file, and it counts 52 keys where the tree holds
57. Each claim was checked against the tree, not taken as given.

* **Item 1 (lower-case adult titles of T13 and T14).** Already fixed in round 12. `titles27` keys `beast tamer` (`4BFBA253`) and
  `pirate deckhand` (`8A144754`) as T20 and T21. Both CRC-32 values were recomputed with `zlib.crc32` and match. The verdict's statement that the
  keys present are only for `Beast tamer` and `Pirate deckhand`, and its output `Street kid, beast tamer`, describe the tree before round 12.
  Tests: `aVersion24SaveHoldingALowerCaseAdultTitleReadsTheNewAdultTitle` (`colony_v24.sav`, which holds `Urchin, beast tamer`) and
  `aVersion25SaveHoldingTheLowerCaseAdultTitlesReadsTheNewTitles` (`colony_v25_adults.sav`).
* **Item 2 (pirate site name).** Already fixed in round 12, as key S01 in `siteNames28`, read below version 28 at `SaveGame.kt` (the site list).
  The verdict's premise is wrong: a site is named `<faction> camp` (`Factions.kt`, `Quests.kt`), and factions are not saved (`SaveGame.kt` writes
  only faction ids). So the saved text is `Rough-Knife gang camp`, which is S01. The bare name is never a site name. Test:
  `aVersion27SaveWithAPirateCampReadsTheCampAndTheFightUnderTheNewNames` loads `colony_v27_sites.sav`.
* **Item 3 (untracked files).** Unchanged. The fixtures and the new files are untracked, the commit step adds them with `git add -A`, and nothing is
  committed (the task says not to commit).

**Changes in round 14.**

* **Case of the new text for the lower-case rows.** The five lower-case rows T12, T17, T19, T20 and T21 now map to lower-case new titles:
  `palisade watch`, `battlefield bandager`, `roadwalker`, `stockman` and `sloop hand` (`LegacyNames.kt`, `titles27` and `titles31`;
  `old_text_cases.tsv`; section 3). The task asked for the lower-case key of T13 and T14 to map to the new lower-case text. Game.kt writes an adult
  title in lower case, so the translated title now equals the text a new colony writes. Before this round a migrated adult title was capitalised
  (`Street kid, Stockman`), which no new colony writes. The rule is recorded in section 3 and in the `LegacyNames` header: the case of the new text
  follows the case of the old text. The capitalised rows are unchanged. The table row P02 (a test-only backstory list, `Farm kid, colonial guard`)
  follows the same rule and now expects `Furrow-born, palisade watch`.
* **Tests.** Expectations that come from a lower-case key now expect the lower-case title: `anOldSaveReadsTheNewBackstoryTitlesAndKeepsTheUnchangedOnes`,
  `aVersion25SaveWrittenByTheHeadCodeReadsEveryRenameSinceVersion25`, `aVersion30FixtureReadsItsTitlesThroughTheVersion31TranslationAndKeepsItsLabels`,
  `aVersion24SaveHoldingALowerCaseAdultTitleReadsTheNewAdultTitle` and `aVersion25SaveHoldingTheLowerCaseAdultTitlesReadsTheNewTitles`. The
  `colony_v26.sav` expectations still read `Palisade watch` and `Furrow-born, Palisade watch`, because that fixture holds the capitalised
  `Colonial guard` (T11). The helper `lowerCaseOldTextOf` matches the new text without case.
* **New test.** `aMigratedLowerCaseAdultTitleIsTheTextTheCurrentCodeWritesForAnAdult` checks each lower-case row: the translated adult title is
  the new text, and it is the title of an entry in `Backstories.adulthood`, lower-cased, which is the form `Game.kt` writes.
* **Sections 16 and 17.** Both describe T20 and T21 with their capitalised new text as it stood when they were added. This round changed those
  values to lower case, so the text in those sections is superseded.
* **Open item 3 (section 9)** is restated: the migration now follows the writer. If the owner capitalises new colonists' titles, the five
  lower-case rows must change with them.

**Checks.**

* `./gradlew :sim:cleanTest :sim:test --console=plain`: BUILD SUCCESSFUL. 62 result files, 508 tests, 0 failures, 0 errors, 0 skipped. The
  round 13 count was 507, and this round adds one test. The first run after the case change had one failure, in
  `everySavedBackstoryInTheCaseFileIsTranslatedPartByPart`, because row P02 still held the capitalised text. P02 was changed and the run passed.
* A script (round 14) read the 57 keys in `LegacyNames.kt` and compared each one with its row in `old_text_cases.tsv` (CRC-32 of the old text, and
  the new text) and with its row in section 3 of this file (CRC-32 and new text). 57 keys, 0 mismatches.
* `git` state: nothing is committed. The fixtures and the new files are untracked, as in section 9 item 1.

**Not done.** Committing (the task says not to). Owner confirmation of decisions items 3, 10, 14, 18, 23, 24, 26 and 27 remains open.
