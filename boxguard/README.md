# BoxGuard Admin — Fabric 1.21.11

Authorized server-admin utility for monitoring and testing your own Minecraft server.

## Included
- Right Shift: open the admin GUI.
- InvSee: read-only snapshot of another player's inventory. Permission level 2 required.
- Suspected Players: a GUI list of players who triggered KillAura/AutoTotem heuristics, with a suspicion score and recent flags.
- ESP Players: administrative player outlines through walls, limited to a 128-block range.
- KillAura detector: server-side heuristic based on high attack rate / rapid target switching.
- AutoTotem detector: server-side heuristic watching rapid Totem switches at low health.
- Test buttons simulate alert messages without running a cheat.
- `/boxguard status`
- `/boxguard detect killaura on|off`
- `/boxguard detect autototem on|off`
- `/boxguard invsee <player>`
- `/boxguard suspects`
- `/boxguard reset suspects`
- `/boxguard test killaura`
- `/boxguard test autototem`

## Important
This project intentionally does **not** implement KillAura or AutoTotem as cheating modules. Those are represented by server-side detection/testing tools instead. Suspicion scores are heuristic signals and should not be treated as proof by themselves.

## Target toolchain
- Minecraft 1.21.11
- Fabric Loader 0.18.1+
- Fabric API 0.141.5+1.21.11
- Fabric Loom 1.14.10
- Java 21

## Build
Use Java 21 and Gradle 9.2+ and run:

    gradle build --no-daemon

The remapped mod jar is created under `build/libs/`.
