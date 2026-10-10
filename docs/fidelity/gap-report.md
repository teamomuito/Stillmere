# Fidelity gap report

Compares Stillmere to vanilla RimWorld (base game only). This file was created for the health pass; it holds one
section so far. Other systems have not been audited here.

Status key: **Matches** (agrees with a `SECONDARY` source), **Partial**, **Gap** (known difference, not fixed),
**UNVERIFIED** (no vanilla figure could be confirmed, so the sim's own tuning stands).

**Nothing in this report is confirmed against RimWorld's own data.** The wiki was unreachable from the build
environment (blocked by network policy), so references are web-search summaries of the wiki (`SECONDARY`) or memory
(`UNVERIFIED`). Details and every value: [health.md](health.md).

## Health and medicine

Phase 5. Code in `sim/`, tests in `HealthFidelityTest.kt` (65 new tests; `./gradlew :sim:test` passes, 402 total).

| Area | Status | Notes |
|---|---|---|
| Body parts and hierarchy | Partial | 29 human parts with correct parents and vital set. Vanilla's bones, tongue and extra digits are not modelled. Finger and toe HP is 12 where I recall 8 (`UNVERIFIED`). |
| Part health | Matches | Per-part HP times health scale, with no body total. Torso, heart, lungs, kidneys, liver and brain agree with `SECONDARY`; the other parts are `UNVERIFIED`. |
| Hit locations | UNVERIFIED | Outer parts by coverage, organs only through their parent (`SECONDARY` agrees). Coverage values and the penetration rule are the author's tuning. |
| Consciousness | Partial | Pumping weight 0.2 agrees; breathing and filtration weights and the pain and blood-loss terms are `UNVERIFIED`. |
| Moving, manipulation | Partial | Structure is the author's. Consciousness does not multiply either directly (`SECONDARY`). |
| Sight, hearing | Matches | Mean of the eyes or ears; one eye lost halves sight. |
| Eating | Matches (changed) | Now jaw only; the stomach moved to Metabolism. |
| Breathing, pumping, filtration | Matches (changed) | Invented fudge factors removed: one lung or kidney lost now halves the capacity. Liver loss and loss of both kidneys now kill ("organ failure"). |
| Metabolism | Partial (new) | Computed from the stomach. Not yet consumed by anything: the rest-rate link is not wired. |
| Pain and pain shock | Matches (changed) | Shock threshold 0.80; Wimp lowers it by 0.50 instead of multiplying pain. The pain-to-consciousness curve is `UNVERIFIED`. |
| Downed state | Matches (changed) | Down when consciousness < 30%, moving ≤ 15%, or pain at the threshold; standing up is the exact negation. |
| Bleeding and clotting | UNVERIFIED | Death at 100% blood loss agrees. Rates, tended residual, clotting and the staged blood-loss hediff are unconfirmed or absent. |
| Wound infection | UNVERIFIED | Qualitatively consistent with wiki anecdotes. No figures confirmed. |
| Illness speed | Gap | Flu runs about 3.5 times faster than the wiki's figures (`SECONDARY`). Left alone; illness tuning was out of scope. |
| Scars, lost limbs | UNVERIFIED | Scars are free and permanent (I recall vanilla scars keeping some severity). |
| Prosthetics | UNVERIFIED | Efficiencies unconfirmed. "Hook hand" may not be a base-game item. |
| Tend quality | Matches (changed) | Doctor-stat curve, ×0.7 self-tend, ±25% luck, clamp to the medicine's cap. Herbal potency, the herbal cap, the no-medicine values and the +0.10 bed offset are `UNVERIFIED`. About double the old tend quality for skilled doctors. |
| Medicine items | Partial | Industrial agrees. Herbal is `UNVERIFIED`. Glitterworld medicine is missing. |
| Healing rates, beds | UNVERIFIED | Kept as the author's tuning. Hospital-bed healing, infection and tend bonuses are all unconfirmed. |
| Surgery | Partial | Amputation and prosthetic or bionic installation. Success ignores medicine potency and no medicine is consumed (both `SECONDARY`/`UNVERIFIED` gaps). No operations on injuries. |
| Downed enemies dying from pain shock | Gap | `SECONDARY`; not implemented. |
| Death at consciousness 0% | Gap | `SECONDARY`; not a separate rule. |
| DLC exclusion | Matches | No DLC-sourced condition or operation found in the sim (audited from memory). |

### Follow-ups

1. Confirm the `UNVERIFIED` values against the game's data or a readable wiki. Start with finger and toe HP, coverage,
   healing rates, the scar rule and the infection numbers.
2. Wire filtration into immunity gain, and Metabolism into rest rate.
3. Apply consciousness directly to Moving and Manipulation.
4. Make surgery success depend on medicine potency, and consume medicine.
5. Re-tune illness progression against the wiki's per-day figures.
6. Add glitterworld medicine and a staged blood-loss hediff.
