# Colony

A RimWorld-style colony sim for Android. A few crash-landed survivors, a hostile rimworld, and the
goal of building a ship to get off it.

Everything is original code with no copied assets: the art is drawn on a canvas.

## what's in it

- **Map**: generated terrain (soil, rich soil, sand, marsh, water, gravel), mountains with steel veins,
  forests and berry bushes.
- **Colonists**: skills with passions, traits, work priorities (1-4 per job), needs (food, rest),
  mood with thoughts, mental breaks (including berserk), injuries, bleeding and doctoring.
- **Autonomous jobs**: mining, chopping, harvesting, sowing, hauling to stockpiles, building with
  material delivery, deconstructing, cooking, research, fuelling campfires, eating (at tables if there is one),
  sleeping in owned beds, fleeing and self-defence.
- **Building**: wood/stone/steel walls, doors, floors, beds, tables, campfires, stoves, research benches,
  gun turrets. Enclosed rooms are detected and have their own temperature (campfires heat them).
- **Zones**: stockpiles and growing zones (rice, potatoes, corn). Crops need warmth and light and die in frost.
- **World**: day/night, four seasons, cold snaps and heat waves.
- **Storyteller**: raids that scale with your wealth and colony size, wanderers who join, supply pods.
- **Combat**: draft colonists, ranged and melee weapons, line of sight, raiders that breach walls and retreat.
- **Research**: Smithing, Stonecutting, Gun turrets and finally the Escape ship. Build it and launch to win.
- **Saves**: autosave every few in-game hours and when the app is paused.

Not here: DLC content, and a lot of the base game's depth (animals, prisoners, apparel, mortars, power,
fire, plant diseases and so on). It is a playable, complete loop rather than a 1:1 copy.

## how to play

Drag to pan, pinch to zoom. Landscape only.

1. Open **Architect > Orders** and paint over trees (Chop) and rock (Mine). Drag a rectangle to paint an area.
2. Colonists haul things into the **Stockpile** zone (one is set up for you at the start).
3. Build beds, a table, and a stove (**Architect**). Add a **growing zone** on soil and pick a crop.
4. **Work** tab: choose who does what. 1 is first, – means never.
5. Raiders come every few days. Tap a colonist and press **Draft**, then tap the ground to move them.
   They shoot anything in range. Walls, doors and turrets help.
6. Build a **research bench**, then pick a project in **Research**. The ship needs steel and a lot of research.

## building

The code is split in two:

- `sim/` is plain Kotlin (JVM) with the whole simulation and unit tests. It has no Android dependencies.
- `app/` is the Android app: a canvas renderer, touch controls and the HUD.

```sh
./gradlew :sim:test                    # simulation tests, no Android SDK needed
./gradlew -PwithApp :app:assembleRelease   # needs the Android SDK
```

The APK ends up in `app/build/outputs/apk/release/`. The `Build APK` workflow builds it on every push and
uploads it as an artifact.
