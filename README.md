# Colony

A RimWorld-style colony simulator for Android, written from scratch in Kotlin. A few survivors land on a hostile
rimworld; you keep them fed, warm, healthy and sane, fight off raiders, and eventually build a ship to leave.

Everything is original code with no copied assets (the art is drawn procedurally on a canvas: textured ground, wall
autotiling, shadowed furniture, per-species animals, rotating pawns with hair and clothing, item sprites). It is built
to play like the base game, including a children-and-pregnancy life cycle, without other DLC. It is not a literal clone:
there is no mod support, balance numbers are my own tuning, and caravan battles run on a small throwaway map without
player control.

## what's in it

**Colonists**
- Backstories (33), 12 skills with passions, 42 traits (including sexual orientation), work priorities (1-4) for 18 job types, and a 24-hour schedule.
- Needs: food, rest, joy and temperature comfort, plus a mood built from dozens of thoughts (room beauty and cleanliness,
  bedroom impressiveness, meals, clothing, pain, weather, drugs, relationships, corpses...).
- Mental breaks (wandering, food binge, insult spree, tantrum, fire starting, berserk, catatonia, running into the wilds).
- Social: chitchat, deep talks, insults, romance (by orientation), marriage, breakups, rivals, family and fights.
- Life stages: birthdays every 60 days, babies fed by adults, children who do light chores and grow up (passions at 7, 10, 13;
  a trait at 13), pregnancy for couples, and old age with bad backs, arthritis, cataracts, hearing loss and dementia.
- Outfit and drug policies per colonist, plus food policy and schedule.
- Drug use with highs, tolerance, addiction and withdrawal.

**Health**
- Full body-part health: 25 human parts, organs, hit locations, armor, penetration into organs, bleeding, pain,
  consciousness, blood loss, infections, scars and lost limbs. Capacities (moving, manipulation, sight...) follow the parts.
- Doctors tend wounds with herbal or industrial medicine; hospital beds heal better. Colonists rescue the downed.
- Illnesses (flu, plague, malaria, sleeping sickness, gut worms, parasites), hypothermia, frostbite, heatstroke,
  malnutrition, food poisoning, toxic buildup.

**Base building**
- 65 buildings, many multi-tile (beds 1×2, tables 2×2, long workbenches, generators and ship parts; rotate while placing): walls, doors, floors, beds, hospital beds, tables, chairs, lamps, recreation, workbenches, power,
  heaters and coolers, sandbags, traps, turrets, mortars, graves, art, comms console, trade beacon, sun lamps and the ship parts.
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
- Wildlife herds (40 kinds of creatures: grazers, predators, farm animals, boomalopes...), hunting, taming, slaughtering, butchering,
  wool, milk, eggs, and breeding with juveniles that grow up.
- Items wear away outdoors, faster in rain.

**World map, factions and caravans**
- A generated 60×40 planet with six biomes, hills, impassable mountains, rivers, lakes and the sea, roads that halve travel
  cost, six factions (two tribes, two outlander unions, two pirate gangs) and their settlements. Your colony's tile decides
  its biome, river, lake and ruggedness. Map sizes from 75 to 200.
- Factions have goodwill, standing (hostile to allied), and relations with each other: a gift to one pleases its friends
  and annoys its enemies. Hostile factions raid you with their own gear; at the comms console you can pay for peace talks,
  request traders, and ask allies for soldiers. A trade beacon and comms console call orbital trade ships.
- Form a caravan from colonists and tame pack animals (35 kg per person, 70-140 kg for big animals), pack goods by weight,
  and send it anywhere. On the road: food, rest, foraging, spoilage, medicine, and ambushes that play out as real battles
  on a small map with your people's weapons, cover and wounds.
- At settlements: trade, fulfil requests, gift, or attack them (defenders fight behind sandbags; a win loots and ruins the
  settlement). Clear bandit camps for friendly factions' rewards. Found a second colony anywhere and switch between colonies.

**Threats and events**
- Storytellers (Marlowe, Juniper, Orrin) and difficulty levels.
- Raids that scale with wealth: assaults, sappers, sieges with mortars and drop pods, with tiered gear.
- Manhunter packs, insect infestations, disease outbreaks, solar flares, eclipses, toxic fallout, short circuits,
  blight, thrumbos, meteorites, aurora, psychic drone and soothe, volcanic winter, crashed mechanoid ships (dormant
  until approached), tame animals wandering in, wanderers joining, supply pods, trade caravans.
- Prisoners: capture, feed, recruit or release. Escape attempts.

**Progress**
- A 53-item tech tree (plus 64 recipes and 91 items, including heavy weapons, powered armor, flake, yayo and go-juice) from basic crafts to the ship parts. Build the ship and launch to win.
- Scenarios: Crashlanded, Lost tribe, Rich explorer, Naked brutality. Character reroll before landing.
- Saves automatically.

## install

1. Download **[colony.apk](https://github.com/teamomuito/rimworld/releases/latest/download/colony.apk)** on your Android phone
   (Android 8.0+). That link always points at the newest build; older ones are on the
   [releases page](https://github.com/teamomuito/rimworld/releases).
2. Open the file and allow installs from your browser or files app when asked.
3. Uninstall an older build first if Android refuses to update it (builds are signed with a throwaway debug key).

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
