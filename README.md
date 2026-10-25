# Vein Mining

Mine one ore block in Hytale and the rest of the vein comes with it. No more chipping away at a
copper cluster one block at a time.

## What it does

- **Breaks the whole vein**: when you break an ore block, every ore block touching it (and
  touching those, and so on) breaks too, up to 64 extra blocks by default, nearest first.
- **One vein = one metal**: blocks join a vein when they share the ore's gather type, so copper in
  stone and copper in sandstone are mined together, but the iron next to them isn't.
- **Plays by the normal rules**: every vein block goes through the game's own mining code, as if you
  had mined it yourself in one hit:
  - your pickaxe must be good enough for that ore, or the block stays put;
  - each block drops exactly what it normally would (ore plus cobble, etc.);
  - each block costs your tool the usual durability.
- **Plays nicely with other plugins**: if a land-claim or protection plugin cancels your break,
  nothing else is mined, and each vein block fires its own break event that can be cancelled too.
- **Off in Creative**, so builders don't lose whole veins by accident.

## Configuration

On first start the plugin writes `config.json` to its data folder
(`<server>/mods/VeinMining_VeinMining/config.json`). Edit it and restart the server.

```json
{
  "maxBlocks": 64,
  "requireCrouch": false,
  "includeDiagonals": false,
  "blocks": ["Ore_*"]
}
```

| Setting | What it does |
|---|---|
| `maxBlocks` | Most extra blocks one break may take with it (`0` turns vein mining off) |
| `requireCrouch` | Only vein mine while crouching |
| `includeDiagonals` | Also follow ore that only touches along an edge or corner. Off by default: with faces only, every mined block opens onto the tunnel, so its drops are always reachable |
| `blocks` | Block ids that count as ore (`*` is a wildcard, case is ignored) |

## Building

Requirements: JDK 25 or newer, Gradle, and Hytale installed through the launcher.

```bash
gradle build          # -> build/libs/VeinMining-<version>.jar
gradle installMod     # builds and copies the jar into Hytale's UserData/Mods folder
gradle runServer      # starts a local dedicated server in ./run with the plugin loaded
```

The Hytale install and Mods folder default to the Flatpak launcher's locations on Linux
(`~/.var/app/com.hypixel.HytaleLauncher/...`). If yours are elsewhere, create a
`local.properties` file (it isn't committed) next to `build.gradle.kts`:

```properties
hytaleDir=/path/to/Hytale/install/release/package/game/latest
hytaleModsDir=/path/to/Hytale/UserData/Mods
```

## Testing in game

1. `gradle installMod`
2. Launch Hytale, create or open a world in Survival (Adventure) mode, and enable **VeinMining** in
   the world's mod settings if it isn't enabled automatically.
3. Find an ore vein, mine one block with a good enough pickaxe, and watch the rest go.

## Project layout

```
src/main/java/dev/veinmining/
  VeinMiningPlugin.java   plugin entry: loads config, registers the break system
  VeinBreakSystem.java    listens for ore breaks and breaks the rest of the vein
  VeinFinder.java         finds the connected blocks of a vein
  VeinMiningConfig.java   config.json settings
src/main/resources/
  manifest.json           Hytale plugin manifest
```

## License

[MIT](LICENSE) © 2026 djsaintg
