# Starhop (Minecraft 26.3, Fabric)

Galaxy-style platforming for Minecraft, built around an original theme. All sounds and effects are original
and made for this mod.

## Moves (v0.2)

| Move | How | What you get |
|---|---|---|
| Triple jump | Jump, land, and jump again quickly while moving. Do it three times. | Each jump goes higher. The 3rd leaves a sparkling spiral. |
| Long jump | Run, then crouch and jump. | Very fast and low, with a cloud streak. |
| Backflip | Stand still, crouch and jump. | A tall jump slightly backwards, with a ring of sparks. |
| Wall jump | Slide down a wall, then press jump. | Kicks you off the wall. You can chain them to climb between two walls. |
| Spin | Press `R` in the air (once per jump). | A short hover with a particle ring. |
| Ground pound | Press crouch in mid-air (let go and press again if you were already crouching). | Hover, then slam down. Landing sends a shockwave that damages nearby monsters, and you take no fall damage. |
| Stomp | Land on top of a monster. | Damages it and bounces you. Hold jump for a higher bounce. Every third stomp in a row bounces highest. |
| Comet Shard | Right-click (Ingredients tab, or `/give @s starhop:comet_shard`). | 15 seconds of Speed III, Jump Boost II and Resistance V. |

Each move has its own sound. Sounds are in `assets/starhop/sounds/` as `.ogg` files, and you can replace any
of them with your own recording using the same file name.

## Things to know

- Crouch plus jump now does a backflip or long jump, so ordinary "sneak jumps" behave differently.
- A ground pound can start from any height. The server recognises it by the speed of the fall.
- Only monsters are stomped or hurt by shockwaves, never animals, villagers or players.
- The moves and their sounds are produced on your own client, so on a multiplayer server other players hear
  only the stomp, the ground pound landing and the Comet Shard.
- Animations are particles. Flips and spins of the character's body would need custom player
  rendering, which is planned once this version builds and runs.

## Controller support

Minecraft 26.3 replaced GLFW with SDL, so the direct gamepad code was removed. Spin is a normal rebindable key
(default `R`, under "Starhop" in Controls), so a controller mod such as Controlify can map it to a button
(not tested). Jumping, crouching and moving are vanilla actions, so every other move works with any input method.

## Build without installing anything (GitHub Actions)

1. Create a free GitHub account and a new repository.
2. Upload the contents of this folder (the `src` folder, `build.gradle`, `gradle.properties`, `settings.gradle`).
   The workflow file must be at `.github/workflows/main.yml` in the repo.
3. Open the repo's **Actions** tab. The "Build Starhop" workflow runs automatically (or click "Run workflow").
4. When it finishes, open the run and download the **starhop-mod** artifact. Unzip it and put the `.jar` in your
   `mods` folder along with Fabric API for 26.3.
5. If the run fails, open it, copy the lines under `* What went wrong:` and send them to Claude.

## Setup on your own computer

1. Install **JDK 25** and **IntelliJ IDEA 2025.3 or newer**.
2. Open this folder as a Gradle project.
3. Create the Gradle wrapper once: `gradle wrapper --gradle-version latest`.
4. Run `./gradlew runClient` to test, `./gradlew build` for the jar in `build/libs/`.

## Not fully tested

The v0.1 code compiled and loaded. The v0.2 code (moves, server logic, sounds) has been checked for syntax but
not compiled. If the build fails, the most likely spots are in `StarhopServer.java`: `hurtServer`,
`damageSources().playerAttack`, `resetFallDistance`, `sendParticles`.

## Tuning

Constants at the top of `StarhopClient.java` control jump heights, speeds and timings. Jumps above about 3 blocks
cause fall damage. If you change `POUND_SPEED`, change `POUND_DY` in `StarhopServer.java` to match.

## Roadmap

- Custom character rendering with real body animations (flips, spins, a pound pose)
- Your own hero character (skin or mob) with your own art
- Small planetoids with custom gravity
- Life meter and coin counter
