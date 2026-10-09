# Time model

Plan and reference for the calendar and the time-dependent rules. Gap-report section 3.1 holds the audit.

## Vanilla reference

Confidence **V**: a secondary summary of the RimWorld Wiki (Ticks, Time pages), via web search on 2026-10-09. Direct access to the
wiki was not available. Re-check against the pages before a release.

| Quantity | Value | Conf |
|---|---|---|
| Ticks per in-game hour | 2,500 | V |
| Ticks per day | 60,000 | V |
| Days per quadrum (season) | 15 | V |
| Quadrums per year | 4 (60 days) | V |
| Ticks per real second at normal speed | 60 | V |
| Day/night | sunlight from dawn to dusk, dark through the night | M (shape only; the timing is set by the constants in `Game.daylight`) |

## What the code now does

| Constant (`Defs.kt`) | Value | Source |
|---|---|---|
| `TICKS_PER_HOUR` | 2,500 | V |
| `TICKS_PER_DAY` | 60,000 | V |
| `DAYS_PER_SEASON` | 15 | V |
| `SEASONS_PER_YEAR` | 4 | V |
| `DAYS_PER_YEAR` | 60 | V (derived) |
| `HOURS_PER_DAY` | 24 | M |
| `LEGACY_TICKS_PER_HOUR` | 1,000 | the previous base, kept for conversion |
| `TIME_SCALE` | 2.5 | derived: 2,500 / 1,000 |
| `tk(n)` | n × 2.5 | converts a count written in old ticks |

Rules for new code:

* A duration, cooldown, timer or interval written in old ticks goes through `tk()`. Write new numbers in hours or days where
  possible (`2 * TICKS_PER_HOUR`), not in ticks.
* A rate applied once per tick (work, XP per tick, joy or rest gain per tick, repair per tick) is divided by `TIME_SCALE`. A rate
  applied per day is already correct, because it divides by `TICKS_PER_DAY`.
* Cadences use `Game.SLOW_TICK` (every 625 ticks, which is 250 old ticks) and `Game.HEALTH_STRIDE` (25 ticks, which is 10 old ticks).

The app runs at 60 ticks per second at 1x, matching vanilla. Its log timestamps and autosave stamp were converted too.

## Determinism

* `sim/` reads no wall clock and no unseeded random source. `TimeModelTest.simulationReadsNoWallClockAndNoUnseededRandomness` scans
  the sources for `currentTimeMillis`, `nanoTime`, `randomUUID`, unseeded `Random()`, `Math.random`, `kotlin.random`, `SecureRandom`, and
  the `now()` methods of `java.time`.
* Save bytes contain no JVM identity hashes: enum-keyed maps that are saved are `EnumMap`s.
* The save timestamp is supplied by the caller (`SaveSession` passes its monotonic clock). The default is 0.
* Named save IDs count up (`save-1`, `save-2`, ...) instead of using random IDs.
* Tests: `sameSeedGivesTheSameStateAfterTheSameTicks` (a day and a half, SHA-256 of the full save bytes),
  `differentSeedsGiveDifferentStates`, `saveAndLoadLeavesTheStateIdentical`, and
  `savedThenLoadedGameRunsExactlyLikeTheOriginal`.

## Parity against the previous time base

A harness ran identical scenarios on the old base (HEAD) and the new one, over 8 seeds, one game day at a time:
walking speed, needs drain, bleeding and healing, a fire spreading, crop growth, a drafted rifleman against a raider, mining and
cutting. Results: walking 67 vs 69 cells per hour; mined cells 7.5 vs 7.5 per day; cut trees the same; crop growth 0.40 vs 0.39;
wounds, needs and fire match within sampling noise. The stone-chunk and mood differences are within the variation of random draws:
the draws happen in a different order now, so outcomes diverge even for the same seed.

## Existing saves: migration (option 1, done)

The save format is now version 25. Versions 14 to 24 are still readable. Each tick-valued quantity is converted on read when the
save is older than version 25:

* **Absolute times** (game tick, channel timers, raid and weather end times, incident history, trader leave times, quest and
  request deadlines, battle start, ransom expiry and cooldown, unreachable marks, log timestamps, pawn break, escape, social,
  pregnancy and suppression times) are multiplied by 2.5.
* **Countdowns and durations in ticks** (job timers, pawn attack and warmup cooldowns, move countdowns, animal production
  timers, injury and hediff ages and durations, plant age, caravan progress) are multiplied by 2.5.
* **Sentinels are kept**: 0 and negative values (the "none" markers such as -1 and the -1,000,000 stock marker), and any value at or
  past Long.MAX_VALUE / 4 (the "never" marker used in battle maps).
* **Not converted**, because they are not in ticks: day counts, severities, fractions, work units, rates, play time in
  milliseconds, the RNG state, ids and keys.
* The header's copy of the tick (`SaveInfo.tick`) is informational only, and nothing in the game or app reads it. It is left as it
  was stored. The day in the header is a day count, so it does not change.

The audit that found these fields is recorded in the commit history (`save-tick-audit`). Four raw literals it found are fixed:
the insult cooldown (120), the wake-up duration (3000), the bench in-use timer (700), and the blast fade in the app (14).

Test: `LegacySaveMigrationTest` loads a real version-24 save written by the pre-change game, and checks each converted value
against what the old game held. It also checks that the calendar (day, hour, season) comes out the same in both bases.
