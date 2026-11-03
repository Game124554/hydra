# RealCraft Realism (Minecraft Java 1.20.1, Forge)

A realism overhaul for Minecraft Java Edition **1.20.1** on **Forge 47.x**. Every feature can be
switched on or off and tuned in the in-game settings screen, including performance limits.

## Features

| Feature | What it does |
|---|---|
| **Smooth terrain** (NoCubes-style) | Stone, dirt, grass, sand, gravel, ores, netherrack and other natural blocks are drawn as smooth surfaces instead of cubes (surface-nets meshing, crack-free across chunks; flat ground stays flat). A smoothness slider goes from cubes (0) to fully smooth (1). |
| **Step assist** | Players step up full blocks, so smoothed slopes can be walked without jumping. |
| **Structural physics** | Mining, placing or blasting near natural blocks triggers support checks. Rock can span a few blocks (arches and tunnels hold), but wide unsupported ceilings cave in. Soil needs support right below it. Falling rock crumbles to cobblestone and hurts whoever it lands on. |
| **Explosion debris** | Explosions hurl part of the blocks they destroy through the air; the debris lands as blocks. |
| **Breakable glass** | Arrows and tridents fly through glass and shatter it. Sprinting into glass or landing on it from a height smashes it (and cuts you). Blast shockwaves blow out nearby windows. |
| **Camera shake** | Explosions (scaled by distance), taking damage, hard landings, thunder, roars, sonic booms and nearby cave-ins. Works on vanilla servers too. |
| **Ragdolls** | Dying creatures topple and tumble with physics against real block collisions instead of the stiff death flip. Arms, legs and head swing like pendulums (humanoids and four-legged animals). Corpses stay a few seconds so the ragdoll can settle. |
| **Item physics** | Dropped items lie flat on the ground at random angles, blocks sit on their base, items tumble through the air and bob in water. |
| **Water** | Stronger currents, heavy armor drags swimmers down, big splashes when creatures fall into water. |
| **Lava** | Radiant heat sets you on fire when you stand right next to lava; water meeting lava bursts into steam clouds. |
| **Bedrock removed** | New chunks get no bedrock floor (overworld) or floor and roof (nether). The replacement block is configurable; set it to `minecraft:air` to open the void. |
| **Hell underground** | Below Y -40 (configurable) the overworld turns into hell: netherrack, magma, soul-sand and basalt patches, nether ores, burning floors, glowstone ceilings and lava lakes. Water boils away. Thick red fog, drifting ash, rising embers and distant rumbles there and in the Nether. |
| **Realistic mobs** | Monsters struggle to spot you in the dark or while sneaking. Hurting an animal makes its herd flee. Headshots with projectiles deal extra damage (helmets soften it) and leg hits slow. Badly wounded creatures limp. Hits spray matching particles (blood, bone, slime...). Big creatures shrug off knockback. |

## Settings

* Press **K** in game (rebindable under Controls), or open **Mods → RealCraft Realism → Config**.
* The top row applies a **performance preset**: Low / Medium / High / Ultra. Presets set the limits
  (ragdoll count and distance, solver quality, items drawn per stack, particle density, cave-in
  work per tick, debris per explosion, shockwave radius). Low also turns smooth terrain off.
* Everything else is a toggle or slider with a tooltip. Yellow categories are visual (client)
  settings. Orange categories are gameplay settings: they apply in singleplayer, and a
  multiplayer server uses its own copy.
* The files are `config/realcraft-client.toml` and `config/realcraft-common.toml`. A few settings
  exist only in the files: extra or excluded smooth blocks, and the bedrock replacement blocks.

### Choosing blocks with tags (datapack)

| Tag | Used for |
|---|---|
| `realcraft:smoothable` | blocks drawn as smooth terrain |
| `realcraft:physics_rigid` | rock-like blocks that can span gaps |
| `realcraft:physics_loose` | soil-like blocks that need support below |
| `realcraft:shatterable` | glass that breaks |
| `realcraft:no_debris` | blocks never thrown by explosions |

## Building

Requirements: JDK 17 and an internet connection (Gradle downloads Minecraft and Forge).

```bash
cd minecraft/realcraft-realism
./gradlew build          # Windows: gradlew.bat build
```

The mod jar is written to `build/libs/realcraft-1.20.1-1.0.0.jar`. Copy it to the `mods` folder of
a Forge 1.20.1 installation (Forge 47.x).

To try it in a development client: `./gradlew runClient`.

The GitHub Actions workflow `.github/workflows/realcraft-mod.yml` builds the jar on every change
to this folder, uploads it as a downloadable artifact and boots a dedicated server to check that
the mod loads.

## Known limitations

* **Smooth terrain is visual only.** Collisions are still block-based, which is why step assist
  exists. Block outlines are still cubes.
* **Smooth terrain needs the vanilla chunk renderer.** OptiFine, Embeddium, Rubidium and other
  Sodium-style renderers replace it, so terrain stays cubic with them. Every other feature still
  works.
* **World generation changes only affect new chunks.** Bedrock removal and the hell layer appear
  in newly generated chunks, so explore new areas or start a new world.
* **Ragdolls are an approximation.** The body is a tumbling rigid box with swinging limbs, not a
  full per-bone simulation. Ragdolls are client-side and purely visual. They animate humanoid and
  four-legged models; other creatures tumble as one piece.
* **Realistic mobs means behaviour, not new models or textures.** The mod ships no art assets;
  pair it with a realistic resource pack for looks.
* Cave-ins can be large in big caverns. Lower *Rock Overhang Span*, or the per-tick limits, if
  they are too much.
