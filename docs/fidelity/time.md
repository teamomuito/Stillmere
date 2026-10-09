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

## Open: existing saves

Save version 24 stores tick numbers in the old base. Loading such a save into the new base would move its calendar and every timer
(raids, weather, traders, quests, pawn breaks, job timers) by a factor of 2.5. That is a save-breaking change, and `CLAUDE.md` says
to ask before making one. The affected fields span roughly two dozen save sections.

Options, for a decision:

1. **Migrate.** Save version 25 writes the new base. Loading a version 24 save multiplies every tick-valued field by 2.5. Needs a
   field-by-field list and a test per section. Largest effort; keeps old saves.
2. **Break.** Bump the version. Old saves are refused with a clear message. Smallest effort; the user loses old saves.
3. **Keep both bases.** Store the time base in the save and run old saves on the legacy base. Most code; it keeps a second calendar
   alive.

The change is committed to branch `ccr-fd954ed9-2t8rr7` so it can be reviewed and tested. **Do not merge it to the default branch until this is decided.** Until the default branch changes, the release workflow publishes nothing from this work.
