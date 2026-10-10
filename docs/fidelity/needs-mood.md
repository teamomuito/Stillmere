# Needs and mood

Phase 3 from [gap-report.md](gap-report.md), **partly done**. Done here: the mental-break thresholds, the human hunger
rate, and the starting food in each scenario, which was scaled to match the new rate. Not done, because no source
was available: rest and joy drains, the comfort, beauty, outdoors and indoors needs, the thought catalogue, drug
tolerance, and per-drug numbers.

Code: `NeedRules.kt` (`NeedRules`, `BreakRules`), `Mood.kt` (`breaksTick`), `Game.kt` (`pawnTick`, starting supplies),
`Caravans.kt` (caravan food). Tests: `NeedsMoodFidelityTest.kt`, and `LifeTest` in `GameTest.kt`.

## Sources

Neither the wiki nor any RimWorld data was readable from this environment (`rimworldwiki.com` does not resolve here). The
values below come from search summaries of wiki pages and their mirrors, read on 2026-10-10. Those are **secondary**
sources, so they are tagged **V**. The game version is not pinned (`CLAUDE.md` still says `<VERSION>`), so these figures
are for the current release.

* **[S1]** Mental break thresholds, from the RimWorld Wiki "Mental break" article and its mirrors. At the base break
  stat of 35%, the minor threshold is 35%, the major 20%, and the extreme 5%.
* **[S2]** Hunger, from the RimWorld Wiki "Saturation" and "Overeating" pages and their mirrors. An adult at a 100%
  hunger rate uses 1.6 nutrition per day, and the food bar's maximum is 1.0.

## Constants

| Constant | Before | Now | Source | Confidence |
|---|---|---|---|---|
| Minor-break mood threshold | 0.30 | **0.35** | S1 | V |
| Major-break mood threshold | 0.20 | 0.20 | S1 | V (unchanged) |
| Extreme-break mood threshold | 0.10 | **0.05** | S1 | V |
| Per-check break chance, minor / major / extreme | 0.45% / 1.2% / 3% | unchanged | Stillmere's own | UNVERIFIED |
| Human food use, per day | 0.7 | **1.6** | S2 | V |
| Gourmand / ascetic / pregnant / under 13 multipliers | 1.3 / 0.9 / 1.25 / 0.6 | unchanged | Stillmere's own | UNVERIFIED |
| Sleeping food multiplier | 0.7 | unchanged | Stillmere's own | UNVERIFIED |
| Caravan human food use | flat 0.7 | same as the colony (traits, pregnancy, age) | consistency | C |
| Animal food use | unchanged | unchanged | Stillmere's own | UNVERIFIED |
| Simple meal nutrition | 0.9 | 0.9 | gap report (V) | V (not re-checked) |

Break chances are attached to severity, not to mood bands. Severity follows the thresholds: below 0.05 is extreme,
below 0.20 is major, below 0.35 is minor. Trait modifiers (psychopath, iron-willed, volatile) are unchanged.

## Starting food

Each scenario's food stock was scaled so that the opening lasts as long as it did before the rate change. The
colonists also start with 0.8 food each (4 nutrition for five colonists), and that part does not scale. So the factor
is the one that keeps `(starting bars + stock) / (colonists × rate)` the same before and after:

| Scenario | Colonists | Stock before (nutrition) | Stock now (nutrition) | Factor |
|---|---|---|---|---|
| Crashlanded | 3 | 30 packaged meals (27) | 72 packaged meals (64.8) | 2.40 |
| The lost tribe | 5 | 60 pemmican (3.0), 40 rice (2.0) | 199 pemmican (10.0), 132 rice (6.6) | 3.31 |
| Rich explorer | 1 | 20 packaged meals (18) | 47 packaged meals (42.3) | 2.34 |
| Solo | 1 | 6 packaged meals (5.4) | 15 packaged meals (13.5) | 2.48 |

**Checked in the simulation.** On the lost-tribe start (seeds 113 and 61, 5 colonists), food ran out on day 3 both
before and after scaling. With seed 61, the first death came on day 6 both before and after. With seed 113 nobody died
by day 6 either way. Before scaling, the new rate alone ran food out on day 2 and killed a colonist on day 5 (seed 113).
Those numbers come from a temporary diagnostic test, since removed.

## Consequences

* **Breaks come sooner.** A colony that sits between 20% and 35% mood now has minor-break checks. The mood model is unchanged: one smoothed scalar, baseline 0.55.
* **Babies need feeding about twice a day.** At 0.6 of the adult rate a baby uses about 0.96 food a day, and one milk
  trip gives about 0.41. The feeding job was not changed. In the `LifeTest` baby test, adults ate the milk stock before the
  baby needed it, so the test now restocks milk when the baby gets hungry.
* **The economy was not playtested.** The tests and the opening check above say nothing about farms, hunting, or
  food over a season. Expect colonies to need about 2.3 times the food production they did before.
* **Caravans** now use the same food rule as the colony, including traits, pregnancy and age. Before, they used a flat rate.

## Next steps (not done)

1. Find sources for rest and joy drains, and for the comfort, beauty, outdoors and indoors needs.
2. Find per-drug tolerance and addiction numbers.
3. Check trait-shifted break thresholds (Steadfast, Nervous) against a source.
4. Playtest the first 30 days with real farming to check the new food balance.
