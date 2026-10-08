# Colony

A RimWorld-style colony simulator for Android, written from scratch in Kotlin. A few survivors land on a hostile
rimworld; you keep them fed, warm, healthy and sane, fight off raiders, and eventually build a ship to leave.

Everything is original code with no copied assets (the art is drawn on a canvas). It is built to play like the base game
without any DLC. It is not literally a 1:1 clone: buildings are one tile each, there is a single map and no world map.

## what's in it

**Colonists**
- Backstories, 12 skills with passions, 30 traits, work priorities (1-4) for 18 job types, and a 24-hour schedule.
- Needs: food, rest, joy and temperature comfort, plus a mood built from dozens of thoughts (room beauty and cleanliness,
  bedroom impressiveness, meals, clothing, pain, weather, drugs, relationships, corpses...).
- Mental breaks (wandering, food binge, insult spree, tantrum, fire starting, berserk, catatonia, running into the wilds).
- Social: chitchat, deep talks, insults, romance, marriage and fights.
- Drug use with highs, tolerance, addiction and withdrawal.

**Health**
- Full body-part health: 25 human parts, organs, hit locations, armor, penetration into organs, bleeding, pain,
  consciousness, blood loss, infections, scars and lost limbs. Capacities (moving, manipulation, sight...) follow the parts.
- Doctors tend wounds with herbal or industrial medicine; hospital beds heal better. Colonists rescue the downed.
- Illnesses (flu, plague, malaria, sleeping sickness, gut worms, parasites), hypothermia, frostbite, heatstroke,
  malnutrition, food poisoning, toxic buildup.

**Base building**
- 60+ buildings: walls, doors, floors, beds, hospital beds, tables, chairs, lamps, recreation, workbenches, power,
  heaters and coolers, sandbags, traps, turrets, mortars, graves, art and the ship parts.
- Rooms are detected from walls; they have temperature, beauty, cleanliness, space and impressiveness.
- Zones: stockpiles with item filters, quality limits and priorities, dumping zones, and growing zones.
- Workbenches with bills (do X times, until you have X, forever) for cooking, butchering, tailoring, smithing,
  machining, stonecutting, drugs and medicine. Items have quality; art has beauty.
- Power grids: conduits, solar, wind, wood generators, batteries; lamps, heaters, coolers, hydroponics, stoves.
- Fire that spreads, rain that puts it out, lightning, and firefighting.

**World**
- Six biomes, rivers, lakes, five rock types, six ores, caves under natural roof, trees that regrow.
- Day and night with real lighting, four seasons, rain, fog, snow and thunderstorms, wind, cold snaps and heat waves.
- Crops that grow by temperature, light and soil; frost and blight; spoilage of food and corpses.
- Wildlife herds: grazers, predators, farm animals. Hunting, taming, slaughtering, butchering, wool, milk, eggs.

**Threats and events**
- Storytellers (Marlowe, Juniper, Orrin) and difficulty levels.
- Raids that scale with wealth: assaults, sappers, sieges with mortars and drop pods, with tiered gear.
- Manhunter packs, insect infestations, disease outbreaks, solar flares, eclipses, toxic fallout, short circuits,
  blight, thrumbos, tame animals wandering in, wanderers joining, supply pods, trade caravans.
- Prisoners: capture, feed, recruit or release. Escape attempts.

**Progress**
- A 33-item tech tree from basic crafts to the ship parts. Build the ship and launch to win.
- Scenarios: Crashlanded, Lost tribe, Rich explorer, Naked brutality. Character reroll before landing.
- Saves automatically.

## how to play

Landscape only. Drag to pan, pinch to zoom, tap to inspect. Open **Menu → How to play** in the game.

## building it

The code is split in two:

- `sim/` is plain Kotlin (JVM) with the whole simulation and its unit tests. No Android dependencies.
- `app/` is the Android app: a canvas renderer, touch controls and the interface.

```sh
./gradlew :sim:test                        # simulation tests, no Android SDK needed
./gradlew -PwithApp :app:assembleRelease   # needs the Android SDK
```

The APK ends up in `app/build/outputs/apk/release/`. The `Build APK` workflow builds it on every push and uploads
it as an artifact.
