# CLAUDE.md

## Project goal

Faithful recreation of the Core (base game, no DLC) RimWorld <VERSION> experience as an Android app written in Kotlin.
Mechanics, formulas, numbers and timings should match vanilla as closely as we can verify.
The `sim/` module stays pure JVM with no Android dependencies.

## Reference rules

- Match mechanics, formulas and numbers. Record every constant you implement in `docs/fidelity/<system>.md` with its source.
  Mark anything you're not certain of as `UNVERIFIED`.
- Never copy RimWorld text strings, art, XML defs or decompiled code. Write original wording and original assets.
- Exclude DLC content (Royalty, Ideology, Biotech, Anomaly, Odyssey). If an existing feature is DLC-sourced, flag it in the
  gap report. Do not delete it without asking.

## Workflow for every task

1. Read the relevant existing code first.
2. Write or update the plan in `docs/fidelity/<system>.md`.
3. Implement logic in `sim/` first, with unit tests.
4. Run `./gradlew :sim:test` and make sure it passes.
5. Update `docs/fidelity/gap-report.md`.
6. Commit and push to a branch named `phase-<n>-<short-name>`.

## Autonomy

Don't ask for approval for coding, testing, committing, or pushing to feature branches.
Ask before:

- deleting files or branches,
- force-pushing,
- rewriting git history,
- anything that would break existing save files.

## Determinism

All randomness goes through a seeded RNG owned by the sim. Never read the wall clock inside `sim/`.
Same seed plus same inputs must produce the same state.

## Commit identity (non-negotiable)

Every commit's author and committer is the repo's configured git identity.
Never write Claude, Anthropic, or AI attribution anywhere: no `Co-Authored-By` lines and no "Generated with" footers,
in commits or PR descriptions.

Repo identity:

```sh
git config user.name "teamomuito"
git config user.email "332685429+teamomuito@users.noreply.github.com"
```

Verify with:

```sh
git log -1 --format='%an <%ae> | %cn <%ce>'
```
